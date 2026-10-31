package com.pharmasafe.repository;

import com.pharmasafe.entity.CaseContactLog;
import com.pharmasafe.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CaseContactLogRepository extends JpaRepository<CaseContactLog, Long> {
    @Query("SELECT l FROM CaseContactLog l WHERE (l.responsibleUser = :user OR l.createdByUser = :user) " +
           "AND ((:role IN ('ROLE_CASE_PROCESSOR', 'ROLE_USER') AND (l.safetyCase.assignedToUser = :user OR l.safetyCase.createdByUser = :user)) " +
           "OR (:role = 'ROLE_MEDICAL_REVIEWER' AND l.safetyCase.workflowState = 'Medical Review') " +
           "OR (:role = 'ROLE_QUALITY_REVIEWER' AND l.safetyCase.workflowState = 'Quality Review')) ORDER BY l.contactAt DESC")
    Page<CaseContactLog> findPersonalLogs(@Param("user") User user, @Param("role") String role, Pageable pageable);
    Page<CaseContactLog> findByResponsibleUserOrCreatedByUserOrderByContactAtDesc(User responsibleUser, User createdByUser, Pageable pageable);
    Page<CaseContactLog> findBySafetyCaseIdOrderByContactAtDesc(Long caseId, Pageable pageable);
}
