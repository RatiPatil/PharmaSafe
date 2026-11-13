package com.pharmasafe.administration;

import com.pharmasafe.entity.*;
import com.pharmasafe.repository.*;
import com.pharmasafe.service.*;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AdministrationService {
  private final AdminScope scope;
  private final UserRepository users;
  private final UserAccessService access;
  private final RoleAdminAssignmentRepository assignments;
  private final AdministrativeEventRepository events;
  private final RegistrationRequestRepository registrations;
  private final PasswordResetRequestRepository resets;
  private final AdministrativeAudit audit;
  private final PasswordEncoder encoder;
  private final SafetyCaseRepository cases;
  @org.springframework.beans.factory.annotation.Autowired private SafetyReportRepository reports;
  @PersistenceContext private EntityManager em;

  public AdministrationService(
      AdminScope scope,
      UserRepository users,
      UserAccessService access,
      RoleAdminAssignmentRepository assignments,
      AdministrativeEventRepository events,
      RegistrationRequestRepository registrations,
      PasswordResetRequestRepository resets,
      AdministrativeAudit audit,
      PasswordEncoder encoder,
      SafetyCaseRepository cases) {
    this.scope = scope;
    this.users = users;
    this.access = access;
    this.assignments = assignments;
    this.events = events;
    this.registrations = registrations;
    this.resets = resets;
    this.audit = audit;
    this.encoder = encoder;
    this.cases = cases;
  }

  private static Pageable page(int page, int size, String column, String direction) {
    return PageRequest.of(
        Math.max(0, page),
        Math.min(50, Math.max(1, size)),
        Sort.by("asc".equals(direction) ? Sort.Direction.ASC : Sort.Direction.DESC, column)
            .and(Sort.by("id")));
  }

  private static String reason(String value) {
    if (value == null || value.isBlank() || value.length() > 2000)
      throw new IllegalArgumentException("A reason of 1–2000 characters is required");
    return value.trim();
  }

  private static List<String> roles(String role) {
    return role.equals("ROLE_CASE_PROCESSOR") ? List.of(role, "ROLE_USER") : List.of(role);
  }

  private Specification<User> userScope(String role) {
    return (r, q, c) -> role.isEmpty() ? c.conjunction() : r.get("role").in(roles(role));
  }

  private Map<String, Object> roleSummary(String role) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("role", role);
    row.put("name", AccessRoleCatalog.NAMES.get(role));
    row.put("totalUsers", users.count(userScope(role)));
    for (String status : List.of("PENDING", "APPROVED", "REJECTED", "SUSPENDED"))
      row.put(
          status.toLowerCase(Locale.ROOT),
          users.count(userScope(role).and((r, q, c) -> c.equal(r.get("accountStatus"), status))));
    var a = assignments.findById(role);
    row.put(
        "administrator",
        a.map(v -> v.administrator == null ? null : UserAccessService.userView(v.administrator))
            .orElse(null));
    row.put("assignmentVersion", a.map(v -> v.rowVersion).orElse(0L));
    row.put("assignedAt", a.map(v -> v.assignedAt).orElse(null));
    row.put("caseRecords", cases.count(workScope(role, null)));
    row.put(
        "reportRecords",
        reports.count((r, q, c) -> r.get("createdByUser").get("role").in(roles(role))));
    row.put(
        "pendingRequests",
        registrations.count(
            (r, q, c) ->
                c.and(
                    c.equal(r.get("requestedRole"), role),
                    c.isNull(r.get("reviewedAt")),
                    c.equal(r.get("user").get("accountStatus"), "PENDING"))));
    row.put(
        "pendingResetRequests",
        resets.count(
            (r, q, c) ->
                c.and(
                    r.get("user").get("role").in(roles(role)),
                    c.equal(r.get("status"), "PENDING"))));
    return row;
  }

  public Map<String, Object> overview(String actor, String requested) {
    String role = scope.role(actor, requested);
    User actorUser = scope.actor(actor);
    boolean global = role.isEmpty();
    var userQuery =
        em.createQuery(
            "select u.role,u.accountStatus,count(u) from User u"
                + (global ? "" : " where u.role in :roles")
                + " group by u.role,u.accountStatus",
            Object[].class);
    if (!global) userQuery.setParameter("roles", roles(role));
    var counts = userQuery.getResultList();
    Map<String, Map<String, Long>> grouped = new LinkedHashMap<>();
    Map<String, Long> totals = new LinkedHashMap<>();
    long total = 0, roleAdmins = 0;
    for (Object[] row : counts) {
      String code = AdminScope.canonical(row[0].toString()), status = row[1].toString();
      long n = ((Number) row[2]).longValue();
      grouped.computeIfAbsent(code, k -> new LinkedHashMap<>()).merge(status, n, Long::sum);
      totals.merge(status, n, Long::sum);
      total += n;
      if (AdminScope.SCOPED_ADMIN.equals(code)) roleAdmins += n;
    }
    var slots =
        global
            ? assignments.findAllWithAdministrators()
            : assignments.findById(role).map(List::of).orElse(List.of());
    Map<String, RoleAdminAssignment> slotByRole = new HashMap<>();
    for (var slot : slots) slotByRole.put(slot.applicationRole, slot);
    var registrationQuery =
        em.createQuery(
            "select r.requestedRole,count(r) from RegistrationRequest r where r.reviewedAt is null"
                + " and r.user.accountStatus='PENDING'"
                + (global ? "" : " and r.requestedRole=:role")
                + " group by r.requestedRole",
            Object[].class);
    if (!global) registrationQuery.setParameter("role", role);
    Map<String, Long> pending = new HashMap<>();
    for (Object[] row : registrationQuery.getResultList())
      pending.put(row[0].toString(), ((Number) row[1]).longValue());
    var resetQuery =
        em.createQuery(
            "select r.user.role,count(r) from PasswordResetRequest r where r.status='PENDING'"
                + (global ? "" : " and r.user.role in :roles")
                + " group by r.user.role",
            Object[].class);
    if (!global) resetQuery.setParameter("roles", roles(role));
    Map<String, Long> resetCounts = new HashMap<>();
    for (Object[] row : resetQuery.getResultList())
      resetCounts.merge(
          AdminScope.canonical(row[0].toString()), ((Number) row[1]).longValue(), Long::sum);
    var reportQuery =
        em.createQuery(
            "select r.createdByUser.role,count(r) from SafetyReport r where r.createdByUser is not"
                + " null"
                + (global ? "" : " and r.createdByUser.role in :roles")
                + " group by r.createdByUser.role",
            Object[].class);
    if (!global) reportQuery.setParameter("roles", roles(role));
    Map<String, Long> reportCounts = new HashMap<>();
    for (Object[] row : reportQuery.getResultList())
      reportCounts.merge(
          AdminScope.canonical(row[0].toString()), ((Number) row[1]).longValue(), Long::sum);
    List<Map<String, Object>> roleRows = new ArrayList<>();
    for (String code : AccessRoleCatalog.FIELDS.keySet().stream().sorted().toList()) {
      if (!global && !role.equals(code)) continue;
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("role", code);
      row.put("name", AccessRoleCatalog.NAMES.get(code));
      var statusCounts = grouped.getOrDefault(code, Map.of());
      row.put("totalUsers", statusCounts.values().stream().mapToLong(Long::longValue).sum());
      for (String status : List.of("PENDING", "APPROVED", "REJECTED", "SUSPENDED"))
        row.put(status.toLowerCase(Locale.ROOT), statusCounts.getOrDefault(status, 0L));
      var slot = slotByRole.get(code);
      row.put(
          "administrator",
          slot == null || slot.administrator == null
              ? null
              : UserAccessService.userView(slot.administrator));
      row.put("assignedAt", slot == null ? null : slot.assignedAt);
      row.put("assignmentVersion", slot == null ? 0L : slot.rowVersion);
      row.put("pendingRequests", pending.getOrDefault(code, 0L));
      row.put("pendingResetRequests", resetCounts.getOrDefault(code, 0L));
      row.put("caseRecords", cases.count(workScope(code, null)));
      row.put("reportRecords", reportCounts.getOrDefault(code, 0L));
      roleRows.add(row);
    }
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("administrativeLevel", AdminScope.isSuper(actorUser) ? "SUPER_ADMIN" : "ROLE_ADMIN");
    result.put("managedRole", role);
    result.put("roles", roleRows);
    result.put("totalUsers", total);
    for (String status : List.of("PENDING", "APPROVED", "REJECTED", "SUSPENDED"))
      result.put(status.toLowerCase(Locale.ROOT), totals.getOrDefault(status, 0L));
    if (AdminScope.isSuper(actorUser) && global) {
      result.put("totalRoleAdmins", roleAdmins);
      result.put(
          "activeRoleAdmins",
          slots.stream()
              .filter(
                  a ->
                      a.administrator != null
                          && a.administrator.isEnabled()
                          && a.administrator.isApproved())
              .count());
    }
    return result;
  }

  public Page<Map<String, Object>> accounts(
      String actor,
      String requested,
      String status,
      String department,
      String organization,
      String search,
      LocalDate from,
      LocalDate to,
      int p,
      int size,
      String sort,
      String direction) {
    String role = scope.role(actor, requested);
    validateDates(from, to);
    if (!status.isBlank()
        && !Set.of("PENDING", "APPROVED", "REJECTED", "SUSPENDED").contains(status))
      throw new IllegalArgumentException("Unsupported account status");
    Specification<User> filter =
        userScope(role)
            .and(
                (r, q, c) -> {
                  var conditions = new ArrayList<jakarta.persistence.criteria.Predicate>();
                  if (!status.isBlank()) conditions.add(c.equal(r.get("accountStatus"), status));
                  if (!department.isBlank())
                    conditions.add(
                        c.equal(c.lower(r.get("department")), department.toLowerCase(Locale.ROOT)));
                  if (!organization.isBlank())
                    conditions.add(
                        c.equal(
                            c.lower(r.get("organization")), organization.toLowerCase(Locale.ROOT)));
                  if (!search.isBlank()) {
                    String term =
                        "%"
                            + search
                                .toLowerCase(Locale.ROOT)
                                .replace("\\", "\\\\")
                                .replace("%", "\\%")
                                .replace("_", "\\_")
                            + "%";
                    var choices = new ArrayList<jakarta.persistence.criteria.Predicate>();
                    if (search.matches("(?i)PS-[0-9]{1,18}"))
                      choices.add(c.equal(r.get("id"), Long.parseLong(search.substring(3))));
                    for (String field :
                        List.of("username", "fullName", "employeeId", "email", "staffCode"))
                      choices.add(c.like(c.lower(r.get(field)), term, '\\'));
                    conditions.add(
                        c.or(choices.toArray(jakarta.persistence.criteria.Predicate[]::new)));
                  }
                  if (from != null)
                    conditions.add(
                        c.greaterThanOrEqualTo(
                            r.get("createdAt"), from.atStartOfDay(ZoneOffset.UTC).toInstant()));
                  if (to != null)
                    conditions.add(
                        c.lessThan(
                            r.get("createdAt"),
                            to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()));
                  return c.and(conditions.toArray(jakarta.persistence.criteria.Predicate[]::new));
                });
    String column =
        Set.of("username", "fullName", "createdAt", "accountStatus").contains(sort)
            ? sort
            : "username";
    return users.findAll(filter, page(p, size, column, direction)).map(UserAccessService::userView);
  }

  private void validateDates(LocalDate from, LocalDate to) {
    if (from != null && to != null && to.isBefore(from))
      throw new IllegalArgumentException("End date must not be before start date");
  }

  public Page<Map<String, Object>> registrationList(
      String actor,
      String requested,
      String status,
      String department,
      String organization,
      String search,
      LocalDate from,
      LocalDate to,
      Long administrator,
      int page,
      int size,
      String sort,
      String direction) {
    String role = scope.role(actor, requested);
    validateDates(from, to);
    if (administrator != null) {
      var assignment =
          assignments
              .findByAdministratorId(administrator)
              .orElseThrow(
                  () ->
                      new IllegalArgumentException("Administrator has no active role assignment"));
      if (!role.isEmpty() && !role.equals(assignment.applicationRole))
        throw new org.springframework.security.access.AccessDeniedException(
            "Administrator is outside this role");
      role = scope.role(actor, assignment.applicationRole);
    }
    return access.registrations(
        role, status, department, organization, search, from, to, page, size, sort, direction);
  }

  public Map<String, Object> details(String actor, Long id, boolean registration) {
    User u =
        registration
            ? registrations
                .findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Request not found"))
                .user
            : users.findById(id).orElseThrow(() -> new EntityNotFoundException("User not found"));
    scope.target(actor, u);
    return registration ? access.details(id) : access.accountDetails(id);
  }

  public Page<Map<String, Object>> resetRequests(
      String actor, String requested, String status, int p, int size) {
    String role = scope.role(actor, requested);
    return resets
        .findAll(
            (r, q, c) ->
                c.and(
                    role.isEmpty() ? c.conjunction() : r.get("user").get("role").in(roles(role)),
                    status.isBlank() ? c.conjunction() : c.equal(r.get("status"), status)),
            page(p, size, "createdAt", "desc"))
        .map(UserAccessService::resetView);
  }

  public Page<AdministrativeEvent> history(
      String actor, String requested, String search, int p, int size) {
    String role = scope.role(actor, requested);
    return events.findAll(
        (r, q, c) ->
            c.and(
                role.isEmpty() ? c.conjunction() : c.equal(r.get("managedRole"), role),
                search.isBlank()
                    ? c.conjunction()
                    : c.or(
                        c.like(
                            c.lower(r.get("actorUsername")),
                            "%" + search.toLowerCase(Locale.ROOT) + "%"),
                        c.like(
                            c.lower(r.get("action")),
                            "%" + search.toLowerCase(Locale.ROOT) + "%"))),
        page(p, size, "createdAt", "desc"));
  }

  public Page<Map<String, Object>> eligible(
      String actor, String role, String search, int p, int size) {
    scope.superAdmin(actor);
    scope.role(actor, role);
    if (role.isBlank()) throw new IllegalArgumentException("Select an application role");
    return users
        .findAll(
            (root, q, c) -> {
              var sub = q.subquery(Long.class);
              var assigned = sub.from(RoleAdminAssignment.class);
              sub.select(assigned.get("administrator").get("id"))
                  .where(c.isNotNull(assigned.get("administrator")));
              return c.and(
                  c.or(
                      c.and(
                          root.get("role").in(roles(role)),
                          c.isTrue(root.get("enabled")),
                          c.equal(root.get("accountStatus"), "APPROVED")),
                      c.and(
                          c.equal(root.get("role"), AdminScope.SCOPED_ADMIN),
                          root.get("accountStatus").in("APPROVED", "SUSPENDED"))),
                  c.isFalse(root.get("locked")),
                  c.not(root.get("id").in(sub)),
                  c.or(
                      c.like(
                          c.lower(root.get("username")),
                          "%" + search.toLowerCase(Locale.ROOT) + "%"),
                      c.like(
                          c.lower(root.get("fullName")),
                          "%" + search.toLowerCase(Locale.ROOT) + "%")));
            },
            page(p, size, "username", "asc"))
        .map(UserAccessService::userView);
  }

  public Page<Map<String, Object>> activity(String actor, String requested, int p, int size) {
    String role = scope.role(actor, requested);
    var repository = em.getCriteriaBuilder();
    var query = repository.createQuery(AuditEvent.class);
    var root = query.from(AuditEvent.class);
    Specification<AuditEvent> filter =
        (r, q, c) -> {
          if (role.isBlank()) return c.conjunction();
          var sub = q.subquery(String.class);
          var u = sub.from(User.class);
          sub.select(u.get("username")).where(u.get("role").in(roles(role)));
          return r.get("username").in(sub);
        };
    var predicate = filter.toPredicate(root, query, repository);
    query
        .where(predicate)
        .orderBy(repository.desc(root.get("createdAt")), repository.desc(root.get("id")));
    var rows =
        em
            .createQuery(query)
            .setFirstResult(Math.max(0, p) * Math.min(50, Math.max(1, size)))
            .setMaxResults(Math.min(50, Math.max(1, size)))
            .getResultList()
            .stream()
            .map(
                e -> {
                  Map<String, Object> m = new LinkedHashMap<>();
                  m.put("id", e.getId());
                  m.put("action", e.getEventType());
                  m.put("actorUsername", e.getUsername());
                  m.put("createdAt", e.getCreatedAt());
                  m.put("outcome", e.getStatus());
                  m.put(
                      "caseNumber",
                      e.getSafetyCase() == null ? null : e.getSafetyCase().getCaseNumber());
                  return m;
                })
            .toList();
    var count = repository.createQuery(Long.class);
    var cr = count.from(AuditEvent.class);
    count.select(repository.count(cr)).where(filter.toPredicate(cr, count, repository));
    return new PageImpl<>(
        rows,
        PageRequest.of(Math.max(0, p), Math.min(50, Math.max(1, size))),
        em.createQuery(count).getSingleResult());
  }

  private RoleAdminAssignment lock(String role) {
    if (!AccessRoleCatalog.FIELDS.containsKey(role))
      throw new IllegalArgumentException("Select a supported application role");
    if (!assignments.existsById(role)) assignments.saveAndFlush(new RoleAdminAssignment(role));
    return assignments.lockRole(role).orElseThrow();
  }

  @Transactional
  public Map<String, Object> provision(
      String actor,
      String role,
      String username,
      String email,
      String fullName,
      String password,
      String why,
      long expectedVersion) {
    User superAdmin = scope.superAdmin(actor);
    reason(why);
    UserAccessService.validatePassword(password);
    String normalized = username.trim().toLowerCase(Locale.ROOT),
        mail = email.trim().toLowerCase(Locale.ROOT);
    if (!normalized.matches("[a-z0-9._-]{3,64}")
        || !mail.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")
        || mail.length() > 254
        || fullName == null
        || fullName.isBlank()
        || fullName.length() > 128)
      throw new IllegalArgumentException("Valid name, unique username and email are required");
    var slot = lock(role);
    version(slot, expectedVersion);
    if (users.existsByUsername(normalized) || users.existsByEmailIgnoreCase(mail))
      throw new IllegalArgumentException("Username or email already registered");
    User u = new User(normalized, encoder.encode(password), AdminScope.SCOPED_ADMIN);
    u.setFullName(fullName.trim());
    u.setEmail(mail);
    u.setDepartment(AccessRoleCatalog.NAMES.get(role));
    u.setApprovedBy(superAdmin.getId());
    u.setApprovedAt(Instant.now());
    users.saveAndFlush(u);
    return assign(superAdmin, slot, u, why);
  }

  @Transactional
  public Map<String, Object> assignExisting(
      String actor, String role, Long userId, String why, long expectedVersion) {
    User a = scope.superAdmin(actor);
    var slot = lock(role);
    version(slot, expectedVersion);
    var u = users.lockById(userId).orElseThrow(() -> new EntityNotFoundException("User not found"));
    if (u.getEmail() == null || u.getFullName() == null || u.getFullName().isBlank())
      throw new IllegalArgumentException(
          "Complete the employee name and official email before administrator assignment");
    if (AdminScope.isSuper(u)
        || (!AdminScope.isRoleAdmin(u) && !role.equals(AdminScope.canonical(u.getRole())))
        || (!u.isApproved() || !u.isEnabled())
            && !(AdminScope.isRoleAdmin(u) && "SUSPENDED".equals(u.getAccountStatus()))
        || u.isLocked())
      throw new IllegalArgumentException(
          "Choose an approved, enabled user of this application role or an unassigned Role Admin");
    var existing = assignments.findByAdministratorId(userId);
    if (existing.isPresent() && !existing.get().applicationRole.equals(role))
      throw new IllegalArgumentException(
          "Release the existing role assignment before reassignment");
    return assign(a, slot, u, why);
  }

  private void version(RoleAdminAssignment slot, long expected) {
    if (slot.rowVersion != expected)
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.CONFLICT,
          "Administrator assignment changed. Refresh before trying again.");
  }

  private Map<String, Object> assign(User actor, RoleAdminAssignment slot, User user, String why) {
    String explanation = reason(why);
    User previous = slot.administrator;
    if (previous != null && previous.getId().equals(user.getId()))
      throw new IllegalArgumentException("This administrator is already assigned");
    Long previousId = previous == null ? null : previous.getId();
    if (previous != null) {
      previous = users.lockById(previous.getId()).orElseThrow();
      String previousState = previous.getAccountStatus();
      previous.setEnabled(false);
      previous.setAccountStatus("SUSPENDED");
      previous.revokeSessions();
      audit.record(
          actor,
          slot.applicationRole,
          previous,
          null,
          "ROLE_ADMIN_SUSPENDED",
          previousState,
          "SUSPENDED",
          explanation,
          null);
    }
    String previousAccountState = user.getAccountStatus();
    user.setRole(AdminScope.SCOPED_ADMIN);
    user.setAccountStatus("APPROVED");
    user.setEnabled(true);
    user.revokeSessions();
    users.saveAndFlush(user);
    if ("SUSPENDED".equals(previousAccountState))
      audit.record(
          actor,
          slot.applicationRole,
          user,
          null,
          "ROLE_ADMIN_ACTIVATED",
          previousAccountState,
          "APPROVED",
          explanation,
          null);
    slot.administrator = user;
    slot.assignedBy = actor;
    slot.assignedAt = Instant.now();
    assignments.saveAndFlush(slot);
    audit.record(
        actor,
        slot.applicationRole,
        user,
        null,
        "ROLE_ADMIN_ASSIGNED",
        previous == null ? "UNASSIGNED" : "ASSIGNED",
        "ASSIGNED",
        explanation,
        previousId);
    return roleSummary(slot.applicationRole);
  }

  @Transactional
  public Map<String, Object> release(String actor, String role, String why, long expectedVersion) {
    User a = scope.superAdmin(actor);
    var slot = lock(role);
    version(slot, expectedVersion);
    if (slot.administrator == null)
      throw new IllegalArgumentException("No administrator is assigned");
    User old = users.lockById(slot.administrator.getId()).orElseThrow();
    String previousAccountState = old.getAccountStatus();
    old.setEnabled(false);
    old.setAccountStatus("SUSPENDED");
    old.revokeSessions();
    audit.record(
        a,
        role,
        old,
        null,
        "ROLE_ADMIN_SUSPENDED",
        previousAccountState,
        "SUSPENDED",
        reason(why),
        null);
    slot.administrator = null;
    slot.assignedBy = a;
    slot.assignedAt = Instant.now();
    assignments.saveAndFlush(slot);
    audit.record(
        a,
        role,
        old,
        null,
        "ROLE_ADMIN_RELEASED",
        "ASSIGNED",
        "UNASSIGNED",
        reason(why),
        old.getId());
    return roleSummary(role);
  }

  Specification<SafetyCase> workScope(String role, Long userId) {
    return (root, q, cb) -> {
      var usersQuery = q.subquery(Long.class);
      var member = usersQuery.from(User.class);
      usersQuery
          .select(member.get("id"))
          .where(
              userId != null
                  ? cb.equal(member.get("id"), userId)
                  : member.get("role").in(roles(role)));
      var assessment = q.subquery(Long.class);
      var ass = assessment.from(CaseAssessment.class);
      var assUser = assessment.from(User.class);
      assessment
          .select(ass.get("safetyCase").get("id"))
          .where(
              cb.and(
                  cb.equal(ass.get("assessedBy"), assUser.get("username")),
                  userId != null
                      ? cb.equal(assUser.get("id"), userId)
                      : assUser.get("role").in(roles(role))));
      var review = q.subquery(Long.class);
      var rev = review.from(CaseReviewEvent.class);
      review
          .select(rev.get("safetyCase").get("id"))
          .where(
              userId != null
                  ? cb.equal(rev.get("reviewerUser").get("id"), userId)
                  : rev.get("reviewerUser").get("role").in(roles(role)));
      return cb.or(
          root.get("assignedToUser").get("id").in(usersQuery),
          root.get("createdByUser").get("id").in(usersQuery),
          root.get("id").in(assessment),
          root.get("id").in(review));
    };
  }

  public Page<Map<String, Object>> work(
      String actor, String requested, Long userId, int p, int size) {
    String role = scope.role(actor, requested);
    if (userId != null) {
      User target =
          users.findById(userId).orElseThrow(() -> new EntityNotFoundException("User not found"));
      scope.target(actor, target);
      if (!role.isBlank() && !role.equals(AdminScope.canonical(target.getRole())))
        throw new org.springframework.security.access.AccessDeniedException(
            "User outside role scope");
    }
    if (role.isBlank() && userId == null)
      return cases.findAll(page(p, size, "updatedAt", "desc")).map(c -> workView(c, role));
    return cases
        .findAll(workScope(role, userId), page(p, size, "updatedAt", "desc"))
        .map(c -> workView(c, role));
  }

  public Page<Map<String, Object>> reportWork(
      String actor, String requested, Long userId, int p, int size) {
    String role = scope.role(actor, requested);
    if (userId != null) {
      User target =
          users.findById(userId).orElseThrow(() -> new EntityNotFoundException("User not found"));
      scope.target(actor, target);
      if (!role.isBlank() && !role.equals(AdminScope.canonical(target.getRole())))
        throw new org.springframework.security.access.AccessDeniedException(
            "User outside role scope");
    }
    Specification<SafetyReport> filter =
        (r, q, c) ->
            userId != null
                ? c.equal(r.get("createdByUser").get("id"), userId)
                : role.isBlank()
                    ? c.conjunction()
                    : r.get("createdByUser").get("role").in(roles(role));
    return reports
        .findAll(filter, page(p, size, "updatedAt", "desc"))
        .map(
            r -> {
              Map<String, Object> row = new LinkedHashMap<>();
              row.put("id", r.getId());
              row.put("reportId", r.getReportId());
              row.put("reportType", r.getReportType());
              row.put("product", r.getProduct());
              row.put("authority", r.getAuthority());
              row.put("status", r.getStatus());
              row.put("dueDate", r.getDueDate());
              row.put("submittedDate", r.getSubmittedDate());
              var author = r.getCreatedByUser();
              row.put(
                  "createdBy",
                  author == null
                          || !role.isBlank() && !role.equals(AdminScope.canonical(author.getRole()))
                      ? null
                      : UserAccessService.userView(author));
              return row;
            });
  }

  private Map<String, Object> workView(SafetyCase c, String role) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", c.getId());
    row.put("caseNumber", c.getCaseNumber());
    row.put("workflowState", c.getWorkflowState());
    row.put("priority", c.getPriority());
    row.put("updatedAt", c.getUpdatedAt());
    row.put(
        "assignedTo",
        c.getAssignedToUser() == null
                || !role.isBlank()
                    && !role.equals(AdminScope.canonical(c.getAssignedToUser().getRole()))
            ? null
            : UserAccessService.userView(c.getAssignedToUser()));
    row.put(
        "createdBy",
        c.getCreatedByUser() == null
                || !role.isBlank()
                    && !role.equals(AdminScope.canonical(c.getCreatedByUser().getRole()))
            ? null
            : UserAccessService.userView(c.getCreatedByUser()));
    return row;
  }
}
