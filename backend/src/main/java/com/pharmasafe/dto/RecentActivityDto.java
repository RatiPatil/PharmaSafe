package com.pharmasafe.dto;

import java.time.Instant;

public class RecentActivityDto {

    private Long id;
    private String eventType;
    private String username;
    private String details;
    private Instant createdAt;

    public RecentActivityDto() {
    }

    public RecentActivityDto(Long id, String eventType, String username, String details, Instant createdAt) {
        this.id = id;
        this.eventType = eventType;
        this.username = username;
        this.details = details;
        this.createdAt = createdAt;
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
