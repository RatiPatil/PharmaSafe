package com.pharmasafe.dto;

import com.pharmasafe.entity.User;
import java.time.Instant;

public class UserResponseDto {

    private Long id;
    private String username;
    private String fullName;
    private String department;
    private String role;
    private boolean enabled;
    private boolean locked;
    private Instant lastSuccessfulLoginAt;
    private Instant createdAt;

    public UserResponseDto() {
    }

    public UserResponseDto(Long id, String username, String fullName, String department, String role, boolean enabled, boolean locked, Instant lastSuccessfulLoginAt, Instant createdAt) {
        this.id = id;
        this.username = username;
        this.fullName = fullName;
        this.department = department;
        this.role = role;
        this.enabled = enabled;
        this.locked = locked;
        this.lastSuccessfulLoginAt = lastSuccessfulLoginAt;
        this.createdAt = createdAt;
    }

    public static UserResponseDto fromEntity(User user) {
        if (user == null) {
            return null;
        }
        return new UserResponseDto(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getDepartment(),
                user.getRole(),
                user.isEnabled(),
                user.isLocked(),
                user.getLastSuccessfulLoginAt(),
                user.getCreatedAt()
        );
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public Instant getLastSuccessfulLoginAt() {
        return lastSuccessfulLoginAt;
    }

    public void setLastSuccessfulLoginAt(Instant lastSuccessfulLoginAt) {
        this.lastSuccessfulLoginAt = lastSuccessfulLoginAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
