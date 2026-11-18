package com.pharmasafe.medicalteam;

public interface MedicalMemberRepository
    extends org.springframework.data.jpa.repository.JpaRepository<MedicalMember, Long>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<MedicalMember> {
  java.util.Optional<MedicalMember> findByTeamIdAndUserId(Long teamId, Long userId);

  java.util.List<MedicalMember> findByUserId(Long id);

  long countByTeamIdAndStatusIn(Long id, java.util.Collection<String> states);

  java.util.List<MedicalMember> findByTeamIdAndStatus(Long id, String status);
}
