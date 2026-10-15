package com.pharmasafe.repository;

import com.pharmasafe.entity.SafetyCase;
import com.pharmasafe.entity.User;
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
}
