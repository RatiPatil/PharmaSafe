package com.pharmasafe.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pharmasafe.dto.SafetyReportDto;
import com.pharmasafe.entity.SafetyReport;
import com.pharmasafe.repository.SafetyReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class SafetyReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SafetyReportRepository safetyReportRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        if (safetyReportRepository.count() == 0) {
            SafetyReport r1 = new SafetyReport();
            r1.setReportId("RPT-TEST-001");
            r1.setReportType("7-day");
            r1.setProduct("Aspirin");
            r1.setAuthority("FDA");
            r1.setDueDate(LocalDate.now().plusDays(2));
            r1.setStatus("Pending");
            r1.setTotalCases(1);
            r1.setSeriousCases(1);
            safetyReportRepository.save(r1);

            SafetyReport r2 = new SafetyReport();
            r2.setReportId("RPT-TEST-002");
            r2.setReportType("15-day");
            r2.setProduct("Ibuprofen");
            r2.setAuthority("EMA");
            r2.setDueDate(LocalDate.now().minusDays(1)); // Overdue
            r2.setStatus("Pending");
            r2.setTotalCases(2);
            r2.setSeriousCases(2);
            safetyReportRepository.save(r2);
        }
    }

    @Test
    @DisplayName("Unauthenticated request to safety reports returns 401")
    void getReports_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/reports"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "officer", roles = {"SAFETY_MANAGER"})
    @DisplayName("Authenticated user can list safety reports")
    void searchReports_authenticated_returnsReports() throws Exception {
        mockMvc.perform(get("/api/reports")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(2))));
    }

    @Test
    @WithMockUser(username = "officer", roles = {"SAFETY_MANAGER"})
    @DisplayName("Expedited summary returns overdue and due soon counts")
    void getExpeditedSummary_returnsAccurateMetrics() throws Exception {
        mockMvc.perform(get("/api/reports/expedited/summary")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPendingReports", greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.overdueReports", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.dueSoonReports", greaterThanOrEqualTo(1)));
    }

    @Test
    @WithMockUser(username = "analyst", roles = {"CASE_PROCESSOR"})
    @DisplayName("Auto-classify determines 7-day expedited report for fatal unexpected events")
    void autoClassify_fatalUnexpected_returns7Day() throws Exception {
        Map<String, Object> payload = Map.of(
                "serious", true,
                "fatal", true,
                "lifeThreatening", false,
                "expectedness", "Unexpected"
        );

        mockMvc.perform(post("/api/reports/auto-classify")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classification", is("7-day")))
                .andExpect(jsonPath("$.deadlineDays", is(7)))
                .andExpect(jsonPath("$.requiresExpeditedReporting", is(true)));
    }

    @Test
    @WithMockUser(username = "analyst", roles = {"CASE_PROCESSOR"})
    @DisplayName("Auto-classify determines 15-day expedited report for serious unexpected events")
    void autoClassify_seriousUnexpected_returns15Day() throws Exception {
        Map<String, Object> payload = Map.of(
                "serious", true,
                "fatal", false,
                "lifeThreatening", false,
                "expectedness", "Unexpected"
        );

        mockMvc.perform(post("/api/reports/auto-classify")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classification", is("15-day")))
                .andExpect(jsonPath("$.deadlineDays", is(15)))
                .andExpect(jsonPath("$.requiresExpeditedReporting", is(true)));
    }

    @Test
    @WithMockUser(username = "analyst", roles = {"CASE_PROCESSOR"})
    @DisplayName("Non-manager cannot submit safety report (requires ADMIN or SAFETY_MANAGER)")
    void submitReport_caseProcessor_forbidden() throws Exception {
        SafetyReport report = safetyReportRepository.findAll().get(0);

        mockMvc.perform(post("/api/reports/" + report.getId() + "/submit")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "manager", roles = {"SAFETY_MANAGER"})
    @DisplayName("Safety manager can submit safety report")
    void submitReport_safetyManager_succeeds() throws Exception {
        SafetyReport report = safetyReportRepository.findAll().get(0);

        mockMvc.perform(post("/api/reports/" + report.getId() + "/submit")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("Submitted")));
    }
}
