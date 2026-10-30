package com.pharmasafe.dto;

import com.pharmasafe.entity.CaseActionItem;
import java.time.Instant;

public record ActionItemResponse(
        Long id, Long caseId, String caseNumber, String description, String assignedUsername,
        String status, Instant dueAt, boolean overdue, String createdByUsername, Instant createdAt,
        String completedByUsername, Instant completedAt, int versionNumber
) {
    public static ActionItemResponse fromEntity(CaseActionItem item, Instant now) {
        boolean overdue = item.getDueAt().isBefore(now)
                && !"COMPLETED".equals(item.getStatus())
                && !"CANCELLED".equals(item.getStatus());
        return new ActionItemResponse(
                item.getId(), item.getSafetyCase().getId(), item.getSafetyCase().getCaseNumber(),
                item.getDescription(), item.getAssignedUser().getUsername(), item.getStatus(),
                item.getDueAt(), overdue, item.getCreatedByUser().getUsername(), item.getCreatedAt(),
                item.getCompletedByUser() != null ? item.getCompletedByUser().getUsername() : null,
                item.getCompletedAt(), item.getVersionNumber());
    }
}
