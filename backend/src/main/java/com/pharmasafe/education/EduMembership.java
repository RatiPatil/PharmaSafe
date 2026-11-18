package com.pharmasafe.education;

import com.pharmasafe.entity.User;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "edu_student_domains")
public class EduMembership {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "student_id")
  public EduStudent student;

  @Column(name = "domain", length = 32, nullable = false)
  public String domain;

  @Column(name = "status", length = 16, nullable = false)
  public String status;

  @ManyToOne(fetch = FetchType.EAGER, optional = true)
  @JoinColumn(name = "reviewer_id")
  public User reviewer;

  @Column(name = "reason", length = 2000, nullable = true)
  public String reason;

  @Column(name = "reviewed_at")
  public Instant reviewedAt;

  @Column(name = "requested_at", nullable = false)
  public Instant requestedAt;

  @Version
  @Column(name = "row_version", nullable = false)
  public long rowVersion;
}
