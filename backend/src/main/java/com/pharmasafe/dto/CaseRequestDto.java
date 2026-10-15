package com.pharmasafe.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class CaseRequestDto {

    @NotBlank(message = "Country code is required")
    @Size(min = 2, max = 3, message = "Country code must be 2 or 3 characters (e.g., USA, GBR)")
    private String countryCode = "USA";

    @NotBlank(message = "Report type is required")
    private String reportType = "Spontaneous";

    @NotNull(message = "Initial receipt date is required")
    private LocalDate initialReceiptDate;

    @NotNull(message = "Latest received date is required")
    private LocalDate latestReceivedDate;

    private String workflowState = "Data Entry";

    @Pattern(regexp = "Routine|Priority|Expedited", message = "Priority must be Routine, Priority, or Expedited")
    private String priority = "Routine";

    private boolean draft = false;

    private String narrative;

    private String companyComments;

    private String assignedToUsername;

    private PatientDto patient;

    private List<ReporterDto> reporters = new ArrayList<>();

    private List<ProductDto> products = new ArrayList<>();

    private List<EventDto> events = new ArrayList<>();

    private List<AssessmentDto> assessments = new ArrayList<>();

    private List<MedicalHistoryDto> medicalHistoryList = new ArrayList<>();

    private List<ReferenceDto> references = new ArrayList<>();

    private List<AttachmentDto> attachments = new ArrayList<>();

    public CaseRequestDto() {
    }

    // Nested DTOs
    public static class PatientDto {
        private String patientIdentifier;
        private BigDecimal ageValue;
        private String ageUnit = "Years";
        private LocalDate dateOfBirth;
        private String gender = "Unknown";
        private BigDecimal weightKg;
        private BigDecimal heightCm;
        private Boolean pregnant = false;
        private String medicalHistory;

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

    public static class ReporterDto {
        private boolean primary = true;
        private String qualification = "Physician";
        private String title;
        private String firstName;
        private String lastName;
        private String institution;
        private String department;
        private String city;
        private String stateProvince;
        private String countryCode = "USA";
        private String postalCode;
        private String email;
        private String phone;

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

    public static class ProductDto {
        private String productType = "Suspect";
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
        private Boolean ongoing = false;
        private String dechallengeResult = "Not Applicable";
        private String rechallengeResult = "Not Applicable";

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

    public static class EventDto {
        private String eventVerbatim;
        private String meddraPt;
        private String meddraLlt;
        private String meddraSoc;
        private LocalDate onsetDate;
        private LocalDate stopDate;
        private String outcome = "Unknown";
        private boolean serious = false;
        private boolean resultsInDeath = false;
        private boolean lifeThreatening = false;
        private boolean requiresHospitalization = false;
        private boolean disablingIncapacitating = false;
        private boolean congenitalAnomaly = false;
        private boolean otherMedicallyImportant = false;

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

    public static class AssessmentDto {
        private String productName;
        private String eventVerbatim;
        private String causalityReporter = "Unassessable";
        private String causalityCompany = "Reasonable Possibility";
        private String expectedness = "Unexpected";
        private String assessmentComment;
        private String assessedBy;

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
    }

    public static class MedicalHistoryDto {
        private String conditionName;
        private LocalDate startDate;
        private LocalDate endDate;
        private Boolean continuing = false;
        private String notes;

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

    public static class ReferenceDto {
        private String referenceType = "Source Reference";
        private String referenceId;
        private String description;

        public String getReferenceType() { return referenceType; }
        public void setReferenceType(String referenceType) { this.referenceType = referenceType; }
        public String getReferenceId() { return referenceId; }
        public void setReferenceId(String referenceId) { this.referenceId = referenceId; }
        public String getReferenceNumber() { return referenceId; }
        public void setReferenceNumber(String referenceNumber) {
            if (this.referenceId == null || this.referenceId.isBlank()) {
                this.referenceId = referenceNumber;
            }
        }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    public static class AttachmentDto {
        private String fileName;
        private String fileType;
        private Long fileSizeBytes;
        private String storagePath;
        private String checksumSha256;
        private String classification = "Source Document";

        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        public String getFileType() { return fileType; }
        public void setFileType(String fileType) { this.fileType = fileType; }
        public Long getFileSizeBytes() { return fileSizeBytes; }
        public void setFileSizeBytes(Long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }
        public String getStoragePath() { return storagePath; }
        public void setStoragePath(String storagePath) { this.storagePath = storagePath; }
        public String getChecksumSha256() { return checksumSha256; }
        public void setChecksumSha256(String checksumSha256) { this.checksumSha256 = checksumSha256; }
        public String getClassification() { return classification; }
        public void setClassification(String classification) { this.classification = classification; }
    }

    // Getters and Setters for CaseRequestDto
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
    public boolean isDraft() { return draft; }
    public void setDraft(boolean draft) { this.draft = draft; }
    public String getNarrative() { return narrative; }
    public void setNarrative(String narrative) { this.narrative = narrative; }
    public String getCompanyComments() { return companyComments; }
    public void setCompanyComments(String companyComments) { this.companyComments = companyComments; }
    public String getAssignedToUsername() { return assignedToUsername; }
    public void setAssignedToUsername(String assignedToUsername) { this.assignedToUsername = assignedToUsername; }
    public PatientDto getPatient() { return patient; }
    public void setPatient(PatientDto patient) { this.patient = patient; }
    public List<ReporterDto> getReporters() { return reporters; }
    public void setReporters(List<ReporterDto> reporters) { this.reporters = reporters; }
    public List<ProductDto> getProducts() { return products; }
    public void setProducts(List<ProductDto> products) { this.products = products; }
    public List<EventDto> getEvents() { return events; }
    public void setEvents(List<EventDto> events) { this.events = events; }
    public List<AssessmentDto> getAssessments() { return assessments; }
    public void setAssessments(List<AssessmentDto> assessments) { this.assessments = assessments; }
    public List<MedicalHistoryDto> getMedicalHistoryList() { return medicalHistoryList; }
    public void setMedicalHistoryList(List<MedicalHistoryDto> medicalHistoryList) { this.medicalHistoryList = medicalHistoryList; }
    public List<ReferenceDto> getReferences() { return references; }
    public void setReferences(List<ReferenceDto> references) { this.references = references; }
    public List<AttachmentDto> getAttachments() { return attachments; }
    public void setAttachments(List<AttachmentDto> attachments) { this.attachments = attachments; }
}
