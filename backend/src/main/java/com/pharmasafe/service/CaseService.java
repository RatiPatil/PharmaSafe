package com.pharmasafe.service;

import com.pharmasafe.dto.CaseRequestDto;
import com.pharmasafe.dto.CaseResponseDto;
import com.pharmasafe.dto.CaseSummaryDto;
import com.pharmasafe.entity.*;
import com.pharmasafe.repository.SafetyCaseRepository;
import com.pharmasafe.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CaseService {

    private static final Logger log = LoggerFactory.getLogger(CaseService.class);

    private final SafetyCaseRepository safetyCaseRepository;
    private final UserRepository userRepository;
    private final CaseNumberGeneratorService caseNumberGeneratorService;
    private final AuditService auditService;

    public CaseService(SafetyCaseRepository safetyCaseRepository,
                       UserRepository userRepository,
                       CaseNumberGeneratorService caseNumberGeneratorService,
                       AuditService auditService) {
        this.safetyCaseRepository = safetyCaseRepository;
        this.userRepository = userRepository;
        this.caseNumberGeneratorService = caseNumberGeneratorService;
        this.auditService = auditService;
    }

    @Transactional
    public CaseResponseDto createCase(CaseRequestDto request, String username, String clientIp, String userAgent) {
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
        safetyCase.setWorkflowState(request.isDraft() ? "Draft" : (request.getWorkflowState() != null ? request.getWorkflowState() : "Data Entry"));
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

        // Map Assessments
        mapAssessments(request, safetyCase);

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
        auditService.logEvent("CASE_CREATED", username, clientIp, userAgent, "SUCCESS", auditDetails);
        log.info("Safety case {} created successfully by user '{}'", savedCase.getCaseNumber(), username);

        return CaseResponseDto.fromEntity(savedCase);
    }

    @Transactional
    public CaseResponseDto updateCase(Long id, CaseRequestDto request, String username, String clientIp, String userAgent) {
        SafetyCase safetyCase = safetyCaseRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Safety case not found with ID: " + id));

        validateCaseDates(request);

        if (!request.isDraft()) {
            validateMinimumCaseCriteria(request);
        }

        User user = null;
        if (username != null) {
            user = userRepository.findByUsername(username.trim().toLowerCase()).orElse(null);
        }

        safetyCase.setCountryCode(request.getCountryCode() != null ? request.getCountryCode().trim().toUpperCase() : safetyCase.getCountryCode());
        safetyCase.setReportType(request.getReportType());
        safetyCase.setInitialReceiptDate(request.getInitialReceiptDate());
        safetyCase.setLatestReceivedDate(request.getLatestReceivedDate());

        // Update workflow state if progressing from draft
        if ("Draft".equalsIgnoreCase(safetyCase.getWorkflowState()) && !request.isDraft()) {
            safetyCase.setWorkflowState("Data Entry");
        } else if (request.getWorkflowState() != null) {
            safetyCase.setWorkflowState(request.getWorkflowState());
        }

        safetyCase.setPriority(request.getPriority() != null ? request.getPriority() : safetyCase.getPriority());
        safetyCase.setNarrative(request.getNarrative());
        safetyCase.setCompanyComments(request.getCompanyComments());
        safetyCase.setVersionNumber(safetyCase.getVersionNumber() + 1);
        safetyCase.setUpdatedByUser(user);

        if (request.getAssignedToUsername() != null && !request.getAssignedToUsername().trim().isEmpty()) {
            User assignedUser = userRepository.findByUsername(request.getAssignedToUsername().trim().toLowerCase()).orElse(null);
            if (assignedUser != null) {
                safetyCase.setAssignedToUser(assignedUser);
            }
        }

        // Clear existing children and re-populate
        mapPatient(request, safetyCase);

        safetyCase.getReporters().clear();
        mapReporters(request, safetyCase);

        safetyCase.getProducts().clear();
        mapProducts(request, safetyCase);

        safetyCase.getEvents().clear();
        mapEvents(request, safetyCase);

        safetyCase.getAssessments().clear();
        mapAssessments(request, safetyCase);

        safetyCase.getMedicalHistoryList().clear();
        mapMedicalHistory(request, safetyCase);

        safetyCase.getReferences().clear();
        mapReferences(request, safetyCase);

        safetyCase.syncSeriousness();
        SafetyCase updatedCase = safetyCaseRepository.saveAndFlush(safetyCase);

        // Audit Logging
        String auditDetails = String.format("Updated safety case %s to version %d (State: %s, Serious: %s)",
                updatedCase.getCaseNumber(), updatedCase.getVersionNumber(), updatedCase.getWorkflowState(), updatedCase.isSerious());
        auditService.logEvent("CASE_UPDATED", username, clientIp, userAgent, "SUCCESS", auditDetails);
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
    public CaseResponseDto getCaseByCaseNumber(String caseNumber) {
        if (caseNumber == null || caseNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("Case number cannot be empty");
        }
        SafetyCase safetyCase = safetyCaseRepository.findByCaseNumberIgnoreCase(caseNumber.trim())
                .orElseThrow(() -> new EntityNotFoundException("Safety case not found with number: " + caseNumber.trim()));
        return CaseResponseDto.fromEntity(safetyCase);
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
    public List<CaseSummaryDto> getAllCases() {
        return safetyCaseRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(CaseSummaryDto::fromEntity)
                .collect(Collectors.toList());
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

    private void mapAssessments(CaseRequestDto request, SafetyCase safetyCase) {
        if (request.getAssessments() != null) {
            for (CaseRequestDto.AssessmentDto ad : request.getAssessments()) {
                CaseAssessment a = new CaseAssessment();
                a.setProductName(ad.getProductName());
                a.setEventVerbatim(ad.getEventVerbatim());
                a.setCausalityReporter(ad.getCausalityReporter() != null ? ad.getCausalityReporter() : "Unassessable");
                a.setCausalityCompany(ad.getCausalityCompany() != null ? ad.getCausalityCompany() : "Reasonable Possibility");
                a.setExpectedness(ad.getExpectedness() != null ? ad.getExpectedness() : "Unexpected");
                a.setAssessmentComment(ad.getAssessmentComment());
                a.setAssessedBy(ad.getAssessedBy());
                safetyCase.addAssessment(a);
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
