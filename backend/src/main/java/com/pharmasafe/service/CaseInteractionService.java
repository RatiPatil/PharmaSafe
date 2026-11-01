package com.pharmasafe.service;

import com.pharmasafe.dto.*;
import com.pharmasafe.entity.*;
import com.pharmasafe.repository.*;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class CaseInteractionService {
    private final SafetyCaseRepository caseRepository;
    private final UserRepository userRepository;
    private final CaseContactLogRepository contactRepository;
    private final CaseActionItemRepository actionRepository;
    private final AuditService auditService;

    public CaseInteractionService(SafetyCaseRepository caseRepository, UserRepository userRepository,
                                  CaseContactLogRepository contactRepository, CaseActionItemRepository actionRepository,
                                  AuditService auditService) {
        this.caseRepository = caseRepository;
        this.userRepository = userRepository;
        this.contactRepository = contactRepository;
        this.actionRepository = actionRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Page<ContactLogResponse> listContacts(String username, String role, Long caseId, Pageable pageable) {
        User actor = actor(username);
        if (caseId != null) {
            SafetyCase safetyCase = caseById(caseId);
            requireCaseAccess(safetyCase, actor, role);
            return contactRepository.findBySafetyCaseIdOrderByContactAtDesc(caseId, pageable).map(ContactLogResponse::fromEntity);
        }
        if ("ROLE_ADMIN".equals(role) || "ROLE_SAFETY_MANAGER".equals(role)) {
            return contactRepository.findAll(PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                    Sort.by(Sort.Order.desc("contactAt"), Sort.Order.asc("id"))))
                    .map(ContactLogResponse::fromEntity);
        }
        return contactRepository.findPersonalLogs(actor, role, pageable)
                .map(ContactLogResponse::fromEntity);
    }

    @Transactional
    public ContactLogResponse createContact(Long caseId, ContactLogRequest request, String username, String role) {
        User actor = actor(username);
        SafetyCase safetyCase = caseById(caseId);
        requireCaseAccess(safetyCase, actor, role);
        validateContactType(request.contactType());
        CaseContactLog log = new CaseContactLog();
        log.setSafetyCase(safetyCase);
        log.setContactAt(request.contactAt());
        log.setContactType(request.contactType().trim());
        log.setDescription(request.description().trim());
        log.setResponsibleUser(request.responsibleUserId() == null ? actor : user(request.responsibleUserId()));
        log.setCreatedByUser(actor);
        CaseContactLog saved = contactRepository.save(log);
        auditService.logCaseEvent(caseId, "CONTACT_LOG_CREATED", actor.getUsername(), "SUCCESS",
                "Contact log created for " + safetyCase.getCaseNumber());
        return ContactLogResponse.fromEntity(saved);
    }

    @Transactional
    public ContactLogResponse updateContact(Long id, ContactLogRequest request, String username, String role) {
        User actor = actor(username);
        CaseContactLog log = contactRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Contact log not found"));
        requireCaseAccess(log.getSafetyCase(), actor, role);
        validateContactType(request.contactType());
        log.setContactAt(request.contactAt());
        log.setContactType(request.contactType().trim());
        log.setDescription(request.description().trim());
        log.setResponsibleUser(request.responsibleUserId() == null ? actor : user(request.responsibleUserId()));
        log.setUpdatedByUser(actor);
        CaseContactLog saved = contactRepository.save(log);
        auditService.logCaseEvent(log.getSafetyCase().getId(), "CONTACT_LOG_UPDATED", actor.getUsername(), "SUCCESS",
                "Contact log " + id + " updated");
        return ContactLogResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public Page<ActionItemResponse> listActions(String username, String role, Long caseId, boolean overdue, Pageable pageable) {
        User actor = actor(username);
        if (caseId != null) {
            SafetyCase safetyCase = caseById(caseId);
            requireCaseAccess(safetyCase, actor, role);
            return actionRepository.findBySafetyCaseIdOrderByDueAtAscIdAsc(caseId, pageable)
                    .map(item -> ActionItemResponse.fromEntity(item, Instant.now()));
        }
        if ("ROLE_ADMIN".equals(role) || "ROLE_SAFETY_MANAGER".equals(role)) {
            return actionRepository.findTeamItems(overdue, Instant.now(), pageable)
                    .map(item -> ActionItemResponse.fromEntity(item, Instant.now()));
        }
        return actionRepository.findPersonalItems(actor, role, overdue, pageable)
                .map(item -> ActionItemResponse.fromEntity(item, Instant.now()));
    }

    @Transactional
    public ActionItemResponse createAction(Long caseId, ActionItemRequest request, String username, String role) {
        User actor = actor(username);
        SafetyCase safetyCase = caseRepository.findForUpdate(caseId).orElseThrow(() -> new EntityNotFoundException("Case not found"));
        requireCaseAccess(safetyCase, actor, role);
        if ("Closed".equals(safetyCase.getWorkflowState())) throw new IllegalStateException("Reopen the case before creating new actions");
        CaseActionItem item = new CaseActionItem();
        item.setSafetyCase(safetyCase);
        item.setDescription(request.description().trim());
        if (!java.util.Set.of("ROLE_ADMIN", "ROLE_SAFETY_MANAGER").contains(role)
                && !actor.getId().equals(request.assignedUserId())) {
            throw new org.springframework.security.access.AccessDeniedException("Only managers can assign tasks to another user");
        }
        User target = user(request.assignedUserId());
        requireCaseAccess(safetyCase, target, target.getRole());
        item.setAssignedUser(target);
        item.setDueAt(request.dueAt());
        item.setCreatedByUser(actor);
        CaseActionItem saved = actionRepository.save(item);
        auditService.logCaseEvent(caseId, "ACTION_ITEM_CREATED", actor.getUsername(), "SUCCESS",
                "Action item created for " + safetyCase.getCaseNumber());
        return ActionItemResponse.fromEntity(saved, Instant.now());
    }

    @Transactional
    public ActionItemResponse reassignAction(Long id, Long assignedUserId, String username, String role) {
        User actor = actor(username);
        CaseActionItem item = actionRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Action item not found"));
        requireCaseAccess(item.getSafetyCase(), actor, role);
        User target = user(assignedUserId);
        requireCaseAccess(item.getSafetyCase(), target, target.getRole());
        item.setAssignedUser(target);
        CaseActionItem saved = actionRepository.save(item);
        auditService.logCaseEvent(item.getSafetyCase().getId(), "ACTION_ITEM_REASSIGNED", actor.getUsername(), "SUCCESS",
                "Action item " + id + " reassigned to " + saved.getAssignedUser().getUsername());
        return ActionItemResponse.fromEntity(saved, Instant.now());
    }

    @Transactional
    public ActionItemResponse completeAction(Long id, String username, String role) {
        User actor = actor(username);
        CaseActionItem item = actionRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Action item not found"));
        if (!"ROLE_ADMIN".equals(role) && !item.getAssignedUser().getUsername().equalsIgnoreCase(actor.getUsername())) {
            throw new org.springframework.security.access.AccessDeniedException("Only the assignee or administrator may complete this action");
        }
        requireCaseAccess(item.getSafetyCase(), actor, role);
        if ("COMPLETED".equals(item.getStatus()) || "CANCELLED".equals(item.getStatus())) {
            throw new IllegalStateException("Action item is already closed");
        }
        if ("FOLLOW_UP".equals(item.getActionCode())) throw new IllegalStateException("Record the follow-up receipt to complete this request");
        item.setStatus("COMPLETED");
        item.setCompletedByUser(actor);
        item.setCompletedAt(Instant.now());
        CaseActionItem saved = actionRepository.save(item);
        auditService.logCaseEvent(item.getSafetyCase().getId(), "ACTION_ITEM_COMPLETED", actor.getUsername(), "SUCCESS",
                "Action item " + id + " completed");
        return ActionItemResponse.fromEntity(saved, Instant.now());
    }

    private User actor(String username) {
        if (username == null || username.isBlank()) throw new org.springframework.security.access.AccessDeniedException("Authenticated user required");
        return userRepository.findByUsername(username.trim().toLowerCase())
                .orElseThrow(() -> new EntityNotFoundException("Authenticated user not found"));
    }

    private User user(Long id) {
        User target = userRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("User not found with ID: " + id));
        if (!target.isEnabled() || target.isLocked() || !java.util.Set.of("ROLE_ADMIN", "ROLE_SAFETY_MANAGER",
                "ROLE_CASE_PROCESSOR", "ROLE_USER", "ROLE_MEDICAL_REVIEWER", "ROLE_QUALITY_REVIEWER").contains(target.getRole())) {
            throw new IllegalArgumentException("Select an active operational user");
        }
        return target;
    }

    private SafetyCase caseById(Long id) {
        return caseRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Safety case not found with ID: " + id));
    }

    private void requireCaseAccess(SafetyCase safetyCase, User actor, String role) {
        if (!new com.pharmasafe.security.WorkspaceAccess().canRead(safetyCase, actor.getUsername(), role)) {
            throw new org.springframework.security.access.AccessDeniedException("You are not authorized to access this case");
        }
    }

    private void validateContactType(String type) {
        if (type == null || !java.util.Set.of("Telephone", "Email", "Portal", "Letter", "Other").contains(type.trim())) {
            throw new IllegalArgumentException("Contact type must be Telephone, Email, Portal, Letter or Other");
        }
    }
}
