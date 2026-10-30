package com.pharmasafe.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "case_review_events", indexes = {
        @Index(name = "idx_case_review_events_case_id", columnList = "case_id")
})
public class CaseReviewEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_id", nullable = false)
    private SafetyCase safetyCase;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_user_id")
    private User reviewerUser;

    @Column(name = "reviewer_username", nullable = false, length = 64)
    private String reviewerUsername;

    @Column(name = "from_state", nullable = false, length = 32)
    private String fromState;

    @Column(name = "to_state", nullable = false, length = 32)
    private String toState;

    @Column(nullable = false, length = 32)
    private String outcome;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String comments;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public SafetyCase getSafetyCase() { return safetyCase; }
    public void setSafetyCase(SafetyCase safetyCase) { this.safetyCase = safetyCase; }
    public User getReviewerUser() { return reviewerUser; }
    public void setReviewerUser(User reviewerUser) { this.reviewerUser = reviewerUser; }
    public String getReviewerUsername() { return reviewerUsername; }
    public void setReviewerUsername(String reviewerUsername) { this.reviewerUsername = reviewerUsername; }
    public String getFromState() { return fromState; }
    public void setFromState(String fromState) { this.fromState = fromState; }
    public String getToState() { return toState; }
    public void setToState(String toState) { this.toState = toState; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public String getComments() { return comments; }
    public void setComments(String comments) { this.comments = comments; }
    public Instant getCreatedAt() { return createdAt; }
}
