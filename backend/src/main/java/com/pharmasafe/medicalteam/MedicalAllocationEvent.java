package com.pharmasafe.medicalteam;

import com.pharmasafe.entity.*;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "mr_case_allocation_events")
public class MedicalAllocationEvent {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "case_id")
  public SafetyCase safetyCase;

  @Column(name = "previous_team_id")
  public Long previousTeamId;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "team_id")
  public MedicalTeam team;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "actor_id")
  public User actor;

  @Column(nullable = false, length = 2000)
  public String reason;

  @Column(name = "recorded_at", nullable = false)
  public Instant recordedAt;

  @Column(name = "source_case_version", nullable = false)
  public long sourceCaseVersion;
}
