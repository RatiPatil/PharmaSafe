package com.pharmasafe.repository;

import com.pharmasafe.entity.CaseActionItem;
import com.pharmasafe.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CaseActionItemRepository extends JpaRepository<CaseActionItem, Long> {
    @Query("SELECT a FROM CaseActionItem a WHERE a.assignedUser = :user " +
           "AND (a.safetyCase.assignedToUser = :user OR a.safetyCase.createdByUser = :user) " +
           "AND (:overdue = false OR (a.dueAt < CURRENT_TIMESTAMP AND a.status NOT IN ('COMPLETED', 'CANCELLED'))) " +
           "ORDER BY a.dueAt ASC, a.id ASC")
    Page<CaseActionItem> findPersonalItems(@Param("user") User user, @Param("overdue") boolean overdue, Pageable pageable);

    Page<CaseActionItem> findBySafetyCaseIdOrderByDueAtAscIdAsc(Long caseId, Pageable pageable);
}
