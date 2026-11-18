package com.pharmasafe.analytics;

import com.pharmasafe.entity.User;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "pv_case_series")
public class PvCaseSeries {
  public String getSnapshotJson() {
    return snapshotJson;
  }

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public User getAnalyst() {
    return analyst;
  }

  @Id
  @Column(length = 36)
  public String id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "analyst_user_id")
  public User analyst;

  @Column(nullable = false, length = 160)
  public String name;

  @Column(nullable = false, length = 3000)
  public String purpose;

  @Column(name = "case_count", nullable = false)
  public int caseCount;

  @Column(name = "captured_at", nullable = false)
  public Instant capturedAt;

  @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
  @Column(name = "criteria_json", columnDefinition = "jsonb", nullable = false)
  public String criteriaJson;

  @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
  @Column(name = "snapshot_json", columnDefinition = "jsonb", nullable = false)
  public String snapshotJson;

  @Column(name = "pdf_bytes", columnDefinition = "bytea", nullable = false)
  public byte[] pdfBytes;

  @Column(name = "pdf_sha256", length = 64, nullable = false)
  public String pdfSha256;
}
