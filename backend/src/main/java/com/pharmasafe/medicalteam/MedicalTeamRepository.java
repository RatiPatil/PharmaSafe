package com.pharmasafe.medicalteam;

public interface MedicalTeamRepository
    extends org.springframework.data.jpa.repository.JpaRepository<MedicalTeam, Long>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<MedicalTeam> {
  java.util.Optional<MedicalTeam> findByLeadId(Long id);

  java.util.Optional<MedicalTeam> findByRegistrationKey(String key);

  long countByActiveTrue();

  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select t from MedicalTeam t where t.id=:id")
  java.util.Optional<MedicalTeam> lockById(
      @org.springframework.data.repository.query.Param("id") Long id);
}
