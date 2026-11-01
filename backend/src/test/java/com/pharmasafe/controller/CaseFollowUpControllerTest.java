package com.pharmasafe.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pharmasafe.entity.*;
import com.pharmasafe.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
@WithMockUser(username="follow-admin",roles="ADMIN")
class CaseFollowUpControllerTest {
 @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired SafetyCaseRepository cases;
 @Autowired UserRepository users; @Autowired CaseFollowUpRepository receipts; @Autowired CaseActionItemRepository actions;
 @Autowired SafetyReportRepository reports; @Autowired PasswordEncoder encoder;
 private SafetyCase record; private User admin,processor;
 @BeforeEach void setup(){
  admin=users.save(new User("follow-admin",encoder.encode("Synthetic-test-password!1"),"ROLE_ADMIN"));
  processor=users.save(new User("follow-processor",encoder.encode("Synthetic-test-password!1"),"ROLE_CASE_PROCESSOR"));
  record=create("FOLLOW-CASE",admin);
 }
 SafetyCase create(String number,User owner){
  SafetyCase c=new SafetyCase();c.setCaseNumber(number);c.setCountryCode("USA");c.setReportType("Spontaneous");
  c.setInitialReceiptDate(LocalDate.of(2026,10,1));c.setLatestReceivedDate(LocalDate.of(2026,10,1));c.setWorkflowState("Data Entry");c.setVersionNumber(1);c.setCreatedByUser(owner);c.setAssignedToUser(owner);
  CasePatient p=new CasePatient();p.setPatientIdentifier("SYNTHETIC-IDENTIFIER");c.setPatient(p);return cases.saveAndFlush(c);
 }
 Map<String,Object> receipt(){return new HashMap<>(Map.of("expectedRowVersion",record.getRowVersion(),"kind","FOLLOW_UP","receivedDate","2026-10-05","significant",true,"justification","New clinical information","summary","Synthetic follow-up details"));}
 void postReceipt(Map<String,Object> body,int status)throws Exception{mvc.perform(post("/api/cases/"+record.getId()+"/follow-ups").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().is(status));}
 @Test void recordsNumberedReceiptAndPreservesCaseIdentity()throws Exception{
  postReceipt(receipt(),200);assertEquals(1,receipts.count());assertEquals("FOLLOW-CASE",record.getCaseNumber());assertEquals(2,record.getVersionNumber());assertEquals(LocalDate.of(2026,10,5),record.getLatestReceivedDate());
  mvc.perform(get("/api/cases/"+record.getId()+"/follow-ups")).andExpect(status().isOk()).andExpect(jsonPath("$.content[0].followUpNumber").value(1)).andExpect(jsonPath("$.content[0].createdByUsername").value("follow-admin"));
 }
 @Test void staleReceiptIsRejectedWithoutExtraRow()throws Exception{var body=receipt();postReceipt(body,200);postReceipt(body,409);assertEquals(1,receipts.count());}
 @Test void amendmentDoesNotChangeReceiptClock()throws Exception{var body=receipt();body.put("kind","AMENDMENT");postReceipt(body,200);assertEquals(LocalDate.of(2026,10,1),record.getLatestReceivedDate());}
 @Test void amendmentCannotResetSafetyReceipt()throws Exception{var body=receipt();body.put("kind","AMENDMENT");body.put("safetyReceivedDate","2026-10-06");postReceipt(body,400);assertEquals(0,receipts.count());}
 @Test void receiptCannotPrecedeInitialDate()throws Exception{var body=receipt();body.put("receivedDate","2026-09-01");postReceipt(body,400);}
 @Test void cannotAttachOtherCaseTask()throws Exception{
  var other=create("OTHER-FOLLOW-CASE",admin);var task=task(other);var body=receipt();body.put("relatedActionItemId",task.getId());postReceipt(body,400);assertEquals("OPEN",task.getStatus());
 }
 CaseActionItem task(SafetyCase c){var t=new CaseActionItem();t.setSafetyCase(c);t.setAssignedUser(admin);t.setCreatedByUser(admin);t.setDueAt(Instant.now().plusSeconds(3600));t.setDescription("Follow up");t.setActionCode("FOLLOW_UP");return actions.saveAndFlush(t);}
 @Test void receiptCompletesLinkedRequestButGenericCompletionCannot()throws Exception{
  var task=task(record);mvc.perform(post("/api/action-items/"+task.getId()+"/complete").with(csrf())).andExpect(status().isBadRequest());
  var body=receipt();body.put("relatedActionItemId",task.getId());postReceipt(body,200);assertEquals("COMPLETED",task.getStatus());assertNotNull(task.getCompletedAt());
 }
 @Test void closureRequiresOpenTasksAndPendingReportsToBeResolved()throws Exception{
  record.setWorkflowState("Locked");record.setLocked(true);cases.saveAndFlush(record);var task=task(record);
  var report=new SafetyReport();report.setReportId("FOLLOW-REPORT");report.setReportType("15-day");report.setProduct("Synthetic");report.setDueDate(LocalDate.now().plusDays(10));report.setRelatedCases(new ArrayList<>(List.of(record)));reports.saveAndFlush(report);
  mvc.perform(get("/api/cases/"+record.getId()+"/closure-readiness")).andExpect(jsonPath("$.canClose").value(false)).andExpect(jsonPath("$.openActionItems").value(1)).andExpect(jsonPath("$.pendingExpeditedReports").value(1));
  mvc.perform(post("/api/cases/"+record.getId()+"/workflow-transition").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"targetState\":\"Closed\",\"comments\":\"Reviewed\"}")).andExpect(status().isBadRequest());
  task.setStatus("COMPLETED");actions.saveAndFlush(task);report.setStatus("Submitted");report.setSubmittedDate(Instant.now());reports.saveAndFlush(report);
  mvc.perform(post("/api/cases/"+record.getId()+"/workflow-transition").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"targetState\":\"Closed\",\"comments\":\"Reviewed\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.locked").value(false)).andExpect(jsonPath("$.closedByUsername").value("follow-admin"));
 }
 @Test void reopeningRequiresPasswordAndCreatesAssignedFollowUp()throws Exception{
  record.setWorkflowState("Closed");cases.saveAndFlush(record);var body=new HashMap<String,Object>(Map.of("expectedRowVersion",record.getRowVersion(),"reason","Additional information requested","assignedUserId",processor.getId(),"dueAt",Instant.now().plusSeconds(86400).toString(),"password","Wrong-password"));
  mvc.perform(post("/api/cases/"+record.getId()+"/reopen").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().isBadRequest());
  body.put("password","Synthetic-test-password!1");mvc.perform(post("/api/cases/"+record.getId()+"/reopen").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().isOk()).andExpect(jsonPath("$.workflowState").value("Data Entry")).andExpect(jsonPath("$.assignedToUsername").value("follow-processor"));
  assertEquals(1,actions.countBySafetyCaseIdAndStatusNotIn(record.getId(),List.of("COMPLETED","CANCELLED")));
 }
 @Test void duplicateCheckIsExactAndScopeRestricted()throws Exception{
  create("OWN-MATCH",processor);
  mvc.perform(post("/api/cases/duplicate-check").with(user("follow-processor").roles("CASE_PROCESSOR")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"patientIdentifier\":\" synthetic-identifier \"}")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].caseNumber").value("OWN-MATCH"));
 }
 @Test void auditorCannotWriteAndReceiptRequiresCsrf()throws Exception{
  mvc.perform(post("/api/cases/"+record.getId()+"/follow-ups").with(user("follow-admin").roles("AUDITOR")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(receipt()))).andExpect(status().isForbidden());
  mvc.perform(post("/api/cases/"+record.getId()+"/follow-ups").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(receipt()))).andExpect(status().isForbidden());
 }
}
