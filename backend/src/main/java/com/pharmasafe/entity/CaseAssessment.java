package com.pharmasafe.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "case_assessments")
public class CaseAssessment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id", nullable = false)
    @JsonIgnore
    private SafetyCase safetyCase;

    @Column(name = "product_name", length = 128)
    private String productName;

    @Column(name = "event_verbatim")
    private String eventVerbatim;

    @Column(name = "causality_reporter", length = 32)
    private String causalityReporter = "Unassessable";

    @Column(name = "causality_company", length = 32)
    private String causalityCompany = "Reasonable Possibility";

    @Column(name = "expectedness", length = 16)
    private String expectedness = "Unexpected";

    @Column(name = "assessment_comment", columnDefinition = "TEXT")
    private String assessmentComment;

    @Column(name = "assessed_by", length = 64)
    private String assessedBy;

    @Column(name = "assessed_at", nullable = false)
    private Instant assessedAt;

    public CaseAssessment() {
    }

    @PrePersist
    protected void onCreate() {
        if (this.assessedAt == null) {
            this.assessedAt = Instant.now();
        }
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public SafetyCase getSafetyCase() {
        return safetyCase;
    }

    public void setSafetyCase(SafetyCase safetyCase) {
        this.safetyCase = safetyCase;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getEventVerbatim() {
        return eventVerbatim;
    }

    public void setEventVerbatim(String eventVerbatim) {
        this.eventVerbatim = eventVerbatim;
    }

    public String getCausalityReporter() {
        return causalityReporter;
    }

    public void setCausalityReporter(String causalityReporter) {
        this.causalityReporter = causalityReporter;
    }

    public String getCausalityCompany() {
        return causalityCompany;
    }

    public void setCausalityCompany(String causalityCompany) {
        this.causalityCompany = causalityCompany;
    }

    public String getExpectedness() {
        return expectedness;
    }

    public void setExpectedness(String expectedness) {
        this.expectedness = expectedness;
    }

    public String getAssessmentComment() {
        return assessmentComment;
    }

    public void setAssessmentComment(String assessmentComment) {
        this.assessmentComment = assessmentComment;
    }

    public String getAssessedBy() {
        return assessedBy;
    }

    public void setAssessedBy(String assessedBy) {
        this.assessedBy = assessedBy;
    }

    public Instant getAssessedAt() {
        return assessedAt;
    }

    public void setAssessedAt(Instant assessedAt) {
        this.assessedAt = assessedAt;
    }
}
