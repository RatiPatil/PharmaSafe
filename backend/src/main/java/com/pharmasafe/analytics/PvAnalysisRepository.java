package com.pharmasafe.analytics;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;

public interface PvAnalysisRepository
    extends JpaRepository<PvAnalysisRecord, String>, JpaSpecificationExecutor<PvAnalysisRecord> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from PvAnalysisRecord a where a.id=:id")
  Optional<PvAnalysisRecord> lockById(
      @org.springframework.data.repository.query.Param("id") String id);

  Optional<PvAnalysisRecord> findFirstBySupersedesId(String id);

  @Query(
      "select count(a) from PvAnalysisRecord a where (:owner is null or a.analyst.id=:owner) and"
          + " not exists (select n.id from PvAnalysisRecord n where n.supersedesId=a.id) and"
          + " ((:state='AWAITING_REVIEW' and not exists (select r.id from PvAnalysisReview r where"
          + " r.analysis.id=a.id)) or exists (select r.id from PvAnalysisReview r where"
          + " r.analysis.id=a.id and r.outcome=:state and r.id=(select max(last.id) from"
          + " PvAnalysisReview last where last.analysis.id=a.id)))")
  long countCurrentReviewState(
      @org.springframework.data.repository.query.Param("owner") Long owner,
      @org.springframework.data.repository.query.Param("state") String state);
}
