package com.pharmasafe.access;

import static org.junit.jupiter.api.Assertions.*;
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
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;

@SpringBootTest(
    properties =
        "spring.datasource.url=jdbc:h2:mem:scoped_administration;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;INIT=CREATE"
            + " SEQUENCE IF NOT EXISTS case_number_seq START WITH 1001")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.test.annotation.DirtiesContext(
    classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class ScopedAdministrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired UserRepository users;
  @Autowired RegistrationRequestRepository requests;
  @Autowired PasswordResetRequestRepository resets;
  @Autowired AdministrativeEventRepository events;
  @Autowired RoleAdminAssignmentRepository assignments;
  @Autowired AdministrationService administration;
  @Autowired UserAccessService access;
  @Autowired SafetyCaseRepository cases;
  @Autowired SafetyReportRepository reports;
  @Autowired AuditEventRepository activity;
  @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
  @Autowired PasswordEncoder encoder;
  @MockBean AccessMailService mail;
  @MockBean AccessRateLimiter limiter;
  static final String PW = "SyntheticScopedPassword123!",
      PROCESSOR = "ROLE_CASE_PROCESSOR",
      MEDICAL = "ROLE_MEDICAL_REVIEWER";
  User root, ra, alice, bob, medical;

  @BeforeEach
  void setup() {
    activity.deleteAll();
    events.deleteAll();
    assignments.deleteAll();
    reports.deleteAll();
    cases.deleteAll();
    resets.deleteAll();
    requests.deleteAll();
    users.deleteAll();
    root = users.saveAndFlush(new User("super-admin", encoder.encode(PW), "ROLE_ADMIN"));
    alice = employee("processor.a", PROCESSOR);
    bob = employee("processor.b", PROCESSOR);
    medical = employee("medical.a", MEDICAL);
    administration.provision(
        root.getUsername(),
        PROCESSOR,
        "processor-admin",
        "processor-admin@example.test",
        "Processor Administrator",
        PW,
        "Assigned by the existing Super Admin",
        0);
    ra = users.findByUsername("processor-admin").orElseThrow();
  }

  User employee(String username, String role) {
    User u = new User(username, encoder.encode(PW), role);
    u.setFullName("Alex Taylor");
    u.setEmail(username + "@example.test");
    return users.saveAndFlush(u);
  }

  RegistrationRequest pending(String username, String role) {
    User u = employee(username, role);
    u.setEnabled(false);
    u.setAccountStatus("PENDING");
    users.saveAndFlush(u);
    RegistrationRequest r = new RegistrationRequest();
    r.user = u;
    r.requestedRole = role;
    r.reason = "Assigned safety responsibilities";
    return requests.saveAndFlush(r);
  }

  org.springframework.test.web.servlet.request.RequestPostProcessor roleAdmin() {
    return user(ra.getUsername()).roles("SCOPED_ADMIN");
  }

  org.springframework.test.web.servlet.request.RequestPostProcessor superAdmin() {
    return user(root.getUsername()).roles("ADMIN");
  }

  ResultActions login(String name) throws Exception {
    return mvc.perform(
        post("/api/auth/login")
            .contentType("application/json")
            .content(json.writeValueAsBytes(Map.of("username", name, "password", PW))));
  }

  String decision() {
    return "{\"reason\":\"Identity and role verified\"}";
  }

  @Test
  void existingAdministratorIsSuperAdminAndEveryRoleHasRealStatistics() throws Exception {
    var logged =
        login(root.getUsername())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.administrativeLevel").value("SUPER_ADMIN"))
            .andExpect(jsonPath("$.user.staffCode").value(root.getStaffCode()))
            .andReturn();
    var session = (MockHttpSession) logged.getRequest().getSession(false);
    mvc.perform(get("/api/administration/overview").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.roles.length()").value(7))
        .andExpect(jsonPath("$.totalRoleAdmins").value(1))
        .andExpect(jsonPath("$.totalUsers").value(5));
    for (String role : AccessRoleCatalog.FIELDS.keySet())
      mvc.perform(get("/api/administration/overview").param("role", role).session(session))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.roles[0].role").value(role));
  }

  @Test
  void roleAdminHasSeparateAuthorityAndScopedLoginIdentity() throws Exception {
    var result =
        login(ra.getUsername())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.role").value("ROLE_SCOPED_ADMIN"))
            .andExpect(jsonPath("$.user.managedRole").value(PROCESSOR))
            .andExpect(jsonPath("$.user.administrativeLevel").value("ROLE_ADMIN"))
            .andReturn();
    var session = (MockHttpSession) result.getRequest().getSession(false);
    mvc.perform(get("/api/administration/users").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2));
    mvc.perform(get("/api/admin/status").session(session)).andExpect(status().isForbidden());
  }

  @Test
  void anUnassignedAdministratorCannotSignInOrAppearInPublicSignup() throws Exception {
    employee("unassigned.admin", AdminScope.SCOPED_ADMIN);
    login("unassigned.admin").andExpect(status().isForbidden());
    mvc.perform(get("/api/auth/access-roles"))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("ROLE_SCOPED_ADMIN"))));
  }

  @Test
  void dedicatedLoginPortalsRestrictIdentityRatherThanGrantingRequestedRoles() throws Exception {
    mvc.perform(
            post("/api/auth/login")
                .contentType("application/json")
                .content(
                    json.writeValueAsBytes(
                        Map.of(
                            "username",
                            alice.getUsername(),
                            "password",
                            PW,
                            "applicationRole",
                            PROCESSOR,
                            "loginMode",
                            "USER"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.user.role").value(PROCESSOR));
    for (var entry :
        List.of(
            new String[] {alice.getUsername(), MEDICAL, "USER"},
            new String[] {alice.getUsername(), PROCESSOR, "ROLE_ADMIN"},
            new String[] {ra.getUsername(), MEDICAL, "ROLE_ADMIN"},
            new String[] {ra.getUsername(), PROCESSOR, "USER"},
            new String[] {root.getUsername(), PROCESSOR, "ROLE_ADMIN"})) {
      var result =
          mvc.perform(
                  post("/api/auth/login")
                      .contentType("application/json")
                      .content(
                          json.writeValueAsBytes(
                              Map.of(
                                  "username",
                                  entry[0],
                                  "password",
                                  PW,
                                  "applicationRole",
                                  entry[1],
                                  "loginMode",
                                  entry[2]))))
              .andExpect(status().isForbidden())
              .andReturn();
      var session = result.getRequest().getSession(false);
      assertTrue(session == null || session.getAttribute("SPRING_SECURITY_CONTEXT") == null);
    }
    mvc.perform(
            post("/api/auth/login")
                .contentType("application/json")
                .content(
                    json.writeValueAsBytes(
                        Map.of(
                            "username",
                            ra.getUsername(),
                            "password",
                            PW,
                            "applicationRole",
                            PROCESSOR,
                            "loginMode",
                            "ROLE_ADMIN"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.user.managedRole").value(PROCESSOR));
    assertEquals(PROCESSOR, users.findById(alice.getId()).orElseThrow().getRole());
  }

  @Test
  void roleAndEmployeeAnalyticsUseTheSameDatabaseOwnershipAsTheWorkList() throws Exception {
    workCase("ANALYTICS-P", alice);
    workCase("ANALYTICS-M", medical);
    activity.saveAndFlush(
        new AuditEvent("CASE_UPDATED", alice.getUsername(), null, null, "SUCCESS", "Own activity"));
    activity.saveAndFlush(
        new AuditEvent(
            "CASE_UPDATED", medical.getUsername(), null, null, "SUCCESS", "Foreign activity"));
    mvc.perform(get("/api/administration/analytics").with(roleAdmin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalCases").value(1))
        .andExpect(jsonPath("$.totalActivity").value(1))
        .andExpect(jsonPath("$.caseStates['Data Entry']").value(1));
    mvc.perform(
            get("/api/administration/analytics")
                .param("userId", bob.getId().toString())
                .with(roleAdmin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalCases").value(0));
    mvc.perform(
            get("/api/administration/analytics")
                .param("userId", medical.getId().toString())
                .with(roleAdmin()))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/administration/analytics").param("role", MEDICAL).with(roleAdmin()))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/administration/analytics").with(superAdmin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalCases").value(2));
    mvc.perform(
            get("/api/administration/analytics")
                .with(user(alice.getUsername()).roles("CASE_PROCESSOR")))
        .andExpect(status().isForbidden());
  }

  @Test
  void duplicateEmployeeNamesHaveStableDifferentStaffCodesAndUsernames() throws Exception {
    assertEquals(alice.getFullName(), bob.getFullName());
    assertNotEquals(alice.getStaffCode(), bob.getStaffCode());
    mvc.perform(get("/api/administration/users").with(roleAdmin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].staffCode").value(alice.getStaffCode()))
        .andExpect(jsonPath("$.content[1].staffCode").value(bob.getStaffCode()))
        .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist());
  }

  @Test
  void roleParameterAndDirectUserIdsCannotCrossAdministrativeScope() throws Exception {
    mvc.perform(get("/api/administration/users").param("role", MEDICAL).with(roleAdmin()))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/administration/overview").param("role", MEDICAL).with(roleAdmin()))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/administration/users/" + medical.getId()).with(roleAdmin()))
        .andExpect(status().isForbidden());
    mvc.perform(
            put("/api/administration/users/" + medical.getId() + "/suspend")
                .with(roleAdmin())
                .with(csrf())
                .contentType("application/json")
                .content(decision()))
        .andExpect(status().isForbidden());
    mvc.perform(
            get("/api/administration/users")
                .param("search", alice.getStaffCode())
                .with(roleAdmin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1));
    assertTrue(users.findById(medical.getId()).orElseThrow().isEnabled());
    assertTrue(
        events.findAll().stream().filter(e -> e.action.equals("ACCESS_DENIED")).count() >= 4);
  }

  @Test
  void normalUsersAndRoleAdminsCannotUseGlobalAdministrationOrProvisioning() throws Exception {
    for (String url :
        List.of(
            "/api/admin/users",
            "/api/admin/registration-requests",
            "/api/workspace/overview",
            "/api/administration/eligible-administrators?role=" + MEDICAL))
      mvc.perform(get(url).with(roleAdmin())).andExpect(status().isForbidden());
    mvc.perform(
            post("/api/administration/assignments/" + MEDICAL)
                .with(roleAdmin())
                .with(csrf())
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isForbidden());
    mvc.perform(
            get("/api/administration/users")
                .with(user(alice.getUsername()).roles("CASE_PROCESSOR")))
        .andExpect(status().isForbidden());
    // Even a forged test authentication does not bypass the database identity policy.
    mvc.perform(get("/api/administration/users").with(user(alice.getUsername()).roles("ADMIN")))
        .andExpect(status().isForbidden());
  }

  @Test
  void onlyOwnRegistrationCanBeApprovedAndASecondReviewReturnsConflict() throws Exception {
    var own = pending("pending.processor", PROCESSOR);
    var foreign = pending("pending.medical", MEDICAL);
    mvc.perform(get("/api/administration/registrations").with(roleAdmin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1));
    mvc.perform(get("/api/administration/registrations/" + foreign.id).with(roleAdmin()))
        .andExpect(status().isForbidden());
    mvc.perform(
            put("/api/administration/registrations/" + foreign.id + "/approve")
                .with(roleAdmin())
                .with(csrf())
                .contentType("application/json")
                .content(decision()))
        .andExpect(status().isForbidden());
    mvc.perform(
            put("/api/administration/registrations/" + own.id + "/approve")
                .with(roleAdmin())
                .with(csrf())
                .contentType("application/json")
                .content("{\"reason\":\"Identity verified\",\"role\":\"ROLE_ADMIN\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value(PROCESSOR))
        .andExpect(jsonPath("$.reviewedBy").value(ra.getId()));
    login(own.user.getUsername()).andExpect(status().isOk());
    mvc.perform(
            put("/api/administration/registrations/" + own.id + "/approve")
                .with(superAdmin())
                .with(csrf())
                .contentType("application/json")
                .content(decision()))
        .andExpect(status().isConflict());
    assertEquals(
        1,
        events.findAll().stream()
            .filter(e -> "REGISTRATION_APPROVE".equals(e.action) && own.id.equals(e.requestId))
            .count());
    mvc.perform(
            get("/api/administration/registrations").param("status", "PENDING").with(superAdmin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1));
  }

  @Test
  void concurrentSuperAndRoleAdminApprovalOnlyCommitsOneDecision() throws Exception {
    var pending = pending("race.processor", PROCESSOR);
    var pool = Executors.newFixedThreadPool(2);
    var start = new CountDownLatch(1);
    try {
      var one =
          pool.submit(
              () -> {
                start.await();
                return mvc.perform(
                        put("/api/administration/registrations/" + pending.id + "/approve")
                            .with(roleAdmin())
                            .with(csrf())
                            .contentType("application/json")
                            .content(decision()))
                    .andReturn()
                    .getResponse()
                    .getStatus();
              });
      var two =
          pool.submit(
              () -> {
                start.await();
                return mvc.perform(
                        put("/api/administration/registrations/" + pending.id + "/approve")
                            .with(superAdmin())
                            .with(csrf())
                            .contentType("application/json")
                            .content(decision()))
                    .andReturn()
                    .getResponse()
                    .getStatus();
              });
      start.countDown();
      assertEquals(
          Set.of(200, 409), Set.of(one.get(20, TimeUnit.SECONDS), two.get(20, TimeUnit.SECONDS)));
    } finally {
      pool.shutdownNow();
    }
    var approved = requests.findById(pending.id).orElseThrow();
    assertNotNull(approved.reviewedBy);
    assertEquals(
        1,
        events.findAll().stream()
            .filter(e -> "REGISTRATION_APPROVE".equals(e.action) && pending.id.equals(e.requestId))
            .count());
  }

  @Test
  void administratorReplacementSerializesWithAnInFlightApproval() throws Exception {
    var request = pending("leased.processor", PROCESSOR);
    var leaseHeld = new CountDownLatch(1);
    var finish = new CountDownLatch(1);
    var pool = Executors.newFixedThreadPool(2);
    try {
      var approval =
          pool.submit(
              () ->
                  new org.springframework.transaction.support.TransactionTemplate(
                          transactionManager)
                      .execute(
                          status -> {
                            access.decideRegistration(
                                request.id, "approve", "Training verified", ra.getUsername());
                            leaseHeld.countDown();
                            try {
                              if (!finish.await(10, TimeUnit.SECONDS))
                                throw new IllegalStateException("Test approval timeout");
                            } catch (InterruptedException e) {
                              Thread.currentThread().interrupt();
                              throw new IllegalStateException(e);
                            }
                            return true;
                          }));
      assertTrue(leaseHeld.await(10, TimeUnit.SECONDS));
      var release =
          pool.submit(
              () ->
                  administration.release(
                      root.getUsername(), PROCESSOR, "Administrator replacement", 1));
      assertThrows(TimeoutException.class, () -> release.get(150, TimeUnit.MILLISECONDS));
      finish.countDown();
      assertTrue(approval.get(10, TimeUnit.SECONDS));
      release.get(10, TimeUnit.SECONDS);
      assertFalse(users.findById(ra.getId()).orElseThrow().isEnabled());
      assertEquals(
          "APPROVED", users.findById(request.user.getId()).orElseThrow().getAccountStatus());
    } finally {
      finish.countDown();
      pool.shutdownNow();
    }
  }

  @Test
  void pendingRejectedAndSuspendedUsersCannotLoginAndSuspensionRevokesSession() throws Exception {
    var pending = pending("status.processor", PROCESSOR);
    login(pending.user.getUsername()).andExpect(status().isForbidden());
    access.decideRegistration(pending.id, "approve", "Training checked", ra.getUsername());
    var session =
        (MockHttpSession)
            login(pending.user.getUsername())
                .andExpect(status().isOk())
                .andReturn()
                .getRequest()
                .getSession(false);
    mvc.perform(
            put("/api/administration/users/" + pending.user.getId() + "/suspend")
                .with(roleAdmin())
                .with(csrf())
                .contentType("application/json")
                .content(decision()))
        .andExpect(status().isOk());
    mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
    login(pending.user.getUsername()).andExpect(status().isForbidden());
    access.accountStatus(pending.user.getId(), "activate", "Access restored", ra.getUsername());
    login(pending.user.getUsername()).andExpect(status().isOk());
    var rejected = pending("rejected.processor", PROCESSOR);
    access.decideRegistration(rejected.id, "reject", "Not eligible", ra.getUsername());
    login(rejected.user.getUsername()).andExpect(status().isForbidden());
  }

  @Test
  void passwordResetApprovalsAndFiltersRespectOwnership() throws Exception {
    access.forgot(alice.getUsername());
    access.forgot(medical.getUsername());
    var own =
        resets.findAll().stream()
            .filter(r -> r.user.getId().equals(alice.getId()))
            .findFirst()
            .orElseThrow();
    var other =
        resets.findAll().stream()
            .filter(r -> r.user.getId().equals(medical.getId()))
            .findFirst()
            .orElseThrow();
    mvc.perform(get("/api/administration/password-resets").with(roleAdmin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1));
    mvc.perform(
            put("/api/administration/password-resets/" + other.id + "/approve")
                .with(roleAdmin())
                .with(csrf())
                .contentType("application/json")
                .content(decision()))
        .andExpect(status().isForbidden());
    mvc.perform(
            put("/api/administration/password-resets/" + own.id + "/approve")
                .with(roleAdmin())
                .with(csrf())
                .contentType("application/json")
                .content(decision()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tokenHash").doesNotExist());
    var token = org.mockito.ArgumentCaptor.forClass(String.class);
    verify(mail).sendReset(eq(alice.getEmail()), token.capture());
    access.reset(token.getValue(), "ReplacementPassword123!", "ReplacementPassword123!");
    assertThrows(IllegalArgumentException.class, () -> access.reset(token.getValue(), PW, PW));
  }

  @Test
  void replacementAndReleaseRevokeSessionsAndRetainAssignmentHistory() throws Exception {
    var session =
        (MockHttpSession)
            login(ra.getUsername())
                .andExpect(status().isOk())
                .andReturn()
                .getRequest()
                .getSession(false);
    administration.provision(
        root.getUsername(),
        PROCESSOR,
        "replacement-admin",
        "replacement@example.test",
        "Replacement Administrator",
        PW,
        "Administrator rotation",
        1);
    assertFalse(users.findById(ra.getId()).orElseThrow().isEnabled());
    mvc.perform(get("/api/administration/overview").session(session))
        .andExpect(status().isUnauthorized());
    login("replacement-admin")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.user.managedRole").value(PROCESSOR));
    assertTrue(
        events.findAll().stream()
            .anyMatch(
                e ->
                    "ROLE_ADMIN_ASSIGNED".equals(e.action)
                        && ra.getId().equals(e.previousAdministratorId)));
    administration.release(root.getUsername(), PROCESSOR, "Rotation completed", 2);
    assertNull(assignments.findById(PROCESSOR).orElseThrow().administrator);
    login("replacement-admin").andExpect(status().isForbidden());
    administration.assignExisting(
        root.getUsername(),
        PROCESSOR,
        users.findByUsername("replacement-admin").orElseThrow().getId(),
        "Reassignment approved",
        3);
    login("replacement-admin").andExpect(status().isOk());
  }

  @Test
  void staleAssignmentAndCrossRolePromotionCannotCreateOrEscalateAccounts() throws Exception {
    assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () ->
            administration.provision(
                root.getUsername(),
                PROCESSOR,
                "stale-admin",
                "stale@example.test",
                "Stale Administrator",
                PW,
                "Old screen submission",
                0));
    assertTrue(users.findByUsername("stale-admin").isEmpty());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            administration.assignExisting(
                root.getUsername(), PROCESSOR, medical.getId(), "Wrong department", 1));
    mvc.perform(
            put("/api/admin/users/" + ra.getId())
                .with(superAdmin())
                .with(csrf())
                .contentType("application/json")
                .content("{\"role\":\"ROLE_MEDICAL_REVIEWER\"}"))
        .andExpect(status().isBadRequest());
  }

  SafetyCase workCase(String number, User creator) {
    SafetyCase c = new SafetyCase();
    c.setCaseNumber(number);
    c.setCountryCode("USA");
    c.setReportType("Spontaneous");
    c.setInitialReceiptDate(LocalDate.now());
    c.setLatestReceivedDate(LocalDate.now());
    c.setCreatedByUser(creator);
    c.setAssignedToUser(creator);
    return cases.saveAndFlush(c);
  }

  @Test
  void employeeWorkIsScopedByServerIdentityAndForeignAccountDetailsAreRedacted() throws Exception {
    var own = workCase("SCOPE-CASE-P", alice);
    workCase("SCOPE-CASE-M", medical);
    CaseAssessment assessment = new CaseAssessment();
    assessment.setAssessedBy(medical.getUsername());
    assessment.setSafetyCase(own);
    own.getAssessments().add(assessment);
    cases.saveAndFlush(own);
    mvc.perform(get("/api/administration/work").with(roleAdmin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].caseNumber").value("SCOPE-CASE-P"));
    mvc.perform(
            get("/api/administration/work")
                .param("userId", medical.getId().toString())
                .with(roleAdmin()))
        .andExpect(status().isForbidden());
    administration.provision(
        root.getUsername(),
        MEDICAL,
        "medical-admin",
        "medical-admin@example.test",
        "Medical Administrator",
        PW,
        "Medical administration",
        0);
    mvc.perform(get("/api/administration/work").with(user("medical-admin").roles("SCOPED_ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(
            jsonPath("$.content[?(@.caseNumber=='SCOPE-CASE-P')].createdBy")
                .value(org.hamcrest.Matchers.contains(org.hamcrest.Matchers.nullValue())));
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(
      strings = {
        "ROLE_CASE_PROCESSOR",
        "ROLE_MEDICAL_REVIEWER",
        "ROLE_QUALITY_REVIEWER",
        "ROLE_SAFETY_MANAGER",
        "ROLE_REPORTING_SPECIALIST",
        "ROLE_AUDITOR"
      })
  void everyApplicationRoleHasTheSameServerEnforcedAdministrativeBoundary(String role)
      throws Exception {
    String actor = ra.getUsername();
    if (!PROCESSOR.equals(role)) {
      administration.provision(
          root.getUsername(),
          role,
          "delegated.admin",
          "delegated@example.test",
          "Delegated Administrator",
          PW,
          "Role ownership verified",
          0);
      actor = "delegated.admin";
    }
    var owned = pending("scope.owned", role);
    var other = pending("scope.other", PROCESSOR.equals(role) ? MEDICAL : PROCESSOR);
    mvc.perform(get("/api/administration/registrations").with(user(actor).roles("SCOPED_ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1));
    mvc.perform(
            put("/api/administration/registrations/" + other.id + "/approve")
                .with(user(actor).roles("SCOPED_ADMIN"))
                .with(csrf())
                .contentType("application/json")
                .content(decision()))
        .andExpect(status().isForbidden());
    mvc.perform(
            put("/api/administration/registrations/" + owned.id + "/approve")
                .with(user(actor).roles("SCOPED_ADMIN"))
                .with(csrf())
                .contentType("application/json")
                .content(decision()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value(role));
  }

  @Test
  void reportingSupervisionUsesReportAuthorshipAndDoesNotGrantReportingMutationRights()
      throws Exception {
    User author = employee("report.author", "ROLE_REPORTING_SPECIALIST");
    User manager = employee("manager.author", "ROLE_SAFETY_MANAGER");
    for (User owner : List.of(author, manager)) {
      SafetyReport report = new SafetyReport();
      report.setReportId("SCOPED-" + owner.getId());
      report.setReportType("15-day");
      report.setProduct("Synthetic product");
      report.setAuthority("FDA");
      report.setDueDate(LocalDate.now().plusDays(1));
      report.setCreatedByUser(owner);
      reports.saveAndFlush(report);
    }
    administration.provision(
        root.getUsername(),
        "ROLE_REPORTING_SPECIALIST",
        "reporting.admin",
        "reporting-admin@example.test",
        "Reporting Administrator",
        PW,
        "Reporting supervision",
        0);
    mvc.perform(
            get("/api/administration/report-work")
                .with(user("reporting.admin").roles("SCOPED_ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].createdBy.username").value(author.getUsername()));
    mvc.perform(
            get("/api/administration/overview").with(user("reporting.admin").roles("SCOPED_ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.roles[0].reportRecords").value(1));
    mvc.perform(
            get("/api/administration/report-work")
                .param("role", "ROLE_SAFETY_MANAGER")
                .with(user("reporting.admin").roles("SCOPED_ADMIN")))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/reports").with(user("reporting.admin").roles("SCOPED_ADMIN")))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/administration/report-work").with(roleAdmin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  void activityAndAdministrativeAuditDoNotReturnOtherRoleActors() throws Exception {
    activity.saveAndFlush(
        new AuditEvent("CASE_UPDATED", alice.getUsername(), null, null, "SUCCESS", "Own role"));
    activity.saveAndFlush(
        new AuditEvent("CASE_UPDATED", medical.getUsername(), null, null, "SUCCESS", "Other role"));
    mvc.perform(get("/api/administration/activity").with(roleAdmin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].actorUsername").value(alice.getUsername()));
    mvc.perform(get("/api/administration/audit").param("role", MEDICAL).with(roleAdmin()))
        .andExpect(status().isForbidden());
  }
}
