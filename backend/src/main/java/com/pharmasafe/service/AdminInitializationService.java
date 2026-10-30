package com.pharmasafe.service;

import com.pharmasafe.dto.UserResponseDto;
import com.pharmasafe.entity.User;
import com.pharmasafe.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class AdminInitializationService {

    private static final Logger log = LoggerFactory.getLogger(AdminInitializationService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public AdminInitializationService(UserRepository userRepository,
                                      PasswordEncoder passwordEncoder,
                                      AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    /**
     * Initializes the first administrator account.
     * Fails if an administrator account already exists to prevent unauthorized account takeover.
     */
    @Transactional
    public UserResponseDto initializeFirstAdmin(String rawUsername, String rawPassword) {
        if (userRepository.existsByRole("ROLE_ADMIN")) {
            throw new IllegalStateException("An administrator account already exists in PharmaSafe. Repeated initialization is rejected.");
        }

        if (rawUsername == null || rawUsername.trim().length() < 3) {
            throw new IllegalArgumentException("Administrator username must be at least 3 characters long.");
        }

        if (rawPassword == null || rawPassword.length() < 8) {
            throw new IllegalArgumentException("Administrator password must be at least 8 characters long.");
        }

        String normalizedUsername = rawUsername.trim().toLowerCase();

        if (userRepository.existsByUsername(normalizedUsername)) {
            throw new IllegalArgumentException("A user with username '" + normalizedUsername + "' already exists.");
        }

        String passwordHash = passwordEncoder.encode(rawPassword);
        User adminUser = new User(normalizedUsername, passwordHash, "ROLE_ADMIN");
        adminUser.setFullName("System Administrator");
        adminUser.setDepartment("Safety Operations");
        User savedAdmin = userRepository.save(adminUser);

        auditService.logEvent("ADMIN_INITIALIZED", normalizedUsername, "127.0.0.1", "CLI/SetupRunner", "SUCCESS",
                "Initial administrator account created");

        return UserResponseDto.fromEntity(savedAdmin);
    }

    @Transactional(readOnly = true)
    public boolean hasAdminAccount() {
        return userRepository.existsByRole("ROLE_ADMIN");
    }

    /**
     * Safe diagnostic routine to check credentials without exposing secrets.
     * Reports whether the user exists, enabled/locked status, and password verification.
     */
    @Transactional(readOnly = true)
    public boolean diagnoseUserAuth(String rawUsername, String rawPassword) {
        if (rawUsername == null || rawUsername.isBlank()) {
            return false;
        }
        String normalizedUsername = rawUsername.trim().toLowerCase();
        Optional<User> userOpt = userRepository.findByUsername(normalizedUsername);
        if (userOpt.isEmpty()) {
            return false;
        }
        User user = userOpt.get();
        return rawPassword != null && passwordEncoder.matches(rawPassword, user.getPasswordHash());
    }

    /**
     * Recovers and unlocks an administrator account with a new secure password.
     * Local-only procedure: updates the existing administrator, resets failed login attempts,
     * clears locked state, and records an audit event without exposing secrets.
     */
    @Transactional
    public UserResponseDto recoverAdminAccount(String rawUsername, String newRawPassword) {
        if (rawUsername == null || rawUsername.isBlank()) {
            throw new IllegalArgumentException("Username cannot be blank for administrator recovery.");
        }

        String normalizedUsername = rawUsername.trim().toLowerCase();

        if (newRawPassword == null || newRawPassword.length() < 12) {
            throw new IllegalArgumentException("New administrator password must be at least 12 characters long.");
        }

        User adminUser = userRepository.findByUsername(normalizedUsername)
                .orElseThrow(() -> new IllegalStateException("Administrator account '" + normalizedUsername + "' does not exist. Recovery rejected."));

        if (!"ROLE_ADMIN".equals(adminUser.getRole())) {
            throw new IllegalStateException("User '" + normalizedUsername + "' is not an administrator. Recovery rejected.");
        }

        String newPasswordHash = passwordEncoder.encode(newRawPassword);
        adminUser.setPasswordHash(newPasswordHash);
        adminUser.setFailedLoginAttempts(0);
        adminUser.setLocked(false);
        adminUser.setLockedAt(null);
        adminUser.setEnabled(true);

        User savedUser = userRepository.save(adminUser);

        // Explicit verification of BCrypt hash and unlocked state
        if (!passwordEncoder.matches(newRawPassword, savedUser.getPasswordHash())) {
            throw new IllegalStateException("Recovery verification failed: BCrypt hash does not match new password.");
        }
        if (savedUser.isLocked() || savedUser.getFailedLoginAttempts() != 0 || savedUser.getLockedAt() != null) {
            throw new IllegalStateException("Recovery verification failed: Account was not properly unlocked.");
        }

        auditService.logEvent("ADMIN_ACCOUNT_RECOVERED", normalizedUsername, "127.0.0.1", "CLI/LocalRecoveryScript", "SUCCESS",
                "Administrator account unlocked, failed login attempts reset to zero, and password updated");

        log.info("Administrator recovery completed successfully for user: '{}'. Account unlocked, failed attempts reset to 0.", normalizedUsername);

        return UserResponseDto.fromEntity(savedUser);
    }
}
