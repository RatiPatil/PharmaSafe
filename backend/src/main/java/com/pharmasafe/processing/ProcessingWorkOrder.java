package com.pharmasafe.processing;

import com.pharmasafe.entity.*;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "cp_work_orders")
public class ProcessingWorkOrder {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "team_id")
  public ProcessingTeam team;

  @Column(nullable = false, length = 160)
  public String title;

  @Column(nullable = false, length = 5000)
  public String description;

  @Column(nullable = false, length = 5000)
  public String instructions;

  @Column(nullable = false, length = 32)
  public String stage;

  @Column(nullable = false, length = 16)
  public String priority;

  @Column(name = "due_at", nullable = false)
  public Instant dueAt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "case_id")
  public SafetyCase safetyCase;

  @Column(name="clinical_required",nullable=false)
  public boolean clinicalRequired;

  @Column(name = "recipient_mode", nullable = false, length = 16)
  public String recipientMode;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "created_by_user_id")
  public User createdBy;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;
}
