package com.pharmasafe.dto;

import com.pharmasafe.entity.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class CaseResponseDto {

    private Long id;
    private String caseNumber;
    private String countryCode;
    private String reportType;
    private LocalDate initialReceiptDate;
    private LocalDate latestReceivedDate;
    private String workflowState;
    private String priority;
    private boolean serious;
    private boolean fatal;
    private String narrative;
    private String companyComments;
    private int versionNumber;
    private String createdByUsername;
    private String updatedByUsername;
    private String assignedToUsername;
    private boolean locked;
    private String lockedByUsername;
    private Instant lockedAt;
    private Instant createdAt;
    private Instant updatedAt;

    private PatientResponseDto patient;
    private List<ReporterResponseDto> reporters = new ArrayList<>();
    private List<ProductResponseDto> products = new ArrayList<>();
    private List<EventResponseDto> events = new ArrayList<>();
    private List<AssessmentResponseDto> assessments = new ArrayList<>();
    private List<MedicalHistoryResponseDto> medicalHistoryList = new ArrayList<>();
    private List<ReferenceResponseDto> references = new ArrayList<>();
    private List<AttachmentResponseDto> attachments = new ArrayList<>();

    public CaseResponseDto() {
    }

    public static CaseResponseDto fromEntity(SafetyCase c) {
        CaseResponseDto dto = new CaseResponseDto();
        dto.setId(c.getId());
        dto.setCaseNumber(c.getCaseNumber());
        dto.setCountryCode(c.getCountryCode());
        dto.setReportType(c.getReportType());
        dto.setInitialReceiptDate(c.getInitialReceiptDate());
        dto.setLatestReceivedDate(c.getLatestReceivedDate());
        dto.setWorkflowState(c.getWorkflowState());
        dto.setPriority(c.getPriority());
        dto.setSerious(c.isSerious());
        dto.setFatal(c.isFatal());
        dto.setNarrative(c.getNarrative());
        dto.setCompanyComments(c.getCompanyComments());
        dto.setVersionNumber(c.getVersionNumber());
        dto.setCreatedAt(c.getCreatedAt());
        dto.setUpdatedAt(c.getUpdatedAt());

        if (c.getCreatedByUser() != null) {
            dto.setCreatedByUsername(c.getCreatedByUser().getUsername());
        }
        if (c.getUpdatedByUser() != null) {
            dto.setUpdatedByUsername(c.getUpdatedByUser().getUsername());
        }
        if (c.getAssignedToUser() != null) {
            dto.setAssignedToUsername(c.getAssignedToUser().getUsername());
        }
        dto.setLocked(c.isLocked());
        if (c.getLockedByUser() != null) {
            dto.setLockedByUsername(c.getLockedByUser().getUsername());
        }
        dto.setLockedAt(c.getLockedAt());

        if (c.getPatient() != null) {
            PatientResponseDto p = new PatientResponseDto();
            p.setId(c.getPatient().getId());
            p.setPatientIdentifier(c.getPatient().getPatientIdentifier());
            p.setAgeValue(c.getPatient().getAgeValue());
            p.setAgeUnit(c.getPatient().getAgeUnit());
            p.setDateOfBirth(c.getPatient().getDateOfBirth());
            p.setGender(c.getPatient().getGender());
            p.setWeightKg(c.getPatient().getWeightKg());
            p.setHeightCm(c.getPatient().getHeightCm());
            p.setPregnant(c.getPatient().getPregnant());
            p.setMedicalHistory(c.getPatient().getMedicalHistory());
            dto.setPatient(p);
        }

        if (c.getReporters() != null) {
            for (CaseReporter r : c.getReporters()) {
                ReporterResponseDto rd = new ReporterResponseDto();
                rd.setId(r.getId());
                rd.setPrimary(r.isPrimary());
                rd.setQualification(r.getQualification());
                rd.setTitle(r.getTitle());
                rd.setFirstName(r.getFirstName());
                rd.setLastName(r.getLastName());
                rd.setInstitution(r.getInstitution());
                rd.setDepartment(r.getDepartment());
                rd.setCity(r.getCity());
                rd.setStateProvince(r.getStateProvince());
                rd.setCountryCode(r.getCountryCode());
                rd.setPostalCode(r.getPostalCode());
                rd.setEmail(r.getEmail());
                rd.setPhone(r.getPhone());
                dto.getReporters().add(rd);
            }
        }

        if (c.getProducts() != null) {
            for (CaseProduct p : c.getProducts()) {
                ProductResponseDto pd = new ProductResponseDto();
                pd.setId(p.getId());
                pd.setProductType(p.getProductType());
                pd.setProductName(p.getProductName());
                pd.setActiveSubstance(p.getActiveSubstance());
                pd.setIndication(p.getIndication());
                pd.setDosageText(p.getDosageText());
                pd.setDosageAmount(p.getDosageAmount());
                pd.setDosageUnit(p.getDosageUnit());
                pd.setFrequency(p.getFrequency());
                pd.setFormulation(p.getFormulation());
                pd.setRouteOfAdministration(p.getRouteOfAdministration());
                pd.setBatchLotNumber(p.getBatchLotNumber());
                pd.setStartDate(p.getStartDate());
                pd.setStopDate(p.getStopDate());
                pd.setOngoing(p.getOngoing());
                pd.setDechallengeResult(p.getDechallengeResult());
                pd.setRechallengeResult(p.getRechallengeResult());
                dto.getProducts().add(pd);
            }
        }

        if (c.getEvents() != null) {
            for (CaseEvent e : c.getEvents()) {
                EventResponseDto ed = new EventResponseDto();
                ed.setId(e.getId());
                ed.setEventVerbatim(e.getEventVerbatim());
                ed.setMeddraPt(e.getMeddraPt());
                ed.setMeddraLlt(e.getMeddraLlt());
                ed.setMeddraSoc(e.getMeddraSoc());
                ed.setOnsetDate(e.getOnsetDate());
                ed.setStopDate(e.getStopDate());
                ed.setOutcome(e.getOutcome());
                ed.setSerious(e.isSerious());
                ed.setResultsInDeath(e.isResultsInDeath());
                ed.setLifeThreatening(e.isLifeThreatening());
                ed.setRequiresHospitalization(e.isRequiresHospitalization());
                ed.setDisablingIncapacitating(e.isDisablingIncapacitating());
                ed.setCongenitalAnomaly(e.isCongenitalAnomaly());
                ed.setOtherMedicallyImportant(e.isOtherMedicallyImportant());
                dto.getEvents().add(ed);
            }
        }

        if (c.getAssessments() != null) {
            for (CaseAssessment a : c.getAssessments()) {
                AssessmentResponseDto ad = new AssessmentResponseDto();
                ad.setId(a.getId());
                ad.setProductName(a.getProductName());
                ad.setEventVerbatim(a.getEventVerbatim());
                ad.setCausalityReporter(a.getCausalityReporter());
                ad.setCausalityCompany(a.getCausalityCompany());
                ad.setExpectedness(a.getExpectedness());
                ad.setAssessmentComment(a.getAssessmentComment());
                ad.setAssessedBy(a.getAssessedBy());
                ad.setAssessedAt(a.getAssessedAt());
                dto.getAssessments().add(ad);
            }
        }

        if (c.getMedicalHistoryList() != null) {
            for (CaseMedicalHistory m : c.getMedicalHistoryList()) {
                MedicalHistoryResponseDto md = new MedicalHistoryResponseDto();
                md.setId(m.getId());
                md.setConditionName(m.getConditionName());
                md.setStartDate(m.getStartDate());
                md.setEndDate(m.getEndDate());
                md.setContinuing(m.getContinuing());
                md.setNotes(m.getNotes());
                dto.getMedicalHistoryList().add(md);
            }
        }

        if (c.getReferences() != null) {
            for (CaseReference r : c.getReferences()) {
                ReferenceResponseDto rd = new ReferenceResponseDto();
                rd.setId(r.getId());
                rd.setReferenceType(r.getReferenceType());
                rd.setReferenceId(r.getReferenceId());
                rd.setDescription(r.getDescription());
                dto.getReferences().add(rd);
            }
        }

        if (c.getAttachments() != null) {
            for (CaseAttachment a : c.getAttachments()) {
                AttachmentResponseDto ad = new AttachmentResponseDto();
                ad.setId(a.getId());
                ad.setFileName(a.getFileName());
                ad.setFileType(a.getFileType());
                ad.setFileSizeBytes(a.getFileSizeBytes());
                ad.setClassification(a.getClassification());
                ad.setUploadedAt(a.getUploadedAt());
                dto.getAttachments().add(ad);
            }
        }

        return dto;
    }

    // Nested Response DTOs
    public static class PatientResponseDto {
        private Long id;
        private String patientIdentifier;
        private BigDecimal ageValue;
        private String ageUnit;
        private LocalDate dateOfBirth;
        private String gender;
        private BigDecimal weightKg;
        private BigDecimal heightCm;
        private Boolean pregnant;
        private String medicalHistory;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getPatientIdentifier() { return patientIdentifier; }
        public void setPatientIdentifier(String patientIdentifier) { this.patientIdentifier = patientIdentifier; }
        public BigDecimal getAgeValue() { return ageValue; }
        public void setAgeValue(BigDecimal ageValue) { this.ageValue = ageValue; }
        public String getAgeUnit() { return ageUnit; }
        public void setAgeUnit(String ageUnit) { this.ageUnit = ageUnit; }
        public LocalDate getDateOfBirth() { return dateOfBirth; }
        public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
        public String getGender() { return gender; }
        public void setGender(String gender) { this.gender = gender; }
        public BigDecimal getWeightKg() { return weightKg; }
        public void setWeightKg(BigDecimal weightKg) { this.weightKg = weightKg; }
        public BigDecimal getHeightCm() { return heightCm; }
        public void setHeightCm(BigDecimal heightCm) { this.heightCm = heightCm; }
        public Boolean getPregnant() { return pregnant; }
        public void setPregnant(Boolean pregnant) { this.pregnant = pregnant; }
        public String getMedicalHistory() { return medicalHistory; }
        public void setMedicalHistory(String medicalHistory) { this.medicalHistory = medicalHistory; }
    }

    public static class ReporterResponseDto {
        private Long id;
        private boolean primary;
        private String qualification;
        private String title;
        private String firstName;
        private String lastName;
        private String institution;
        private String department;
        private String city;
        private String stateProvince;
        private String countryCode;
        private String postalCode;
        private String email;
        private String phone;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public boolean isPrimary() { return primary; }
        public void setPrimary(boolean primary) { this.primary = primary; }
        public String getQualification() { return qualification; }
        public void setQualification(String qualification) { this.qualification = qualification; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getFirstName() { return firstName; }
        public void setFirstName(String firstName) { this.firstName = firstName; }
        public String getLastName() { return lastName; }
        public void setLastName(String lastName) { this.lastName = lastName; }
        public String getInstitution() { return institution; }
        public void setInstitution(String institution) { this.institution = institution; }
        public String getDepartment() { return department; }
        public void setDepartment(String department) { this.department = department; }
        public String getCity() { return city; }
        public void setCity(String city) { this.city = city; }
        public String getStateProvince() { return stateProvince; }
        public void setStateProvince(String stateProvince) { this.stateProvince = stateProvince; }
        public String getCountryCode() { return countryCode; }
        public void setCountryCode(String countryCode) { this.countryCode = countryCode; }
        public String getPostalCode() { return postalCode; }
        public void setPostalCode(String postalCode) { this.postalCode = postalCode; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
    }

    public static class ProductResponseDto {
        private Long id;
        private String productType;
        private String productName;
        private String activeSubstance;
        private String indication;
        private String dosageText;
        private BigDecimal dosageAmount;
        private String dosageUnit;
        private String frequency;
        private String formulation;
        private String routeOfAdministration;
        private String batchLotNumber;
        private LocalDate startDate;
        private LocalDate stopDate;
        private Boolean ongoing;
        private String dechallengeResult;
        private String rechallengeResult;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getProductType() { return productType; }
        public void setProductType(String productType) { this.productType = productType; }
        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }
        public String getActiveSubstance() { return activeSubstance; }
        public void setActiveSubstance(String activeSubstance) { this.activeSubstance = activeSubstance; }
        public String getIndication() { return indication; }
        public void setIndication(String indication) { this.indication = indication; }
        public String getDosageText() { return dosageText; }
        public void setDosageText(String dosageText) { this.dosageText = dosageText; }
        public BigDecimal getDosageAmount() { return dosageAmount; }
        public void setDosageAmount(BigDecimal dosageAmount) { this.dosageAmount = dosageAmount; }
        public String getDosageUnit() { return dosageUnit; }
        public void setDosageUnit(String dosageUnit) { this.dosageUnit = dosageUnit; }
        public String getFrequency() { return frequency; }
        public void setFrequency(String frequency) { this.frequency = frequency; }
        public String getFormulation() { return formulation; }
        public void setFormulation(String formulation) { this.formulation = formulation; }
        public String getRouteOfAdministration() { return routeOfAdministration; }
        public void setRouteOfAdministration(String routeOfAdministration) { this.routeOfAdministration = routeOfAdministration; }
        public String getBatchLotNumber() { return batchLotNumber; }
        public void setBatchLotNumber(String batchLotNumber) { this.batchLotNumber = batchLotNumber; }
        public LocalDate getStartDate() { return startDate; }
        public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
        public LocalDate getStopDate() { return stopDate; }
        public void setStopDate(LocalDate stopDate) { this.stopDate = stopDate; }
        public Boolean getOngoing() { return ongoing; }
        public void setOngoing(Boolean ongoing) { this.ongoing = ongoing; }
        public String getDechallengeResult() { return dechallengeResult; }
        public void setDechallengeResult(String dechallengeResult) { this.dechallengeResult = dechallengeResult; }
        public String getRechallengeResult() { return rechallengeResult; }
        public void setRechallengeResult(String rechallengeResult) { this.rechallengeResult = rechallengeResult; }
    }

    public static class EventResponseDto {
        private Long id;
        private String eventVerbatim;
        private String meddraPt;
        private String meddraLlt;
        private String meddraSoc;
        private LocalDate onsetDate;
        private LocalDate stopDate;
        private String outcome;
        private boolean serious;
        private boolean resultsInDeath;
        private boolean lifeThreatening;
        private boolean requiresHospitalization;
        private boolean disablingIncapacitating;
        private boolean congenitalAnomaly;
        private boolean otherMedicallyImportant;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getEventVerbatim() { return eventVerbatim; }
        public void setEventVerbatim(String eventVerbatim) { this.eventVerbatim = eventVerbatim; }
        public String getMeddraPt() { return meddraPt; }
        public void setMeddraPt(String meddraPt) { this.meddraPt = meddraPt; }
        public String getMeddraLlt() { return meddraLlt; }
        public void setMeddraLlt(String meddraLlt) { this.meddraLlt = meddraLlt; }
        public String getMeddraSoc() { return meddraSoc; }
        public void setMeddraSoc(String meddraSoc) { this.meddraSoc = meddraSoc; }
        public LocalDate getOnsetDate() { return onsetDate; }
        public void setOnsetDate(LocalDate onsetDate) { this.onsetDate = onsetDate; }
        public LocalDate getStopDate() { return stopDate; }
        public void setStopDate(LocalDate stopDate) { this.stopDate = stopDate; }
        public String getOutcome() { return outcome; }
        public void setOutcome(String outcome) { this.outcome = outcome; }
        public boolean isSerious() { return serious; }
        public void setSerious(boolean serious) { this.serious = serious; }
        public boolean isResultsInDeath() { return resultsInDeath; }
        public void setResultsInDeath(boolean resultsInDeath) { this.resultsInDeath = resultsInDeath; }
        public boolean isLifeThreatening() { return lifeThreatening; }
        public void setLifeThreatening(boolean lifeThreatening) { this.lifeThreatening = lifeThreatening; }
        public boolean isRequiresHospitalization() { return requiresHospitalization; }
        public void setRequiresHospitalization(boolean requiresHospitalization) { this.requiresHospitalization = requiresHospitalization; }
        public boolean isDisablingIncapacitating() { return disablingIncapacitating; }
        public void setDisablingIncapacitating(boolean disablingIncapacitating) { this.disablingIncapacitating = disablingIncapacitating; }
        public boolean isCongenitalAnomaly() { return congenitalAnomaly; }
        public void setCongenitalAnomaly(boolean congenitalAnomaly) { this.congenitalAnomaly = congenitalAnomaly; }
        public boolean isOtherMedicallyImportant() { return otherMedicallyImportant; }
        public void setOtherMedicallyImportant(boolean otherMedicallyImportant) { this.otherMedicallyImportant = otherMedicallyImportant; }
    }

    public static class AssessmentResponseDto {
        private Long id;
        private String productName;
        private String eventVerbatim;
        private String causalityReporter;
        private String causalityCompany;
        private String expectedness;
        private String assessmentComment;
        private String assessedBy;
        private Instant assessedAt;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }
        public String getEventVerbatim() { return eventVerbatim; }
        public void setEventVerbatim(String eventVerbatim) { this.eventVerbatim = eventVerbatim; }
        public String getCausalityReporter() { return causalityReporter; }
        public void setCausalityReporter(String causalityReporter) { this.causalityReporter = causalityReporter; }
        public String getCausalityCompany() { return causalityCompany; }
        public void setCausalityCompany(String causalityCompany) { this.causalityCompany = causalityCompany; }
        public String getExpectedness() { return expectedness; }
        public void setExpectedness(String expectedness) { this.expectedness = expectedness; }
        public String getAssessmentComment() { return assessmentComment; }
        public void setAssessmentComment(String assessmentComment) { this.assessmentComment = assessmentComment; }
        public String getAssessedBy() { return assessedBy; }
        public void setAssessedBy(String assessedBy) { this.assessedBy = assessedBy; }
        public Instant getAssessedAt() { return assessedAt; }
        public void setAssessedAt(Instant assessedAt) { this.assessedAt = assessedAt; }
    }

    public static class MedicalHistoryResponseDto {
        private Long id;
        private String conditionName;
        private LocalDate startDate;
        private LocalDate endDate;
        private Boolean continuing;
        private String notes;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getConditionName() { return conditionName; }
        public void setConditionName(String conditionName) { this.conditionName = conditionName; }
        public LocalDate getStartDate() { return startDate; }
        public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
        public LocalDate getEndDate() { return endDate; }
        public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
        public Boolean getContinuing() { return continuing; }
        public void setContinuing(Boolean continuing) { this.continuing = continuing; }
        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
    }

    public static class ReferenceResponseDto {
        private Long id;
        private String referenceType;
        private String referenceId;
        private String description;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getReferenceType() { return referenceType; }
        public void setReferenceType(String referenceType) { this.referenceType = referenceType; }
        public String getReferenceId() { return referenceId; }
        public void setReferenceId(String referenceId) { this.referenceId = referenceId; }
        public String getReferenceNumber() { return referenceId; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    public static class AttachmentResponseDto {
        private Long id;
        private String fileName;
        private String fileType;
        private Long fileSizeBytes;
        private String classification;
        private Instant uploadedAt;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        public String getFileType() { return fileType; }
        public void setFileType(String fileType) { this.fileType = fileType; }
        public Long getFileSizeBytes() { return fileSizeBytes; }
        public void setFileSizeBytes(Long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }
        public String getClassification() { return classification; }
        public void setClassification(String classification) { this.classification = classification; }
        public Instant getUploadedAt() { return uploadedAt; }
        public void setUploadedAt(Instant uploadedAt) { this.uploadedAt = uploadedAt; }
    }

    // Getters and Setters for CaseResponseDto
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCaseNumber() { return caseNumber; }
    public void setCaseNumber(String caseNumber) { this.caseNumber = caseNumber; }
    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }
    public String getReportType() { return reportType; }
    public void setReportType(String reportType) { this.reportType = reportType; }
    public LocalDate getInitialReceiptDate() { return initialReceiptDate; }
    public void setInitialReceiptDate(LocalDate initialReceiptDate) { this.initialReceiptDate = initialReceiptDate; }
    public LocalDate getLatestReceivedDate() { return latestReceivedDate; }
    public void setLatestReceivedDate(LocalDate latestReceivedDate) { this.latestReceivedDate = latestReceivedDate; }
    public String getWorkflowState() { return workflowState; }
    public void setWorkflowState(String workflowState) { this.workflowState = workflowState; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public boolean isSerious() { return serious; }
    public void setSerious(boolean serious) { this.serious = serious; }
    public boolean isFatal() { return fatal; }
    public void setFatal(boolean fatal) { this.fatal = fatal; }
    public String getNarrative() { return narrative; }
    public void setNarrative(String narrative) { this.narrative = narrative; }
    public String getCompanyComments() { return companyComments; }
    public void setCompanyComments(String companyComments) { this.companyComments = companyComments; }
    public int getVersionNumber() { return versionNumber; }
    public void setVersionNumber(int versionNumber) { this.versionNumber = versionNumber; }
    public String getCreatedByUsername() { return createdByUsername; }
    public void setCreatedByUsername(String createdByUsername) { this.createdByUsername = createdByUsername; }
    public String getUpdatedByUsername() { return updatedByUsername; }
    public void setUpdatedByUsername(String updatedByUsername) { this.updatedByUsername = updatedByUsername; }
    public String getAssignedToUsername() { return assignedToUsername; }
    public void setAssignedToUsername(String assignedToUsername) { this.assignedToUsername = assignedToUsername; }
    public boolean isLocked() { return locked; }
    public void setLocked(boolean locked) { this.locked = locked; }
    public String getLockedByUsername() { return lockedByUsername; }
    public void setLockedByUsername(String lockedByUsername) { this.lockedByUsername = lockedByUsername; }
    public Instant getLockedAt() { return lockedAt; }
    public void setLockedAt(Instant lockedAt) { this.lockedAt = lockedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public PatientResponseDto getPatient() { return patient; }
    public void setPatient(PatientResponseDto patient) { this.patient = patient; }
    public List<ReporterResponseDto> getReporters() { return reporters; }
    public void setReporters(List<ReporterResponseDto> reporters) { this.reporters = reporters; }
    public List<ProductResponseDto> getProducts() { return products; }
    public void setProducts(List<ProductResponseDto> products) { this.products = products; }
    public List<EventResponseDto> getEvents() { return events; }
    public void setEvents(List<EventResponseDto> events) { this.events = events; }
    public List<AssessmentResponseDto> getAssessments() { return assessments; }
    public void setAssessments(List<AssessmentResponseDto> assessments) { this.assessments = assessments; }
    public List<MedicalHistoryResponseDto> getMedicalHistoryList() { return medicalHistoryList; }
    public void setMedicalHistoryList(List<MedicalHistoryResponseDto> medicalHistoryList) { this.medicalHistoryList = medicalHistoryList; }
    public List<ReferenceResponseDto> getReferences() { return references; }
    public void setReferences(List<ReferenceResponseDto> references) { this.references = references; }
    public List<AttachmentResponseDto> getAttachments() { return attachments; }
    public void setAttachments(List<AttachmentResponseDto> attachments) { this.attachments = attachments; }
}
