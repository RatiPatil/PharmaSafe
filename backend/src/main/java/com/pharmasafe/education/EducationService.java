package com.pharmasafe.education;

import com.fasterxml.jackson.databind.*;
import com.pharmasafe.entity.User;
import com.pharmasafe.repository.UserRepository;
import com.pharmasafe.service.*;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class EducationService {
  @PersistenceContext private EntityManager em;
  private final UserRepository users;
  private final EducationDomains domains;
  private final EducationAccess access;
  private final PasswordEncoder encoder;
  private final ObjectMapper json;
  private final AuditService audit;
  private final CasePdfRenderer renderer;

  public EducationService(
      UserRepository u,
      EducationDomains d,
      EducationAccess a,
      PasswordEncoder e,
      ObjectMapper j,
      AuditService au,
      CasePdfRenderer r) {
    users = u;
    domains = d;
    access = a;
    encoder = e;
    json = j;
    audit = au;
    renderer = r;
  }

  public record CollegeInput(
      @NotBlank @Pattern(regexp = "[A-Z0-9][A-Z0-9-]{2,15}") String code,
      @NotBlank @Size(max = 180) String collegeName) {}

  public record CollegeState(
      @NotNull @PositiveOrZero Long expectedVersion,
      @NotBlank @Size(max = 180) String collegeName,
      boolean active,
      @NotBlank @Size(max = 2000) String reason) {}

  public record AdminInput(
      @NotBlank @Pattern(regexp = "COLLEGE_ADMIN|DOMAIN_ADMIN") String kind,
      @Positive Long collegeId,
      @Size(max = 32) String domain,
      @NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{3,64}") String username,
      @NotBlank @Size(max = 128) String fullName,
      @NotBlank @Email @Size(max = 128) String email,
      @NotBlank @Size(min = 12, max = 72) String password,
      @NotBlank @Size(max = 2000) String reason) {}

  public record State(
      @NotNull @PositiveOrZero Long expectedVersion,
      boolean active,
      @NotBlank @Size(max = 2000) String reason) {}

  public record Reserve(@NotNull @Positive Long collegeId) {}

  public record Register(
      @NotNull @Positive Long collegeId,
      @Size(max = 64) String reservationToken,
      @NotBlank @Size(max = 128) String fullName,
      @NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{3,64}") String username,
      @NotBlank @Email @Size(max = 128) String email,
      @NotBlank @Size(min = 12, max = 72) String password,
      @NotBlank @Size(min = 12, max = 72) String confirmPassword,
      @NotEmpty @Size(min = 7, max = 7) Set<@NotBlank String> domains) {}

  public record Decision(
      @NotNull @PositiveOrZero Long expectedVersion,
      @NotBlank @Pattern(regexp = "APPROVED|REJECTED") String status,
      @NotBlank @Size(max = 2000) String reason) {}

  public record Assign(
      @NotNull @Positive Long collegeId,
      @NotBlank @Size(max = 32) String domain,
      @NotBlank @Size(max = 64) String stage,
      @NotBlank @Size(max = 160) String title,
      @NotBlank @Size(max = 5000) String description,
      @NotBlank @Size(max = 5000) String instructions,
      @NotNull Instant dueAt,
      @NotBlank @Pattern(regexp = "INDIVIDUAL|ALL_APPROVED|SELECTED") String recipientMode,
      @Size(max = 32) String studentCode,
      boolean requireDocument,
      @Positive Long sourceOrderId,
      @Size(max = 100)
          Set<@Pattern(regexp = "(?:ST-[0-9]{6,18}|[A-Z0-9][A-Z0-9-]{2,15}-[0-9]{4,15})") String>
              studentCodes) {}

  public record Submit(
      @NotNull @PositiveOrZero Long expectedVersion,
      @NotBlank @Pattern(regexp = "IN_PROGRESS|SUBMITTED") String status,
      @Min(0) @Max(100) int progress,
      @NotBlank @Size(max = 5000) String workDone,
      @NotNull @Size(max = 3000) String pendingWork,
      @NotNull @Size(max = 3000) String remainingWork,
      @NotNull @Size(max = 7) Map<String, String> evidence) {}

  public record Review(
      @NotNull @PositiveOrZero Long expectedVersion,
      @NotBlank @Pattern(regexp = "RETURNED|COMPLETED") String outcome,
      @NotBlank @Size(max = 3000) String feedback) {}

  public record Filter(
      @Positive Long collegeId,
      @Size(max = 32) String domain,
      @Size(max = 32) String studentCode,
      @Size(max = 16) String status) {}

  public record Download(byte[] bytes, String fileName) {}

  private record Actor(User user, String role, Long college, String domain, Long student) {}

  private User identity(String name) {
    return users
        .findByUsername(name)
        .orElseThrow(() -> new AccessDeniedException("Account unavailable"));
  }

  private Actor actor(String name) {
    var u = identity(name);
    if (!u.isApproved() || !u.isEnabled() || u.isLocked() || !access.enabled(u))
      throw new AccessDeniedException("College or administrator access is inactive");
    return switch (u.getRole()) {
      case "ROLE_ADMIN" -> new Actor(u, u.getRole(), null, null, null);
      case "ROLE_COLLEGE_ADMIN" -> {
        var g =
            one(
                "select g from EduCollegeAdmin g where g.user.id=:u",
                EduCollegeAdmin.class,
                Map.of("u", u.getId()));
        yield new Actor(u, u.getRole(), g.college.id, null, null);
      }
      case "ROLE_DOMAIN_ADMIN" -> {
        var g =
            one(
                "select g from EduDomainAdmin g where g.user.id=:u",
                EduDomainAdmin.class,
                Map.of("u", u.getId()));
        yield new Actor(u, u.getRole(), null, g.domain, null);
      }
      case "ROLE_STUDENT" -> {
        var s =
            one(
                "select s from EduStudent s where s.user.id=:u",
                EduStudent.class,
                Map.of("u", u.getId()));
        yield new Actor(u, u.getRole(), s.college.id, null, s.id);
      }
      default -> throw new AccessDeniedException("College workspace access required");
    };
  }

  private boolean root(Actor a) {
    return a.role.equals("ROLE_ADMIN");
  }

  private boolean student(Actor a) {
    return a.student != null;
  }

  private void rootOnly(Actor a) {
    if (!root(a)) throw new AccessDeniedException("Main Super Admin access required");
  }

  private void oversight(Actor a) {
    if (!root(a) && !a.role.equals("ROLE_DOMAIN_ADMIN"))
      throw new AccessDeniedException("Domain Admin access required");
  }

  private void manager(Actor a) {
    if (!root(a) && !a.role.equals("ROLE_COLLEGE_ADMIN") && !a.role.equals("ROLE_DOMAIN_ADMIN"))
      throw new AccessDeniedException("College Admin access required");
  }

  private <T> T find(Class<T> type, Long id, boolean lock) {
    T v = em.find(type, id, lock ? LockModeType.PESSIMISTIC_WRITE : LockModeType.NONE);
    if (v == null) throw new EntityNotFoundException("Record not found");
    return v;
  }

  private <T> List<T> list(String q, Class<T> t, Map<String, Object> p, int start, int max) {
    var query = em.createQuery(q, t);
    p.forEach(query::setParameter);
    return query.setFirstResult(start).setMaxResults(max).getResultList();
  }

  private <T> T one(String q, Class<T> t, Map<String, Object> p) {
    return list(q, t, p, 0, 1).stream()
        .findFirst()
        .orElseThrow(() -> new EntityNotFoundException("Record not found"));
  }

  private long count(String q, Map<String, Object> p) {
    var v = em.createQuery(q, Long.class);
    p.forEach(v::setParameter);
    return v.getSingleResult();
  }

  private void save(Object o) {
    em.persist(o);
    em.flush();
  }

  private void version(long current, Long expected) {
    if (expected == null || current != expected)
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Record changed. Refresh before saving.");
  }

  private void college(Actor a, Long id) {
    if (a.college != null && !a.college.equals(id))
      throw new AccessDeniedException("This college is outside your scope");
  }

  private EduMembership membership(Actor a, String domain, boolean lock) {
    var m =
        one(
            "select m from EduMembership m where m.student.id=:s and m.domain=:d",
            EduMembership.class,
            Map.of("s", a.student, "d", domain));
    if (lock) em.lock(m, LockModeType.PESSIMISTIC_WRITE);
    if (!m.status.equals("APPROVED"))
      throw new AccessDeniedException("This domain requires college approval");
    return m;
  }

  private void domain(Actor a, String d) {
    domains.get(d);
    if (a.domain != null && !a.domain.equals(d))
      throw new AccessDeniedException("This domain is outside your assigned authority");
    if (student(a)) membership(a, d, false);
  }

  private void scope(Actor a, Filter f, boolean required) {
    if (f.collegeId != null) college(a, f.collegeId);
    if (required && (f.domain == null || f.domain.isBlank()))
      throw new IllegalArgumentException("Select a domain");
    if (f.domain != null && !f.domain.isBlank()) domain(a, f.domain);
    if (f.studentCode != null && !f.studentCode.isBlank()) {
      var s = studentByCode(f.studentCode);
      college(a, s.college.id);
      if (f.collegeId != null && !f.collegeId.equals(s.college.id))
        throw new IllegalArgumentException("Student does not belong to the selected college");
      String requested = f.domain != null && !f.domain.isBlank() ? f.domain : a.domain;
      if (requested != null
          && count(
                  "select count(m) from EduMembership m where m.student.id=:s and m.domain=:d",
                  Map.of("s", s.id, "d", requested))
              == 0) throw new AccessDeniedException("Student is not registered for this domain");
      if (student(a) && !a.student.equals(s.id))
        throw new AccessDeniedException("Only your own student information is available");
    }
    if (f.status != null
        && !f.status.isBlank()
        && !Set.of("NOT_STARTED", "IN_PROGRESS", "SUBMITTED", "RETURNED", "COMPLETED", "PENDING")
            .contains(f.status)) throw new IllegalArgumentException("Select a valid work status");
  }

  private EduStudent studentByCode(String code) {
    return one(
        "select s from EduStudent s where s.studentCode=:code",
        EduStudent.class,
        Map.of("code", code.trim().toUpperCase(Locale.ROOT)));
  }

  private Map<String, Object> collegeView(EduCollege c) {
    return Map.of(
        "id", c.id, "code", c.code, "name", c.name, "active", c.active, "rowVersion", c.rowVersion);
  }

  public Map<String, Object> catalog() {
    return Map.of(
        "colleges",
        list(
                "select c from EduCollege c where c.active=true order by c.name",
                EduCollege.class,
                Map.of(),
                0,
                1000)
            .stream()
            .map(this::collegeView)
            .toList(),
        "domains",
        domains.all);
  }

  @Transactional
  public Map<String, Object> reserve(Reserve d) {
    var c = find(EduCollege.class, d.collegeId, true);
    if (!c.active) throw new IllegalArgumentException("College is not accepting registrations");
    var r = new EduReservation();
    String token = UUID.randomUUID().toString();
    r.college = c;
    r.tokenHash = UserAccessService.hash(token);
    r.expiresAt = Instant.now().plusSeconds(1200);
    save(r);
    return Map.of("reservationToken", token, "expiresAt", r.expiresAt);
  }

  private User newUser(
      String username, String fullName, String email, String password, String role) {
    if (username.trim().matches("(?i)(?:ST-[0-9]{6,18}|[A-Z0-9][A-Z0-9-]{2,15}-[0-9]{4,15})")
        && users.findStudentLoginName(username.trim()).isPresent())
      throw new IllegalArgumentException(
          "Choose a username different from the reserved Student ID format");
    UserAccessService.validatePassword(password);
    String normalized = username.trim().toLowerCase(Locale.ROOT),
        mail = email.trim().toLowerCase(Locale.ROOT);
    if (users.existsByUsername(normalized) || users.existsByEmailIgnoreCase(mail))
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Username or email is already registered");
    var u = new User(normalized, encoder.encode(password), role);
    u.setFullName(fullName.trim());
    u.setEmail(mail);
    u.setDepartment("Education");
    return users.saveAndFlush(u);
  }

  @Transactional
  public Map<String, Object> register(Register d) {
    if (!d.password.equals(d.confirmPassword))
      throw new IllegalArgumentException("Passwords do not match");
    d.domains.forEach(domains::get);
    if (d.domains.size() != 7
        || !d.domains.containsAll(domains.all.stream().map(EducationDomains.Domain::code).toList()))
      throw new IllegalArgumentException("Select all seven domains to register");
    var c = find(EduCollege.class, d.collegeId, true);
    if (!c.active) throw new IllegalArgumentException("College is inactive");
    EduReservation r;
    if (d.reservationToken == null || d.reservationToken.isBlank()) {
      r = new EduReservation();
      r.college = c;
      r.tokenHash = UserAccessService.hash(UUID.randomUUID().toString());
      r.expiresAt = Instant.now().plusSeconds(1200);
      save(r);
    } else
      r =
          one(
              "select r from EduReservation r where r.tokenHash=:token",
              EduReservation.class,
              Map.of("token", UserAccessService.hash(d.reservationToken)));
    em.lock(r, LockModeType.PESSIMISTIC_WRITE);
    if (r.consumed || !r.college.id.equals(c.id) || r.expiresAt.isBefore(Instant.now()))
      throw new IllegalArgumentException(
          "Student ID reservation expired or changed college. Generate a new ID.");
    var u = newUser(d.username, d.fullName, d.email, d.password, "ROLE_STUDENT");
    var sequence = em.find(EduStudentSequence.class, c.id, LockModeType.PESSIMISTIC_WRITE);
    if (sequence == null) {
      sequence = new EduStudentSequence();
      sequence.collegeId = c.id;
      save(sequence);
    }
    String generated;
    do {
      if (sequence.lastValue >= 999999999999999L)
        throw new IllegalStateException("Student identifier sequence is exhausted");
      sequence.lastValue++;
      generated = c.code + "-" + String.format(java.util.Locale.ROOT, "%04d", sequence.lastValue);
    } while (users.existsByUsername(generated.toLowerCase(java.util.Locale.ROOT)));
    r.studentCode = generated;
    em.flush();
    var s = new EduStudent();
    s.user = u;
    s.college = c;
    s.reservation = r;
    s.studentCode = r.studentCode();
    s.createdAt = Instant.now();
    s.updatedAt = s.createdAt;
    save(s);
    for (String code : d.domains) {
      var m = new EduMembership();
      m.student = s;
      m.domain = code;
      m.status = "PENDING";
      m.requestedAt = s.createdAt;
      save(m);
    }
    r.consumed = true;
    em.flush();
    audit.logAccountEvent(
        "STUDENT_REGISTERED",
        u.getUsername(),
        "student=" + s.studentCode + "; college=" + c.code + "; domains=" + d.domains);
    return Map.of(
        "studentCode",
        s.studentCode,
        "fullName",
        u.getFullName(),
        "collegeName",
        c.name,
        "registrationStatus",
        "PENDING_COLLEGE_APPROVAL",
        "message",
        "Registration saved. Sign in to track your college's domain approvals.");
  }

  public List<Map<String, Object>> colleges(String name) {
    return colleges(name, null);
  }

  public List<Map<String, Object>> colleges(String name, String selectedDomain) {
    var a = actor(name);
    if (selectedDomain != null && !selectedDomain.isBlank()) domain(a, selectedDomain);
    return list(
            "select c from EduCollege c"
                + (a.college == null ? "" : " where c.id=:id")
                + " order by c.name",
            EduCollege.class,
            a.college == null ? Map.of() : Map.of("id", a.college),
            0,
            1000)
        .stream()
        .map(
            c -> {
              var v = new LinkedHashMap<>(collegeView(c));
              v.put(
                  "students",
                  count(
                      "select count(distinct m.student.id) from EduMembership m where"
                          + " m.student.college.id=:c"
                          + (selectedDomain == null || selectedDomain.isBlank()
                              ? ""
                              : " and m.domain=:d"),
                      selectedDomain == null || selectedDomain.isBlank()
                          ? Map.of("c", c.id)
                          : Map.of("c", c.id, "d", selectedDomain)));
              return (Map<String, Object>) v;
            })
        .toList();
  }

  @Transactional
  public Map<String, Object> createCollege(String name, CollegeInput d) {
    var a = actor(name);
    oversight(a);
    if (count("select count(c) from EduCollege c where c.code=:code", Map.of("code", d.code)) > 0)
      throw new IllegalArgumentException("College code already exists");
    var c = new EduCollege();
    c.code = d.code;
    c.name = d.collegeName.trim();
    c.active = true;
    c.createdAt = Instant.now();
    save(c);
    audit.logAccountEvent("COLLEGE_CREATED", name, "college=" + c.code);
    return collegeView(c);
  }

  @Transactional
  public Map<String, Object> collegeState(String name, Long id, CollegeState d) {
    var a = actor(name);
    oversight(a);
    var c = find(EduCollege.class, id, true);
    version(c.rowVersion, d.expectedVersion);
    var before = collegeView(c);
    c.name = d.collegeName.trim();
    c.active = d.active;
    em.flush();
    audit.logAccountEvent(
        "COLLEGE_UPDATED",
        name,
        "college=" + c.code + "; active=" + c.active + "; reason=" + d.reason);
    changeLog(name, "COLLEGE", id, before, collegeView(c), d.reason);
    return collegeView(c);
  }

  private Map<String, Object> grantView(
      User u, String kind, Long id, Long collegeId, String domain, boolean active, long version) {
    var v = new LinkedHashMap<String, Object>();
    v.put("id", id);
    v.put("kind", kind);
    v.put("username", u.getUsername());
    v.put("fullName", u.getFullName());
    v.put("userId", u.getId());
    v.put("collegeId", collegeId);
    v.put("domain", domain);
    v.put("active", active);
    v.put("rowVersion", version);
    return v;
  }

  public List<Map<String, Object>> administrators(String name) {
    rootOnly(actor(name));
    var result = new ArrayList<Map<String, Object>>();
    list("select g from EduCollegeAdmin g order by g.id", EduCollegeAdmin.class, Map.of(), 0, 1000)
        .forEach(
            g ->
                result.add(
                    grantView(
                        g.user,
                        "COLLEGE_ADMIN",
                        g.id,
                        g.college.id,
                        null,
                        g.active,
                        g.rowVersion)));
    list("select g from EduDomainAdmin g order by g.id", EduDomainAdmin.class, Map.of(), 0, 1000)
        .forEach(
            g ->
                result.add(
                    grantView(
                        g.user, "DOMAIN_ADMIN", g.id, null, g.domain, g.active, g.rowVersion)));
    return result;
  }

  @Transactional
  public Map<String, Object> createAdmin(String name, AdminInput d) {
    var a = actor(name);
    rootOnly(a);
    if (d.kind.equals("COLLEGE_ADMIN")) {
      if (d.collegeId == null) throw new IllegalArgumentException("Select a college");
      var c = find(EduCollege.class, d.collegeId, true);
      if (!c.active) throw new IllegalArgumentException("Select an active college");
      var u = newUser(d.username, d.fullName, d.email, d.password, "ROLE_COLLEGE_ADMIN");
      var g = new EduCollegeAdmin();
      g.user = u;
      g.college = c;
      g.active = true;
      g.assignedAt = Instant.now();
      save(g);
      audit.logAccountEvent(
          "COLLEGE_ADMIN_CREATED",
          name,
          "user=" + u.getUsername() + "; college=" + c.code + "; reason=" + d.reason);
      return grantView(u, d.kind, g.id, c.id, null, true, g.rowVersion);
    }
    users.lockById(a.user.getId()).orElseThrow();
    if (count("select count(g) from EduDomainAdmin g where g.active=true", Map.of()) > 0)
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "One active Domain Admin already exists. Deactivate it before appointing a replacement.");
    var u = newUser(d.username, d.fullName, d.email, d.password, "ROLE_DOMAIN_ADMIN");
    var g = new EduDomainAdmin();
    g.user = u;
    g.domain = null;
    g.active = true;
    g.assignedAt = Instant.now();
    save(g);
    audit.logAccountEvent(
        "DOMAIN_ADMIN_CREATED",
        name,
        "user=" + u.getUsername() + "; scope=ALL_SEVEN_DOMAINS" + "; reason=" + d.reason);
    return grantView(u, d.kind, g.id, null, g.domain, true, g.rowVersion);
  }

  @Transactional
  public void adminState(String name, String kind, Long id, State d) {
    rootOnly(actor(name));
    User u;
    if (kind.equals("COLLEGE_ADMIN")) {
      var g = find(EduCollegeAdmin.class, id, true);
      version(g.rowVersion, d.expectedVersion);
      g.active = d.active;
      u = g.user;
    } else if (kind.equals("DOMAIN_ADMIN")) {
      var g = find(EduDomainAdmin.class, id, true);
      version(g.rowVersion, d.expectedVersion);
      users.lockById(identity(name).getId()).orElseThrow();
      if (d.active
          && !g.active
          && count("select count(x) from EduDomainAdmin x where x.active=true", Map.of()) > 0)
        throw new ResponseStatusException(
            HttpStatus.CONFLICT,
            "One active Domain Admin already exists. Deactivate it before appointing a"
                + " replacement.");
      g.active = d.active;
      u = g.user;
    } else throw new IllegalArgumentException("Invalid administrator kind");
    u.setEnabled(d.active);
    u.setAccountStatus(d.active ? "APPROVED" : "SUSPENDED");
    u.revokeSessions();
    em.flush();
    audit.logAccountEvent(
        "EDUCATION_ADMIN_STATE",
        name,
        "user=" + u.getUsername() + "; active=" + d.active + "; reason=" + d.reason);
  }

  private String where(Actor a, Filter f, String alias, Map<String, Object> p, boolean task) {
    var clauses = new ArrayList<String>();
    if (a.college != null) {
      clauses.add(alias + ".college.id=:ownCollege");
      p.put("ownCollege", a.college);
    }
    if (a.domain != null) {
      clauses.add(alias + ".domain=:ownDomain");
      p.put("ownDomain", a.domain);
    }
    if (student(a)) {
      clauses.add(alias + (task ? ".student.id" : ".id") + "=:ownStudent");
      p.put("ownStudent", a.student);
    }
    if (f.collegeId != null) {
      clauses.add(alias + ".college.id=:filterCollege");
      p.put("filterCollege", f.collegeId);
    }
    if (f.domain != null && !f.domain.isBlank() && task) {
      clauses.add(alias + ".domain=:filterDomain");
      p.put("filterDomain", f.domain);
    }
    if (f.studentCode != null && !f.studentCode.isBlank()) {
      clauses.add(alias + (task ? ".student.studentCode" : ".studentCode") + "=:studentCode");
      p.put("studentCode", f.studentCode.trim().toUpperCase(Locale.ROOT));
    }
    if (task && f.status != null && !f.status.isBlank()) {
      if (f.status.equals("PENDING")) clauses.add(alias + ".status <> 'COMPLETED'");
      else {
        clauses.add(alias + ".status=:status");
        p.put("status", f.status);
      }
    }
    if (task) clauses.add(alias + ".archived=false");
    return clauses.isEmpty() ? "" : " where " + String.join(" and ", clauses);
  }

  private Map<String, Object> stats(Actor a, Filter f) {
    var p = new HashMap<String, Object>();
    String w = where(a, f, "t", p, true);
    var q =
        em.createQuery(
            "select t.status,count(t),avg(t.progress) from EduTask t" + w + " group by t.status",
            Object[].class);
    p.forEach(q::setParameter);
    var counts = new LinkedHashMap<String, Long>();
    for (var s : List.of("NOT_STARTED", "IN_PROGRESS", "SUBMITTED", "RETURNED", "COMPLETED"))
      counts.put(s, 0L);
    long total = 0;
    double progress = 0;
    for (var row : q.getResultList()) {
      long n = (Long) row[1];
      counts.put((String) row[0], n);
      total += n;
      progress += ((Double) row[2]) * n;
    }
    var late = new HashMap<>(p);
    late.put("now", Instant.now());
    long overdue =
        count(
            "select count(t) from EduTask t"
                + w
                + (w.isEmpty() ? " where " : " and ")
                + "t.dueAt<:now and t.status<>'COMPLETED'",
            late);
    var ep = new HashMap<String, Object>();
    String ew = " where 1=1";
    if (f.domain != null && !f.domain.isBlank()) {
      ew += " and m.domain=:domain";
      ep.put("domain", f.domain);
    }
    Long ec = a.college == null ? f.collegeId : a.college;
    if (ec != null) {
      ew += " and s.college.id=:college";
      ep.put("college", ec);
    }
    if (a.student != null) {
      ew += " and s.id=:student";
      ep.put("student", a.student);
    }
    if (f.studentCode != null && !f.studentCode.isBlank()) {
      ew += " and s.studentCode=:code";
      ep.put("code", f.studentCode.trim().toUpperCase(Locale.ROOT));
    }
    long studentCount =
        count("select count(distinct s.id) from EduMembership m join m.student s" + ew, ep);
    var cp = new HashMap<String, Object>();
    String cw = " where 1=1";
    if (ec != null) {
      cw += " and c.id=:college";
      cp.put("college", ec);
    } else if (f.studentCode != null && !f.studentCode.isBlank()) {
      cw += " and c.id=:college";
      cp.put("college", studentByCode(f.studentCode).college.id);
    }
    long collegeCount = count("select count(c) from EduCollege c" + cw, cp);
    return Map.of(
        "students",
        studentCount,
        "colleges",
        collegeCount,
        "total",
        total,
        "statusCounts",
        counts,
        "averageProgress",
        total == 0 ? 0 : Math.round(progress / total),
        "overdue",
        overdue);
  }

  public Map<String, Object> overview(String name, Filter f) {
    var a = actor(name);
    if (f.collegeId != null) college(a, f.collegeId);
    if (f.domain != null && !f.domain.isBlank()) domain(a, f.domain);
    if (f.studentCode != null && !f.studentCode.isBlank()) scope(a, f, false);
    var cards = new ArrayList<Map<String, Object>>();
    for (var d : domains.all) {
      boolean allowed = a.domain == null || a.domain.equals(d.code());
      String status = allowed ? "AUTHORIZED" : "OUTSIDE_SCOPE";
      if (student(a)) {
        var m =
            list(
                "select m from EduMembership m where m.student.id=:s and m.domain=:d",
                EduMembership.class,
                Map.of("s", a.student, "d", d.code()),
                0,
                1);
        status = m.isEmpty() ? "NOT_SELECTED" : m.get(0).status;
        allowed = status.equals("APPROVED");
      }
      var v = new LinkedHashMap<String, Object>();
      v.put("code", d.code());
      v.put("name", d.name());
      v.put("description", d.description());
      v.put("authorized", allowed);
      v.put("status", status);
      v.put(
          "stats",
          allowed ? stats(a, new Filter(f.collegeId, d.code(), f.studentCode, f.status)) : null);
      cards.add(v);
    }
    var v = new LinkedHashMap<String, Object>();
    v.put("role", a.role);
    v.put("fullName", a.user.getFullName());
    v.put("username", a.user.getUsername());
    v.put("collegeId", a.college);
    v.put("assignedDomain", a.domain);
    v.put("studentCode", student(a) ? find(EduStudent.class, a.student, false).studentCode : null);
    v.put("domains", cards);
    v.put(
        "pendingStudentApprovals",
        a.role.equals("ROLE_COLLEGE_ADMIN")
            ? count(
                "select count(distinct m.student.id) from EduMembership m where"
                    + " m.student.college.id=:c and m.student.user.enabled=true and"
                    + " m.status='PENDING'",
                Map.of("c", a.college))
            : 0);
    v.put("updatedAt", Instant.now());
    return v;
  }

  private Map<String, Object> memberView(EduMembership m) {
    return Map.of(
        "id",
        m.id,
        "domain",
        m.domain,
        "status",
        m.status,
        "rowVersion",
        m.rowVersion,
        "reason",
        Objects.requireNonNullElse(m.reason, ""),
        "student",
        studentView(m.student));
  }

  private Map<String, Object> studentView(EduStudent s) {
    var v = new LinkedHashMap<String, Object>();
    v.put("id", s.id);
    v.put("studentCode", s.studentCode);
    v.put("collegeId", s.college.id);
    v.put("collegeName", s.college.name);
    v.put("fullName", s.user.getFullName());
    v.put("username", s.user.getUsername());
    v.put("email", s.user.getEmail());
    v.put("createdAt", s.createdAt);
    v.put("rowVersion", s.rowVersion);
    v.put("active", s.user.isEnabled());
    return v;
  }

  public Map<String, Object> students(String name, Filter f, String search, int page) {
    var a = actor(name);
    if (student(a))
      throw new AccessDeniedException("Student directory is restricted to administrators");
    scope(a, f, false);
    String d = f.domain == null || f.domain.isBlank() ? a.domain : f.domain;
    var p = new HashMap<String, Object>();
    String w = where(new Actor(a.user, a.role, a.college, null, null), f, "s", p, false);
    if (d != null && !d.isBlank()) {
      p.put("directoryDomain", d);
      w +=
          (w.isEmpty() ? " where " : " and ")
              + "exists(select m.id from EduMembership m where m.student.id=s.id and"
              + " m.domain=:directoryDomain)";
    }
    if (f.status != null && !f.status.isBlank()) {
      w +=
          (w.isEmpty() ? " where " : " and ")
              + "exists(select t.id from EduTask t where t.student.id=s.id and t.archived=false"
              + (d == null || d.isBlank() ? "" : " and t.domain=:directoryDomain")
              + (f.status.equals("PENDING")
                  ? " and t.status <> 'COMPLETED')"
                  : " and t.status=:workStatus)");
      if (!f.status.equals("PENDING")) p.put("workStatus", f.status);
    }
    if (search != null && !search.isBlank()) {
      if (search.length() > 100) throw new IllegalArgumentException("Search is too long");
      p.put(
          "term",
          "%" + search.toLowerCase(Locale.ROOT).replace("%", "\\%").replace("_", "\\_") + "%");
      w +=
          (w.isEmpty() ? " where " : " and ")
              + "(lower(s.studentCode) like :term escape '\\' or lower(s.user.fullName) like :term"
              + " escape '\\' or lower(s.user.username) like :term escape '\\' or"
              + " lower(s.user.email) like :term escape '\\')";
    }
    var content =
        list(
                "select s from EduStudent s" + w + " order by s.id",
                EduStudent.class,
                p,
                Math.max(0, page) * 20,
                20)
            .stream()
            .map(
                s -> {
                  var v = studentView(s);
                  v.put(
                      "domains",
                      list(
                              "select m from EduMembership m where m.student.id=:s"
                                  + (a.domain == null ? "" : " and m.domain=:domain")
                                  + " order by m.id",
                              EduMembership.class,
                              a.domain == null
                                  ? Map.of("s", s.id)
                                  : Map.of("s", s.id, "domain", a.domain),
                              0,
                              7)
                          .stream()
                          .map(m -> Map.of("domain", m.domain, "status", m.status))
                          .toList());
                  v.put("work", stats(a, new Filter(s.college.id, d, s.studentCode, null)));
                  return v;
                })
            .toList();
    return paged(content, count("select count(s) from EduStudent s" + w, p), page);
  }

  private Map<String, Object> paged(List<?> values, long total, int page) {
    return Map.of(
        "content",
        values,
        "totalElements",
        total,
        "page",
        Math.max(0, page),
        "totalPages",
        (total + 19) / 20);
  }

  public Map<String, Object> requests(
      String name, String domain, Long collegeId, String status, int page) {
    var a = actor(name);
    if (student(a))
      throw new AccessDeniedException("Access requests are restricted to administrators");
    scope(a, new Filter(collegeId, domain, null, null), false);
    var p = new HashMap<String, Object>();
    var clauses = new ArrayList<String>();
    if (a.college != null) {
      clauses.add("m.student.college.id=:own");
      p.put("own", a.college);
    }
    if (collegeId != null) {
      clauses.add("m.student.college.id=:college");
      p.put("college", collegeId);
    }
    String d = a.domain != null ? a.domain : domain;
    if (d != null && !d.isBlank()) {
      clauses.add("m.domain=:d");
      p.put("d", d);
    }
    if (status != null && !status.isBlank()) {
      if (!Set.of("PENDING", "APPROVED", "REJECTED").contains(status))
        throw new IllegalArgumentException("Invalid access status");
      clauses.add("m.status=:status");
      p.put("status", status);
    }
    String w = clauses.isEmpty() ? "" : " where " + String.join(" and ", clauses);
    return paged(
        list(
                "select m from EduMembership m" + w + " order by m.requestedAt desc,m.id desc",
                EduMembership.class,
                p,
                Math.max(0, page) * 20,
                20)
            .stream()
            .map(this::memberView)
            .toList(),
        count("select count(m) from EduMembership m" + w, p),
        page);
  }

  @Transactional
  public Map<String, Object> decide(String name, Long id, Decision d) {
    var a = actor(name);
    manager(a);
    if (a.role.equals("ROLE_DOMAIN_ADMIN"))
      throw new AccessDeniedException("Student approval belongs to the associated College Admin");
    var m = find(EduMembership.class, id, true);
    college(a, m.student.college.id);
    if (!m.student.college.active) throw new IllegalArgumentException("College is inactive");
    version(m.rowVersion, d.expectedVersion);
    if (m.status.equals(d.status))
      throw new IllegalArgumentException("Access already has this status");
    m.status = d.status;
    m.reviewer = a.user;
    m.reason = d.reason.trim();
    m.reviewedAt = Instant.now();
    em.flush();
    audit.logAccountEvent(
        "STUDENT_DOMAIN_DECISION",
        name,
        "student="
            + m.student.studentCode
            + "; domain="
            + m.domain
            + "; status="
            + m.status
            + "; reason="
            + m.reason);
    return memberView(m);
  }

  private Map<String, Object> taskView(EduTask t) {
    var v = new LinkedHashMap<String, Object>();
    v.put("id", t.id);
    v.put("collegeId", t.college.id);
    v.put("collegeName", t.college.name);
    v.put("studentCode", t.student.studentCode);
    v.put("studentName", t.student.user.getFullName());
    v.put("domain", t.domain);
    v.put("stage", t.stage);
    v.put("title", t.title);
    v.put("description", t.description);
    v.put("instructions", t.instructions);
    v.put("dueAt", t.dueAt);
    v.put("status", t.status);
    v.put("progress", t.progress);
    v.put("requireDocument", t.requireDocument);
    v.put("rowVersion", t.rowVersion);
    v.put("archived", t.archived);
    v.put("sourceOrderId", t.sourceOrder == null ? null : t.sourceOrder.id);
    v.put("assignedBy", t.assignedBy.getUsername());
    v.put("createdAt", t.createdAt);
    v.put("updatedAt", t.updatedAt);
    return v;
  }

  public Map<String, Object> tasks(String name, Filter f, int page) {
    var a = actor(name);
    scope(a, f, true);
    var p = new HashMap<String, Object>();
    String w = where(a, f, "t", p, true);
    return paged(
        list(
                "select t from EduTask t" + w + " order by t.updatedAt desc,t.id desc",
                EduTask.class,
                p,
                Math.max(0, page) * 20,
                20)
            .stream()
            .map(this::taskView)
            .toList(),
        count("select count(t) from EduTask t" + w, p),
        page);
  }

  @Transactional
  public List<Map<String, Object>> assign(String name, Assign d) {
    var a = actor(name);
    manager(a);
    college(a, d.collegeId);
    var c = find(EduCollege.class, d.collegeId, true);
    if (!c.active) throw new IllegalArgumentException("College is inactive");
    var desc = domains.get(d.domain);
    if (!desc.stages().contains(d.stage))
      throw new IllegalArgumentException("Select a stage belonging to this domain");
    if (!d.dueAt.isAfter(Instant.now()))
      throw new IllegalArgumentException("Select a future deadline");
    var p = new HashMap<String, Object>(Map.of("c", c.id, "d", d.domain));
    String filter = "";
    EduWorkOrder order = null;
    if (d.sourceOrderId != null) {
      order = find(EduWorkOrder.class, d.sourceOrderId, true);
      college(a, order.college.id);
      domain(a, order.domain);
      if (order.archived
          || !order.college.id.equals(c.id)
          || !order.domain.equals(d.domain)
          || !order.title.equals(d.title.trim())
          || !order.description.equals(d.description.trim())
          || !order.instructions.equals(d.instructions.trim())
          || !order.stage.equals(d.stage)
          || d.dueAt
              .truncatedTo(java.time.temporal.ChronoUnit.MILLIS)
              .isAfter(order.dueAt.truncatedTo(java.time.temporal.ChronoUnit.MILLIS))
          || order.requireDocument != d.requireDocument)
        throw new IllegalArgumentException(
            "Use the original college work instructions and a deadline no later than its due date");
    }
    if (d.recipientMode.equals("SELECTED")) {
      if (d.studentCodes == null || d.studentCodes.isEmpty())
        throw new IllegalArgumentException("Select at least one Student ID");
      filter = " and m.student.studentCode in :codes";
      p.put("codes", d.studentCodes);
    }
    if (d.recipientMode.equals("INDIVIDUAL")) {
      if (d.studentCode == null || d.studentCode.isBlank())
        throw new IllegalArgumentException("Enter a Student ID");
      filter = " and m.student.studentCode=:code";
      p.put("code", d.studentCode.trim().toUpperCase(Locale.ROOT));
    }
    var recipients =
        list(
            "select m from EduMembership m where m.student.college.id=:c and m.domain=:d and"
                + " m.status='APPROVED' and m.student.user.enabled=true and"
                + " m.student.user.accountStatus='APPROVED' and m.student.user.locked=false"
                + filter
                + " order by m.id",
            EduMembership.class,
            p,
            0,
            501);
    if (recipients.isEmpty())
      throw new IllegalArgumentException("No approved students match this college and domain");
    if (recipients.size() > 500)
      throw new IllegalArgumentException("Select individual students for a cohort larger than 500");
    if (d.recipientMode.equals("SELECTED") && recipients.size() != d.studentCodes.size())
      throw new IllegalArgumentException(
          "Every selected Student ID must have approved access in this college and domain");
    var result = new ArrayList<Map<String, Object>>();
    for (var m : recipients) {
      em.lock(m, LockModeType.PESSIMISTIC_WRITE);
      if (!m.status.equals("APPROVED"))
        throw new IllegalArgumentException("Student access changed. Refresh before assignment.");
      var t = new EduTask();
      t.sourceOrder = order;
      t.college = c;
      t.student = m.student;
      t.assignedBy = a.user;
      t.domain = d.domain;
      t.stage = d.stage;
      t.title = d.title.trim();
      t.description = d.description.trim();
      t.instructions = d.instructions.trim();
      t.dueAt = d.dueAt.truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
      t.requireDocument = d.requireDocument;
      t.status = "NOT_STARTED";
      t.createdAt = Instant.now();
      t.updatedAt = t.createdAt;
      save(t);
      result.add(taskView(t));
    }
    audit.logAccountEvent(
        "EDUCATION_WORK_ASSIGNED",
        name,
        "college=" + c.code + "; domain=" + d.domain + "; recipients=" + result.size());
    return result;
  }

  private EduTask authorizedTask(Actor a, Long id, boolean lock) {
    var t = find(EduTask.class, id, lock);
    college(a, t.college.id);
    domain(a, t.domain);
    if (student(a) && !a.student.equals(t.student.id))
      throw new AccessDeniedException("Only your own work is available");
    return t;
  }

  private Map<String, Object> submissionView(EduSubmission s) {
    var v = new LinkedHashMap<String, Object>();
    v.put("id", s.id);
    v.put("status", s.status);
    v.put("progress", s.progress);
    v.put("workDone", s.workDone);
    v.put("pendingWork", s.pendingWork);
    v.put("remainingWork", s.remainingWork);
    v.put("evidence", parse(s.evidenceJson));
    v.put("createdAt", s.createdAt);
    v.put("studentCode", s.student.studentCode);
    v.put("sourceVersion", s.sourceVersion);
    v.put(
        "documents",
        list(
                "select d from EduDocument d where d.submission.id=:s order by d.id",
                EduDocument.class,
                Map.of("s", s.id),
                0,
                20)
            .stream()
            .map(
                d ->
                    Map.of(
                        "id",
                        d.id,
                        "fileName",
                        d.fileName,
                        "sha256",
                        d.sha256,
                        "createdAt",
                        d.createdAt))
            .toList());
    v.put(
        "feedback",
        list(
                "select f from EduFeedback f where f.submission.id=:s order by f.id",
                EduFeedback.class,
                Map.of("s", s.id),
                0,
                20)
            .stream()
            .map(
                f ->
                    Map.of(
                        "id",
                        f.id,
                        "outcome",
                        f.outcome,
                        "feedback",
                        f.feedback,
                        "reviewer",
                        f.reviewer.getUsername(),
                        "createdAt",
                        f.createdAt))
            .toList());
    return v;
  }

  public Map<String, Object> detail(String name, Long id, int page) {
    var a = actor(name);
    var t = authorizedTask(a, id, false);
    var v = taskView(t);
    v.put(
        "submissions",
        paged(
            list(
                    "select s from EduSubmission s where s.task.id=:t order by s.id desc",
                    EduSubmission.class,
                    Map.of("t", id),
                    Math.max(0, page) * 20,
                    20)
                .stream()
                .map(this::submissionView)
                .toList(),
            count("select count(s) from EduSubmission s where s.task.id=:t", Map.of("t", id)),
            page));
    return v;
  }

  private String encode(Object data) {
    try {
      return json.writeValueAsString(data);
    } catch (Exception e) {
      throw new IllegalStateException("Evidence encoding failed");
    }
  }

  private Object parse(String value) {
    try {
      return json.readTree(value);
    } catch (Exception e) {
      throw new IllegalStateException("Stored evidence could not be read");
    }
  }

  private byte[] file(MultipartFile f) {
    try {
      if (f.getSize() > 5 * 1024 * 1024 || f.getSize() < 5)
        throw new IllegalArgumentException("Upload a PDF of at most 5 MB");
      byte[] b = f.getBytes();
      if (!new String(b, 0, 5, java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-"))
        throw new IllegalArgumentException("Only PDF documents are supported");
      return b;
    } catch (java.io.IOException e) {
      throw new IllegalArgumentException("Document could not be read");
    }
  }

  @Transactional
  public Map<String, Object> submit(String name, Long id, Submit d, MultipartFile upload) {
    var a = actor(name);
    if (!student(a)) throw new AccessDeniedException("Only the assigned student can submit work");
    var t = authorizedTask(a, id, true);
    if (t.archived) throw new IllegalArgumentException("This work has been archived");
    membership(a, t.domain, true);
    version(t.rowVersion, d.expectedVersion);
    if (Set.of("SUBMITTED", "COMPLETED").contains(t.status))
      throw new IllegalArgumentException("This work is awaiting review or completed");
    boolean finalWork = d.status.equals("SUBMITTED");
    if ((finalWork && d.progress != 100) || (!finalWork && d.progress >= 100))
      throw new IllegalArgumentException("Only submitted work can have 100 percent progress");
    if (finalWork && (!d.pendingWork.isBlank() || !d.remainingWork.isBlank()))
      throw new IllegalArgumentException(
          "Resolve pending and remaining work before final submission");
    domains.evidence(t.domain, d.evidence, finalWork);
    if (finalWork && t.requireDocument && (upload == null || upload.isEmpty()))
      throw new IllegalArgumentException("Upload the required PDF with your final submission");
    byte[] bytes = upload == null || upload.isEmpty() ? null : file(upload);
    var s = new EduSubmission();
    s.task = t;
    s.student = t.student;
    s.sourceVersion = t.rowVersion;
    s.status = d.status;
    s.progress = d.progress;
    s.workDone = d.workDone.trim();
    s.pendingWork = d.pendingWork.trim();
    s.remainingWork = d.remainingWork.trim();
    s.evidenceJson = encode(d.evidence);
    s.createdAt = Instant.now();
    save(s);
    if (bytes != null) {
      var doc = new EduDocument();
      doc.submission = s;
      String original =
          Objects.requireNonNullElse(upload.getOriginalFilename(), "student-evidence.pdf")
              .replace('\\', '/');
      original =
          original.substring(original.lastIndexOf('/') + 1).replaceAll("[^A-Za-z0-9._ -]", "_");
      if (!original.toLowerCase(Locale.ROOT).endsWith(".pdf"))
        throw new IllegalArgumentException("Use a .pdf filename");
      doc.fileName = original.length() > 160 ? "student-evidence.pdf" : original;
      doc.bytes = bytes;
      doc.sha256 = hashBytes(bytes);
      doc.createdAt = s.createdAt;
      save(doc);
    }
    t.status = d.status;
    t.progress = d.progress;
    t.updatedAt = Instant.now();
    em.flush();
    audit.logAccountEvent(
        "STUDENT_WORK_SUBMITTED",
        name,
        "task=" + id + "; student=" + t.student.studentCode + "; status=" + t.status);
    return detail(name, id, 0);
  }

  @Transactional
  public Map<String, Object> review(String name, Long id, Review d) {
    var a = actor(name);
    manager(a);
    var t = authorizedTask(a, id, true);
    if (t.archived) throw new IllegalArgumentException("This work has been archived");
    version(t.rowVersion, d.expectedVersion);
    if (!t.status.equals("SUBMITTED"))
      throw new IllegalArgumentException("Only submitted work can be reviewed");
    var s =
        one(
            "select s from EduSubmission s where s.task.id=:t order by s.id desc",
            EduSubmission.class,
            Map.of("t", id));
    var f = new EduFeedback();
    f.task = t;
    f.submission = s;
    f.reviewer = a.user;
    f.outcome = d.outcome;
    f.feedback = d.feedback.trim();
    f.createdAt = Instant.now();
    save(f);
    t.status = d.outcome;
    if (d.outcome.equals("RETURNED")) t.progress = Math.min(99, t.progress);
    t.updatedAt = Instant.now();
    em.flush();
    audit.logAccountEvent("EDUCATION_WORK_REVIEWED", name, "task=" + id + "; outcome=" + d.outcome);
    return detail(name, id, 0);
  }

  private String hashBytes(byte[] bytes) {
    try {
      return HexFormat.of()
          .formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public Download document(String name, Long taskId, Long docId) {
    var a = actor(name);
    authorizedTask(a, taskId, false);
    var doc = find(EduDocument.class, docId, false);
    if (!doc.submission.task.id.equals(taskId))
      throw new AccessDeniedException("Document is outside this task");
    if (!hashBytes(doc.bytes).equals(doc.sha256))
      throw new IllegalStateException("Stored document integrity check failed");
    return new Download(doc.bytes, doc.fileName);
  }

  private Map<String, Object> reportView(EduReport r) {
    var v = new LinkedHashMap<String, Object>();
    v.put("id", r.id);
    v.put("domain", r.domain);
    v.put("collegeId", r.college == null ? null : r.college.id);
    v.put("studentCode", r.student == null ? null : r.student.studentCode);
    v.put("status", r.filterStatus);
    v.put("fileName", r.fileName);
    v.put("sha256", r.sha256);
    v.put("createdAt", r.createdAt);
    v.put("generatedBy", r.generatedBy.getUsername());
    return v;
  }

  @Transactional(isolation = Isolation.REPEATABLE_READ)
  public Map<String, Object> report(String name, Filter f) {
    var a = actor(name);
    scope(a, f, true);
    Long collegeId = a.college != null ? a.college : f.collegeId;
    EduStudent s =
        student(a)
            ? find(EduStudent.class, a.student, false)
            : f.studentCode == null || f.studentCode.isBlank()
                ? null
                : studentByCode(f.studentCode);
    if (collegeId == null && s != null) collegeId = s.college.id;
    var p = new HashMap<String, Object>();
    String w = where(a, f, "t", p, true);
    long total = count("select count(t) from EduTask t" + w, p);
    if (total > 200)
      throw new IllegalArgumentException(
          "Report exceeds 200 tasks. Narrow the college, student or status filter.");
    var rows = list("select t from EduTask t" + w + " order by t.id", EduTask.class, p, 0, 200);
    var snapshot = new LinkedHashMap<String, Object>();
    snapshot.put(
        "documentControl",
        Map.of(
            "documentKind",
            "EDUCATION_PROGRESS",
            "generatedBy",
            name,
            "generatedAt",
            Instant.now(),
            "domain",
            domains.get(f.domain).name()));
    snapshot.put(
        "filters",
        Map.of(
            "college",
            collegeId == null
                ? "All authorized colleges"
                : find(EduCollege.class, collegeId, false).name,
            "student",
            s == null ? "All authorized students" : s.studentCode,
            "status",
            f.status == null || f.status.isBlank() ? "All statuses" : f.status));
    snapshot.put("statistics", stats(a, f));
    snapshot.put(
        "tasks",
        rows.stream()
            .map(
                t -> {
                  var v = taskView(t);
                  var latest =
                      list(
                          "select s from EduSubmission s where s.task.id=:t order by s.id desc",
                          EduSubmission.class,
                          Map.of("t", t.id),
                          0,
                          1);
                  v.put(
                      "latestSubmission", latest.isEmpty() ? null : submissionView(latest.get(0)));
                  return v;
                })
            .toList());
    var r = new EduReport();
    r.generatedBy = a.user;
    r.college = collegeId == null ? null : find(EduCollege.class, collegeId, false);
    r.student = s;
    r.domain = f.domain;
    r.filterStatus = f.status;
    r.createdAt = Instant.now();
    r.snapshotJson = encode(snapshot);
    try {
      r.pdfBytes =
          renderer.render(
              json.readTree(r.snapshotJson),
              r.snapshotJson.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    } catch (IllegalArgumentException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("Report PDF generation failed", e);
    }
    r.sha256 = hashBytes(r.pdfBytes);
    r.fileName = "PharmaSafe_Student_Progress_" + UUID.randomUUID() + ".pdf";
    save(r);
    audit.logAccountEvent(
        "EDUCATION_REPORT_EXPORTED",
        name,
        "report=" + r.id + "; tasks=" + total + "; domain=" + r.domain);
    return reportView(r);
  }

  public Map<String, Object> reports(String name, Filter f, int page) {
    var a = actor(name);
    scope(a, f, true);
    var p = new HashMap<String, Object>(Map.of("d", f.domain));
    String w = " where r.domain=:d";
    if (!root(a) && !a.role.equals("ROLE_DOMAIN_ADMIN")) {
      w += " and r.generatedBy.id=:u";
      p.put("u", a.user.getId());
    }
    Long collegeId = a.college != null ? a.college : f.collegeId;
    if (collegeId != null) {
      w += " and r.college.id=:c";
      p.put("c", collegeId);
    }
    if (f.studentCode != null && !f.studentCode.isBlank()) {
      w += " and r.student.studentCode=:s";
      p.put("s", f.studentCode.trim().toUpperCase(Locale.ROOT));
    }
    if (f.status != null && !f.status.isBlank()) {
      w += " and r.filterStatus=:status";
      p.put("status", f.status);
    }
    return paged(
        list(
                "select r from EduReport r" + w + " order by r.id desc",
                EduReport.class,
                p,
                Math.max(0, page) * 20,
                20)
            .stream()
            .map(this::reportView)
            .toList(),
        count("select count(r) from EduReport r" + w, p),
        page);
  }

  public Download reportPdf(String name, Long id) {
    var a = actor(name);
    var r = find(EduReport.class, id, false);
    if (!root(a)
        && !a.role.equals("ROLE_DOMAIN_ADMIN")
        && !r.generatedBy.getId().equals(a.user.getId()))
      throw new AccessDeniedException("Report is outside your export history");
    scope(
        a,
        new Filter(
            r.college == null ? null : r.college.id,
            r.domain,
            r.student == null ? null : r.student.studentCode,
            r.filterStatus),
        true);
    if (!hashBytes(r.pdfBytes).equals(r.sha256))
      throw new IllegalStateException("Stored report integrity check failed");
    return new Download(r.pdfBytes, r.fileName);
  }

  public record StudentCreate(
      @NotNull @Positive Long collegeId,
      @NotBlank String domain,
      @NotBlank @Size(max = 128) String fullName,
      @NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{3,64}") String username,
      @NotBlank @Email @Size(max = 128) String email,
      @NotBlank @Size(min = 12, max = 72) String password,
      @NotBlank @Size(max = 2000) String reason) {}

  public record StudentEdit(
      @NotNull @PositiveOrZero Long expectedVersion,
      @NotBlank String domain,
      @NotBlank @Size(max = 128) String fullName,
      @NotBlank @Email @Size(max = 128) String email,
      boolean active,
      @NotBlank @Size(max = 2000) String reason) {}

  public record WorkEdit(
      @NotNull @PositiveOrZero Long expectedVersion,
      @NotBlank @Size(max = 160) String title,
      @NotBlank @Size(max = 5000) String description,
      @NotBlank @Size(max = 5000) String instructions,
      @NotNull Instant dueAt,
      boolean requireDocument,
      boolean archived,
      @NotBlank @Size(max = 2000) String reason) {}

  public record OrderInput(
      @NotNull @Positive Long collegeId,
      @NotBlank String domain,
      @NotBlank String stage,
      @NotBlank @Size(max = 160) String title,
      @NotBlank @Size(max = 5000) String description,
      @NotBlank @Size(max = 5000) String instructions,
      @NotNull Instant dueAt,
      boolean requireDocument) {}

  public record Preferences(boolean compactView, boolean reduceMotion) {}

  private void changeLog(
      String name, String kind, Long id, Object before, Object after, String reason) {
    var change = new EduManagementChange();
    change.actorId = identity(name).getId();
    change.recordKind = kind;
    change.recordId = id;
    change.beforeJson = encode(before);
    change.afterJson = encode(after);
    change.reason = reason;
    change.createdAt = Instant.now();
    save(change);
    audit.logAccountEvent(
        "EDUCATION_" + kind + "_CHANGED", name, "record=" + id + "; reason=" + reason);
  }

  @Transactional
  public Map<String, Object> addStudent(String name, StudentCreate d) {
    var a = actor(name);
    manager(a);
    college(a, d.collegeId);
    domain(a, d.domain);
    var reservation = reserve(new Reserve(d.collegeId));
    var codes = new LinkedHashSet<String>();
    domains.all.forEach(x -> codes.add(x.code()));
    var result =
        register(
            new Register(
                d.collegeId,
                (String) reservation.get("reservationToken"),
                d.fullName,
                d.username,
                d.email,
                d.password,
                d.password,
                codes));
    var s = studentByCode((String) result.get("studentCode"));
    var m =
        one(
            "select m from EduMembership m where m.student.id=:s and m.domain=:d",
            EduMembership.class,
            Map.of("s", s.id, "d", d.domain));
    if (!a.role.equals("ROLE_DOMAIN_ADMIN"))
      decide(name, m.id, new Decision(m.rowVersion, "APPROVED", d.reason));
    return studentView(s);
  }

  public Map<String, Object> studentProfile(String name, Long id, String domain) {
    var a = actor(name);
    var s = find(EduStudent.class, id, false);
    college(a, s.college.id);
    if (student(a) && !a.student.equals(id))
      throw new AccessDeniedException("Only your own profile is available");
    if (domain != null && !domain.isBlank()) domain(a, domain);
    var v = studentView(s);
    v.put("work", stats(a, new Filter(s.college.id, domain, s.studentCode, null)));
    v.put(
        "domains",
        list(
                "select m from EduMembership m where m.student.id=:s order by m.id",
                EduMembership.class,
                Map.of("s", id),
                0,
                7)
            .stream()
            .map(m -> Map.of("domain", m.domain, "status", m.status))
            .toList());
    return v;
  }

  public Map<String, Object> myProfile(String name) {
    var a = actor(name);
    return student(a)
        ? studentProfile(name, a.student, null)
        : Map.of(
            "fullName",
            Objects.requireNonNullElse(a.user.getFullName(), a.user.getUsername()),
            "username",
            a.user.getUsername(),
            "email",
            Objects.requireNonNullElse(a.user.getEmail(), ""),
            "role",
            a.role,
            "college",
            a.college == null ? "All colleges" : find(EduCollege.class, a.college, false).name);
  }

  @Transactional
  public Map<String, Object> editStudent(String name, Long id, StudentEdit d) {
    var a = actor(name);
    manager(a);
    domain(a, d.domain);
    var s = find(EduStudent.class, id, true);
    college(a, s.college.id);
    version(s.rowVersion, d.expectedVersion);
    var before = studentView(s);
    var mail = d.email.trim().toLowerCase(Locale.ROOT);
    var existing = users.findByEmailIgnoreCase(mail);
    if (existing.isPresent() && !existing.get().getId().equals(s.user.getId()))
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
    boolean revoke =
        !mail.equalsIgnoreCase(Objects.requireNonNullElse(s.user.getEmail(), ""))
            || s.user.isEnabled() != d.active
            || s.user.isApproved() != d.active;
    s.user.setFullName(d.fullName.trim());
    s.user.setEmail(mail);
    s.user.setEnabled(d.active);
    s.user.setAccountStatus(d.active ? "APPROVED" : "SUSPENDED");
    if (revoke) s.user.revokeSessions();
    s.updatedAt = Instant.now();
    em.flush();
    var after = studentView(s);
    changeLog(name, "STUDENT", id, before, after, d.reason);
    return after;
  }

  @Transactional
  public Map<String, Object> editTask(String name, Long id, WorkEdit d) {
    var a = actor(name);
    manager(a);
    var t = authorizedTask(a, id, true);
    version(t.rowVersion, d.expectedVersion);
    var before = taskView(t);
    if (!d.archived) {
      if (!t.status.equals("NOT_STARTED")
          || t.progress != 0
          || count("select count(s) from EduSubmission s where s.task.id=:t", Map.of("t", id)) > 0)
        throw new ResponseStatusException(
            HttpStatus.CONFLICT,
            "Work with student activity is retained unchanged. Archive it and create replacement"
                + " work instead.");
      if (!d.dueAt.isAfter(Instant.now()))
        throw new IllegalArgumentException("Select a future deadline");
      if (t.sourceOrder != null
          && (!d.title.trim().equals(t.sourceOrder.title)
              || !d.description.trim().equals(t.sourceOrder.description)
              || !d.instructions.trim().equals(t.sourceOrder.instructions)
              || d.dueAt
                  .truncatedTo(java.time.temporal.ChronoUnit.MILLIS)
                  .isAfter(t.sourceOrder.dueAt.truncatedTo(java.time.temporal.ChronoUnit.MILLIS))
              || d.requireDocument != t.sourceOrder.requireDocument))
        throw new AccessDeniedException(
            "College instructions must match the Domain Admin work order");
      t.title = d.title.trim();
      t.description = d.description.trim();
      t.instructions = d.instructions.trim();
      t.dueAt = d.dueAt.truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
      t.requireDocument = d.requireDocument;
    }
    t.archived = d.archived;
    t.lastChangedBy = a.user;
    t.updatedAt = Instant.now();
    em.flush();
    var after = taskView(t);
    changeLog(name, "TASK", id, before, after, d.reason);
    return after;
  }

  private Map<String, Object> orderView(EduWorkOrder o) {
    var v = new LinkedHashMap<String, Object>();
    v.put("id", o.id);
    v.put("collegeId", o.college.id);
    v.put("collegeName", o.college.name);
    v.put("domain", o.domain);
    v.put("stage", o.stage);
    v.put("title", o.title);
    v.put("description", o.description);
    v.put("instructions", o.instructions);
    v.put("dueAt", o.dueAt);
    v.put("requireDocument", o.requireDocument);
    v.put("rowVersion", o.rowVersion);
    v.put("archived", o.archived);
    v.put(
        "assignedStudents",
        count("select count(t) from EduTask t where t.sourceOrder.id=:id", Map.of("id", o.id)));
    return v;
  }

  public List<Map<String, Object>> orders(String name, Filter f) {
    var a = actor(name);
    manager(a);
    scope(a, f, true);
    var p = new HashMap<String, Object>(Map.of("d", f.domain));
    String w = " where o.domain=:d and o.archived=false";
    Long c = a.college == null ? f.collegeId : a.college;
    if (c != null) {
      w += " and o.college.id=:c";
      p.put("c", c);
    }
    return list(
            "select o from EduWorkOrder o" + w + " order by o.id desc",
            EduWorkOrder.class,
            p,
            0,
            500)
        .stream()
        .map(this::orderView)
        .toList();
  }

  @Transactional
  public Map<String, Object> addOrder(String name, OrderInput d) {
    var a = actor(name);
    oversight(a);
    domain(a, d.domain);
    var c = find(EduCollege.class, d.collegeId, true);
    if (!c.active) throw new IllegalArgumentException("College is inactive");
    if (!domains.get(d.domain).stages().contains(d.stage))
      throw new IllegalArgumentException("Choose a domain stage");
    if (!d.dueAt.isAfter(Instant.now()))
      throw new IllegalArgumentException("Select a future deadline");
    var o = new EduWorkOrder();
    o.college = c;
    o.createdBy = a.user;
    o.domain = d.domain;
    o.stage = d.stage;
    o.title = d.title.trim();
    o.description = d.description.trim();
    o.instructions = d.instructions.trim();
    o.dueAt = d.dueAt.truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
    o.requireDocument = d.requireDocument;
    o.createdAt = Instant.now();
    save(o);
    changeLog(name, "ORDER", o.id, Map.of(), orderView(o), "College work order created");
    return orderView(o);
  }

  @Transactional
  public Map<String, Object> editOrder(String name, Long id, WorkEdit d) {
    var a = actor(name);
    oversight(a);
    var o = find(EduWorkOrder.class, id, true);
    domain(a, o.domain);
    version(o.rowVersion, d.expectedVersion);
    var before = orderView(o);
    if (!d.archived) {
      if (count("select count(t) from EduTask t where t.sourceOrder.id=:id", Map.of("id", id)) > 0)
        throw new ResponseStatusException(
            HttpStatus.CONFLICT,
            "Dispatched work instructions are retained. Archive and create a replacement order.");
      if (!d.dueAt.isAfter(Instant.now()))
        throw new IllegalArgumentException("Choose a future deadline");
      o.title = d.title.trim();
      o.description = d.description.trim();
      o.instructions = d.instructions.trim();
      o.dueAt = d.dueAt.truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
      o.requireDocument = d.requireDocument;
    }
    o.archived = d.archived;
    o.lastChangedBy = a.user;
    em.flush();
    var after = orderView(o);
    changeLog(name, "ORDER", id, before, after, d.reason);
    return after;
  }

  public Preferences preferences(String name) {
    var a = actor(name);
    var found =
        list(
            "select p from EduPreference p where p.userId=:id",
            EduPreference.class,
            Map.of("id", a.user.getId()),
            0,
            1);
    return found.isEmpty()
        ? new Preferences(false, false)
        : new Preferences(found.get(0).compactView, found.get(0).reduceMotion);
  }

  @Transactional
  public Preferences preferences(String name, Preferences d) {
    var a = actor(name);
    var p = em.find(EduPreference.class, a.user.getId());
    if (p == null) {
      p = new EduPreference();
      p.userId = a.user.getId();
      em.persist(p);
    }
    p.compactView = d.compactView;
    p.reduceMotion = d.reduceMotion;
    return d;
  }

  public record StudentAccessDecision(
      @NotBlank @Pattern(regexp = "APPROVED|REJECTED") String status,
      @NotBlank @Size(max = 2000) String reason,
      @NotEmpty @Size(max = 7)
          Map<@NotBlank String, @NotNull @PositiveOrZero Long> expectedVersions) {}

  private void collegeApprover(Actor a) {
    if (!a.role.equals("ROLE_COLLEGE_ADMIN"))
      throw new AccessDeniedException(
          "Only the student's College Admin may grant college-wide student access");
  }

  public Map<String, Object> pendingStudentAccess(String name, int page) {
    var a = actor(name);
    collegeApprover(a);
    var p = Map.<String, Object>of("c", a.college);
    String w =
        " where s.college.id=:c and s.user.enabled=true and exists(select m.id from EduMembership m"
            + " where m.student.id=s.id and m.status='PENDING')";
    var content =
        list(
                "select s from EduStudent s" + w + " order by s.createdAt desc,s.id desc",
                EduStudent.class,
                p,
                Math.max(0, page) * 20,
                20)
            .stream()
            .map(
                s -> {
                  var v = studentView(s);
                  v.put(
                      "pendingDomains",
                      list(
                              "select m from EduMembership m where m.student.id=:s and"
                                  + " m.status='PENDING' order by m.domain",
                              EduMembership.class,
                              Map.of("s", s.id),
                              0,
                              7)
                          .stream()
                          .map(
                              m ->
                                  Map.of(
                                      "domain",
                                      m.domain,
                                      "name",
                                      domains.get(m.domain).name(),
                                      "rowVersion",
                                      m.rowVersion))
                          .toList());
                  return v;
                })
            .toList();
    return paged(content, count("select count(s) from EduStudent s" + w, p), page);
  }

  @Transactional
  public Map<String, Object> decideStudentAccess(String name, Long id, StudentAccessDecision d) {
    var a = actor(name);
    collegeApprover(a);
    var s = find(EduStudent.class, id, true);
    college(a, s.college.id);
    var members =
        list(
            "select m from EduMembership m where m.student.id=:s and m.status='PENDING' order by"
                + " m.id",
            EduMembership.class,
            Map.of("s", id),
            0,
            7);
    if (members.isEmpty() || members.size() != d.expectedVersions.size())
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Student access changed. Refresh before deciding.");
    for (var m : members) {
      em.lock(m, LockModeType.PESSIMISTIC_WRITE);
      version(m.rowVersion, d.expectedVersions.get(m.domain));
    }
    for (var m : members) decide(name, m.id, new Decision(m.rowVersion, d.status, d.reason));
    audit.logAccountEvent(
        "COLLEGE_STUDENT_ACCESS_DECIDED",
        name,
        "student="
            + s.studentCode
            + "; status="
            + d.status
            + "; domains="
            + members.size()
            + "; reason="
            + d.reason);
    return Map.of(
        "studentCode", s.studentCode, "status", d.status, "updatedDomains", members.size());
  }
}
