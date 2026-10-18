package com.pharmasafe.controller;

import com.pharmasafe.entity.AuditEvent;
import com.pharmasafe.entity.CaseEvent;
import com.pharmasafe.entity.CaseProduct;
import com.pharmasafe.entity.SafetyCase;
import com.pharmasafe.entity.User;
import com.pharmasafe.repository.AuditEventRepository;
import com.pharmasafe.repository.SafetyCaseRepository;
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
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SafetyCaseRepository safetyCaseRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User officer1;
    private User officer2;
    private User adminUser;

    @BeforeEach
    public void setup() {
        safetyCaseRepository.deleteAll();
        auditEventRepository.deleteAll();

        officer1 = userRepository.findByUsername("officer1").orElseGet(() ->
                userRepository.save(new User("officer1", passwordEncoder.encode("Pass1234!"), "ROLE_USER")));
        officer2 = userRepository.findByUsername("officer2").orElseGet(() ->
                userRepository.save(new User("officer2", passwordEncoder.encode("Pass1234!"), "ROLE_USER")));
        adminUser = userRepository.findByUsername("admin").orElseGet(() ->
                userRepository.save(new User("admin", passwordEncoder.encode("Admin1234!"), "ROLE_ADMIN")));
    }

    @Test
    @DisplayName("GET /api/dashboard/summary unauthenticated returns 401")
    public void testSummaryUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/dashboard/summary"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "officer1", roles = {"USER"})
    @DisplayName("GET /api/dashboard/summary with empty database returns zero counts and UNCONFIGURED action items")
    public void testSummaryEmptyDatabase() throws Exception {
        mockMvc.perform(get("/api/dashboard/summary").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCases").value(0))
                .andExpect(jsonPath("$.assignedCases").value(0))
                .andExpect(jsonPath("$.awaitingReviewCases").value(0))
                .andExpect(jsonPath("$.userAwaitingReviewCases").value(0))
                .andExpect(jsonPath("$.seriousCases").value(0))
                .andExpect(jsonPath("$.expeditedCases").value(0))
                .andExpect(jsonPath("$.overdueActionItems").value(nullValue()))
                .andExpect(jsonPath("$.actionItemsStatus").value("UNCONFIGURED"))
                .andExpect(jsonPath("$.actionItemsNote").value(containsString("Stage 5")));
    }

    @Test
    @WithMockUser(username = "officer1", roles = {"USER"})
    @DisplayName("GET /api/dashboard/summary computes precise scoped counts correctly")
    public void testSummaryWithCases() throws Exception {
        // Case 1: Assigned to officer1, Data Entry, Routine
        createCase("PS-2026-USA-00001", officer1, "Data Entry", "Routine", false);

        // Case 2: Assigned to officer1, Quality Review, Expedited, Serious
        createCase("PS-2026-USA-00002", officer1, "Quality Review", "Expedited", true);

        // Case 3: Assigned to officer2, Medical Review, Routine, Serious
        createCase("PS-2026-USA-00003", officer2, "Medical Review", "Routine", true);

        // Case 4: Unassigned (null), Quality Review, Routine, not serious
        createCase("PS-2026-USA-00004", null, "Quality Review", "Routine", false);

        mockMvc.perform(get("/api/dashboard/summary").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCases").value(4))
                .andExpect(jsonPath("$.assignedCases").value(2))
                .andExpect(jsonPath("$.awaitingReviewCases").value(3)) // Case 2, 3, 4
                .andExpect(jsonPath("$.userAwaitingReviewCases").value(1)) // Case 2
                .andExpect(jsonPath("$.seriousCases").value(2)) // Case 2, 3
                .andExpect(jsonPath("$.expeditedCases").value(1)) // Case 2
                .andExpect(jsonPath("$.overdueActionItems").value(nullValue()))
                .andExpect(jsonPath("$.actionItemsStatus").value("UNCONFIGURED"));
    }

    @Test
    @DisplayName("GET /api/dashboard/activity unauthenticated returns 401")
    public void testActivityUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/dashboard/activity"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "officer1", roles = {"USER"})
    @DisplayName("GET /api/dashboard/activity for USER returns only user's own events without sensitive audit details")
    public void testActivityForUser() throws Exception {
        auditEventRepository.save(new AuditEvent("CASE_CREATED", "officer1", "192.168.1.10", "Mozilla/5.0", "SUCCESS", "Created case PS-2026-USA-00001"));
        auditEventRepository.save(new AuditEvent("CASE_CREATED", "officer2", "192.168.1.20", "Mozilla/5.0", "SUCCESS", "Created case PS-2026-USA-00002"));

        mockMvc.perform(get("/api/dashboard/activity").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].username").value("officer1"))
                .andExpect(jsonPath("$[0].eventType").value("CASE_CREATED"))
                .andExpect(jsonPath("$[0].details").value("Created case PS-2026-USA-00001"))
                .andExpect(jsonPath("$[0].ipAddress").doesNotExist())
                .andExpect(jsonPath("$[0].userAgent").doesNotExist());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("GET /api/dashboard/activity for ADMIN returns system-wide case events")
    public void testActivityForAdmin() throws Exception {
        auditEventRepository.save(new AuditEvent("CASE_CREATED", "officer1", "127.0.0.1", "Mozilla", "SUCCESS", "Created case PS-2026-USA-00001"));
        auditEventRepository.save(new AuditEvent("CASE_UPDATED", "officer2", "127.0.0.1", "Mozilla", "SUCCESS", "Updated case PS-2026-USA-00002"));
        auditEventRepository.save(new AuditEvent("LOGIN_SUCCESS", "officer1", "127.0.0.1", "Mozilla", "SUCCESS", "Login success")); // should be filtered out

        mockMvc.perform(get("/api/dashboard/activity").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].eventType", containsInAnyOrder("CASE_CREATED", "CASE_UPDATED")));
    }

    @Test
    @WithMockUser(username = "officer1", roles = {"USER"})
    @DisplayName("GET /api/dashboard/worklist returns paginated, filtered cases with primary product and event")
    public void testWorklistPaginationAndFiltering() throws Exception {
        createCase("PS-2026-USA-00010", officer1, "Data Entry", "Routine", false);
        createCase("PS-2026-USA-00020", officer1, "Quality Review", "Expedited", true);
        createCase("PS-2026-USA-00030", officer2, "Data Entry", "Routine", false);

        // 1. All cases paginated
        mockMvc.perform(get("/api/dashboard/worklist?page=0&size=2&sortBy=caseNumber&direction=asc")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[0].caseNumber").value("PS-2026-USA-00010"))
                .andExpect(jsonPath("$.content[0].primaryProduct").value("Aspirin 100mg"))
                .andExpect(jsonPath("$.content[0].primaryEvent").value("Severe Headache"));

        // 2. Assigned only filter
        mockMvc.perform(get("/api/dashboard/worklist?assignedOnly=true")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].assignedToUsername", everyItem(equalTo("officer1"))));

        // 3. Workflow state filter
        mockMvc.perform(get("/api/dashboard/worklist?workflowState=Quality Review")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].caseNumber").value("PS-2026-USA-00020"));
    }

    private SafetyCase createCase(String caseNumber, User assignedTo, String workflowState, String priority, boolean serious) {
        SafetyCase sc = new SafetyCase();
        sc.setCaseNumber(caseNumber);
        sc.setCountryCode("USA");
        sc.setReportType("Spontaneous");
        sc.setInitialReceiptDate(LocalDate.now());
        sc.setLatestReceivedDate(LocalDate.now());
        sc.setWorkflowState(workflowState);
        sc.setPriority(priority);
        sc.setAssignedToUser(assignedTo);
        sc.setCreatedByUser(officer1);
        sc.setUpdatedByUser(officer1);

        CaseProduct product = new CaseProduct();
        product.setProductName("Aspirin 100mg");
        product.setProductType("Suspect");
        product.setFormulation("Tablet");
        sc.getProducts().add(product);
        product.setSafetyCase(sc);

        CaseEvent event = new CaseEvent();
        event.setEventVerbatim("Severe Headache");
        event.setOnsetDate(LocalDate.now());
        event.setSerious(serious);
        event.setRequiresHospitalization(serious);
        sc.getEvents().add(event);
        event.setSafetyCase(sc);

        sc.syncSeriousness();
        return safetyCaseRepository.save(sc);
    }
}
