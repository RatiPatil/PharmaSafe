package com.pharmasafe.security;

import com.pharmasafe.entity.User;
import com.pharmasafe.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;

@Service
public class CustomUserDetailsService implements UserDetailsService {
    @org.springframework.beans.factory.annotation.Autowired(required=false) private com.pharmasafe.processing.ProcessingTeamRepository processingTeams;

    private final UserRepository userRepository;
    private final com.pharmasafe.administration.RoleAdminAssignmentRepository adminAssignments;

    public CustomUserDetailsService(UserRepository userRepository,com.pharmasafe.administration.RoleAdminAssignmentRepository adminAssignments) {
        this.userRepository = userRepository;
        this.adminAssignments=adminAssignments;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        if (username == null || username.trim().isEmpty()) {
            throw new UsernameNotFoundException("Username cannot be empty");
        }

        String normalizedUsername = username.trim().toLowerCase();
        User user = userRepository.findByUsername(normalizedUsername)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        /*
         * Account non-locked flag:
         * In development mode (ACCOUNT_LOCKOUT_ENABLED = false), accountNonLocked is set to true
         * so that users with residual locked flags can authenticate if credentials match.
         * Must be restored to `!user.isLocked()` before production.
         */
        boolean accountNonLocked = !com.pharmasafe.service.AuthenticationService.ACCOUNT_LOCKOUT_ENABLED || !user.isLocked();

        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPasswordHash(),
                user.isEnabled() && user.isApproved() && (!"ROLE_CASE_TEAM_LEAD".equals(user.getRole())||processingTeams==null||processingTeams.findByLeadId(user.getId()).filter(t->t.active).isPresent()) && com.pharmasafe.administration.AdminScope.loginRole(user.getRole()) && (!com.pharmasafe.administration.AdminScope.SCOPED_ADMIN.equals(user.getRole())||adminAssignments.findByAdministratorId(user.getId()).isPresent()),
                true, // accountNonExpired
                true, // credentialsNonExpired
                accountNonLocked,
                com.pharmasafe.administration.AdminScope.authorities(user.getRole())
        );
    }
}
