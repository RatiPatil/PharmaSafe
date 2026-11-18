package com.pharmasafe.service;

import com.pharmasafe.dto.AccessRegistrationDto;
import com.pharmasafe.entity.*;
import com.pharmasafe.repository.*;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

@Service
public class UserAccessService {
 @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager em;
 @org.springframework.beans.factory.annotation.Autowired private com.pharmasafe.administration.AdminScope adminScope;
 @org.springframework.beans.factory.annotation.Autowired private com.pharmasafe.administration.AdministrativeAudit adminAudit;
 @org.springframework.beans.factory.annotation.Autowired private com.pharmasafe.processing.ProcessingTeamRepository processingTeams;
 @org.springframework.beans.factory.annotation.Autowired private com.pharmasafe.processing.ProcessingMemberRepository processingMembers;
 @org.springframework.beans.factory.annotation.Autowired private com.pharmasafe.medicalteam.MedicalTeamRepository medicalTeams;
 @org.springframework.beans.factory.annotation.Autowired private com.pharmasafe.medicalteam.MedicalMemberRepository medicalMembers;
 private final UserRepository users;
 private final RegistrationRequestRepository registrations;
 private final PasswordResetRequestRepository resets;
 private final PasswordEncoder encoder;
 private final AccessRoleCatalog catalog;
 private final AuditService audit;
 private final AccessMailService mail;
 public UserAccessService(UserRepository users,RegistrationRequestRepository registrations,PasswordResetRequestRepository resets,
   PasswordEncoder encoder,AccessRoleCatalog catalog,AuditService audit,AccessMailService mail){
  this.users=users;this.registrations=registrations;this.resets=resets;this.encoder=encoder;this.catalog=catalog;this.audit=audit;this.mail=mail;
 }
 public static void validatePassword(String value){
  if(value==null||value.length()<12||value.getBytes(StandardCharsets.UTF_8).length>72||!value.matches("(?s).*[A-Za-z].*")||!value.matches("(?s).*[^A-Za-z].*"))
   throw new IllegalArgumentException("Use 12–72 bytes with letters and a number or symbol");
 }
 @Transactional
 public Map<String,Object> register(AccessRegistrationDto d){
  String role=catalog.verify(d.selectionToken()); validatePassword(d.password());
  if(!d.password().equals(d.confirmPassword()))throw new IllegalArgumentException("Passwords do not match");
  var fields=AccessRoleCatalog.FIELDS.get(role);
  if(!fields.containsAll(d.details().keySet())||fields.stream().anyMatch(k->d.details().get(k)==null||d.details().get(k).isBlank()||d.details().get(k).length()>1000))
   throw new IllegalArgumentException("Complete the required details for your selected role");
  String username=d.username().trim().toLowerCase(Locale.ROOT),email=d.email().trim().toLowerCase(Locale.ROOT),employee=d.employeeId().trim().toLowerCase(Locale.ROOT);
  if(users.existsByUsername(username)||users.existsByEmailIgnoreCase(email)||users.existsByEmployeeIdIgnoreCase(employee))
   throw new IllegalArgumentException("Username, email or employee ID is already registered. Contact your administrator.");
  com.pharmasafe.processing.ProcessingTeam processingTeam=null;
  com.pharmasafe.medicalteam.MedicalTeam medicalTeam=null;
  if(d.teamRegistrationKey()!=null&&role.equals("ROLE_MEDICAL_REVIEWER")){
   var invited=medicalTeams.findByRegistrationKey(d.teamRegistrationKey()).orElseThrow(()->new IllegalArgumentException("Medical team registration link is invalid"));
   medicalTeam=medicalTeams.lockById(invited.id).orElseThrow();
   if(!medicalTeam.active||!medicalTeam.registrationOpen||!medicalTeam.lead.isEnabled()||!medicalTeam.lead.isApproved()||medicalTeam.lead.isLocked())throw new IllegalArgumentException("Medical team registration is closed");
   if(medicalMembers.countByTeamIdAndStatusIn(medicalTeam.id,List.of("PENDING","ACTIVE"))>=medicalTeam.maxMembers)throw new IllegalArgumentException("This medical team has no remaining employee seats");
  }
  if(d.teamRegistrationKey()!=null&&!role.equals("ROLE_MEDICAL_REVIEWER")){
   if(!role.equals("ROLE_CASE_PROCESSOR"))throw new IllegalArgumentException("Team registration is only available for Case Processing employees");
   var invited=processingTeams.findByRegistrationKey(d.teamRegistrationKey()).orElseThrow(()->new IllegalArgumentException("Team registration link is invalid"));
   processingTeam=processingTeams.lockById(invited.id).orElseThrow();
   if(!processingTeam.active||!processingTeam.registrationOpen||!processingTeam.lead.isEnabled()||!processingTeam.lead.isApproved()||processingTeam.lead.isLocked())throw new IllegalArgumentException("Team registration is closed");
   if(processingMembers.countByTeamIdAndStatusIn(processingTeam.id,List.of("PENDING","ACTIVE"))>=processingTeam.maxMembers)throw new IllegalArgumentException("This team has no remaining employee seats");
  }
  var u=new User(username,encoder.encode(d.password()),role);u.setEnabled(false);u.setAccountStatus("PENDING");
  u.setFullName(d.fullName().trim());u.setEmail(email);u.setEmployeeId(employee);u.setMobileNumber(d.mobileNumber().trim());
  u.setDepartment(d.department().trim());u.setDesignation(d.designation().trim());u.setOrganization(d.organization().trim());
  users.saveAndFlush(u);
  var r=new RegistrationRequest();r.processingTeam=processingTeam;r.medicalTeam=medicalTeam;r.user=u;r.requestedRole=role;r.reason=d.reason().trim();r.additionalInformation=d.additionalInformation();r.details.putAll(d.details());registrations.saveAndFlush(r);
  if(processingTeam!=null){var member=new com.pharmasafe.processing.ProcessingMember();member.team=processingTeam;member.user=u;member.status="PENDING";member.joinedAt=Instant.now();processingMembers.saveAndFlush(member);}
  if(medicalTeam!=null){var member=new com.pharmasafe.medicalteam.MedicalMember();member.team=medicalTeam;member.user=u;member.status="PENDING";member.joinedAt=Instant.now();medicalMembers.saveAndFlush(member);}
  audit.logAccountEvent("REGISTRATION_SUBMITTED",username,"userId="+u.getId()+"; requestId="+r.id+"; requestedRole="+role);
  return Map.of("requestId",r.id,"status","PENDING","message","Request received. An administrator must approve your account before you can sign in.");
 }
 private User admin(String name){return users.findByUsername(name).orElseThrow(()->new EntityNotFoundException("Administrator not found"));}
 private String reason(String reason){if(reason==null||reason.isBlank()||reason.length()>2000)throw new IllegalArgumentException("A reason of 1–2000 characters is required");return reason.trim();}
 private void audit(String action,String actor,User user,Long requestId,String reason){audit.logAccountEvent(action,actor,"userId="+user.getId()+"; requestId="+requestId+"; reason="+reason);}
 @Transactional
 public Map<String,Object> decideRegistration(Long id,String decision,String reason,String actor){
  var candidate=registrations.findById(id).orElseThrow(()->new EntityNotFoundException("Request not found"));if(candidate.processingTeam!=null)processingTeams.lockById(candidate.processingTeam.id).orElseThrow();if(candidate.medicalTeam!=null)medicalTeams.lockById(candidate.medicalTeam.id).orElseThrow();adminScope.mutationTarget(actor,candidate.user);
  var r=registrations.lockById(id).orElseThrow(()->new EntityNotFoundException("Request not found"));
  em.refresh(r);var u=users.lockById(r.user.getId()).orElseThrow();em.refresh(u);
  adminScope.target(actor,u);
  if(r.reviewedAt!=null)throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"This request has already been processed. Refresh the list.");
  if(decision.equals("approve")&&r.processingTeam!=null&&!r.processingTeam.active)throw new IllegalArgumentException("Activate the team before approving its employees");
  if(decision.equals("approve")&&r.medicalTeam!=null&&!r.medicalTeam.active)throw new IllegalArgumentException("Activate the medical team before approving its employees");
  if(!"PENDING".equals(u.getAccountStatus()))throw new IllegalArgumentException("Only pending requests can be approved or rejected");
  if(!Set.of("approve","reject").contains(decision))throw new IllegalArgumentException("Unsupported decision");
  String why=reason(reason);User a=admin(actor);r.reviewedBy=a;r.reviewedAt=Instant.now();r.decisionReason=why;
  u.setAccountStatus(decision.equals("approve")?"APPROVED":"REJECTED");u.setEnabled(decision.equals("approve"));
  if(decision.equals("approve")){u.setRole(r.requestedRole);u.setApprovedBy(a.getId());u.setApprovedAt(r.reviewedAt);}
  adminAudit.record(a,r.requestedRole,u,r.id,"REGISTRATION_"+decision.toUpperCase(),"PENDING",u.getAccountStatus(),why,null);
  users.saveAndFlush(u);
  if(r.processingTeam!=null){var member=processingMembers.findByTeamIdAndUserId(r.processingTeam.id,u.getId()).orElseThrow();member.status=decision.equals("approve")?"ACTIVE":"REJECTED";processingMembers.saveAndFlush(member);}
  if(r.medicalTeam!=null){var member=medicalMembers.findByTeamIdAndUserId(r.medicalTeam.id,u.getId()).orElseThrow();member.status=decision.equals("approve")?"ACTIVE":"REJECTED";medicalMembers.saveAndFlush(member);}
  u.revokeSessions();audit(decision.equals("approve")?"REGISTRATION_APPROVED":"REGISTRATION_REJECTED",actor,u,r.id,why);
  return registrationView(r);
 }
 @Transactional
 public Map<String,Object> accountStatus(Long id,String action,String reason,String actor){
  var candidate=users.findById(id).orElseThrow(()->new EntityNotFoundException("User not found"));adminScope.mutationTarget(actor,candidate);
  var u=users.lockById(id).orElseThrow(()->new EntityNotFoundException("User not found"));em.refresh(u);
  adminScope.target(actor,u);String previousStatus=u.getAccountStatus();
  if(u.getUsername().equals(actor)||Set.of("ROLE_ADMIN","ROLE_SCOPED_ADMIN").contains(u.getRole()))throw new IllegalArgumentException("Administrator access must be managed through the existing controlled administrator process");
  String why=reason(reason);
  if(action.equals("suspend")&&u.isApproved()){u.setAccountStatus("SUSPENDED");u.setEnabled(false);}
  else if(action.equals("activate")&&"SUSPENDED".equals(u.getAccountStatus())){u.setAccountStatus("APPROVED");u.setEnabled(true);}
  else throw new IllegalArgumentException("Only approved accounts can be suspended and suspended accounts activated");
  adminAudit.record(admin(actor),u.getRole(),u,null,"ACCOUNT_"+action.toUpperCase(),previousStatus,u.getAccountStatus(),why,null);
  u.revokeSessions();audit(action.equals("suspend")?"ACCOUNT_SUSPENDED":"ACCOUNT_ACTIVATED",actor,u,null,why);return userView(u);
 }
 @Transactional
 public void forgot(String identifier){
  String value=identifier.trim().toLowerCase(Locale.ROOT);
  var found=value.contains("@")?users.findByEmailIgnoreCase(value):users.findByUsername(value);
  if(found.isEmpty())return;
  var u=users.lockById(found.get().getId()).orElseThrow();
  if(!u.isApproved()||!u.isEnabled()||u.getEmail()==null||resets.countByUserIdAndCreatedAtAfter(u.getId(),Instant.now().minusSeconds(3600))>=3)return;
  var r=new PasswordResetRequest();r.user=u;resets.saveAndFlush(r);audit("PASSWORD_RESET_REQUESTED",u.getUsername(),u,r.id,"Recovery requested");
 }
 @Transactional
 public Map<String,Object> decideReset(Long id,String decision,String reason,String actor){
  var candidate=resets.findById(id).orElseThrow(()->new EntityNotFoundException("Reset request not found"));
  adminScope.mutationTarget(actor,candidate.user);
  var u=users.lockById(candidate.user.getId()).orElseThrow();em.refresh(u);
  var r=resets.lockById(id).orElseThrow();em.refresh(r);
  adminScope.target(actor,u);
  if(!r.status.equals("PENDING"))throw new IllegalArgumentException("Only pending requests can be reviewed");
  if(!Set.of("approve","reject").contains(decision))throw new IllegalArgumentException("Unsupported decision");
  String why=reason(reason);
  if(decision.equals("approve")){
   if(!u.isApproved()||!u.isEnabled())throw new IllegalArgumentException("Only approved, enabled accounts can reset a password");
   byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
   r.tokenHash=hash(token);r.issuedAuthVersion=u.getAuthVersion();r.expiresAt=Instant.now().plusSeconds(1800);mail.sendReset(u.getEmail(),token);
  }
  r.status=decision.equals("approve")?"APPROVED":"REJECTED";r.reviewedBy=admin(actor);r.reviewedAt=Instant.now();r.decisionReason=why;
  adminAudit.record(admin(actor),u.getRole(),u,r.id,"RESET_"+decision.toUpperCase(),"PENDING",r.status,why,null);
  audit(decision.equals("approve")?"PASSWORD_RESET_APPROVED":"PASSWORD_RESET_REJECTED",actor,u,r.id,why);return resetView(r);
 }
 public static String hash(String token){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException("Hash unavailable");}}
 @Transactional
 public void reset(String token,String password,String confirm){
  validatePassword(password);if(!password.equals(confirm))throw new IllegalArgumentException("Passwords do not match");
  var candidate=resets.findByTokenHash(hash(token)).orElseThrow(()->new IllegalArgumentException("Reset link is invalid or expired"));
  var u=users.lockById(candidate.user.getId()).orElseThrow();em.refresh(u);var r=resets.lockById(candidate.id).orElseThrow();em.refresh(r);
  if(!"APPROVED".equals(r.status)||r.tokenHash==null||r.issuedAuthVersion==null||r.issuedAuthVersion!=u.getAuthVersion()||r.expiresAt==null||!r.expiresAt.isAfter(Instant.now())||!u.isApproved()||!u.isEnabled())throw new IllegalArgumentException("Reset link is invalid or expired");
  // Invalidate all other issued links when this password changes.
  u.setPasswordHash(encoder.encode(password));u.revokeSessions();u.setFailedLoginAttempts(0);u.setLocked(false);u.setLockedAt(null);
  r.status="USED";r.usedAt=Instant.now();r.tokenHash=null;
  for(var other:resets.findAll((root,q,cb)->cb.and(cb.equal(root.get("user").get("id"),u.getId()),cb.equal(root.get("status"),"APPROVED")))){
   if(!other.id.equals(r.id)){other.status="REJECTED";other.tokenHash=null;other.decisionReason="Invalidated by a later password change";}
  }
  audit("PASSWORD_CHANGED",u.getUsername(),u,r.id,"Single-use recovery completed; sessions revoked");
 }
 public static Map<String,Object> userView(User u){
  Map<String,Object> m=new LinkedHashMap<>();m.put("staffCode",u.getStaffCode());m.put("userId",u.getId());m.put("createdAt",u.getCreatedAt());m.put("mobileNumber",u.getMobileNumber());m.put("designation",u.getDesignation());m.put("approvedBy",u.getApprovedBy());m.put("approvedAt",u.getApprovedAt());m.put("username",u.getUsername());m.put("fullName",u.getFullName());m.put("email",u.getEmail());m.put("employeeId",u.getEmployeeId());m.put("department",u.getDepartment());m.put("organization",u.getOrganization());m.put("role",u.getRole());m.put("accountStatus",u.getAccountStatus());m.put("enabled",u.isEnabled());return m;
 }
 public static Map<String,Object> registrationView(RegistrationRequest r){
  var m=userView(r.user);m.put("teamId",r.medicalTeam!=null?r.medicalTeam.id:r.processingTeam==null?null:r.processingTeam.id);m.put("teamCode",r.medicalTeam!=null?r.medicalTeam.code():r.processingTeam==null?null:r.processingTeam.code());m.put("id",r.id);m.put("requestedRole",r.requestedRole);m.put("reason",r.reason);m.put("additionalInformation",r.additionalInformation);m.put("details",r.details);m.put("mobileNumber",r.user.getMobileNumber());m.put("designation",r.user.getDesignation());m.put("createdAt",r.createdAt);review(m,r.reviewedBy,r.reviewedAt,r.decisionReason);return m;
 }
 public static Map<String,Object> resetView(PasswordResetRequest r){var m=userView(r.user);m.put("id",r.id);m.put("status",r.status);m.put("createdAt",r.createdAt);m.put("expiresAt",r.expiresAt);review(m,r.reviewedBy,r.reviewedAt,r.decisionReason);return m;}
 private static void review(Map<String,Object> m,User reviewer,Instant at,String reason){m.put("reviewedBy",reviewer==null?null:reviewer.getId());m.put("reviewedByUsername",reviewer==null?null:reviewer.getUsername());m.put("reviewedByStaffCode",reviewer==null?null:reviewer.getStaffCode());m.put("reviewedAt",at);m.put("decisionReason",reason);}
 @Transactional(readOnly=true) public Map<String,Object> accountDetails(Long id){
  User u=users.findById(id).orElseThrow(()->new EntityNotFoundException("User not found"));
  return registrations.findByUserId(id).map(UserAccessService::registrationView).orElseGet(()->userView(u));
 }
 @Transactional(readOnly=true) public Map<String,Object> details(Long id){return registrationView(registrations.findById(id).orElseThrow(()->new EntityNotFoundException("Request not found")));}
 @Transactional(readOnly=true)
 public Page<Map<String,Object>> registrations(String role,String status,String department,String organization,String search,LocalDate from,LocalDate to,int page,int size,String sort,String direction){
  Specification<RegistrationRequest> spec=(root,q,cb)->{
   var u=root.get("user");var p=new ArrayList<jakarta.persistence.criteria.Predicate>();
   if(!role.isBlank())p.add(cb.equal(root.get("requestedRole"),role));if(!status.isBlank())p.add(cb.equal(u.get("accountStatus"),status));
   if(!department.isBlank())p.add(cb.equal(cb.lower(u.get("department")),department.toLowerCase(Locale.ROOT)));if(!organization.isBlank())p.add(cb.equal(cb.lower(u.get("organization")),organization.toLowerCase(Locale.ROOT)));
   if(!search.isBlank()){String term="%"+search.toLowerCase(Locale.ROOT).replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";p.add(cb.or(search.matches("(?i)PS-[0-9]{1,18}")?cb.equal(u.get("id"),Long.parseLong(search.substring(3))):cb.disjunction(),cb.like(cb.lower(u.get("username")),term,'\\'),cb.like(cb.lower(u.get("staffCode")),term,'\\'),cb.like(cb.lower(u.get("fullName")),term,'\\'),cb.like(cb.lower(u.get("employeeId")),term,'\\'),cb.like(cb.lower(u.get("email")),term,'\\')));}
   if(from!=null)p.add(cb.greaterThanOrEqualTo(root.get("createdAt"),from.atStartOfDay(ZoneOffset.UTC).toInstant()));if(to!=null)p.add(cb.lessThan(root.get("createdAt"),to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()));
   return cb.and(p.toArray(new jakarta.persistence.criteria.Predicate[0]));
  };
  String column=Map.of("createdAt","createdAt","fullName","user.fullName","role","requestedRole","status","user.accountStatus").getOrDefault(sort,"createdAt");
  return registrations.findAll(spec,PageRequest.of(Math.max(0,page),Math.max(1,Math.min(50,size)),Sort.by("asc".equalsIgnoreCase(direction)?Sort.Direction.ASC:Sort.Direction.DESC,column).and(Sort.by("id")))).map(UserAccessService::registrationView);
 }
 @Transactional(readOnly=true) public Page<Map<String,Object>> resetRequests(int page,int size,String status){return resets.findAll((root,q,cb)->status.isBlank()?cb.conjunction():cb.equal(root.get("status"),status),PageRequest.of(Math.max(0,page),Math.max(1,Math.min(50,size)),Sort.by(Sort.Direction.DESC,"createdAt","id"))).map(UserAccessService::resetView);}
 @Transactional(readOnly=true) public Page<Map<String,Object>> accounts(String role,String status,String department,String organization,String search,int page,int size){
  return users.findAll((root,q,cb)->{
   var p=new ArrayList<jakarta.persistence.criteria.Predicate>();
   for(var entry:Map.of("role",role,"accountStatus",status,"department",department,"organization",organization).entrySet())if(!entry.getValue().isBlank())p.add(cb.equal(root.get(entry.getKey()),entry.getValue()));
   if(!search.isBlank()){String term="%"+search.toLowerCase(Locale.ROOT)+"%";p.add(cb.or(cb.like(cb.lower(root.get("fullName")),term),cb.like(cb.lower(root.get("employeeId")),term),cb.like(cb.lower(root.get("email")),term)));}
   return cb.and(p.toArray(new jakarta.persistence.criteria.Predicate[0]));
  },PageRequest.of(Math.max(0,page),Math.max(1,Math.min(50,size)),Sort.by("username","id"))).map(UserAccessService::userView);
 }
}
