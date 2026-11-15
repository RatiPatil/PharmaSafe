package com.pharmasafe.processing;

public interface ProcessingMemberRepository
    extends org.springframework.data.jpa.repository.JpaRepository<ProcessingMember, Long>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<ProcessingMember> {
  java.util.Optional<ProcessingMember> findByTeamIdAndUserId(Long teamId, Long userId);

  java.util.List<ProcessingMember> findByUserId(Long id);

  long countByTeamIdAndStatusIn(Long id, java.util.Collection<String> states);

  java.util.List<ProcessingMember> findByTeamIdAndStatus(Long id, String status);
}
