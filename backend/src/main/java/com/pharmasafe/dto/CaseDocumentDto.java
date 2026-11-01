package com.pharmasafe.dto;
import com.pharmasafe.entity.CaseDocumentExport;
import jakarta.validation.constraints.*;
import java.time.Instant;
public record CaseDocumentDto(String id,Long caseId,String documentKind,boolean finalDocument,int caseVersion,
 long sourceRowVersion,String workflowState,String templateVersion,String generatedByUsername,Instant generatedAt,
 String fileName,String pdfSha256) {
 public record Request(@NotBlank @Pattern(regexp="CASE_FORM|MEDICAL_SUMMARY") String documentKind,
  boolean finalDocument,@NotNull Long expectedRowVersion) {}
 public static CaseDocumentDto from(CaseDocumentExport d){return new CaseDocumentDto(d.getId(),d.getSafetyCase().getId(),d.getDocumentKind(),d.getFinalDocument(),d.getCaseVersion(),d.getSourceRowVersion(),d.getWorkflowState(),d.getTemplateVersion(),d.getGeneratedByUsername(),d.getGeneratedAt(),d.getFileName(),d.getPdfSha256());}
}
