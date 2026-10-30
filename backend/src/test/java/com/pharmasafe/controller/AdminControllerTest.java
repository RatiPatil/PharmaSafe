package com.pharmasafe.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pharmasafe.entity.User;
import com.pharmasafe.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        if (!userRepository.existsByUsername("admin")) {
            User admin = new User("admin", passwordEncoder.encode("Password123!"), "ROLE_ADMIN");
            admin.setFullName("System Administrator");
            admin.setDepartment("Safety Operations");
            userRepository.save(admin);
        }
        if (!userRepository.existsByUsername("processor1")) {
            User user = new User("processor1", passwordEncoder.encode("Password123!"), "ROLE_CASE_PROCESSOR");
            user.setFullName("John Processor");
            user.setDepartment("Data Management");
            userRepository.save(user);
        }
    }

    @Test
    @DisplayName("Unauthenticated request to admin users returns 401")
    void getUsers_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "processor1", roles = {"CASE_PROCESSOR"})
    @DisplayName("Case Processor cannot access admin endpoints (403 Forbidden)")
    void getUsers_caseProcessor_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("Administrator can list all users with details")
    void getUsers_admin_returnsUserList() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$[0].username", notNullValue()));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("Administrator can create a new user account with role and department")
    void createUser_admin_createsAndReturnsUser() throws Exception {
        String newUsername = "reviewer_" + System.currentTimeMillis();
        Map<String, String> payload = Map.of(
                "username", newUsername,
                "password", "ReviewerPass123!",
                "role", "ROLE_MEDICAL_REVIEWER",
                "fullName", "Dr. Jane Reviewer",
                "department", "Medical Assessment"
        );

        mockMvc.perform(post("/api/admin/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username", is(newUsername)))
                .andExpect(jsonPath("$.role", is("ROLE_MEDICAL_REVIEWER")))
                .andExpect(jsonPath("$.fullName", is("Dr. Jane Reviewer")))
                .andExpect(jsonPath("$.department", is("Medical Assessment")))
                .andExpect(jsonPath("$.enabled", is(true)));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("Administrator can toggle user enabled/active status")
    void toggleUserStatus_admin_togglesStatus() throws Exception {
        User user = userRepository.findByUsername("processor1").orElseThrow();
        boolean initialStatus = user.isEnabled();

        mockMvc.perform(patch("/api/admin/users/" + user.getId() + "/toggle-status")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled", is(!initialStatus)));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("Administrator can retrieve available system roles")
    void getAvailableRoles_returnsConfiguredRoles() throws Exception {
        mockMvc.perform(get("/api/admin/roles")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(4))));
    }
}
