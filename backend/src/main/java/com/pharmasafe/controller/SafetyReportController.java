package com.pharmasafe.controller;

import com.pharmasafe.dto.SafetyReportDto;
import com.pharmasafe.service.SafetyReportService;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
@PreAuthorize("hasAnyRole('ADMIN', 'SAFETY_MANAGER', 'REPORTING_SPECIALIST', 'AUDITOR')")
public class SafetyReportController {

    private final SafetyReportService safetyReportService;

    public SafetyReportController(SafetyReportService safetyReportService) {
        this.safetyReportService = safetyReportService;
    }

    @GetMapping
    public ResponseEntity<Page<SafetyReportDto>> searchReports(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String reportType,
            @RequestParam(required = false) String product,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<SafetyReportDto> results = safetyReportService.searchReports(status, reportType, product, page, size);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SafetyReportDto> getReportById(@PathVariable Long id) {
        SafetyReportDto dto = safetyReportService.getReportById(id);
        return ResponseEntity.ok(dto);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SAFETY_MANAGER', 'REPORTING_SPECIALIST')")
    public ResponseEntity<SafetyReportDto> createReport(
            @RequestBody SafetyReportDto requestDto,
            Authentication authentication
    ) {
        String username = authentication != null ? authentication.getName() : "anonymous";
        SafetyReportDto created = safetyReportService.createReport(requestDto, username);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SAFETY_MANAGER', 'REPORTING_SPECIALIST')")
    public ResponseEntity<SafetyReportDto> updateReport(
            @PathVariable Long id,
            @RequestBody SafetyReportDto requestDto,
            Authentication authentication
    ) {
        String username = authentication != null ? authentication.getName() : "anonymous";
        SafetyReportDto updated = safetyReportService.updateReport(id, requestDto, username);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyRole('ADMIN', 'SAFETY_MANAGER', 'REPORTING_SPECIALIST')")
    public ResponseEntity<SafetyReportDto> submitReport(
            @PathVariable Long id,
            Authentication authentication
    ) {
        String username = authentication != null ? authentication.getName() : "anonymous";
        SafetyReportDto submitted = safetyReportService.submitReport(id, username);
        return ResponseEntity.ok(submitted);
    }

    @GetMapping("/expedited/summary")
    public ResponseEntity<Map<String, Object>> getExpeditedSummary() {
        Map<String, Object> summary = safetyReportService.getExpeditedSummary();
        return ResponseEntity.ok(summary);
    }

    @PostMapping("/auto-classify")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> autoClassify(
            @RequestBody Map<String, Object> params
    ) {
        boolean serious = Boolean.TRUE.equals(params.get("serious"));
        boolean fatal = Boolean.TRUE.equals(params.get("fatal"));
        boolean lifeThreatening = Boolean.TRUE.equals(params.get("lifeThreatening"));
        String expectedness = params.get("expectedness") != null ? String.valueOf(params.get("expectedness")) : "Unexpected";
        boolean unexpected = "Unexpected".equalsIgnoreCase(expectedness) || "Unlisted".equalsIgnoreCase(expectedness);

        String classification;
        int deadlineDays;
        String rationale;

        if ((fatal || lifeThreatening) && unexpected) {
            classification = "7-day";
            deadlineDays = 7;
            rationale = "Fatal or life-threatening adverse event that is unexpected / unlisted requires expedited 7-day reporting under FDA 21 CFR 312.32 / ICH E2A.";
        } else if (serious && unexpected) {
            classification = "15-day";
            deadlineDays = 15;
            rationale = "Serious and unexpected adverse event requires expedited 15-day safety reporting to regulatory authorities.";
        } else if (serious && !unexpected) {
            classification = "Periodic";
            deadlineDays = 60;
            rationale = "Serious but expected adverse event. To be included in periodic aggregate reporting (e.g. PBRER / PSUR).";
        } else {
            classification = "Periodic";
            deadlineDays = 90;
            rationale = "Non-serious adverse event. Monitored for safety signals and periodic safety update reporting.";
        }

        Map<String, Object> result = new HashMap<>();
        result.put("classification", classification);
        result.put("deadlineDays", deadlineDays);
        result.put("rationale", rationale);
        result.put("requiresExpeditedReporting", "7-day".equals(classification) || "15-day".equals(classification));

        return ResponseEntity.ok(result);
    }
}
