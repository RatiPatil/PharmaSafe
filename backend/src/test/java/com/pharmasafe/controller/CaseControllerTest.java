package com.pharmasafe.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pharmasafe.dto.CaseRequestDto;
import com.pharmasafe.entity.SafetyCase;
import com.pharmasafe.entity.User;
import com.pharmasafe.repository.AuditEventRepository;
import com.pharmasafe.repository.SafetyCaseRepository;
import com.pharmasafe.repository.UserRepository;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class CaseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SafetyCaseRepository safetyCaseRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User testUser;

    @BeforeEach
    public void setup() {
        safetyCaseRepository.deleteAll();
        userRepository.deleteAll();
        auditEventRepository.deleteAll();

        testUser = new User("safetyofficer", passwordEncoder.encode("Password123!@#"), "ROLE_USER");
        userRepository.save(testUser);
    }

    private CaseRequestDto buildValidCaseRequest() {
        CaseRequestDto dto = new CaseRequestDto();
        dto.setCountryCode("USA");
        dto.setReportType("Spontaneous");
        dto.setInitialReceiptDate(LocalDate.now().minusDays(2));
        dto.setLatestReceivedDate(LocalDate.now());
        dto.setWorkflowState("Data Entry");
        dto.setPriority("Routine");
        dto.setNarrative("Patient experienced acute allergic reaction 30 minutes post administration.");
        dto.setCompanyComments("Initial spontaneous report under medical review.");

        // Patient
        CaseRequestDto.PatientDto patient = new CaseRequestDto.PatientDto();
        patient.setPatientIdentifier("PT-001");
        patient.setAgeValue(new BigDecimal("45.0"));
        patient.setAgeUnit("Years");
        patient.setGender("Female");
        patient.setWeightKg(new BigDecimal("68.5"));
        patient.setMedicalHistory("Mild seasonal allergies");
        dto.setPatient(patient);

        // Reporter
        CaseRequestDto.ReporterDto reporter = new CaseRequestDto.ReporterDto();
        reporter.setPrimary(true);
        reporter.setQualification("Physician");
        reporter.setFirstName("Sarah");
        reporter.setLastName("Jenkins");
        reporter.setInstitution("Metropolitan General Hospital");
        reporter.setCity("Boston");
        reporter.setStateProvince("MA");
        reporter.setCountryCode("USA");
        reporter.setEmail("sjenkins@metropolitan.org");
        dto.setReporters(List.of(reporter));

        // Product
        CaseRequestDto.ProductDto product = new CaseRequestDto.ProductDto();
        product.setProductType("Suspect");
        product.setProductName("CardioGuard 50mg");
        product.setActiveSubstance("Cardiolol Hydrochloride");
        product.setIndication("Hypertension");
        product.setDosageText("50mg Once daily");
        product.setFormulation("Tablet");
        product.setRouteOfAdministration("Oral");
        product.setStartDate(LocalDate.now().minusDays(10));
        product.setStopDate(LocalDate.now().minusDays(2));
        product.setDechallengeResult("Positive");
        dto.setProducts(List.of(product));

        // Event
        CaseRequestDto.EventDto event = new CaseRequestDto.EventDto();
        event.setEventVerbatim("Severe acute urticaria and facial angioedema");
        event.setMeddraPt("Angioedema");
        event.setMeddraSoc("Immune system disorders");
        event.setOnsetDate(LocalDate.now().minusDays(2));
        event.setOutcome("Recovering/Resolving");
        event.setSerious(true);
        event.setRequiresHospitalization(true);
        dto.setEvents(List.of(event));

        return dto;
    }

    @Test
    @WithMockUser(username = "safetyofficer", roles = {"USER"})
    public void testCreateCaseSuccess() throws Exception {
        CaseRequestDto request = buildValidCaseRequest();

        mockMvc.perform(post("/api/cases")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.caseNumber", startsWith("PS-")))
                .andExpect(jsonPath("$.countryCode", is("USA")))
                .andExpect(jsonPath("$.reportType", is("Spontaneous")))
                .andExpect(jsonPath("$.workflowState", is("Data Entry")))
                .andExpect(jsonPath("$.serious", is(true)))
                .andExpect(jsonPath("$.patient.patientIdentifier", is("PT-001")))
                .andExpect(jsonPath("$.reporters[0].lastName", is("Jenkins")))
                .andExpect(jsonPath("$.products[0].productName", is("CardioGuard 50mg")))
                .andExpect(jsonPath("$.events[0].meddraPt", is("Angioedema")));

        // Verify PostgreSQL persistence
        List<SafetyCase> cases = safetyCaseRepository.findAll();
        assertEquals(1, cases.size());
        SafetyCase saved = cases.get(0);
        assertTrue(saved.getCaseNumber().startsWith("PS-"));
        assertEquals("USA", saved.getCountryCode());
        assertTrue(saved.isSerious());
        assertNotNull(saved.getPatient());
        assertEquals(1, saved.getReporters().size());
        assertEquals(1, saved.getProducts().size());
        assertEquals(1, saved.getEvents().size());

        // Verify Audit Event was recorded
        assertTrue(auditEventRepository.count() > 0);
        boolean hasAudit = auditEventRepository.findAll().stream()
                .anyMatch(a -> "CASE_CREATED".equals(a.getEventType()) && a.getDetails().contains(saved.getCaseNumber()));
        assertTrue(hasAudit);
    }

    @Test
    @WithMockUser(username = "safetyofficer", roles = {"USER"})
    public void testCreateDraftCaseWithPartialData() throws Exception {
        CaseRequestDto draftRequest = new CaseRequestDto();
        draftRequest.setCountryCode("GBR");
        draftRequest.setReportType("Literature");
        draftRequest.setInitialReceiptDate(LocalDate.now());
        draftRequest.setLatestReceivedDate(LocalDate.now());
        draftRequest.setDraft(true); // Draft allows partial entries without mandatory products/events
        draftRequest.setNarrative("Draft case from ongoing scientific publication review.");

        mockMvc.perform(post("/api/cases")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(draftRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.caseNumber", startsWith("PS-")))
                .andExpect(jsonPath("$.workflowState", is("Draft")))
                .andExpect(jsonPath("$.countryCode", is("GBR")));

        List<SafetyCase> cases = safetyCaseRepository.findAll();
        assertEquals(1, cases.size());
        assertEquals("Draft", cases.get(0).getWorkflowState());
    }

    @Test
    @WithMockUser(username = "safetyofficer", roles = {"USER"})
    public void testCreateCaseValidationErrorOnInvertedDates() throws Exception {
        CaseRequestDto request = buildValidCaseRequest();
        // Invert receipt dates
        request.setInitialReceiptDate(LocalDate.now());
        request.setLatestReceivedDate(LocalDate.now().minusDays(5));

        mockMvc.perform(post("/api/cases")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Latest received date cannot be before initial receipt date")));
    }

    @Test
    @WithMockUser(username = "safetyofficer", roles = {"USER"})
    public void testCreateCaseMissingSuspectProductFailsNonDraft() throws Exception {
        CaseRequestDto request = buildValidCaseRequest();
        request.setProducts(List.of()); // Empty products for non-draft submission

        mockMvc.perform(post("/api/cases")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("At least one product is required")));
    }

    @Test
    public void testCreateCaseUnauthorized() throws Exception {
        CaseRequestDto request = buildValidCaseRequest();

        mockMvc.perform(post("/api/cases")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "safetyofficer", roles = {"USER"})
    public void testUpdateCaseSuccessAndAuditLogged() throws Exception {
        CaseRequestDto request = buildValidCaseRequest();

        // 1. Create initial case
        String responseContent = mockMvc.perform(post("/api/cases")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        SafetyCase created = safetyCaseRepository.findAll().get(0);
        Long caseId = created.getId();
        int initialVersion = created.getVersionNumber();

        // 2. Prepare update
        request.setPriority("Expedited");
        request.setNarrative("Updated narrative: Follow-up received from treating physician.");
        request.getEvents().get(0).setOutcome("Fatal");
        request.getEvents().get(0).setResultsInDeath(true);

        mockMvc.perform(put("/api/cases/" + caseId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(caseId.intValue())))
                .andExpect(jsonPath("$.versionNumber", is(initialVersion + 1)))
                .andExpect(jsonPath("$.priority", is("Expedited")))
                .andExpect(jsonPath("$.fatal", is(true)))
                .andExpect(jsonPath("$.narrative", containsString("Follow-up received")));

        // Verify database reflects version bump & fatal outcome
        SafetyCase updated = safetyCaseRepository.findById(caseId).orElseThrow();
        assertEquals(initialVersion + 1, updated.getVersionNumber());
        assertEquals("Expedited", updated.getPriority());
        assertTrue(updated.isFatal());

        // Verify CASE_UPDATED audit event
        boolean hasUpdateAudit = auditEventRepository.findAll().stream()
                .anyMatch(a -> "CASE_UPDATED".equals(a.getEventType()) && a.getDetails().contains("version " + updated.getVersionNumber()));
        assertTrue(hasUpdateAudit);
    }

    @Test
    @WithMockUser(username = "safetyofficer", roles = {"USER"})
    public void testGetCaseByIdAndCaseNumber() throws Exception {
        CaseRequestDto request = buildValidCaseRequest();

        mockMvc.perform(post("/api/cases")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        SafetyCase created = safetyCaseRepository.findAll().get(0);

        // Retrieve by ID
        mockMvc.perform(get("/api/cases/" + created.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseNumber", is(created.getCaseNumber())))
                .andExpect(jsonPath("$.countryCode", is("USA")));

        // Retrieve by Case Number
        mockMvc.perform(get("/api/cases/by-number/" + created.getCaseNumber()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(created.getId().intValue())))
                .andExpect(jsonPath("$.reportType", is("Spontaneous")));
    }

    @Test
    @WithMockUser(username = "safetyofficer", roles = {"USER"})
    public void testGetCaseNotFoundReturns404() throws Exception {
        mockMvc.perform(get("/api/cases/99999"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/cases/by-number/NONEXISTENT-CASE"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "safetyofficer", roles = {"USER"})
    public void testSearchAndAssignedCases() throws Exception {
        CaseRequestDto req1 = buildValidCaseRequest();
        req1.setCountryCode("USA");

        CaseRequestDto req2 = buildValidCaseRequest();
        req2.setCountryCode("DEU");
        req2.setWorkflowState("Medical Review");

        mockMvc.perform(post("/api/cases").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(req1)));
        mockMvc.perform(post("/api/cases").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(req2)));

        // Search by country
        mockMvc.perform(get("/api/cases").param("country", "DEU"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].countryCode", is("DEU")))
                .andExpect(jsonPath("$[0].workflowState", is("Medical Review")));

        // Search all
        mockMvc.perform(get("/api/cases"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        // Get assigned cases (both assigned to safetyofficer by default)
        mockMvc.perform(get("/api/cases/assigned"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }
}
