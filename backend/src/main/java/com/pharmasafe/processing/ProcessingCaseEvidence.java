package com.pharmasafe.processing;

import com.pharmasafe.entity.*;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "cp_processing_evidence")
public class ProcessingCaseEvidence {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "assignment_id", nullable = false)
  public ProcessingAssignment assignment;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "case_id", nullable = false)
  public SafetyCase safetyCase;

  @Column(name = "case_row_version", nullable = false)
  public long caseRowVersion;

  @Column(nullable = false, length = 32)
  public String kind;

  @Column(nullable = false, length = 32)
  public String outcome;

  @Column(nullable = false, length = 5000)
  public String notes;

  @Column(name = "dictionary_reference", nullable = false, length = 500)
  public String dictionaryReference;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "recorded_by", nullable = false)
  public User recordedBy;

  @Column(name = "recorded_at", nullable = false)
  public Instant recordedAt;
}
