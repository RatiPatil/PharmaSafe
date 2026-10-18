package com.pharmasafe.service;

import com.pharmasafe.dto.DashboardSummaryDto;
import com.pharmasafe.dto.RecentActivityDto;
import com.pharmasafe.entity.AuditEvent;
import com.pharmasafe.entity.User;
import com.pharmasafe.repository.AuditEventRepository;
import com.pharmasafe.repository.SafetyCaseRepository;
import com.pharmasafe.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private final SafetyCaseRepository safetyCaseRepository;
    private final UserRepository userRepository;
    private final AuditEventRepository auditEventRepository;

    private static final List<String> REVIEW_STATES = Arrays.asList("Quality Review", "Medical Review");
    private static final List<String> CASE_EVENTS = Arrays.asList("CASE_CREATED", "CASE_UPDATED");

    public DashboardService(SafetyCaseRepository safetyCaseRepository,
                            UserRepository userRepository,
                            AuditEventRepository auditEventRepository) {
        this.safetyCaseRepository = safetyCaseRepository;
        this.userRepository = userRepository;
        this.auditEventRepository = auditEventRepository;
    }

    @Transactional(readOnly = true)
    public DashboardSummaryDto getSummary(String username) {
        long totalCases = safetyCaseRepository.count();
        long awaitingReviewCases = safetyCaseRepository.countByWorkflowStateIn(REVIEW_STATES);
        long seriousCases = safetyCaseRepository.countSeriousCases();
        long expeditedCases = safetyCaseRepository.countByPriority("Expedited");

        long assignedCases = 0;
        long userAwaitingReviewCases = 0;

        if (username != null && !username.isBlank()) {
            User user = userRepository.findByUsername(username.trim().toLowerCase()).orElse(null);
            if (user != null) {
                assignedCases = safetyCaseRepository.countByAssignedToUser(user);
                userAwaitingReviewCases = safetyCaseRepository.countByAssignedToUserAndWorkflowStateIn(user, REVIEW_STATES);
            }
        }

        return new DashboardSummaryDto(
                totalCases,
                assignedCases,
                awaitingReviewCases,
                userAwaitingReviewCases,
                seriousCases,
                expeditedCases
        );
    }

    @Transactional(readOnly = true)
    public List<RecentActivityDto> getRecentActivity(String username, boolean isAdmin) {
        List<AuditEvent> events;
        if (isAdmin) {
            events = auditEventRepository.findTop15ByEventTypeInOrderByCreatedAtDesc(CASE_EVENTS);
        } else if (username != null && !username.isBlank()) {
            events = auditEventRepository.findTop15ByUsernameAndEventTypeInOrderByCreatedAtDesc(
                    username.trim().toLowerCase(), CASE_EVENTS);
        } else {
            return Collections.emptyList();
        }

        return events.stream()
                .map(e -> new RecentActivityDto(
                        e.getId(),
                        e.getEventType(),
                        e.getUsername(),
                        sanitizeDetails(e.getDetails()),
                        e.getCreatedAt()
                ))
                .collect(Collectors.toList());
    }

    private String sanitizeDetails(String details) {
        if (details == null) {
            return "-";
        }
        return details.replaceAll("(?i)(password|token|secret)=[^, ]+", "$1=***");
    }
}
