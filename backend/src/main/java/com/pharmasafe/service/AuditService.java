package com.pharmasafe.service;

import com.pharmasafe.entity.AuditEvent;
import com.pharmasafe.repository.AuditEventRepository;
import com.pharmasafe.entity.SafetyCase;
import com.pharmasafe.repository.SafetyCaseRepository;
import com.pharmasafe.dto.AuditEventResponseDto;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final AuditEventRepository auditEventRepository;
    private final SafetyCaseRepository safetyCaseRepository;

    public AuditService(AuditEventRepository auditEventRepository, SafetyCaseRepository safetyCaseRepository) {
        this.auditEventRepository = auditEventRepository;
        this.safetyCaseRepository = safetyCaseRepository;
    }

    /**
     * Persists an audit event in an independent transaction.
     * Ensures audit history is retained even if the caller transaction rolls back.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logEvent(String eventType, String username, String ipAddress, String userAgent, String status, String details) {
        AuditEvent event = new AuditEvent(
                eventType,
                username != null ? username.trim().toLowerCase() : null,
                ipAddress,
                userAgent,
                status,
                details
        );
        auditEventRepository.save(event);
    }

    @Transactional
    public void logAccountEvent(String eventType, String username, String details) {
        auditEventRepository.save(new AuditEvent(eventType, username, null, null, "SUCCESS", details));
    }

    @Transactional
    public void logCaseEvent(Long caseId, String eventType, String username, String status, String details) {
        logCaseEvent(caseId, eventType, username, status, details, null, null);
    }

    @Transactional
    public void logCaseEvent(Long caseId, String eventType, String username, String status, String details,
                             String ipAddress, String userAgent) {
        AuditEvent event = new AuditEvent(eventType,
                username != null ? username.trim().toLowerCase() : null,
                ipAddress, userAgent, status, details);
        SafetyCase safetyCase = safetyCaseRepository.findById(caseId).orElse(null);
        event.setSafetyCase(safetyCase);
        auditEventRepository.save(event);
    }

    @Transactional(readOnly = true)
    public List<AuditEventResponseDto> getCaseAuditHistory(Long caseId) {
        return auditEventRepository.findBySafetyCaseIdOrderByCreatedAtDesc(caseId)
                .stream().map(AuditEventResponseDto::fromEntity).toList();
    }
}
