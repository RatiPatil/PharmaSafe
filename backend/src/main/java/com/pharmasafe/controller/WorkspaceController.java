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
    public WorkspaceController(WorkspaceQueueService queue, UserRepository users, AuditEventRepository audit) {
        this.queue = queue; this.users = users; this.audit = audit;
    }
    @GetMapping("/cases")
    public Page<CaseSummaryDto> cases(Authentication auth, @RequestParam(required=false) String search,
            @RequestParam(required=false) String state, @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size) {
        return queue.queue(auth, search, state, null, null, null, false,
                PageRequest.of(Math.max(0, page), Math.max(1, Math.min(50, size)),
                Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.asc("id"))));
    }
    @GetMapping("/assignees")
    @PreAuthorize("hasAnyRole('ADMIN', 'SAFETY_MANAGER')")
    public List<Map<String,Object>> assignees() {
        return users.findAll().stream().filter(u -> u.isEnabled() && !u.isLocked())
            .filter(u -> Set.of("ROLE_ADMIN", "ROLE_CASE_PROCESSOR", "ROLE_USER", "ROLE_MEDICAL_REVIEWER",
                    "ROLE_QUALITY_REVIEWER", "ROLE_SAFETY_MANAGER").contains(u.getRole()))
            .map(u -> Map.<String,Object>of("id", u.getId(), "username", u.getUsername(), "role", u.getRole())).toList();
    }
    @GetMapping("/audit")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
    public List<AuditEventResponseDto> audit() {
        return audit.findTop50ByOrderByCreatedAtDesc().stream().map(AuditEventResponseDto::fromEntity).toList();
    }
}
