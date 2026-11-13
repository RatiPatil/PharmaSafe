package com.pharmasafe.administration;

import com.pharmasafe.entity.User;
import com.pharmasafe.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class AdministrativeAudit {
  private final AdministrativeEventRepository events;
  private final UserRepository users;
  private final RoleAdminAssignmentRepository assignments;

  public AdministrativeAudit(
      AdministrativeEventRepository events,
      UserRepository users,
      RoleAdminAssignmentRepository assignments) {
    this.events = events;
    this.users = users;
    this.assignments = assignments;
  }

  @Transactional
  public void record(
      User actor,
      String role,
      User target,
      Long request,
      String action,
      String previous,
      String next,
      String reason,
      Long previousAdmin) {
    AdministrativeEvent e = new AdministrativeEvent();
    e.actorId = actor.getId();
    e.actorUsername = actor.getUsername();
    e.actorLevel = AdminScope.isSuper(actor) ? "SUPER_ADMIN" : "ROLE_ADMIN";
    e.managedRole = AdminScope.canonical(role);
    e.targetUserId = target == null ? null : target.getId();
    e.targetUsername = target == null ? null : target.getUsername();
    e.requestId = request;
    e.action = action;
    e.previousStatus = previous;
    e.newStatus = next;
    e.reason = reason;
    e.outcome = "SUCCESS";
    e.previousAdministratorId = previousAdmin;
    events.save(e);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void denied(String username, String role, String path) {
    var u = users.findByUsername(username);
    AdministrativeEvent e = new AdministrativeEvent();
    e.actorId = u.map(User::getId).orElse(null);
    e.actorUsername = username.length() > 64 ? username.substring(0, 64) : username;
    e.actorLevel =
        u.map(
                v ->
                    AdminScope.isSuper(v)
                        ? "SUPER_ADMIN"
                        : AdminScope.isRoleAdmin(v) ? "ROLE_ADMIN" : "USER")
            .orElse("USER");
    e.managedRole =
        role != null
            ? role
            : u.flatMap(v -> assignments.findByAdministratorId(v.getId()))
                .map(a -> a.applicationRole)
                .orElseGet(
                    () ->
                        u.map(
                                v ->
                                    com.pharmasafe.service.AccessRoleCatalog.FIELDS.containsKey(
                                            AdminScope.canonical(v.getRole()))
                                        ? AdminScope.canonical(v.getRole())
                                        : null)
                            .orElse(null));
    e.action = "ACCESS_DENIED";
    e.reason =
        path == null
            ? "Attempt to access an unauthorized administrative scope"
            : "Administrative access denied: " + path.substring(0, Math.min(path.length(), 1800));
    e.outcome = "DENIED";
    events.saveAndFlush(e);
  }
}
