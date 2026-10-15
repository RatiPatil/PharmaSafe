package com.pharmasafe.controller;

import com.pharmasafe.dto.AuditEventResponseDto;
import com.pharmasafe.repository.AuditEventRepository;
import com.pharmasafe.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AuditEventRepository auditEventRepository;
    private final UserRepository userRepository;

    public AdminController(AuditEventRepository auditEventRepository, UserRepository userRepository) {
        this.auditEventRepository = auditEventRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<List<AuditEventResponseDto>> getAuditLogs() {
        List<AuditEventResponseDto> logs = auditEventRepository.findTop50ByOrderByCreatedAtDesc()
                .stream()
                .map(AuditEventResponseDto::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(logs);
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getAdminStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("totalUsers", userRepository.count());
        status.put("totalAuditEvents", auditEventRepository.count());
        status.put("systemStatus", "OPERATIONAL");
        return ResponseEntity.ok(status);
    }
}
