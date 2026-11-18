package com.pharmasafe.medicalteam;

public interface MedicalAssignmentRepository
    extends org.springframework.data.jpa.repository.JpaRepository<MedicalAssignment, Long>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<MedicalAssignment> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select a from MedicalAssignment a where a.id=:id")
  java.util.Optional<MedicalAssignment> lockById(
      @org.springframework.data.repository.query.Param("id") Long id);
}
