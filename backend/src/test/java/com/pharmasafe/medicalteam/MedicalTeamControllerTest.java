package com.pharmasafe.medicalteam;

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
        "spring.datasource.url=jdbc:h2:mem:medical_team_hierarchy;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;INIT=CREATE"
            + " SEQUENCE IF NOT EXISTS case_number_seq START WITH 1001")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.test.annotation.DirtiesContext(
    classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class MedicalTeamControllerTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate jdbc;
  @Autowired UserRepository users;
  @Autowired RoleAdminAssignmentRepository owners;
  @Autowired MedicalTeamRepository teams;
  @Autowired MedicalMemberRepository members;
  @Autowired MedicalAssignmentRepository assignments;
  @Autowired MedicalWorkOrderRepository orders;
  @Autowired MedicalWorkUpdateRepository updates;
  @Autowired MedicalWorkDocumentRepository documents;
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
          connection.getMetaData().getURL().startsWith("jdbc:h2:mem:medical_team_hierarchy"));
    }
    // Only this isolated in-memory test database is cleared; no production settings are loaded.
    jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
    for (String table :
        List.of(
            "mr_review_conclusions",
            "mr_case_allocation_events",
            "mr_case_allocations",
            "mr_work_documents",
            "mr_work_updates",
            "mr_work_assignments",
            "mr_work_orders",
            "registration_details",
            "registration_requests",
            "mr_team_members",
            "mr_teams",
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
    var owner = new RoleAdminAssignment("ROLE_MEDICAL_REVIEWER");
    owner.administrator = central;
    owner.assignedBy = root;
    owner.assignedAt = Instant.now();
    owners.saveAndFlush(owner);
    var other = new RoleAdminAssignment("ROLE_CASE_PROCESSOR");
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
                post("/api/medical-teams/teams")
                    .with(as(central))
                    .with(csrf())
                    .contentType("application/json")
                    .content(
                        body(
                            Map.of(
                                "name",
                                "Medical Team " + suffix,
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

  MedicalTeam team(JsonNode d) {
    return teams.findById(d.path("id").asLong()).orElseThrow();
  }

  User lead(MedicalTeam t) {
    return users.findById(t.lead.getId()).orElseThrow();
  }

  User employee(MedicalTeam t, String name) {
    User u = new User(name, encoder.encode(PW), "ROLE_MEDICAL_REVIEWER");
    u.setFullName("Synthetic Employee");
    users.saveAndFlush(u);
    MedicalMember m = new MedicalMember();
    m.team = t;
    m.user = u;
    m.status = "ACTIVE";
    m.joinedAt = Instant.now();
    members.saveAndFlush(m);
    return u;
  }

  Map<String, Object> work(MedicalTeam t, String mode, User employee, String stage) {
    var d = new LinkedHashMap<String, Object>();
    d.put("teamId", t.id);
    d.put("clinicalRequired", false);
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

  JsonNode assign(MedicalTeam t, User employee, String stage) throws Exception {
    return parsed(
            mvc.perform(
                    post("/api/medical-teams/work")
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
                "sourceReviewed",
                valid,
                "seriousnessReviewed",
                true,
                "causalityReviewed",
                true,
                "expectednessReviewed",
                true),
            "evidenceReference",
            ""));
  }

  JsonNode register(MedicalTeam t, String suffix) throws Exception {
    var config =
        json.readTree(getClass().getResourceAsStream("/validation/input-rules.json"))
            .path("registrationChoices");
    var details = new LinkedHashMap<String, String>();
    for (String field : AccessRoleCatalog.FIELDS.get("ROLE_MEDICAL_REVIEWER")) {
      var choice = config.path(field);
      details.put(field, choice.isArray() ? choice.get(0).asText() : "Synthetic training evidence");
    }
    var d = new LinkedHashMap<String, Object>();
    d.put("selectionToken", catalog.issue("ROLE_MEDICAL_REVIEWER"));
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
    d.put("reason", "Joining this Case Medical team");
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
  void onlyCaseMedicalAdminCanCreateAndProvisionTeamLeads() throws Exception {
    mvc.perform(
            post("/api/medical-teams/teams")
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
    assertEquals("ROLE_MEDICAL_TEAM_LEAD", lead(t).getRole());
    assertTrue(encoder.matches(PW, lead(t).getPasswordHash()));
    assertFalse(result.toString().contains(PW));
    assertTrue(result.path("registrationPath").asText().contains(t.registrationKey));
    mvc.perform(get("/api/medical-teams/invitations/" + t.registrationKey))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.teamCode").value(t.code()))
        .andExpect(jsonPath("$.remainingSeats").value(10))
        .andExpect(jsonPath("$.leadName").value("Synthetic Team Lead"))
        .andExpect(jsonPath("$.employees").doesNotExist());
    mvc.perform(get("/api/medical-teams/invitations/code/" + t.code()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.registrationKey").value(t.registrationKey))
        .andExpect(jsonPath("$.teamCode").value(t.code()));
    mvc.perform(get("/api/medical-teams/invitations/code/MR-TL-999999"))
        .andExpect(status().isNotFound());
    t.registrationOpen = false;
    teams.saveAndFlush(t);
    mvc.perform(get("/api/medical-teams/invitations/code/" + t.code()))
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
        mvc.perform(get("/api/medical-teams/invitations/not-a-valid-invite"))
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
                .content(body(Map.of("role", "ROLE_MEDICAL_REVIEWER"))))
        .andExpect(status().isOk());
  }

  @Test
  void dedicatedTeamLeadLoginCannotUseEmployeeOrRoleAdminPortal() throws Exception {
    MedicalTeam t = team(createTeam("login"));
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
                              "ROLE_MEDICAL_REVIEWER",
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
                            "ROLE_MEDICAL_REVIEWER",
                            "loginMode",
                            "TEAM_LEAD"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.user.administrativeLevel").value("TEAM_LEAD"));
  }

  @Test
  void publicLinkRoutesApprovalsToTheLeadAndCentralAdminOnly() throws Exception {
    MedicalTeam a = team(createTeam("a")), b = team(createTeam("b"));
    mvc.perform(get("/api/medical-teams/invitations/" + a.registrationKey))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.teamCode").value(a.code()))
        .andExpect(jsonPath("$.employees").doesNotExist());
    JsonNode r = register(a, "request");
    long request = r.path("requestId").asLong();
    mvc.perform(get("/api/medical-teams/registrations").with(as(lead(b))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
    mvc.perform(
            post("/api/medical-teams/registrations/" + request + "/approve")
                .with(as(lead(b)))
                .with(csrf())
                .contentType("application/json")
                .content(body(Map.of("reason", "Trying another team"))))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/medical-teams/registrations").with(as(central)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1));
    mvc.perform(
            post("/api/medical-teams/registrations/" + request + "/approve")
                .with(as(lead(a)))
                .with(csrf())
                .contentType("application/json")
                .content(body(Map.of("reason", "Team qualifications verified"))))
        .andExpect(status().isOk());
    var user = users.findByUsername("employee.request").orElseThrow();
    assertTrue(user.isEnabled());
    assertEquals("ACTIVE", members.findByTeamIdAndUserId(a.id, user.getId()).orElseThrow().status);
    mvc.perform(
            post("/api/medical-teams/registrations/" + request + "/approve")
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
            post("/api/medical-teams/registrations/" + r.id + "/approve")
                .with(as(central))
                .with(csrf())
                .contentType("application/json")
                .content(body(Map.of("reason", "Central team approval"))))
        .andExpect(status().isOk());
  }

  Map<String, Object> registerPayloadForFullTeam(MedicalTeam t) throws Exception {
    var config =
        json.readTree(getClass().getResourceAsStream("/validation/input-rules.json"))
            .path("registrationChoices");
    var details = new LinkedHashMap<String, String>();
    for (String f : AccessRoleCatalog.FIELDS.get("ROLE_MEDICAL_REVIEWER"))
      details.put(
          f, config.path(f).isArray() ? config.path(f).get(0).asText() : "Training evidence");
    Map<String, Object> d = new LinkedHashMap<>();
    d.put("selectionToken", catalog.issue("ROLE_MEDICAL_REVIEWER"));
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
    long leads = users.countByRole("ROLE_MEDICAL_TEAM_LEAD");
    mvc.perform(
            post("/api/medical-teams/teams")
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
    assertEquals(leads, users.countByRole("ROLE_MEDICAL_TEAM_LEAD"));
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
                    post("/api/medical-teams/work")
                        .with(as(lead(a)))
                        .with(csrf())
                        .contentType("application/json")
                        .content(body(work(a, "ALL_TEAM", null, "SERIOUSNESS_REVIEW"))))
                .andExpect(status().isCreated())
                .andReturn());
    assertEquals(2, result.size());
    assertEquals(2, documents.count());
    mvc.perform(get("/api/medical-teams/work").with(as(first)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1));
    long secondWork =
        result.get(0).path("employee").path("userId").asLong() == second.getId()
            ? result.get(0).path("id").asLong()
            : result.get(1).path("id").asLong();
    mvc.perform(get("/api/medical-teams/work/" + secondWork).with(as(first)))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/medical-teams/work/" + secondWork).with(as(other)))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/medical-teams/work/" + secondWork).with(as(lead(b))))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/medical-teams/work/" + secondWork).with(as(central)))
        .andExpect(status().isOk());
    var doc =
        documents.findAll().stream()
            .filter(d -> d.assignment.id.equals(secondWork))
            .findFirst()
            .orElseThrow();
    byte[] bytes =
        mvc.perform(
                get("/api/medical-teams/work/" + secondWork + "/documents/" + doc.id + "/pdf")
                    .with(as(second)))
            .andExpect(status().isOk())
            .andExpect(content().contentType("application/pdf"))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    try (var pdf = Loader.loadPDF(bytes)) {
      var text = new PDFTextStripper().getText(pdf);
      assertTrue(text.contains("MEDICAL REVIEW ASSIGNMENT"));
      assertTrue(text.contains(second.getStaffCode()));
    }
    mvc.perform(
            get("/api/medical-teams/work/" + secondWork + "/documents/" + doc.id + "/pdf")
                .with(as(first)))
        .andExpect(status().isForbidden());
  }

  @Test
  void individualAssignmentRejectsAnotherTeamsStaffId() throws Exception {
    var a = team(createTeam("scopeA"));
    var b = team(createTeam("scopeB"));
    User other = employee(b, "wrong.employee");
    mvc.perform(
            post("/api/medical-teams/work")
                .with(as(lead(a)))
                .with(csrf())
                .contentType("application/json")
                .content(body(work(a, "INDIVIDUAL", other, "SOURCE_REVIEW"))))
        .andExpect(status().isForbidden());
    assertEquals(0, orders.count());
    assertEquals(0, documents.count());
  }

  @Test
  void progressResponsesAreVersionedValidatedAndPdfHistoryIsPreserved() throws Exception {
    var t = team(createTeam("progress"));
    User e = employee(t, "progress.employee");
    long id = assign(t, e, "SERIOUSNESS_REVIEW").path("id").asLong();
    var first = progress(0, "BLOCKED", 25, false);
    mvc.perform(
            post("/api/medical-teams/work/" + id + "/updates")
                .with(as(e))
                .with(csrf())
                .contentType("application/json")
                .content(body(first)))
        .andExpect(status().isCreated());
    mvc.perform(
            post("/api/medical-teams/work/" + id + "/updates")
                .with(as(e))
                .with(csrf())
                .contentType("application/json")
                .content(body(first)))
        .andExpect(status().isConflict());
    long version = assignments.findById(id).orElseThrow().rowVersion;
    mvc.perform(
            post("/api/medical-teams/work/" + id + "/updates")
                .with(as(e))
                .with(csrf())
                .contentType("application/json")
                .content(body(progress(version, "COMPLETED", 100, false))))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/medical-teams/work/" + id + "/updates")
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
            post("/api/medical-teams/work/" + id + "/reopen")
                .with(as(lead(t)))
                .with(csrf())
                .contentType("application/json")
                .content(body(Map.of("reason", "Correct the source event description"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.supervisorNote").value("Correct the source event description"));
    byte[] downloaded =
        mvc.perform(
                get("/api/medical-teams/work/" + id + "/documents/" + original.id + "/pdf")
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
    long id = assign(t, e, "SOURCE_REVIEW").path("id").asLong();
    for (User actor : List.of(lead(t), central))
      mvc.perform(
              post("/api/medical-teams/work/" + id + "/updates")
                  .with(as(actor))
                  .with(csrf())
                  .contentType("application/json")
                  .content(body(progress(0, "COMPLETED", 100, true))))
          .andExpect(status().isForbidden());
    assertEquals(0, updates.count());
  }

  @Test
  void pdfFailureRollsBackAssignmentAndProgressAtomically() throws Exception {
    var t = team(createTeam("atomic"));
    User e = employee(t, "atomic.employee");
    doThrow(new java.io.IOException("Synthetic renderer failure"))
        .when(renderer)
        .render(any(JsonNode.class), any(byte[].class));
    mvc.perform(
            post("/api/medical-teams/work")
                .with(as(lead(t)))
                .with(csrf())
                .contentType("application/json")
                .content(body(work(t, "INDIVIDUAL", e, "SOURCE_REVIEW"))))
        .andExpect(status().isBadRequest());
    assertEquals(0, assignments.count());
    assertEquals(0, orders.count());
    doCallRealMethod().when(renderer).render(any(JsonNode.class), any(byte[].class));
    long id = assign(t, e, "SOURCE_REVIEW").path("id").asLong();
    doThrow(new java.io.IOException("Synthetic renderer failure"))
        .when(renderer)
        .render(any(JsonNode.class), any(byte[].class));
    mvc.perform(
            post("/api/medical-teams/work/" + id + "/updates")
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
            get("/api/medical-teams/unassigned-employees")
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

  JsonNode sourceCase() throws Exception {
    var cp =
        users.saveAndFlush(new User("source.processor", encoder.encode(PW), "ROLE_CASE_PROCESSOR"));
    var c =
        parsed(
            mvc.perform(
                    post("/api/cases")
                        .with(as(cp))
                        .with(csrf())
                        .contentType("application/json")
                        .content(body(clinicalInput(true))))
                .andExpect(status().isCreated())
                .andReturn());
    var entity = cases.findById(c.path("id").asLong()).orElseThrow();
    entity.setWorkflowState("Medical Review");
    cases.saveAndFlush(entity);
    return parsed(
        mvc.perform(get("/api/cases/" + entity.getId()).with(as(root)))
            .andExpect(status().isOk())
            .andReturn());
  }

  JsonNode clinicalAssignment(MedicalTeam t, User e, JsonNode c) throws Exception {
    mvc.perform(
            post("/api/medical-teams/teams/" + t.id + "/case-allocations")
                .with(as(central))
                .with(csrf())
                .contentType("application/json")
                .content(
                    body(
                        Map.of(
                            "caseNumber",
                            c.path("caseNumber").asText(),
                            "expectedCaseVersion",
                            c.path("rowVersion").asLong(),
                            "reason",
                            "Medical intake allocation"))))
        .andExpect(status().isOk());
    var d = work(t, "INDIVIDUAL", e, "QUALITY_HANDOFF");
    d.put("caseNumber", c.path("caseNumber").asText());
    d.put("clinicalRequired", true);
    return parsed(
            mvc.perform(
                    post("/api/medical-teams/work")
                        .with(as(lead(t)))
                        .with(csrf())
                        .contentType("application/json")
                        .content(body(d)))
                .andExpect(status().isCreated())
                .andReturn())
        .get(0);
  }

  JsonNode source(long assignment, User actor) throws Exception {
    return parsed(
        mvc.perform(get("/api/medical-teams/work/" + assignment + "/clinical-case").with(as(actor)))
            .andExpect(status().isOk())
            .andReturn());
  }

  void assessment(long assignment, User actor, long version) throws Exception {
    mvc.perform(
            post("/api/medical-teams/work/" + assignment + "/assessments")
                .with(as(actor))
                .with(csrf())
                .contentType("application/json")
                .content(
                    body(
                        Map.of(
                            "expectedCaseVersion",
                            version,
                            "productName",
                            "Synthetic Drug",
                            "eventVerbatim",
                            "Synthetic rash",
                            "causalityCompany",
                            "Possible",
                            "expectedness",
                            "Unexpected",
                            "assessmentComment",
                            "Reviewed synthetic source and clinical timeline"))))
        .andExpect(status().isOk());
  }

  @Test
  void medicalEvidenceCompletionAndQualityHandoffUseActualSavedData() throws Exception {
    var t = team(createTeam("clinical"));
    var e = employee(t, "medical.employee");
    var c = sourceCase();
    var a = clinicalAssignment(t, e, c);
    long id = a.path("id").asLong();
    mvc.perform(
            post("/api/medical-teams/work/" + id + "/updates")
                .with(as(e))
                .with(csrf())
                .contentType("application/json")
                .content(body(progress(0, "COMPLETED", 100, true))))
        .andExpect(status().isBadRequest());
    assessment(id, e, source(id, e).path("rowVersion").asLong());
    var current = source(id, e);
    mvc.perform(
            post("/api/medical-teams/work/" + id + "/conclusions")
                .with(as(e))
                .with(csrf())
                .contentType("application/json")
                .content(
                    body(
                        Map.of(
                            "expectedCaseVersion",
                            current.path("rowVersion").asLong(),
                            "medicalNarrative",
                            "Clinical timeline and source reviewed for synthetic rash",
                            "reviewSummary",
                            "Possible association with synthetic medicinal product",
                            "assessedSeriousness",
                            "NON_SERIOUS",
                            "severitySummary",
                            "Mild clinical severity",
                            "sourceReviewed",
                            true,
                            "timelineReviewed",
                            true))))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/medical-teams/work/" + id + "/updates")
                .with(as(e))
                .with(csrf())
                .contentType("application/json")
                .content(body(progress(0, "COMPLETED", 100, true))))
        .andExpect(status().isCreated());
    var d =
        parsed(
            mvc.perform(get("/api/medical-teams/work/" + id).with(as(lead(t))))
                .andExpect(status().isOk())
                .andReturn());
    assertEquals(2, d.path("documents").path("totalElements").asInt());
    for (var doc : d.path("documents").path("content")) {
      var pdf =
          mvc.perform(
                  get("/api/medical-teams/work/"
                          + id
                          + "/documents/"
                          + doc.path("id").asText()
                          + "/pdf")
                      .with(as(e)))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsByteArray();
      try (var loaded = Loader.loadPDF(pdf)) {
        assertTrue(new PDFTextStripper().getText(loaded).contains("MEDICAL REVIEW"));
      }
    }
    mvc.perform(
            post("/api/medical-teams/work/" + id + "/decision")
                .with(as(e))
                .with(csrf())
                .contentType("application/json")
                .content(
                    body(
                        Map.of(
                            "expectedCaseVersion",
                            source(id, e).path("rowVersion").asLong(),
                            "targetState",
                            "Quality Review",
                            "reason",
                            "Completed medical evidence ready for quality review"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.workflowState").value("Quality Review"));
  }

  @Test
  void departmentAndTeamBoundariesProtectClinicalSourcesAndAssignments() throws Exception {
    var t = team(createTeam("scope"));
    var e = employee(t, "reviewer.one");
    var other = team(createTeam("other"));
    var colleague = employee(other, "reviewer.two");
    var c = sourceCase();
    var a = clinicalAssignment(t, e, c);
    long id = a.path("id").asLong();
    var cp = users.findByUsername("source.processor").orElseThrow();
    for (var u : List.of(cp, foreignAdmin, colleague, lead(other))) {
      mvc.perform(get("/api/medical-teams/work/" + id).with(as(u)))
          .andExpect(status().isForbidden());
      mvc.perform(get("/api/medical-teams/work/" + id + "/clinical-case").with(as(u)))
          .andExpect(status().isForbidden());
    }
    mvc.perform(get("/api/cases/" + c.path("id").asLong()).with(as(colleague)))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/processing-teams/overview").with(as(e)))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/medical-teams/teams/" + t.id + "/members")
                .with(as(lead(t)))
                .with(csrf())
                .contentType("application/json")
                .content(
                    body(
                        Map.of(
                            "employeeStaffCode",
                            cp.getStaffCode(),
                            "reason",
                            "Wrong department must be rejected"))))
        .andExpect(status().isForbidden());
    for (var u : List.of(cp, lead(t))) {
      mvc.perform(
              post("/api/auth/login")
                  .with(csrf())
                  .contentType("application/json")
                  .content(
                      body(
                          Map.of(
                              "username",
                              u.getUsername(),
                              "password",
                              PW,
                              "applicationRole",
                              u == cp ? "ROLE_MEDICAL_REVIEWER" : "ROLE_CASE_PROCESSOR",
                              "loginMode",
                              u == cp ? "USER" : "TEAM_LEAD"))))
          .andExpect(status().isForbidden());
    }
    mvc.perform(
            post("/api/cases/" + c.path("id").asLong() + "/workflow-transition")
                .with(as(e))
                .with(csrf())
                .contentType("application/json")
                .content(body(Map.of("targetState", "Quality Review", "reason", "Bypass attempt"))))
        .andExpect(status().isForbidden());
  }

  @Test
  void blockedMedicalResponseReturnsToOriginalProcessorAndStaleEvidenceIsRejected()
      throws Exception {
    var t = team(createTeam("correction"));
    var e = employee(t, "medical.correction");
    var c = sourceCase();
    var a = clinicalAssignment(t, e, c);
    long id = a.path("id").asLong();
    long revision = source(id, e).path("rowVersion").asLong();
    assessment(id, e, revision);
    mvc.perform(
            post("/api/medical-teams/work/" + id + "/conclusions")
                .with(as(e))
                .with(csrf())
                .contentType("application/json")
                .content(
                    body(
                        Map.of(
                            "expectedCaseVersion",
                            revision,
                            "medicalNarrative",
                            "Synthetic source evidence",
                            "reviewSummary",
                            "Source needs correction",
                            "assessedSeriousness",
                            "UNDETERMINED",
                            "severitySummary",
                            "Insufficient evidence",
                            "sourceReviewed",
                            true,
                            "timelineReviewed",
                            true))))
        .andExpect(status().isConflict());
    mvc.perform(
            post("/api/medical-teams/work/" + id + "/updates")
                .with(as(e))
                .with(csrf())
                .contentType("application/json")
                .content(body(progress(0, "BLOCKED", 40, false))))
        .andExpect(status().isCreated());
    mvc.perform(
            post("/api/medical-teams/work/" + id + "/decision")
                .with(as(e))
                .with(csrf())
                .contentType("application/json")
                .content(
                    body(
                        Map.of(
                            "expectedCaseVersion",
                            source(id, e).path("rowVersion").asLong(),
                            "targetState",
                            "Data Entry",
                            "reason",
                            "Confirm missing clinical exposure dates"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.workflowState").value("Data Entry"));
    var entity = cases.findById(c.path("id").asLong()).orElseThrow();
    assertEquals(
        users.findByUsername("source.processor").orElseThrow().getId(),
        entity.getAssignedToUser().getId());
    mvc.perform(get("/api/medical-teams/work/" + id + "/clinical-case").with(as(e)))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/medical-teams/work/" + id).with(as(lead(t))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.documents.totalElements").value(2));
  }
}
