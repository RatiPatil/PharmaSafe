package com.pharmasafe.security;

import com.pharmasafe.entity.SafetyCase;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import java.util.Set;

/** Shared policy for case read scope and coarse operation permissions. */
@Component("workspaceAccess")
public class WorkspaceAccess {
    public static final Set<String> ROLES = Set.of("ROLE_ADMIN", "ROLE_CASE_PROCESSOR",
            "ROLE_MEDICAL_REVIEWER", "ROLE_QUALITY_REVIEWER", "ROLE_SAFETY_MANAGER",
            "ROLE_REPORTING_SPECIALIST", "ROLE_AUDITOR", "ROLE_USER");
    public boolean has(Authentication auth, String... roles) {
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> Set.of(roles).contains(a.getAuthority()));
    }
    public boolean canRead(SafetyCase c, String username, String role) {
        if (!ROLES.contains(role)) return false;
        if (Set.of("ROLE_ADMIN", "ROLE_SAFETY_MANAGER", "ROLE_AUDITOR").contains(role)) return true;
        if ("ROLE_REPORTING_SPECIALIST".equals(role))
            return Set.of("Approved", "Locked", "Closed").contains(c.getWorkflowState());
        if ("ROLE_MEDICAL_REVIEWER".equals(role))
            return "Medical Review".equals(c.getWorkflowState());
        if ("ROLE_QUALITY_REVIEWER".equals(role))
            return "Quality Review".equals(c.getWorkflowState());
        return (c.getAssignedToUser() != null && c.getAssignedToUser().getUsername().equalsIgnoreCase(username))
                || (c.getCreatedByUser() != null && c.getCreatedByUser().getUsername().equalsIgnoreCase(username));
    }
    public static String normalizeRole(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Role is required");
        String role = value.trim().toUpperCase(java.util.Locale.ROOT);
        if (!role.startsWith("ROLE_")) role = "ROLE_" + role;
        if (!ROLES.contains(role)) throw new IllegalArgumentException("Unsupported role");
        return role;
    }
}
