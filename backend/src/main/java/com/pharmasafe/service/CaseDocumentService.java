package com.pharmasafe.service;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.pharmasafe.dto.*;
import com.pharmasafe.entity.*;
import com.pharmasafe.repository.*;
import com.pharmasafe.security.WorkspaceAccess;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.Instant;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.*;

@Service
public class CaseDocumentService {
 private final SafetyCaseRepository cases; private final UserRepository users; private final CaseDocumentExportRepository documents;
 private final CaseFollowUpRepository receipts; private final CaseContactLogRepository contacts; private final CaseActionItemRepository actions;
 private final CaseReviewEventRepository reviews; private final SafetyReportRepository reports; private final AuditService audit;
 private final ObjectMapper json; private final CasePdfRenderer renderer;
 private static final Set<String> SUPPORTED=Set.of("ROLE_ADMIN","ROLE_SAFETY_MANAGER","ROLE_CASE_PROCESSOR","ROLE_USER","ROLE_MEDICAL_REVIEWER","ROLE_QUALITY_REVIEWER","ROLE_REPORTING_SPECIALIST","ROLE_AUDITOR");
 public CaseDocumentService(SafetyCaseRepository cases,UserRepository users,CaseDocumentExportRepository documents,
 CaseFollowUpRepository receipts,CaseContactLogRepository contacts,CaseActionItemRepository actions,CaseReviewEventRepository reviews,
 SafetyReportRepository reports,AuditService audit,ObjectMapper json,CasePdfRenderer renderer){this.cases=cases;this.users=users;this.documents=documents;this.receipts=receipts;this.contacts=contacts;this.actions=actions;this.reviews=reviews;this.reports=reports;this.audit=audit;this.json=json;this.renderer=renderer;}
 private SafetyCase access(Long id,String actor,String role,boolean lock){
  if(!SUPPORTED.contains(role))throw new AccessDeniedException("Document access is not configured for your role");
  SafetyCase c=(lock?cases.findForUpdate(id):cases.findById(id)).orElseThrow(()->new EntityNotFoundException("Case not found"));
  if(!new WorkspaceAccess().canRead(c,actor,role))throw new AccessDeniedException("You do not have access to this case document");return c;
 }
 @Transactional(readOnly=true)
 public Page<CaseDocumentDto> list(Long id,String actor,String role,Pageable page){access(id,actor,role,false);return documents.findMetadataByCaseId(id,page);}
 @Transactional(isolation=Isolation.REPEATABLE_READ)
 public CaseDocumentDto create(Long id,CaseDocumentDto.Request request,String actor,String role){
  SafetyCase c=access(id,actor,role,true);
  if(request.expectedRowVersion()==null||request.expectedRowVersion()!=c.getRowVersion())throw new org.springframework.orm.ObjectOptimisticLockingFailureException(SafetyCase.class,id);
  if("MEDICAL_SUMMARY".equals(request.documentKind())&&!Set.of("ROLE_ADMIN","ROLE_SAFETY_MANAGER","ROLE_MEDICAL_REVIEWER","ROLE_REPORTING_SPECIALIST").contains(role))throw new AccessDeniedException("Medical summary export is not enabled for your role");
  if(request.finalDocument()){
   if(!Set.of("ROLE_ADMIN","ROLE_SAFETY_MANAGER","ROLE_REPORTING_SPECIALIST").contains(role))throw new AccessDeniedException("Only reporting specialists or workflow managers can issue final documents");
   if(!"Closed".equals(c.getWorkflowState()))throw new IllegalStateException("Close the case before issuing a final document");
   if(actions.countBySafetyCaseIdAndStatusNotIn(id,List.of("COMPLETED","CANCELLED"))>0||reports.countPendingExpedited(id)>0)throw new IllegalStateException("Resolve open actions and pending expedited reports before issuing a final document");
  }
  User user=users.findByUsername(actor).orElseThrow(()->new AccessDeniedException("Account not found"));
  CaseDocumentExport d=new CaseDocumentExport();d.setId(UUID.randomUUID().toString());d.setSafetyCase(c);d.setDocumentKind(request.documentKind());d.setFinalDocument(request.finalDocument());d.setCaseVersion(c.getVersionNumber());d.setSourceRowVersion(c.getRowVersion());d.setWorkflowState(c.getWorkflowState());d.setTemplateVersion("case-tables-1.0");d.setGeneratedAt(Instant.now());d.setGeneratedByUser(user);d.setGeneratedByUsername(actor);
  ObjectNode snapshot=snapshot(c,d);
  try{
   String original=json.writerWithDefaultPrettyPrinter().writeValueAsString(snapshot);byte[] source=original.getBytes(StandardCharsets.UTF_8);
   if(source.length>5_000_000)throw new IllegalArgumentException("Case snapshot exceeds the 5 MB export limit; contact your administrator");
   byte[] pdf=renderer.render(snapshot,source);d.setSnapshotJson(original);d.setPdfBytes(pdf);d.setPdfSha256(hash(pdf));
   String number=c.getCaseNumber().replaceAll("[^A-Za-z0-9._-]","_");if(number.length()>70)number=number.substring(0,70);
   d.setFileName(number+"-v"+c.getVersionNumber()+"-"+(request.finalDocument()?"final":"working")+"-"+request.documentKind().toLowerCase(Locale.ROOT)+".pdf");
   documents.saveAndFlush(d);audit.logCaseEvent(id,"CASE_DOCUMENT_GENERATED",actor,"SUCCESS","Document "+d.getId()+"; "+request.documentKind()+"; source revision "+c.getRowVersion()+"; final="+request.finalDocument()+"; SHA-256 "+d.getPdfSha256());
   return CaseDocumentDto.from(d);
  }catch(java.io.IOException e){throw new IllegalStateException("The PDF could not be generated; no archive was saved",e);}
 }
 private ObjectNode snapshot(SafetyCase c,CaseDocumentExport d){
  ObjectNode out=json.createObjectNode();ObjectNode control=out.putObject("documentControl");control.put("product","PharmaSafe Safety Suite");control.put("documentId",d.getId());control.put("caseNumber",c.getCaseNumber());control.put("documentKind",d.getDocumentKind());control.put("finalDocument",d.getFinalDocument());control.put("caseVersion",c.getVersionNumber());control.put("sourceRowVersion",c.getRowVersion());control.put("workflowState",c.getWorkflowState());control.put("generatedBy",d.getGeneratedByUsername());control.put("generatedAtUtc",d.getGeneratedAt().toString());control.put("templateVersion",d.getTemplateVersion());
  JsonNode clinical=json.valueToTree(CaseResponseDto.fromEntity(c));ObjectNode admin=out.putObject("administration");
  clinical.fields().forEachRemaining(f->{if(!f.getValue().isContainerNode()&&!Set.of("patient","narrative","companyComments").contains(f.getKey()))admin.set(f.getKey(),f.getValue());});
  for(String key:List.of("patient","products","events","assessments","medicalHistoryList"))out.set(key,clinical.path(key));
  ObjectNode narrative=out.putObject("narrative");narrative.set("caseNarrative",clinical.path("narrative"));narrative.set("companyComments",clinical.path("companyComments"));
  if("CASE_FORM".equals(d.getDocumentKind())){
   for(String key:List.of("reporters","references","attachments"))out.set(key.equals("attachments")?"attachmentMetadata":key,clinical.path(key));
   out.set("followUps",json.valueToTree(receipts.findBySafetyCaseId(c.getId(),Pageable.unpaged(Sort.by("followUpNumber"))).map(FollowUpResponse::from).getContent()));
   out.set("contactHistory",json.valueToTree(contacts.findBySafetyCaseIdOrderByContactAtDesc(c.getId(),Pageable.unpaged()).map(ContactLogResponse::fromEntity).getContent()));
   out.set("actionItems",json.valueToTree(actions.findBySafetyCaseIdOrderByDueAtAscIdAsc(c.getId(),Pageable.unpaged()).map(a->ActionItemResponse.fromEntity(a,d.getGeneratedAt())).getContent()));
   ArrayNode workflow=out.putArray("workflowHistory");for(var review:reviews.findBySafetyCaseIdOrderByCreatedAtDesc(c.getId())){
    var row=workflow.addObject();row.put("reviewer",review.getReviewerUsername());row.put("fromState",review.getFromState());row.put("toState",review.getToState());row.put("outcome",review.getOutcome());row.put("comments",review.getComments());row.put("patientReviewed",review.isPatientReviewed());row.put("productsEventsReviewed",review.isProductsEventsReviewed());row.put("medicalReviewed",review.isMedicalReviewed());row.put("createdAt",review.getCreatedAt().toString());
   }
   ArrayNode schedule=out.putArray("scheduledReportRecords");for(var report:reports.findByRelatedCasesId(c.getId())){
    var row=schedule.addObject();row.put("reportId",report.getReportId());row.put("reportType",report.getReportType());row.put("authority",report.getAuthority());row.put("status",report.getStatus());row.put("dueDate",report.getDueDate().toString());if(report.getSubmittedDate()!=null)row.put("submittedAt",report.getSubmittedDate().toString());
   }
   out.set("auditTrailAtGeneration",json.valueToTree(audit.getCaseAuditHistory(c.getId())));
  }return out;
 }
 public record Download(String fileName,byte[] bytes,String sha256){}
 @Transactional
 public Download download(Long caseId,String documentId,String actor,String role){
  access(caseId,actor,role,false);CaseDocumentExport d=documents.findById(documentId).filter(x->x.getSafetyCase().getId().equals(caseId)).orElseThrow(()->new EntityNotFoundException("Case document not found"));
  if(!MessageDigest.isEqual(hash(d.getPdfBytes()).getBytes(StandardCharsets.US_ASCII),d.getPdfSha256().getBytes(StandardCharsets.US_ASCII)))throw new IllegalStateException("Archived PDF integrity verification failed");
  audit.logCaseEvent(caseId,"CASE_DOCUMENT_DOWNLOADED",actor,"SUCCESS","Document "+documentId+"; SHA-256 "+d.getPdfSha256());return new Download(d.getFileName(),d.getPdfBytes(),d.getPdfSha256());
 }
 static String hash(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
