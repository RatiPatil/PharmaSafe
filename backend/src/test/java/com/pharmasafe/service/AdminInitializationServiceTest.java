package com.pharmasafe.service;

import com.pharmasafe.dto.UserResponseDto;
import com.pharmasafe.entity.User;
import com.pharmasafe.repository.AuditEventRepository;
import com.pharmasafe.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class AdminInitializationServiceTest {

    @Autowired
    private AdminInitializationService adminInitializationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        auditEventRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Initial administrator creation succeeds with BCrypt hash and ROLE_ADMIN")
    void testInitializeFirstAdminSuccess() {
        assertFalse(adminInitializationService.hasAdminAccount());

        UserResponseDto createdAdmin = adminInitializationService.initializeFirstAdmin("sysadmin", "SecureAdminPass123!");

        assertNotNull(createdAdmin);
        assertEquals("sysadmin", createdAdmin.getUsername());
        assertEquals("ROLE_ADMIN", createdAdmin.getRole());
        assertTrue(createdAdmin.isEnabled());
        assertFalse(createdAdmin.isLocked());

        // Verify password in DB is hashed and not plaintext
        User userInDb = userRepository.findByUsername("sysadmin").orElseThrow();
        assertNotEquals("SecureAdminPass123!", userInDb.getPasswordHash());
        assertTrue(passwordEncoder.matches("SecureAdminPass123!", userInDb.getPasswordHash()));

        assertTrue(adminInitializationService.hasAdminAccount());
    }

    @Test
    @DisplayName("Repeated administrator initialization is rejected when an admin already exists")
    void testRepeatedInitializationRejected() {
        adminInitializationService.initializeFirstAdmin("sysadmin", "SecureAdminPass123!");
        assertTrue(adminInitializationService.hasAdminAccount());

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                adminInitializationService.initializeFirstAdmin("newadmin", "AnotherSecurePass456!")
        );

        assertTrue(ex.getMessage().contains("Repeated initialization is rejected"));
    }

    @Test
    @DisplayName("Short username or password is rejected with IllegalArgumentException")
    void testInvalidAdminCredentialsRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                adminInitializationService.initializeFirstAdmin("ab", "ValidPassword123!")
        );

        assertThrows(IllegalArgumentException.class, () ->
                adminInitializationService.initializeFirstAdmin("validuser", "short")
        );
    }

    @Test
    @DisplayName("Safe diagnostic reports correct match status without throwing or altering state")
    void testDiagnoseUserAuth() {
        adminInitializationService.initializeFirstAdmin("sysadmin", "SecureAdminPass123!");

        // Correct password matches
        assertTrue(adminInitializationService.diagnoseUserAuth("sysadmin", "SecureAdminPass123!"));

        // Incorrect password fails safely
        assertFalse(adminInitializationService.diagnoseUserAuth("sysadmin", "WrongPassword999!"));

        // Nonexistent user fails safely
        assertFalse(adminInitializationService.diagnoseUserAuth("unknownuser", "SecureAdminPass123!"));

        // Blank username/password fails safely
        assertFalse(adminInitializationService.diagnoseUserAuth("", "SecureAdminPass123!"));
        assertFalse(adminInitializationService.diagnoseUserAuth("sysadmin", null));
    }

    @Test
    @DisplayName("Administrator recovery resets password, clears locked state, and resets failed attempts")
    void testRecoverAdminAccountSuccess() {
        adminInitializationService.initializeFirstAdmin("sysadmin", "SecureAdminPass123!");

        // Simulate 3 failed attempts and account lockout
        User userInDb = userRepository.findByUsername("sysadmin").orElseThrow();
        userInDb.setFailedLoginAttempts(3);
        userInDb.setLocked(true);
        userInDb.setLockedAt(Instant.now());
        userRepository.save(userInDb);

        // Perform administrator recovery with new secure password (>= 12 chars)
        UserResponseDto recovered = adminInitializationService.recoverAdminAccount("sysadmin", "RecoveredAdminPassword2026!");

        assertNotNull(recovered);
        assertEquals("sysadmin", recovered.getUsername());
        assertEquals("ROLE_ADMIN", recovered.getRole());
        assertTrue(recovered.isEnabled());
        assertFalse(recovered.isLocked());

        // Verify updated entity in database
        User updatedUser = userRepository.findByUsername("sysadmin").orElseThrow();
        assertFalse(updatedUser.isLocked());
        assertNull(updatedUser.getLockedAt());
        assertEquals(0, updatedUser.getFailedLoginAttempts());
        assertTrue(updatedUser.isEnabled());

        // Verify new password matches and old password is invalidated
        assertTrue(passwordEncoder.matches("RecoveredAdminPassword2026!", updatedUser.getPasswordHash()));
        assertFalse(passwordEncoder.matches("SecureAdminPass123!", updatedUser.getPasswordHash()));

        // Verify audit event is recorded
        assertTrue(auditEventRepository.findAll().stream()
                .anyMatch(event -> "ADMIN_ACCOUNT_RECOVERED".equals(event.getEventType()) && "sysadmin".equals(event.getUsername())));
    }

    @Test
    @DisplayName("Administrator recovery rejects passwords shorter than 12 characters")
    void testRecoverAdminPasswordTooShortRejected() {
        adminInitializationService.initializeFirstAdmin("sysadmin", "SecureAdminPass123!");

        // 11 characters - must be rejected
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                adminInitializationService.recoverAdminAccount("sysadmin", "ShortPass12")
        );
        assertTrue(ex.getMessage().contains("at least 12 characters"));
    }

    @Test
    @DisplayName("Administrator recovery rejects non-existent administrator account")
    void testRecoverNonexistentAdminRejected() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                adminInitializationService.recoverAdminAccount("nonexistent", "RecoveredAdminPassword2026!")
        );
        assertTrue(ex.getMessage().contains("does not exist"));
    }

    @Test
    @DisplayName("Administrator recovery rejects non-admin accounts")
    void testRecoverNonAdminUserRejected() {
        // Create standard user with ROLE_USER
        User standardUser = new User("regularuser", passwordEncoder.encode("Password123!"), "ROLE_USER");
        userRepository.save(standardUser);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                adminInitializationService.recoverAdminAccount("regularuser", "RecoveredAdminPassword2026!")
        );
        assertTrue(ex.getMessage().contains("not an administrator"));
    }

    @Test
    @DisplayName("Administrator recovery rejects null or blank username and password")
    void testRecoverAdminBlankInputsRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                adminInitializationService.recoverAdminAccount("", "ValidPassword1234!"));
        assertThrows(IllegalArgumentException.class, () ->
                adminInitializationService.recoverAdminAccount("admin", null));
        assertThrows(IllegalArgumentException.class, () ->
                adminInitializationService.recoverAdminAccount("admin", "   "));
    }
}
