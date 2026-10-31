package com.pharmasafe.controller;
import com.pharmasafe.entity.*;
import com.pharmasafe.repository.*;
import com.pharmasafe.security.WorkspaceAccess;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class WorkspaceAccessTest {
    @Autowired MockMvc mvc;
    @Autowired SafetyCaseRepository cases;
    @Autowired UserRepository users;
    @Autowired jakarta.persistence.EntityManager entityManager;
    User owner;
    SafetyCase medical;
    SafetyCase quality;
    @BeforeEach void seed() {
        owner = users.save(new User("queue-owner", "hash", "ROLE_CASE_PROCESSOR"));
        medical = seedCase("QUEUE-MED", "Medical Review");
        quality = seedCase("QUEUE-QC", "Quality Review");
    }
    SafetyCase seedCase(String number, String state) {
        SafetyCase c = new SafetyCase(); c.setCaseNumber(number); c.setWorkflowState(state);
        c.setInitialReceiptDate(LocalDate.now()); c.setLatestReceivedDate(LocalDate.now());
        c.setCreatedByUser(owner); c.setAssignedToUser(owner);
        return cases.saveAndFlush(c);
    }
    @Test @WithMockUser(username="reviewer",roles="MEDICAL_REVIEWER")
    void medicalQueueAndDetailsHaveSameScope() throws Exception {
        mvc.perform(get("/api/workspace/cases")).andExpect(status().isOk())
            .andExpect(jsonPath("$.content[*].caseNumber",hasItem("QUEUE-MED")))
            .andExpect(jsonPath("$.content[*].caseNumber",not(hasItem("QUEUE-QC"))));
        mvc.perform(get("/api/cases/"+quality.getId())).andExpect(status().isForbidden());
        mvc.perform(get("/api/cases/"+quality.getId()+"/audit-history")).andExpect(status().isForbidden());
        mvc.perform(get("/api/dashboard/worklist")).andExpect(status().isOk())
            .andExpect(jsonPath("$.content[*].caseNumber",not(hasItem("QUEUE-QC"))));
    }
    @Test @WithMockUser(username="outsider",roles="CASE_PROCESSOR")
    void processorCannotReadOrUpdateSomeoneElsesCase() throws Exception {
        mvc.perform(get("/api/workspace/cases")).andExpect(status().isOk())
            .andExpect(jsonPath("$.content[*].caseNumber",not(hasItem("QUEUE-MED"))));
        mvc.perform(get("/api/cases/"+medical.getId())).andExpect(status().isForbidden());
    }
    @Test @WithMockUser(roles="AUDITOR")
    void auditorIsReadOnlyEvenWithCsrf() throws Exception {
        mvc.perform(post("/api/cases").with(csrf()).contentType("application/json").content("{}"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/reports").with(csrf()).contentType("application/json").content("{}"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/action-items/1/complete").with(csrf())).andExpect(status().isForbidden());
        mvc.perform(get("/api/workspace/audit")).andExpect(status().isOk());
        mvc.perform(get("/api/admin/users")).andExpect(status().isForbidden());
    }
    @Test @WithMockUser(roles="REPORTING_SPECIALIST")
    void reportingSeesApprovedCasesOnly() throws Exception {
        seedCase("QUEUE-APPROVED", "Approved");
        mvc.perform(get("/api/workspace/cases")).andExpect(status().isOk())
            .andExpect(jsonPath("$.content[*].caseNumber",hasItem("QUEUE-APPROVED")))
            .andExpect(jsonPath("$.content[*].caseNumber",not(hasItem("QUEUE-MED"))));
        mvc.perform(get("/api/reports")).andExpect(status().isOk());
        mvc.perform(post("/api/reports").with(csrf()).contentType("application/json")
            .content("{\"relatedCaseNumbers\":[\"QUEUE-MED\"]}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/cases/"+medical.getId()+"/workflow-transition").with(csrf())
            .contentType("application/json").content("{\"targetState\":\"Quality Review\"}"))
            .andExpect(status().isForbidden());
    }
    @Test void policyRejectsUnknownRolesAndDoesNotExpandReadAccessForAuthorship() {
        WorkspaceAccess policy = new WorkspaceAccess();
        assertFalse(policy.canRead(medical,owner.getUsername(),"ROLE_UNKNOWN"));
        assertFalse(policy.canRead(quality,owner.getUsername(),"ROLE_MEDICAL_REVIEWER"));
        assertEquals("ROLE_AUDITOR",WorkspaceAccess.normalizeRole("auditor"));
        assertThrows(IllegalArgumentException.class,()->WorkspaceAccess.normalizeRole("superuser"));
    }
    @Test @WithMockUser(username="reviewer",roles="MEDICAL_REVIEWER")
    void medicalReviewCanAdvanceButCannotGiveFinalApproval() throws Exception {
        users.save(new User("reviewer", "hash", "ROLE_MEDICAL_REVIEWER"));
        mvc.perform(post("/api/cases/"+medical.getId()+"/workflow-transition").with(csrf())
            .contentType("application/json").content("{\"targetState\":\"Quality Review\",\"comments\":\"Clinical review completed\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.workflowState",is("Quality Review")));
        mvc.perform(post("/api/cases/"+medical.getId()+"/workflow-transition").with(csrf())
            .contentType("application/json").content("{\"targetState\":\"Approved\"}"))
            .andExpect(status().isForbidden());
    }
    @Test @WithMockUser(username="manager",roles="SAFETY_MANAGER")
    void managerLockTransitionSetsTheDatabaseLockFlag() throws Exception {
        users.save(new User("manager", "hash", "ROLE_SAFETY_MANAGER"));
        SafetyCase approved = seedCase("QUEUE-LOCK", "Approved");
        mvc.perform(post("/api/cases/"+approved.getId()+"/workflow-transition").with(csrf())
            .contentType("application/json").content("{\"targetState\":\"Locked\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.locked",is(true)))
            .andExpect(jsonPath("$.lockedByUsername",is("manager")));
    }
    @Test @WithMockUser(username="admin",roles="ADMIN")
    void adminCannotCreateAnUnknownRole() throws Exception {
        mvc.perform(post("/api/admin/users").with(csrf()).contentType("application/json")
            .content("{\"username\":\"bad-role-user\",\"password\":\"StrongPassword123!\",\"role\":\"SUPERUSER\"}"))
            .andExpect(status().isBadRequest());
        assertTrue(users.findByUsername("bad-role-user").isEmpty());
    }

    @Test void optimisticLockRejectsConcurrentOverwrite() {
        entityManager.detach(medical);
        entityManager.createNativeQuery("UPDATE cases SET row_version = row_version + 1 WHERE id = :id")
                .setParameter("id", medical.getId()).executeUpdate();
        medical.setNarrative("Stale update");
        assertThrows(org.springframework.orm.ObjectOptimisticLockingFailureException.class,
                () -> cases.saveAndFlush(medical));
    }
    @Test @WithMockUser(username="reviewer",roles="MEDICAL_REVIEWER")
    void reviewerCanSeeOwnContactAndTaskForTheReviewQueue() throws Exception {
        User reviewer = users.save(new User("reviewer", "hash", "ROLE_MEDICAL_REVIEWER"));
        mvc.perform(post("/api/cases/"+medical.getId()+"/contact-logs").with(csrf())
            .contentType("application/json").content("{\"contactAt\":\"2026-10-31T10:00:00Z\",\"contactType\":\"Email\",\"description\":\"Review follow-up\"}"))
            .andExpect(status().isCreated());
        mvc.perform(get("/api/contact-logs")).andExpect(status().isOk())
            .andExpect(jsonPath("$.content[*].description",hasItem("Review follow-up")));
        mvc.perform(post("/api/cases/"+medical.getId()+"/action-items").with(csrf())
            .contentType("application/json").content("{\"description\":\"Check clinical evidence\",\"assignedUserId\":"+reviewer.getId()+",\"dueAt\":\"2026-11-01T10:00:00Z\"}"))
            .andExpect(status().isCreated());
        mvc.perform(get("/api/action-items")).andExpect(status().isOk())
            .andExpect(jsonPath("$.content[*].description",hasItem("Check clinical evidence")));
    }

}
