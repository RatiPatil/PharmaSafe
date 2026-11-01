package com.pharmasafe.dto;
import com.pharmasafe.entity.CaseFollowUp;
import java.time.*;
public record FollowUpResponse(Long id,Long caseId,int followUpNumber,String kind,LocalDate receivedDate,
 LocalDate safetyReceivedDate,boolean significant,String justification,String summary,String sourceReference,
 int previousCaseVersion,int caseVersion,Long relatedActionItemId,String createdByUsername,Instant createdAt) {
 public static FollowUpResponse from(CaseFollowUp f) {
  return new FollowUpResponse(f.getId(),f.getSafetyCase().getId(),f.getFollowUpNumber(),f.getKind(),f.getReceivedDate(),
   f.getSafetyReceivedDate(),f.getSignificant(),f.getJustification(),f.getSummary(),f.getSourceReference(),
   f.getPreviousCaseVersion(),f.getCaseVersion(),f.getRelatedActionItem()==null?null:f.getRelatedActionItem().getId(),f.getCreatedByUsername(),f.getCreatedAt());
 }
}
