package com.pharmasafe.repository;

import com.pharmasafe.entity.CaseReviewEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CaseReviewEventRepository extends JpaRepository<CaseReviewEvent, Long> {
    List<CaseReviewEvent> findBySafetyCaseIdOrderByCreatedAtDesc(Long caseId);
}
