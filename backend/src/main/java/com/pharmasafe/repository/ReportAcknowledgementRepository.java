package com.pharmasafe.repository;

import com.pharmasafe.entity.ReportAcknowledgement;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportAcknowledgementRepository
    extends JpaRepository<ReportAcknowledgement, Long> {
  List<ReportAcknowledgement> findTop50ByReportIdOrderByRecordedAtDescIdDesc(Long reportId);

  List<ReportAcknowledgement> findByReportIdOrderByRecordedAtAscIdAsc(Long reportId);

  long countByReportId(Long reportId);
}
