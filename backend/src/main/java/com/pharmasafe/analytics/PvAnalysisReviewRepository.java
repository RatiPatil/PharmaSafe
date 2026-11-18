package com.pharmasafe.analytics;

import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PvAnalysisReviewRepository extends JpaRepository<PvAnalysisReview, Long> {
  Optional<PvAnalysisReview> findFirstByAnalysisIdOrderByIdDesc(String analysisId);

  Page<PvAnalysisReview> findByAnalysisIdOrderByIdDesc(String analysisId, Pageable page);
}
