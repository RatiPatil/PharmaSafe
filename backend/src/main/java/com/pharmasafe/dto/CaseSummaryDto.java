package com.pharmasafe.dto;

import com.pharmasafe.entity.SafetyCase;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;

public class CaseSummaryDto {

    private Long id;
    private String caseNumber;
    private String countryCode;
    private String reportType;
    private String workflowState;
    private String priority;
    private boolean serious;
    private boolean fatal;
    private String primaryProduct;
    private String primaryEvent;
    private LocalDate initialReceiptDate;
    private LocalDate latestReceivedDate;
    private int versionNumber;
    private String assignedToUsername;
    private boolean locked;
    private String lockedByUsername;
    private Instant createdAt;
    private Instant updatedAt;

    public CaseSummaryDto() {
    }

    public static CaseSummaryDto fromEntity(SafetyCase c) {
        CaseSummaryDto dto = new CaseSummaryDto();
        dto.setId(c.getId());
        dto.setCaseNumber(c.getCaseNumber());
        dto.setCountryCode(c.getCountryCode());
        dto.setReportType(c.getReportType());
        dto.setWorkflowState(c.getWorkflowState());
        dto.setPriority(c.getPriority());
        dto.setSerious(c.isSerious());
        dto.setFatal(c.isFatal());
        dto.setInitialReceiptDate(c.getInitialReceiptDate());
        dto.setLatestReceivedDate(c.getLatestReceivedDate());
        dto.setVersionNumber(c.getVersionNumber());
        dto.setLocked(c.isLocked());
        if (c.getLockedByUser() != null) {
            dto.setLockedByUsername(c.getLockedByUser().getUsername());
        }
        dto.setCreatedAt(c.getCreatedAt());
        dto.setUpdatedAt(c.getUpdatedAt());

        if (c.getAssignedToUser() != null) {
            dto.setAssignedToUsername(c.getAssignedToUser().getUsername());
        }

        if (c.getProducts() != null && !c.getProducts().isEmpty()) {
            dto.setPrimaryProduct(c.getProducts().stream()
                    .filter(p -> "Suspect".equalsIgnoreCase(p.getProductType()))
                    .min(Comparator.comparing(p -> p.getId() == null ? Long.MAX_VALUE : p.getId()))
                    .orElse(c.getProducts().stream().min(Comparator.comparing(p -> p.getId() == null ? Long.MAX_VALUE : p.getId())).orElse(null))
                    .getProductName());
        } else {
            dto.setPrimaryProduct("-");
        }

        if (c.getEvents() != null && !c.getEvents().isEmpty()) {
            dto.setPrimaryEvent(c.getEvents().stream()
                    .min(Comparator.comparing(e -> e.getId() == null ? Long.MAX_VALUE : e.getId()))
                    .orElseThrow()
                    .getEventVerbatim());
        } else {
            dto.setPrimaryEvent("-");
        }

        return dto;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCaseNumber() { return caseNumber; }
    public void setCaseNumber(String caseNumber) { this.caseNumber = caseNumber; }
    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }
    public String getReportType() { return reportType; }
    public void setReportType(String reportType) { this.reportType = reportType; }
    public String getWorkflowState() { return workflowState; }
    public void setWorkflowState(String workflowState) { this.workflowState = workflowState; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public boolean isSerious() { return serious; }
    public void setSerious(boolean serious) { this.serious = serious; }
    public boolean isFatal() { return fatal; }
    public void setFatal(boolean fatal) { this.fatal = fatal; }
    public String getPrimaryProduct() { return primaryProduct; }
    public void setPrimaryProduct(String primaryProduct) { this.primaryProduct = primaryProduct; }
    public String getPrimaryEvent() { return primaryEvent; }
    public void setPrimaryEvent(String primaryEvent) { this.primaryEvent = primaryEvent; }
    public LocalDate getInitialReceiptDate() { return initialReceiptDate; }
    public void setInitialReceiptDate(LocalDate initialReceiptDate) { this.initialReceiptDate = initialReceiptDate; }
    public LocalDate getLatestReceivedDate() { return latestReceivedDate; }
    public void setLatestReceivedDate(LocalDate latestReceivedDate) { this.latestReceivedDate = latestReceivedDate; }
    public int getVersionNumber() { return versionNumber; }
    public void setVersionNumber(int versionNumber) { this.versionNumber = versionNumber; }
    public String getAssignedToUsername() { return assignedToUsername; }
    public void setAssignedToUsername(String assignedToUsername) { this.assignedToUsername = assignedToUsername; }
    public boolean isLocked() { return locked; }
    public void setLocked(boolean locked) { this.locked = locked; }
    public String getLockedByUsername() { return lockedByUsername; }
    public void setLockedByUsername(String lockedByUsername) { this.lockedByUsername = lockedByUsername; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
