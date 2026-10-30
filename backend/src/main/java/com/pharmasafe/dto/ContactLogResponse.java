package com.pharmasafe.dto;

import com.pharmasafe.entity.CaseContactLog;
import java.time.Instant;

public record ContactLogResponse(
        Long id, Long caseId, String caseNumber, Instant contactAt, String contactType,
        String description, String responsibleUsername, String createdByUsername, Instant createdAt,
        String updatedByUsername, Instant updatedAt
) {
    public static ContactLogResponse fromEntity(CaseContactLog log) {
        return new ContactLogResponse(
                log.getId(), log.getSafetyCase().getId(), log.getSafetyCase().getCaseNumber(),
                log.getContactAt(), log.getContactType(), log.getDescription(),
                log.getResponsibleUser() != null ? log.getResponsibleUser().getUsername() : null,
                log.getCreatedByUser().getUsername(), log.getCreatedAt(),
                log.getUpdatedByUser() != null ? log.getUpdatedByUser().getUsername() : null,
                log.getUpdatedAt());
    }
}
