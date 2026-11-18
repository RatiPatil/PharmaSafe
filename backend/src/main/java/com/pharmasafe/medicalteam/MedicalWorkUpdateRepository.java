package com.pharmasafe.medicalteam;

public interface MedicalWorkUpdateRepository
    extends org.springframework.data.jpa.repository.JpaRepository<MedicalWorkUpdate, Long>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<MedicalWorkUpdate> {
  org.springframework.data.domain.Page<MedicalWorkUpdate>
      findByAssignmentIdOrderByRecordedAtDescIdDesc(
          Long id, org.springframework.data.domain.Pageable pageable);
}
