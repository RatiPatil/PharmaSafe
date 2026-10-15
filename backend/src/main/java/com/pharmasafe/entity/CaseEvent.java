package com.pharmasafe.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "case_events")
public class CaseEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id", nullable = false)
    @JsonIgnore
    private SafetyCase safetyCase;

    @Column(name = "event_verbatim", nullable = false)
    private String eventVerbatim;

    @Column(name = "meddra_pt", length = 128)
    private String meddraPt;

    @Column(name = "meddra_llt", length = 128)
    private String meddraLlt;

    @Column(name = "meddra_soc", length = 128)
    private String meddraSoc;

    @Column(name = "onset_date", nullable = false)
    private LocalDate onsetDate;

    @Column(name = "stop_date")
    private LocalDate stopDate;

    @Column(nullable = false, length = 32)
    private String outcome = "Unknown";

    @Column(name = "is_serious", nullable = false)
    private boolean serious = false;

    @Column(name = "results_in_death", nullable = false)
    private boolean resultsInDeath = false;

    @Column(name = "life_threatening", nullable = false)
    private boolean lifeThreatening = false;

    @Column(name = "requires_hospitalization", nullable = false)
    private boolean requiresHospitalization = false;

    @Column(name = "disabling_incapacitating", nullable = false)
    private boolean disablingIncapacitating = false;

    @Column(name = "congenital_anomaly", nullable = false)
    private boolean congenitalAnomaly = false;

    @Column(name = "other_medically_important", nullable = false)
    private boolean otherMedicallyImportant = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public CaseEvent() {
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
        if (resultsInDeath || lifeThreatening || requiresHospitalization ||
            disablingIncapacitating || congenitalAnomaly || otherMedicallyImportant) {
            this.serious = true;
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

    public String getEventVerbatim() {
        return eventVerbatim;
    }

    public void setEventVerbatim(String eventVerbatim) {
        this.eventVerbatim = eventVerbatim;
    }

    public String getMeddraPt() {
        return meddraPt;
    }

    public void setMeddraPt(String meddraPt) {
        this.meddraPt = meddraPt;
    }

    public String getMeddraLlt() {
        return meddraLlt;
    }

    public void setMeddraLlt(String meddraLlt) {
        this.meddraLlt = meddraLlt;
    }

    public String getMeddraSoc() {
        return meddraSoc;
    }

    public void setMeddraSoc(String meddraSoc) {
        this.meddraSoc = meddraSoc;
    }

    public LocalDate getOnsetDate() {
        return onsetDate;
    }

    public void setOnsetDate(LocalDate onsetDate) {
        this.onsetDate = onsetDate;
    }

    public LocalDate getStopDate() {
        return stopDate;
    }

    public void setStopDate(LocalDate stopDate) {
        this.stopDate = stopDate;
    }

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }

    public boolean isSerious() {
        return serious;
    }

    public void setSerious(boolean serious) {
        this.serious = serious;
    }

    public boolean isResultsInDeath() {
        return resultsInDeath;
    }

    public void setResultsInDeath(boolean resultsInDeath) {
        this.resultsInDeath = resultsInDeath;
    }

    public boolean isLifeThreatening() {
        return lifeThreatening;
    }

    public void setLifeThreatening(boolean lifeThreatening) {
        this.lifeThreatening = lifeThreatening;
    }

    public boolean isRequiresHospitalization() {
        return requiresHospitalization;
    }

    public void setRequiresHospitalization(boolean requiresHospitalization) {
        this.requiresHospitalization = requiresHospitalization;
    }

    public boolean isDisablingIncapacitating() {
        return disablingIncapacitating;
    }

    public void setDisablingIncapacitating(boolean disablingIncapacitating) {
        this.disablingIncapacitating = disablingIncapacitating;
    }

    public boolean isCongenitalAnomaly() {
        return congenitalAnomaly;
    }

    public void setCongenitalAnomaly(boolean congenitalAnomaly) {
        this.congenitalAnomaly = congenitalAnomaly;
    }

    public boolean isOtherMedicallyImportant() {
        return otherMedicallyImportant;
    }

    public void setOtherMedicallyImportant(boolean otherMedicallyImportant) {
        this.otherMedicallyImportant = otherMedicallyImportant;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
