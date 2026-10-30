package com.pharmasafe.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "case_contact_logs", indexes = {
        @Index(name = "idx_contact_logs_case", columnList = "case_id"),
        @Index(name = "idx_contact_logs_responsible_user", columnList = "responsible_user_id")
})
public class CaseContactLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_id", nullable = false)
    private SafetyCase safetyCase;

    @Column(name = "contact_at", nullable = false)
    private Instant contactAt;

    @Column(name = "contact_type", nullable = false, length = 32)
    private String contactType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responsible_user_id")
    private User responsibleUser;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdByUser;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by_user_id")
    private User updatedByUser;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public SafetyCase getSafetyCase() { return safetyCase; }
    public void setSafetyCase(SafetyCase safetyCase) { this.safetyCase = safetyCase; }
    public Instant getContactAt() { return contactAt; }
    public void setContactAt(Instant contactAt) { this.contactAt = contactAt; }
    public String getContactType() { return contactType; }
    public void setContactType(String contactType) { this.contactType = contactType; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public User getResponsibleUser() { return responsibleUser; }
    public void setResponsibleUser(User responsibleUser) { this.responsibleUser = responsibleUser; }
    public User getCreatedByUser() { return createdByUser; }
    public void setCreatedByUser(User createdByUser) { this.createdByUser = createdByUser; }
    public Instant getCreatedAt() { return createdAt; }
    public User getUpdatedByUser() { return updatedByUser; }
    public void setUpdatedByUser(User updatedByUser) { this.updatedByUser = updatedByUser; }
    public Instant getUpdatedAt() { return updatedAt; }
}
