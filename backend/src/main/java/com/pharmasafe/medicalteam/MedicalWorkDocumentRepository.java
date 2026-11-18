package com.pharmasafe.medicalteam;

public interface MedicalWorkDocumentRepository
    extends org.springframework.data.jpa.repository.JpaRepository<MedicalWorkDocument, String>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<MedicalWorkDocument> {
  org.springframework.data.domain.Page<MedicalWorkDocument>
      findByAssignmentIdOrderByGeneratedAtDescIdDesc(
          Long id, org.springframework.data.domain.Pageable pageable);
}
