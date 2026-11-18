package com.pharmasafe.analytics;

import com.pharmasafe.entity.*;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "pv_analysis_records")
public class PvAnalysisRecord {
  public String getId() {
    return id;
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
  public String title;

  @Column(nullable = false, length = 3000)
  public String objective;

  @Column(nullable = false, length = 5000)
  public String findings;

  @Column(nullable = false, length = 3000)
  public String recommendation;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "case_id")
  public SafetyCase safetyCase;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "series_id")
  public PvCaseSeries series;

  @Column(name = "supersedes_id", length = 36, unique = true)
  public String supersedesId;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;

  @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
  @Column(name = "snapshot_json", columnDefinition = "jsonb", nullable = false)
  public String snapshotJson;

  @Column(name = "pdf_bytes", columnDefinition = "bytea", nullable = false)
  public byte[] pdfBytes;

  @Column(name = "pdf_sha256", length = 64, nullable = false)
  public String pdfSha256;
}
