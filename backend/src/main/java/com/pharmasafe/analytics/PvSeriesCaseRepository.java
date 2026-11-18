package com.pharmasafe.analytics;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PvSeriesCaseRepository extends JpaRepository<PvSeriesCase, Long> {
  boolean existsBySeriesIdAndSafetyCaseId(String seriesId, Long caseId);
}
