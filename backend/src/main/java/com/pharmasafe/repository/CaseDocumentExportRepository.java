package com.pharmasafe.repository;
import com.pharmasafe.entity.CaseDocumentExport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.*;
public interface CaseDocumentExportRepository extends JpaRepository<CaseDocumentExport,String> {
 Page<CaseDocumentExport> findBySafetyCaseId(Long id,Pageable pageable);
}
