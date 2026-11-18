package com.pharmasafe.education;

import com.pharmasafe.entity.User;
import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "edu_progress_reports")
public class EduReport {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "generated_by")
  public User generatedBy;

  @ManyToOne(fetch = FetchType.EAGER, optional = true)
  @JoinColumn(name = "college_id")
  public EduCollege college;

  @ManyToOne(fetch = FetchType.EAGER, optional = true)
  @JoinColumn(name = "student_id")
  public EduStudent student;

  @Column(name = "domain", length = 32, nullable = false)
  public String domain;

  @Column(name = "filter_status", length = 16, nullable = true)
  public String filterStatus;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "snapshot_json", columnDefinition = "jsonb", nullable = false)
  public String snapshotJson;

  @Column(name = "pdf_bytes", columnDefinition = "bytea", nullable = false)
  public byte[] pdfBytes;

  @Column(name = "sha256", length = 64, nullable = false)
  public String sha256;

  @Column(name = "file_name", length = 180, nullable = false)
  public String fileName;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;
}
