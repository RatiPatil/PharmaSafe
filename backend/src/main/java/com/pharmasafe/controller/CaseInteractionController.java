package com.pharmasafe.controller;

import com.pharmasafe.dto.*;
import com.pharmasafe.service.CaseInteractionService;
import jakarta.validation.Valid;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class CaseInteractionController {
    private final CaseInteractionService service;

    public CaseInteractionController(CaseInteractionService service) { this.service = service; }

    @GetMapping("/contact-logs")
    public Page<ContactLogResponse> listContacts(@RequestParam(required = false) Long caseId,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size,
                                                  Authentication auth) {
        return service.listContacts(auth.getName(), role(auth), caseId, pageable(page, size));
    }

    @PostMapping("/cases/{caseId}/contact-logs")
    public ResponseEntity<ContactLogResponse> createContact(@PathVariable Long caseId,
                                                             @Valid @RequestBody ContactLogRequest request,
                                                             Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createContact(caseId, request, auth.getName(), role(auth)));
    }

    @PutMapping("/contact-logs/{id}")
    public ContactLogResponse updateContact(@PathVariable Long id, @Valid @RequestBody ContactLogRequest request,
                                            Authentication auth) {
        return service.updateContact(id, request, auth.getName(), role(auth));
    }

    @GetMapping("/action-items")
    public Page<ActionItemResponse> listActions(@RequestParam(required = false) Long caseId,
                                                @RequestParam(defaultValue = "false") boolean overdue,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size,
                                                Authentication auth) {
        return service.listActions(auth.getName(), role(auth), caseId, overdue, pageable(page, size));
    }

    @PostMapping("/cases/{caseId}/action-items")
    public ResponseEntity<ActionItemResponse> createAction(@PathVariable Long caseId,
                                                            @Valid @RequestBody ActionItemRequest request,
                                                            Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createAction(caseId, request, auth.getName(), role(auth)));
    }

    @PatchMapping("/action-items/{id}/assignment")
    public ActionItemResponse reassignAction(@PathVariable Long id, @RequestParam Long assignedUserId, Authentication auth) {
        return service.reassignAction(id, assignedUserId, auth.getName(), role(auth));
    }

    @PostMapping("/action-items/{id}/complete")
    public ActionItemResponse completeAction(@PathVariable Long id, Authentication auth) {
        return service.completeAction(id, auth.getName(), role(auth));
    }

    private Pageable pageable(int page, int size) {
        return PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 50)));
    }

    private String role(Authentication auth) {
        return auth.getAuthorities().stream().findFirst().map(a -> a.getAuthority()).orElse("");
    }
}
