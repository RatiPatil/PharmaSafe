package com.pharmasafe.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "cases", indexes = {
    @Index(name = "idx_cases_case_number", columnList = "case_number", unique = true),
    @Index(name = "idx_cases_country_code", columnList = "country_code"),
    @Index(name = "idx_cases_workflow_state", columnList = "workflow_state"),
    @Index(name = "idx_cases_latest_received_date", columnList = "latest_received_date")
})
public class SafetyCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "case_number", nullable = false, unique = true, length = 64)
    private String caseNumber;

    @Column(name = "country_code", nullable = false, length = 3)
    private String countryCode = "USA";

    @Column(name = "report_type", nullable = false, length = 32)
    private String reportType = "Spontaneous";

    @Column(name = "initial_receipt_date", nullable = false)
    private LocalDate initialReceiptDate;

    @Column(name = "latest_received_date", nullable = false)
    private LocalDate latestReceivedDate;

    @Column(name = "workflow_state", nullable = false, length = 32)
    private String workflowState = "Data Entry";

    @Column(name = "priority", nullable = false, length = 16)
    private String priority = "Routine";

    @Column(name = "is_serious", nullable = false)
    private boolean serious = false;

    @Column(name = "is_fatal", nullable = false)
    private boolean fatal = false;

    @Column(columnDefinition = "TEXT")
    private String narrative;

    @Column(name = "company_comments", columnDefinition = "TEXT")
    private String companyComments;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    @Column(name = "version_number", nullable = false)
    private int versionNumber = 1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdByUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by_user_id")
    private User updatedByUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to_user_id")
    private User assignedToUser;

    @Column(nullable = false)
    private boolean locked = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "locked_by_user_id")
    private User lockedByUser;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @OneToOne(mappedBy = "safetyCase", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private CasePatient patient;

    @OneToMany(mappedBy = "safetyCase", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CaseReporter> reporters = new ArrayList<>();

    @OneToMany(mappedBy = "safetyCase", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CaseProduct> products = new ArrayList<>();

    @OneToMany(mappedBy = "safetyCase", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CaseEvent> events = new ArrayList<>();

    @OneToMany(mappedBy = "safetyCase", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CaseAssessment> assessments = new ArrayList<>();

    @OneToMany(mappedBy = "safetyCase", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CaseMedicalHistory> medicalHistoryList = new ArrayList<>();

    @OneToMany(mappedBy = "safetyCase", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CaseReference> references = new ArrayList<>();

    @OneToMany(mappedBy = "safetyCase", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CaseAttachment> attachments = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public SafetyCase() {
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        syncSeriousness();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
        syncSeriousness();
    }

    public void syncSeriousness() {
        boolean anySerious = false;
        boolean anyFatal = false;
        if (events != null) {
            for (CaseEvent ev : events) {
                if (ev.isSerious()) {
                    anySerious = true;
                }
                if (ev.isResultsInDeath() || "Fatal".equalsIgnoreCase(ev.getOutcome())) {
                    anyFatal = true;
                    anySerious = true;
                }
            }
        }
        this.serious = anySerious;
        this.fatal = anyFatal;
    }

    // Helper relationship methods
    public void setPatient(CasePatient patient) {
        this.patient = patient;
        if (patient != null) {
            patient.setSafetyCase(this);
        }
    }

    public void addReporter(CaseReporter reporter) {
        reporters.add(reporter);
        reporter.setSafetyCase(this);
    }

    public void addProduct(CaseProduct product) {
        products.add(product);
        product.setSafetyCase(this);
    }

    public void addEvent(CaseEvent event) {
        events.add(event);
        event.setSafetyCase(this);
        syncSeriousness();
    }

    public void addAssessment(CaseAssessment assessment) {
        assessments.add(assessment);
        assessment.setSafetyCase(this);
    }

    public void addMedicalHistory(CaseMedicalHistory history) {
        medicalHistoryList.add(history);
        history.setSafetyCase(this);
    }

    public void addReference(CaseReference ref) {
        references.add(ref);
        ref.setSafetyCase(this);
    }

    public void addAttachment(CaseAttachment att) {
        attachments.add(att);
        att.setSafetyCase(this);
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCaseNumber() {
        return caseNumber;
    }

    public void setCaseNumber(String caseNumber) {
        this.caseNumber = caseNumber;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public String getReportType() {
        return reportType;
    }

    public void setReportType(String reportType) {
        this.reportType = reportType;
    }

    public LocalDate getInitialReceiptDate() {
        return initialReceiptDate;
    }

    public void setInitialReceiptDate(LocalDate initialReceiptDate) {
        this.initialReceiptDate = initialReceiptDate;
    }

    public LocalDate getLatestReceivedDate() {
        return latestReceivedDate;
    }

    public void setLatestReceivedDate(LocalDate latestReceivedDate) {
        this.latestReceivedDate = latestReceivedDate;
    }

    public String getWorkflowState() {
        return workflowState;
    }

    public void setWorkflowState(String workflowState) {
        this.workflowState = workflowState;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public boolean isSerious() {
        return serious;
    }

    public void setSerious(boolean serious) {
        this.serious = serious;
    }

    public boolean isFatal() {
        return fatal;
    }

    public void setFatal(boolean fatal) {
        this.fatal = fatal;
    }

    public String getNarrative() {
        return narrative;
    }

    public void setNarrative(String narrative) {
        this.narrative = narrative;
    }

    public String getCompanyComments() {
        return companyComments;
    }

    public void setCompanyComments(String companyComments) {
        this.companyComments = companyComments;
    }

    public int getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(int versionNumber) {
        this.versionNumber = versionNumber;
    }

    public User getCreatedByUser() {
        return createdByUser;
    }

    public void setCreatedByUser(User createdByUser) {
        this.createdByUser = createdByUser;
    }

    public User getUpdatedByUser() {
        return updatedByUser;
    }

    public void setUpdatedByUser(User updatedByUser) {
        this.updatedByUser = updatedByUser;
    }

    public User getAssignedToUser() {
        return assignedToUser;
    }

    public void setAssignedToUser(User assignedToUser) {
        this.assignedToUser = assignedToUser;
    }

    public CasePatient getPatient() {
        return patient;
    }

    public List<CaseReporter> getReporters() {
        return reporters;
    }

    public void setReporters(List<CaseReporter> reporters) {
        this.reporters = reporters;
    }

    public List<CaseProduct> getProducts() {
        return products;
    }

    public void setProducts(List<CaseProduct> products) {
        this.products = products;
    }

    public List<CaseEvent> getEvents() {
        return events;
    }

    public void setEvents(List<CaseEvent> events) {
        this.events = events;
    }

    public List<CaseAssessment> getAssessments() {
        return assessments;
    }

    public void setAssessments(List<CaseAssessment> assessments) {
        this.assessments = assessments;
    }

    public List<CaseMedicalHistory> getMedicalHistoryList() {
        return medicalHistoryList;
    }

    public void setMedicalHistoryList(List<CaseMedicalHistory> medicalHistoryList) {
        this.medicalHistoryList = medicalHistoryList;
    }

    public List<CaseReference> getReferences() {
        return references;
    }

    public void setReferences(List<CaseReference> references) {
        this.references = references;
    }

    public List<CaseAttachment> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<CaseAttachment> attachments) {
        this.attachments = attachments;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public User getLockedByUser() {
        return lockedByUser;
    }

    public void setLockedByUser(User lockedByUser) {
        this.lockedByUser = lockedByUser;
    }

    public Instant getLockedAt() {
        return lockedAt;
    }

    public void setLockedAt(Instant lockedAt) {
        this.lockedAt = lockedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
