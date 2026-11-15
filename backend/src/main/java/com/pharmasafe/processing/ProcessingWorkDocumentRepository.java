package com.pharmasafe.processing;

public interface ProcessingWorkDocumentRepository
    extends org.springframework.data.jpa.repository.JpaRepository<ProcessingWorkDocument, String>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<ProcessingWorkDocument> {
  org.springframework.data.domain.Page<ProcessingWorkDocument>
      findByAssignmentIdOrderByGeneratedAtDescIdDesc(
          Long id, org.springframework.data.domain.Pageable pageable);
}
