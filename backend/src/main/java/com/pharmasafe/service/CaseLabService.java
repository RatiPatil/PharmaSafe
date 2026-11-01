package com.pharmasafe.service;
import com.pharmasafe.entity.*;
import com.pharmasafe.dto.LabResultDto;
import com.pharmasafe.repository.*;
import com.pharmasafe.security.WorkspaceAccess;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
@Service
public class CaseLabService {
 private final SafetyCaseRepository cases; private final CaseLabResultRepository labs; private final UserRepository users; private final AuditService audit;
 public CaseLabService(SafetyCaseRepository cases,CaseLabResultRepository labs,UserRepository users,AuditService audit){this.cases=cases;this.labs=labs;this.users=users;this.audit=audit;}
 private SafetyCase access(Long id,String actor,String role,boolean write,Long expected){
  SafetyCase c=(write?cases.findForUpdate(id):cases.findById(id)).orElseThrow(()->new EntityNotFoundException("Case not found"));
  if(!new WorkspaceAccess().canRead(c,actor,role))throw new AccessDeniedException("You do not have access to this case");
  if(write){
   if(!Set.of("ROLE_ADMIN","ROLE_SAFETY_MANAGER","ROLE_CASE_PROCESSOR","ROLE_USER").contains(role))throw new AccessDeniedException("Laboratory entry requires a processing role");
   if(c.isLocked()||!Set.of("Draft","Data Entry").contains(c.getWorkflowState()))throw new IllegalStateException("Return the case to data entry before changing laboratory evidence");
   if(expected==null||expected!=c.getRowVersion())throw new org.springframework.orm.ObjectOptimisticLockingFailureException(SafetyCase.class,id);
  }return c;
 }
 @Transactional(readOnly=true) public List<LabResultDto> list(Long id,String actor,String role){access(id,actor,role,false,null);return labs.findBySafetyCaseIdOrderByTestDateDescIdDesc(id).stream().map(LabResultDto::from).toList();}
 private void touch(SafetyCase c,String actor){c.setUpdatedByUser(users.findByUsername(actor).orElseThrow(()->new AccessDeniedException("Account not found")));c.setVersionNumber(c.getVersionNumber()+1);cases.saveAndFlush(c);}
 @Transactional public LabResultDto add(Long id,LabResultDto.Create r,String actor,String role){
  SafetyCase c=access(id,actor,role,true,r.expectedRowVersion());
  if(r.normalLow()!=null&&r.normalHigh()!=null&&r.normalLow().compareTo(r.normalHigh())>0)throw new IllegalArgumentException("Lower reference limit cannot exceed upper reference limit");
  if(labs.countBySafetyCaseId(id)>=1500)throw new IllegalStateException("This case has reached the 1500 laboratory record limit");
  CaseLabResult v=new CaseLabResult();v.setSafetyCase(c);v.setTestName(r.testName().trim());v.setTestDate(r.testDate());v.setResultText(r.resultText().trim());v.setUnit(r.unit());v.setNormalLow(r.normalLow());v.setNormalHigh(r.normalHigh());v.setInterpretation(r.interpretation());v.setNotes(r.notes());v.setRecordedBy(actor);v.setRecordedAt(Instant.now());
  touch(c,actor);labs.saveAndFlush(v);audit.logCaseEvent(id,"LAB_RESULT_RECORDED",actor,"SUCCESS","Laboratory record #"+v.getId()+" added");return LabResultDto.from(v);
 }
 @Transactional public LabResultDto voidResult(Long id,Long labId,LabResultDto.Void r,String actor,String role){
  SafetyCase c=access(id,actor,role,true,r.expectedRowVersion());CaseLabResult v=labs.findById(labId).orElseThrow(()->new EntityNotFoundException("Laboratory record not found"));
  if(!v.getSafetyCase().getId().equals(id))throw new EntityNotFoundException("Laboratory record not found in this case");
  if(v.getVoidedAt()!=null)throw new IllegalStateException("This laboratory record is already voided");
  v.setVoidReason(r.reason().trim());v.setVoidedBy(actor);v.setVoidedAt(Instant.now());touch(c,actor);labs.saveAndFlush(v);
  audit.logCaseEvent(id,"LAB_RESULT_VOIDED",actor,"SUCCESS","Laboratory record #"+v.getId()+" voided: "+r.reason().trim());return LabResultDto.from(v);
 }
}
