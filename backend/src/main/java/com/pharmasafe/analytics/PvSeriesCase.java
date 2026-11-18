package com.pharmasafe.analytics;

import com.pharmasafe.entity.SafetyCase;
import jakarta.persistence.*;

@Entity
@Table(
    name = "pv_series_cases",
    uniqueConstraints = @UniqueConstraint(columnNames = {"series_id", "case_id"}))
public class PvSeriesCase {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "series_id")
  public PvCaseSeries series;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "case_id")
  public SafetyCase safetyCase;

  @Column(name = "source_row_version", nullable = false)
  public long sourceRowVersion;

  @Column(name = "case_number", length = 64, nullable = false)
  public String caseNumber;

  @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
  @Column(name = "snapshot_json", columnDefinition = "jsonb", nullable = false)
  public String snapshotJson;
}
