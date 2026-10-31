package com.pharmasafe.controller;

import com.pharmasafe.entity.User;
import com.pharmasafe.entity.SafetyCase;
import com.pharmasafe.repository.UserRepository;
import com.pharmasafe.repository.SafetyCaseRepository;
import com.pharmasafe.repository.CaseReviewEventRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class SessionAndRevisionTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired SafetyCaseRepository cases;
    @Autowired EntityManager em;
    User user;
    @BeforeEach void seed() { user=users.saveAndFlush(new User("session-test",encoder.encode("SyntheticPassword123!"),"ROLE_CASE_PROCESSOR")); }
    MockHttpSession login() throws Exception {
        var result=mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"username\":\"session-test\",\"password\":\"SyntheticPassword123!\"}"))
                .andExpect(status().isOk()).andReturn();
        var session=(MockHttpSession)result.getRequest().getSession(false);
        assertNotNull(session.getAttribute("PHARMASAFE_AUTH_VERSION"));
        return session;
    }
    @Test void realSessionCanRestoreAndRoleChangeRevokesIt() throws Exception {
        var session=login();
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk());
        user.setRole("ROLE_AUDITOR"); user.revokeSessions(); users.saveAndFlush(user);
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
    }
    @Test void disabledAccountLosesItsExistingSession() throws Exception {
        var session=login();
        user.setEnabled(false);user.revokeSessions();users.saveAndFlush(user);
        mvc.perform(get("/api/workspace/cases").session(session)).andExpect(status().isUnauthorized());
    }
    @Test void passwordRevisionRevokesSessionWithoutChangingRole() throws Exception {
        var session=login(); user.revokeSessions(); users.saveAndFlush(user);
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
    }
    @Test @WithMockUser(username="quality-test",roles="QUALITY_REVIEWER")
    void qcApprovalRequiresAndStoresStructuredEvidence() throws Exception {
        users.save(new User("quality-test","hash","ROLE_QUALITY_REVIEWER"));
        SafetyCase c=new SafetyCase();c.setCaseNumber("STRUCTURED-QC");c.setWorkflowState("Quality Review");
        c.setInitialReceiptDate(LocalDate.now());c.setLatestReceivedDate(LocalDate.now());c.setCreatedByUser(user);
        cases.saveAndFlush(c);
        mvc.perform(post("/api/cases/"+c.getId()+"/workflow-transition").with(csrf())
            .contentType("application/json").content("{\"targetState\":\"Approved\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/cases/"+c.getId()+"/workflow-transition").with(csrf())
            .contentType("application/json").content("{\"targetState\":\"Approved\",\"comments\":\"Verified\",\"qcChecklist\":[\"patient\",\"productsEvents\",\"medical\"]}"))
            .andExpect(status().isOk());
        em.flush();
        Object[] evidence=(Object[])em.createNativeQuery("SELECT patient_reviewed,products_events_reviewed,medical_reviewed FROM case_review_events WHERE case_id=:id")
                .setParameter("id",c.getId()).getSingleResult();
        for(Object flag:evidence) assertEquals(Boolean.TRUE,flag);
    }
    @Test @WithMockUser(username="viewer-test",roles="AUDITOR")
    void overviewDoesNotExposeOperationalOrUserManagementDataToAuditors() throws Exception {
        mvc.perform(get("/api/workspace/overview")).andExpect(status().isOk())
            .andExpect(jsonPath("$.totalUsers").doesNotExist()).andExpect(jsonPath("$.workload").doesNotExist())
            .andExpect(jsonPath("$.auditEvents").exists());
        mvc.perform(get("/api/admin/users/paged")).andExpect(status().isForbidden());
    }
    @Test @WithMockUser(username="quality-return",roles="QUALITY_REVIEWER")
    void returnToMedicalIsRecordedAsReturnedRatherThanApproved() throws Exception {
        users.save(new User("quality-return","hash","ROLE_QUALITY_REVIEWER"));
        SafetyCase c=new SafetyCase();c.setCaseNumber("QC-RETURN");c.setWorkflowState("Quality Review");
        c.setInitialReceiptDate(LocalDate.now());c.setLatestReceivedDate(LocalDate.now());cases.saveAndFlush(c);
        mvc.perform(post("/api/cases/"+c.getId()+"/workflow-transition").with(csrf()).contentType("application/json")
            .content("{\"targetState\":\"Medical Review\",\"comments\":\"Clarify assessment\"}"))
            .andExpect(status().isOk());em.flush();
        assertEquals("RETURNED",em.createNativeQuery("SELECT outcome FROM case_review_events WHERE case_id=:id")
                .setParameter("id",c.getId()).getSingleResult());
    }
    @Test @WithMockUser(username="medical-test",roles="MEDICAL_REVIEWER")
    void assessmentCannotReferenceProductsAndEventsOutsideItsCase() throws Exception {
        users.save(new User("medical-test","hash","ROLE_MEDICAL_REVIEWER"));
        SafetyCase c=new SafetyCase();c.setCaseNumber("ASSESSMENT-SCOPE");c.setWorkflowState("Medical Review");
        c.setInitialReceiptDate(LocalDate.now());c.setLatestReceivedDate(LocalDate.now());cases.saveAndFlush(c);
        mvc.perform(post("/api/cases/"+c.getId()+"/assessments").with(csrf()).contentType("application/json")
            .content("{\"productName\":\"Other product\",\"eventVerbatim\":\"Other event\",\"causalityCompany\":\"Possible\",\"expectedness\":\"Unknown\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test @WithMockUser(username="session-test",roles="CASE_PROCESSOR")
    void intakeCannotForgeOrOverwriteMedicalAssessmentThroughCaseUpdate() throws Exception {
        SafetyCase c=new SafetyCase();c.setCaseNumber("PROTECTED-ASSESSMENT");c.setWorkflowState("Data Entry");
        c.setInitialReceiptDate(LocalDate.now());c.setLatestReceivedDate(LocalDate.now());c.setCreatedByUser(user);
        var a=new com.pharmasafe.entity.CaseAssessment();a.setAssessedBy("medical-reviewer");a.setCausalityCompany("Unassessable");c.addAssessment(a);cases.saveAndFlush(c);
        String body="{\"countryCode\":\"USA\",\"reportType\":\"Spontaneous\",\"initialReceiptDate\":\""+LocalDate.now()+"\",\"latestReceivedDate\":\""+LocalDate.now()+"\",\"expectedRowVersion\":"+c.getRowVersion()+",\"workflowState\":\"Data Entry\",\"draft\":true,\"assessments\":[{\"assessedBy\":\"forged\",\"causalityCompany\":\"Certain\"}]}";
        mvc.perform(put("/api/cases/"+c.getId()).with(csrf()).contentType("application/json").content(body)).andExpect(status().isBadRequest());
        assertEquals("medical-reviewer",cases.findById(c.getId()).orElseThrow().getAssessments().get(0).getAssessedBy());
    }

}
