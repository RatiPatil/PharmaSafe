package com.pharmasafe.medicalteam;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicalConclusionRepository extends JpaRepository<MedicalConclusion, Long> {
  Optional<MedicalConclusion> findFirstByAssignmentIdAndSourceCaseVersionOrderByIdDesc(
      Long id, long sourceCaseVersion);

  List<MedicalConclusion> findTop20ByAssignmentIdOrderByIdDesc(Long assignmentId);
}
