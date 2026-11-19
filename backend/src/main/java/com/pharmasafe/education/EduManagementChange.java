package com.pharmasafe.education;

import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "edu_management_changes")
public class EduManagementChange {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "actor_id", nullable = false)
  public Long actorId;

  @Column(name = "record_kind", length = 16, nullable = false)
  public String recordKind;

  @Column(name = "record_id", nullable = false)
  public Long recordId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "before_json", columnDefinition = "jsonb", nullable = false)
  public String beforeJson;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "after_json", columnDefinition = "jsonb", nullable = false)
  public String afterJson;

  @Column(length = 2000, nullable = false)
  public String reason;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;
}
