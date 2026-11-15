package com.pharmasafe.processing;

public interface ProcessingAssignmentRepository
    extends org.springframework.data.jpa.repository.JpaRepository<ProcessingAssignment, Long>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<ProcessingAssignment> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query(
      "select a from ProcessingAssignment a where a.id=:id")
  java.util.Optional<ProcessingAssignment> lockById(
      @org.springframework.data.repository.query.Param("id") Long id);
}
