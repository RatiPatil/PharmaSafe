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
           "AND (l.safetyCase.assignedToUser = :user OR l.safetyCase.createdByUser = :user) ORDER BY l.contactAt DESC")
    Page<CaseContactLog> findPersonalLogs(@Param("user") User user, Pageable pageable);
    Page<CaseContactLog> findByResponsibleUserOrCreatedByUserOrderByContactAtDesc(User responsibleUser, User createdByUser, Pageable pageable);
    Page<CaseContactLog> findBySafetyCaseIdOrderByContactAtDesc(Long caseId, Pageable pageable);
}
