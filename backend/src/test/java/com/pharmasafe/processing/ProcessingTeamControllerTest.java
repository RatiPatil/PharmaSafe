package com.pharmasafe.processing;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import com.pharmasafe.administration.*;
import com.pharmasafe.entity.*;
import com.pharmasafe.repository.*;
import com.pharmasafe.service.*;
import java.time.*;
import java.util.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;

@SpringBootTest(
    properties =
        "spring.datasource.url=jdbc:h2:mem:processing_team_hierarchy;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;INIT=CREATE"
            + " SEQUENCE IF NOT EXISTS case_number_seq START WITH 1001")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.test.annotation.DirtiesContext(
    classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class ProcessingTeamControllerTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate jdbc;
  @Autowired UserRepository users;
  @Autowired RoleAdminAssignmentRepository owners;
  @Autowired ProcessingTeamRepository teams;
  @Autowired ProcessingMemberRepository members;
  @Autowired ProcessingAssignmentRepository assignments;
  @Autowired ProcessingWorkOrderRepository orders;
  @Autowired ProcessingWorkUpdateRepository updates;
  @Autowired ProcessingWorkDocumentRepository documents;
  @Autowired RegistrationRequestRepository registrations;
  @Autowired SafetyCaseRepository cases;
  @Autowired AccessRoleCatalog catalog;
  @Autowired PasswordEncoder encoder;
  @Autowired AdministrativeEventRepository adminEvents;
  @MockBean AccessRateLimiter limiter;
  @MockBean AccessMailService mail;
  @SpyBean CasePdfRenderer renderer;
  static final String PW = "SyntheticTeamPassword123!";
  User root, central, foreignAdmin;

  @BeforeEach
  void setup() throws Exception {
    try (var connection = jdbc.getDataSource().getConnection()) {
      assertTrue(
          connection.getMetaData().getURL().startsWith("jdbc:h2:mem:processing_team_hierarchy"));
    }
    // Only this isolated in-memory test database is cleared; no production settings are loaded.
    jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
    for (String table :
        List.of(
            "cp_processing_evidence",
            "cp_assignment_cases",
            "cp_work_documents",
            "cp_work_updates",
            "cp_work_assignments",
            "cp_work_orders",
            "registration_details",
            "registration_requests",
            "cp_team_members",
            "cp_teams",
            "administrative_events",
            "audit_events",
            "role_admin_assignments",
            "cases",
            "users")) jdbc.update("DELETE FROM " + table);
    jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");
    root = users.saveAndFlush(new User("team-root", encoder.encode(PW), "ROLE_ADMIN"));
    central = users.saveAndFlush(new User("cp-central", encoder.encode(PW), "ROLE_SCOPED_ADMIN"));
    foreignAdmin =
        users.saveAndFlush(new User("medical-central", encoder.encode(PW), "ROLE_SCOPED_ADMIN"));
    var owner = new RoleAdminAssignment("ROLE_CASE_PROCESSOR");
    owner.administrator = central;
    owner.assignedBy = root;
    owner.assignedAt = Instant.now();
    owners.saveAndFlush(owner);
    var other = new RoleAdminAssignment("ROLE_MEDICAL_REVIEWER");
    other.administrator = foreignAdmin;
    other.assignedBy = root;
    other.assignedAt = Instant.now();
    owners.saveAndFlush(other);
  }

  UserRequestPostProcessor as(User u) {
    return user(u.getUsername())
        .authorities(
            AdminScope.authorities(u.getRole())
                .toArray(org.springframework.security.core.GrantedAuthority[]::new));
  }

  byte[] body(Object d) throws Exception {
    return json.writeValueAsBytes(d);
  }

  JsonNode parsed(MvcResult r) throws Exception {
    return json.readTree(r.getResponse().getContentAsByteArray());
  }

  JsonNode createTeam(String suffix) throws Exception {
    return parsed(
        mvc.perform(
                post("/api/processing-teams/teams")
                    .with(as(central))
                    .with(csrf())
                    .contentType("application/json")
                    .content(
                        body(
                            Map.of(
                                "name",
                                "Processing Team " + suffix,
                                "fullName",
                                "Synthetic Team Lead",
                                "username",
                                "team.lead." + suffix,
                                "email",
                                "lead." + suffix + "@example.test",
                                "password",
                                PW,
                                "reason",
                                "Creating a supervised processing team"))))
            .andExpect(status().isCreated())
            .andReturn());
  }

  ProcessingTeam team(JsonNode d) {
    return teams.findById(d.path("id").asLong()).orElseThrow();
  }

  User lead(ProcessingTeam t) {
    return users.findById(t.lead.getId()).orElseThrow();
  }

  User employee(ProcessingTeam t, String name) {
    User u = new User(name, encoder.encode(PW), "ROLE_CASE_PROCESSOR");
    u.setFullName("Synthetic Employee");
    users.saveAndFlush(u);
    ProcessingMember m = new ProcessingMember();
    m.team = t;
    m.user = u;
    m.status = "ACTIVE";
    m.joinedAt = Instant.now();
    members.saveAndFlush(m);
    return u;
  }

  Map<String, Object> work(ProcessingTeam t, String mode, User employee, String stage) {
    var d = new LinkedHashMap<String, Object>();
    d.put("teamId", t.id);
    d.put("title", "Verify adverse event intake");
    d.put(
        "description",
        "Collect and validate patient, reporter, suspect drug and event information");
    d.put("instructions", "Document missing data and preserve source evidence");
    d.put("stage", stage);
    d.put("priority", "Priority");
    d.put("dueAt", Instant.now().plusSeconds(7200).toString());
    d.put("recipientMode", mode);
    if (employee != null) d.put("employeeStaffCode", employee.getStaffCode());
    return d;
  }

  JsonNode assign(ProcessingTeam t, User employee, String stage) throws Exception {
    return parsed(
            mvc.perform(
                    post("/api/processing-teams/work")
                        .with(as(lead(t)))
                        .with(csrf())
                        .contentType("application/json")
                        .content(body(work(t, "INDIVIDUAL", employee, stage))))
                .andExpect(status().isCreated())
                .andReturn())
        .get(0);
  }

  Map<String, Object> progress(long version, String status, int percent, boolean valid) {
    return new LinkedHashMap<>(
        Map.of(
            "expectedVersion",
            version,
            "status",
            status,
            "progress",
            percent,
            "workDone",
            "Collected and reviewed the source report",
            "pendingWork",
            status.equals("COMPLETED") ? "" : "Awaiting source confirmation",
            "remainingWork",
            status.equals("COMPLETED") ? "" : "Finalize source verification",
            "blockers",
            status.equals("BLOCKED") ? "Patient information is missing" : "",
            "reason",
            "New source evidence reviewed",
            "checklist",
            Map.of(
                "patientVerified",
                valid,
                "reporterVerified",
                true,
                "drugVerified",
                true,
                "eventVerified",
                true),
            "codingReference",
            ""));
  }

  JsonNode register(ProcessingTeam t, String suffix) throws Exception {
    var config =
        json.readTree(getClass().getResourceAsStream("/validation/input-rules.json"))
            .path("registrationChoices");
    var details = new LinkedHashMap<String, String>();
    for (String field : AccessRoleCatalog.FIELDS.get("ROLE_CASE_PROCESSOR")) {
      var choice = config.path(field);
      details.put(field, choice.isArray() ? choice.get(0).asText() : "Synthetic training evidence");
    }
    var d = new LinkedHashMap<String, Object>();
    d.put("selectionToken", catalog.issue("ROLE_CASE_PROCESSOR"));
    d.put("fullName", "Synthetic Employee");
    d.put("employeeId", "EMP-" + suffix);
    d.put("email", "employee." + suffix + "@example.test");
    d.put("mobileNumber", "+15551234567");
    d.put("department", config.path("department").get(0).asText());
    d.put("designation", config.path("designation").get(0).asText());
    d.put("organization", "Synthetic PV Organization");
    d.put("username", "employee." + suffix);
    d.put("password", PW);
    d.put("confirmPassword", PW);
    d.put("reason", "Joining this Case Processing team");
    d.put("details", details);
    d.put("teamRegistrationKey", t.registrationKey);
    return parsed(
        mvc.perform(
                post("/api/auth/register")
                    .with(csrf())
                    .contentType("application/json")
                    .content(body(d)))
            .andExpect(status().isCreated())
            .andReturn());
  }

  @Test
  void onlyCaseProcessingAdminCanCreateAndProvisionTeamLeads() throws Exception {
    mvc.perform(
            post("/api/processing-teams/teams")
                .with(as(foreignAdmin))
                .with(csrf())
                .contentType("application/json")
                .content(
                    body(
                        Map.of(
                            "name",
                            "Foreign team",
                            "fullName",
                            "Foreign Lead",
                            "username",
                            "foreign.lead",
                            "email",
                            "foreign@example.test",
                            "password",
                            PW,
                            "reason",
                            "Testing scope"))))
        .andExpect(status().isForbidden());
    var result = createTeam("one");
    var t = team(result);
    assertEquals("ROLE_CASE_TEAM_LEAD", lead(t).getRole());
    assertTrue(encoder.matches(PW, lead(t).getPasswordHash()));
    assertFalse(result.toString().contains(PW));
    assertTrue(result.path("registrationPath").asText().contains(t.registrationKey));
    mvc.perform(get("/api/processing-teams/invitations/" + t.registrationKey))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.teamCode").value(t.code()))
        .andExpect(jsonPath("$.remainingSeats").value(10))
        .andExpect(jsonPath("$.leadName").value("Synthetic Team Lead"))
        .andExpect(jsonPath("$.employees").doesNotExist());
    mvc.perform(get("/api/processing-teams/invitations/code/" + t.code()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.registrationKey").value(t.registrationKey))
        .andExpect(jsonPath("$.teamCode").value(t.code()));
    mvc.perform(get("/api/processing-teams/invitations/code/CP-TL-999999"))
        .andExpect(status().isNotFound());
    t.registrationOpen = false;
    teams.saveAndFlush(t);
    mvc.perform(get("/api/processing-teams/invitations/code/" + t.code()))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/cases").with(as(lead(t)))).andExpect(status().isForbidden());
    mvc.perform(get("/api/administration/users").with(as(lead(t))))
        .andExpect(status().isForbidden());
  }

  @Test
  @org.springframework.test.annotation.DirtiesContext(
      methodMode = org.springframework.test.annotation.DirtiesContext.MethodMode.BEFORE_METHOD)
  void anonymousReadsDoNotReplaceTheTokenUsedByTeamRegistration() throws Exception {
    // A fresh filter chain is needed: MockMvc's csrf() helper used by other
    // tests installs a session-token test repository on the shared filter.
    var catalogResponse =
        mvc.perform(get("/api/auth/access-roles"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse();
    var invitationResponse =
        mvc.perform(get("/api/processing-teams/invitations/not-a-valid-invite"))
            .andExpect(status().isNotFound())
            .andReturn()
            .getResponse();
    assertNull(catalogResponse.getCookie("XSRF-TOKEN"));
    assertNull(invitationResponse.getCookie("XSRF-TOKEN"));
    var security =
        mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn().getResponse();
    var cookie = security.getCookie("XSRF-TOKEN");
    assertNotNull(cookie);
    String token = json.readTree(security.getContentAsString()).path("token").asText();
    assertFalse(token.isBlank());
    mvc.perform(
            post("/api/auth/role-selection")
                .cookie(cookie)
                .header("X-XSRF-TOKEN", token)
                .contentType("application/json")
                .content(body(Map.of("role", "ROLE_CASE_PROCESSOR"))))
        .andExpect(status().isOk());
  }

  @Test
  void dedicatedTeamLeadLoginCannotUseEmployeeOrRoleAdminPortal() throws Exception {
    ProcessingTeam t = team(createTeam("login"));
    String username = lead(t).getUsername();
    for (String mode : List.of("USER", "ROLE_ADMIN"))
      mvc.perform(
              post("/api/auth/login")
                  .with(csrf())
                  .contentType("application/json")
                  .content(
                      body(
                          Map.of(
                              "username",
                              username,
                              "password",
                              PW,
                              "applicationRole",
                              "ROLE_CASE_PROCESSOR",
                              "loginMode",
                              mode))))
          .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/auth/login")
                .with(csrf())
                .contentType("application/json")
                .content(
                    body(
                        Map.of(
                            "username",
                            username,
                            "password",
                            PW,
                            "applicationRole",
                            "ROLE_CASE_PROCESSOR",
                            "loginMode",
                            "TEAM_LEAD"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.user.administrativeLevel").value("TEAM_LEAD"));
  }

  @Test
  void publicLinkRoutesApprovalsToTheLeadAndCentralAdminOnly() throws Exception {
    ProcessingTeam a = team(createTeam("a")), b = team(createTeam("b"));
    mvc.perform(get("/api/processing-teams/invitations/" + a.registrationKey))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.teamCode").value(a.code()))
        .andExpect(jsonPath("$.employees").doesNotExist());
    JsonNode r = register(a, "request");
    long request = r.path("requestId").asLong();
    mvc.perform(get("/api/processing-teams/registrations").with(as(lead(b))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
    mvc.perform(
            post("/api/processing-teams/registrations/" + request + "/approve")
                .with(as(lead(b)))
                .with(csrf())
                .contentType("application/json")
                .content(body(Map.of("reason", "Trying another team"))))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/processing-teams/registrations").with(as(central)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1));
    mvc.perform(
            post("/api/processing-teams/registrations/" + request + "/approve")
                .with(as(lead(a)))
                .with(csrf())
                .contentType("application/json")
                .content(body(Map.of("reason", "Team qualifications verified"))))
        .andExpect(status().isOk());
    var user = users.findByUsername("employee.request").orElseThrow();
    assertTrue(user.isEnabled());
    assertEquals("ACTIVE", members.findByTeamIdAndUserId(a.id, user.getId()).orElseThrow().status);
    mvc.perform(
            post("/api/processing-teams/registrations/" + request + "/approve")
                .with(as(central))
                .with(csrf())
                .contentType("application/json")
                .content(body(Map.of("reason", "A second approval must not be applied"))))
        .andExpect(status().isConflict());
  }

  @Test
  void centralAdminCanApproveAndPendingRegistrationReservesCapacity() throws Exception {
    var t = team(createTeam("capacity"));
    for (int i = 0; i < 10; i++) register(t, "seat" + i);
    assertEquals(10, members.countByTeamIdAndStatusIn(t.id, List.of("PENDING", "ACTIVE")));
    long before = users.count();
    var d = registerPayloadForFullTeam(t);
    mvc.perform(
            post("/api/auth/register")
                .with(csrf())
                .contentType("application/json")
                .content(body(d)))
        .andExpect(status().isBadRequest());
    assertEquals(before, users.count());
    var r =
        registrations
            .findByUserId(users.findByUsername("employee.seat0").orElseThrow().getId())
            .orElseThrow();
    mvc.perform(
            post("/api/processing-teams/registrations/" + r.id + "/approve")
                .with(as(central))
                .with(csrf())
                .contentType("application/json")
                .content(body(Map.of("reason", "Central team approval"))))
        .andExpect(status().isOk());
  }

  Map<String, Object> registerPayloadForFullTeam(ProcessingTeam t) throws Exception {
    var config =
        json.readTree(getClass().getResourceAsStream("/validation/input-rules.json"))
            .path("registrationChoices");
    var details = new LinkedHashMap<String, String>();
    for (String f : AccessRoleCatalog.FIELDS.get("ROLE_CASE_PROCESSOR"))
      details.put(
          f, config.path(f).isArray() ? config.path(f).get(0).asText() : "Training evidence");
    Map<String, Object> d = new LinkedHashMap<>();
    d.put("selectionToken", catalog.issue("ROLE_CASE_PROCESSOR"));
    d.put("fullName", "Synthetic Employee");
    d.put("employeeId", "EMP-overflow");
    d.put("email", "overflow@example.test");
    d.put("mobileNumber", "+15551234567");
    d.put("department", config.path("department").get(0).asText());
    d.put("designation", config.path("designation").get(0).asText());
    d.put("organization", "Synthetic Org");
    d.put("username", "overflow.employee");
    d.put("password", PW);
    d.put("confirmPassword", PW);
    d.put("reason", "Team capacity check");
    d.put("details", details);
    d.put("teamRegistrationKey", t.registrationKey);
    return d;
  }

  @Test
  void tenActiveTeamLimitDoesNotCreateAnEleventhLeadAccount() throws Exception {
    for (int i = 0; i < 10; i++) createTeam("limit" + i);
    long leads = users.countByRole("ROLE_CASE_TEAM_LEAD");
    mvc.perform(
            post("/api/processing-teams/teams")
                .with(as(central))
                .with(csrf())
                .contentType("application/json")
                .content(
                    body(
                        Map.of(
                            "name",
                            "Overflow",
                            "fullName",
                            "Extra Lead",
                            "username",
                            "extra.lead",
                            "email",
                            "extra@example.test",
                            "password",
                            PW,
                            "reason",
                            "Capacity validation"))))
        .andExpect(status().isBadRequest());
    assertEquals(leads, users.countByRole("ROLE_CASE_TEAM_LEAD"));
  }

  @Test
  void bulkAssignmentCreatesSeparateEmployeeWorkAndPrivatePdfs() throws Exception {
    var a = team(createTeam("bulkA"));
    var b = team(createTeam("bulkB"));
    User first = employee(a, "first.employee"),
        second = employee(a, "second.employee"),
        other = employee(b, "other.employee");
    JsonNode result =
        parsed(
            mvc.perform(
                    post("/api/processing-teams/work")
                        .with(as(lead(a)))
                        .with(csrf())
                        .contentType("application/json")
                        .content(body(work(a, "ALL_TEAM", null, "VALIDATION"))))
                .andExpect(status().isCreated())
                .andReturn());
    assertEquals(2, result.size());
    assertEquals(2, documents.count());
    mvc.perform(get("/api/processing-teams/work").with(as(first)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1));
    long secondWork =
        result.get(0).path("employee").path("userId").asLong() == second.getId()
            ? result.get(0).path("id").asLong()
            : result.get(1).path("id").asLong();
    mvc.perform(get("/api/processing-teams/work/" + secondWork).with(as(first)))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/processing-teams/work/" + secondWork).with(as(other)))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/processing-teams/work/" + secondWork).with(as(lead(b))))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/processing-teams/work/" + secondWork).with(as(central)))
        .andExpect(status().isOk());
    var doc =
        documents.findAll().stream()
            .filter(d -> d.assignment.id.equals(secondWork))
            .findFirst()
            .orElseThrow();
    byte[] bytes =
        mvc.perform(
                get("/api/processing-teams/work/" + secondWork + "/documents/" + doc.id + "/pdf")
                    .with(as(second)))
            .andExpect(status().isOk())
            .andExpect(content().contentType("application/pdf"))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    try (var pdf = Loader.loadPDF(bytes)) {
      var text = new PDFTextStripper().getText(pdf);
      assertTrue(text.contains("CASE PROCESSING ASSIGNMENT"));
      assertTrue(text.contains(second.getStaffCode()));
    }
    mvc.perform(
            get("/api/processing-teams/work/" + secondWork + "/documents/" + doc.id + "/pdf")
                .with(as(first)))
        .andExpect(status().isForbidden());
  }

  @Test
  void individualAssignmentRejectsAnotherTeamsStaffId() throws Exception {
    var a = team(createTeam("scopeA"));
    var b = team(createTeam("scopeB"));
    User other = employee(b, "wrong.employee");
    mvc.perform(
            post("/api/processing-teams/work")
                .with(as(lead(a)))
                .with(csrf())
                .contentType("application/json")
                .content(body(work(a, "INDIVIDUAL", other, "INTAKE"))))
        .andExpect(status().isForbidden());
    assertEquals(0, orders.count());
    assertEquals(0, documents.count());
  }

  @Test
  void progressResponsesAreVersionedValidatedAndPdfHistoryIsPreserved() throws Exception {
    var t = team(createTeam("progress"));
    User e = employee(t, "progress.employee");
    long id = assign(t, e, "VALIDATION").path("id").asLong();
    var first = progress(0, "BLOCKED", 25, false);
    mvc.perform(
            post("/api/processing-teams/work/" + id + "/updates")
                .with(as(e))
                .with(csrf())
                .contentType("application/json")
                .content(body(first)))
        .andExpect(status().isCreated());
    mvc.perform(
            post("/api/processing-teams/work/" + id + "/updates")
                .with(as(e))
                .with(csrf())
                .contentType("application/json")
                .content(body(first)))
        .andExpect(status().isConflict());
    long version = assignments.findById(id).orElseThrow().rowVersion;
    mvc.perform(
            post("/api/processing-teams/work/" + id + "/updates")
                .with(as(e))
                .with(csrf())
                .contentType("application/json")
                .content(body(progress(version, "COMPLETED", 100, false))))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/processing-teams/work/" + id + "/updates")
                .with(as(e))
                .with(csrf())
                .contentType("application/json")
                .content(body(progress(version, "COMPLETED", 100, true))))
        .andExpect(status().isCreated());
    assertEquals(2, updates.count());
    assertEquals(3, documents.count());
    var original =
        documents.findAll().stream()
            .filter(d -> d.documentKind.equals("EMPLOYEE_RESPONSE"))
            .findFirst()
            .orElseThrow();
    byte[] saved = original.pdfBytes.clone();
    mvc.perform(
            post("/api/processing-teams/work/" + id + "/reopen")
                .with(as(lead(t)))
                .with(csrf())
                .contentType("application/json")
                .content(body(Map.of("reason", "Correct the source event description"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.supervisorNote").value("Correct the source event description"));
    byte[] downloaded =
        mvc.perform(
                get("/api/processing-teams/work/" + id + "/documents/" + original.id + "/pdf")
                    .with(as(lead(t))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    assertArrayEquals(saved, downloaded);
    assertEquals(99, assignments.findById(id).orElseThrow().progress);
  }

  @Test
  void leadAndAdminCannotImpersonateAnEmployeesProgress() throws Exception {
    var t = team(createTeam("noimpersonation"));
    User e = employee(t, "own.employee");
    long id = assign(t, e, "INTAKE").path("id").asLong();
    for (User actor : List.of(lead(t), central))
      mvc.perform(
              post("/api/processing-teams/work/" + id + "/updates")
                  .with(as(actor))
                  .with(csrf())
                  .contentType("application/json")
                  .content(body(progress(0, "COMPLETED", 100, true))))
          .andExpect(status().isForbidden());
    assertEquals(0, updates.count());
  }

  @Test
  void caseAllocationChangesOnlyTheAssignedProcessorWithinTheTeam() throws Exception {
    var t = team(createTeam("caseallocation"));
    User original = employee(t, "original.processor"), next = employee(t, "next.processor");
    SafetyCase c = new SafetyCase();
    c.setCaseNumber("TEAM-CASE");
    c.setCountryCode("USA");
    c.setReportType("Spontaneous");
    c.setInitialReceiptDate(LocalDate.now());
    c.setLatestReceivedDate(LocalDate.now());
    c.setAssignedToUser(original);
    cases.saveAndFlush(c);
    var request = work(t, "INDIVIDUAL", next, "INTAKE");
    request.put("caseNumber", "TEAM-CASE");
    mvc.perform(
            post("/api/processing-teams/work")
                .with(as(lead(t)))
                .with(csrf())
                .contentType("application/json")
                .content(body(request)))
        .andExpect(status().isCreated());
    var allocated = assignments.findAll().get(0);
    mvc.perform(
            get("/api/processing-teams/work/" + allocated.id + "/clinical-case").with(as(lead(t))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.caseNumber").value("TEAM-CASE"));
    mvc.perform(get("/api/cases/" + c.getId()).with(as(lead(t)))).andExpect(status().isForbidden());
    SafetyCase saved = cases.findById(c.getId()).orElseThrow();
    assertEquals(next.getId(), saved.getAssignedToUser().getId());
    assertEquals("Data Entry", saved.getWorkflowState());
  }

  @Test
  void pdfFailureRollsBackAssignmentAndProgressAtomically() throws Exception {
    var t = team(createTeam("atomic"));
    User e = employee(t, "atomic.employee");
    doThrow(new java.io.IOException("Synthetic renderer failure"))
        .when(renderer)
        .render(any(JsonNode.class), any(byte[].class));
    mvc.perform(
            post("/api/processing-teams/work")
                .with(as(lead(t)))
                .with(csrf())
                .contentType("application/json")
                .content(body(work(t, "INDIVIDUAL", e, "INTAKE"))))
        .andExpect(status().isBadRequest());
    assertEquals(0, assignments.count());
    assertEquals(0, orders.count());
    doCallRealMethod().when(renderer).render(any(JsonNode.class), any(byte[].class));
    long id = assign(t, e, "INTAKE").path("id").asLong();
    doThrow(new java.io.IOException("Synthetic renderer failure"))
        .when(renderer)
        .render(any(JsonNode.class), any(byte[].class));
    mvc.perform(
            post("/api/processing-teams/work/" + id + "/updates")
                .with(as(e))
                .with(csrf())
                .contentType("application/json")
                .content(body(progress(0, "COMPLETED", 100, true))))
        .andExpect(status().isBadRequest());
    assertEquals(0, updates.count());
    assertEquals("NOT_STARTED", assignments.findById(id).orElseThrow().status);
    assertEquals(1, documents.count());
  }

  @Test
  void actualDatabaseRoleDefeatsForgedRequestAuthorities() throws Exception {
    var t = team(createTeam("dbrole"));
    User employee = employee(t, "fake.admin.employee");
    mvc.perform(
            get("/api/processing-teams/unassigned-employees")
                .with(user(employee.getUsername()).roles("SCOPED_ADMIN")))
        .andExpect(status().isForbidden());
  }

  Map<String, Object> clinicalInput(boolean coded) {
    var d = new LinkedHashMap<String, Object>();
    d.put("countryCode", "USA");
    d.put("reportType", "Spontaneous");
    d.put("initialReceiptDate", LocalDate.now().toString());
    d.put("latestReceivedDate", LocalDate.now().toString());
    d.put("priority", "Routine");
    d.put("patient", Map.of("patientIdentifier", "SYNTHETIC-TASK-PATIENT", "gender", "Unknown"));
    d.put(
        "reporters",
        List.of(
            Map.of(
                "primary",
                true,
                "qualification",
                "Physician",
                "firstName",
                "Synthetic",
                "lastName",
                "Reporter",
                "countryCode",
                "USA")));
    d.put("products", List.of(Map.of("productType", "Suspect", "productName", "Synthetic Drug")));
    var ev = new LinkedHashMap<String, Object>();
    ev.put("eventVerbatim", "Synthetic rash");
    ev.put("onsetDate", LocalDate.now().toString());
    ev.put("outcome", "Unknown");
    if (coded) {
      ev.put("meddraPt", "Rash");
      ev.put("meddraLlt", "Rash");
      ev.put("meddraSoc", "Skin and subcutaneous tissue disorders");
    }
    d.put("events", List.of(ev));
    d.put(
        "narrative",
        "Fictional source receipt, patient, drug exposure, event timeline and outcome for workflow"
            + " verification.");
    return d;
  }

  JsonNode createClinical(long id, User employee, boolean coded) throws Exception {
    return parsed(
        mvc.perform(
                post("/api/processing-teams/work/" + id + "/clinical-case")
                    .with(as(employee))
                    .with(csrf())
                    .contentType("application/json")
                    .content(body(clinicalInput(coded))))
            .andExpect(status().isCreated())
            .andReturn());
  }

  Map<String, Object> review(long version, String kind, String outcome) {
    return Map.of(
        "expectedCaseVersion",
        version,
        "kind",
        kind,
        "outcome",
        outcome,
        "notes",
        "Source identifiers, product/event coding and dictionary release checked against fictional"
            + " source documents.",
        "dictionaryReference",
        kind.equals("CODING")
            ? "Licensed dictionary release and source product code reference"
            : "");
  }

  @Test
  void employeeCreatesClinicalWorkAndSupervisorsInspectWithoutEditing() throws Exception {
    var t = team(createTeam("workstation"));
    var e = employee(t, "clinical.worker");
    var colleague = employee(t, "clinical.colleague");
    var request = work(t, "INDIVIDUAL", e, "INTAKE");
    request.put("clinicalRequired", true);
    long id =
        parsed(
                mvc.perform(
                        post("/api/processing-teams/work")
                            .with(as(lead(t)))
                            .with(csrf())
                            .contentType("application/json")
                            .content(body(request)))
                    .andExpect(status().isCreated())
                    .andReturn())
            .get(0)
            .path("id")
            .asLong();
    mvc.perform(
            post("/api/processing-teams/work/" + id + "/updates")
                .with(as(e))
                .with(csrf())
                .contentType("application/json")
                .content(body(progress(0, "COMPLETED", 100, true))))
        .andExpect(status().isBadRequest());
    var c = createClinical(id, e, false);
    assertEquals(e.getUsername(), c.path("assignedToUsername").asText());
    for (User supervisor : List.of(lead(t), central, root))
      mvc.perform(get("/api/processing-teams/work/" + id + "/clinical-case").with(as(supervisor)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.patient.patientIdentifier").value("SYNTHETIC-TASK-PATIENT"));
    mvc.perform(get("/api/processing-teams/work/" + id + "/clinical-case").with(as(colleague)))
        .andExpect(status().isForbidden());
    for (User actor : List.of(lead(t), central, colleague))
      mvc.perform(
              post("/api/processing-teams/work/" + id + "/clinical-case")
                  .with(as(actor))
                  .with(csrf())
                  .contentType("application/json")
                  .content(body(clinicalInput(false))))
          .andExpect(status().isForbidden());
    long before = cases.count();
    mvc.perform(
            post("/api/processing-teams/work/" + id + "/clinical-case")
                .with(as(e))
                .with(csrf())
                .contentType("application/json")
                .content(body(clinicalInput(false))))
        .andExpect(status().isConflict());
    assertEquals(before, cases.count());
    mvc.perform(
            post("/api/processing-teams/work/" + id + "/updates")
                .with(as(e))
                .with(csrf())
                .contentType("application/json")
                .content(body(progress(0, "COMPLETED", 100, true))))
        .andExpect(status().isCreated());
    var pdf =
        documents.findAll().stream()
            .filter(x -> "EMPLOYEE_RESPONSE".equals(x.documentKind))
            .findFirst()
            .orElseThrow();
    assertTrue(pdf.snapshotJson.contains("clinicalCaseAtResponse"));
    assertTrue(pdf.snapshotJson.contains("SYNTHETIC-TASK-PATIENT"));
  }

  @Test
  void completionUsesSavedCaseFactsRatherThanOnlyEmployeeCheckboxes() throws Exception {
    var t = team(createTeam("realcriteria"));
    var e = employee(t, "criteria.worker");
    long id = assign(t, e, "VALIDATION").path("id").asLong();
    var input = clinicalInput(false);
    input.put("patient", Map.of("gender", "Unknown"));
    input.put(
        "reporters",
        List.of(Map.of("primary", true, "qualification", "Physician", "countryCode", "USA")));
    input.put("draft", true);
    mvc.perform(
            post("/api/processing-teams/work/" + id + "/clinical-case")
                .with(as(e))
                .with(csrf())
                .contentType("application/json")
                .content(body(input)))
        .andExpect(status().isCreated());
    mvc.perform(
            post("/api/processing-teams/work/" + id + "/updates")
                .with(as(e))
                .with(csrf())
                .contentType("application/json")
                .content(body(progress(0, "COMPLETED", 100, true))))
        .andExpect(status().isBadRequest());
    assertEquals(0, updates.count());
    assertEquals("NOT_STARTED", assignments.findById(id).orElseThrow().status);
  }

  @Test
  void unresolvedOrOutdatedReviewCannotCompleteLinkedClinicalStage() throws Exception {
    var t = team(createTeam("revisionchecks"));
    var e = employee(t, "coding.worker");
    long id = assign(t, e, "DUPLICATE_REVIEW").path("id").asLong();
    var c = createClinical(id, e, true);
    long revision = c.path("rowVersion").asLong();
    for (String outcome : List.of("NO_DUPLICATE", "POTENTIAL_DUPLICATE"))
      mvc.perform(
              post("/api/processing-teams/work/" + id + "/processing-evidence")
                  .with(as(e))
                  .with(csrf())
                  .contentType("application/json")
                  .content(body(review(revision, "DUPLICATE_REVIEW", outcome))))
          .andExpect(status().isOk());
    var completion = progress(0, "COMPLETED", 100, true);
    completion.put("checklist", Map.of("duplicateChecked", true));
    mvc.perform(
            post("/api/processing-teams/work/" + id + "/updates")
                .with(as(e))
                .with(csrf())
                .contentType("application/json")
                .content(body(completion)))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/processing-teams/work/" + id + "/processing-evidence")
                .with(as(e))
                .with(csrf())
                .contentType("application/json")
                .content(body(review(revision, "DUPLICATE_REVIEW", "DISTINCT_CASE"))))
        .andExpect(status().isOk());
    var changed = clinicalInput(true);
    changed.put("expectedRowVersion", revision);
    changed.put("companyComments", "New source evidence changes the case revision");
    var updated =
        parsed(
            mvc.perform(
                    put("/api/cases/" + c.path("id").asLong())
                        .with(as(e))
                        .with(csrf())
                        .contentType("application/json")
                        .content(body(changed)))
                .andExpect(status().isOk())
                .andReturn());
    mvc.perform(
            post("/api/processing-teams/work/" + id + "/processing-evidence")
                .with(as(e))
                .with(csrf())
                .contentType("application/json")
                .content(body(review(revision, "DUPLICATE_REVIEW", "NO_DUPLICATE"))))
        .andExpect(status().isConflict());
    mvc.perform(
            post("/api/processing-teams/work/" + id + "/updates")
                .with(as(e))
                .with(csrf())
                .contentType("application/json")
                .content(body(completion)))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/processing-teams/work/" + id + "/processing-evidence")
                .with(as(e))
                .with(csrf())
                .contentType("application/json")
                .content(
                    body(
                        review(
                            updated.path("rowVersion").asLong(),
                            "DUPLICATE_REVIEW",
                            "NO_DUPLICATE"))))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/processing-teams/work/" + id + "/updates")
                .with(as(e))
                .with(csrf())
                .contentType("application/json")
                .content(body(completion)))
        .andExpect(status().isCreated());
  }

  @Test
  void codingRequiresSavedTermsAndReviewIsEmployeeOnly() throws Exception {
    var t = team(createTeam("codingevidence"));
    var e = employee(t, "terms.worker");
    long id = assign(t, e, "CODING").path("id").asLong();
    var c = createClinical(id, e, false);
    mvc.perform(
            post("/api/processing-teams/work/" + id + "/processing-evidence")
                .with(as(e))
                .with(csrf())
                .contentType("application/json")
                .content(body(review(c.path("rowVersion").asLong(), "CODING", "VERIFIED"))))
        .andExpect(status().isBadRequest());
    var changed = clinicalInput(true);
    changed.put("expectedRowVersion", c.path("rowVersion").asLong());
    var updated =
        parsed(
            mvc.perform(
                    put("/api/cases/" + c.path("id").asLong())
                        .with(as(e))
                        .with(csrf())
                        .contentType("application/json")
                        .content(body(changed)))
                .andExpect(status().isOk())
                .andReturn());
    for (User actor : List.of(lead(t), central, foreignAdmin))
      mvc.perform(
              post("/api/processing-teams/work/" + id + "/processing-evidence")
                  .with(as(actor))
                  .with(csrf())
                  .contentType("application/json")
                  .content(body(review(updated.path("rowVersion").asLong(), "CODING", "VERIFIED"))))
          .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/processing-teams/work/" + id + "/processing-evidence")
                .with(as(e))
                .with(csrf())
                .contentType("application/json")
                .content(body(review(updated.path("rowVersion").asLong(), "CODING", "VERIFIED"))))
        .andExpect(status().isOk());
    var completion = progress(0, "COMPLETED", 100, true);
    completion.put("checklist", Map.of("codingReviewed", true));
    completion.put("codingReference", "See preserved dictionary verification");
    mvc.perform(
            post("/api/processing-teams/work/" + id + "/updates")
                .with(as(e))
                .with(csrf())
                .contentType("application/json")
                .content(body(completion)))
        .andExpect(status().isCreated());
  }

  @Test
  void employeeCannotLinkColleagueCaseAndInactiveTeamsCannotCreateClinicalWork() throws Exception {
    var t = team(createTeam("linkboundaries"));
    var e = employee(t, "link.worker");
    var other = employee(t, "link.other");
    long otherId = assign(t, other, "INTAKE").path("id").asLong();
    var c = createClinical(otherId, other, false);
    long id = assign(t, e, "INTAKE").path("id").asLong();
    mvc.perform(
            post("/api/processing-teams/work/" + id + "/clinical-case/link")
                .with(as(e))
                .with(csrf())
                .contentType("application/json")
                .content(body(Map.of("caseNumber", c.path("caseNumber").asText()))))
        .andExpect(status().isForbidden());
    long own = assign(t, other, "NARRATIVE").path("id").asLong();
    mvc.perform(
            post("/api/processing-teams/work/" + own + "/clinical-case/link")
                .with(as(other))
                .with(csrf())
                .contentType("application/json")
                .content(body(Map.of("caseNumber", c.path("caseNumber").asText()))))
        .andExpect(status().isOk());
    t.active = false;
    teams.saveAndFlush(t);
    long before = cases.count();
    mvc.perform(
            post("/api/processing-teams/work/" + id + "/clinical-case")
                .with(as(e))
                .with(csrf())
                .contentType("application/json")
                .content(body(clinicalInput(false))))
        .andExpect(status().isForbidden());
    assertEquals(before, cases.count());
  }

  @Test
  void medicalHandoffRequiresCompletedVerifiedClinicalWorkAndPreservesReviewerSeparation()
      throws Exception {
    var t = team(createTeam("clinicalhandoff"));
    var e = employee(t, "handoff.worker");
    long id = assign(t, e, "MEDICAL_HANDOFF").path("id").asLong();
    var c = createClinical(id, e, true);
    long revision = c.path("rowVersion").asLong();
    var handoff =
        Map.of(
            "expectedCaseVersion",
            revision,
            "reason",
            "Complete source verification, duplicate and coding review; ready for clinical review");
    mvc.perform(
            post("/api/processing-teams/work/" + id + "/medical-handoff")
                .with(as(e))
                .with(csrf())
                .contentType("application/json")
                .content(body(handoff)))
        .andExpect(status().isBadRequest());
    for (var r :
        List.of(
            review(revision, "DUPLICATE_REVIEW", "NO_DUPLICATE"),
            review(revision, "CODING", "VERIFIED")))
      mvc.perform(
              post("/api/processing-teams/work/" + id + "/processing-evidence")
                  .with(as(e))
                  .with(csrf())
                  .contentType("application/json")
                  .content(body(r)))
          .andExpect(status().isOk());
    mvc.perform(
            post("/api/processing-teams/work/" + id + "/updates")
                .with(as(e))
                .with(csrf())
                .contentType("application/json")
                .content(body(progress(0, "COMPLETED", 100, true))))
        .andExpect(status().isCreated());
    for (User actor : List.of(lead(t), central))
      mvc.perform(
              post("/api/processing-teams/work/" + id + "/medical-handoff")
                  .with(as(actor))
                  .with(csrf())
                  .contentType("application/json")
                  .content(body(handoff)))
          .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/processing-teams/work/" + id + "/medical-handoff")
                .with(as(e))
                .with(csrf())
                .contentType("application/json")
                .content(body(handoff)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.workflowState").value("Medical Review"));
    mvc.perform(get("/api/processing-teams/work/" + id + "/clinical-case").with(as(central)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.workflowState").value("Medical Review"));
    var changed = clinicalInput(true);
    changed.put("expectedRowVersion", revision + 1);
    mvc.perform(
            put("/api/cases/" + c.path("id").asLong())
                .with(as(e))
                .with(csrf())
                .contentType("application/json")
                .content(body(changed)))
        .andExpect(status().isForbidden());
  }

  @Test
  void partialResponseCannotArchiveClinicalDataAfterCaseReassignment() throws Exception {
    var t = team(createTeam("partialscope"));
    var e = employee(t, "partial.employee");
    var next = employee(t, "partial.next");
    long id = assign(t, e, "INTAKE").path("id").asLong();
    var created = createClinical(id, e, false);
    var c = cases.findById(created.path("id").asLong()).orElseThrow();
    c.setAssignedToUser(next);
    cases.saveAndFlush(c);
    long before = documents.count();
    mvc.perform(
            post("/api/processing-teams/work/" + id + "/updates")
                .with(as(e))
                .with(csrf())
                .contentType("application/json")
                .content(body(progress(0, "IN_PROGRESS", 30, true))))
        .andExpect(status().isForbidden());
    assertEquals(0, updates.count());
    assertEquals(before, documents.count());
  }
}
