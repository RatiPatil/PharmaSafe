package com.pharmasafe.processing;

import com.pharmasafe.entity.*;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "cp_work_documents")
public class ProcessingWorkDocument {
  @Id
  @Column(length = 36)
  public String id;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "assignment_id")
  public ProcessingAssignment assignment;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "update_id")
  public ProcessingWorkUpdate update;

  @Column(name = "document_kind", nullable = false, length = 24)
  public String documentKind;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "generated_by_user_id")
  public User generatedBy;

  @Column(name = "generated_at", nullable = false)
  public Instant generatedAt;

  @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
  @Column(name = "snapshot_json", nullable = false, columnDefinition = "jsonb")
  public String snapshotJson;

  @Column(name = "pdf_bytes", nullable = false, columnDefinition = "bytea")
  public byte[] pdfBytes;

  @Column(name = "pdf_sha256", nullable = false, length = 64)
  public String pdfSha256;

  @Column(name = "file_name", nullable = false, length = 180)
  public String fileName;
}
