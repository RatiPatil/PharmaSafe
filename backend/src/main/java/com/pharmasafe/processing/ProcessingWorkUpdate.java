package com.pharmasafe.processing;

import com.pharmasafe.entity.*;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "cp_work_updates")
public class ProcessingWorkUpdate {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "assignment_id")
  public ProcessingAssignment assignment;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "employee_user_id")
  public User employee;

  @Column(nullable = false, length = 16)
  public String status;

  @Column(nullable = false)
  public int progress;

  @Column(name = "work_done", nullable = false, length = 5000)
  public String workDone;

  @Column(name = "pending_work", nullable = false, length = 3000)
  public String pendingWork;

  @Column(name = "remaining_work", nullable = false, length = 3000)
  public String remainingWork;

  @Column(nullable = false, length = 2000)
  public String blockers;

  @Column(nullable = false, length = 2000)
  public String reason;

  @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
  @Column(name = "checklist_json", nullable = false, columnDefinition = "jsonb")
  public String checklistJson;

  @Column(name = "coding_reference", nullable = false, length = 500)
  public String codingReference;

  @Column(name = "source_assignment_version", nullable = false)
  public long sourceAssignmentVersion;

  @Column(name = "recorded_at", nullable = false)
  public Instant recordedAt;
}
