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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
    public ResponseEntity<CaseResponseDto> getCaseById(@PathVariable Long id, Authentication authentication) {
        String role = authentication.getAuthorities().stream().findFirst().map(a -> a.getAuthority()).orElse("");
        CaseResponseDto response = caseService.getCaseByIdAuthorized(id, authentication.getName(), role);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/by-number/{caseNumber}")
    public ResponseEntity<CaseResponseDto> getCaseByCaseNumber(@PathVariable String caseNumber, Authentication authentication) {
        String role = authentication.getAuthorities().stream().findFirst().map(a -> a.getAuthority()).orElse("");
        CaseResponseDto response = caseService.getCaseByCaseNumberAuthorized(caseNumber, authentication.getName(), role);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<CaseSummaryDto>> searchCases(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String workflowState,
            @RequestParam(required = false) String reportType,
            Authentication authentication
    ) {
        String role = authentication.getAuthorities().stream().findFirst().map(a -> a.getAuthority()).orElse("");
        List<CaseSummaryDto> results = caseService.searchCasesAuthorized(
                search, country, workflowState, reportType, authentication.getName(), role);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/assigned")
    public ResponseEntity<List<CaseSummaryDto>> getAssignedCases(Authentication authentication) {
        String username = authentication != null ? authentication.getName() : null;
        List<CaseSummaryDto> assignedCases = caseService.getAssignedCases(username);
        return ResponseEntity.ok(assignedCases);
    }

    @GetMapping("/assigned/paged")
    public ResponseEntity<Page<CaseSummaryDto>> getAssignedCasesPaged(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        String username = authentication.getName();
        com.pharmasafe.entity.User user = caseService.findUser(username);
        Page<CaseSummaryDto> assigned = caseService.getAssignedCasesPage(
                user, PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 50)),
                        Sort.by(Sort.Direction.DESC, "updatedAt").and(Sort.by(Sort.Direction.ASC, "id"))));
        return ResponseEntity.ok(assigned);
    }

    @PostMapping("/{id}/lock")
    @PreAuthorize("hasAnyRole('ADMIN', 'SAFETY_MANAGER')")
    public ResponseEntity<CaseResponseDto> lockCase(
            @PathVariable Long id,
            Authentication authentication
    ) {
        String username = authentication != null ? authentication.getName() : "anonymous";
        CaseResponseDto response = caseService.lockCase(id, username);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/unlock")
    @PreAuthorize("hasAnyRole('ADMIN', 'SAFETY_MANAGER')")
    public ResponseEntity<CaseResponseDto> unlockCase(
            @PathVariable Long id,
            Authentication authentication
    ) {
        String username = authentication != null ? authentication.getName() : "anonymous";
        CaseResponseDto response = caseService.unlockCase(id, username);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('ADMIN', 'SAFETY_MANAGER')")
    public ResponseEntity<CaseResponseDto> assignCase(
            @PathVariable Long id,
            @RequestBody java.util.Map<String, Object> body,
            Authentication authentication
    ) {
        String username = authentication != null ? authentication.getName() : "anonymous";
        Object userIdObj = body.get("userId");
        if (userIdObj == null) {
            throw new IllegalArgumentException("userId is required for case assignment");
        }
        Long targetUserId = Long.valueOf(userIdObj.toString());
        CaseResponseDto response = caseService.assignCase(id, targetUserId, username);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/workflow-transition")
    public ResponseEntity<CaseResponseDto> transitionWorkflow(
            @PathVariable Long id,
            @RequestBody java.util.Map<String, String> body,
            Authentication authentication
    ) {
        String username = authentication != null ? authentication.getName() : "anonymous";
        String targetState = body.get("targetState");
        String comments = body.get("comments");
        String role = authentication != null
                ? authentication.getAuthorities().stream().findFirst().map(a -> a.getAuthority()).orElse("")
                : "";
        CaseResponseDto response = caseService.transitionWorkflow(id, targetState, comments, username, role);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/audit-history")
    public ResponseEntity<List<com.pharmasafe.dto.AuditEventResponseDto>> getAuditHistory(@PathVariable Long id) {
        return ResponseEntity.ok(caseService.getCaseAuditHistory(id));
    }

    @PostMapping("/{id}/assessments")
    public ResponseEntity<CaseResponseDto> addAssessment(
            @PathVariable Long id,
            @RequestBody CaseRequestDto.AssessmentDto assessmentDto,
            Authentication authentication
    ) {
        String username = authentication != null ? authentication.getName() : "anonymous";
        CaseResponseDto response = caseService.addAssessment(id, assessmentDto, username);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/assessments/calculate-causality")
    public ResponseEntity<java.util.Map<String, String>> calculateCausality(
            @RequestBody java.util.Map<String, Object> params
    ) {
        boolean temporal = Boolean.TRUE.equals(params.get("temporalSequence")) || "Yes".equalsIgnoreCase(String.valueOf(params.get("temporalSequence")));
        String dechallenge = params.get("dechallenge") != null ? String.valueOf(params.get("dechallenge")) : "Unknown";
        String rechallenge = params.get("rechallenge") != null ? String.valueOf(params.get("rechallenge")) : "Not performed";
        boolean altExplanation = Boolean.TRUE.equals(params.get("alternativeExplanation")) || "Yes".equalsIgnoreCase(String.valueOf(params.get("alternativeExplanation")));
        boolean plausible = Boolean.TRUE.equals(params.get("pharmacologicallyPlausible")) || "Yes".equalsIgnoreCase(String.valueOf(params.get("pharmacologicallyPlausible")));

        String suggestedCausality;
        String reasoning;

        if (!temporal) {
            suggestedCausality = "Unlikely";
            reasoning = "Event onset does not have a plausible time relationship to drug administration.";
        } else if ("Positive".equalsIgnoreCase(rechallenge) && "Positive".equalsIgnoreCase(dechallenge) && !altExplanation) {
            suggestedCausality = "Certain";
            reasoning = "Event has plausible time relationship, positive dechallenge, positive rechallenge, and cannot be explained by disease or other drugs.";
        } else if ("Positive".equalsIgnoreCase(dechallenge) && !altExplanation && plausible) {
            suggestedCausality = "Probable";
            reasoning = "Reasonable time sequence, positive dechallenge response, unlikely to be attributed to disease or other drugs.";
        } else if (temporal && !altExplanation) {
            suggestedCausality = "Possible";
            reasoning = "Reasonable time relationship, but information on dechallenge may be lacking or unclear.";
        } else if (altExplanation) {
            suggestedCausality = "Unlikely";
            reasoning = "Alternative disease pathology or concomitant medication provides more plausible explanation.";
        } else {
            suggestedCausality = "Conditional";
            reasoning = "More data needed before definitive assessment can be concluded.";
        }

        java.util.Map<String, String> result = new java.util.HashMap<>();
        result.put("suggestedCausality", suggestedCausality);
        result.put("reasoning", reasoning);
        result.put("disclaimer", "Calculated recommendation based on standard WHO-UMC criteria. Must be reviewed and authorized by a Medical Reviewer.");

        return ResponseEntity.ok(result);
    }

    private String extractClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null || xfHeader.isEmpty() || "unknown".equalsIgnoreCase(xfHeader)) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
    }
}
