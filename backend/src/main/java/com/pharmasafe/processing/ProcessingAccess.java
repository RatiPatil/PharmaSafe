package com.pharmasafe.processing;

import com.pharmasafe.administration.*;
import com.pharmasafe.entity.User;
import com.pharmasafe.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
public class ProcessingAccess {
  public static final String LEAD = "ROLE_CASE_TEAM_LEAD", ROLE = "ROLE_CASE_PROCESSOR";
  private final UserRepository users;
  private final RoleAdminAssignmentRepository owners;
  private final ProcessingTeamRepository teams;
  private final ProcessingMemberRepository members;

  public ProcessingAccess(
      UserRepository u,
      RoleAdminAssignmentRepository o,
      ProcessingTeamRepository t,
      ProcessingMemberRepository m) {
    users = u;
    owners = o;
    teams = t;
    members = m;
  }

  public User actor(String username) {
    User u =
        users
            .findByUsername(username)
            .orElseThrow(() -> new AccessDeniedException("Account unavailable"));
    if (!u.isApproved() || !u.isEnabled() || u.isLocked())
      throw new AccessDeniedException("Account is not active");
    level(u);
    return u;
  }

  public String level(User u) {
    if (AdminScope.isSuper(u)) return "ADMIN";
    if (AdminScope.isRoleAdmin(u)
        && owners
            .findByAdministratorId(u.getId())
            .filter(a -> ROLE.equals(a.applicationRole))
            .isPresent()) return "ADMIN";
    if (LEAD.equals(u.getRole()) && teams.findByLeadId(u.getId()).filter(t -> t.active).isPresent())
      return "TEAM_LEAD";
    if (ROLE.equals(AdminScope.canonical(u.getRole()))) return "EMPLOYEE";
    throw new AccessDeniedException("Case Processing team access is required");
  }

  public void admin(User u) {
    if (!"ADMIN".equals(level(u)))
      throw new AccessDeniedException("Only the Case Processing Admin can manage teams");
  }

  public ProcessingTeam team(User u, Long id, boolean write) {
    ProcessingTeam t =
        teams.findById(id).orElseThrow(() -> new EntityNotFoundException("Team not found"));
    String level = level(u);
    boolean allowed =
        "ADMIN".equals(level)
            || "TEAM_LEAD".equals(level) && t.lead.getId().equals(u.getId())
            || !write
                && "EMPLOYEE".equals(level)
                && members.findByTeamIdAndUserId(id, u.getId()).isPresent();
    if (!allowed) throw new AccessDeniedException("This team is outside your scope");
    if (write && !t.active) throw new IllegalArgumentException("This team is inactive");
    return t;
  }

  public void assignment(User u, ProcessingAssignment a) {
    String level = level(u);
    if ("EMPLOYEE".equals(level)) {
      if (!a.employee.getId().equals(u.getId()))
        throw new AccessDeniedException("Only your own assignments are available");
    } else team(u, a.team.id, false);
  }
}
