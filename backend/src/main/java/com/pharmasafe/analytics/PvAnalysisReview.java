package com.pharmasafe.analytics;

import com.pharmasafe.entity.User;
import jakarta.persistence.*;
import java.time.*;

@Entity
@Table(name = "pv_analysis_reviews")
public class PvAnalysisReview {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "analysis_id")
  public PvAnalysisRecord analysis;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "reviewer_user_id")
  public User reviewer;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "previous_review_id")
  public PvAnalysisReview previous;

  @Column(nullable = false, length = 32)
  public String outcome;

  @Column(nullable = false, length = 3000)
  public String comments;

  @Column(name = "follow_up_due_date")
  public LocalDate followUpDueDate;

  @Column(name = "reviewed_at", nullable = false)
  public Instant reviewedAt;

  @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
  @Column(name = "snapshot_json", columnDefinition = "jsonb", nullable = false)
  public String snapshotJson;

  @Column(name = "pdf_bytes", columnDefinition = "bytea", nullable = false)
  public byte[] pdfBytes;

  @Column(name = "pdf_sha256", length = 64, nullable = false)
  public String pdfSha256;
}
