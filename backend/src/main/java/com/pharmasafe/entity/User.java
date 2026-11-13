package com.pharmasafe.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_users_username", columnList = "username", unique = true),
    @Index(name = "idx_users_role", columnList = "role")
})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name="staff_code",insertable=false,updatable=false,length=32) private String staffCode;
    public String getStaffCode(){return id==null?null:"PS-"+id;}

    @Column(name = "auth_version", nullable = false)
    private long authVersion;
    @Version
    @Column(name="account_row_version", nullable=false)
    private long accountRowVersion;
    public long getAccountRowVersion() { return accountRowVersion; }
    public long getAuthVersion() { return authVersion; }
    public void revokeSessions() { authVersion++; }


    @Column(nullable = false, unique = true, length = 64)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 128)
    private String passwordHash;

    @Column(nullable = false, length = 32)
    private String role;

    @Column(name = "full_name", length = 128)
    private String fullName;

    @Column(length = 64)
    private String department = "Safety";

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts = 0;

    @Column(nullable = false)
    private boolean locked = false;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "last_successful_login_at")
    private Instant lastSuccessfulLoginAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name="account_status", length=16)
    private String accountStatus = "APPROVED";
    public String getAccountStatus() { return accountStatus; }
    public void setAccountStatus(String value) { accountStatus=value; }
    @Column(name="email", length=254)
    private String email = null;
    public String getEmail() { return email; }
    public void setEmail(String value) { email=value; }
    @Column(name="employee_id", length=64)
    private String employeeId = null;
    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String value) { employeeId=value; }
    @Column(name="mobile_number", length=32)
    private String mobileNumber = null;
    public String getMobileNumber() { return mobileNumber; }
    public void setMobileNumber(String value) { mobileNumber=value; }
    @Column(name="designation", length=128)
    private String designation = null;
    public String getDesignation() { return designation; }
    public void setDesignation(String value) { designation=value; }
    @Column(name="organization", length=128)
    private String organization = null;
    public String getOrganization() { return organization; }
    public void setOrganization(String value) { organization=value; }
    @Column(name="approved_by")
    private Long approvedBy = null;
    public Long getApprovedBy() { return approvedBy; }
    public void setApprovedBy(Long value) { approvedBy=value; }
    @Column(name="approved_at")
    private Instant approvedAt = null;
    public Instant getApprovedAt() { return approvedAt; }
    public void setApprovedAt(Instant value) { approvedAt=value; }
    public boolean isApproved() { return "APPROVED".equals(accountStatus); }

    public User() {
    }

    public User(String username, String passwordHash, String role) {
        this.username = username != null ? username.trim().toLowerCase() : null;
        this.passwordHash = passwordHash;
        this.role = role;
        this.enabled = true;
        this.failedLoginAttempts = 0;
        this.locked = false;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
        if (this.username != null) {
            this.username = this.username.trim().toLowerCase();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
        if (this.username != null) {
            this.username = this.username.trim().toLowerCase();
        }
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
        this.username = username != null ? username.trim().toLowerCase() : null;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
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

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public void setFailedLoginAttempts(int failedLoginAttempts) {
        this.failedLoginAttempts = failedLoginAttempts;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public Instant getLockedAt() {
        return lockedAt;
    }

    public void setLockedAt(Instant lockedAt) {
        this.lockedAt = lockedAt;
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

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
