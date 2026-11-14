package com.pharmasafe.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import com.pharmasafe.administration.*;
import com.pharmasafe.entity.*;
import com.pharmasafe.repository.*;
import java.time.*;
import java.util.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(
    properties =
        "spring.datasource.url=jdbc:h2:mem:report_evidence;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;INIT=CREATE"
            + " SEQUENCE IF NOT EXISTS case_number_seq START WITH 1001")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@WithMockUser(username = "evidence-admin", roles = "ADMIN")
class ReportEvidenceControllerTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired UserRepository users;
  @Autowired SafetyReportRepository reports;
  @Autowired ReportDocumentExportRepository documents;
  @Autowired ReportAcknowledgementRepository acknowledgements;
  @Autowired RoleAdminAssignmentRepository assignments;
  @Autowired SafetyCaseRepository cases;
  SafetyReport report;
  User admin, reporter;

  @BeforeEach
  void setup() {
    admin = users.saveAndFlush(new User("evidence-admin", "unused", "ROLE_ADMIN"));
    reporter =
        users.saveAndFlush(new User("evidence-reporter", "unused", "ROLE_REPORTING_SPECIALIST"));
    report = new SafetyReport();
    report.setReportId("RPT-EVIDENCE-001");
    report.setReportType("PSUR");
    report.setProduct("Synthetic study product");
    report.setDueDate(LocalDate.now().plusDays(5));
    report.setStatus("Submitted");
    report.setSubmittedDate(Instant.now().minusSeconds(3600));
    report.setSummary("Original recorded summary");
    report.setCreatedByUser(reporter);
    report = reports.saveAndFlush(report);
  }

  String path() {
    return "/api/reports/" + report.getId();
  }

  byte[] body(Object o) throws Exception {
    return json.writeValueAsBytes(o);
  }

  JsonNode archive() throws Exception {
    return json.readTree(
        mvc.perform(
                post(path() + "/documents")
                    .with(csrf())
                    .contentType("application/json")
                    .content(body(Map.of("expectedVersion", report.getRowVersion()))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsByteArray());
  }

  Map<String, Object> ack(long v, String outcome) {
    return Map.of(
        "expectedVersion",
        v,
        "outcome",
        outcome,
        "reference",
        "ACK-001",
        "receivedAt",
        Instant.now().minusSeconds(60).toString(),
        "message",
        "External receiver response",
        "reason",
        "Manually recorded from receiver evidence");
  }

  @Test
  void archivedPdfRemainsByteIdenticalAfterReportChanges() throws Exception {
    var d = archive();
    String url = path() + "/documents/" + d.path("id").asText() + "/pdf";
    byte[] original =
        mvc.perform(get(url))
            .andExpect(status().isOk())
            .andExpect(content().contentType("application/pdf"))
            .andExpect(header().string("Cache-Control", "no-store"))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    assertEquals("%PDF", new String(original, 0, 4, java.nio.charset.StandardCharsets.US_ASCII));
    try (var pdf = Loader.loadPDF(original)) {
      String text = new PDFTextStripper().getText(pdf);
      assertTrue(text.contains("SAFETY REPORT RECORD"));
      assertTrue(text.contains("Original recorded summary"));
      assertNotNull(pdf.getDocumentCatalog().getNames().getEmbeddedFiles());
    }
    report.setSummary("Changed later");
    reports.saveAndFlush(report);
    assertArrayEquals(
        original,
        mvc.perform(get(url))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray());
  }

  @Test
  void staleAndForeignReportArchiveRequestsFail() throws Exception {
    var d = archive();
    mvc.perform(
            post(path() + "/documents")
                .with(csrf())
                .contentType("application/json")
                .content(body(Map.of("expectedVersion", 100))))
        .andExpect(status().isConflict());
    SafetyReport other = new SafetyReport();
    other.setReportId("RPT-OTHER");
    other.setReportType("PSUR");
    other.setProduct("Other");
    other.setDueDate(LocalDate.now());
    other = reports.saveAndFlush(other);
    mvc.perform(
            get("/api/reports/" + other.getId() + "/documents/" + d.path("id").asText() + "/pdf"))
        .andExpect(status().isNotFound());
  }

  @Test
  void acknowledgementCorrectionsAppendAndOldVersionsFail() throws Exception {
    long old = report.getRowVersion();
    mvc.perform(
            post(path() + "/acknowledgements")
                .with(csrf())
                .contentType("application/json")
                .content(body(ack(old, "REJECTED"))))
        .andExpect(status().isCreated());
    mvc.perform(
            post(path() + "/acknowledgements")
                .with(csrf())
                .contentType("application/json")
                .content(body(ack(old, "ACCEPTED"))))
        .andExpect(status().isConflict());
    mvc.perform(
            post(path() + "/acknowledgements")
                .with(csrf())
                .contentType("application/json")
                .content(body(ack(report.getRowVersion(), "ACCEPTED"))))
        .andExpect(status().isCreated());
    assertEquals(2, acknowledgements.count());
    mvc.perform(get(path() + "/evidence"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.acknowledgementCount").value(2));
    assertEquals("Submitted", report.getStatus());
  }

  @Test
  void acknowledgementDatesAndPendingReportsAreValidated() throws Exception {
    var future = new HashMap<>(ack(report.getRowVersion(), "ACCEPTED"));
    future.put("receivedAt", Instant.now().plusSeconds(600).toString());
    mvc.perform(
            post(path() + "/acknowledgements")
                .with(csrf())
                .contentType("application/json")
                .content(body(future)))
        .andExpect(status().isBadRequest());
    report.setStatus("Pending");
    report.setSubmittedDate(null);
    reports.saveAndFlush(report);
    mvc.perform(
            post(path() + "/acknowledgements")
                .with(csrf())
                .contentType("application/json")
                .content(body(ack(report.getRowVersion(), "ACCEPTED"))))
        .andExpect(status().isBadRequest());
    assertEquals(0, acknowledgements.count());
  }

  @Test
  void clinicalUsersAndAuditorWritesAreDenied() throws Exception {
    users.saveAndFlush(new User("evidence-auditor", "unused", "ROLE_AUDITOR"));
    mvc.perform(get(path() + "/evidence").with(user("normal-processor").roles("CASE_PROCESSOR")))
        .andExpect(status().isForbidden());
    mvc.perform(
            post(path() + "/acknowledgements")
                .with(user("evidence-auditor").roles("AUDITOR"))
                .with(csrf())
                .contentType("application/json")
                .content(body(ack(report.getRowVersion(), "ACCEPTED"))))
        .andExpect(status().isForbidden());
    mvc.perform(get(path() + "/evidence").with(user("evidence-auditor").roles("AUDITOR")))
        .andExpect(status().isOk());
  }

  @Test
  void scopedAdminEvidenceIsLimitedToTheAssignedRole() throws Exception {
    User own = users.saveAndFlush(new User("report-role-admin", "unused", "ROLE_SCOPED_ADMIN"));
    RoleAdminAssignment slot = new RoleAdminAssignment("ROLE_REPORTING_SPECIALIST");
    slot.administrator = own;
    slot.assignedBy = admin;
    slot.assignedAt = Instant.now();
    assignments.saveAndFlush(slot);
    mvc.perform(get(path() + "/evidence").with(user(own.getUsername()).roles("SCOPED_ADMIN")))
        .andExpect(status().isOk());
    mvc.perform(
            post(path() + "/documents")
                .with(user(own.getUsername()).roles("SCOPED_ADMIN"))
                .with(csrf())
                .contentType("application/json")
                .content(body(Map.of("expectedVersion", report.getRowVersion()))))
        .andExpect(status().isCreated());
    report.setCreatedByUser(admin);
    reports.saveAndFlush(report);
    mvc.perform(get(path() + "/evidence").with(user(own.getUsername()).roles("SCOPED_ADMIN")))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/reports").with(user(own.getUsername()).roles("SCOPED_ADMIN")))
        .andExpect(status().isForbidden());
  }

  @Test
  void invalidCountsAndPeriodsAreRejected() throws Exception {
    mvc.perform(
            post("/api/reports")
                .with(csrf())
                .contentType("application/json")
                .content(
                    body(
                        Map.of(
                            "product",
                            "Synthetic",
                            "reportType",
                            "PSUR",
                            "totalCases",
                            1,
                            "seriousCases",
                            2))))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/reports")
                .with(csrf())
                .contentType("application/json")
                .content(
                    body(
                        Map.of(
                            "product",
                            "Synthetic",
                            "reportType",
                            "PSUR",
                            "periodStart",
                            "2026-11-10",
                            "periodEnd",
                            "2026-11-01"))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void repeatSubmissionPreservesItsDateAndSyntheticSubmissionIsBlocked() throws Exception {
    Instant original = report.getSubmittedDate();
    mvc.perform(post(path() + "/submit").with(csrf())).andExpect(status().isOk());
    assertEquals(original, report.getSubmittedDate());
    SafetyCase c = new SafetyCase();
    c.setCaseNumber("DEMO-TEST");
    c.setCountryCode("USA");
    c.setReportType("Spontaneous");
    c.setInitialReceiptDate(LocalDate.now());
    c.setLatestReceivedDate(LocalDate.now());
    c.setSyntheticRecord(true);
    cases.saveAndFlush(c);
    report.setStatus("Pending");
    report.setSubmittedDate(null);
    report.getRelatedCases().add(c);
    reports.saveAndFlush(report);
    mvc.perform(post(path() + "/submit").with(csrf())).andExpect(status().isBadRequest());
  }

  @Test
  void updatedReportReturnsTheVersionNeededForAnImmediateArchive() throws Exception {
    report.setStatus("Pending");
    report.setSubmittedDate(null);
    reports.saveAndFlush(report);
    var updated =
        json.readTree(
            mvc.perform(
                    put(path())
                        .with(csrf())
                        .contentType("application/json")
                        .content(body(Map.of("summary", "Updated report content"))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray());
    mvc.perform(
            post(path() + "/documents")
                .with(csrf())
                .contentType("application/json")
                .content(body(Map.of("expectedVersion", updated.path("rowVersion").asLong()))))
        .andExpect(status().isCreated());
  }

  @Test
  void tamperedReportArchiveIsNotDownloaded() throws Exception {
    var generated = archive();
    var d = documents.findById(generated.path("id").asText()).orElseThrow();
    d.setPdfSha256("0".repeat(64));
    documents.saveAndFlush(d);
    mvc.perform(get(path() + "/documents/" + d.getId() + "/pdf"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void realAndSyntheticCasesCannotBeMixedInOneReport() throws Exception {
    for (boolean synthetic : new boolean[] {false, true}) {
      SafetyCase c = new SafetyCase();
      c.setCaseNumber(synthetic ? "DEMO-MIX" : "REAL-MIX");
      c.setCountryCode("USA");
      c.setReportType("Spontaneous");
      c.setInitialReceiptDate(LocalDate.now());
      c.setLatestReceivedDate(LocalDate.now());
      c.setWorkflowState("Approved");
      c.setSyntheticRecord(synthetic);
      cases.saveAndFlush(c);
    }
    mvc.perform(
            post("/api/reports")
                .with(csrf())
                .contentType("application/json")
                .content(
                    body(
                        Map.of(
                            "product",
                            "Study product",
                            "reportType",
                            "PSUR",
                            "relatedCaseNumbers",
                            List.of("DEMO-MIX", "REAL-MIX")))))
        .andExpect(status().isBadRequest());
  }
}
