package com.pharmasafe.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "report_acknowledgements")
public class ReportAcknowledgement {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "report_id", nullable = false)
  private SafetyReport report;

  @Column(nullable = false, length = 16)
  private String outcome;

  @Column(nullable = false, length = 128)
  private String reference;

  @Column(name = "received_at", nullable = false)
  private Instant receivedAt;

  @Column(nullable = false, length = 2000)
  private String message;

  @Column(nullable = false, length = 2000)
  private String reason;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "recorded_by_user_id", nullable = false)
  private User recordedByUser;

  @Column(name = "recorded_by_username", nullable = false, length = 64)
  private String recordedByUsername;

  @Column(name = "recorded_at", nullable = false)
  private Instant recordedAt;

  @Column(name = "source_report_version", nullable = false)
  private long sourceReportVersion;

  public Long getId() {
    return id;
  }

  public void setId(Long value) {
    id = value;
  }

  public SafetyReport getReport() {
    return report;
  }

  public void setReport(SafetyReport value) {
    report = value;
  }

  public String getOutcome() {
    return outcome;
  }

  public void setOutcome(String value) {
    outcome = value;
  }

  public String getReference() {
    return reference;
  }

  public void setReference(String value) {
    reference = value;
  }

  public Instant getReceivedAt() {
    return receivedAt;
  }

  public void setReceivedAt(Instant value) {
    receivedAt = value;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String value) {
    message = value;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String value) {
    reason = value;
  }

  public User getRecordedByUser() {
    return recordedByUser;
  }

  public void setRecordedByUser(User value) {
    recordedByUser = value;
  }

  public String getRecordedByUsername() {
    return recordedByUsername;
  }

  public void setRecordedByUsername(String value) {
    recordedByUsername = value;
  }

  public Instant getRecordedAt() {
    return recordedAt;
  }

  public void setRecordedAt(Instant value) {
    recordedAt = value;
  }

  public long getSourceReportVersion() {
    return sourceReportVersion;
  }

  public void setSourceReportVersion(long value) {
    sourceReportVersion = value;
  }
}
