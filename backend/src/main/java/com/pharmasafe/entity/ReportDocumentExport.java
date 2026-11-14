package com.pharmasafe.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "report_document_exports")
public class ReportDocumentExport {
  @Id
  @Column(length = 36)
  private String id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "report_id", nullable = false)
  private SafetyReport report;

  @Column(name = "source_report_version", nullable = false)
  private long sourceReportVersion;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "generated_by_user_id", nullable = false)
  private User generatedByUser;

  @Column(name = "generated_by_username", nullable = false, length = 64)
  private String generatedByUsername;

  @Column(name = "generated_at", nullable = false)
  private Instant generatedAt;

  @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
  @Column(name = "snapshot_json", nullable = false, columnDefinition = "jsonb")
  private String snapshotJson;

  @Column(name = "pdf_bytes", nullable = false, columnDefinition = "bytea")
  private byte[] pdfBytes;

  @Column(name = "pdf_sha256", nullable = false, length = 64)
  private String pdfSha256;

  @Column(name = "file_name", nullable = false, length = 180)
  private String fileName;

  public String getId() {
    return id;
  }

  public void setId(String value) {
    id = value;
  }

  public SafetyReport getReport() {
    return report;
  }

  public void setReport(SafetyReport value) {
    report = value;
  }

  public long getSourceReportVersion() {
    return sourceReportVersion;
  }

  public void setSourceReportVersion(long value) {
    sourceReportVersion = value;
  }

  public User getGeneratedByUser() {
    return generatedByUser;
  }

  public void setGeneratedByUser(User value) {
    generatedByUser = value;
  }

  public String getGeneratedByUsername() {
    return generatedByUsername;
  }

  public void setGeneratedByUsername(String value) {
    generatedByUsername = value;
  }

  public Instant getGeneratedAt() {
    return generatedAt;
  }

  public void setGeneratedAt(Instant value) {
    generatedAt = value;
  }

  public String getSnapshotJson() {
    return snapshotJson;
  }

  public void setSnapshotJson(String value) {
    snapshotJson = value;
  }

  public byte[] getPdfBytes() {
    return pdfBytes;
  }

  public void setPdfBytes(byte[] value) {
    pdfBytes = value;
  }

  public String getPdfSha256() {
    return pdfSha256;
  }

  public void setPdfSha256(String value) {
    pdfSha256 = value;
  }

  public String getFileName() {
    return fileName;
  }

  public void setFileName(String value) {
    fileName = value;
  }
}
