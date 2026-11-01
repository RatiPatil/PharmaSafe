package com.pharmasafe.controller;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pharmasafe.entity.*;
import com.pharmasafe.repository.*;
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
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
@WithMockUser(username="lab-admin",roles="ADMIN")
class CaseLabControllerTest {
 @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired SafetyCaseRepository cases; @Autowired UserRepository users;
 @Autowired CaseLabResultRepository labs; @Autowired AuditEventRepository audits; @Autowired CaseDocumentExportRepository documents;
 SafetyCase record;
 @BeforeEach void setup(){User actor=users.save(new User("lab-admin","synthetic-hash","ROLE_ADMIN"));record=new SafetyCase();record.setCaseNumber("LAB-CASE");record.setCountryCode("USA");record.setReportType("Spontaneous");record.setInitialReceiptDate(LocalDate.now());record.setLatestReceivedDate(LocalDate.now());record.setWorkflowState("Data Entry");record.setCreatedByUser(actor);record=cases.saveAndFlush(record);}
 String base(){return "/api/cases/"+record.getId()+"/laboratory";}
 Map<String,Object> body(){Map<String,Object> r=new HashMap<>();r.put("expectedRowVersion",record.getRowVersion());r.put("testName","ALT");r.put("testDate",LocalDate.now().toString());r.put("resultText","120");r.put("unit","U/L");r.put("normalLow",5);r.put("normalHigh",40);r.put("interpretation","HIGH");r.put("notes","Synthetic evidence");return r;}
 long add()throws Exception{return json.readTree(mvc.perform(post(base()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body()))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("id").asLong();}
 @Test void roundTripAuditAndPdfRetainVoidedEvidence()throws Exception{
  long before=record.getRowVersion();long id=add();assertTrue(record.getRowVersion()>before);assertEquals(2,record.getVersionNumber());
  mvc.perform(get(base())).andExpect(status().isOk()).andExpect(jsonPath("$[0].testName").value("ALT")).andExpect(jsonPath("$[0].normalHigh").value(40));
  mvc.perform(post(base()+"/"+id+"/void").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("expectedRowVersion",record.getRowVersion(),"reason","Incorrect specimen; new sample requested")))).andExpect(status().isOk()).andExpect(jsonPath("$.voidedBy").value("lab-admin"));
  assertEquals("120",labs.findById(id).orElseThrow().getResultText());assertEquals(1,labs.countBySafetyCaseId(record.getId()));
  assertTrue(audits.findBySafetyCaseIdOrderByCreatedAtDesc(record.getId()).stream().anyMatch(a->"LAB_RESULT_VOIDED".equals(a.getEventType())));
  String response=mvc.perform(post("/api/cases/"+record.getId()+"/documents").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("expectedRowVersion",record.getRowVersion(),"documentKind","CASE_FORM","finalDocument",false)))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
  var snapshot=json.readTree(documents.findById(json.readTree(response).path("id").asText()).orElseThrow().getSnapshotJson());assertEquals("120",snapshot.path("laboratoryEvidence").get(0).path("resultText").asText());assertFalse(snapshot.path("laboratoryEvidence").get(0).path("voidedAt").isNull());
 }
 @Test void rejectsBadRangeFutureDateAndMissingCsrf()throws Exception{
  var b=body();b.put("normalLow",80);mvc.perform(post(base()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(b))).andExpect(status().isBadRequest());
  b=body();b.put("testDate",LocalDate.now().plusDays(1).toString());mvc.perform(post(base()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(b))).andExpect(status().isBadRequest());
  mvc.perform(post(base()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body()))).andExpect(status().isForbidden());assertEquals(0,labs.count());
 }
 @Test void rejectsStaleRevisionAndLockedCase()throws Exception{
  var b=body();b.put("expectedRowVersion",999);mvc.perform(post(base()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(b))).andExpect(status().isConflict());
  record.setLocked(true);cases.saveAndFlush(record);mvc.perform(post(base()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body()))).andExpect(status().isBadRequest());assertEquals(0,labs.count());
 }
 @Test void cannotVoidAnEvidenceRecordFromAnotherCase()throws Exception{
  long id=add();mvc.perform(post(base()+"/"+id+"/void").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("expectedRowVersion",record.getRowVersion(),"reason","Correction")))).andExpect(status().isOk());
  mvc.perform(post(base()+"/"+id+"/void").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("expectedRowVersion",record.getRowVersion(),"reason","Again")))).andExpect(status().isBadRequest());
  SafetyCase other=new SafetyCase();other.setCaseNumber("LAB-OTHER");other.setCountryCode("USA");other.setReportType("Spontaneous");other.setInitialReceiptDate(LocalDate.now());other.setLatestReceivedDate(LocalDate.now());other=cases.saveAndFlush(other);
  mvc.perform(post("/api/cases/"+other.getId()+"/laboratory/"+id+"/void").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("expectedRowVersion",other.getRowVersion(),"reason","Wrong case")))).andExpect(status().isNotFound());
 }
 @Test void auditorReadsButCannotWrite()throws Exception{add();mvc.perform(get(base()).with(user("audit").roles("AUDITOR"))).andExpect(status().isOk());mvc.perform(post(base()).with(user("audit").roles("AUDITOR")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body()))).andExpect(status().isForbidden());}
 @Test void unrelatedProcessorCannotRead()throws Exception{mvc.perform(get(base()).with(user("stranger").roles("CASE_PROCESSOR"))).andExpect(status().isForbidden());}
}
