package com.pharmasafe.repository;

import com.pharmasafe.entity.ReportDocumentExport;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportDocumentExportRepository
    extends JpaRepository<ReportDocumentExport, String> {
  List<ReportDocumentExport> findTop50ByReportIdOrderByGeneratedAtDescIdDesc(Long reportId);

  long countByReportId(Long reportId);
}
