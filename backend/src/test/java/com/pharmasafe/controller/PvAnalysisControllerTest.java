package com.pharmasafe.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import com.pharmasafe.administration.*;
import com.pharmasafe.analytics.*;
import com.pharmasafe.entity.*;
import com.pharmasafe.repository.*;
import com.pharmasafe.service.CasePdfRenderer;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PvAnalysisControllerTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired UserRepository users;
  @Autowired SafetyCaseRepository cases;
  @Autowired PvAnalysisRepository records;
  @Autowired PvCaseSeriesRepository series;
  @Autowired PvSeriesCaseRepository seriesCases;
  @Autowired PvAnalysisReviewRepository reviews;
  @Autowired RoleAdminAssignmentRepository assignments;
  @SpyBean CasePdfRenderer renderer;
  User analyst, other, manager, root, pvAdmin, foreignAdmin;
  SafetyCase reviewed, unreviewed;

  @BeforeEach
  void setup() {
    analyst = account("pv-test-analyst", "ROLE_PV_ANALYST");
    other = account("pv-test-other", "ROLE_PV_ANALYST");
    manager = account("pv-test-manager", "ROLE_SAFETY_MANAGER");
    root = account("pv-test-root", "ROLE_ADMIN");
    pvAdmin = account("pv-test-admin", "ROLE_SCOPED_ADMIN");
    foreignAdmin = account("pv-test-foreign", "ROLE_SCOPED_ADMIN");
    slot("ROLE_PV_ANALYST", pvAdmin);
    slot("ROLE_MEDICAL_REVIEWER", foreignAdmin);
    reviewed = safety("PV-REVIEWED", "Approved");
    unreviewed = safety("PV-UNREVIEWED", "Medical Review");
  }

  User account(String name, String role) {
    return users.saveAndFlush(new User(name, "hash", role));
  }

  void slot(String role, User admin) {
    var a = assignments.findById(role).orElse(new RoleAdminAssignment(role));
    a.administrator = admin;
    a.assignedBy = root;
    a.assignedAt = Instant.now();
    assignments.saveAndFlush(a);
  }

  SafetyCase safety(String number, String state) {
    var c = new SafetyCase();
    c.setCaseNumber(number);
    c.setWorkflowState(state);
    c.setInitialReceiptDate(LocalDate.now());
    c.setLatestReceivedDate(LocalDate.now());
    var p = new CaseProduct();
    p.setProductName("Synthetic Product");
    p.setSafetyCase(c);
    c.getProducts().add(p);
    var e = new CaseEvent();
    e.setEventVerbatim("Synthetic reaction");
    e.setOnsetDate(LocalDate.now());
    e.setSafetyCase(c);
    c.getEvents().add(e);
    return cases.saveAndFlush(c);
  }

  org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
          .UserRequestPostProcessor
      as(User u) {
    return user(u.getUsername())
        .authorities(
            new org.springframework.security.core.authority.SimpleGrantedAuthority(u.getRole()));
  }

  String body(String number) throws Exception {
    return json.writeValueAsString(
        Map.of(
            "title",
            "Synthetic safety assessment",
            "objective",
            "Review reported safety evidence",
            "findings",
            "A descriptive pattern requires evaluation",
            "recommendation",
            "Safety Manager should review the source evidence",
            "caseNumber",
            number));
  }

  JsonNode create() throws Exception {
    return json.readTree(
        mvc.perform(
                post("/api/pv-analyses")
                    .with(as(analyst))
                    .with(csrf())
                    .contentType("application/json")
                    .content(body(reviewed.getCaseNumber())))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  @Test
  void pvQueuesDetailsAndStatisticsUseReviewedCasesOnly() throws Exception {
    mvc.perform(get("/api/workspace/cases").with(as(analyst)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[?(@.caseNumber=='PV-REVIEWED')]").isNotEmpty())
        .andExpect(jsonPath("$.content[?(@.caseNumber=='PV-UNREVIEWED')]").isEmpty());
    mvc.perform(get("/api/cases/" + reviewed.getId()).with(as(analyst))).andExpect(status().isOk());
    mvc.perform(get("/api/cases/" + unreviewed.getId()).with(as(analyst)))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/pv-analyses/trends").with(as(analyst)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.products['Synthetic Product']").value(1))
        .andExpect(jsonPath("$.reactions['Synthetic reaction']").value(1));
  }

  @Test
  void pvCannotEditCasesApproveOrSubmitRegulatoryReports() throws Exception {
    for (String url : List.of("/api/cases", "/api/reports", "/api/processing-teams/teams"))
      mvc.perform(
              post(url)
                  .with(as(analyst))
                  .with(csrf())
                  .contentType("application/json")
                  .content("{}"))
          .andExpect(status().isForbidden());
    mvc.perform(
            put("/api/cases/" + reviewed.getId())
                .with(as(analyst))
                .with(csrf())
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/admin/users").with(as(analyst))).andExpect(status().isForbidden());
  }

  @Test
  void authorOwnsReportsWhileManagerAndPvAdminCanReview() throws Exception {
    var r = create();
    String id = r.path("id").asText();
    mvc.perform(get("/api/pv-analyses").with(as(other)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
    mvc.perform(get("/api/pv-analyses/" + id + "/pdf").with(as(other)))
        .andExpect(status().isForbidden());
    byte[] original =
        mvc.perform(get("/api/pv-analyses/" + id + "/pdf").with(as(analyst)))
            .andExpect(status().isOk())
            .andExpect(content().contentType("application/pdf"))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    assertTrue(new String(original, 0, 5).equals("%PDF-"));
    for (var reader : List.of(manager, pvAdmin, root)) {
      mvc.perform(get("/api/pv-analyses").with(as(reader)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.content[0].id").value(id));
      assertArrayEquals(
          original,
          mvc.perform(get("/api/pv-analyses/" + id + "/pdf").with(as(reader)))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsByteArray());
    }
    assertEquals("Approved", cases.findById(reviewed.getId()).orElseThrow().getWorkflowState());
  }

  @Test
  void otherRoleAdminsAndEmployeesCannotReadOrImpersonateAnalysts() throws Exception {
    for (User u : List.of(foreignAdmin, account("pv-processor", "ROLE_CASE_PROCESSOR"))) {
      mvc.perform(get("/api/pv-analyses/trends").with(as(u))).andExpect(status().isForbidden());
      mvc.perform(
              post("/api/pv-analyses")
                  .with(as(u))
                  .with(csrf())
                  .contentType("application/json")
                  .content(body("")))
          .andExpect(status().isForbidden());
    }
    mvc.perform(get("/api/pv-analyses?userId=" + other.getId()).with(as(analyst)))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/pv-analyses?userId=" + manager.getId()).with(as(pvAdmin)))
        .andExpect(status().isForbidden());
  }

  @Test
  void invalidInputsAndUnreviewedCaseLinksAreRejected() throws Exception {
    mvc.perform(
            post("/api/pv-analyses")
                .with(as(analyst))
                .with(csrf())
                .contentType("application/json")
                .content(body(unreviewed.getCaseNumber())))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/pv-analyses")
                .with(as(analyst))
                .with(csrf())
                .contentType("application/json")
                .content(body("").replace("Review reported safety evidence", " ")))
        .andExpect(status().isBadRequest());
    assertEquals(0, records.count());
  }

  @Test
  void failedPdfGenerationRollsBackTheAnalysis() throws Exception {
    doThrow(new java.io.IOException("Synthetic render failure"))
        .when(renderer)
        .render(any(), any());
    mvc.perform(
            post("/api/pv-analyses")
                .with(as(analyst))
                .with(csrf())
                .contentType("application/json")
                .content(body("")))
        .andExpect(status().isInternalServerError());
    assertEquals(0, records.count());
  }

  Map<String, Object> criteria() {
    return new LinkedHashMap<>(
        Map.of(
            "product",
            "Synthetic",
            "reaction",
            "reaction",
            "seriousness",
            "ALL",
            "includeSynthetic",
            false));
  }

  JsonNode capture() throws Exception {
    return json.readTree(
        mvc.perform(
                post("/api/pv-analyses/series")
                    .with(as(analyst))
                    .with(csrf())
                    .contentType("application/json")
                    .content(
                        json.writeValueAsBytes(
                            Map.of(
                                "name",
                                "Synthetic focused population",
                                "purpose",
                                "Evaluate the reviewed source population",
                                "criteria",
                                criteria()))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  JsonNode report(String seriesId, String prior) throws Exception {
    var d = new LinkedHashMap<String, Object>();
    d.put("title", "Frozen cohort evaluation");
    d.put("objective", "Review a defined population");
    d.put("findings", "Observed reports require source evaluation");
    d.put("recommendation", "Manager should evaluate evidence");
    d.put("seriesId", seriesId);
    d.put("supersedesId", prior);
    return json.readTree(
        mvc.perform(
                post("/api/pv-analyses")
                    .with(as(analyst))
                    .with(csrf())
                    .contentType("application/json")
                    .content(json.writeValueAsBytes(d)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  @Test
  void caseSeriesUsesReviewedAndLiteralConjunctiveFiltersWithoutDuplicates() throws Exception {
    var d = criteria();
    mvc.perform(
            post("/api/pv-analyses/series/preview")
                .with(as(analyst))
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsBytes(d)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1));
    d.put("product", "%");
    mvc.perform(
            post("/api/pv-analyses/series/preview")
                .with(as(analyst))
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsBytes(d)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
    d = criteria();
    d.put("receiptFrom", LocalDate.now().plusDays(1).toString());
    d.put("receiptTo", LocalDate.now().toString());
    mvc.perform(
            post("/api/pv-analyses/series/preview")
                .with(as(analyst))
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsBytes(d)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void frozenSeriesRetainsItsRevisionListingAfterSourceChangesAndEnforcesOwnerScope()
      throws Exception {
    var saved = capture();
    String id = saved.path("id").asText();
    assertEquals(1, saved.path("caseCount").asInt());
    assertEquals(1, seriesCases.count());
    var pdf =
        mvc.perform(get("/api/pv-analyses/series/" + id + "/pdf").with(as(analyst)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    assertTrue(
        new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII).startsWith("%PDF"));
    reviewed.setWorkflowState("Data Entry");
    cases.saveAndFlush(reviewed);
    mvc.perform(get("/api/pv-analyses/series/" + id).with(as(analyst)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.snapshot.caseListing[0].workflowState").value("Approved"));
    for (User u : List.of(manager, pvAdmin, root))
      assertArrayEquals(
          pdf,
          mvc.perform(get("/api/pv-analyses/series/" + id + "/pdf").with(as(u)))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsByteArray());
    for (User u : List.of(other, foreignAdmin))
      mvc.perform(get("/api/pv-analyses/series/" + id).with(as(u)))
          .andExpect(status().isForbidden());
    mvc.perform(get("/api/pv-analyses/series?userId=" + analyst.getId()).with(as(other)))
        .andExpect(status().isForbidden());
  }

  @Test
  void publishedReportContainsFrozenPopulationAndRejectsAnotherAnalystsSeries() throws Exception {
    String id = capture().path("id").asText();
    var published = report(id, "");
    var stored = records.findById(published.path("id").asText()).orElseThrow();
    assertTrue(stored.snapshotJson.contains("frozenCaseSeries"));
    assertTrue(stored.snapshotJson.contains("PV-REVIEWED"));
    assertFalse(stored.snapshotJson.contains("PV-UNREVIEWED"));
    var d = json.readTree(body(""));
    ((com.fasterxml.jackson.databind.node.ObjectNode) d).put("seriesId", id);
    mvc.perform(
            post("/api/pv-analyses")
                .with(as(other))
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsBytes(d)))
        .andExpect(status().isForbidden());
    ((com.fasterxml.jackson.databind.node.ObjectNode) d)
        .put("caseNumber", unreviewed.getCaseNumber());
    mvc.perform(
            post("/api/pv-analyses")
                .with(as(analyst))
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsBytes(d)))
        .andExpect(status().isForbidden());
  }

  @Test
  void reviewCycleIsManagerOnlyVersionedAndPreservesOriginalAndReplacement() throws Exception {
    var published = report(capture().path("id").asText(), "");
    String id = published.path("id").asText();
    byte[] original = records.findById(id).orElseThrow().pdfBytes.clone();
    var request =
        Map.of(
            "expectedLatestReviewId",
            0,
            "outcome",
            "FOLLOW_UP_REQUIRED",
            "comments",
            "Reassess the chronology and source coding",
            "followUpDueDate",
            LocalDate.now().plusDays(3).toString());
    for (User u : List.of(analyst, pvAdmin, foreignAdmin))
      mvc.perform(
              post("/api/pv-analyses/" + id + "/reviews")
                  .with(as(u))
                  .with(csrf())
                  .contentType("application/json")
                  .content(json.writeValueAsBytes(request)))
          .andExpect(status().isForbidden());
    var response =
        json.readTree(
            mvc.perform(
                    post("/api/pv-analyses/" + id + "/reviews")
                        .with(as(manager))
                        .with(csrf())
                        .contentType("application/json")
                        .content(json.writeValueAsBytes(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());
    mvc.perform(
            post("/api/pv-analyses/" + id + "/reviews")
                .with(as(root))
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsBytes(request)))
        .andExpect(status().isConflict());
    long reviewId = response.path("id").asLong();
    byte[] feedback =
        mvc.perform(
                get("/api/pv-analyses/" + id + "/reviews/" + reviewId + "/pdf").with(as(analyst)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    assertArrayEquals(
        feedback,
        mvc.perform(
                get("/api/pv-analyses/" + id + "/reviews/" + reviewId + "/pdf").with(as(manager)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray());
    mvc.perform(get("/api/pv-analyses/" + id + "/reviews").with(as(other)))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/pv-analyses/work-summary").with(as(analyst)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.followUpRequired").value(1));
    var replacement = report(published.path("seriesId").asText(), id);
    assertEquals(id, replacement.path("supersedesId").asText());
    assertArrayEquals(original, records.findById(id).orElseThrow().pdfBytes);
    mvc.perform(get("/api/pv-analyses/work-summary").with(as(analyst)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.awaitingReview").value(1))
        .andExpect(jsonPath("$.followUpRequired").value(0));
    mvc.perform(
            post("/api/pv-analyses/" + id + "/reviews")
                .with(as(manager))
                .with(csrf())
                .contentType("application/json")
                .content(
                    json.writeValueAsBytes(
                        Map.of(
                            "expectedLatestReviewId",
                            reviewId,
                            "outcome",
                            "ACKNOWLEDGED",
                            "comments",
                            "Old report reviewed"))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void emptyAndOversizedPopulationsAreRejectedAndPdfFailureIsAtomic() throws Exception {
    var d =
        Map.of(
            "name",
            "No population",
            "purpose",
            "Test empty population",
            "criteria",
            Map.of("product", "No match"));
    mvc.perform(
            post("/api/pv-analyses/series")
                .with(as(analyst))
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsBytes(d)))
        .andExpect(status().isBadRequest());
    doThrow(new java.io.IOException("Synthetic PDF failure"))
        .when(renderer)
        .render(any(JsonNode.class), any(byte[].class));
    mvc.perform(
            post("/api/pv-analyses/series")
                .with(as(analyst))
                .with(csrf())
                .contentType("application/json")
                .content(
                    json.writeValueAsBytes(
                        Map.of(
                            "name",
                            "Atomic evidence",
                            "purpose",
                            "Test rollback",
                            "criteria",
                            criteria()))))
        .andExpect(status().isInternalServerError());
    assertEquals(0, series.count());
    assertEquals(0, seriesCases.count());
  }

  @Test
  void moreThanTwoHundredMatchesCannotBeSilentlyTruncated() throws Exception {
    var bulk = new ArrayList<SafetyCase>();
    for (int i = 0; i < 200; i++) {
      var c = new SafetyCase();
      c.setCaseNumber("PV-LIMIT-" + i);
      c.setWorkflowState("Approved");
      c.setInitialReceiptDate(LocalDate.now());
      c.setLatestReceivedDate(LocalDate.now());
      bulk.add(c);
    }
    cases.saveAllAndFlush(bulk);
    mvc.perform(
            post("/api/pv-analyses/series")
                .with(as(analyst))
                .with(csrf())
                .contentType("application/json")
                .content(
                    json.writeValueAsBytes(
                        Map.of(
                            "name",
                            "Large population",
                            "purpose",
                            "Validate the population bound",
                            "criteria",
                            Map.of("includeSynthetic", false)))))
        .andExpect(status().isBadRequest());
    assertEquals(0, series.count());
    assertEquals(0, seriesCases.count());
  }

  @Test
  void managerPdfFailureDoesNotSavePartialFeedbackOrChangeOriginal() throws Exception {
    var p = create();
    String id = p.path("id").asText();
    byte[] original = records.findById(id).orElseThrow().pdfBytes.clone();
    doThrow(new java.io.IOException("Synthetic review rendering failure"))
        .when(renderer)
        .render(any(JsonNode.class), any(byte[].class));
    mvc.perform(
            post("/api/pv-analyses/" + id + "/reviews")
                .with(as(manager))
                .with(csrf())
                .contentType("application/json")
                .content(
                    json.writeValueAsBytes(
                        Map.of(
                            "expectedLatestReviewId",
                            0,
                            "outcome",
                            "ACKNOWLEDGED",
                            "comments",
                            "Source interpretation evaluated"))))
        .andExpect(status().isInternalServerError());
    assertEquals(0, reviews.count());
    assertArrayEquals(original, records.findById(id).orElseThrow().pdfBytes);
  }
}
