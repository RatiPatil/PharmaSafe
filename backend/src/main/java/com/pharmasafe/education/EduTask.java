package com.pharmasafe.education;

import com.pharmasafe.entity.User;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "edu_tasks")
public class EduTask {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "college_id")
  public EduCollege college;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "student_id")
  public EduStudent student;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "assigned_by")
  public User assignedBy;

  @Column(name = "domain", length = 32, nullable = false)
  public String domain;

  @Column(name = "stage", length = 64, nullable = false)
  public String stage;

  @Column(name = "title", length = 160, nullable = false)
  public String title;

  @Column(name = "description", length = 5000, nullable = false)
  public String description;

  @Column(name = "instructions", length = 5000, nullable = false)
  public String instructions;

  @Column(name = "due_at", nullable = false)
  public Instant dueAt;

  @Column(name = "require_document", nullable = false)
  public boolean requireDocument;

  @Column(name = "status", length = 16, nullable = false)
  public String status;

  @Column(nullable = false)
  public int progress;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  public Instant updatedAt;

  @Version
  @Column(name = "row_version", nullable = false)
  public long rowVersion;
}
