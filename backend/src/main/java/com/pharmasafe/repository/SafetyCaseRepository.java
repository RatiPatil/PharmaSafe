package com.pharmasafe.repository;

import com.pharmasafe.entity.SafetyCase;
import com.pharmasafe.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SafetyCaseRepository extends JpaRepository<SafetyCase, Long> {

    Optional<SafetyCase> findByCaseNumber(String caseNumber);

    Optional<SafetyCase> findByCaseNumberIgnoreCase(String caseNumber);

    List<SafetyCase> findByAssignedToUserOrderByCreatedAtDesc(User user);

    List<SafetyCase> findAllByOrderByCreatedAtDesc();

    long countByAssignedToUser(User user);

    @Query("SELECT COUNT(c) FROM SafetyCase c WHERE c.workflowState IN :states")
    long countByWorkflowStateIn(@Param("states") List<String> states);

    @Query("SELECT COUNT(c) FROM SafetyCase c WHERE c.assignedToUser = :user AND c.workflowState IN :states")
    long countByAssignedToUserAndWorkflowStateIn(@Param("user") User user, @Param("states") List<String> states);

    @Query("SELECT COUNT(c) FROM SafetyCase c WHERE c.serious = true")
    long countSeriousCases();

    @Query("SELECT COUNT(c) FROM SafetyCase c WHERE c.priority = :priority")
    long countByPriority(@Param("priority") String priority);

    @Query("SELECT c FROM SafetyCase c WHERE " +
           "(:search IS NULL OR :search = '' OR LOWER(c.caseNumber) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(c.narrative) LIKE LOWER(CONCAT('%', :search, '%'))) AND " +
           "(:country IS NULL OR :country = '' OR c.countryCode = :country) AND " +
           "(:workflowState IS NULL OR :workflowState = '' OR c.workflowState = :workflowState) AND " +
           "(:reportType IS NULL OR :reportType = '' OR c.reportType = :reportType) " +
           "ORDER BY c.createdAt DESC")
    List<SafetyCase> searchCases(
            @Param("search") String search,
            @Param("country") String country,
            @Param("workflowState") String workflowState,
            @Param("reportType") String reportType
    );

    @Query(value = "SELECT c FROM SafetyCase c WHERE " +
           "(:assignedUsername IS NULL OR :assignedUsername = '' OR (c.assignedToUser IS NOT NULL AND LOWER(c.assignedToUser.username) = LOWER(:assignedUsername))) AND " +
           "(:search IS NULL OR :search = '' OR LOWER(c.caseNumber) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(c.narrative) LIKE LOWER(CONCAT('%', :search, '%'))) AND " +
           "(:country IS NULL OR :country = '' OR c.countryCode = :country) AND " +
           "(:workflowState IS NULL OR :workflowState = '' OR c.workflowState = :workflowState) AND " +
           "(:priority IS NULL OR :priority = '' OR c.priority = :priority) AND " +
           "(:reportType IS NULL OR :reportType = '' OR c.reportType = :reportType)",
           countQuery = "SELECT COUNT(c) FROM SafetyCase c WHERE " +
           "(:assignedUsername IS NULL OR :assignedUsername = '' OR (c.assignedToUser IS NOT NULL AND LOWER(c.assignedToUser.username) = LOWER(:assignedUsername))) AND " +
           "(:search IS NULL OR :search = '' OR LOWER(c.caseNumber) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(c.narrative) LIKE LOWER(CONCAT('%', :search, '%'))) AND " +
           "(:country IS NULL OR :country = '' OR c.countryCode = :country) AND " +
           "(:workflowState IS NULL OR :workflowState = '' OR c.workflowState = :workflowState) AND " +
           "(:priority IS NULL OR :priority = '' OR c.priority = :priority) AND " +
           "(:reportType IS NULL OR :reportType = '' OR c.reportType = :reportType)")
    Page<SafetyCase> findPaginatedCases(
            @Param("assignedUsername") String assignedUsername,
            @Param("search") String search,
            @Param("country") String country,
            @Param("workflowState") String workflowState,
            @Param("priority") String priority,
            @Param("reportType") String reportType,
            Pageable pageable
    );
}
