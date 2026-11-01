package com.pharmasafe.repository;
import com.pharmasafe.entity.CaseFollowUp;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.*;
import java.time.LocalDate;
public interface CaseFollowUpRepository extends JpaRepository<CaseFollowUp,Long> {
 Page<CaseFollowUp> findBySafetyCaseId(Long caseId, Pageable page);
 @Query("SELECT COALESCE(MAX(f.followUpNumber),0) FROM CaseFollowUp f WHERE f.safetyCase.id=:caseId")
 int maxNumber(@Param("caseId") Long caseId);
 @Query("SELECT MAX(f.receivedDate) FROM CaseFollowUp f WHERE f.safetyCase.id=:caseId AND f.kind='FOLLOW_UP'")
 LocalDate latestReceipt(@Param("caseId") Long caseId);
 @Query("SELECT MIN(f.receivedDate) FROM CaseFollowUp f WHERE f.safetyCase.id=:caseId")
 LocalDate earliestReceipt(@Param("caseId") Long caseId);
 boolean existsByRelatedActionItemId(Long actionId);
}
