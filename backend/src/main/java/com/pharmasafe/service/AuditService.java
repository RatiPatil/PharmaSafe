package com.pharmasafe.service;

import com.pharmasafe.entity.AuditEvent;
import com.pharmasafe.repository.AuditEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final AuditEventRepository auditEventRepository;

    public AuditService(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
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
}
