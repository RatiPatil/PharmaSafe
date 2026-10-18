package com.pharmasafe.dto;

public class DashboardSummaryDto {

    private long totalCases;
    private long assignedCases;
    private long awaitingReviewCases;
    private long userAwaitingReviewCases;
    private long seriousCases;
    private long expeditedCases;
    private Integer overdueActionItems; // null because schema does not contain action_items table
    private String actionItemsStatus;   // "UNCONFIGURED"
    private String actionItemsNote;

    public DashboardSummaryDto() {
        this.overdueActionItems = null;
        this.actionItemsStatus = "UNCONFIGURED";
        this.actionItemsNote = "Action items module not configured in schema (Planned for Stage 5)";
    }

    public DashboardSummaryDto(long totalCases,
                               long assignedCases,
                               long awaitingReviewCases,
                               long userAwaitingReviewCases,
                               long seriousCases,
                               long expeditedCases) {
        this.totalCases = totalCases;
        this.assignedCases = assignedCases;
        this.awaitingReviewCases = awaitingReviewCases;
        this.userAwaitingReviewCases = userAwaitingReviewCases;
        this.seriousCases = seriousCases;
        this.expeditedCases = expeditedCases;
        this.overdueActionItems = null;
        this.actionItemsStatus = "UNCONFIGURED";
        this.actionItemsNote = "Action items module not configured in schema (Planned for Stage 5)";
    }

    public long getTotalCases() {
        return totalCases;
    }

    public void setTotalCases(long totalCases) {
        this.totalCases = totalCases;
    }

    public long getAssignedCases() {
        return assignedCases;
    }

    public void setAssignedCases(long assignedCases) {
        this.assignedCases = assignedCases;
    }

    public long getAwaitingReviewCases() {
        return awaitingReviewCases;
    }

    public void setAwaitingReviewCases(long awaitingReviewCases) {
        this.awaitingReviewCases = awaitingReviewCases;
    }

    public long getUserAwaitingReviewCases() {
        return userAwaitingReviewCases;
    }

    public void setUserAwaitingReviewCases(long userAwaitingReviewCases) {
        this.userAwaitingReviewCases = userAwaitingReviewCases;
    }

    public long getSeriousCases() {
        return seriousCases;
    }

    public void setSeriousCases(long seriousCases) {
        this.seriousCases = seriousCases;
    }

    public long getExpeditedCases() {
        return expeditedCases;
    }

    public void setExpeditedCases(long expeditedCases) {
        this.expeditedCases = expeditedCases;
    }

    public Integer getOverdueActionItems() {
        return overdueActionItems;
    }

    public void setOverdueActionItems(Integer overdueActionItems) {
        this.overdueActionItems = overdueActionItems;
    }

    public String getActionItemsStatus() {
        return actionItemsStatus;
    }

    public void setActionItemsStatus(String actionItemsStatus) {
        this.actionItemsStatus = actionItemsStatus;
    }

    public String getActionItemsNote() {
        return actionItemsNote;
    }

    public void setActionItemsNote(String actionItemsNote) {
        this.actionItemsNote = actionItemsNote;
    }
}
