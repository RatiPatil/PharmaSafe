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
            "edu_submission_documents",
            "edu_task_feedback",
            "edu_task_submissions",
            "edu_progress_reports",
            "edu_tasks",
            "edu_student_domains",
            "edu_students",
            "edu_college_admins",
            "edu_domain_admins",
            "edu_student_id_reservations",
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
    return send(null, "/api/education/register", registration(name, c, reserve(c), selected), 201);
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
  void studentIdsAreReservedUniqueCollegeBoundAndSingleUse() throws Exception {
    var a = reserve(college);
    var b = reserve(college);
    assertNotEquals(a.path("studentCode"), b.path("studentCode"));
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
                "student.one", college, a, Set.of("ROLE_CASE_PROCESSOR", "ROLE_MEDICAL_REVIEWER")),
            201);
    assertEquals(a.path("studentCode"), created.path("studentCode"));
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
  void allSevenDomainsSupportApprovalAssignmentEvidenceFeedbackAndPdf() throws Exception {
    Set<String> all = new HashSet<>();
    domains.all.forEach(d -> all.add(d.code()));
    var student = register("student.all", college, all);
    String code = student.path("studentCode").asText();
    for (var d : domains.all) {
      approve("college.alpha", "student.all", d.code());
      var a = assign("college.alpha", college, d.code(), code, true).get(0);
      long id = a.path("id").asLong();
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
    read("domain.processor", "/api/education/tasks?domain=ROLE_MEDICAL_REVIEWER", 403);
    read("student.alpha", "/api/education/students", 403);
    send(
        "domain.processor",
        "/api/education/colleges",
        Map.of("code", "BAD", "collegeName", "Unauthorized"),
        403);
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
            "Domain administrators have read-only oversight"),
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
    assertEquals("ROLE_STUDENT", send(null, "/api/auth/login", Map.of("username", code, "password", PW), 200).path("user").path("role").asText());
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

  @Test void domainAdminSeesOnlyAssignedDomainAcrossCollegesAndExportsFilteredReports() throws Exception {
    var alpha=register("student.domain.alpha",college,Set.of("ROLE_CASE_PROCESSOR"));
    var beta=register("student.domain.beta",otherCollege,Set.of("ROLE_CASE_PROCESSOR"));
    approve("college.alpha","student.domain.alpha","ROLE_CASE_PROCESSOR");approve("college.beta","student.domain.beta","ROLE_CASE_PROCESSOR");
    assign("college.alpha",college,"ROLE_CASE_PROCESSOR",alpha.path("studentCode").asText(),false);
    assign("college.beta",otherCollege,"ROLE_CASE_PROCESSOR",beta.path("studentCode").asText(),false);
    var overview=read("domain.processor","/api/education/overview",200);assertEquals(7,overview.path("domains").size());
    int allowed=0;for(var card:overview.path("domains")){if(card.path("authorized").asBoolean()){allowed++;assertEquals("ROLE_CASE_PROCESSOR",card.path("code").asText());assertEquals(2,card.path("stats").path("total").asInt());}else assertTrue(card.path("stats").isNull());}assertEquals(1,allowed);
    assertEquals(1,read("domain.processor","/api/education/students?domain=ROLE_CASE_PROCESSOR&collegeId="+otherCollege,200).path("totalElements").asInt());
    assertEquals(1,read("domain.processor","/api/education/tasks?domain=ROLE_CASE_PROCESSOR&collegeId="+college,200).path("totalElements").asInt());
    send("domain.processor","/api/education/tasks",Map.of("collegeId",college,"domain","ROLE_CASE_PROCESSOR","stage","Case intake","title","Unauthorized assignment","description","Must be restricted to college administrators","instructions","Synthetic evidence","dueAt",Instant.now().plusSeconds(3600).toString(),"recipientMode","ALL_APPROVED"),403);
    var report=send("domain.processor","/api/education/reports",Map.of("domain","ROLE_CASE_PROCESSOR","collegeId",college,"studentCode",alpha.path("studentCode").asText()),201);
    byte[] pdf=mvc.perform(get("/api/education/reports/"+report.path("id").asLong()+"/pdf").with(as("domain.processor"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
    try(var document=org.apache.pdfbox.Loader.loadPDF(pdf)){String text=new org.apache.pdfbox.text.PDFTextStripper().getText(document);assertTrue(text.contains(alpha.path("studentCode").asText()));assertFalse(text.contains(beta.path("studentCode").asText()));}
    send("domain.processor","/api/education/reports",Map.of("domain","ROLE_MEDICAL_REVIEWER"),403);
    read("college.beta","/api/education/reports/"+report.path("id").asLong()+"/pdf",403);
  }
}
