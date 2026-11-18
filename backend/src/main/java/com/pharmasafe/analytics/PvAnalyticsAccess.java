package com.pharmasafe.analytics;

import com.pharmasafe.administration.AdminScope;
import com.pharmasafe.entity.User;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
public class PvAnalyticsAccess {
  private final AdminScope scope;

  public PvAnalyticsAccess(AdminScope s) {
    scope = s;
  }

  public User actor(String name) {
    var u = scope.actor(name);
    if (!java.util.Set.of(
            "ROLE_PV_ANALYST", "ROLE_SAFETY_MANAGER", "ROLE_ADMIN", "ROLE_SCOPED_ADMIN")
        .contains(u.getRole())) throw new AccessDeniedException("PV analytics access required");
    if (AdminScope.isRoleAdmin(u)) scope.role(name, "ROLE_PV_ANALYST");
    return u;
  }

  public void author(User u) {
    if (!"ROLE_PV_ANALYST".equals(u.getRole()))
      throw new AccessDeniedException("Only an analyst can author this evidence");
  }

  public void read(User u, Long owner) {
    if ("ROLE_PV_ANALYST".equals(u.getRole()) && !u.getId().equals(owner))
      throw new AccessDeniedException("Evidence is outside your analyst scope");
  }

  public void review(User u) {
    if (!java.util.Set.of("ROLE_SAFETY_MANAGER", "ROLE_ADMIN").contains(u.getRole()))
      throw new AccessDeniedException(
          "Only a Safety Manager or global Super Admin can review an analysis");
  }
}
