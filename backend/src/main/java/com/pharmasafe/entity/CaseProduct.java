package com.pharmasafe.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "case_products")
public class CaseProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id", nullable = false)
    @JsonIgnore
    private SafetyCase safetyCase;

    @Column(name = "product_type", nullable = false, length = 16)
    private String productType = "Suspect";

    @Column(name = "product_name", nullable = false, length = 128)
    private String productName;

    @Column(name = "active_substance", length = 128)
    private String activeSubstance;

    @Column(length = 255)
    private String indication;

    @Column(name = "dosage_text", length = 128)
    private String dosageText;

    @Column(name = "dosage_amount", precision = 10, scale = 3)
    private BigDecimal dosageAmount;

    @Column(name = "dosage_unit", length = 16)
    private String dosageUnit;

    @Column(length = 32)
    private String frequency;

    @Column(length = 64)
    private String formulation;

    @Column(name = "route_of_administration", length = 64)
    private String routeOfAdministration;

    @Column(name = "batch_lot_number", length = 64)
    private String batchLotNumber;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "stop_date")
    private LocalDate stopDate;

    @Column(name = "ongoing")
    private Boolean ongoing = false;

    @Column(name = "dechallenge_result", length = 32)
    private String dechallengeResult = "Not Applicable";

    @Column(name = "rechallenge_result", length = 32)
    private String rechallengeResult = "Not Applicable";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public CaseProduct() {
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

    public String getProductType() {
        return productType;
    }

    public void setProductType(String productType) {
        this.productType = productType;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getActiveSubstance() {
        return activeSubstance;
    }

    public void setActiveSubstance(String activeSubstance) {
        this.activeSubstance = activeSubstance;
    }

    public String getIndication() {
        return indication;
    }

    public void setIndication(String indication) {
        this.indication = indication;
    }

    public String getDosageText() {
        return dosageText;
    }

    public void setDosageText(String dosageText) {
        this.dosageText = dosageText;
    }

    public BigDecimal getDosageAmount() {
        return dosageAmount;
    }

    public void setDosageAmount(BigDecimal dosageAmount) {
        this.dosageAmount = dosageAmount;
    }

    public String getDosageUnit() {
        return dosageUnit;
    }

    public void setDosageUnit(String dosageUnit) {
        this.dosageUnit = dosageUnit;
    }

    public String getFrequency() {
        return frequency;
    }

    public void setFrequency(String frequency) {
        this.frequency = frequency;
    }

    public String getFormulation() {
        return formulation;
    }

    public void setFormulation(String formulation) {
        this.formulation = formulation;
    }

    public String getRouteOfAdministration() {
        return routeOfAdministration;
    }

    public void setRouteOfAdministration(String routeOfAdministration) {
        this.routeOfAdministration = routeOfAdministration;
    }

    public String getBatchLotNumber() {
        return batchLotNumber;
    }

    public void setBatchLotNumber(String batchLotNumber) {
        this.batchLotNumber = batchLotNumber;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getStopDate() {
        return stopDate;
    }

    public void setStopDate(LocalDate stopDate) {
        this.stopDate = stopDate;
    }

    public Boolean getOngoing() {
        return ongoing;
    }

    public void setOngoing(Boolean ongoing) {
        this.ongoing = ongoing;
    }

    public String getDechallengeResult() {
        return dechallengeResult;
    }

    public void setDechallengeResult(String dechallengeResult) {
        this.dechallengeResult = dechallengeResult;
    }

    public String getRechallengeResult() {
        return rechallengeResult;
    }

    public void setRechallengeResult(String rechallengeResult) {
        this.rechallengeResult = rechallengeResult;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
