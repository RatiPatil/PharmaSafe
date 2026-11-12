package com.pharmasafe.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pharmasafe.dto.LoginRequestDto;
import com.pharmasafe.entity.User;
import com.pharmasafe.repository.AuditEventRepository;
import com.pharmasafe.repository.UserRepository;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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

        // Seed a standard user
        User standardUser = new User("testuser", passwordEncoder.encode("Password123!"), "ROLE_USER");
        userRepository.save(standardUser);

        // Seed an admin user
        User adminUser = new User("adminuser", passwordEncoder.encode("AdminPass123!"), "ROLE_ADMIN");
        userRepository.save(adminUser);

        // Seed an inactive user
        User inactiveUser = new User("inactiveuser", passwordEncoder.encode("InactivePass123!"), "ROLE_USER");
        inactiveUser.setEnabled(false);
        userRepository.save(inactiveUser);
    }

    @Test
    @DisplayName("Successful login returns 200 OK, session cookie, user details without password hash")
    void testSuccessfulLogin() throws Exception {
        LoginRequestDto loginRequest = new LoginRequestDto("testuser", "Password123!");

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Authentication successful"))
                .andExpect(jsonPath("$.user.username").value("testuser"))
                .andExpect(jsonPath("$.user.role").value("ROLE_USER"))
                .andExpect(jsonPath("$.user.enabled").value(true))
                .andExpect(jsonPath("$.user.locked").value(false))
                .andExpect(jsonPath("$.user.password").doesNotExist())
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andReturn();

        // Verify session was created
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertNotNull(session, "Session should be established on successful login");

        // Verify audit log recorded
        assertTrue(auditEventRepository.count() > 0);
    }

    @Test
    @DisplayName("Invalid credentials return 401 Unauthorized with safe error message")
    void testInvalidCredentials() throws Exception {
        LoginRequestDto loginRequest = new LoginRequestDto("testuser", "WrongPassword!");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid username or password"));

        User user = userRepository.findByUsername("testuser").orElseThrow();
        assertEquals(1, user.getFailedLoginAttempts());
        assertFalse(user.isLocked());
    }

    @Test
    @DisplayName("Three failed logins lock an account and correct credentials cannot bypass the lock")
    void testThreeFailuresLockAccount() throws Exception {
        for(int i=1;i<=3;i++) mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequestDto("testuser","WrongPassword!"))))
                .andExpect(status().is(i<3?401:423));
        User user=userRepository.findByUsername("testuser").orElseThrow();
        assertTrue(user.isLocked());assertEquals(3,user.getFailedLoginAttempts());assertNotNull(user.getLockedAt());
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequestDto("testuser","Password123!"))))
                .andExpect(status().isLocked());
    }

    @Test
    @DisplayName("Existing locked accounts remain locked with either valid or invalid credentials")
    void testExistingLockedAccountCannotAuthenticate() throws Exception {
        User user=userRepository.findByUsername("testuser").orElseThrow();
        user.setLocked(true);user.setLockedAt(Instant.now());userRepository.saveAndFlush(user);
        for(String password:java.util.List.of("Password123!","WrongPassword!"))
            mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new LoginRequestDto("testuser",password))))
                    .andExpect(status().isLocked());
        assertTrue(userRepository.findByUsername("testuser").orElseThrow().isLocked());
    }

    @Test
    @DisplayName("Failed login counter resets to 0 after a successful login")
    void testFailedAttemptCounterResetAfterSuccess() throws Exception {
        LoginRequestDto badRequest = new LoginRequestDto("testuser", "WrongPassword!");

        // 2 failed attempts
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(badRequest)));
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(badRequest)));

        User user = userRepository.findByUsername("testuser").orElseThrow();
        assertEquals(2, user.getFailedLoginAttempts());

        // 3rd attempt is successful
        LoginRequestDto goodRequest = new LoginRequestDto("testuser", "Password123!");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(goodRequest)))
                .andExpect(status().isOk());

        User resetUser = userRepository.findByUsername("testuser").orElseThrow();
        assertEquals(0, resetUser.getFailedLoginAttempts());
        assertNotNull(resetUser.getLastSuccessfulLoginAt());
    }

    @Test
    @DisplayName("Inactive account login is rejected with 403 Forbidden")
    void testInactiveAccountRejected() throws Exception {
        LoginRequestDto loginRequest = new LoginRequestDto("inactiveuser", "InactivePass123!");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("Account is inactive")));
    }

    @Test
    @DisplayName("GET /api/auth/me returns current authenticated user details")
    void testGetCurrentUserEndpoint() throws Exception {
        // First login to acquire session
        LoginRequestDto loginRequest = new LoginRequestDto("testuser", "Password123!");
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertNotNull(session);

        // Call /api/auth/me using the active session
        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("testuser"))
                .andExpect(jsonPath("$.role").value("ROLE_USER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("POST /api/auth/logout invalidates session and subsequent /api/auth/me returns 401")
    void testLogoutAndSessionInvalidation() throws Exception {
        // Login
        LoginRequestDto loginRequest = new LoginRequestDto("testuser", "Password123!");
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertNotNull(session);

        // Logout
        mockMvc.perform(post("/api/auth/logout").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logout successful"));

        assertTrue(session.isInvalid(), "HTTP session should be invalidated after logout");

        // Calling /api/auth/me without valid session returns 401 Unauthorized
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Unauthenticated request to protected endpoint returns 401 Unauthorized")
    void testUnauthenticatedAccessDenied() throws Exception {
        mockMvc.perform(get("/api/admin/status"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "regularuser", roles = {"USER"})
    @DisplayName("Regular user accessing admin endpoint returns 403 Forbidden")
    void testRoleBasedAccessDeniedForNonAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/status"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "adminuser", roles = {"ADMIN"})
    @DisplayName("Administrator accessing admin endpoint returns 200 OK")
    void testRoleBasedAccessAllowedForAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.systemStatus").value("OPERATIONAL"));
    }

    @Test
    @DisplayName("CORS preflight from production frontend origin is allowed with credentials")
    void testCorsPreflightProductionFrontend() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options("/api/auth/login")
                        .header("Origin", "https://pharmasafe-frontend.onrender.com")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type,X-XSRF-TOKEN"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://pharmasafe-frontend.onrender.com"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }
}
