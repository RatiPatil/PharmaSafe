package com.pharmasafe.medicalteam;

import com.pharmasafe.entity.*;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "mr_review_conclusions")
public class MedicalConclusion {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "assignment_id")
  public MedicalAssignment assignment;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "case_id")
  public SafetyCase safetyCase;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "employee_id")
  public User employee;

  @Column(name = "source_case_version", nullable = false)
  public long sourceCaseVersion;

  @Column(name = "medical_narrative", nullable = false, length = 5000)
  public String medicalNarrative;

  @Column(name = "review_summary", nullable = false, length = 3000)
  public String reviewSummary;

  @Column(name = "assessed_seriousness", nullable = false, length = 20)
  public String assessedSeriousness;

  @Column(name = "severity_summary", nullable = false, length = 1000)
  public String severitySummary;

  @Column(name = "source_reviewed", nullable = false)
  public boolean sourceReviewed;

  @Column(name = "timeline_reviewed", nullable = false)
  public boolean timelineReviewed;

  @Column(name = "recorded_at", nullable = false)
  public Instant recordedAt;
}
