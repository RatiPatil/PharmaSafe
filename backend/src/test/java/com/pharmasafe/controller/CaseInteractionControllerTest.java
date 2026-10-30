package com.pharmasafe.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pharmasafe.entity.*;
import com.pharmasafe.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CaseInteractionControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired UserRepository userRepository;
    @Autowired SafetyCaseRepository caseRepository;
    @Autowired CaseContactLogRepository contactRepository;
    @Autowired CaseActionItemRepository actionRepository;
    @Autowired AuditEventRepository auditRepository;

    private User processor;
    private User otherUser;
    private User manager;

    @BeforeEach
    void setUp() {
        actionRepository.deleteAll();
        contactRepository.deleteAll();
        caseRepository.deleteAll();
        auditRepository.deleteAll();
        userRepository.deleteAll();
        processor = saveUser("processor", "ROLE_CASE_PROCESSOR");
        otherUser = saveUser("other", "ROLE_CASE_PROCESSOR");
        manager = saveUser("manager", "ROLE_SAFETY_MANAGER");
    }

    @Test
    @WithMockUser(username = "processor", roles = "CASE_PROCESSOR")
    void contactLogPersistsAndUnauthorizedUserCannotReadIt() throws Exception {
        SafetyCase safetyCase = saveCase("processor", "Data Entry");
        Map<String, Object> request = Map.of(
                "contactAt", Instant.now().toString(),
                "contactType", "Email",
                "description", "Requested missing reporter follow-up");

        mockMvc.perform(post("/api/cases/" + safetyCase.getId() + "/contact-logs")
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description", is("Requested missing reporter follow-up")));

        org.junit.jupiter.api.Assertions.assertEquals(1, contactRepository.count());

        mockMvc.perform(get("/api/contact-logs?caseId=" + safetyCase.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));

        mockMvc.perform(get("/api/contact-logs?caseId=" + safetyCase.getId())
                        .with(user("other").roles("CASE_PROCESSOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "processor", roles = "CASE_PROCESSOR")
    void overdueActionItemsExcludeCompletedItemsAndPersistCompletion() throws Exception {
        SafetyCase safetyCase = saveCase("processor", "Data Entry");
        Map<String, Object> request = Map.of(
                "description", "Complete follow-up",
                "assignedUserId", processor.getId(),
                "dueAt", Instant.now().minusSeconds(3600).toString());

        String response = mockMvc.perform(post("/api/cases/" + safetyCase.getId() + "/action-items")
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.overdue", is(true)))
                .andReturn().getResponse().getContentAsString();
        Long itemId = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(get("/api/action-items?overdue=true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));

        mockMvc.perform(post("/api/action-items/" + itemId + "/complete").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andExpect(jsonPath("$.completedByUsername", is("processor")));

        mockMvc.perform(get("/api/action-items?overdue=true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));
        org.junit.jupiter.api.Assertions.assertEquals("COMPLETED", actionRepository.findById(itemId).orElseThrow().getStatus());
    }

    @Test
    @WithMockUser(username = "processor", roles = "CASE_PROCESSOR")
    void onlyManagerMayAssignCases() throws Exception {
        SafetyCase safetyCase = saveCase("processor", "Data Entry");
        mockMvc.perform(post("/api/cases/" + safetyCase.getId() + "/assign")
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("userId", otherUser.getId()))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/cases/" + safetyCase.getId() + "/assign")
                        .with(user("manager").roles("SAFETY_MANAGER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("userId", otherUser.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedToUsername", is("other")));
    }

    @Test
    @WithMockUser(username = "medical", roles = "MEDICAL_REVIEWER")
    void medicalAndQualityReviewersRetainStateAuthorizedCaseAccess() throws Exception {
        saveUser("medical", "ROLE_MEDICAL_REVIEWER");
        saveUser("quality", "ROLE_QUALITY_REVIEWER");
        SafetyCase medicalCase = saveCase("processor", "Medical Review");
        mockMvc.perform(get("/api/cases/" + medicalCase.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workflowState", is("Medical Review")));

        SafetyCase qualityCase = saveCase("processor", "Quality Review");
        mockMvc.perform(get("/api/cases/" + qualityCase.getId())
                        .with(user("quality").roles("QUALITY_REVIEWER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workflowState", is("Quality Review")));
    }

    private User saveUser(String username, String role) {
        return userRepository.save(new User(username, passwordEncoder.encode("Password123!@#"), role));
    }

    private SafetyCase saveCase(String creator, String state) {
        SafetyCase safetyCase = new SafetyCase();
        safetyCase.setCaseNumber("PS-TEST-" + System.nanoTime());
        safetyCase.setCountryCode("USA");
        safetyCase.setReportType("Spontaneous");
        safetyCase.setInitialReceiptDate(LocalDate.now());
        safetyCase.setLatestReceivedDate(LocalDate.now());
        safetyCase.setWorkflowState(state);
        safetyCase.setCreatedByUser(userRepository.findByUsername(creator).orElse(processor));
        safetyCase.setAssignedToUser(processor);
        return caseRepository.saveAndFlush(safetyCase);
    }
}
