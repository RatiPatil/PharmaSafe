package com.pharmasafe.dto;

import com.pharmasafe.entity.AuditEvent;
import java.time.Instant;

public class AuditEventResponseDto {

    private Long id;
    private String eventType;
    private String username;
    private String ipAddress;
    private String userAgent;
    private String status;
    private String details;
    private Instant createdAt;

    public AuditEventResponseDto() {
    }

    public AuditEventResponseDto(Long id, String eventType, String username, String ipAddress, String userAgent, String status, String details, Instant createdAt) {
        this.id = id;
        this.eventType = eventType;
        this.username = username;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.status = status;
        this.details = details;
        this.createdAt = createdAt;
    }

    public static AuditEventResponseDto fromEntity(AuditEvent event) {
        if (event == null) {
            return null;
        }
        return new AuditEventResponseDto(
                event.getId(),
                event.getEventType(),
                event.getUsername(),
                event.getIpAddress(),
                event.getUserAgent(),
                event.getStatus(),
                event.getDetails(),
                event.getCreatedAt()
        );
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
