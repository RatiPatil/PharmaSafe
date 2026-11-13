package com.pharmasafe.service;

import com.pharmasafe.dto.LoginRequestDto;
import com.pharmasafe.dto.UserResponseDto;
import com.pharmasafe.entity.User;
import com.pharmasafe.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.Optional;

@Service
public class AuthenticationService {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationService.class);
    private static final int MAX_FAILED_ATTEMPTS = 3;

    /** Lock accounts after three consecutive failed attempts; recovery is administrator-approved. */
    public static final boolean ACCOUNT_LOCKOUT_ENABLED = true;

    // Pre-computed BCrypt hash used for timing attack defense when username is not found
    private static final String DUMMY_HASH = "$2a$10$7EqJtq98hPqEX7fNZaFWoO.6VupF1Hk/uYt1l585e1p9/5k6X.fS2";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final SecurityContextRepository securityContextRepository;

    public AuthenticationService(UserRepository userRepository,
                                 PasswordEncoder passwordEncoder,
                                 AuditService auditService,
                                 SecurityContextRepository securityContextRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.securityContextRepository = securityContextRepository;
    }

    /**
     * Authenticates user against PostgreSQL with row-level pessimistic locking
     * to eliminate concurrent race conditions on the 3-attempt lockout threshold.
     */
    @Transactional(noRollbackFor = {BadCredentialsException.class, LockedException.class})
    public UserResponseDto login(LoginRequestDto request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String clientIp = getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        if (request.getUsername() == null || request.getUsername().trim().isEmpty() ||
            request.getPassword() == null || request.getPassword().isEmpty()) {
            auditService.logEvent("LOGIN_FAILED", "anonymous", clientIp, userAgent, "FAILURE", "Missing username or password");
            throw new BadCredentialsException("Invalid username or password");
        }

        String normalizedUsername = request.getUsername().trim().toLowerCase();

        // Row-level lock (SELECT ... FOR UPDATE) to serialize concurrent login attempts
        String loginName = normalizedUsername;
        if (normalizedUsername.contains("@")) loginName = userRepository.findByEmailIgnoreCase(normalizedUsername)
                .map(User::getUsername).orElse(normalizedUsername);
        Optional<User> userOpt = userRepository.findByUsernameForUpdate(loginName);
        normalizedUsername = loginName.length() > 64 ? "unknown-account" : loginName;

        if (userOpt.isEmpty()) {
            log.warn("Auth diagnostic: user '{}' exists=false", normalizedUsername);
            // Constant-time execution to prevent timing attacks
            passwordEncoder.matches(request.getPassword(), DUMMY_HASH);
            auditService.logEvent("LOGIN_FAILED", normalizedUsername, clientIp, userAgent, "FAILURE", "Unknown user or bad credentials");
            throw new BadCredentialsException("Invalid username or password");
        }

        User user = userOpt.get();
        log.info("Auth diagnostic: user '{}' exists=true, enabled={}, locked={}, failedAttempts={}",
                normalizedUsername, user.isEnabled(), user.isLocked(), user.getFailedLoginAttempts());

        /*
         * Account Lockout Verification:
         * When ACCOUNT_LOCKOUT_ENABLED is true, locked accounts are rejected immediately.
         * In development mode (ACCOUNT_LOCKOUT_ENABLED = false), this check is bypassed so
         * that accounts marked as locked can authenticate if valid credentials are provided.
         */
        if (ACCOUNT_LOCKOUT_ENABLED && user.isLocked()) {
            auditService.logEvent("LOGIN_FAILED", normalizedUsername, clientIp, userAgent, "FAILURE", "Account is locked");
            throw new LockedException("Account is locked due to consecutive failed login attempts. Please contact an administrator.");
        }

        if (!user.isEnabled() || !user.isApproved() || !com.pharmasafe.administration.AdminScope.loginRole(user.getRole()) || (com.pharmasafe.administration.AdminScope.SCOPED_ADMIN.equals(user.getRole()) && adminAssignments.findByAdministratorId(user.getId()).isEmpty())) {
            auditService.logEvent("LOGIN_FAILED", normalizedUsername, clientIp, userAgent, "FAILURE", "Account is disabled");
            throw new DisabledException("Account is inactive. Please contact an administrator.");
        }

        boolean passwordMatches = passwordEncoder.matches(request.getPassword(), user.getPasswordHash());
        log.info("Auth diagnostic: user '{}' password verification={}",
                normalizedUsername, passwordMatches ? "SUCCESS" : "FAILED");

        if (!passwordMatches) {
            int newAttempts = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(newAttempts);

            if (ACCOUNT_LOCKOUT_ENABLED && newAttempts >= MAX_FAILED_ATTEMPTS) {
                user.setLocked(true);
                user.setLockedAt(Instant.now());
                userRepository.save(user);

                auditService.logEvent("ACCOUNT_LOCKED", normalizedUsername, clientIp, userAgent, "WARNING",
                        "Account locked after " + MAX_FAILED_ATTEMPTS + " consecutive failed attempts");
                throw new LockedException("Account has been locked due to 3 consecutive failed login attempts. Please contact an administrator.");
            } else {
                userRepository.save(user);
                auditService.logEvent("LOGIN_FAILED", normalizedUsername, clientIp, userAgent, "FAILURE",
                        "Invalid credentials, attempt " + newAttempts + " of " + MAX_FAILED_ATTEMPTS);
                throw new BadCredentialsException("Invalid username or password");
            }
        }

        // A role portal restricts sign-in; it never grants the requested role.
        if(request.getApplicationRole()!=null||request.getLoginMode()!=null){
            String role=request.getApplicationRole(),mode=request.getLoginMode();
            if(!AccessRoleCatalog.FIELDS.containsKey(role==null?"":role)||!java.util.Set.of("USER","ROLE_ADMIN").contains(mode==null?"":mode))throw new IllegalArgumentException("Choose a supported role and sign-in tab");
            boolean allowed="USER".equals(mode)?role.equals(com.pharmasafe.administration.AdminScope.canonical(user.getRole())):com.pharmasafe.administration.AdminScope.SCOPED_ADMIN.equals(user.getRole())&&adminAssignments.findByAdministratorId(user.getId()).map(a->role.equals(a.applicationRole)).orElse(false);
            if(!allowed){auditService.logEvent("LOGIN_PORTAL_DENIED",normalizedUsername,clientIp,userAgent,"DENIED","Account does not match the selected role portal");throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN,"This account is not assigned to the selected role and login tab. Choose the matching role or use general sign in.");}
        }

        // On successful authentication: reset failed attempt counter, auto-clear any residual locked status, and update last login timestamp
        user.setFailedLoginAttempts(0);
        if (user.isLocked()) {
            user.setLocked(false);
            user.setLockedAt(null);
            log.info("Auto-cleared residual locked flag on successful login for user '{}'", normalizedUsername);
        }
        user.setLastSuccessfulLoginAt(Instant.now());
        userRepository.save(user);

        auditService.logEvent("LOGIN_SUCCESS", normalizedUsername, clientIp, userAgent, "SUCCESS", "User logged in successfully");

        // Establish Spring Security Context and attach to current HTTP Session
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                user.getUsername(),
                null,
                com.pharmasafe.administration.AdminScope.authorities(user.getRole())
        );

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        httpRequest.getSession(true);
        httpRequest.changeSessionId();
        httpRequest.getSession(true).setAttribute("PHARMASAFE_AUTH_VERSION", user.getAuthVersion());
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        return response(user);
    }

    public void logout(HttpServletRequest request, HttpServletResponse response) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal()))
                ? auth.getName() : "anonymous";
        String clientIp = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");

        auditService.logEvent("LOGOUT", username, clientIp, userAgent, "SUCCESS", "User logged out");

        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    @org.springframework.beans.factory.annotation.Autowired private com.pharmasafe.administration.RoleAdminAssignmentRepository adminAssignments;
    private UserResponseDto response(User user){var dto=UserResponseDto.fromEntity(user);if(adminAssignments!=null&&com.pharmasafe.administration.AdminScope.SCOPED_ADMIN.equals(user.getRole()))adminAssignments.findByAdministratorId(user.getId()).ifPresent(a->dto.setManagedRole(a.applicationRole));return dto;}
    @Transactional(readOnly = true)
    public UserResponseDto getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }

        String username = auth.getName().trim().toLowerCase();
        return userRepository.findByUsername(username)
                .map(this::response)
                .orElse(null);
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
