package com.pharmasafe.service;

import com.pharmasafe.dto.*;
import com.pharmasafe.entity.*;
import com.pharmasafe.repository.*;
import com.pharmasafe.security.WorkspaceAccess;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.Set;

@Service
public class CaseFollowUpService {
 private final SafetyCaseRepository cases; private final CaseFollowUpRepository receipts;
 private final UserRepository users; private final CaseActionItemRepository actions;
 private final CaseReviewEventRepository reviews; private final AuditService audit;
 private final PasswordEncoder passwords; private final CaseClosureGuard closure;
 public CaseFollowUpService(SafetyCaseRepository cases,CaseFollowUpRepository receipts,UserRepository users,
  CaseActionItemRepository actions,CaseReviewEventRepository reviews,AuditService audit,PasswordEncoder passwords,CaseClosureGuard closure) {
  this.cases=cases;this.receipts=receipts;this.users=users;this.actions=actions;this.reviews=reviews;this.audit=audit;this.passwords=passwords;this.closure=closure;
 }
 private SafetyCase read(Long id,String username,String role,boolean lock) {
  SafetyCase c=(lock?cases.findForUpdate(id):cases.findById(id)).orElseThrow(()->new EntityNotFoundException("Case not found"));
  if(!new WorkspaceAccess().canRead(c,username,role))throw new AccessDeniedException("You are not authorized to access this case");
  return c;
 }
 private User actor(String username) { return users.findByUsername(username).orElseThrow(()->new AccessDeniedException("Account not found")); }
 private void revision(SafetyCase c,Long expected) {
  if(expected==null||expected.longValue()!=c.getRowVersion())throw new org.springframework.orm.ObjectOptimisticLockingFailureException(SafetyCase.class,c.getId());
 }
 @Transactional(readOnly=true)
 public Page<FollowUpResponse> list(Long id,String username,String role,Pageable page) {
  read(id,username,role,false);return receipts.findBySafetyCaseId(id,page).map(FollowUpResponse::from);
 }
 @Transactional(readOnly=true)
 public CaseClosureGuard.Readiness readiness(Long id,String username,String role) {return closure.check(read(id,username,role,false));}
 @Transactional
 public FollowUpResponse record(Long id,FollowUpRequest request,String username,String role) {
  SafetyCase c=read(id,username,role,true);revision(c,request.expectedRowVersion());
  if(c.isLocked()||!Set.of("Draft","Data Entry").contains(c.getWorkflowState()))throw new IllegalStateException("Reopen or return this case to data entry before recording information");
  if(request.receivedDate().isBefore(c.getInitialReceiptDate()))throw new IllegalArgumentException("Receipt date cannot precede the initial receipt date");
  if(request.safetyReceivedDate()!=null&&request.safetyReceivedDate().isBefore(request.receivedDate()))throw new IllegalArgumentException("Safety receipt date cannot precede receipt date");
  if("AMENDMENT".equals(request.kind())&&(request.safetyReceivedDate()!=null||request.relatedActionItemId()!=null))throw new IllegalArgumentException("Amendments cannot reset receipt dates or complete follow-up requests");
  User user=actor(username);CaseActionItem action=null;
  if(request.relatedActionItemId()!=null) {
   action=actions.findById(request.relatedActionItemId()).orElseThrow(()->new EntityNotFoundException("Follow-up request not found"));
   if(!action.getSafetyCase().getId().equals(id)||!"FOLLOW_UP".equals(action.getActionCode())||Set.of("COMPLETED","CANCELLED").contains(action.getStatus()))throw new IllegalArgumentException("Select an open follow-up request for this case");
   if(!Set.of("ROLE_ADMIN","ROLE_SAFETY_MANAGER").contains(role)&&!action.getAssignedUser().getId().equals(user.getId()))throw new AccessDeniedException("Only the assignee or manager can complete this request");
   action.setStatus("COMPLETED");action.setCompletedAt(Instant.now());action.setCompletedByUser(user);
  }
  CaseFollowUp f=new CaseFollowUp();f.setSafetyCase(c);f.setFollowUpNumber(receipts.maxNumber(id)+1);f.setKind(request.kind());
  f.setReceivedDate(request.receivedDate());f.setSafetyReceivedDate(request.safetyReceivedDate());f.setSignificant(request.significant());
  f.setJustification(request.justification().trim());f.setSummary(request.summary().trim());f.setSourceReference(request.sourceReference());
  f.setPreviousCaseVersion(c.getVersionNumber());c.setVersionNumber(c.getVersionNumber()+1);f.setCaseVersion(c.getVersionNumber());
  f.setRelatedActionItem(action);f.setCreatedByUser(user);f.setCreatedByUsername(username);
  if("FOLLOW_UP".equals(request.kind())&&(c.getLatestReceivedDate()==null||request.receivedDate().isAfter(c.getLatestReceivedDate())))c.setLatestReceivedDate(request.receivedDate());
  c.setUpdatedByUser(user);cases.saveAndFlush(c);receipts.saveAndFlush(f);
  audit.logCaseEvent(id,"CASE_"+request.kind()+"_RECORDED",username,"SUCCESS","Receipt #"+f.getFollowUpNumber()+"; case version "+f.getCaseVersion());
  return FollowUpResponse.from(f);
 }
 @Transactional
 public CaseResponseDto reopen(Long id,ReopenCaseRequest request,String username,String role) {
  SafetyCase c=read(id,username,role,true);revision(c,request.expectedRowVersion());
  if(!Set.of("Approved","Locked","Closed").contains(c.getWorkflowState()))throw new IllegalStateException("Only approved, locked or closed cases can be reopened");
  User user=actor(username);
  if(!passwords.matches(request.password(),user.getPasswordHash()))throw new IllegalArgumentException("Password confirmation failed");
  User target=users.findById(request.assignedUserId()).orElseThrow(()->new IllegalArgumentException("Select an active case processor"));
  if(!target.isEnabled()||target.isLocked()||!Set.of("ROLE_ADMIN","ROLE_CASE_PROCESSOR","ROLE_USER").contains(target.getRole()))throw new IllegalArgumentException("Select an active case processor");
  String previous=c.getWorkflowState();c.setWorkflowState("Data Entry");c.setLocked(false);c.setLockedAt(null);c.setLockedByUser(null);
  c.setClosedAt(null);c.setClosedByUser(null);c.setAssignedToUser(target);c.setUpdatedByUser(user);cases.saveAndFlush(c);
  CaseActionItem action=new CaseActionItem();action.setSafetyCase(c);action.setActionCode("FOLLOW_UP");action.setDescription(request.reason().trim());
  action.setAssignedUser(target);action.setCreatedByUser(user);action.setDueAt(request.dueAt());actions.save(action);
  CaseReviewEvent review=new CaseReviewEvent();review.setSafetyCase(c);review.setReviewerUser(user);review.setReviewerUsername(username);
  review.setFromState(previous);review.setToState("Data Entry");review.setOutcome("REOPENED");review.setComments(request.reason().trim());reviews.save(review);
  audit.logCaseEvent(id,"CASE_REOPENED",username,"SUCCESS","From "+previous+"; assigned to "+target.getUsername()+"; reason: "+request.reason().trim());
  return CaseResponseDto.fromEntity(c);
 }
}
