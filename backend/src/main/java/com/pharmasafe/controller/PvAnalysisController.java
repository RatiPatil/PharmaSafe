package com.pharmasafe.controller;

import com.pharmasafe.analytics.PvAnalysisService;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pv-analyses")
@PreAuthorize("hasAnyRole('ADMIN','PV_ANALYST','SAFETY_MANAGER','SCOPED_ADMIN')")
public class PvAnalysisController {
  private final PvAnalysisService service;
  private final com.pharmasafe.analytics.PvCaseSeriesService series;
  private final com.pharmasafe.analytics.PvAnalysisReviewService reviews;

  public PvAnalysisController(
      PvAnalysisService s,
      com.pharmasafe.analytics.PvCaseSeriesService cs,
      com.pharmasafe.analytics.PvAnalysisReviewService rs) {
    series = cs;
    reviews = rs;
    service = s;
  }

  @GetMapping("/work-summary")
  public Map<String, Object> summary(
      Authentication a, @RequestParam(required = false) Long userId) {
    return service.workSummary(a.getName(), userId);
  }

  @GetMapping("/trends")
  public Map<String, Object> trends(Authentication a) {
    return service.trends(a.getName());
  }

  @GetMapping
  public Page<Map<String, Object>> list(
      Authentication a,
      @RequestParam(required = false) Long userId,
      @RequestParam(defaultValue = "0") int page) {
    return service.list(a.getName(), userId, page);
  }

  @PostMapping
  @PreAuthorize("hasRole('PV_ANALYST')")
  public ResponseEntity<Map<String, Object>> create(
      Authentication a, @Valid @RequestBody PvAnalysisService.Create d) {
    return ResponseEntity.status(201).body(service.create(a.getName(), d));
  }

  @PostMapping("/series/preview")
  public Page<com.pharmasafe.dto.CaseSummaryDto> preview(
      Authentication a,
      @Valid @RequestBody com.pharmasafe.analytics.PvCaseSeriesService.Criteria d,
      @RequestParam(defaultValue = "0") int page) {
    return series.preview(a.getName(), d, page);
  }

  @GetMapping("/series")
  public Page<Map<String, Object>> series(
      Authentication a,
      @RequestParam(required = false) Long userId,
      @RequestParam(defaultValue = "0") int page) {
    return series.list(a.getName(), userId, page);
  }

  @PostMapping("/series")
  @PreAuthorize("hasRole('PV_ANALYST')")
  public ResponseEntity<Map<String, Object>> saveSeries(
      Authentication a, @Valid @RequestBody com.pharmasafe.analytics.PvCaseSeriesService.Create d) {
    return ResponseEntity.status(201).body(series.create(a.getName(), d));
  }

  @GetMapping("/series/{id}")
  public Map<String, Object> seriesDetail(Authentication a, @PathVariable String id) {
    return series.detail(a.getName(), id);
  }

  @GetMapping("/series/{id}/pdf")
  public ResponseEntity<byte[]> seriesPdf(Authentication a, @PathVariable String id) {
    return pdf(series.download(a.getName(), id));
  }

  @GetMapping("/{id}/reviews")
  public Page<Map<String, Object>> reviewHistory(
      Authentication a, @PathVariable String id, @RequestParam(defaultValue = "0") int page) {
    return reviews.list(a.getName(), id, page);
  }

  @PostMapping("/{id}/reviews")
  @PreAuthorize("hasAnyRole('ADMIN','SAFETY_MANAGER')")
  public ResponseEntity<Map<String, Object>> review(
      Authentication a,
      @PathVariable String id,
      @Valid @RequestBody com.pharmasafe.analytics.PvAnalysisReviewService.Create d) {
    return ResponseEntity.status(201).body(reviews.create(a.getName(), id, d));
  }

  @GetMapping("/{id}/reviews/{reviewId}/pdf")
  public ResponseEntity<byte[]> reviewPdf(
      Authentication a, @PathVariable String id, @PathVariable Long reviewId) {
    return pdf(reviews.download(a.getName(), id, reviewId));
  }

  private ResponseEntity<byte[]> pdf(com.pharmasafe.service.CaseDocumentService.Download d) {
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

  @GetMapping("/{id}/pdf")
  public ResponseEntity<byte[]> download(Authentication a, @PathVariable String id) {
    var d = service.download(a.getName(), id);
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
