package com.pharmasafe.repository;

import com.pharmasafe.entity.SafetyReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface SafetyReportRepository extends JpaRepository<SafetyReport, Long> {

    List<SafetyReport> findByRelatedCasesId(Long caseId);
    Optional<SafetyReport> findByReportId(String reportId);

    long countByStatus(String status);

    long countByDueDateBeforeAndStatus(LocalDate date, String status);

    @Query("SELECT r FROM SafetyReport r WHERE " +
           "(:status IS NULL OR :status = '' OR r.status = :status) AND " +
           "(:reportType IS NULL OR :reportType = '' OR r.reportType = :reportType) AND " +
           "(:product IS NULL OR :product = '' OR LOWER(r.product) LIKE LOWER(CONCAT('%', :product, '%')))")
    Page<SafetyReport> searchReports(
            @Param("status") String status,
            @Param("reportType") String reportType,
            @Param("product") String product,
            Pageable pageable
    );

    List<SafetyReport> findTop10ByOrderByDueDateAsc();
    long countByStatusNotAndDueDateBefore(String status, java.time.LocalDate date);
    org.springframework.data.domain.Page<SafetyReport> findByStatusNotOrderByDueDateAscIdAsc(String status, org.springframework.data.domain.Pageable page);
    @Query("SELECT COUNT(r) FROM SafetyReport r JOIN r.relatedCases c WHERE c.id=:caseId AND r.reportType IN ('7-day','15-day') AND (r.status<>'Submitted' OR r.submittedDate IS NULL)")
    long countPendingExpedited(@Param("caseId") Long caseId);
    @Query("SELECT COUNT(r) FROM SafetyReport r JOIN r.relatedCases c WHERE c.id=:caseId AND r.status='Submitted' AND r.submittedDate IS NOT NULL")
    long countSubmittedForCase(@Param("caseId") Long caseId);
}
