package com.pharmasafe.education;

import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "edu_task_submissions")
public class EduSubmission {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "task_id")
  public EduTask task;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "student_id")
  public EduStudent student;

  @Column(name = "source_task_version", nullable = false)
  public long sourceVersion;

  @Column(name = "status", length = 16, nullable = false)
  public String status;

  @Column(nullable = false)
  public int progress;

  @Column(name = "work_done", length = 5000, nullable = false)
  public String workDone;

  @Column(name = "pending_work", length = 3000, nullable = false)
  public String pendingWork;

  @Column(name = "remaining_work", length = 3000, nullable = false)
  public String remainingWork;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "evidence_json", columnDefinition = "jsonb", nullable = false)
  public String evidenceJson;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;
}
