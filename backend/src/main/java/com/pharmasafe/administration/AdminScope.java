package com.pharmasafe.administration;

import com.pharmasafe.entity.User;
import com.pharmasafe.repository.UserRepository;
import com.pharmasafe.service.AccessRoleCatalog;
import java.util.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
public class AdminScope {
  public static final String SCOPED_ADMIN = "ROLE_SCOPED_ADMIN";
  @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager em;
  @org.springframework.beans.factory.annotation.Autowired private com.pharmasafe.processing.ProcessingTeamRepository processingTeams;
  @org.springframework.beans.factory.annotation.Autowired private com.pharmasafe.processing.ProcessingMemberRepository processingMembers;
  @org.springframework.beans.factory.annotation.Autowired private com.pharmasafe.medicalteam.MedicalTeamRepository medicalTeams;
  @org.springframework.beans.factory.annotation.Autowired private com.pharmasafe.medicalteam.MedicalMemberRepository medicalMembers;
  private final UserRepository users;
  private final RoleAdminAssignmentRepository assignments;

  public AdminScope(UserRepository users, RoleAdminAssignmentRepository assignments) {
    this.users = users;
    this.assignments = assignments;
  }

  public static boolean isSuper(User u) {
    return "ROLE_ADMIN".equals(u.getRole());
  }

  public static boolean isRoleAdmin(User u) {
    return SCOPED_ADMIN.equals(u.getRole());
  }

  public static String canonical(String role) {
    return "ROLE_USER".equals(role) ? "ROLE_CASE_PROCESSOR" : role;
  }

  public static List<org.springframework.security.core.authority.SimpleGrantedAuthority>
      authorities(String role) {
    return "ROLE_ADMIN".equals(role)
        ? List.of(
            new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"),
            new org.springframework.security.core.authority.SimpleGrantedAuthority(
                "ROLE_SUPER_ADMIN"))
        : List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority(role));
  }

  public static boolean loginRole(String role) {
    return "ROLE_MEDICAL_TEAM_LEAD".equals(role) || "ROLE_CASE_TEAM_LEAD".equals(role) || com.pharmasafe.security.WorkspaceAccess.ROLES.contains(role)
        || SCOPED_ADMIN.equals(role);
  }

  public User actor(String name) {
    User u =
        users
            .findByUsername(name)
            .orElseThrow(() -> new AccessDeniedException("Administrator not available"));
    if (!u.isEnabled() || !u.isApproved() || u.isLocked()) deny(name, null);
    return u;
  }

  public String assigned(User actor) {
    return assignments
        .findByAdministratorId(actor.getId())
        .map(a -> a.applicationRole)
        .orElse(null);
  }

  public String role(String actor, String requested) {
    User a = actor(actor);
    if (isSuper(a)) {
      if (requested == null || requested.isBlank()) return "";
      if (!AccessRoleCatalog.FIELDS.containsKey(requested))
        throw new IllegalArgumentException("Select a supported application role");
      return requested;
    }
    String own = assigned(a);
    if (!isRoleAdmin(a)
        || own == null
        || requested != null && !requested.isBlank() && !own.equals(requested)) deny(actor, own);
    return own;
  }

  private void leadTarget(User actor,User target){
    var team=processingTeams.findByLeadId(actor.getId()).filter(t->t.active).orElseThrow(()->new AccessDeniedException("No active Case Processing team"));
    if(!"ROLE_CASE_PROCESSOR".equals(canonical(target.getRole()))||processingMembers.findByTeamIdAndUserId(team.id,target.getId()).isEmpty())deny(actor.getUsername(),"ROLE_CASE_PROCESSOR");
  }
  private void medicalLeadTarget(User actor,User target){var t=medicalTeams.findByLeadId(actor.getId()).filter(v->v.active).orElseThrow(()->new AccessDeniedException("No active Medical Review team"));if(!"ROLE_MEDICAL_REVIEWER".equals(target.getRole())||medicalMembers.findByTeamIdAndUserId(t.id,target.getId()).isEmpty())deny(actor.getUsername(),"ROLE_MEDICAL_REVIEWER");}
  public void target(String actor, User target) {
    User identity=actor(actor);if("ROLE_MEDICAL_TEAM_LEAD".equals(identity.getRole())){medicalLeadTarget(identity,target);return;}if("ROLE_CASE_TEAM_LEAD".equals(identity.getRole())){leadTarget(identity,target);return;}
    String scope = role(actor, "");
    if (!scope.isEmpty()
        && (!scope.equals(canonical(target.getRole())) || isRoleAdmin(target) || isSuper(target)))
      deny(actor, scope);
  }

  @org.springframework.transaction.annotation.Transactional(
      propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
  public void mutationTarget(String name, User target) {
    User a = actor(name);
    if("ROLE_MEDICAL_TEAM_LEAD".equals(a.getRole())){var team=medicalTeams.findByLeadId(a.getId()).orElseThrow();medicalTeams.lockById(team.id).orElseThrow();em.refresh(a);if(!a.isEnabled()||!a.isApproved()||a.isLocked())deny(name,null);medicalLeadTarget(a,target);return;}
    if("ROLE_CASE_TEAM_LEAD".equals(a.getRole())){
      var team=processingTeams.findByLeadId(a.getId()).orElseThrow(()->new AccessDeniedException("No active team"));processingTeams.lockById(team.id).orElseThrow();em.refresh(a);if(!a.isEnabled()||!a.isApproved()||a.isLocked())deny(name,null);leadTarget(a,target);return;
    }
    if (isSuper(a)) {
      target(name, target);
      return;
    }
    var candidate =
        assignments
            .findByAdministratorId(a.getId())
            .orElseThrow(() -> new AccessDeniedException("No active administrative assignment"));
    var slot =
        assignments
            .lockRole(candidate.applicationRole)
            .orElseThrow(() -> new AccessDeniedException("No active administrative assignment"));
    em.refresh(slot, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
    em.refresh(a);
    if (slot.administrator == null || !slot.administrator.getId().equals(a.getId()))
      deny(name, null);
    target(name, target);
  }

  public User superAdmin(String actor) {
    User a = actor(actor);
    if (!isSuper(a)) deny(actor, assigned(a));
    return a;
  }

  private void deny(String actor, String role) {
    throw new AccessDeniedException("This administrative action is outside your assigned role");
  }
}
