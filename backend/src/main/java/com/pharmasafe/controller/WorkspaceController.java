package com.pharmasafe.controller;
import com.pharmasafe.dto.*;
import com.pharmasafe.repository.*;
import com.pharmasafe.service.WorkspaceQueueService;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/workspace")
@PreAuthorize("isAuthenticated()")
public class WorkspaceController {
    private final WorkspaceQueueService queue;
    private final UserRepository users;
    private final AuditEventRepository audit;
    private final SafetyReportRepository reports;
    private final CaseActionItemRepository actions;
    public WorkspaceController(WorkspaceQueueService queue, UserRepository users, AuditEventRepository audit, SafetyReportRepository reports, CaseActionItemRepository actions) {
        this.queue = queue; this.users = users; this.audit = audit; this.reports = reports; this.actions = actions;
    }
    @GetMapping("/overview")
    @org.springframework.transaction.annotation.Transactional(readOnly=true)
    public Map<String,Object> overview(Authentication auth) {
        Map<String,Object> data = queue.overview(auth);
        String role = auth.getAuthorities().iterator().next().getAuthority();
        boolean manager = Set.of("ROLE_ADMIN", "ROLE_SAFETY_MANAGER").contains(role);
        if (manager) {
            Map<String,Long> workload = new LinkedHashMap<>();
            for (Object[] row : users.caseWorkload()) workload.put(row[0] == null ? "Unassigned" : row[0].toString(), (Long)row[1]);
            data.put("workload", workload);
        }
        if ("ROLE_ADMIN".equals(role)) {
            data.put("totalUsers", users.count());
            data.put("enabledUsers", users.countByEnabledTrue());
        }
        if (Set.of("ROLE_ADMIN", "ROLE_AUDITOR").contains(role)) {
            data.put("auditEvents", audit.count());
            data.put("recentAudit", audit.findTop50ByOrderByCreatedAtDesc().stream().limit(6).map(AuditEventResponseDto::fromEntity).toList());
        }
        if (Set.of("ROLE_ADMIN", "ROLE_SAFETY_MANAGER", "ROLE_REPORTING_SPECIALIST", "ROLE_AUDITOR").contains(role)) {
            data.put("totalReports", reports.count());
            data.put("overdueReports", reports.countByStatusNotAndDueDateBefore("Submitted", java.time.LocalDate.now()));
            data.put("upcomingReports", reports.findByStatusNotOrderByDueDateAscIdAsc("Submitted", PageRequest.of(0,6)).map(SafetyReportDto::fromEntity).getContent());
        }
        if (!Set.of("ROLE_REPORTING_SPECIALIST", "ROLE_AUDITOR").contains(role)) {
            data.put("openActions", actions.countWorkspaceOpen(auth.getName(), role, false, java.time.Instant.now()));
            data.put("overdueActions", actions.countWorkspaceOpen(auth.getName(), role, true, java.time.Instant.now()));
        }
        return data;
    }

    @GetMapping("/cases")
    public Page<CaseSummaryDto> cases(Authentication auth, @RequestParam(required=false) String search,
            @RequestParam(required=false) String state, @RequestParam(required=false) String priority, @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size) {
        return queue.queue(auth, search, state, priority, null, null, false,
                PageRequest.of(Math.max(0, page), Math.max(1, Math.min(50, size)),
                auth.getAuthorities().stream().anyMatch(a -> Set.of("ROLE_MEDICAL_REVIEWER", "ROLE_QUALITY_REVIEWER").contains(a.getAuthority()))
                    ? Sort.by(Sort.Order.asc("initialReceiptDate"), Sort.Order.asc("id"))
                    : Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.asc("id"))));
    }
    @GetMapping("/assignees")
    @PreAuthorize("hasAnyRole('ADMIN', 'SAFETY_MANAGER')")
    public List<Map<String,Object>> assignees() {
        return users.findAll().stream().filter(u -> u.isEnabled() && !u.isLocked())
            .filter(u -> Set.of("ROLE_ADMIN", "ROLE_CASE_PROCESSOR", "ROLE_USER", "ROLE_MEDICAL_REVIEWER",
                    "ROLE_QUALITY_REVIEWER", "ROLE_SAFETY_MANAGER").contains(u.getRole()))
            .map(u -> Map.<String,Object>of("id", u.getId(), "username", u.getUsername(), "role", u.getRole())).toList();
    }
    @GetMapping("/audit/paged")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
    public Page<AuditEventResponseDto> auditPage(@RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size, @RequestParam(defaultValue="") String search) {
        return audit.findByUsernameContainingIgnoreCaseOrEventTypeContainingIgnoreCase(search.trim(),search.trim(),
                PageRequest.of(Math.max(0,page),Math.min(50,Math.max(1,size)),
                Sort.by(Sort.Order.desc("createdAt"),Sort.Order.desc("id")))).map(AuditEventResponseDto::fromEntity);
    }
    @GetMapping("/audit")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
    public List<AuditEventResponseDto> audit() {
        return audit.findTop50ByOrderByCreatedAtDesc().stream().map(AuditEventResponseDto::fromEntity).toList();
    }
}
