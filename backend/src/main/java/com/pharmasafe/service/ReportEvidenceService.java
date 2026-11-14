package com.pharmasafe.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pharmasafe.administration.AdminScope;
import com.pharmasafe.dto.SafetyReportDto;
import com.pharmasafe.entity.*;
import com.pharmasafe.repository.*;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReportEvidenceService {
  private final SafetyReportRepository reports;
  private final ReportAcknowledgementRepository acknowledgements;
  private final ReportDocumentExportRepository documents;
  private final AdminScope scope;
  private final ObjectMapper json;
  private final CasePdfRenderer renderer;
  private final AuditService audit;

  public ReportEvidenceService(
      SafetyReportRepository r,
      ReportAcknowledgementRepository a,
      ReportDocumentExportRepository d,
      AdminScope s,
      ObjectMapper j,
      CasePdfRenderer p,
      AuditService au) {
    reports = r;
    acknowledgements = a;
    documents = d;
    scope = s;
    json = j;
    renderer = p;
    audit = au;
  }

  public record Version(@NotNull @PositiveOrZero Long expectedVersion) {}

  public record Acknowledgement(
      @NotNull @PositiveOrZero Long expectedVersion,
      @NotBlank @Pattern(regexp = "ACCEPTED|REJECTED") String outcome,
      @NotBlank @Size(max = 128) String reference,
      @NotNull Instant receivedAt,
      @NotNull @Size(max = 2000) String message,
      @NotBlank @Size(max = 2000) String reason) {}

  private User actor(String username, SafetyReport report, boolean write) {
    User u = scope.actor(username);
    if (Set.of("ROLE_ADMIN", "ROLE_SAFETY_MANAGER", "ROLE_REPORTING_SPECIALIST")
        .contains(u.getRole())) return u;
    if (!write && "ROLE_AUDITOR".equals(u.getRole())) return u;
    if (!write && AdminScope.isRoleAdmin(u)) {
      String own = scope.role(username, "");
      User owner = report.getCreatedByUser();
      if (owner != null && own.equals(AdminScope.canonical(owner.getRole()))) return u;
    }
    throw new AccessDeniedException("Report evidence is outside your role");
  }

  private SafetyReport report(Long id, boolean lock) {
    return (lock ? reports.findLockedById(id) : reports.findById(id))
        .orElseThrow(() -> new EntityNotFoundException("Report not found"));
  }

  private void version(SafetyReport r, Long expected) {
    if (expected == null || expected != r.getRowVersion())
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Report changed. Reload its details before continuing.");
  }

  private Map<String, Object> ackView(ReportAcknowledgement a) {
    return Map.of(
        "id",
        a.getId(),
        "outcome",
        a.getOutcome(),
        "reference",
        a.getReference(),
        "receivedAt",
        a.getReceivedAt(),
        "message",
        a.getMessage(),
        "reason",
        a.getReason(),
        "recordedBy",
        a.getRecordedByUsername(),
        "recordedAt",
        a.getRecordedAt(),
        "sourceReportVersion",
        a.getSourceReportVersion());
  }

  private Map<String, Object> documentView(ReportDocumentExport d) {
    return Map.of(
        "id",
        d.getId(),
        "fileName",
        d.getFileName(),
        "generatedAt",
        d.getGeneratedAt(),
        "generatedBy",
        d.getGeneratedByUsername(),
        "sha256",
        d.getPdfSha256(),
        "sourceReportVersion",
        d.getSourceReportVersion());
  }

  @Transactional(readOnly = true)
  public Map<String, Object> detail(Long id, String username) {
    SafetyReport r = report(id, false);
    actor(username, r, false);
    return Map.of(
        "report",
        SafetyReportDto.fromEntity(r),
        "acknowledgements",
        acknowledgements.findTop50ByReportIdOrderByRecordedAtDescIdDesc(id).stream()
            .map(this::ackView)
            .toList(),
        "documents",
        documents.findTop50ByReportIdOrderByGeneratedAtDescIdDesc(id).stream()
            .map(this::documentView)
            .toList(),
        "acknowledgementCount",
        acknowledgements.countByReportId(id),
        "documentCount",
        documents.countByReportId(id));
  }

  @Transactional
  public Map<String, Object> acknowledge(Long id, Acknowledgement request, String username) {
    SafetyReport r = report(id, true);
    User u = actor(username, r, true);
    version(r, request.expectedVersion());
    Instant now = Instant.now();
    if (!"Submitted".equals(r.getStatus()) || r.getSubmittedDate() == null)
      throw new IllegalArgumentException("Record submission before recording an acknowledgement");
    if (request.receivedAt().isAfter(now)
        || request
            .receivedAt()
            .isBefore(r.getSubmittedDate().truncatedTo(java.time.temporal.ChronoUnit.SECONDS)))
      throw new IllegalArgumentException(
          "Acknowledgement time must be after submission and not in the future");
    ReportAcknowledgement a = new ReportAcknowledgement();
    a.setReport(r);
    a.setOutcome(request.outcome());
    a.setReference(request.reference().trim());
    a.setReceivedAt(request.receivedAt());
    a.setMessage(request.message().trim());
    a.setReason(request.reason().trim());
    a.setRecordedByUser(u);
    a.setRecordedByUsername(u.getUsername());
    a.setRecordedAt(now);
    a.setSourceReportVersion(r.getRowVersion());
    acknowledgements.saveAndFlush(a);
    r.setUpdatedAt(now);
    reports.saveAndFlush(r);
    audit.logEvent(
        "REPORT_ACKNOWLEDGEMENT_RECORDED",
        username,
        null,
        null,
        "SUCCESS",
        "Manual external acknowledgement recorded for "
            + r.getReportId()
            + "; "
            + a.getOutcome()
            + "; reference "
            + a.getReference());
    return ackView(a);
  }

  @Transactional
  public Map<String, Object> archive(Long id, Version request, String username) {
    SafetyReport r = report(id, true);
    User u = actor(username, r, false);
    version(r, request.expectedVersion());
    ReportDocumentExport d = new ReportDocumentExport();
    d.setId(UUID.randomUUID().toString());
    d.setReport(r);
    d.setSourceReportVersion(r.getRowVersion());
    d.setGeneratedByUser(u);
    d.setGeneratedByUsername(username);
    d.setGeneratedAt(Instant.now());
    d.setFileName("PharmaSafe-report-" + id + "-" + d.getId() + ".pdf");
    ObjectNode snapshot = json.createObjectNode();
    ObjectNode control = snapshot.putObject("documentControl");
    control.put("reportId", r.getReportId());
    control.put("documentKind", "REPORT_RECORD");
    control.put("documentId", d.getId());
    control.put("sourceReportVersion", r.getRowVersion());
    control.put("generatedBy", username);
    control.put("generatedAtUtc", d.getGeneratedAt().toString());
    control.put("finalDocument", false);
    control.put(
        "syntheticRecord", r.getRelatedCases().stream().anyMatch(SafetyCase::isSyntheticRecord));
    snapshot.set("reportAtGeneration", json.valueToTree(SafetyReportDto.fromEntity(r)));
    snapshot.put(
        "scope",
        "Internal report tracking and case listing; no regulatory transmission or signature. Counts"
            + " are recorded at scheduling; case listing is captured at PDF generation.");
    var listing = snapshot.putArray("linkedCaseListing");
    for (SafetyCase c : r.getRelatedCases()) {
      var row = listing.addObject();
      row.put("caseNumber", c.getCaseNumber());
      row.put("workflowState", c.getWorkflowState());
      row.put("priority", c.getPriority());
      row.put("serious", c.isSerious());
      row.put("fatal", c.isFatal());
      row.put("syntheticRecord", c.isSyntheticRecord());
      row.put("caseVersion", c.getVersionNumber());
    }
    snapshot.set(
        "acknowledgementHistory",
        json.valueToTree(
            acknowledgements.findByReportIdOrderByRecordedAtAscIdAsc(id).stream()
                .map(this::ackView)
                .toList()));
    snapshot.put("acknowledgementHistoryCount", acknowledgements.countByReportId(id));
    try {
      byte[] source = json.writeValueAsBytes(snapshot);
      if (source.length > 5_000_000)
        throw new IllegalArgumentException("Report snapshot exceeds the 5 MB export limit");
      d.setSnapshotJson(new String(source, java.nio.charset.StandardCharsets.UTF_8));
      byte[] bytes = renderer.render(snapshot, source);
      d.setPdfBytes(bytes);
      d.setPdfSha256(CaseDocumentService.hash(bytes));
      documents.saveAndFlush(d);
      audit.logEvent(
          "REPORT_DOCUMENT_CREATED",
          username,
          null,
          null,
          "SUCCESS",
          "Report "
              + r.getReportId()
              + "; document "
              + d.getId()
              + "; SHA-256 "
              + d.getPdfSha256());
      return documentView(d);
    } catch (java.io.IOException e) {
      throw new IllegalStateException("Report PDF generation failed; no archive was saved", e);
    }
  }

  @Transactional
  public CaseDocumentService.Download download(Long id, String documentId, String username) {
    SafetyReport r = report(id, false);
    actor(username, r, false);
    ReportDocumentExport d =
        documents
            .findById(documentId)
            .filter(v -> v.getReport().getId().equals(id))
            .orElseThrow(() -> new EntityNotFoundException("Report document not found"));
    if (!CaseDocumentService.hash(d.getPdfBytes()).equals(d.getPdfSha256()))
      throw new IllegalStateException("Archived report PDF integrity verification failed");
    audit.logEvent(
        "REPORT_DOCUMENT_DOWNLOADED",
        username,
        null,
        null,
        "SUCCESS",
        "Report " + r.getReportId() + "; document " + documentId);
    return new CaseDocumentService.Download(d.getFileName(), d.getPdfBytes(), d.getPdfSha256());
  }
}
