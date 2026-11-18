package com.pharmasafe.education;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "edu_colleges")
public class EduCollege {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "code", length = 16, nullable = false)
  public String code;

  @Column(name = "name", length = 180, nullable = false)
  public String name;

  @Column(nullable = false)
  public boolean active;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;

  @Version
  @Column(name = "row_version", nullable = false)
  public long rowVersion;
}
