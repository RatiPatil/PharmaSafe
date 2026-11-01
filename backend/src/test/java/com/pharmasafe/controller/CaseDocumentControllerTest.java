package com.pharmasafe.controller;
import com.fasterxml.jackson.databind.*;
import com.pharmasafe.entity.*;
import com.pharmasafe.repository.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
@WithMockUser(username="pdf-admin",roles="ADMIN")
class CaseDocumentControllerTest {
 @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired UserRepository users;@Autowired SafetyCaseRepository cases;@Autowired CaseActionItemRepository actions;@Autowired SafetyReportRepository reports;@Autowired CaseDocumentExportRepository documents;
 private SafetyCase record;private User admin,analyst;
 @BeforeEach void setup(){admin=users.saveAndFlush(new User("pdf-admin","unused-test-hash","ROLE_ADMIN"));analyst=users.saveAndFlush(new User("pdf-analyst","unused-test-hash","ROLE_CASE_PROCESSOR"));record=new SafetyCase();record.setCaseNumber("PS-SYNTHETIC-PDF-01");record.setCountryCode("USA");record.setReportType("Spontaneous");record.setWorkflowState("Closed");record.setVersionNumber(3);record.setInitialReceiptDate(LocalDate.of(2026,10,1));record.setLatestReceivedDate(LocalDate.of(2026,10,5));record.setCreatedByUser(admin);record.setAssignedToUser(analyst);record.setNarrative("Synthetic clinical narrative. β-blocker exposure followed by a fictional reaction.\n"+"Long clinical statement with details for table pagination. ".repeat(130));record.setCompanyComments("Reviewed synthetic evidence. No actual patient information.");CasePatient patient=new CasePatient();patient.setPatientIdentifier("SYNTHETIC-PATIENT");patient.setGender("Unknown");record.setPatient(patient);CaseProduct product=new CaseProduct();product.setProductName("Synthetic study product");product.setProductType("Suspect");record.addProduct(product);CaseEvent event=new CaseEvent();event.setEventVerbatim("Synthetic adverse event");event.setOnsetDate(LocalDate.of(2026,10,2));event.setOutcome("Recovered");event.setSerious(true);record.addEvent(event);cases.saveAndFlush(record);}
 String base(){return "/api/cases/"+record.getId()+"/documents";}
 String body(String kind,boolean finalDoc)throws Exception{return json.writeValueAsString(Map.of("documentKind",kind,"finalDocument",finalDoc,"expectedRowVersion",record.getRowVersion()));}
 JsonNode generate(boolean finalDoc)throws Exception{return json.readTree(mvc.perform(post(base()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body("CASE_FORM",finalDoc))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());}
 @Test void completeFinalPdfHasTablesAllDataPagesAndEmbeddedSource()throws Exception{
  var metadata=generate(true);String id=metadata.path("id").asText();
  byte[] bytes=mvc.perform(get(base()+"/"+id+"/pdf")).andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF)).andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse().getContentAsByteArray();
  try(var pdf=Loader.loadPDF(bytes)){
   String text=new PDFTextStripper().getText(pdf);assertTrue(pdf.getNumberOfPages()>3);for(String value:List.of("PharmaSafe Safety Suite","FINAL RECORD","SYNTHETIC-PATIENT","Synthetic study product","Synthetic adverse event","β-blocker","Page 1 of"))assertTrue(text.contains(value),value);
   var files=new org.apache.pdfbox.pdmodel.PDDocumentNameDictionary(pdf.getDocumentCatalog()).getEmbeddedFiles().getNames();byte[] original=files.get("source-data.json").getEmbeddedFile().toByteArray();JsonNode source=json.readTree(original);assertEquals(record.getNarrative(),source.path("narrative").path("caseNarrative").asText());assertEquals(3,source.path("documentControl").path("caseVersion").asInt());assertEquals("Suspect",source.path("products").get(0).path("productType").asText());
  }
  Path dir=Path.of("target/qa-pdfs");Files.createDirectories(dir);Files.write(dir.resolve("complete-case-final.pdf"),bytes);
  record.setNarrative("Later change after archived snapshot");cases.saveAndFlush(record);
  byte[] original=mvc.perform(get(base()+"/"+id+"/pdf")).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();assertArrayEquals(bytes,original);
 }
 @Test void finalExportRequiresClosedCaseAndApprovedRole()throws Exception{
  record.setWorkflowState("Data Entry");cases.saveAndFlush(record);mvc.perform(post(base()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body("CASE_FORM",true))).andExpect(status().isBadRequest());
  mvc.perform(post(base()).with(user("pdf-analyst").roles("CASE_PROCESSOR")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body("CASE_FORM",true))).andExpect(status().isForbidden());
 }
 @Test void finalIssuanceCannotBypassOutstandingWork()throws Exception{
  CaseActionItem action=new CaseActionItem();action.setSafetyCase(record);action.setDescription("Synthetic legacy follow-up");action.setAssignedUser(admin);action.setCreatedByUser(admin);action.setDueAt(Instant.now().plusSeconds(3600));actions.saveAndFlush(action);
  mvc.perform(post(base()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body("CASE_FORM",true))).andExpect(status().isBadRequest());
  action.setStatus("COMPLETED");action.setCompletedByUser(admin);action.setCompletedAt(Instant.now());actions.saveAndFlush(action);
  SafetyReport report=new SafetyReport();report.setReportId("PDF-PENDING-REPORT");report.setReportType("15-day");report.setProduct("Synthetic product");report.setDueDate(LocalDate.now().plusDays(10));report.setRelatedCases(new ArrayList<>(List.of(record)));reports.saveAndFlush(report);
  mvc.perform(post(base()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body("CASE_FORM",true))).andExpect(status().isBadRequest());assertEquals(0,documents.count());
 }
 @Test void staleExportCannotFreezeAnOlderRevision()throws Exception{mvc.perform(post(base()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("documentKind","CASE_FORM","finalDocument",false,"expectedRowVersion",record.getRowVersion()+1)))).andExpect(status().isConflict());assertEquals(0,documents.count());}
 @Test void downloadAndHistoryEnforceCurrentCaseScope()throws Exception{var d=generate(false);User outsider=users.save(new User("pdf-outsider","unused","ROLE_CASE_PROCESSOR"));mvc.perform(get(base()).with(user(outsider.getUsername()).roles("CASE_PROCESSOR"))).andExpect(status().isForbidden());mvc.perform(get(base()+"/"+d.path("id").asText()+"/pdf").with(user(outsider.getUsername()).roles("CASE_PROCESSOR"))).andExpect(status().isForbidden());}
 @Test void archiveCannotBeReadThroughAnotherCasePath()throws Exception{var d=generate(false);SafetyCase other=new SafetyCase();other.setCaseNumber("OTHER-SYNTHETIC");other.setCountryCode("USA");other.setReportType("Spontaneous");other.setInitialReceiptDate(LocalDate.now());other.setLatestReceivedDate(LocalDate.now());other.setCreatedByUser(admin);cases.saveAndFlush(other);mvc.perform(get("/api/cases/"+other.getId()+"/documents/"+d.path("id").asText()+"/pdf")).andExpect(status().isNotFound());}
 @Test void generationRequiresCsrfAndSummaryPermission()throws Exception{mvc.perform(post(base()).contentType(MediaType.APPLICATION_JSON).content(body("CASE_FORM",false))).andExpect(status().isForbidden());mvc.perform(post(base()).with(user("pdf-analyst").roles("CASE_PROCESSOR")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body("MEDICAL_SUMMARY",false))).andExpect(status().isForbidden());}
 @Test void medicalSummaryOmitsContactAndReporterSections()throws Exception{record.setWorkflowState("Medical Review");cases.saveAndFlush(record);String response=mvc.perform(post(base()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body("MEDICAL_SUMMARY",false))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();var doc=documents.findById(json.readTree(response).path("id").asText()).orElseThrow();var snapshot=json.readTree(doc.getSnapshotJson());assertFalse(snapshot.has("reporters"));assertFalse(snapshot.has("contactHistory"));assertTrue(snapshot.has("assessments"));Files.createDirectories(Path.of("target/qa-pdfs"));Files.write(Path.of("target/qa-pdfs/medical-summary-working.pdf"),doc.getPdfBytes());}
 @Test void unsupportedUnicodeIsPreservedInSourceAndEscapedInTable()throws Exception{record.setNarrative("Synthetic 漢字 and emoji 🧪");cases.saveAndFlush(record);var d=generate(false);var doc=documents.findById(d.path("id").asText()).orElseThrow();try(var pdf=Loader.loadPDF(doc.getPdfBytes())){assertTrue(new PDFTextStripper().getText(pdf).contains("[U+6F22]"));assertTrue(doc.getSnapshotJson().contains("漢字"));}}
 @Test void tamperedArchiveIsNotDownloaded()throws Exception{var d=generate(false);var archive=documents.findById(d.path("id").asText()).orElseThrow();archive.setPdfSha256("a".repeat(64));documents.saveAndFlush(archive);mvc.perform(get(base()+"/"+archive.getId()+"/pdf")).andExpect(status().isBadRequest());}
}
