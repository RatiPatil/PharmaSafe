package com.pharmasafe.repository;
import com.pharmasafe.entity.CaseDocumentExport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.pharmasafe.dto.CaseDocumentDto;
public interface CaseDocumentExportRepository extends JpaRepository<CaseDocumentExport,String> {
 @Query(value="SELECT new com.pharmasafe.dto.CaseDocumentDto(d.id,d.safetyCase.id,d.documentKind,d.finalDocument,d.caseVersion,d.sourceRowVersion,d.workflowState,d.templateVersion,d.generatedByUsername,d.generatedAt,d.fileName,d.pdfSha256) FROM CaseDocumentExport d WHERE d.safetyCase.id=:id",countQuery="SELECT COUNT(d) FROM CaseDocumentExport d WHERE d.safetyCase.id=:id")
 Page<CaseDocumentDto> findMetadataByCaseId(@Param("id") Long id,Pageable pageable);
 Page<CaseDocumentExport> findBySafetyCaseId(Long id,Pageable pageable);
}
