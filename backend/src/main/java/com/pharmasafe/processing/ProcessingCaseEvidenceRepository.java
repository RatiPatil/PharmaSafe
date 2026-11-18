package com.pharmasafe.processing;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessingCaseEvidenceRepository
    extends JpaRepository<ProcessingCaseEvidence, Long> {
  List<ProcessingCaseEvidence> findTop50ByAssignmentIdOrderByRecordedAtDescIdDesc(
      Long assignmentId);

  java.util.Optional<ProcessingCaseEvidence>
      findFirstByAssignmentIdAndSafetyCaseIdAndCaseRowVersionAndKindOrderByRecordedAtDescIdDesc(
          Long assignmentId, Long caseId, long revision, String kind);
}
