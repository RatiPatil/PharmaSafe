package com.pharmasafe.service;
import com.pharmasafe.dto.CaseSummaryDto;
import com.pharmasafe.entity.SafetyCase;
import com.pharmasafe.repository.SafetyCaseRepository;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.criteria.*;
import java.util.*;

@Service
public class WorkspaceQueueService {
    private final SafetyCaseRepository cases;
    public WorkspaceQueueService(SafetyCaseRepository cases) { this.cases = cases; }
    @Transactional(readOnly = true)
    public Page<CaseSummaryDto> queue(Authentication auth, String search, String state, String priority,
            String country, String reportType, boolean assignedOnly, Pageable pageable) {
        Set<String> roles = new HashSet<>();
        auth.getAuthorities().forEach(a -> roles.add(a.getAuthority()));
        Specification<SafetyCase> spec = (root, query, cb) -> {
            List<Predicate> scope = new ArrayList<>();
            if (roles.stream().anyMatch(Set.of("ROLE_ADMIN", "ROLE_SAFETY_MANAGER", "ROLE_AUDITOR")::contains)) scope.add(cb.conjunction());
            if (roles.contains("ROLE_MEDICAL_REVIEWER")) scope.add(cb.equal(root.get("workflowState"), "Medical Review"));
            if (roles.contains("ROLE_QUALITY_REVIEWER")) scope.add(cb.equal(root.get("workflowState"), "Quality Review"));
            if (roles.contains("ROLE_REPORTING_SPECIALIST")) scope.add(root.get("workflowState").in("Approved", "Locked", "Closed"));
            if (roles.contains("ROLE_CASE_PROCESSOR") || roles.contains("ROLE_USER")) {
                scope.add(cb.or(cb.equal(root.join("assignedToUser", JoinType.LEFT).get("username"), auth.getName()),
                        cb.equal(root.join("createdByUser", JoinType.LEFT).get("username"), auth.getName())));
            }
            List<Predicate> filters = new ArrayList<>();
            filters.add(cb.or(scope.toArray(Predicate[]::new)));
            if (assignedOnly) filters.add(cb.equal(root.join("assignedToUser", JoinType.LEFT).get("username"), auth.getName()));
            if (search != null && !search.isBlank()) filters.add(cb.like(cb.lower(root.get("caseNumber")), "%" + search.trim().toLowerCase(Locale.ROOT) + "%"));
            if (state != null && !state.isBlank()) filters.add(cb.equal(root.get("workflowState"), state));
            if (priority != null && !priority.isBlank()) filters.add(cb.equal(root.get("priority"), priority));
            if (country != null && !country.isBlank()) filters.add(cb.equal(root.get("countryCode"), country));
            if (reportType != null && !reportType.isBlank()) filters.add(cb.equal(root.get("reportType"), reportType));
            return cb.and(filters.toArray(Predicate[]::new));
        };
        return cases.findAll(spec, pageable).map(CaseSummaryDto::fromEntity);
    }
}
