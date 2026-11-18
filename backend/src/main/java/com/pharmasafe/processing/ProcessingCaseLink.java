package com.pharmasafe.processing;

import com.pharmasafe.entity.*;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "cp_assignment_cases")
public class ProcessingCaseLink {
  @Id
  @Column(name = "assignment_id")
  public Long assignmentId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "case_id", nullable = false)
  public SafetyCase safetyCase;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "employee_id", nullable = false)
  public User employee;

  @Column(name = "linked_at", nullable = false)
  public Instant linkedAt;
}
