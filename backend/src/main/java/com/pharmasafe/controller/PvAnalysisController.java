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

  public PvAnalysisController(PvAnalysisService s) {
    service = s;
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
