package com.pharmasafe.processing;

import com.pharmasafe.entity.*;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "cp_work_assignments")
public class ProcessingAssignment {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "supervisor_note", nullable = false, length = 2000)
  public String supervisorNote = "";

  @Column(name = "reopened_at")
  public java.time.Instant reopenedAt;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "work_order_id")
  public ProcessingWorkOrder workOrder;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "team_id")
  public ProcessingTeam team;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "employee_user_id")
  public User employee;

  @Column(nullable = false, length = 16)
  public String status;

  @Column(nullable = false)
  public int progress;

  @Version
  @Column(name = "row_version", nullable = false)
  public long rowVersion;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  public Instant updatedAt;
}
