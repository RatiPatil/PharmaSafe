package com.pharmasafe.service;

import com.pharmasafe.dto.CaseRequestDto;
import com.pharmasafe.dto.CaseResponseDto;
import com.pharmasafe.dto.CaseSummaryDto;
import com.pharmasafe.entity.*;
import com.pharmasafe.repository.SafetyCaseRepository;
import com.pharmasafe.repository.CaseReviewEventRepository;
import com.pharmasafe.repository.CaseFollowUpRepository;
import com.pharmasafe.repository.SafetyReportRepository;
import com.pharmasafe.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CaseService {

    private static final Logger log = LoggerFactory.getLogger(CaseService.class);

    private final SafetyCaseRepository safetyCaseRepository;
    private final UserRepository userRepository;
    private final CaseNumberGeneratorService caseNumberGeneratorService;
    private final AuditService auditService;
    private final CaseReviewEventRepository caseReviewEventRepository;
    private final CaseClosureGuard closureGuard;
    private final CaseFollowUpRepository followUps;
    private final SafetyReportRepository reports;

    public CaseService(SafetyCaseRepository safetyCaseRepository,
                       UserRepository userRepository,
                       CaseNumberGeneratorService caseNumberGeneratorService,
                       AuditService auditService,
                       CaseReviewEventRepository caseReviewEventRepository, CaseClosureGuard closureGuard, CaseFollowUpRepository followUps, SafetyReportRepository reports) {
        this.closureGuard=closureGuard;this.followUps=followUps;this.reports=reports;
        this.safetyCaseRepository = safetyCaseRepository;
        this.userRepository = userRepository;
        this.caseNumberGeneratorService = caseNumberGeneratorService;
        this.auditService = auditService;
        this.caseReviewEventRepository = caseReviewEventRepository;
    }

    @Transactional
    public CaseResponseDto createCase(CaseRequestDto request, String username, String clientIp, String userAgent) {
        rejectIntakeAssessmentWrites(request);
        validateCaseDates(request);

        // Conditional validation: For formal submissions (non-drafts), ICH E2B requires minimum criteria
        if (!request.isDraft()) {
            validateMinimumCaseCriteria(request);
        }

        User user = null;
        if (username != null) {
            user = userRepository.findByUsername(username.trim().toLowerCase()).orElse(null);
        }

        String caseNumber = caseNumberGeneratorService.generateCaseNumber(request.getCountryCode());

        SafetyCase safetyCase = new SafetyCase();
        safetyCase.setCaseNumber(caseNumber);
        safetyCase.setCountryCode(request.getCountryCode() != null ? request.getCountryCode().trim().toUpperCase() : "USA");
        safetyCase.setReportType(request.getReportType());
        safetyCase.setInitialReceiptDate(request.getInitialReceiptDate());
        safetyCase.setLatestReceivedDate(request.getLatestReceivedDate());
        safetyCase.setWorkflowState(request.isDraft() ? "Draft" : "Data Entry");
        safetyCase.setPriority(request.getPriority() != null ? request.getPriority() : "Routine");
        safetyCase.setNarrative(request.getNarrative());
        safetyCase.setCompanyComments(request.getCompanyComments());
        safetyCase.setVersionNumber(1);
        safetyCase.setCreatedByUser(user);
        safetyCase.setUpdatedByUser(user);

        // Assign case
        if (request.getAssignedToUsername() != null && !request.getAssignedToUsername().trim().isEmpty()) {
            User assignedUser = userRepository.findByUsername(request.getAssignedToUsername().trim().toLowerCase()).orElse(user);
            safetyCase.setAssignedToUser(assignedUser);
        } else {
            safetyCase.setAssignedToUser(user);
        }

        // Map Patient
        mapPatient(request, safetyCase);

        // Map Reporters
        mapReporters(request, safetyCase);

        // Map Products
        mapProducts(request, safetyCase);

        // Map Events
        mapEvents(request, safetyCase);

        // Medical assessments are authored separately in the medical review workflow.

        // Map Medical History
        mapMedicalHistory(request, safetyCase);

        // Map References
        mapReferences(request, safetyCase);

        // Map Attachments
        mapAttachments(request, safetyCase, user);

        safetyCase.syncSeriousness();
        SafetyCase savedCase = safetyCaseRepository.saveAndFlush(safetyCase);

        // Audit Logging
        String auditDetails = String.format("Created safety case %s (Country: %s, Type: %s, State: %s, Serious: %s)",
                savedCase.getCaseNumber(), savedCase.getCountryCode(), savedCase.getReportType(),
                savedCase.getWorkflowState(), savedCase.isSerious());
        auditService.logCaseEvent(savedCase.getId(), "CASE_CREATED", username, "SUCCESS", auditDetails, clientIp, userAgent);
        log.info("Safety case {} created successfully by user '{}'", savedCase.getCaseNumber(), username);

        return CaseResponseDto.fromEntity(savedCase);
    }

    @Transactional
    public CaseResponseDto updateCase(Long id, CaseRequestDto request, String username, String clientIp, String userAgent) {
        SafetyCase safetyCase = safetyCaseRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Safety case not found with ID: " + id));

        if (safetyCase.isLocked()) {
            throw new IllegalStateException("Cannot update safety case " + safetyCase.getCaseNumber() +
                    " because it is currently locked by " +
                    (safetyCase.getLockedByUser() != null ? safetyCase.getLockedByUser().getUsername() : "another user") +
                    ". Unlock the case first before editing.");
        }

        if (request.getExpectedRowVersion() != null && request.getExpectedRowVersion() != safetyCase.getRowVersion()) {
            throw new org.springframework.orm.ObjectOptimisticLockingFailureException(SafetyCase.class, id);
        }
        rejectIntakeAssessmentWrites(request);
        validateCaseDates(request);

        if (!("Draft".equals(safetyCase.getWorkflowState()) && request.isDraft())) {
            validateMinimumCaseCriteria(request);
        }

        User user = null;
        if (username != null) {
            user = userRepository.findByUsername(username.trim().toLowerCase()).orElse(null);
        }

        var earliest = followUps.earliestReceipt(id); var latest = followUps.latestReceipt(id);
        if (earliest != null && (request.getInitialReceiptDate() == null || request.getInitialReceiptDate().isAfter(earliest))) throw new IllegalArgumentException("Initial receipt date cannot follow an existing receipt");
        if (latest != null && (request.getLatestReceivedDate() == null || request.getLatestReceivedDate().isBefore(latest))) throw new IllegalArgumentException("Latest receipt date cannot precede recorded follow-up information");
        if (reports.countSubmittedForCase(id)>0 && !java.util.Objects.equals(request.getInitialReceiptDate(),safetyCase.getInitialReceiptDate())) throw new IllegalArgumentException("Initial receipt date cannot change after report submission");
        safetyCase.setCountryCode(request.getCountryCode() != null ? request.getCountryCode().trim().toUpperCase() : safetyCase.getCountryCode());
        safetyCase.setReportType(request.getReportType());
        safetyCase.setInitialReceiptDate(request.getInitialReceiptDate());
        safetyCase.setLatestReceivedDate(request.getLatestReceivedDate());

        if ("Draft".equals(safetyCase.getWorkflowState()) && !request.isDraft()
                && "Data Entry".equals(request.getWorkflowState())) {
            safetyCase.setWorkflowState("Data Entry");
        }
        if (request.getWorkflowState() != null && !request.getWorkflowState().equals(safetyCase.getWorkflowState())) {
            throw new IllegalArgumentException("Use the workflow transition action to change state");
        }
        safetyCase.setPriority(request.getPriority() != null ? request.getPriority() : safetyCase.getPriority());
        safetyCase.setNarrative(request.getNarrative());
        safetyCase.setCompanyComments(request.getCompanyComments());
        safetyCase.setVersionNumber(safetyCase.getVersionNumber() + 1);
        safetyCase.setUpdatedByUser(user);

        if (request.getAssignedToUsername() != null && !request.getAssignedToUsername().isBlank()
                && (safetyCase.getAssignedToUser() == null || !request.getAssignedToUsername()
                    .equalsIgnoreCase(safetyCase.getAssignedToUser().getUsername()))) {
            throw new IllegalArgumentException("Use the assignment action to change case ownership");
        }
        // Clear existing children and re-populate
        mapPatient(request, safetyCase);

        safetyCase.getReporters().clear();
        mapReporters(request, safetyCase);

        safetyCase.getProducts().clear();
        mapProducts(request, safetyCase);

        safetyCase.getEvents().clear();
        mapEvents(request, safetyCase);

        // Preserve medical reviewer records during intake corrections.

        safetyCase.getMedicalHistoryList().clear();
        mapMedicalHistory(request, safetyCase);

        safetyCase.getReferences().clear();
        mapReferences(request, safetyCase);

        safetyCase.syncSeriousness();
        SafetyCase updatedCase = safetyCaseRepository.saveAndFlush(safetyCase);

        // Audit Logging
        String auditDetails = String.format("Updated safety case %s to version %d (State: %s, Serious: %s)",
                updatedCase.getCaseNumber(), updatedCase.getVersionNumber(), updatedCase.getWorkflowState(), updatedCase.isSerious());
        auditService.logCaseEvent(updatedCase.getId(), "CASE_UPDATED", username, "SUCCESS", auditDetails, clientIp, userAgent);
        log.info("Safety case {} updated to version {} by user '{}'", updatedCase.getCaseNumber(), updatedCase.getVersionNumber(), username);

        return CaseResponseDto.fromEntity(updatedCase);
    }

    @Transactional(readOnly = true)
    public CaseResponseDto getCaseById(Long id) {
        SafetyCase safetyCase = safetyCaseRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Safety case not found with ID: " + id));
        return CaseResponseDto.fromEntity(safetyCase);
    }

    @Transactional(readOnly = true)
    public CaseResponseDto getCaseByIdAuthorized(Long id, String username, String role) {
        SafetyCase safetyCase = safetyCaseRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Safety case not found with ID: " + id));
        requireReadAccess(safetyCase, username, role);
        return CaseResponseDto.fromEntity(safetyCase);
    }

    @Transactional(readOnly = true)
    public CaseResponseDto getCaseByCaseNumber(String caseNumber) {
        if (caseNumber == null || caseNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("Case number cannot be empty");
        }
        SafetyCase safetyCase = safetyCaseRepository.findByCaseNumberIgnoreCase(caseNumber.trim())
                .orElseThrow(() -> new EntityNotFoundException("Safety case not found with number: " + caseNumber.trim()));
        return CaseResponseDto.fromEntity(safetyCase);
    }

    @Transactional(readOnly = true)
    public CaseResponseDto getCaseByCaseNumberAuthorized(String caseNumber, String username, String role) {
        if (caseNumber == null || caseNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("Case number cannot be empty");
        }
        SafetyCase safetyCase = safetyCaseRepository.findByCaseNumberIgnoreCase(caseNumber.trim())
                .orElseThrow(() -> new EntityNotFoundException("Safety case not found with number: " + caseNumber.trim()));
        requireReadAccess(safetyCase, username, role);
        return CaseResponseDto.fromEntity(safetyCase);
    }

    private void requireReadAccess(SafetyCase safetyCase, String username, String role) {
        if (!new com.pharmasafe.security.WorkspaceAccess().canRead(safetyCase, username, role)) {
            throw new org.springframework.security.access.AccessDeniedException("You are not authorized to access this case");
        }
    }

    @Transactional(readOnly = true)
    public List<CaseSummaryDto> searchCases(String search, String country, String workflowState, String reportType) {
        List<SafetyCase> cases = safetyCaseRepository.searchCases(
                (search != null && !search.trim().isEmpty()) ? search.trim() : null,
                (country != null && !country.trim().isEmpty()) ? country.trim().toUpperCase() : null,
                (workflowState != null && !workflowState.trim().isEmpty()) ? workflowState.trim() : null,
                (reportType != null && !reportType.trim().isEmpty()) ? reportType.trim() : null
        );
        return cases.stream().map(CaseSummaryDto::fromEntity).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CaseSummaryDto> searchCasesAuthorized(String search, String country, String workflowState,
                                                       String reportType, String username, String role) {
        List<CaseSummaryDto> results = searchCases(search, country, workflowState, reportType);
        return results.stream().filter(summary -> safetyCaseRepository.findById(summary.getId())
                .map(c -> new com.pharmasafe.security.WorkspaceAccess().canRead(c, username, role))
                .orElse(false)).toList();
    }

    @Transactional(readOnly = true)
    public List<CaseSummaryDto> getAssignedCases(String username) {
        if (username == null || username.trim().isEmpty()) {
            return Collections.emptyList();
        }
        User user = userRepository.findByUsername(username.trim().toLowerCase()).orElse(null);
        if (user == null) {
            return Collections.emptyList();
        }
        List<SafetyCase> cases = safetyCaseRepository.findByAssignedToUserOrderByCreatedAtDesc(user);
        return cases.stream().map(CaseSummaryDto::fromEntity).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public User findUser(String username) {
        return userRepository.findByUsername(username.trim().toLowerCase())
                .orElseThrow(() -> new EntityNotFoundException("Authenticated user not found"));
    }

    @Transactional(readOnly = true)
    public Page<CaseSummaryDto> getAssignedCasesPage(User user, Pageable pageable) {
        return safetyCaseRepository.findByAssignedToUser(user, pageable).map(CaseSummaryDto::fromEntity);
    }

    @Transactional(readOnly = true)
    public List<CaseSummaryDto> getAllCases() {
        return safetyCaseRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(CaseSummaryDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    public CaseResponseDto lockCase(Long id, String username) {
        SafetyCase safetyCase = safetyCaseRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Safety case not found with ID: " + id));
        if ("Closed".equals(safetyCase.getWorkflowState())) throw new IllegalStateException("Reopen the case before changing it");

        if (safetyCase.isLocked()) {
            throw new IllegalStateException("Case " + safetyCase.getCaseNumber() + " is already locked by " +
                    (safetyCase.getLockedByUser() != null ? safetyCase.getLockedByUser().getUsername() : "another user"));
        }

        User user = null;
        if (username != null) {
            user = userRepository.findByUsername(username.trim().toLowerCase()).orElse(null);
        }

        safetyCase.setLocked(true);
        safetyCase.setLockedByUser(user);
        safetyCase.setLockedAt(Instant.now());
        SafetyCase saved = safetyCaseRepository.saveAndFlush(safetyCase);

        auditService.logCaseEvent(id, "CASE_LOCKED", username, "SUCCESS",
                "Case " + safetyCase.getCaseNumber() + " was locked by " + username);

        CaseResponseDto response = CaseResponseDto.fromEntity(saved);
        if (response.isLocked() && response.getLockedByUsername() == null) {
            response.setLockedByUsername(username);
        }
        return response;
    }

    @Transactional
    public CaseResponseDto unlockCase(Long id, String username) {
        SafetyCase safetyCase = safetyCaseRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Safety case not found with ID: " + id));
        if ("Closed".equals(safetyCase.getWorkflowState())) throw new IllegalStateException("Reopen the case before changing it");

        if (!safetyCase.isLocked()) {
            throw new IllegalStateException("Case " + safetyCase.getCaseNumber() + " is not currently locked");
        }

        safetyCase.setLocked(false);
        safetyCase.setLockedByUser(null);
        safetyCase.setLockedAt(null);
        SafetyCase saved = safetyCaseRepository.saveAndFlush(safetyCase);

        auditService.logCaseEvent(id, "CASE_UNLOCKED", username, "SUCCESS",
                "Case " + safetyCase.getCaseNumber() + " was unlocked by " + username);

        CaseResponseDto response = CaseResponseDto.fromEntity(saved);
        if (response.isLocked() && response.getLockedByUsername() == null) {
            response.setLockedByUsername(username);
        }
        return response;
    }

    @Transactional
    public CaseResponseDto changePriority(Long id, String priority, String username) {
        if (priority == null || !Set.of("Routine", "Priority", "Expedited").contains(priority)) {
            throw new IllegalArgumentException("Invalid priority");
        }
        SafetyCase c = safetyCaseRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Case not found"));
        if ("Closed".equals(c.getWorkflowState())) throw new IllegalStateException("Reopen the case before changing it");
        if (c.isLocked()) throw new IllegalStateException("Unlock the case before changing its priority");
        c.setPriority(priority);
        c.setUpdatedByUser(findUser(username));
        safetyCaseRepository.saveAndFlush(c);
        auditService.logCaseEvent(id, "CASE_PRIORITY_CHANGED", username, "SUCCESS", "Priority changed to " + priority);
        return CaseResponseDto.fromEntity(c);
    }

    @Transactional
    public CaseResponseDto assignCase(Long id, Long targetUserId, String username) {
        SafetyCase safetyCase = safetyCaseRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Safety case not found with ID: " + id));
        if ("Closed".equals(safetyCase.getWorkflowState())) throw new IllegalStateException("Reopen the case before changing it");

        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new EntityNotFoundException("Target user not found with ID: " + targetUserId));
        if (!targetUser.isEnabled()) {
            throw new IllegalArgumentException("Only enabled users may receive case assignments");
        }
        if (!Set.of("ROLE_CASE_PROCESSOR", "ROLE_USER", "ROLE_MEDICAL_REVIEWER",
                "ROLE_QUALITY_REVIEWER", "ROLE_SAFETY_MANAGER", "ROLE_ADMIN").contains(targetUser.getRole())) {
            throw new IllegalArgumentException("Target user role is not eligible for case assignment");
        }

        String prevAssigned = safetyCase.getAssignedToUser() != null ? safetyCase.getAssignedToUser().getUsername() : "Unassigned";
        safetyCase.setAssignedToUser(targetUser);
        SafetyCase saved = safetyCaseRepository.saveAndFlush(safetyCase);

        auditService.logCaseEvent(id, "CASE_ASSIGNED", username, "SUCCESS",
                "Case " + safetyCase.getCaseNumber() + " assigned from " + prevAssigned + " to " + targetUser.getUsername());

        return CaseResponseDto.fromEntity(saved);
    }

    @Transactional
    public CaseResponseDto transitionWorkflow(Long id, String targetState, String comments, String username, String role) {
        return transitionWorkflow(id, targetState, comments, username, role, java.util.Set.of());
    }

    @Transactional
    public CaseResponseDto transitionWorkflow(Long id, String targetState, String comments, String username, String role,
                                               java.util.Set<String> qcChecklist) {
        SafetyCase safetyCase = safetyCaseRepository.findForUpdate(id)
                .orElseThrow(() -> new EntityNotFoundException("Safety case not found with ID: " + id));

        if (targetState == null || targetState.trim().isEmpty()) {
            throw new IllegalArgumentException("Target workflow state cannot be empty");
        }

        String prevState = safetyCase.getWorkflowState();
        String nextState = targetState.trim();
        validateWorkflowTransition(safetyCase, prevState, nextState, role, username, comments);
        if (safetyCase.isLocked() && !("Locked".equals(prevState) && "Closed".equals(nextState))) {
            throw new IllegalStateException("Unlock the case before routing it");
        }
        if ("Approved".equals(nextState) && (qcChecklist == null || !qcChecklist.containsAll(
                java.util.Set.of("patient", "productsEvents", "medical")))) {
            throw new IllegalArgumentException("Confirm all three quality review checks before approval");
        }
        if ("Closed".equals(nextState)) {
            closureGuard.requireReady(safetyCase);
            safetyCase.setLocked(false); safetyCase.setLockedAt(null); safetyCase.setLockedByUser(null);
            safetyCase.setClosedAt(Instant.now()); safetyCase.setClosedByUser(findUser(username));
        }
        safetyCase.setWorkflowState(nextState);
        if ("Locked".equals(nextState)) {
            safetyCase.setLocked(true);
            safetyCase.setLockedAt(Instant.now());
            safetyCase.setLockedByUser(findUser(username));
        }
        if (comments != null && !comments.trim().isEmpty()) {
            String existingComments = safetyCase.getCompanyComments();
            safetyCase.setCompanyComments((existingComments != null ? existingComments + "\n" : "") +
                    "[" + nextState + " by " + username + "]: " + comments.trim());
        }
        SafetyCase saved = safetyCaseRepository.saveAndFlush(safetyCase);

        User reviewer = username == null ? null : userRepository.findByUsername(username.trim().toLowerCase()).orElse(null);
        CaseReviewEvent review = new CaseReviewEvent();
        review.setSafetyCase(saved);
        if ("Approved".equals(nextState)) {
            review.setPatientReviewed(true); review.setProductsEventsReviewed(true); review.setMedicalReviewed(true);
        }
        review.setReviewerUser(reviewer);
        review.setReviewerUsername(username);
        review.setFromState(prevState);
        review.setToState(nextState);
        review.setOutcome((prevState.equals("Medical Review") && nextState.equals("Data Entry"))
                || (prevState.equals("Quality Review") && nextState.equals("Medical Review")) ? "RETURNED"
                : nextState.equals("Approved") ? "APPROVED" : nextState.equals("Locked") ? "LOCKED"
                : nextState.equals("Closed") ? "CLOSED" : "ROUTED");
        review.setComments(comments == null || comments.trim().isEmpty() ? "Transition approved" : comments.trim());
        caseReviewEventRepository.save(review);

        auditService.logCaseEvent(saved.getId(), "WORKFLOW_TRANSITION", username, "SUCCESS",
                "Case " + safetyCase.getCaseNumber() + " transitioned from '" + prevState + "' to '" + nextState + "'. " +
                        (comments != null ? "Comments: " + comments : ""));

        return CaseResponseDto.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public List<com.pharmasafe.dto.AuditEventResponseDto> getCaseAuditHistory(Long id) {
        if (!safetyCaseRepository.existsById(id)) {
            throw new EntityNotFoundException("Safety case not found with ID: " + id);
        }
        return auditService.getCaseAuditHistory(id);
    }

    private void validateWorkflowTransition(SafetyCase safetyCase, String current, String target,
                                            String role, String username, String comments) {
        String normalizedRole = role == null ? "" : role;
        boolean admin = "ROLE_ADMIN".equals(normalizedRole);
        boolean processor = admin || "ROLE_CASE_PROCESSOR".equals(normalizedRole) || "ROLE_USER".equals(normalizedRole);
        boolean medical = admin || "ROLE_MEDICAL_REVIEWER".equals(normalizedRole);
        boolean quality = admin || "ROLE_QUALITY_REVIEWER".equals(normalizedRole);
        boolean manager = admin || "ROLE_SAFETY_MANAGER".equals(normalizedRole);

        boolean allowed = switch (current) {
            case "Draft" -> target.equals("Data Entry") && processor;
            case "Data Entry" -> target.equals("Medical Review") && processor;
            case "Medical Review" -> (target.equals("Quality Review") && medical) || (target.equals("Data Entry") && medical);
            case "Quality Review" -> (target.equals("Approved") && quality) || (target.equals("Medical Review") && quality);
            case "Approved" -> target.equals("Locked") && manager;
            case "Locked" -> target.equals("Closed") && manager;
            default -> false;
        };
        if (!allowed) {
            throw new IllegalStateException("Transition from " + current + " to " + target + " is not permitted for role " + normalizedRole);
        }
        if ((target.equals("Data Entry") || target.equals("Medical Review")) && (comments == null || comments.trim().isEmpty())) {
            throw new IllegalArgumentException("A reason is required when returning a case for correction or review");
        }
        if (target.equals("Approved") && current.equals("Quality Review")
                && safetyCase.getCreatedByUser() != null
                && safetyCase.getCreatedByUser().getUsername().equalsIgnoreCase(username)) {
            throw new IllegalStateException("The case author cannot be the final quality approver");
        }
    }

    @Transactional
    public CaseResponseDto addAssessment(Long id, CaseRequestDto.AssessmentDto ad, String username) {
        SafetyCase safetyCase = safetyCaseRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Safety case not found with ID: " + id));

        if (!"Medical Review".equals(safetyCase.getWorkflowState()) || safetyCase.isLocked()) {
            throw new IllegalStateException("Medical assessments can only be added to unlocked cases in Medical Review");
        }
        if (ad.getProductName() == null || safetyCase.getProducts().stream().noneMatch(p -> p.getProductName().equals(ad.getProductName()))
                || ad.getEventVerbatim() == null || safetyCase.getEvents().stream().noneMatch(e -> e.getEventVerbatim().equals(ad.getEventVerbatim()))) {
            throw new IllegalArgumentException("Select a product and event belonging to this case");
        }
        if (ad.getCausalityCompany() == null || ad.getCausalityCompany().isBlank()
                || ad.getExpectedness() == null || ad.getExpectedness().isBlank()) {
            throw new IllegalArgumentException("Causality and expectedness must be explicitly recorded");
        }
        CaseAssessment a = new CaseAssessment();
        a.setProductName(ad.getProductName());
        a.setEventVerbatim(ad.getEventVerbatim());
        a.setCausalityReporter(ad.getCausalityReporter() != null ? ad.getCausalityReporter() : "Unassessable");
        a.setCausalityCompany(ad.getCausalityCompany() != null ? ad.getCausalityCompany() : "Reasonable Possibility");
        a.setExpectedness(ad.getExpectedness() != null ? ad.getExpectedness() : "Unexpected");
        a.setAssessmentComment(ad.getAssessmentComment());
        a.setAssessedBy(username != null ? username : ad.getAssessedBy());
        safetyCase.addAssessment(a);

        SafetyCase saved = safetyCaseRepository.saveAndFlush(safetyCase);

        auditService.logCaseEvent(id, "ASSESSMENT_ADDED", username, "SUCCESS",
                "Assessment added for case " + safetyCase.getCaseNumber() + ": " +
                        ad.getProductName() + " / " + ad.getEventVerbatim() + " -> " + a.getCausalityCompany() + " (" + a.getExpectedness() + ")");

        return CaseResponseDto.fromEntity(saved);
    }

    private void rejectIntakeAssessmentWrites(CaseRequestDto request) {
        if (request.getAssessments() != null && !request.getAssessments().isEmpty()) {
            throw new IllegalArgumentException("Medical assessments must be recorded in the medical review workspace");
        }
    }

    // Validation Helpers
    private void validateCaseDates(CaseRequestDto request) {
        if (request.getInitialReceiptDate() == null) {
            throw new IllegalArgumentException("Initial receipt date is required");
        }
        if (request.getLatestReceivedDate() == null) {
            throw new IllegalArgumentException("Latest received date is required");
        }
        if (request.getLatestReceivedDate().isBefore(request.getInitialReceiptDate())) {
            throw new IllegalArgumentException("Latest received date cannot be before initial receipt date");
        }
    }

    private void validateMinimumCaseCriteria(CaseRequestDto request) {
        // ICH E2B minimum criteria for valid ICSR: Reporter, Patient, Suspect Product, Adverse Event
        if (request.getReporters() == null || request.getReporters().isEmpty()) {
            throw new IllegalArgumentException("At least one reporter is required to save a safety case");
        }
        if (request.getProducts() == null || request.getProducts().isEmpty()) {
            throw new IllegalArgumentException("At least one product is required to save a safety case");
        }
        boolean hasSuspect = request.getProducts().stream()
                .anyMatch(p -> "Suspect".equalsIgnoreCase(p.getProductType()) && p.getProductName() != null && !p.getProductName().trim().isEmpty());
        if (!hasSuspect) {
            throw new IllegalArgumentException("At least one suspect product with a valid name is required");
        }

        if (request.getEvents() == null || request.getEvents().isEmpty()) {
            throw new IllegalArgumentException("At least one adverse event is required to save a safety case");
        }
        for (CaseRequestDto.EventDto ev : request.getEvents()) {
            if (ev.getEventVerbatim() == null || ev.getEventVerbatim().trim().isEmpty()) {
                throw new IllegalArgumentException("Adverse event verbatim description cannot be empty");
            }
            if (ev.getOnsetDate() == null) {
                throw new IllegalArgumentException("Adverse event onset date is required");
            }
            if (ev.getStopDate() != null && ev.getStopDate().isBefore(ev.getOnsetDate())) {
                throw new IllegalArgumentException("Adverse event stop date cannot be before onset date");
            }
        }
    }

    // Entity Mapping Helpers
    private void mapPatient(CaseRequestDto request, SafetyCase safetyCase) {
        if (request.getPatient() != null) {
            CasePatient patient = safetyCase.getPatient();
            if (patient == null) {
                patient = new CasePatient();
            }
            patient.setPatientIdentifier(request.getPatient().getPatientIdentifier());
            patient.setAgeValue(request.getPatient().getAgeValue());
            patient.setAgeUnit(request.getPatient().getAgeUnit() != null ? request.getPatient().getAgeUnit() : "Years");
            patient.setDateOfBirth(request.getPatient().getDateOfBirth());
            patient.setGender(request.getPatient().getGender() != null ? request.getPatient().getGender() : "Unknown");
            patient.setWeightKg(request.getPatient().getWeightKg());
            patient.setHeightCm(request.getPatient().getHeightCm());
            patient.setPregnant(request.getPatient().getPregnant() != null ? request.getPatient().getPregnant() : false);
            patient.setMedicalHistory(request.getPatient().getMedicalHistory());
            safetyCase.setPatient(patient);
        }
    }

    private void mapReporters(CaseRequestDto request, SafetyCase safetyCase) {
        if (request.getReporters() != null) {
            for (CaseRequestDto.ReporterDto rd : request.getReporters()) {
                CaseReporter r = new CaseReporter();
                r.setPrimary(rd.isPrimary());
                r.setQualification(rd.getQualification() != null ? rd.getQualification() : "Physician");
                r.setTitle(rd.getTitle());
                r.setFirstName(rd.getFirstName());
                r.setLastName(rd.getLastName());
                r.setInstitution(rd.getInstitution());
                r.setDepartment(rd.getDepartment());
                r.setCity(rd.getCity());
                r.setStateProvince(rd.getStateProvince());
                r.setCountryCode(rd.getCountryCode() != null ? rd.getCountryCode().trim().toUpperCase() : "USA");
                r.setPostalCode(rd.getPostalCode());
                r.setEmail(rd.getEmail());
                r.setPhone(rd.getPhone());
                safetyCase.addReporter(r);
            }
        }
    }

    private void mapProducts(CaseRequestDto request, SafetyCase safetyCase) {
        if (request.getProducts() != null) {
            for (CaseRequestDto.ProductDto pd : request.getProducts()) {
                CaseProduct p = new CaseProduct();
                p.setProductType(pd.getProductType() != null ? pd.getProductType() : "Suspect");
                p.setProductName(pd.getProductName());
                p.setActiveSubstance(pd.getActiveSubstance());
                p.setIndication(pd.getIndication());
                p.setDosageText(pd.getDosageText());
                p.setDosageAmount(pd.getDosageAmount());
                p.setDosageUnit(pd.getDosageUnit());
                p.setFrequency(pd.getFrequency());
                p.setFormulation(pd.getFormulation());
                p.setRouteOfAdministration(pd.getRouteOfAdministration());
                p.setBatchLotNumber(pd.getBatchLotNumber());
                p.setStartDate(pd.getStartDate());
                p.setStopDate(pd.getStopDate());
                p.setOngoing(pd.getOngoing() != null ? pd.getOngoing() : false);
                p.setDechallengeResult(pd.getDechallengeResult() != null ? pd.getDechallengeResult() : "Not Applicable");
                p.setRechallengeResult(pd.getRechallengeResult() != null ? pd.getRechallengeResult() : "Not Applicable");
                safetyCase.addProduct(p);
            }
        }
    }

    private void mapEvents(CaseRequestDto request, SafetyCase safetyCase) {
        if (request.getEvents() != null) {
            for (CaseRequestDto.EventDto ed : request.getEvents()) {
                CaseEvent e = new CaseEvent();
                e.setEventVerbatim(ed.getEventVerbatim());
                e.setMeddraPt(ed.getMeddraPt());
                e.setMeddraLlt(ed.getMeddraLlt());
                e.setMeddraSoc(ed.getMeddraSoc());
                e.setOnsetDate(ed.getOnsetDate());
                e.setStopDate(ed.getStopDate());
                e.setOutcome(ed.getOutcome() != null ? ed.getOutcome() : "Unknown");
                e.setSerious(ed.isSerious());
                e.setResultsInDeath(ed.isResultsInDeath());
                e.setLifeThreatening(ed.isLifeThreatening());
                e.setRequiresHospitalization(ed.isRequiresHospitalization());
                e.setDisablingIncapacitating(ed.isDisablingIncapacitating());
                e.setCongenitalAnomaly(ed.isCongenitalAnomaly());
                e.setOtherMedicallyImportant(ed.isOtherMedicallyImportant());
                e.syncSeriousness();
                safetyCase.addEvent(e);
            }
        }
    }

    private void mapMedicalHistory(CaseRequestDto request, SafetyCase safetyCase) {
        if (request.getMedicalHistoryList() != null) {
            for (CaseRequestDto.MedicalHistoryDto md : request.getMedicalHistoryList()) {
                CaseMedicalHistory m = new CaseMedicalHistory();
                m.setConditionName(md.getConditionName());
                m.setStartDate(md.getStartDate());
                m.setEndDate(md.getEndDate());
                m.setContinuing(md.getContinuing() != null ? md.getContinuing() : false);
                m.setNotes(md.getNotes());
                safetyCase.addMedicalHistory(m);
            }
        }
    }

    private void mapReferences(CaseRequestDto request, SafetyCase safetyCase) {
        if (request.getReferences() != null) {
            for (CaseRequestDto.ReferenceDto rd : request.getReferences()) {
                CaseReference r = new CaseReference();
                r.setReferenceType(rd.getReferenceType() != null && !rd.getReferenceType().isBlank() ? rd.getReferenceType() : "Source Reference");
                String refId = rd.getReferenceId();
                if (refId == null || refId.isBlank()) {
                    refId = "REF-" + System.currentTimeMillis();
                }
                r.setReferenceId(refId);
                r.setDescription(rd.getDescription());
                safetyCase.addReference(r);
            }
        }
    }

    private void mapAttachments(CaseRequestDto request, SafetyCase safetyCase, User user) {
        if (request.getAttachments() != null) {
            for (CaseRequestDto.AttachmentDto ad : request.getAttachments()) {
                CaseAttachment a = new CaseAttachment();
                a.setFileName(ad.getFileName());
                a.setFileType(ad.getFileType());
                a.setFileSizeBytes(ad.getFileSizeBytes() != null ? ad.getFileSizeBytes() : 0L);
                a.setStoragePath(ad.getStoragePath() != null ? ad.getStoragePath() : "secure_store/" + ad.getFileName());
                a.setChecksumSha256(ad.getChecksumSha256() != null ? ad.getChecksumSha256() : "SHA256_UNVERIFIED");
                a.setClassification(ad.getClassification() != null ? ad.getClassification() : "Source Document");
                a.setUploadedByUser(user);
                safetyCase.addAttachment(a);
            }
        }
    }
}
