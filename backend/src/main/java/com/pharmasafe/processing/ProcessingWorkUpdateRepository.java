package com.pharmasafe.processing;

public interface ProcessingWorkUpdateRepository
    extends org.springframework.data.jpa.repository.JpaRepository<ProcessingWorkUpdate, Long>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<ProcessingWorkUpdate> {
  org.springframework.data.domain.Page<ProcessingWorkUpdate>
      findByAssignmentIdOrderByRecordedAtDescIdDesc(
          Long id, org.springframework.data.domain.Pageable pageable);
}
