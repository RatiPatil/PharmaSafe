package com.pharmasafe.administration;

import com.pharmasafe.entity.*;
import com.pharmasafe.repository.UserRepository;
import jakarta.persistence.*;
import jakarta.persistence.criteria.*;
import java.util.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only aggregates use the exact supervisory ownership policy, not client role claims. */
@Service
@Transactional(readOnly = true)
public class AdministrationAnalytics {
  private final AdminScope scope;
  private final UserRepository users;
  private final AdministrationService administration;
  @PersistenceContext private EntityManager em;

  public AdministrationAnalytics(
      AdminScope scope, UserRepository users, AdministrationService administration) {
    this.scope = scope;
    this.users = users;
    this.administration = administration;
  }

  private List<String> members(String role) {
    return role.equals("ROLE_CASE_PROCESSOR") ? List.of(role, "ROLE_USER") : List.of(role);
  }

  private <T> Map<String, Long> distribution(
      Class<T> type, String attribute, Specification<T> filter) {
    var cb = em.getCriteriaBuilder();
    var query = cb.createTupleQuery();
    var root = query.from(type);
    query
        .multiselect(root.get(attribute), cb.countDistinct(root))
        .where(filter.toPredicate(root, query, cb))
        .groupBy(root.get(attribute))
        .orderBy(cb.desc(cb.countDistinct(root)), cb.asc(root.get(attribute)));
    Map<String, Long> values = new LinkedHashMap<>();
    for (var row : em.createQuery(query).getResultList())
      values.put(
          row.get(0) == null ? "Not recorded" : row.get(0).toString(),
          ((Number) row.get(1)).longValue());
    return values;
  }

  public Map<String, Object> overview(String actor, String requested, Long userId) {
    String role = scope.role(actor, requested);
    if (userId != null) {
      var target =
          users
              .findById(userId)
              .orElseThrow(() -> new EntityNotFoundException("Employee not found"));
      scope.target(actor, target);
      if (!role.isBlank() && !role.equals(AdminScope.canonical(target.getRole())))
        throw new org.springframework.security.access.AccessDeniedException(
            "Employee outside the selected role");
    }
    Specification<SafetyCase> caseScope =
        role.isBlank() && userId == null
            ? (r, q, c) -> c.conjunction()
            : administration.workScope(role, userId);
    Specification<SafetyReport> reportScope =
        (r, q, c) ->
            userId != null
                ? c.equal(r.get("createdByUser").get("id"), userId)
                : role.isBlank()
                    ? c.conjunction()
                    : r.get("createdByUser").get("role").in(members(role));
    Specification<AuditEvent> activityScope =
        (r, q, c) -> {
          if (role.isBlank() && userId == null) return c.conjunction();
          var sub = q.subquery(String.class);
          var u = sub.from(User.class);
          sub.select(u.get("username"))
              .where(
                  userId == null ? u.get("role").in(members(role)) : c.equal(u.get("id"), userId));
          return r.get("username").in(sub);
        };
    Map<String, Object> result = new LinkedHashMap<>();
    var states = distribution(SafetyCase.class, "workflowState", caseScope);
    var reports = distribution(SafetyReport.class, "status", reportScope);
    var activity = distribution(AuditEvent.class, "eventType", activityScope);
    result.put("role", role);
    result.put("userId", userId);
    result.put("caseStates", states);
    result.put("casePriorities", distribution(SafetyCase.class, "priority", caseScope));
    result.put("reportStates", reports);
    result.put("activity", activity);
    result.put("totalCases", states.values().stream().mapToLong(Long::longValue).sum());
    result.put("totalReports", reports.values().stream().mapToLong(Long::longValue).sum());
    result.put("totalActivity", activity.values().stream().mapToLong(Long::longValue).sum());
    return result;
  }
}
