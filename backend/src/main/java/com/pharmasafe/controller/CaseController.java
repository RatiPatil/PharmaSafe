package com.pharmasafe.controller;

import com.pharmasafe.dto.CaseRequestDto;
import com.pharmasafe.dto.CaseResponseDto;
import com.pharmasafe.dto.CaseSummaryDto;
import com.pharmasafe.service.CaseService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cases")
@PreAuthorize("isAuthenticated()")
public class CaseController {

    private final CaseService caseService;

    public CaseController(CaseService caseService) {
        this.caseService = caseService;
    }

    @PostMapping
    public ResponseEntity<CaseResponseDto> createCase(
            @Valid @RequestBody CaseRequestDto requestDto,
            Authentication authentication,
            HttpServletRequest request
    ) {
        String username = authentication != null ? authentication.getName() : "anonymous";
        String clientIp = extractClientIp(request);
        String userAgent = request.getHeader("User-Agent");

        CaseResponseDto response = caseService.createCase(requestDto, username, clientIp, userAgent);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<CaseResponseDto> updateCase(
            @PathVariable Long id,
            @Valid @RequestBody CaseRequestDto requestDto,
            Authentication authentication,
            HttpServletRequest request
    ) {
        String username = authentication != null ? authentication.getName() : "anonymous";
        String clientIp = extractClientIp(request);
        String userAgent = request.getHeader("User-Agent");

        CaseResponseDto response = caseService.updateCase(id, requestDto, username, clientIp, userAgent);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CaseResponseDto> getCaseById(@PathVariable Long id) {
        CaseResponseDto response = caseService.getCaseById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/by-number/{caseNumber}")
    public ResponseEntity<CaseResponseDto> getCaseByCaseNumber(@PathVariable String caseNumber) {
        CaseResponseDto response = caseService.getCaseByCaseNumber(caseNumber);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<CaseSummaryDto>> searchCases(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String workflowState,
            @RequestParam(required = false) String reportType
    ) {
        List<CaseSummaryDto> results = caseService.searchCases(search, country, workflowState, reportType);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/assigned")
    public ResponseEntity<List<CaseSummaryDto>> getAssignedCases(Authentication authentication) {
        String username = authentication != null ? authentication.getName() : null;
        List<CaseSummaryDto> assignedCases = caseService.getAssignedCases(username);
        return ResponseEntity.ok(assignedCases);
    }

    private String extractClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null || xfHeader.isEmpty() || "unknown".equalsIgnoreCase(xfHeader)) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
    }
}
