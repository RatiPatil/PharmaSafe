package com.pharmasafe.repository;

import com.pharmasafe.entity.CaseActionItem;
import com.pharmasafe.entity.User;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CaseActionItemRepository extends JpaRepository<CaseActionItem, Long> {
    @Query("SELECT a FROM CaseActionItem a WHERE a.assignedUser = :user " +
           "AND ((:role IN ('ROLE_CASE_PROCESSOR', 'ROLE_USER') AND (a.safetyCase.assignedToUser = :user OR a.safetyCase.createdByUser = :user)) " +
           "OR (:role = 'ROLE_MEDICAL_REVIEWER' AND a.safetyCase.workflowState = 'Medical Review') " +
           "OR (:role = 'ROLE_QUALITY_REVIEWER' AND a.safetyCase.workflowState = 'Quality Review')) " +
           "AND (:overdue = false OR (a.dueAt < CURRENT_TIMESTAMP AND a.status NOT IN ('COMPLETED', 'CANCELLED'))) " +
           "ORDER BY a.dueAt ASC, a.id ASC")
    Page<CaseActionItem> findPersonalItems(@Param("user") User user, @Param("role") String role, @Param("overdue") boolean overdue, Pageable pageable);

    Page<CaseActionItem> findBySafetyCaseIdOrderByDueAtAscIdAsc(Long caseId, Pageable pageable);
    @org.springframework.data.jpa.repository.Query("SELECT i FROM CaseActionItem i WHERE :overdue = false OR (i.dueAt < :now AND i.status NOT IN ('COMPLETED', 'CANCELLED')) ORDER BY i.dueAt ASC, i.id ASC")
    Page<CaseActionItem> findTeamItems(@org.springframework.data.repository.query.Param("overdue") boolean overdue,
            @org.springframework.data.repository.query.Param("now") Instant now, Pageable pageable);
    @Query("SELECT COUNT(a) FROM CaseActionItem a JOIN a.safetyCase c LEFT JOIN c.assignedToUser assignee LEFT JOIN c.createdByUser creator WHERE a.status NOT IN ('COMPLETED','CANCELLED') " +
        "AND (:overdue = false OR a.dueAt < :now) AND (:role IN ('ROLE_ADMIN','ROLE_SAFETY_MANAGER') OR " +
        "(a.assignedUser.username = :username AND ((:role IN ('ROLE_USER','ROLE_CASE_PROCESSOR') AND " +
        "(assignee.username = :username OR creator.username = :username)) " +
        "OR (:role = 'ROLE_MEDICAL_REVIEWER' AND a.safetyCase.workflowState = 'Medical Review') " +
        "OR (:role = 'ROLE_QUALITY_REVIEWER' AND a.safetyCase.workflowState = 'Quality Review'))))")
    long countWorkspaceOpen(@Param("username") String username, @Param("role") String role,
            @Param("overdue") boolean overdue, @Param("now") Instant now);
}
