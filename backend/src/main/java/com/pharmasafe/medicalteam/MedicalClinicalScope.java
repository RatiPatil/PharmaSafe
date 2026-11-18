package com.pharmasafe.medicalteam;

import com.pharmasafe.entity.*;
import com.pharmasafe.repository.UserRepository;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
public class MedicalClinicalScope {
  private final MedicalMemberRepository members;
  private final MedicalCaseAllocationRepository allocations;
  private final UserRepository users;

  public MedicalClinicalScope(
      MedicalMemberRepository m, MedicalCaseAllocationRepository a, UserRepository u) {
    members = m;
    allocations = a;
    users = u;
  }

  public boolean isTeamReviewer(String username) {
    var u = users.findByUsername(username).orElse(null);
    return u != null
        && members.findByUserId(u.getId()).stream()
            .anyMatch(m -> !java.util.Set.of("REJECTED", "LEFT").contains(m.status));
  }

  public boolean canRead(SafetyCase c, String username) {
    var u = users.findByUsername(username).orElse(null);
    if (u == null) return false;
    var current =
        members.findByUserId(u.getId()).stream()
            .filter(m -> !java.util.Set.of("REJECTED", "LEFT").contains(m.status))
            .toList();
    var allocation = allocations.findById(c.getId()).orElse(null);
    if (allocation == null) return current.isEmpty();
    return allocation.team.active
        && c.getAssignedToUser() != null
        && c.getAssignedToUser().getId().equals(u.getId())
        && current.stream()
            .anyMatch(m -> m.team.id.equals(allocation.team.id) && "ACTIVE".equals(m.status));
  }

  public Specification<SafetyCase> scope(String username) {
    var u = users.findByUsername(username).orElse(null);
    if(u==null) return (r,q,c)->c.disjunction();
    var current =
        members.findByUserId(u.getId()).stream()
            .filter(m -> !java.util.Set.of("REJECTED", "LEFT").contains(m.status))
            .toList();
    var ids =
        current.stream()
            .filter(m -> "ACTIVE".equals(m.status) && m.team.active)
            .map(m -> m.team.id)
            .toList();
    return (r, q, c) -> {
      var sub = q.subquery(Long.class);
      var a = sub.from(MedicalCaseAllocation.class);
      sub.select(a.get("caseId")).where(c.equal(a.get("caseId"), r.get("id")));
      if (current.isEmpty()) return c.not(c.exists(sub));
      var own = q.subquery(Long.class);
      var t = own.from(MedicalCaseAllocation.class);
      own.select(t.get("caseId"))
          .where(c.equal(t.get("caseId"), r.get("id")), t.get("team").get("id").in(ids));
      return c.and(c.exists(own), c.equal(r.get("assignedToUser").get("id"), u.getId()));
    };
  }
}
