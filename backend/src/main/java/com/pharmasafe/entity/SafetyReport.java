package com.pharmasafe.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "safety_reports", indexes = {
    @Index(name = "idx_safety_reports_report_id", columnList = "report_id", unique = true),
    @Index(name = "idx_safety_reports_due_date", columnList = "due_date"),
    @Index(name = "idx_safety_reports_status", columnList = "status"),
    @Index(name = "idx_safety_reports_product", columnList = "product"),
    @Index(name = "idx_safety_reports_type", columnList = "report_type")
})
public class SafetyReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_id", nullable = false, unique = true, length = 64)
    private String reportId;

    @Column(name = "report_type", nullable = false, length = 32)
    private String reportType; // '7-day', '15-day', 'PSUR', 'DSUR', 'PBRER', 'PADER'

    @Column(nullable = false, length = 128)
    private String product;

    @Column(nullable = false, length = 32)
    private String authority = "FDA";

    @Column(name = "data_lock_point")
    private LocalDate dataLockPoint;

    @Column(name = "period_start")
    private LocalDate periodStart;

    @Column(name = "period_end")
    private LocalDate periodEnd;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "submitted_date")
    private Instant submittedDate;

    @Column(nullable = false, length = 32)
    private String status = "Pending"; // 'Pending', 'Submitted', 'Overdue'

    @Column(name = "total_cases", nullable = false)
    private int totalCases = 0;

    @Column(name = "serious_cases", nullable = false)
    private int seriousCases = 0;

    @Column(name = "fatal_cases", nullable = false)
    private int fatalCases = 0;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(columnDefinition = "TEXT")
    private String conclusions;

    @Column(columnDefinition = "TEXT")
    private String recommendations;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdByUser;

    @ManyToMany
    @JoinTable(
        name = "safety_report_cases",
        joinColumns = @JoinColumn(name = "report_id"),
        inverseJoinColumns = @JoinColumn(name = "case_id")
    )
    private List<SafetyCase> relatedCases = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public SafetyReport() {
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getReportId() {
        return reportId;
    }

    public void setReportId(String reportId) {
        this.reportId = reportId;
    }

    public String getReportType() {
        return reportType;
    }

    public void setReportType(String reportType) {
        this.reportType = reportType;
    }

    public String getProduct() {
        return product;
    }

    public void setProduct(String product) {
        this.product = product;
    }

    public String getAuthority() {
        return authority;
    }

    public void setAuthority(String authority) {
        this.authority = authority;
    }

    public LocalDate getDataLockPoint() {
        return dataLockPoint;
    }

    public void setDataLockPoint(LocalDate dataLockPoint) {
        this.dataLockPoint = dataLockPoint;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public void setPeriodStart(LocalDate periodStart) {
        this.periodStart = periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public void setPeriodEnd(LocalDate periodEnd) {
        this.periodEnd = periodEnd;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public Instant getSubmittedDate() {
        return submittedDate;
    }

    public void setSubmittedDate(Instant submittedDate) {
        this.submittedDate = submittedDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getTotalCases() {
        return totalCases;
    }

    public void setTotalCases(int totalCases) {
        this.totalCases = totalCases;
    }

    public int getSeriousCases() {
        return seriousCases;
    }

    public void setSeriousCases(int seriousCases) {
        this.seriousCases = seriousCases;
    }

    public int getFatalCases() {
        return fatalCases;
    }

    public void setFatalCases(int fatalCases) {
        this.fatalCases = fatalCases;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getConclusions() {
        return conclusions;
    }

    public void setConclusions(String conclusions) {
        this.conclusions = conclusions;
    }

    public String getRecommendations() {
        return recommendations;
    }

    public void setRecommendations(String recommendations) {
        this.recommendations = recommendations;
    }

    public User getCreatedByUser() {
        return createdByUser;
    }

    public void setCreatedByUser(User createdByUser) {
        this.createdByUser = createdByUser;
    }

    public List<SafetyCase> getRelatedCases() {
        return relatedCases;
    }

    public void setRelatedCases(List<SafetyCase> relatedCases) {
        this.relatedCases = relatedCases;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
