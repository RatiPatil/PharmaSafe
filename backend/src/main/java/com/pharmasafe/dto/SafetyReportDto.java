package com.pharmasafe.dto;

import com.pharmasafe.entity.SafetyCase;
import com.pharmasafe.entity.SafetyReport;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class SafetyReportDto {

    private Long id;
    private String reportId;
    private String reportType;
    private String product;
    private String authority;
    private LocalDate dataLockPoint;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private LocalDate dueDate;
    private Instant submittedDate;
    private String status;
    private int totalCases;
    private int seriousCases;
    private int fatalCases;
    private String summary;
    private String conclusions;
    private String recommendations;
    private String createdByUsername;
    private List<String> relatedCaseNumbers = new ArrayList<>();
    private Long daysRemaining;
    private Instant createdAt;
    private Instant updatedAt;

    public SafetyReportDto() {
    }

    public static SafetyReportDto fromEntity(SafetyReport report) {
        if (report == null) {
            return null;
        }
        SafetyReportDto dto = new SafetyReportDto();
        dto.setId(report.getId());
        dto.setReportId(report.getReportId());
        dto.setReportType(report.getReportType());
        dto.setProduct(report.getProduct());
        dto.setAuthority(report.getAuthority());
        dto.setDataLockPoint(report.getDataLockPoint());
        dto.setPeriodStart(report.getPeriodStart());
        dto.setPeriodEnd(report.getPeriodEnd());
        dto.setDueDate(report.getDueDate());
        dto.setSubmittedDate(report.getSubmittedDate());
        dto.setStatus(report.getStatus());
        dto.setTotalCases(report.getTotalCases());
        dto.setSeriousCases(report.getSeriousCases());
        dto.setFatalCases(report.getFatalCases());
        dto.setSummary(report.getSummary());
        dto.setConclusions(report.getConclusions());
        dto.setRecommendations(report.getRecommendations());
        if (report.getCreatedByUser() != null) {
            dto.setCreatedByUsername(report.getCreatedByUser().getUsername());
        }
        if (report.getRelatedCases() != null) {
            dto.setRelatedCaseNumbers(report.getRelatedCases().stream()
                    .map(SafetyCase::getCaseNumber)
                    .collect(Collectors.toList()));
        }

        // Calculate days remaining
        if (report.getDueDate() != null) {
            LocalDate today = LocalDate.now();
            dto.setDaysRemaining(ChronoUnit.DAYS.between(today, report.getDueDate()));
            if ("Pending".equalsIgnoreCase(report.getStatus()) && today.isAfter(report.getDueDate())) {
                dto.setStatus("Overdue");
            }
        }

        dto.setCreatedAt(report.getCreatedAt());
        dto.setUpdatedAt(report.getUpdatedAt());
        return dto;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getReportId() { return reportId; }
    public void setReportId(String reportId) { this.reportId = reportId; }
    public String getReportType() { return reportType; }
    public void setReportType(String reportType) { this.reportType = reportType; }
    public String getProduct() { return product; }
    public void setProduct(String product) { this.product = product; }
    public String getAuthority() { return authority; }
    public void setAuthority(String authority) { this.authority = authority; }
    public LocalDate getDataLockPoint() { return dataLockPoint; }
    public void setDataLockPoint(LocalDate dataLockPoint) { this.dataLockPoint = dataLockPoint; }
    public LocalDate getPeriodStart() { return periodStart; }
    public void setPeriodStart(LocalDate periodStart) { this.periodStart = periodStart; }
    public LocalDate getPeriodEnd() { return periodEnd; }
    public void setPeriodEnd(LocalDate periodEnd) { this.periodEnd = periodEnd; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public Instant getSubmittedDate() { return submittedDate; }
    public void setSubmittedDate(Instant submittedDate) { this.submittedDate = submittedDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getTotalCases() { return totalCases; }
    public void setTotalCases(int totalCases) { this.totalCases = totalCases; }
    public int getSeriousCases() { return seriousCases; }
    public void setSeriousCases(int seriousCases) { this.seriousCases = seriousCases; }
    public int getFatalCases() { return fatalCases; }
    public void setFatalCases(int fatalCases) { this.fatalCases = fatalCases; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getConclusions() { return conclusions; }
    public void setConclusions(String conclusions) { this.conclusions = conclusions; }
    public String getRecommendations() { return recommendations; }
    public void setRecommendations(String recommendations) { this.recommendations = recommendations; }
    public String getCreatedByUsername() { return createdByUsername; }
    public void setCreatedByUsername(String createdByUsername) { this.createdByUsername = createdByUsername; }
    public List<String> getRelatedCaseNumbers() { return relatedCaseNumbers; }
    public void setRelatedCaseNumbers(List<String> relatedCaseNumbers) { this.relatedCaseNumbers = relatedCaseNumbers; }
    public Long getDaysRemaining() { return daysRemaining; }
    public void setDaysRemaining(Long daysRemaining) { this.daysRemaining = daysRemaining; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
