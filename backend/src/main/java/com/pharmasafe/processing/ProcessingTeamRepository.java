package com.pharmasafe.processing;

public interface ProcessingTeamRepository
    extends org.springframework.data.jpa.repository.JpaRepository<ProcessingTeam, Long>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<ProcessingTeam> {
  java.util.Optional<ProcessingTeam> findByLeadId(Long id);

  java.util.Optional<ProcessingTeam> findByRegistrationKey(String key);

  long countByActiveTrue();

  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select t from ProcessingTeam t where t.id=:id")
  java.util.Optional<ProcessingTeam> lockById(
      @org.springframework.data.repository.query.Param("id") Long id);
}
