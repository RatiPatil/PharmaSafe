package com.pharmasafe.medicalteam;

import com.pharmasafe.entity.User;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "mr_case_allocations")
public class MedicalCaseAllocation {
  @Id
  @Column(name = "case_id")
  public Long caseId;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "team_id")
  public MedicalTeam team;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "allocated_by")
  public User allocatedBy;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "return_processor_id")
  public User returnProcessor;

  @Column(name = "allocated_at", nullable = false)
  public Instant allocatedAt;

  @Column(nullable = false, length = 2000)
  public String reason;

  @Version
  @Column(name = "row_version", nullable = false)
  public long rowVersion;
}
