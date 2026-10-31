package com.pharmasafe.controller;

import com.pharmasafe.dto.CaseSummaryDto;
import com.pharmasafe.dto.DashboardSummaryDto;
import com.pharmasafe.dto.RecentActivityDto;
import com.pharmasafe.service.DashboardService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/dashboard")
@PreAuthorize("isAuthenticated()")
public class DashboardController {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "createdAt", "updatedAt", "caseNumber", "initialReceiptDate", "latestReceivedDate", "priority", "workflowState"
    );

    private final com.pharmasafe.service.WorkspaceQueueService queue;
    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService, com.pharmasafe.service.WorkspaceQueueService queue) {
        this.queue = queue;
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('ADMIN', 'SAFETY_MANAGER', 'AUDITOR')")
    public ResponseEntity<DashboardSummaryDto> getSummary(Authentication authentication) {
        String username = authentication != null ? authentication.getName() : null;
        DashboardSummaryDto summary = dashboardService.getSummary(username);
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/activity")
    public ResponseEntity<List<RecentActivityDto>> getRecentActivity(Authentication authentication) {
        String username = authentication != null ? authentication.getName() : null;
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        List<RecentActivityDto> activity = dashboardService.getRecentActivity(username, isAdmin);
        return ResponseEntity.ok(activity);
    }

    @GetMapping("/worklist")
    public ResponseEntity<Page<CaseSummaryDto>> getWorklist(
            @RequestParam(required = false, defaultValue = "false") boolean assignedOnly,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String workflowState,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String reportType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction,
            Authentication authentication
    ) {
        int boundedSize = Math.max(1, Math.min(size, 50));
        String cleanSortBy = ALLOWED_SORT_FIELDS.contains(sortBy) ? sortBy : "createdAt";
        Sort sort = "asc".equalsIgnoreCase(direction) ? Sort.by(cleanSortBy).ascending() : Sort.by(cleanSortBy).descending();
        Pageable pageable = PageRequest.of(Math.max(0, page), boundedSize, sort);

        Page<CaseSummaryDto> dtoPage = queue.queue(authentication, search, workflowState, priority,
                country, reportType, assignedOnly, pageable);
        return ResponseEntity.ok(dtoPage);
    }
}
