package com.pharmasafe.education;

import com.pharmasafe.entity.User;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "edu_domain_admins")
public class EduDomainAdmin {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "user_id")
  public User user;

  @Column(name = "domain", length = 32, nullable = false)
  public String domain;

  @Column(nullable = false)
  public boolean active;

  @Column(name = "assigned_at", nullable = false)
  public Instant assignedAt;

  @Version
  @Column(name = "row_version", nullable = false)
  public long rowVersion;
}
