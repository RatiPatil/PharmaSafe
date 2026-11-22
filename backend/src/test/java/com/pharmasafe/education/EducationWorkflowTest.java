package com.pharmasafe.education;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import com.pharmasafe.entity.User;
import com.pharmasafe.repository.UserRepository;
import com.pharmasafe.service.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;

@SpringBootTest(
    properties =
        "spring.datasource.url=jdbc:h2:mem:education_flow;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;INIT=CREATE"
            + " SEQUENCE IF NOT EXISTS case_number_seq START WITH 1001")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.test.annotation.DirtiesContext(
    classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class EducationWorkflowTest {
  @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired UserRepository users;
  @Autowired PasswordEncoder encoder;
  @Autowired EducationDomains domains;
  @MockBean AccessRateLimiter limiter;
  @MockBean AccessMailService mail;
  static final String PW = "SyntheticCollegePassword123!";
  String admin = "edu.root";
  long college, otherCollege;

  byte[] body(Object o) throws Exception {
    return json.writeValueAsBytes(o);
  }

  JsonNode parse(MvcResult r) throws Exception {
    return json.readTree(r.getResponse().getContentAsByteArray());
  }

  org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
          .UserRequestPostProcessor
      as(String name) {
    var u = users.findByUsername(name).orElseThrow();
    return user(name).roles(u.getRole().substring(5));
  }

  JsonNode send(String name, String path, Object o, int status) throws Exception {
    var request = post(path).with(csrf()).contentType("application/json").content(body(o));
    if (name != null) request.with(as(name));
    return parse(mvc.perform(request).andExpect(status().is(status)).andReturn());
  }

  JsonNode read(String name, String path, int status) throws Exception {
    var r = get(path);
    if (name != null) r.with(as(name));
    return parse(mvc.perform(r).andExpect(status().is(status)).andReturn());
  }

  @BeforeEach
  void setup() throws Exception {
    try (var connection = jdbc.getDataSource().getConnection()) {
      assertTrue(connection.getMetaData().getURL().startsWith("jdbc:h2:mem:education_flow"));
    }
    jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
    for (String table :
        List.of(
            "edu_management_changes",
            "edu_preferences",
            "edu_submission_documents",
            "edu_task_feedback",
            "edu_task_submissions",
            "edu_report_domains",
            "edu_progress_reports",
            "edu_tasks",
            "edu_work_orders",
            "edu_student_domains",
            "edu_students",
            "edu_college_admins",
            "edu_domain_admins",
            "edu_student_id_reservations",
            "edu_college_student_sequences",
            "edu_colleges",
            "audit_events",
            "users")) jdbc.update("DELETE FROM " + table);
    jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");
    users.saveAndFlush(new User(admin, encoder.encode(PW), "ROLE_ADMIN"));
    college =
        send(
                admin,
                "/api/education/colleges",
                Map.of("code", "SYN-A", "collegeName", "Synthetic College Alpha"),
                201)
            .path("id")
            .asLong();
    otherCollege =
        send(
                admin,
                "/api/education/colleges",
                Map.of("code", "SYN-B", "collegeName", "Synthetic College Beta"),
                201)
            .path("id")
            .asLong();
    createAdmin("college.alpha", "COLLEGE_ADMIN", college, "ROLE_CASE_PROCESSOR");
    createAdmin("college.beta", "COLLEGE_ADMIN", otherCollege, "ROLE_MEDICAL_REVIEWER");
    createAdmin("domain.processor", "DOMAIN_ADMIN", college, "ROLE_CASE_PROCESSOR");
  }

  JsonNode createAdmin(String name, String kind, long c, String domain) throws Exception {
    return send(
        admin,
        "/api/education/administrators",
        Map.of(
            "kind",
            kind,
            "collegeId",
            c,
            "domain",
            domain,
            "username",
            name,
            "fullName",
            "Synthetic Administrator",
            "email",
            name + "@example.test",
            "password",
            PW,
            "reason",
            "Synthetic workflow verification"),
        201);
  }

  JsonNode reserve(long c) throws Exception {
    return send(null, "/api/education/student-id", Map.of("collegeId", c), 201);
  }

  Map<String, Object> registration(String name, long c, JsonNode id, Set<String> selected) {
    return Map.of(
        "collegeId",
        c,
        "reservationToken",
        id.path("reservationToken").asText(),
        "username",
        name,
        "fullName",
        "Synthetic Student",
        "email",
        name + "@example.test",
        "password",
        PW,
        "confirmPassword",
        PW,
        "domains",
        selected);
  }

  JsonNode register(String name, long c, Set<String> selected) throws Exception {
    return send(
        null,
        "/api/education/register",
        registration(
            name,
            c,
            reserve(c),
            new java.util.HashSet<>(
                domains.all.stream().map(EducationDomains.Domain::code).toList())),
        201);
  }

  void approve(String who, String student, String domain) throws Exception {
    var requests = read(who, "/api/education/requests?domain=" + domain + "&status=PENDING", 200);
    for (var r : requests.path("content"))
      if (r.path("student").path("username").asText().equals(student))
        send(
            who,
            "/api/education/requests/" + r.path("id").asLong() + "/decision",
            Map.of(
                "expectedVersion",
                r.path("rowVersion").asLong(),
                "status",
                "APPROVED",
                "reason",
                "Student enrollment verified"),
            200);
  }

  JsonNode assign(String who, long c, String domain, String student, boolean doc) throws Exception {
    var d = new LinkedHashMap<String, Object>();
    d.put("collegeId", c);
    d.put("domain", domain);
    d.put("stage", domains.get(domain).stages().get(0));
    d.put("title", "Synthetic domain assignment");
    d.put("description", "Complete the domain learning exercise against synthetic source evidence");
    d.put("instructions", "Document reasoning and supporting sources");
    d.put("dueAt", Instant.now().plusSeconds(86400).toString());
    d.put("recipientMode", student == null ? "ALL_APPROVED" : "INDIVIDUAL");
    if (student != null) d.put("studentCode", student);
    d.put("requireDocument", doc);
    return send(who, "/api/education/tasks", d, 201);
  }

  Map<String, String> evidence(String domain) {
    var v = new HashMap<String, String>();
    for (var f : domains.get(domain).evidenceFields())
      v.put(
          f.key(),
          f.choices().isEmpty()
              ? "Synthetic domain evidence, analysis and reasoning"
              : f.choices().get(0));
    return v;
  }

  JsonNode submit(
      String who, long task, long version, String domain, String state, boolean doc, int expected)
      throws Exception {
    var d =
        Map.of(
            "expectedVersion",
            version,
            "status",
            state,
            "progress",
            state.equals("SUBMITTED") ? 100 : 40,
            "workDone",
            "Synthetic learning activity completed",
            "pendingWork",
            state.equals("SUBMITTED") ? "" : "Awaiting source confirmation",
            "remainingWork",
            state.equals("SUBMITTED") ? "" : "Finish evidence review",
            "evidence",
            evidence(domain));
    var r =
        multipart("/api/education/tasks/" + task + "/submissions")
            .file(new MockMultipartFile("data", "data.json", "application/json", body(d)));
    if (doc)
      r.file(
          new MockMultipartFile(
              "file", "source.pdf", "application/pdf", "%PDF-1.4\nSynthetic source".getBytes()));
    return parse(
        mvc.perform(r.with(as(who)).with(csrf())).andExpect(status().is(expected)).andReturn());
  }

  @Test
  void studentIdsAreHiddenUntilCommittedCollegeBoundAndSingleUse() throws Exception {
    var a = reserve(college);
    var b = reserve(college);
    assertFalse(a.has("studentCode"));
    assertFalse(b.has("studentCode"));
    send(
        null,
        "/api/education/register",
        registration("student.wrong", otherCollege, a, Set.of("ROLE_CASE_PROCESSOR")),
        400);
    var created =
        send(
            null,
            "/api/education/register",
            registration(
                "student.one",
                college,
                a,
                new HashSet<>(domains.all.stream().map(EducationDomains.Domain::code).toList())),
            201);
    assertEquals("SYN-A-0001", created.path("studentCode").asText());
    send(
        null,
        "/api/education/register",
        registration("student.reuse", college, a, Set.of("ROLE_CASE_PROCESSOR")),
        400);
    send(null, "/api/education/register", registration("student.empty", college, b, Set.of()), 400);
    var u = users.findByUsername("student.one").orElseThrow();
    assertTrue(encoder.matches(PW, u.getPasswordHash()));
    assertNotEquals(PW, u.getPasswordHash());
    assertEquals(7, read("student.one", "/api/education/overview", 200).path("domains").size());
    read("student.one", "/api/education/tasks?domain=ROLE_CASE_PROCESSOR", 403);
  }

  @Test
  void collegeWideDecisionsAreScopedVersionedAndAtomic() throws Exception {
    register("student.bulk", college, Set.of());
    register("student.other", otherCollege, Set.of());
    var pending = read("college.alpha", "/api/education/student-access", 200);
    assertEquals(1, pending.path("totalElements").asInt());
    var entry = pending.path("content").get(0);
    assertEquals(7, entry.path("pendingDomains").size());
    var versions = new HashMap<String, Long>();
    entry
        .path("pendingDomains")
        .forEach(d -> versions.put(d.path("domain").asText(), d.path("rowVersion").asLong()));
    var decision =
        Map.of(
            "status",
            "APPROVED",
            "reason",
            "College verified student enrollment",
            "expectedVersions",
            versions);
    String path = "/api/education/student-access/" + entry.path("id").asLong() + "/decision";
    read("domain.processor", "/api/education/student-access", 403);
    send("domain.processor", path, decision, 403);
    send(admin, path, decision, 403);
    send("college.beta", path, decision, 403);
    var stale = new HashMap<>(versions);
    stale.put(stale.keySet().iterator().next(), 999L);
    send(
        "college.alpha",
        path,
        Map.of(
            "status",
            "APPROVED",
            "reason",
            "Stale approval must be atomic",
            "expectedVersions",
            stale),
        409);
    assertEquals(
        7,
        read("college.alpha", "/api/education/student-access", 200)
            .path("content")
            .get(0)
            .path("pendingDomains")
            .size());
    assertEquals(7, send("college.alpha", path, decision, 200).path("updatedDomains").asInt());
    assertEquals(
        0,
        read("college.alpha", "/api/education/student-access", 200).path("totalElements").asInt());
    send("college.alpha", path, decision, 409);
    var other = read("college.beta", "/api/education/student-access", 200).path("content").get(0);
    var otherVersions = new HashMap<String, Long>();
    other
        .path("pendingDomains")
        .forEach(d -> otherVersions.put(d.path("domain").asText(), d.path("rowVersion").asLong()));
    send(
        "college.beta",
        "/api/education/student-access/" + other.path("id").asLong() + "/decision",
        Map.of(
            "status",
            "REJECTED",
            "reason",
            "Enrollment could not be verified",
            "expectedVersions",
            otherVersions),
        200);
    assertEquals(
        0,
        read("college.beta", "/api/education/student-access", 200).path("totalElements").asInt());
  }

  @Test
  void simultaneousSuccessfulRegistrationsGetUniqueCollegeSequences() throws Exception {
    var pool = java.util.concurrent.Executors.newFixedThreadPool(4);
    try {
      var jobs = new ArrayList<java.util.concurrent.Callable<String>>();
      for (int i = 0; i < 4; i++) {
        final String name = "student.concurrent" + i;
        jobs.add(
            () -> {
              var form =
                  new LinkedHashMap<>(
                      registration(
                          name,
                          college,
                          json.createObjectNode(),
                          new HashSet<>(
                              domains.all.stream().map(EducationDomains.Domain::code).toList())));
              form.remove("reservationToken");
              return send(null, "/api/education/register", form, 201).path("studentCode").asText();
            });
      }
      var ids = new HashSet<String>();
      for (var result : pool.invokeAll(jobs)) ids.add(result.get());
      assertEquals(Set.of("SYN-A-0001", "SYN-A-0002", "SYN-A-0003", "SYN-A-0004"), ids);
      var invalid =
          new LinkedHashMap<>(
              registration(
                  "student.concurrent0",
                  college,
                  json.createObjectNode(),
                  new HashSet<>(domains.all.stream().map(EducationDomains.Domain::code).toList())));
      invalid.remove("reservationToken");
      send(null, "/api/education/register", invalid, 409);
      assertEquals(
          4L,
          jdbc.queryForObject(
              "SELECT last_value FROM edu_college_student_sequences WHERE college_id=?",
              Long.class,
              college));
      assertEquals(
          "SYN-B-0001",
          register("student.beta.sequence", otherCollege, Set.of()).path("studentCode").asText());
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void advancedReportsCombineCollegeStudentAndMultipleDomainsWithoutLeakingData() throws Exception {
    var a = register("student.alpha.one", college, Set.of());
    var b = register("student.alpha.two", college, Set.of());
    var c = register("student.beta.one", otherCollege, Set.of());
    for (var d : domains.all) {
      approve("college.alpha", "student.alpha.one", d.code());
      approve("college.alpha", "student.alpha.two", d.code());
      approve("college.beta", "student.beta.one", d.code());
    }
    String one = a.path("studentCode").asText(),
        two = b.path("studentCode").asText(),
        other = c.path("studentCode").asText();
    assign("college.alpha", college, "ROLE_CASE_PROCESSOR", one, false);
    assign("college.alpha", college, "ROLE_MEDICAL_REVIEWER", one, false);
    assign("college.alpha", college, "ROLE_CASE_PROCESSOR", two, false);
    assign("college.beta", otherCollege, "ROLE_MEDICAL_REVIEWER", other, false);
    assign("college.beta", otherCollege, "ROLE_PV_ANALYST", other, false);
    var rootPreview = read(admin, "/api/education/report-preview", 200);
    assertEquals(5, rootPreview.path("summary").path("total").asInt());
    assertEquals(2, rootPreview.path("summary").path("colleges").asInt());
    assertEquals(7, rootPreview.path("domains").size());
    assertEquals(
        3,
        read(admin, "/api/education/report-options", 200)
            .path("students")
            .path("totalElements")
            .asInt());
    assertEquals(
        1,
        read(
                admin,
                "/api/education/report-preview?collegeId="
                    + otherCollege
                    + "&studentCode="
                    + other
                    + "&domains=ROLE_PV_ANALYST",
                200)
            .path("summary")
            .path("total")
            .asInt());
    var rootReport = send(admin, "/api/education/reports", Map.of(), 201);
    assertEquals(7, rootReport.path("domains").size());
    mvc.perform(
            get("/api/education/reports/" + rootReport.path("id").asLong() + "/pdf")
                .with(as(admin)))
        .andExpect(status().isOk());
    read("college.alpha", "/api/education/reports/" + rootReport.path("id").asLong() + "/pdf", 403);
    assertEquals(
        3,
        read("college.alpha", "/api/education/report-preview", 200)
            .path("summary")
            .path("total")
            .asInt());
    assertEquals(
        2,
        read("college.alpha", "/api/education/report-preview?studentCode=" + one, 200)
            .path("summary")
            .path("total")
            .asInt());
    assertEquals(
        1,
        read(
                "college.alpha",
                "/api/education/report-preview?studentCode=" + one + "&domains=ROLE_CASE_PROCESSOR",
                200)
            .path("summary")
            .path("total")
            .asInt());
    assertEquals(
        2,
        read("college.alpha", "/api/education/report-preview?domains=ROLE_CASE_PROCESSOR", 200)
            .path("summary")
            .path("total")
            .asInt());
    assertEquals(
        3,
        read(
                "college.alpha",
                "/api/education/report-preview?domains=ROLE_CASE_PROCESSOR&domains=ROLE_MEDICAL_REVIEWER",
                200)
            .path("summary")
            .path("total")
            .asInt());
    assertEquals(
        5,
        read("domain.processor", "/api/education/report-preview", 200)
            .path("summary")
            .path("total")
            .asInt());
    assertEquals(
        3,
        read("domain.processor", "/api/education/report-preview?collegeId=" + college, 200)
            .path("summary")
            .path("total")
            .asInt());
    assertEquals(
        2,
        read(
                "domain.processor",
                "/api/education/report-preview?collegeId=" + college + "&studentCode=" + one,
                200)
            .path("summary")
            .path("total")
            .asInt());
    assertEquals(
        1,
        read(
                "domain.processor",
                "/api/education/report-preview?collegeId="
                    + college
                    + "&studentCode="
                    + one
                    + "&domains=ROLE_MEDICAL_REVIEWER",
                200)
            .path("summary")
            .path("total")
            .asInt());
    assertEquals(
        2,
        read(
                "domain.processor",
                "/api/education/report-preview?collegeId="
                    + college
                    + "&domains=ROLE_CASE_PROCESSOR",
                200)
            .path("summary")
            .path("total")
            .asInt());
    assertEquals(
        1,
        read("domain.processor", "/api/education/report-preview?domains=ROLE_PV_ANALYST", 200)
            .path("summary")
            .path("total")
            .asInt());
    assertEquals(
        2,
        read("domain.processor", "/api/education/report-preview?domains=ROLE_MEDICAL_REVIEWER", 200)
            .path("summary")
            .path("total")
            .asInt());
    read("college.alpha", "/api/education/report-preview?collegeId=" + otherCollege, 403);
    read("college.alpha", "/api/education/report-options?collegeId=" + otherCollege, 403);
    read("college.alpha", "/api/education/report-preview?studentCode=" + other, 403);
    read(
        "domain.processor",
        "/api/education/report-preview?collegeId=" + otherCollege + "&studentCode=" + one,
        400);
    read("domain.processor", "/api/education/report-preview?domains=ROLE_ADMIN", 400);
    read("student.alpha.one", "/api/education/report-options", 403);
    read("student.alpha.one", "/api/education/report-preview", 403);
    read("domain.processor", "/api/education/report-preview?collegeId=9223372036854775807", 404);
    read("domain.processor", "/api/education/report-preview?page=-1", 400);
    var options = read("college.alpha", "/api/education/report-options", 200);
    assertEquals(2, options.path("students").path("totalElements").asInt());
    assertFalse(options.toString().contains(other));
    var betaOptions =
        read("domain.processor", "/api/education/report-options?collegeId=" + otherCollege, 200);
    assertEquals(1, betaOptions.path("students").path("totalElements").asInt());
    assertEquals(
        other, betaOptions.path("students").path("content").get(0).path("studentCode").asText());
    var report =
        send(
            "college.alpha",
            "/api/education/reports",
            Map.of(
                "studentCode",
                one,
                "domains",
                Set.of("ROLE_CASE_PROCESSOR", "ROLE_MEDICAL_REVIEWER")),
            201);
    assertEquals(2, report.path("domains").size());
    assertTrue(report.path("domain").isNull());
    byte[] pdf =
        mvc.perform(
                get("/api/education/reports/" + report.path("id").asLong() + "/pdf")
                    .with(as("college.alpha")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    try (var doc = org.apache.pdfbox.Loader.loadPDF(pdf)) {
      String text = new org.apache.pdfbox.text.PDFTextStripper().getText(doc);
      assertTrue(text.contains(one));
      assertFalse(text.contains(other));
      assertFalse(text.contains(two));
      assertTrue(text.contains("Case Processor"));
      assertTrue(text.contains("Medical Reviewer"));
    }
    assertEquals(
        1,
        read(
                "domain.processor",
                "/api/education/reports?collegeId=" + college + "&studentCode=" + one,
                200)
            .path("totalElements")
            .asInt());
    assertEquals(
        0,
        read("domain.processor", "/api/education/reports?domains=ROLE_CASE_PROCESSOR", 200)
            .path("totalElements")
            .asInt());
    read("college.beta", "/api/education/reports/" + report.path("id").asLong() + "/pdf", 403);
    read("student.alpha.two", "/api/education/reports/" + report.path("id").asLong() + "/pdf", 403);
    var studentReport =
        send(
            "student.alpha.one",
            "/api/education/reports",
            Map.of("domain", "ROLE_CASE_PROCESSOR"),
            201);
    mvc.perform(
            get("/api/education/reports/" + studentReport.path("id").asLong() + "/pdf")
                .with(as("college.alpha")))
        .andExpect(status().isOk());
    jdbc.update("UPDATE edu_domain_admins SET domain='ROLE_CASE_PROCESSOR' WHERE active=TRUE");
    read("domain.processor", "/api/education/report-preview?domains=ROLE_MEDICAL_REVIEWER", 403);
    assertEquals(
        2,
        read(admin, "/api/education/report-preview?domains=ROLE_MEDICAL_REVIEWER", 200)
            .path("summary")
            .path("total")
            .asInt());
    mvc.perform(
            get("/api/education/reports/" + report.path("id").asLong() + "/pdf").with(as(admin)))
        .andExpect(status().isOk());
    read("domain.processor", "/api/education/reports/" + report.path("id").asLong() + "/pdf", 403);
    assertEquals(
        0,
        read("domain.processor", "/api/education/reports", 200)
            .path("content")
            .findValues("id")
            .stream()
            .filter(n -> n.asLong() == report.path("id").asLong())
            .count());
  }

  @Test
  void loginPortalsSeparateCollegeAdministrationWithoutChangingLegacyOrOtherAccounts()
      throws Exception {
    for (var user : List.of("college.alpha", admin, "domain.processor")) {
      String right = user.equals("college.alpha") ? "COLLEGE_ADMIN" : "USER",
          wrong = user.equals("college.alpha") ? "USER" : "COLLEGE_ADMIN";
      mvc.perform(
              post("/api/auth/login")
                  .with(csrf())
                  .contentType("application/json")
                  .content(body(Map.of("username", user, "password", PW, "loginPortal", wrong))))
          .andExpect(status().isForbidden());
      mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
      var result =
          send(
              null,
              "/api/auth/login",
              Map.of("username", user, "password", PW, "loginPortal", right),
              200);
      assertTrue(result.path("success").asBoolean());
    }
    send(
        null,
        "/api/auth/login",
        Map.of("username", "college.alpha", "password", PW, "loginPortal", "OTHER"),
        400);
    send(
        null,
        "/api/auth/login",
        Map.of(
            "username",
            "college.alpha",
            "password",
            PW,
            "loginPortal",
            "COLLEGE_ADMIN",
            "applicationRole",
            "ROLE_CASE_PROCESSOR",
            "loginMode",
            "USER"),
        400);
    assertTrue(
        send(null, "/api/auth/login", Map.of("username", "college.alpha", "password", PW), 200)
            .path("success")
            .asBoolean());
  }

  @Test
  void allSevenDomainsSupportApprovalAssignmentEvidenceFeedbackAndPdf() throws Exception {
    Set<String> all = new HashSet<>();
    domains.all.forEach(d -> all.add(d.code()));
    var student = register("student.all", college, all);
    String code = student.path("studentCode").asText();
    for (var d : domains.all) {
      approve("college.alpha", "student.all", d.code());
      var a = assign("college.alpha", college, d.code(), code, true).get(0);
      long id = a.path("id").asLong();
      assertEquals(
          1,
          read("college.alpha", "/api/education/tasks?domain=" + d.code() + "&status=PENDING", 200)
              .path("totalElements")
              .asInt());
      assertEquals(
          1,
          read(
                  "college.alpha",
                  "/api/education/students?domain=" + d.code() + "&status=PENDING",
                  200)
              .path("totalElements")
              .asInt());
      submit("student.all", id, 0, d.code(), "SUBMITTED", false, 400);
      var result = submit("student.all", id, 0, d.code(), "SUBMITTED", true, 201);
      assertEquals("SUBMITTED", result.path("status").asText());
      send(
          "college.alpha",
          "/api/education/tasks/" + id + "/review",
          Map.of(
              "expectedVersion",
              result.path("rowVersion").asLong(),
              "outcome",
              "COMPLETED",
              "feedback",
              "Evidence and domain reasoning accepted"),
          200);
      var detail = read("student.all", "/api/education/tasks/" + id, 200);
      assertEquals("COMPLETED", detail.path("status").asText());
      assertEquals(
          0,
          read("college.alpha", "/api/education/tasks?domain=" + d.code() + "&status=PENDING", 200)
              .path("totalElements")
              .asInt());
      assertEquals(
          1,
          read(
                  "college.alpha",
                  "/api/education/tasks?domain=" + d.code() + "&status=COMPLETED",
                  200)
              .path("totalElements")
              .asInt());
      assertEquals(1, detail.path("submissions").path("content").get(0).path("feedback").size());
      var report = send("student.all", "/api/education/reports", Map.of("domain", d.code()), 201);
      byte[] pdf =
          mvc.perform(
                  get("/api/education/reports/" + report.path("id").asLong() + "/pdf")
                      .with(as("student.all")))
              .andExpect(status().isOk())
              .andExpect(content().contentType("application/pdf"))
              .andReturn()
              .getResponse()
              .getContentAsByteArray();
      try (var document = org.apache.pdfbox.Loader.loadPDF(pdf)) {
        assertTrue(new org.apache.pdfbox.text.PDFTextStripper().getText(document).contains(code));
      }
      assertEquals(
          1,
          read("college.alpha", "/api/education/tasks?domain=" + d.code(), 200)
              .path("totalElements")
              .asInt());
    }
  }

  @Test
  void collegeDomainAndStudentScopesCannotBeForged() throws Exception {
    var one =
        register("student.alpha", college, Set.of("ROLE_CASE_PROCESSOR", "ROLE_MEDICAL_REVIEWER"));
    var two = register("student.beta", otherCollege, Set.of("ROLE_CASE_PROCESSOR"));
    approve("college.alpha", "student.alpha", "ROLE_CASE_PROCESSOR");
    approve("college.beta", "student.beta", "ROLE_CASE_PROCESSOR");
    var a =
        assign(
                "college.alpha",
                college,
                "ROLE_CASE_PROCESSOR",
                one.path("studentCode").asText(),
                false)
            .get(0);
    read("college.beta", "/api/education/tasks/" + a.path("id").asLong(), 403);
    read("student.beta", "/api/education/tasks/" + a.path("id").asLong(), 403);
    read("college.alpha", "/api/education/students?collegeId=" + otherCollege, 403);
    read("domain.processor", "/api/education/tasks?domain=ROLE_MEDICAL_REVIEWER", 200);
    read("student.alpha", "/api/education/students", 403);
    send(
        "domain.processor",
        "/api/education/colleges",
        Map.of("code", "BAD", "collegeName", "Domain created college"),
        201);
    var med =
        read("college.alpha", "/api/education/requests?domain=ROLE_MEDICAL_REVIEWER", 200)
            .path("content")
            .get(0);
    send(
        "college.beta",
        "/api/education/requests/" + med.path("id").asLong() + "/decision",
        Map.of("expectedVersion", 0, "status", "APPROVED", "reason", "Cross college attempt"),
        403);
    send(
        "domain.processor",
        "/api/education/requests/" + med.path("id").asLong() + "/decision",
        Map.of(
            "expectedVersion",
            0,
            "status",
            "APPROVED",
            "reason",
            "Domain administrator cannot replace college approval"),
        403);
    read("student.alpha", "/api/cases", 403);
    users.saveAndFlush(new User("clinical.user", encoder.encode(PW), "ROLE_CASE_PROCESSOR"));
    read("clinical.user", "/api/education/overview", 403);
    assertEquals(
        1,
        read(
                "domain.processor",
                "/api/education/students?domain=ROLE_CASE_PROCESSOR&collegeId=" + otherCollege,
                200)
            .path("totalElements")
            .asInt());
  }

  @Test
  void staleResponsesReturnedWorkAndIndependentBroadcastArePreserved() throws Exception {
    var one = register("student.one", college, Set.of("ROLE_CASE_PROCESSOR"));
    var two = register("student.two", college, Set.of("ROLE_CASE_PROCESSOR"));
    approve("college.alpha", "student.one", "ROLE_CASE_PROCESSOR");
    approve("college.alpha", "student.two", "ROLE_CASE_PROCESSOR");
    var work = assign("college.alpha", college, "ROLE_CASE_PROCESSOR", null, false);
    assertEquals(2, work.size());
    long id = work.get(0).path("id").asLong();
    String owner =
        work.get(0).path("studentCode").asText().equals(one.path("studentCode").asText())
            ? "student.one"
            : "student.two";
    var first = submit(owner, id, 0, "ROLE_CASE_PROCESSOR", "IN_PROGRESS", false, 201);
    submit(owner, id, 0, "ROLE_CASE_PROCESSOR", "SUBMITTED", false, 409);
    var submitted =
        submit(
            owner,
            id,
            first.path("rowVersion").asLong(),
            "ROLE_CASE_PROCESSOR",
            "SUBMITTED",
            false,
            201);
    var returned =
        send(
            "college.alpha",
            "/api/education/tasks/" + id + "/review",
            Map.of(
                "expectedVersion",
                submitted.path("rowVersion").asLong(),
                "outcome",
                "RETURNED",
                "feedback",
                "Expand source traceability"),
            200);
    assertEquals("RETURNED", returned.path("status").asText());
    var again =
        submit(
            owner,
            id,
            returned.path("rowVersion").asLong(),
            "ROLE_CASE_PROCESSOR",
            "SUBMITTED",
            false,
            201);
    assertEquals(3, again.path("submissions").path("totalElements").asInt());
    assertEquals(
        "NOT_STARTED",
        read("college.alpha", "/api/education/tasks/" + work.get(1).path("id").asLong(), 200)
            .path("status")
            .asText());
  }

  @Test
  void commonLoginUsesActualAuthorityAndRevokedCollegeAdminCannotContinue() throws Exception {
    var student = register("student.login", college, Set.of("ROLE_PV_ANALYST"));
    send(null, "/api/auth/login", Map.of("username", "student.login", "password", PW), 200);
    var code = student.path("studentCode").asText();
    assertEquals(
        "ROLE_STUDENT",
        send(null, "/api/auth/login", Map.of("username", code, "password", PW), 200)
            .path("user")
            .path("role")
            .asText());
    send(null, "/api/auth/login", Map.of("username", code.toLowerCase(), "password", PW), 200);
    send(null, "/api/auth/login", Map.of("username", code, "password", "WrongPassword123!"), 401);
    var admins = read(admin, "/api/education/administrators", 200);
    for (var g : admins)
      if (g.path("username").asText().equals("college.alpha"))
        mvc.perform(
                put("/api/education/administrators/COLLEGE_ADMIN/" + g.path("id").asLong())
                    .with(as(admin))
                    .with(csrf())
                    .contentType("application/json")
                    .content(
                        body(
                            Map.of(
                                "expectedVersion",
                                g.path("rowVersion").asLong(),
                                "active",
                                false,
                                "reason",
                                "Access management verification"))))
            .andExpect(status().isOk());
    read("college.alpha", "/api/education/overview", 403);
    send(null, "/api/auth/login", Map.of("username", "college.alpha", "password", PW), 403);
    assertEquals(7, read(null, "/api/auth/access-roles", 200).size());
    read(admin, "/api/administration/overview", 200);
  }

  @Test
  void oneDomainAdminSeesSevenDomainsAcrossCollegesAndExportsFilteredReports() throws Exception {
    var alpha = register("student.domain.alpha", college, Set.of("ROLE_CASE_PROCESSOR"));
    var beta = register("student.domain.beta", otherCollege, Set.of("ROLE_CASE_PROCESSOR"));
    approve("college.alpha", "student.domain.alpha", "ROLE_CASE_PROCESSOR");
    approve("college.beta", "student.domain.beta", "ROLE_CASE_PROCESSOR");
    assign(
        "college.alpha", college, "ROLE_CASE_PROCESSOR", alpha.path("studentCode").asText(), false);
    assign(
        "college.beta",
        otherCollege,
        "ROLE_CASE_PROCESSOR",
        beta.path("studentCode").asText(),
        false);
    var overview = read("domain.processor", "/api/education/overview", 200);
    assertEquals(7, overview.path("domains").size());
    int allowed = 0;
    for (var card : overview.path("domains")) {
      assertTrue(card.path("authorized").asBoolean());
      allowed++;
      assertFalse(card.path("stats").isNull());
      assertEquals(
          card.path("code").asText().equals("ROLE_CASE_PROCESSOR") ? 2 : 0,
          card.path("stats").path("total").asInt());
    }
    assertEquals(7, allowed);
    send(
        admin,
        "/api/education/administrators",
        Map.of(
            "kind",
            "DOMAIN_ADMIN",
            "username",
            "domain.second",
            "fullName",
            "Second Administrator",
            "email",
            "second@example.test",
            "password",
            PW,
            "reason",
            "Duplicate account must be refused"),
        409);
    read("domain.processor", "/api/cases", 403);
    read("domain.processor", "/api/education/administrators", 403);
    var collegeReport =
        send(
            "college.alpha",
            "/api/education/reports",
            Map.of(
                "domain", "ROLE_CASE_PROCESSOR", "studentCode", alpha.path("studentCode").asText()),
            201);
    mvc.perform(
            get("/api/education/reports/" + collegeReport.path("id").asLong() + "/pdf")
                .with(as("domain.processor")))
        .andExpect(status().isOk());
    assertEquals(
        1,
        read(
                "domain.processor",
                "/api/education/reports?domain=ROLE_CASE_PROCESSOR&collegeId="
                    + college
                    + "&studentCode="
                    + alpha.path("studentCode").asText(),
                200)
            .path("totalElements")
            .asInt());
    assertEquals(
        1,
        read(
                "domain.processor",
                "/api/education/students?domain=ROLE_CASE_PROCESSOR&collegeId=" + otherCollege,
                200)
            .path("totalElements")
            .asInt());
    assertEquals(
        1,
        read(
                "domain.processor",
                "/api/education/tasks?domain=ROLE_CASE_PROCESSOR&collegeId=" + college,
                200)
            .path("totalElements")
            .asInt());
    send(
        "domain.processor",
        "/api/education/tasks",
        Map.of(
            "collegeId",
            college,
            "domain",
            "ROLE_CASE_PROCESSOR",
            "stage",
            "Case intake",
            "title",
            "Unauthorized assignment",
            "description",
            "Must be restricted to college administrators",
            "instructions",
            "Synthetic evidence",
            "dueAt",
            Instant.now().plusSeconds(3600).toString(),
            "recipientMode",
            "ALL_APPROVED"),
        201);
    var report =
        send(
            "domain.processor",
            "/api/education/reports",
            Map.of(
                "domain",
                "ROLE_CASE_PROCESSOR",
                "collegeId",
                college,
                "studentCode",
                alpha.path("studentCode").asText()),
            201);
    byte[] pdf =
        mvc.perform(
                get("/api/education/reports/" + report.path("id").asLong() + "/pdf")
                    .with(as("domain.processor")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    try (var document = org.apache.pdfbox.Loader.loadPDF(pdf)) {
      String text = new org.apache.pdfbox.text.PDFTextStripper().getText(document);
      assertTrue(text.contains(alpha.path("studentCode").asText()));
      assertFalse(text.contains(beta.path("studentCode").asText()));
    }
    send(
        "domain.processor",
        "/api/education/reports",
        Map.of("domain", "ROLE_MEDICAL_REVIEWER"),
        201);
    send(
        "domain.processor",
        "/api/education/reports",
        Map.of(
            "domain", "ROLE_MEDICAL_REVIEWER", "studentCode", alpha.path("studentCode").asText()),
        201);
    read("college.beta", "/api/education/reports/" + report.path("id").asLong() + "/pdf", 403);
  }

  @Test
  void singletonDomainAdminReplacementRequiresDeactivationAndRevokesOldAccess() throws Exception {
    var grants = read(admin, "/api/education/administrators", 200);
    JsonNode original = null;
    for (var g : grants) if (g.path("kind").asText().equals("DOMAIN_ADMIN")) original = g;
    assertNotNull(original);
    var url = "/api/education/administrators/DOMAIN_ADMIN/" + original.path("id").asLong();
    mvc.perform(
            put(url)
                .with(as(admin))
                .with(csrf())
                .contentType("application/json")
                .content(
                    body(
                        Map.of(
                            "expectedVersion",
                            original.path("rowVersion").asLong(),
                            "active",
                            false,
                            "reason",
                            "Administrator handover"))))
        .andExpect(status().isOk());
    read("domain.processor", "/api/education/overview", 403);
    var replacement = createAdmin("domain.replacement", "DOMAIN_ADMIN", college, "");
    assertTrue(replacement.path("domain").isNull());
    mvc.perform(
            put(url)
                .with(as(admin))
                .with(csrf())
                .contentType("application/json")
                .content(
                    body(
                        Map.of(
                            "expectedVersion",
                            original.path("rowVersion").asLong() + 1,
                            "active",
                            true,
                            "reason",
                            "Must not activate a second administrator"))))
        .andExpect(status().isConflict());
    var overview = read("domain.replacement", "/api/education/overview", 200);
    for (var d : overview.path("domains")) assertTrue(d.path("authorized").asBoolean());
  }

  @Test
  void registrationRejectsPartialDomainsAndAdminCanManageOnlyAuthorizedStudents() throws Exception {
    var id = reserve(college);
    send(
        null,
        "/api/education/register",
        registration("partial.student", college, id, Set.of("ROLE_CASE_PROCESSOR")),
        400);
    var input =
        Map.of(
            "collegeId",
            college,
            "domain",
            "ROLE_CASE_PROCESSOR",
            "fullName",
            "Managed Student",
            "username",
            "managed.student",
            "email",
            "managed@example.test",
            "password",
            PW,
            "reason",
            "Verified enrollment by college coordinator");
    send("college.beta", "/api/education/students", input, 403);
    var student = send("college.alpha", "/api/education/students", input, 201);
    long studentId = student.path("id").asLong();
    assertEquals(
        7,
        jdbc.queryForObject(
            "SELECT count(*) FROM edu_student_domains WHERE student_id=?",
            Integer.class,
            studentId));
    var edit =
        Map.of(
            "domain",
            "ROLE_CASE_PROCESSOR",
            "expectedVersion",
            student.path("rowVersion").asLong(),
            "fullName",
            "Updated Student",
            "email",
            "updated@example.test",
            "active",
            true,
            "reason",
            "Verified profile correction");
    mvc.perform(
            put("/api/education/students/" + studentId)
                .with(as("college.beta"))
                .with(csrf())
                .contentType("application/json")
                .content(body(edit)))
        .andExpect(status().isForbidden());
    mvc.perform(
            put("/api/education/students/" + studentId)
                .with(as("domain.processor"))
                .with(csrf())
                .contentType("application/json")
                .content(body(edit)))
        .andExpect(status().isOk());
    assertEquals(
        "Updated Student",
        read("managed.student", "/api/education/profile", 200).path("fullName").asText());
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT count(*) FROM edu_management_changes WHERE record_kind='STUDENT' AND"
                + " record_id=?",
            Integer.class,
            studentId));
    mvc.perform(
            put("/api/education/preferences")
                .with(as("managed.student"))
                .with(csrf())
                .contentType("application/json")
                .content(body(Map.of("compactView", true, "reduceMotion", true))))
        .andExpect(status().isOk());
    assertTrue(
        read("managed.student", "/api/education/preferences", 200)
            .path("reduceMotion")
            .asBoolean());
    read(
        "college.beta",
        "/api/education/students/" + studentId + "?domain=ROLE_CASE_PROCESSOR",
        403);
  }

  @Test
  void domainCollegeOrdersSelectedAssignmentsEditingAndArchivingRetainEvidence() throws Exception {
    var student = register("order.student", college, Set.of());
    approve("college.alpha", "order.student", "ROLE_CASE_PROCESSOR");
    var deadline = Instant.now().plusSeconds(86400).toString();
    var input =
        Map.of(
            "collegeId",
            college,
            "domain",
            "ROLE_CASE_PROCESSOR",
            "stage",
            "Case intake",
            "title",
            "Domain college order",
            "description",
            "Review source case evidence",
            "instructions",
            "Validate all four minimum criteria",
            "dueAt",
            deadline,
            "requireDocument",
            false);
    send("college.alpha", "/api/education/orders", input, 403);
    var order = send("domain.processor", "/api/education/orders", input, 201);
    assertEquals(
        0, read("college.beta", "/api/education/orders?domain=ROLE_CASE_PROCESSOR", 200).size());
    var assign = new LinkedHashMap<String, Object>(input);
    assign.put("sourceOrderId", order.path("id").asLong());
    assign.put("recipientMode", "SELECTED");
    assign.put("studentCodes", Set.of(student.path("studentCode").asText()));
    var tampered = new LinkedHashMap<>(assign);
    tampered.put("instructions", "Altered global instructions");
    send("college.alpha", "/api/education/tasks", tampered, 400);
    var task = send("college.alpha", "/api/education/tasks", assign, 201).get(0);
    long taskId = task.path("id").asLong();
    var orderEdit =
        Map.of(
            "expectedVersion",
            order.path("rowVersion").asLong(),
            "title",
            "Changed order",
            "description",
            "Changed source evidence",
            "instructions",
            "Changed instructions",
            "dueAt",
            deadline,
            "requireDocument",
            false,
            "archived",
            false,
            "reason",
            "Original instructions must remain once dispatched");
    mvc.perform(
            put("/api/education/orders/" + order.path("id").asLong())
                .with(as("domain.processor"))
                .with(csrf())
                .contentType("application/json")
                .content(body(orderEdit)))
        .andExpect(status().isConflict());
    var direct =
        assign(
                "domain.processor",
                college,
                "ROLE_CASE_PROCESSOR",
                student.path("studentCode").asText(),
                false)
            .get(0);
    var edit =
        Map.of(
            "expectedVersion",
            direct.path("rowVersion").asLong(),
            "title",
            "Corrected task",
            "description",
            "Clarified source evidence",
            "instructions",
            "Validate original source criteria carefully",
            "dueAt",
            deadline,
            "requireDocument",
            false,
            "archived",
            false,
            "reason",
            "Clarify before student starts work");
    mvc.perform(
            put("/api/education/tasks/" + direct.path("id").asLong())
                .with(as("college.alpha"))
                .with(csrf())
                .contentType("application/json")
                .content(body(edit)))
        .andExpect(status().isOk());
    var submitted =
        submit("order.student", taskId, 0, "ROLE_CASE_PROCESSOR", "SUBMITTED", false, 201);
    var archive = new LinkedHashMap<String, Object>();
    archive.put("expectedVersion", submitted.path("rowVersion").asLong());
    archive.put("title", task.path("title").asText());
    archive.put("description", task.path("description").asText());
    archive.put("instructions", task.path("instructions").asText());
    archive.put("dueAt", deadline);
    archive.put("requireDocument", false);
    archive.put("archived", true);
    archive.put("reason", "Withdraw assignment; retain submitted evidence");
    mvc.perform(
            put("/api/education/tasks/" + taskId)
                .with(as("college.beta"))
                .with(csrf())
                .contentType("application/json")
                .content(body(archive)))
        .andExpect(status().isForbidden());
    mvc.perform(
            put("/api/education/tasks/" + taskId)
                .with(as("college.alpha"))
                .with(csrf())
                .contentType("application/json")
                .content(body(archive)))
        .andExpect(status().isOk());
    var detail = read("domain.processor", "/api/education/tasks/" + taskId, 200);
    assertTrue(detail.path("archived").asBoolean());
    assertEquals(1, detail.path("submissions").path("totalElements").asInt());
    assertEquals(
        1,
        read("college.alpha", "/api/education/tasks?domain=ROLE_CASE_PROCESSOR", 200)
            .path("totalElements")
            .asInt());
    assertTrue(
        jdbc.queryForObject("SELECT count(*) FROM edu_management_changes", Integer.class) >= 3);
  }

  @Test
  void studentNameEditsPreserveSessionsButAccessChangesRevokeThem() throws Exception {
    register("session.student", college, Set.of());
    var u = users.findByUsername("session.student").orElseThrow();
    long before = u.getAuthVersion();
    var student =
        read("college.alpha", "/api/education/students?domain=ROLE_CASE_PROCESSOR", 200)
            .path("content")
            .get(0);
    var payload =
        new LinkedHashMap<String, Object>(
            Map.of(
                "domain",
                "ROLE_CASE_PROCESSOR",
                "expectedVersion",
                student.path("rowVersion").asLong(),
                "fullName",
                "Updated Name",
                "email",
                u.getEmail(),
                "active",
                true,
                "reason",
                "Correct display name without interrupting work"));
    mvc.perform(
            put("/api/education/students/" + student.path("id").asLong())
                .with(as("college.alpha"))
                .with(csrf())
                .contentType("application/json")
                .content(body(payload)))
        .andExpect(status().isOk());
    assertEquals(before, users.findByUsername("session.student").orElseThrow().getAuthVersion());
    payload.put("expectedVersion", student.path("rowVersion").asLong() + 1);
    payload.put("active", false);
    payload.put("reason", "Revoke student access");
    mvc.perform(
            put("/api/education/students/" + student.path("id").asLong())
                .with(as("college.alpha"))
                .with(csrf())
                .contentType("application/json")
                .content(body(payload)))
        .andExpect(status().isOk());
    assertTrue(users.findByUsername("session.student").orElseThrow().getAuthVersion() > before);
    read("session.student", "/api/education/overview", 403);
  }
}
