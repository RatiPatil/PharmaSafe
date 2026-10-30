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
            return contactRepository.findByResponsibleUserOrCreatedByUserOrderByContactAtDesc(actor, actor, pageable)
                    .map(ContactLogResponse::fromEntity);
        }
        return contactRepository.findPersonalLogs(actor, pageable)
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
        return actionRepository.findPersonalItems(actor, overdue, pageable)
                .map(item -> ActionItemResponse.fromEntity(item, Instant.now()));
    }

    @Transactional
    public ActionItemResponse createAction(Long caseId, ActionItemRequest request, String username, String role) {
        User actor = actor(username);
        SafetyCase safetyCase = caseById(caseId);
        requireCaseAccess(safetyCase, actor, role);
        CaseActionItem item = new CaseActionItem();
        item.setSafetyCase(safetyCase);
        item.setDescription(request.description().trim());
        item.setAssignedUser(user(request.assignedUserId()));
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
        item.setAssignedUser(user(assignedUserId));
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
        return userRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("User not found with ID: " + id));
    }

    private SafetyCase caseById(Long id) {
        return caseRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Safety case not found with ID: " + id));
    }

    private void requireCaseAccess(SafetyCase safetyCase, User actor, String role) {
        boolean elevated = "ROLE_ADMIN".equals(role) || "ROLE_SAFETY_MANAGER".equals(role);
        boolean related = safetyCase.getAssignedToUser() == actor
                || safetyCase.getCreatedByUser() == actor
                || (safetyCase.getAssignedToUser() != null && safetyCase.getAssignedToUser().getId().equals(actor.getId()))
                || (safetyCase.getCreatedByUser() != null && safetyCase.getCreatedByUser().getId().equals(actor.getId()));
        if ("ROLE_MEDICAL_REVIEWER".equals(role)) {
            related = related || java.util.Set.of("Medical Review", "Quality Review").contains(safetyCase.getWorkflowState());
        }
        if ("ROLE_QUALITY_REVIEWER".equals(role)) {
            related = related || java.util.Set.of("Quality Review", "Approved", "Locked", "Closed").contains(safetyCase.getWorkflowState());
        }
        if (!elevated && !related) throw new org.springframework.security.access.AccessDeniedException("You are not authorized to access this case");
    }

    private void validateContactType(String type) {
        if (type == null || !java.util.Set.of("Telephone", "Email", "Portal", "Letter", "Other").contains(type.trim())) {
            throw new IllegalArgumentException("Contact type must be Telephone, Email, Portal, Letter or Other");
        }
    }
}
