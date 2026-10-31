package com.pharmasafe.controller;

import com.pharmasafe.dto.AuditEventResponseDto;
import com.pharmasafe.dto.UserResponseDto;
import com.pharmasafe.entity.User;
import com.pharmasafe.repository.AuditEventRepository;
import com.pharmasafe.repository.UserRepository;
import com.pharmasafe.service.AuditService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AuditEventRepository auditEventRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public AdminController(AuditEventRepository auditEventRepository,
                           UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           AuditService auditService) {
        this.auditEventRepository = auditEventRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
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

    @GetMapping("/users")
    public ResponseEntity<List<UserResponseDto>> listUsers() {
        List<UserResponseDto> users = userRepository.findAll().stream()
                .map(UserResponseDto::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(users);
    }

    @GetMapping("/users/paged")
    public org.springframework.data.domain.Page<UserResponseDto> usersPage(
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size,
            @RequestParam(defaultValue="") String search) {
        return userRepository.findByUsernameContainingIgnoreCaseOrFullNameContainingIgnoreCase(search.trim(), search.trim(),
                org.springframework.data.domain.PageRequest.of(Math.max(0,page), Math.min(50,Math.max(1,size)),
                org.springframework.data.domain.Sort.by("username").and(org.springframework.data.domain.Sort.by("id"))))
                .map(UserResponseDto::fromEntity);
    }

    @PostMapping("/users")
    public ResponseEntity<UserResponseDto> createUser(
            @RequestBody Map<String, String> request,
            Authentication authentication
    ) {
        String adminUsername = authentication != null ? authentication.getName() : "admin";
        String username = request.get("username");
        String password = request.get("password");
        String role = request.get("role");
        String fullName = request.get("fullName");
        String department = request.get("department");

        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username is required");
        }
        if (password == null || password.trim().length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters");
        }
        if (userRepository.findByUsername(username.trim().toLowerCase()).isPresent()) {
            throw new IllegalArgumentException("Username already exists: " + username);
        }

        String assignedRole = com.pharmasafe.security.WorkspaceAccess.normalizeRole(
                role == null ? "ROLE_CASE_PROCESSOR" : role);

        User newUser = new User(username.trim().toLowerCase(), passwordEncoder.encode(password), assignedRole);
        newUser.setFullName(fullName != null ? fullName.trim() : username.trim());
        newUser.setDepartment(department != null ? department.trim() : "Safety");
        newUser.setEnabled(true);

        User saved = userRepository.save(newUser);

        auditService.logEvent("USER_CREATED", adminUsername, null, null, "SUCCESS",
                "New user account created: " + saved.getUsername() + " (" + saved.getRole() + ")");

        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponseDto.fromEntity(saved));
    }

    @PutMapping("/users/{id}")
    public ResponseEntity<UserResponseDto> updateUser(
            @PathVariable Long id,
            @RequestBody Map<String, String> request,
            Authentication authentication
    ) {
        String adminUsername = authentication != null ? authentication.getName() : "admin";
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found with ID: " + id));

        if (request.containsKey("fullName")) {
            user.setFullName(request.get("fullName"));
        }
        if (request.containsKey("department")) {
            user.setDepartment(request.get("department"));
        }
        if (request.containsKey("role")) {
            if (user.getUsername().equalsIgnoreCase(adminUsername)) {
                throw new IllegalArgumentException("Ask another administrator to change your role");
            }
            String newRole = com.pharmasafe.security.WorkspaceAccess.normalizeRole(request.get("role"));
            if (!newRole.equals(user.getRole())) user.revokeSessions();
            user.setRole(newRole);
        }

        User saved = userRepository.save(user);

        auditService.logEvent("USER_UPDATED", adminUsername, null, null, "SUCCESS",
                "User account updated: " + saved.getUsername() + " (Role: " + saved.getRole() + ")");

        return ResponseEntity.ok(UserResponseDto.fromEntity(saved));
    }

    @PatchMapping("/users/{id}/toggle-status")
    public ResponseEntity<UserResponseDto> toggleUserStatus(
            @PathVariable Long id,
            Authentication authentication
    ) {
        String adminUsername = authentication != null ? authentication.getName() : "admin";
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found with ID: " + id));

        if ("admin".equalsIgnoreCase(user.getUsername()) || user.getUsername().equalsIgnoreCase(adminUsername)) {
            throw new IllegalArgumentException("Cannot deactivate primary system administrator account");
        }

        user.setEnabled(!user.isEnabled());
        user.revokeSessions();
        User saved = userRepository.save(user);

        auditService.logEvent("USER_STATUS_TOGGLED", adminUsername, null, null, "SUCCESS",
                "User " + saved.getUsername() + " status changed to: " + (saved.isEnabled() ? "Active" : "Inactive"));

        return ResponseEntity.ok(UserResponseDto.fromEntity(saved));
    }

    @PatchMapping("/users/{id}/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            Authentication authentication
    ) {
        String adminUsername = authentication != null ? authentication.getName() : "admin";
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found with ID: " + id));

        String newPassword = body.get("newPassword");
        if (newPassword == null || newPassword.trim().length() < 6) {
            throw new IllegalArgumentException("New password must be at least 6 characters");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.revokeSessions();
        user.setLocked(false);
        user.setFailedLoginAttempts(0);
        userRepository.save(user);

        auditService.logEvent("USER_PASSWORD_RESET", adminUsername, null, null, "SUCCESS",
                "Password reset by admin for user: " + user.getUsername());

        Map<String, String> response = new HashMap<>();
        response.put("message", "Password reset successfully for user " + user.getUsername());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/roles")
    public ResponseEntity<List<Map<String, String>>> getAvailableRoles() {
        List<Map<String, String>> roles = List.of(
                Map.of("role", "ROLE_ADMIN", "name", "Administrator", "description", "Full system administration, user management, and security audit log access"),
                Map.of("role", "ROLE_CASE_PROCESSOR", "name", "Case Processor", "description", "Case intake, triage, patient/reporter/product/event data entry and submission"),
                Map.of("role", "ROLE_MEDICAL_REVIEWER", "name", "Medical Reviewer", "description", "Medical review, WHO-UMC causality assessment, listedness evaluation, and clinical approvals"),
                Map.of("role", "ROLE_QUALITY_REVIEWER", "name", "Quality Reviewer", "description", "Quality review, completeness verification, and approval or return for correction"),
                Map.of("role", "ROLE_SAFETY_MANAGER", "name", "Safety Manager", "description", "Workflow oversight, case locking/unlocking, expedited clock monitoring, and regulatory reporting"),
                Map.of("role", "ROLE_REPORTING_SPECIALIST", "name", "Reporting Specialist", "description", "Prepare and track safety reports"),
                Map.of("role", "ROLE_AUDITOR", "name", "Auditor / Viewer", "description", "Read-only case and audit access")
        );
        return ResponseEntity.ok(roles);
    }
}
