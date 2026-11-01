package com.pharmasafe.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "case_action_items", indexes = {
        @Index(name = "idx_action_items_case", columnList = "case_id"),
        @Index(name = "idx_action_items_assignee_status_due", columnList = "assigned_user_id,status,due_at")
})
public class CaseActionItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name="action_code",nullable=false,length=32) private String actionCode="GENERAL";
    public String getActionCode() { return actionCode; }
    public void setActionCode(String value) { actionCode=value; }


    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_id", nullable = false)
    private SafetyCase safetyCase;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assigned_user_id", nullable = false)
    private User assignedUser;

    @Column(nullable = false, length = 16)
    private String status = "OPEN";

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdByUser;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "completed_by_user_id")
    private User completedByUser;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    public Long getId() { return id; }
    public SafetyCase getSafetyCase() { return safetyCase; }
    public void setSafetyCase(SafetyCase safetyCase) { this.safetyCase = safetyCase; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public User getAssignedUser() { return assignedUser; }
    public void setAssignedUser(User assignedUser) { this.assignedUser = assignedUser; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getDueAt() { return dueAt; }
    public void setDueAt(Instant dueAt) { this.dueAt = dueAt; }
    public User getCreatedByUser() { return createdByUser; }
    public void setCreatedByUser(User createdByUser) { this.createdByUser = createdByUser; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public int getVersionNumber() { return versionNumber; }
    public User getCompletedByUser() { return completedByUser; }
    public void setCompletedByUser(User completedByUser) { this.completedByUser = completedByUser; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
