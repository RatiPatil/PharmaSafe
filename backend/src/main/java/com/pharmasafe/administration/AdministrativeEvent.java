package com.pharmasafe.administration;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "administrative_events")
public class AdministrativeEvent {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "actor_id")
  public Long actorId;

  @Column(name = "actor_username", nullable = false, length = 64)
  public String actorUsername;

  @Column(name = "actor_level", nullable = false, length = 20)
  public String actorLevel;

  @Column(name = "managed_role", length = 32)
  public String managedRole;

  @Column(name = "target_user_id")
  public Long targetUserId;

  @Column(name = "target_username", length = 64)
  public String targetUsername;

  @Column(name = "request_id")
  public Long requestId;

  @Column(name = "previous_administrator_id")
  public Long previousAdministratorId;

  @Column(nullable = false, length = 64)
  public String action;

  @Column(name = "previous_status", length = 32)
  public String previousStatus;

  @Column(name = "new_status", length = 32)
  public String newStatus;

  @Column(nullable = false, length = 2000)
  public String reason;

  @Column(nullable = false, length = 16)
  public String outcome;

  @Column(name = "created_at", nullable = false, updatable = false)
  public Instant createdAt = Instant.now();
}
