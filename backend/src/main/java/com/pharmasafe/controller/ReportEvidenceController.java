package com.pharmasafe.controller;

import com.pharmasafe.service.ReportEvidenceService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reports/{reportId}")
@PreAuthorize(
    "hasAnyRole('ADMIN','SAFETY_MANAGER','REPORTING_SPECIALIST','AUDITOR','SCOPED_ADMIN')")
public class ReportEvidenceController {
  private final ReportEvidenceService service;

  public ReportEvidenceController(ReportEvidenceService s) {
    service = s;
  }

  @GetMapping("/evidence")
  public Map<String, Object> detail(@PathVariable Long reportId, Authentication a) {
    return service.detail(reportId, a.getName());
  }

  @PostMapping("/acknowledgements")
  @PreAuthorize("hasAnyRole('ADMIN','SAFETY_MANAGER','REPORTING_SPECIALIST')")
  public ResponseEntity<Map<String, Object>> acknowledge(
      @PathVariable Long reportId,
      @Valid @RequestBody ReportEvidenceService.Acknowledgement request,
      Authentication a) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(service.acknowledge(reportId, request, a.getName()));
  }

  @PostMapping("/documents")
  public ResponseEntity<Map<String, Object>> archive(
      @PathVariable Long reportId,
      @Valid @RequestBody ReportEvidenceService.Version request,
      Authentication a) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(service.archive(reportId, request, a.getName()));
  }

  @GetMapping("/documents/{documentId}/pdf")
  public ResponseEntity<byte[]> download(
      @PathVariable Long reportId, @PathVariable String documentId, Authentication a) {
    var d = service.download(reportId, documentId, a.getName());
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .cacheControl(CacheControl.noStore())
        .header("Pragma", "no-cache")
        .header("X-Content-Type-Options", "nosniff")
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(d.fileName()).build().toString())
        .body(d.bytes());
  }
}
