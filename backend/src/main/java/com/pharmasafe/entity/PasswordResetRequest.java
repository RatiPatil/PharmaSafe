package com.pharmasafe.entity;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="password_reset_requests")
public class PasswordResetRequest {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @ManyToOne(optional=false) @JoinColumn(name="user_id") public User user;
    @Column(name="created_at",nullable=false) public Instant createdAt=Instant.now();
    @ManyToOne @JoinColumn(name="reviewed_by") public User reviewedBy;
    @Column(name="reviewed_at") public Instant reviewedAt;
    @Column(name="decision_reason",length=2000) public String decisionReason;
    @Version @Column(name="row_version") public long version;
    @Column(nullable=false) public String status="PENDING";
    @Column(name="token_hash",length=64) public String tokenHash;
    @Column(name="issued_auth_version") public Long issuedAuthVersion;
    @Column(name="expires_at") public Instant expiresAt;
    @Column(name="used_at") public Instant usedAt;
}
