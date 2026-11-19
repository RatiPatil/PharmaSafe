package com.pharmasafe.education;

import com.pharmasafe.entity.User;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "edu_work_orders")
public class EduWorkOrder {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "college_id")
  public EduCollege college;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "created_by")
  public User createdBy;

  @Column(length = 32, nullable = false)
  public String domain;

  @Column(length = 64, nullable = false)
  public String stage;

  @Column(length = 160, nullable = false)
  public String title;

  @Column(length = 5000, nullable = false)
  public String description;

  @Column(length = 5000, nullable = false)
  public String instructions;

  @Column(name = "due_at", nullable = false)
  public Instant dueAt;

  @Column(name = "require_document", nullable = false)
  public boolean requireDocument;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "last_changed_by")
  public User lastChangedBy;

  @Column(nullable = false)
  public boolean archived;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;

  @Version
  @Column(name = "row_version", nullable = false)
  public long rowVersion;
}
