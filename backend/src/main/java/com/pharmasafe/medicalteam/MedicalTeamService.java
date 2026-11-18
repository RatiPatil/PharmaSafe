package com.pharmasafe.medicalteam;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.pharmasafe.administration.AdminScope;
import com.pharmasafe.entity.*;
import com.pharmasafe.repository.*;
import com.pharmasafe.service.*;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class MedicalTeamService {
  private final MedicalAccess access;
  private final MedicalTeamRepository teams;
  private final MedicalMemberRepository members;
  private final MedicalWorkOrderRepository orders;
  private final MedicalAssignmentRepository assignments;
  private final MedicalWorkUpdateRepository updates;
  private final MedicalWorkDocumentRepository documents;
  private final UserRepository users;
  private final RegistrationRequestRepository registrations;
  private final com.pharmasafe.administration.RoleAdminAssignmentRepository owners;
  private final SafetyCaseRepository cases;
  private final UserAccessService userAccess;
  private final PasswordEncoder passwords;
  private final AuditService audit;
  private final ObjectMapper json;
  private final CasePdfRenderer renderer;
  private final MedicalCaseWorkbench workbench;

  public MedicalTeamService(
      MedicalAccess a,
      MedicalTeamRepository t,
      MedicalMemberRepository m,
      MedicalWorkOrderRepository o,
      MedicalAssignmentRepository w,
      MedicalWorkUpdateRepository u,
      MedicalWorkDocumentRepository d,
      UserRepository us,
      RegistrationRequestRepository r,
      com.pharmasafe.administration.RoleAdminAssignmentRepository ow,
      SafetyCaseRepository c,
      UserAccessService ua,
      PasswordEncoder p,
      AuditService au,
      ObjectMapper j,
      CasePdfRenderer re,
      MedicalCaseWorkbench wb) {
    workbench = wb;
    access = a;
    teams = t;
    members = m;
    orders = o;
    assignments = w;
    updates = u;
    documents = d;
    users = us;
    registrations = r;
    owners = ow;
    cases = c;
    userAccess = ua;
    passwords = p;
    audit = au;
    json = j;
    renderer = re;
  }

  public record CreateTeam(
      @NotBlank @Size(max = 100) String name,
      @NotBlank @Size(max = 128) String fullName,
      @NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{3,64}") String username,
      @NotBlank @Email @Size(max = 254) String email,
      @NotBlank String password,
      @NotBlank @Size(max = 2000) String reason) {}

  public record Reason(@NotBlank @Size(max = 2000) String reason) {}

  public record MemberRequest(
      @NotBlank @Pattern(regexp = "PS-[1-9][0-9]{0,17}") String employeeStaffCode,
      @NotBlank @Size(max = 2000) String reason) {}

  public record TeamState(
      @NotNull @PositiveOrZero Long expectedVersion,
      boolean active,
      boolean registrationOpen,
      @NotBlank @Size(max = 2000) String reason) {}

  public record WorkRequest(
      @NotNull @Positive Long teamId,
      @NotBlank @Size(max = 160) String title,
      @NotBlank @Size(max = 5000) String description,
      @NotNull @Size(max = 5000) String instructions,
      @NotBlank
          @Pattern(
              regexp =
                  "SOURCE_REVIEW|CLINICAL_ASSESSMENT|SERIOUSNESS_REVIEW|CAUSALITY|EXPECTEDNESS|MEDICAL_NARRATIVE|QC_RESPONSE|QUALITY_HANDOFF")
          String stage,
      @NotBlank @Pattern(regexp = "Routine|Priority|Expedited") String priority,
      @NotNull Instant dueAt,
      @NotBlank @Pattern(regexp = "INDIVIDUAL|ALL_TEAM") String recipientMode,
      @Pattern(regexp = "PS-[1-9][0-9]{0,17}") String employeeStaffCode,
      @Size(max = 64) String caseNumber,
      boolean clinicalRequired) {}

  public record UpdateRequest(
      @NotNull @PositiveOrZero Long expectedVersion,
      @NotBlank @Pattern(regexp = "IN_PROGRESS|BLOCKED|COMPLETED") String status,
      @Min(0) @Max(100) int progress,
      @NotBlank @Size(max = 5000) String workDone,
      @NotNull @Size(max = 3000) String pendingWork,
      @NotNull @Size(max = 3000) String remainingWork,
      @NotNull @Size(max = 2000) String blockers,
      @NotBlank @Size(max = 2000) String reason,
      @NotNull @Size(max = 7) Map<String, Boolean> checklist,
      @NotNull @Size(max = 500) String evidenceReference) {}

  private static final Set<String> CHECKS =
      Set.of(
          "sourceReviewed",
          "seriousnessReviewed",
          "causalityReviewed",
          "expectednessReviewed",
          "timelineReviewed",
          "narrativeReviewed",
          "handoffReady");

  private Pageable page(int p, int size) {
    return PageRequest.of(
        Math.max(0, p),
        Math.max(1, Math.min(50, size)),
        Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id")));
  }

  private Long staffId(String code) {
    try {
      if (code == null || !code.matches("PS-[1-9][0-9]{0,17}"))
        throw new IllegalArgumentException();
      return Long.parseLong(code.substring(3));
    } catch (Exception e) {
      throw new IllegalArgumentException("Use the permanent employee staff ID, for example PS-17");
    }
  }

  private Map<String, Object> person(User u) {
    return Map.of(
        "userId",
        u.getId(),
        "staffCode",
        u.getStaffCode(),
        "username",
        u.getUsername(),
        "fullName",
        Objects.toString(u.getFullName(), u.getUsername()),
        "accountStatus",
        u.getAccountStatus(),
        "enabled",
        u.isEnabled());
  }

  private Map<String, Object> teamView(MedicalTeam t) {
    Map<String, Object> v = new LinkedHashMap<>();
    v.put("id", t.id);
    v.put("teamCode", t.code());
    v.put("name", t.name);
    v.put("lead", person(t.lead));
    v.put("active", t.active);
    v.put("registrationOpen", t.registrationOpen);
    v.put("maxMembers", t.maxMembers);
    v.put("occupiedSeats", members.countByTeamIdAndStatusIn(t.id, List.of("ACTIVE", "PENDING")));
    v.put("registrationPath", "#/access/ROLE_MEDICAL_REVIEWER/register?team=" + t.registrationKey);
    v.put("rowVersion", t.rowVersion);
    return v;
  }

  private Specification<MedicalTeam> teamScope(User u) {
    String level = access.level(u);
    return (r, q, c) ->
        "ADMIN".equals(level)
            ? c.conjunction()
            : "TEAM_LEAD".equals(level)
                ? c.equal(r.get("lead").get("id"), u.getId())
                : r.get("id")
                    .in(members.findByUserId(u.getId()).stream().map(m -> m.team.id).toList());
  }

  private Specification<MedicalAssignment> workScope(
      User u, Long teamId, String employeeStaffCode, String status) {
    String level = access.level(u);
    if (teamId != null) access.team(u, teamId, false);
    Long employeeId =
        employeeStaffCode == null || employeeStaffCode.isBlank()
            ? null
            : staffId(employeeStaffCode);
    return (r, q, c) -> {
      var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
      if ("TEAM_LEAD".equals(level))
        predicates.add(c.equal(r.get("team").get("lead").get("id"), u.getId()));
      if ("EMPLOYEE".equals(level)) predicates.add(c.equal(r.get("employee").get("id"), u.getId()));
      if (teamId != null) predicates.add(c.equal(r.get("team").get("id"), teamId));
      if (employeeId != null) predicates.add(c.equal(r.get("employee").get("id"), employeeId));
      if (status != null && !status.isBlank()) {
        if (!Set.of("NOT_STARTED", "IN_PROGRESS", "BLOCKED", "COMPLETED").contains(status))
          throw new IllegalArgumentException("Unsupported task status");
        predicates.add(c.equal(r.get("status"), status));
      }
      return c.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
    };
  }

  public Map<String, Object> invitation(String key) {
    MedicalTeam t =
        teams
            .findByRegistrationKey(key)
            .filter(
                v ->
                    v.active
                        && v.registrationOpen
                        && v.lead.isApproved()
                        && v.lead.isEnabled()
                        && !v.lead.isLocked())
            .orElseThrow(
                () -> new EntityNotFoundException("This team registration link is unavailable"));
    long occupied = members.countByTeamIdAndStatusIn(t.id, List.of("ACTIVE", "PENDING"));
    return Map.of(
        "teamCode",
        t.code(),
        "name",
        t.name,
        "leadName",
        Objects.toString(t.lead.getFullName(), t.lead.getUsername()),
        "remainingSeats",
        Math.max(0, t.maxMembers - occupied));
  }

  public Map<String, Object> invitationByCode(String code) {
    String normalized = code.trim().toUpperCase(Locale.ROOT);
    if (!normalized.matches("MR-TL-[0-9]{3,18}"))
      throw new IllegalArgumentException("Enter the Team Lead ID, for example MR-TL-001");
    MedicalTeam team;
    try {
      team =
          teams
              .findById(Long.parseLong(normalized.substring(6)))
              .filter(t -> t.code().equals(normalized))
              .orElseThrow(() -> new EntityNotFoundException("Team Lead ID not found"));
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Invalid Team Lead ID");
    }
    var result = new LinkedHashMap<String, Object>(invitation(team.registrationKey));
    result.put("registrationKey", team.registrationKey);
    return result;
  }

  @Transactional
  public Map<String, Object> createTeam(CreateTeam d, String username) {
    User actor = access.actor(username);
    access.admin(actor);
    owners.lockRole(MedicalAccess.ROLE).orElseThrow();
    if (teams.countByActiveTrue() >= 10)
      throw new IllegalArgumentException("The ten active Team Lead slots are occupied");
    UserAccessService.validatePassword(d.password());
    String login = d.username().trim().toLowerCase(Locale.ROOT),
        email = d.email().trim().toLowerCase(Locale.ROOT);
    if (users.existsByUsername(login) || users.existsByEmailIgnoreCase(email))
      throw new IllegalArgumentException("Username or official email already registered");
    User lead = new User(login, passwords.encode(d.password()), MedicalAccess.LEAD);
    lead.setFullName(d.fullName().trim());
    lead.setEmail(email);
    lead.setDepartment("Medical Review");
    lead.setApprovedBy(actor.getId());
    lead.setApprovedAt(Instant.now());
    users.saveAndFlush(lead);
    MedicalTeam t = new MedicalTeam();
    t.name = d.name().trim();
    t.lead = lead;
    t.registrationKey = UUID.randomUUID().toString();
    t.registrationOpen = true;
    t.active = true;
    t.maxMembers = 10;
    t.createdBy = actor;
    t.createdAt = Instant.now();
    teams.saveAndFlush(t);
    audit.logAccountEvent(
        "MR_TEAM_CREATED",
        username,
        "team=" + t.code() + "; lead=" + lead.getStaffCode() + "; reason=" + d.reason());
    return teamView(t);
  }

  @Transactional
  public Map<String, Object> teamState(Long id, TeamState d, String username) {
    User actor = access.actor(username);
    access.admin(actor);
    MedicalTeam t =
        teams.lockById(id).orElseThrow(() -> new EntityNotFoundException("Team not found"));
    if (t.rowVersion != d.expectedVersion())
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Team changed. Refresh before continuing.");
    if (t.active != d.active()) {
      t.lead.setEnabled(d.active());
      t.lead.setAccountStatus(d.active() ? "APPROVED" : "SUSPENDED");
      t.lead.revokeSessions();
      users.saveAndFlush(t.lead);
    }
    t.active = d.active();
    t.registrationOpen = d.active() && d.registrationOpen();
    teams.saveAndFlush(t);
    audit.logAccountEvent(
        "MR_TEAM_STATE_CHANGED",
        username,
        "team=" + t.code() + "; active=" + t.active + "; reason=" + d.reason());
    return teamView(t);
  }

  public List<Map<String, Object>> listTeams(String username) {
    User actor = access.actor(username);
    return teams.findAll(teamScope(actor), Sort.by("id")).stream().map(this::teamView).toList();
  }

  public Page<Map<String, Object>> members(Long teamId, int p, int size, String username) {
    User actor = access.actor(username);
    MedicalTeam t = access.team(actor, teamId, false);
    if ("EMPLOYEE".equals(access.level(actor)))
      throw new AccessDeniedException("Only team supervisors can inspect the employee directory");
    return members
        .findAll(
            (r, q, c) -> c.equal(r.get("team").get("id"), t.id),
            PageRequest.of(Math.max(0, p), Math.max(1, Math.min(50, size)), Sort.by("id")))
        .map(
            m -> {
              var v = new LinkedHashMap<>(person(m.user));
              v.put("membershipStatus", m.status);
              v.put("teamId", m.team.id);
              return v;
            });
  }

  public Page<Map<String, Object>> unassigned(int p, int size, String username) {
    User actor = access.actor(username);
    access.admin(actor);
    return users
        .findAll(
            (r, q, c) -> {
              var sub = q.subquery(Long.class);
              var m = sub.from(MedicalMember.class);
              sub.select(m.get("user").get("id"))
                  .where(m.get("status").in(List.of("ACTIVE", "PENDING")));
              return c.and(
                  r.get("role").in(List.of("ROLE_MEDICAL_REVIEWER")),
                  c.isTrue(r.get("enabled")),
                  c.equal(r.get("accountStatus"), "APPROVED"),
                  c.not(r.get("id").in(sub)));
            },
            PageRequest.of(Math.max(0, p), Math.max(1, Math.min(50, size)), Sort.by("username")))
        .map(this::person);
  }

  @Transactional
  public Map<String, Object> addMember(Long teamId, MemberRequest d, String username) {
    User actor = access.actor(username);
    access.admin(actor);
    MedicalTeam t =
        teams.lockById(teamId).orElseThrow(() -> new EntityNotFoundException("Team not found"));
    access.team(actor, teamId, true);
    User u =
        users
            .findById(staffId(d.employeeStaffCode()))
            .orElseThrow(() -> new EntityNotFoundException("Employee not found"));
    if (!MedicalAccess.ROLE.equals(AdminScope.canonical(u.getRole()))
        || !u.isApproved()
        || !u.isEnabled()
        || u.isLocked())
      throw new IllegalArgumentException("Choose an approved active Medical Review employee");
    if (members.findByUserId(u.getId()).stream()
        .anyMatch(m -> Set.of("PENDING", "ACTIVE").contains(m.status)))
      throw new IllegalArgumentException("Employee already belongs to an active team");
    if (members.countByTeamIdAndStatusIn(teamId, List.of("ACTIVE", "PENDING")) >= t.maxMembers)
      throw new IllegalArgumentException("Team has no free employee seats");
    var m = members.findByTeamIdAndUserId(teamId, u.getId()).orElseGet(MedicalMember::new);
    m.team = t;
    m.user = u;
    m.status = "ACTIVE";
    m.joinedAt = Instant.now();
    members.saveAndFlush(m);
    audit.logAccountEvent(
        "MR_EMPLOYEE_ASSIGNED",
        username,
        "team=" + t.code() + "; employee=" + u.getStaffCode() + "; reason=" + d.reason());
    return person(u);
  }

  public Page<Map<String, Object>> registrations(Long teamId, int p, int size, String username) {
    User actor = access.actor(username);
    String level = access.level(actor);
    if ("EMPLOYEE".equals(level))
      throw new AccessDeniedException(
          "Employee registration approval is restricted to supervisors");
    if (teamId != null) access.team(actor, teamId, false);
    return registrations
        .findAll(
            (r, q, c) -> {
              var pred = new ArrayList<jakarta.persistence.criteria.Predicate>();
              pred.add(c.equal(r.get("requestedRole"), MedicalAccess.ROLE));
              pred.add(c.isNotNull(r.get("medicalTeam")));
              if (teamId != null) pred.add(c.equal(r.get("medicalTeam").get("id"), teamId));
              if ("TEAM_LEAD".equals(level))
                pred.add(c.equal(r.get("medicalTeam").get("lead").get("id"), actor.getId()));
              return c.and(pred.toArray(jakarta.persistence.criteria.Predicate[]::new));
            },
            PageRequest.of(
                Math.max(0, p),
                Math.max(1, Math.min(50, size)),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))))
        .map(UserAccessService::registrationView);
  }

  @Transactional
  public Map<String, Object> decide(Long id, String decision, Reason request, String username) {
    User actor = access.actor(username);
    if ("EMPLOYEE".equals(access.level(actor)))
      throw new AccessDeniedException("Approval requires a supervisor");
    RegistrationRequest r =
        registrations
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Registration not found"));
    if (r.medicalTeam == null || !MedicalAccess.ROLE.equals(r.requestedRole))
      throw new AccessDeniedException("This is not a team registration");
    access.team(actor, r.medicalTeam.id, "approve".equals(decision));
    return userAccess.decideRegistration(id, decision, request.reason(), username);
  }

  private Map<String, Object> assignmentView(MedicalAssignment a) {
    Map<String, Object> v = new LinkedHashMap<>();
    v.put("id", a.id);
    v.put("teamId", a.team.id);
    v.put("teamCode", a.team.code());
    v.put("teamName", a.team.name);
    v.put("employee", person(a.employee));
    v.put("title", a.workOrder.title);
    v.put("stage", a.workOrder.stage);
    v.put("clinicalRequired", a.workOrder.clinicalRequired);
    v.put("priority", a.workOrder.priority);
    v.put("dueAt", a.workOrder.dueAt);
    v.put("status", a.status);
    v.put("progress", a.progress);
    v.put("supervisorNote", a.supervisorNote);
    v.put("reopenedAt", a.reopenedAt);
    v.put("teamActive", a.team.active);
    v.put("rowVersion", a.rowVersion);
    v.put("updatedAt", a.updatedAt);
    v.put("overdue", !"COMPLETED".equals(a.status) && a.workOrder.dueAt.isBefore(Instant.now()));
    return v;
  }

  public Page<Map<String, Object>> work(
      Long teamId, String employeeStaffCode, String status, int p, int size, String username) {
    User actor = access.actor(username);
    return assignments
        .findAll(workScope(actor, teamId, employeeStaffCode, status), page(p, size))
        .map(this::assignmentView);
  }

  public Map<String, Object> overview(Long teamId, String username) {
    User actor = access.actor(username);
    if (teamId != null) access.team(actor, teamId, false);
    var scope = workScope(actor, teamId, null, null);
    Map<String, Long> counts = new LinkedHashMap<>();
    for (String s : List.of("NOT_STARTED", "IN_PROGRESS", "BLOCKED", "COMPLETED"))
      counts.put(s, assignments.count(scope.and((r, q, c) -> c.equal(r.get("status"), s))));
    long total = counts.values().stream().mapToLong(Long::longValue).sum();
    long overdue =
        assignments.count(
            scope.and(
                (r, q, c) ->
                    c.and(
                        c.notEqual(r.get("status"), "COMPLETED"),
                        c.lessThan(r.get("workOrder").get("dueAt"), Instant.now()))));
    var ts =
        teams.findAll(teamScope(actor), Sort.by("id")).stream()
            .filter(t -> teamId == null || t.id.equals(teamId))
            .toList();
    List<Map<String, Object>> byTeam =
        ts.stream()
            .map(
                t -> {
                  long assigned = assignments.count(workScope(actor, t.id, null, null));
                  long complete = assignments.count(workScope(actor, t.id, null, "COMPLETED"));
                  return Map.<String, Object>of(
                      "teamId",
                      t.id,
                      "teamCode",
                      t.code(),
                      "name",
                      t.name,
                      "assigned",
                      assigned,
                      "completed",
                      complete);
                })
            .toList();
    var teamIds = ts.stream().map(t -> t.id).toList();
    long pending =
        "EMPLOYEE".equals(access.level(actor))
            ? 0
            : registrations.count(
                (r, q, c) ->
                    c.and(
                        r.get("medicalTeam").get("id").in(teamIds), c.isNull(r.get("reviewedAt"))));
    long activeEmployees =
        members.count(
            (r, q, c) ->
                c.and(r.get("team").get("id").in(teamIds), c.equal(r.get("status"), "ACTIVE")));
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("level", access.level(actor));
    result.put("actor", person(actor));
    result.put("statusCounts", counts);
    result.put("totalAssignments", total);
    result.put("overdue", overdue);
    result.put("teams", byTeam);
    result.put("updatedAt", Instant.now());
    result.put("capacityPerTeam", 10);
    result.put("teamCapacity", 10);
    result.put("pendingRequests", pending);
    result.put("activeEmployees", activeEmployees);
    result.put("clinicalQueue", workbench.queueCounts(username, teamId));
    return result;
  }

  @Transactional
  public List<Map<String, Object>> assign(WorkRequest d, String username) {
    User actor = access.actor(username);
    MedicalTeam t =
        teams.lockById(d.teamId()).orElseThrow(() -> new EntityNotFoundException("Team not found"));
    access.team(actor, t.id, true);
    if (d.dueAt().isBefore(Instant.now()))
      throw new IllegalArgumentException("Choose a future assignment due time");
    List<User> targets;
    if ("ALL_TEAM".equals(d.recipientMode())) {
      if (d.caseNumber() != null && !d.caseNumber().isBlank())
        throw new IllegalArgumentException(
            "A single clinical case cannot be broadcast to the whole team");
      targets =
          members.findByTeamIdAndStatus(t.id, "ACTIVE").stream()
              .map(m -> m.user)
              .filter(
                  u ->
                      MedicalAccess.ROLE.equals(u.getRole())
                          && u.isApproved()
                          && u.isEnabled()
                          && !u.isLocked())
              .toList();
    } else {
      User u =
          users
              .findById(staffId(d.employeeStaffCode()))
              .orElseThrow(() -> new EntityNotFoundException("Employee not found"));
      var member =
          members
              .findByTeamIdAndUserId(t.id, u.getId())
              .filter(m -> "ACTIVE".equals(m.status))
              .orElseThrow(
                  () ->
                      new AccessDeniedException(
                          "The employee staff ID is not active in this team"));
      if (!MedicalAccess.ROLE.equals(u.getRole())
          || !u.isApproved()
          || !u.isEnabled()
          || u.isLocked()) throw new IllegalArgumentException("Employee is not active");
      targets = List.of(member.user);
    }
    if (targets.isEmpty()) throw new IllegalArgumentException("This team has no active employees");
    if (d.clinicalRequired() && (d.caseNumber() == null || d.caseNumber().isBlank()))
      throw new IllegalArgumentException(
          "Clinical medical work requires an allocated Medical Review case");
    SafetyCase linked = null;
    if (d.caseNumber() != null && !d.caseNumber().isBlank()) {
      var candidate =
          cases
              .findByCaseNumberIgnoreCase(d.caseNumber().trim())
              .orElseThrow(() -> new EntityNotFoundException("Case not found"));
      linked = cases.findForUpdate(candidate.getId()).orElseThrow();
      if (!"Medical Review".equals(linked.getWorkflowState()) || linked.isLocked())
        throw new IllegalArgumentException("Only unlocked cases in Medical Review can be assigned");
      workbench.requireAllocation(linked, t.id);
      final Long clinicalId = linked.getId(), employeeId = targets.get(0).getId();
      if (assignments.count(
              (r, q, b) ->
                  b.and(
                      b.equal(r.get("workOrder").get("safetyCase").get("id"), clinicalId),
                      b.notEqual(r.get("employee").get("id"), employeeId),
                      b.notEqual(r.get("status"), "COMPLETED")))
          > 0)
        throw new IllegalArgumentException(
            "Complete outstanding medical work before assigning this case to another reviewer");
      linked.setAssignedToUser(targets.get(0));
      linked.setUpdatedByUser(actor);
      audit.logCaseEvent(
          linked.getId(),
          "MR_TEAM_CASE_ASSIGNED",
          username,
          "SUCCESS",
          "Assigned to " + targets.get(0).getStaffCode() + " through team " + t.code());
    }
    MedicalWorkOrder order = new MedicalWorkOrder();
    order.team = t;
    order.title = d.title().trim();
    order.description = d.description().trim();
    order.instructions = d.instructions().trim();
    order.stage = d.stage();
    order.clinicalRequired = d.clinicalRequired();
    order.priority = d.priority();
    order.dueAt = d.dueAt();
    order.recipientMode = d.recipientMode();
    order.safetyCase = linked;
    order.createdBy = actor;
    order.createdAt = Instant.now();
    orders.saveAndFlush(order);
    var result = new ArrayList<Map<String, Object>>();
    for (User employee : targets) {
      MedicalAssignment a = new MedicalAssignment();
      a.workOrder = order;
      a.team = t;
      a.employee = employee;
      a.status = "NOT_STARTED";
      a.progress = 0;
      a.createdAt = Instant.now();
      a.updatedAt = a.createdAt;
      assignments.saveAndFlush(a);
      archive(a, null, actor);
      result.add(assignmentView(a));
    }
    audit.logAccountEvent(
        "MR_WORK_ASSIGNED",
        username,
        "team=" + t.code() + "; order=" + order.id + "; recipients=" + targets.size());
    return result;
  }

  private Map<String, Object> updateView(MedicalWorkUpdate u) {
    try {
      Map<String, Object> v = new LinkedHashMap<>();
      v.put("id", u.id);
      v.put("status", u.status);
      v.put("progress", u.progress);
      v.put("workDone", u.workDone);
      v.put("pendingWork", u.pendingWork);
      v.put("remainingWork", u.remainingWork);
      v.put("blockers", u.blockers);
      v.put("reason", u.reason);
      v.put("recordedAt", u.recordedAt);
      v.put("checklist", json.readTree(u.checklistJson));
      v.put("evidenceReference", u.evidenceReference);
      return v;
    } catch (java.io.IOException e) {
      throw new IllegalStateException("Stored progress evidence could not be read", e);
    }
  }

  private Map<String, Object> documentView(MedicalWorkDocument d) {
    return Map.of(
        "id",
        d.id,
        "documentKind",
        d.documentKind,
        "fileName",
        d.fileName,
        "generatedAt",
        d.generatedAt,
        "generatedBy",
        d.generatedBy.getUsername(),
        "sha256",
        d.pdfSha256);
  }

  public Map<String, Object> detail(Long id, int page, String username) {
    User actor = access.actor(username);
    MedicalAssignment a =
        assignments
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Assignment not found"));
    access.assignment(actor, a);
    Map<String, Object> v = new LinkedHashMap<>(assignmentView(a));
    v.put(
        "canRespond",
        "EMPLOYEE".equals(access.level(actor))
            && a.employee.getId().equals(actor.getId())
            && a.team.active
            && workbench.canWork(actor, a)
            && members
                .findByTeamIdAndUserId(a.team.id, actor.getId())
                .filter(m -> "ACTIVE".equals(m.status))
                .isPresent());
    v.put("description", a.workOrder.description);
    v.put("instructions", a.workOrder.instructions);
    v.put("assignedBy", person(a.workOrder.createdBy));
    SafetyCase linked = workbench.linked(a);
    v.put("caseId", linked == null ? null : linked.getId());
    v.put("medicalReadiness", workbench.view(a));
    v.put("caseNumber", linked == null ? null : linked.getCaseNumber());
    v.put(
        "updates",
        updates
            .findByAssignmentIdOrderByRecordedAtDescIdDesc(
                id, PageRequest.of(Math.max(0, page), 20))
            .map(this::updateView));
    v.put(
        "documents",
        documents
            .findByAssignmentIdOrderByGeneratedAtDescIdDesc(
                id, PageRequest.of(Math.max(0, page), 20))
            .map(this::documentView));
    return v;
  }

  @Transactional
  public com.pharmasafe.dto.CaseResponseDto clinicalCase(Long id, String username) {
    User actor = access.actor(username);
    MedicalAssignment a =
        assignments
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Assignment not found"));
    access.assignment(actor, a);
    SafetyCase c = workbench.linked(a);
    if (c == null) throw new EntityNotFoundException("This task has no linked clinical case");
    workbench.canInspect(actor, a, c);
    audit.logCaseEvent(
        c.getId(),
        "MR_TEAM_CASE_VIEWED",
        username,
        "SUCCESS",
        "Read-only case inspection through assignment " + id);
    return com.pharmasafe.dto.CaseResponseDto.fromEntity(c);
  }

  @Transactional
  public Map<String, Object> update(Long id, UpdateRequest d, String username) {
    User actor = access.actor(username);
    if (!"EMPLOYEE".equals(access.level(actor)))
      throw new AccessDeniedException("Only the assigned employee can submit progress");
    MedicalAssignment a =
        assignments
            .lockById(id)
            .orElseThrow(() -> new EntityNotFoundException("Assignment not found"));
    access.assignment(actor, a);
    if (!a.team.active
        || members
            .findByTeamIdAndUserId(a.team.id, actor.getId())
            .filter(m -> "ACTIVE".equals(m.status))
            .isEmpty()) throw new AccessDeniedException("An active team membership is required");
    if (a.rowVersion != d.expectedVersion())
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Assignment changed. Reload before submitting progress.");
    if ("COMPLETED".equals(a.status))
      throw new IllegalArgumentException(
          "Completed work must be reopened by its Team Lead before correction");
    if (!CHECKS.containsAll(d.checklist().keySet())
        || d.checklist().values().stream().anyMatch(Objects::isNull))
      throw new IllegalArgumentException("Unsupported medical checklist field");
    if ((d.progress() == 100) != d.status().equals("COMPLETED"))
      throw new IllegalArgumentException("Only completed work can have 100 percent progress");
    if ("BLOCKED".equals(d.status()) && d.blockers().isBlank())
      throw new IllegalArgumentException("Explain the blocker or missing information");
    // Every response PDF, including partial progress, must capture an owned case under its lock.
    SafetyCase linked = workbench.linked(a);
    if (linked != null) {
      linked = cases.findForUpdate(linked.getId()).orElseThrow();
      if (!workbench.canWork(actor, a))
        throw new AccessDeniedException(
            "Medical work is restricted to your current allocated Medical Review case");
      if (linked.getAssignedToUser() == null
          || !actor.getId().equals(linked.getAssignedToUser().getId()))
        throw new AccessDeniedException("The clinical case is no longer assigned to you");
    }
    if ("COMPLETED".equals(d.status())) {
      if (linked == null && a.workOrder.clinicalRequired)
        throw new IllegalArgumentException(
            "Create or link the clinical case before completing this clinical assignment");
      if (linked != null) {
        var missing = workbench.missing(a, linked);
        if (!missing.isEmpty()) throw new IllegalArgumentException(String.join(" ", missing));
      }
      if (!d.pendingWork().isBlank() || !d.remainingWork().isBlank())
        throw new IllegalArgumentException("Completed work cannot have pending or remaining work");
      if (!Boolean.TRUE.equals(d.checklist().get("sourceReviewed")))
        throw new IllegalArgumentException(
            "Confirm source clinical evidence review before completing medical work");
    }
    MedicalWorkUpdate u = new MedicalWorkUpdate();
    u.assignment = a;
    u.employee = actor;
    u.status = d.status();
    u.progress = d.progress();
    u.workDone = d.workDone().trim();
    u.pendingWork = d.pendingWork().trim();
    u.remainingWork = d.remainingWork().trim();
    u.blockers = d.blockers().trim();
    u.reason = d.reason().trim();
    u.evidenceReference = d.evidenceReference().trim();
    try {
      u.checklistJson = json.writeValueAsString(d.checklist());
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid checklist");
    }
    u.sourceAssignmentVersion = a.rowVersion;
    u.recordedAt = Instant.now();
    updates.saveAndFlush(u);
    a.status = u.status;
    a.progress = u.progress;
    a.updatedAt = u.recordedAt;
    assignments.saveAndFlush(a);
    archive(a, u, actor);
    audit.logAccountEvent(
        "MR_PROGRESS_RECORDED",
        username,
        "assignment="
            + id
            + "; update="
            + u.id
            + "; state="
            + u.status
            + "; progress="
            + u.progress);
    return updateView(u);
  }

  @Transactional
  public Map<String, Object> reopen(Long id, Reason d, String username) {
    User actor = access.actor(username);
    MedicalAssignment a =
        assignments
            .lockById(id)
            .orElseThrow(() -> new EntityNotFoundException("Assignment not found"));
    access.team(actor, a.team.id, true);
    if (!"COMPLETED".equals(a.status))
      throw new IllegalArgumentException("Only completed work can be reopened");
    a.supervisorNote = d.reason().trim();
    a.reopenedAt = Instant.now();
    a.status = "IN_PROGRESS";
    a.progress = 99;
    a.updatedAt = Instant.now();
    assignments.saveAndFlush(a);
    audit.logAccountEvent(
        "MR_WORK_REOPENED", username, "assignment=" + id + "; reason=" + d.reason());
    return assignmentView(a);
  }

  private void archive(MedicalAssignment a, MedicalWorkUpdate u, User actor) {
    MedicalWorkDocument d = new MedicalWorkDocument();
    d.id = UUID.randomUUID().toString();
    d.assignment = a;
    d.update = u;
    d.documentKind = u == null ? "ASSIGNMENT" : "EMPLOYEE_RESPONSE";
    d.generatedBy = actor;
    d.generatedAt = Instant.now();
    d.fileName = "PharmaSafe-team-work-" + a.id + "-" + d.id + ".pdf";
    ObjectNode snapshot = json.createObjectNode();
    ObjectNode control = snapshot.putObject("documentControl");
    control.put("documentKind", u == null ? "MEDICAL_ASSIGNMENT" : "MEDICAL_PROGRESS");
    control.put("reportId", "MR-WORK-" + a.id);
    control.put("documentId", d.id);
    control.put("generatedBy", actor.getUsername());
    control.put("generatedAtUtc", d.generatedAt.toString());
    control.put("finalDocument", false);
    snapshot.set("assignment", json.valueToTree(assignmentView(a)));
    snapshot.put("description", a.workOrder.description);
    snapshot.put("instructions", a.workOrder.instructions);
    snapshot.put("teamLeadStaffCode", a.team.lead.getStaffCode());
    snapshot.put("createdBy", a.workOrder.createdBy.getUsername());
    snapshot.put(
        "clinicalCaseNumber",
        workbench.linked(a) == null ? "Not linked" : workbench.linked(a).getCaseNumber());
    snapshot.put(
        "scope",
        "Internal Medical Review task evidence. Task completion does not approve a clinical case"
            + " or transmit a regulatory report.");
    if (u != null) {
      snapshot.set("employeeResponse", json.valueToTree(updateView(u)));
      SafetyCase linked = workbench.linked(a);
      if (linked != null) {
        snapshot.set(
            "medicalCaseAtResponse",
            json.valueToTree(com.pharmasafe.dto.CaseResponseDto.fromEntity(linked)));
        snapshot.set("medicalReadinessAtResponse", json.valueToTree(workbench.view(a)));
        snapshot.set(
            "laboratoryAtResponse", json.valueToTree(workbench.laboratorySnapshot(linked)));
      }
    }
    try {
      byte[] source = json.writeValueAsBytes(snapshot);
      d.snapshotJson = new String(source, java.nio.charset.StandardCharsets.UTF_8);
      d.pdfBytes = renderer.render(snapshot, source);
      d.pdfSha256 = hash(d.pdfBytes);
      documents.saveAndFlush(d);
    } catch (java.io.IOException e) {
      throw new IllegalStateException(
          "Task PDF generation failed; the assignment or response was not saved", e);
    }
  }

  private String hash(byte[] bytes) {
    try {
      return HexFormat.of()
          .formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @Transactional
  public CaseDocumentService.Download download(
      Long assignmentId, String documentId, String username) {
    User actor = access.actor(username);
    MedicalAssignment a =
        assignments
            .findById(assignmentId)
            .orElseThrow(() -> new EntityNotFoundException("Assignment not found"));
    access.assignment(actor, a);
    MedicalWorkDocument d =
        documents
            .findById(documentId)
            .filter(v -> v.assignment.id.equals(assignmentId))
            .orElseThrow(() -> new EntityNotFoundException("Task document not found"));
    if (!hash(d.pdfBytes).equals(d.pdfSha256))
      throw new IllegalStateException("Task PDF integrity verification failed");
    audit.logAccountEvent(
        "MR_DOCUMENT_DOWNLOADED",
        username,
        "assignment=" + assignmentId + "; document=" + documentId);
    return new CaseDocumentService.Download(d.fileName, d.pdfBytes, d.pdfSha256);
  }
}
