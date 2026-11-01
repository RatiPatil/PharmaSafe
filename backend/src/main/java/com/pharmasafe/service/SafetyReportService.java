package com.pharmasafe.service;

import com.pharmasafe.dto.SafetyReportDto;
import com.pharmasafe.entity.SafetyCase;
import com.pharmasafe.entity.SafetyReport;
import com.pharmasafe.entity.User;
import com.pharmasafe.repository.SafetyCaseRepository;
import com.pharmasafe.repository.SafetyReportRepository;
import com.pharmasafe.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class SafetyReportService {

    private final SafetyReportRepository safetyReportRepository;
    private final SafetyCaseRepository safetyCaseRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public SafetyReportService(SafetyReportRepository safetyReportRepository,
                               SafetyCaseRepository safetyCaseRepository,
                               UserRepository userRepository,
                               AuditService auditService) {
        this.safetyReportRepository = safetyReportRepository;
        this.safetyCaseRepository = safetyCaseRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Page<SafetyReportDto> searchReports(String status, String reportType, String product, int page, int size) {
        int boundedSize = Math.max(1, Math.min(size, 50));
        Pageable pageable = PageRequest.of(Math.max(0, page), boundedSize, Sort.by("dueDate").ascending());
        Page<SafetyReport> reports = safetyReportRepository.searchReports(status, reportType, product, pageable);
        return reports.map(SafetyReportDto::fromEntity);
    }

    @Transactional(readOnly = true)
    public SafetyReportDto getReportById(Long id) {
        SafetyReport report = safetyReportRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Safety report not found with ID: " + id));
        return SafetyReportDto.fromEntity(report);
    }

    @Transactional
    public SafetyReportDto createReport(SafetyReportDto request, String username) {
        User user = null;
        if (username != null) {
            user = userRepository.findByUsername(username.trim().toLowerCase()).orElse(null);
        }

        SafetyReport report = new SafetyReport();
        String reportId = request.getReportId();
        if (reportId == null || reportId.trim().isEmpty()) {
            String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            reportId = "RPT-" + datePart + "-" + UUID.randomUUID();
        }
        report.setReportId(reportId);
        report.setReportType(request.getReportType() != null ? request.getReportType() : "15-day");
        report.setProduct(request.getProduct() != null ? request.getProduct() : "All Products");
        report.setAuthority(request.getAuthority() != null ? request.getAuthority() : "FDA");
        report.setDataLockPoint(request.getDataLockPoint());
        report.setPeriodStart(request.getPeriodStart());
        report.setPeriodEnd(request.getPeriodEnd());

        LocalDate dueDate = request.getDueDate();
        if (dueDate == null) {
            // Default 15 days or 7 days from now
            int days = "7-day".equalsIgnoreCase(request.getReportType()) ? 7 : 15;
            dueDate = LocalDate.now().plusDays(days);
        }
        report.setDueDate(dueDate);
        report.setStatus("Pending");
        report.setSummary(request.getSummary());
        report.setConclusions(request.getConclusions());
        report.setRecommendations(request.getRecommendations());
        report.setCreatedByUser(user);

        // Link cases if provided
        if (request.getRelatedCaseNumbers() != null && !request.getRelatedCaseNumbers().isEmpty()) {
            List<SafetyCase> linkedCases = new ArrayList<>();
            int total = 0;
            int serious = 0;
            int fatal = 0;
            Set<Long> linkedIds = new HashSet<>();
            for (String caseNum : request.getRelatedCaseNumbers().stream().sorted(java.util.Comparator.nullsFirst(String::compareTo)).toList()) {
                if (caseNum == null || caseNum.isBlank()) throw new IllegalArgumentException("Case number is required");
                Optional<SafetyCase> cOpt = safetyCaseRepository.findByCaseNumberIgnoreCase(caseNum.trim());
                if (cOpt.isEmpty()) throw new IllegalArgumentException("Related case not found: " + caseNum);
                if (cOpt.isPresent()) {
                    SafetyCase c = safetyCaseRepository.findForUpdate(cOpt.get().getId()).orElseThrow();
                    if ("Closed".equals(c.getWorkflowState()) && Set.of("7-day","15-day").contains(report.getReportType())) throw new IllegalArgumentException("Reopen the case before scheduling a new expedited report");
                    if (!Set.of("Approved", "Locked", "Closed").contains(c.getWorkflowState())) {
                        throw new IllegalArgumentException("Related case must be approved before report preparation");
                    }
                    if (!linkedIds.add(c.getId())) continue;
                    linkedCases.add(c);
                    total++;
                    if (c.isSerious()) serious++;
                    if (c.isFatal()) fatal++;
                }
            }
            report.setRelatedCases(linkedCases);
            report.setTotalCases(total);
            report.setSeriousCases(serious);
            report.setFatalCases(fatal);
        } else {
            report.setTotalCases(request.getTotalCases());
            report.setSeriousCases(request.getSeriousCases());
            report.setFatalCases(request.getFatalCases());
        }

        SafetyReport saved = safetyReportRepository.save(report);

        auditService.logEvent("REPORT_CREATED", username, null, null, "SUCCESS",
                "Safety report " + saved.getReportId() + " (" + saved.getReportType() + ") created for " + saved.getProduct());

        return SafetyReportDto.fromEntity(saved);
    }

    @Transactional
    public SafetyReportDto updateReport(Long id, SafetyReportDto request, String username) {
        SafetyReport report = safetyReportRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Safety report not found with ID: " + id));

        if ("Submitted".equals(report.getStatus())) throw new IllegalStateException("Submitted report records cannot be edited");
        if (request.getStatus()!=null && !request.getStatus().equals(report.getStatus())) throw new IllegalArgumentException("Use the submission action to record submission");
        if (request.getProduct() != null) report.setProduct(request.getProduct());
        if (request.getAuthority() != null) report.setAuthority(request.getAuthority());
        if (request.getDueDate() != null) report.setDueDate(request.getDueDate());
        if (request.getSummary() != null) report.setSummary(request.getSummary());
        if (request.getConclusions() != null) report.setConclusions(request.getConclusions());
        if (request.getRecommendations() != null) report.setRecommendations(request.getRecommendations());
        if (request.getStatus() != null) report.setStatus(request.getStatus());

        SafetyReport saved = safetyReportRepository.save(report);

        auditService.logEvent("REPORT_UPDATED", username, null, null, "SUCCESS",
                "Safety report " + saved.getReportId() + " updated by " + username);

        return SafetyReportDto.fromEntity(saved);
    }

    @Transactional
    public SafetyReportDto submitReport(Long id, String username) {
        SafetyReport report = safetyReportRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Safety report not found with ID: " + id));

        report.setStatus("Submitted");
        report.setSubmittedDate(Instant.now());
        SafetyReport saved = safetyReportRepository.save(report);

        auditService.logEvent("REPORT_SUBMITTED", username, null, null, "SUCCESS",
                "Safety report " + saved.getReportId() + " successfully submitted to " + saved.getAuthority() + " by " + username);

        return SafetyReportDto.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getExpeditedSummary() {
        LocalDate today = LocalDate.now();
        LocalDate threeDaysFromNow = today.plusDays(3);

        List<SafetyReport> allPending = safetyReportRepository.findAll().stream()
                .filter(r -> "Pending".equalsIgnoreCase(r.getStatus()))
                .toList();

        long overdue = allPending.stream().filter(r -> r.getDueDate().isBefore(today)).count();
        long dueSoon = allPending.stream().filter(r -> !r.getDueDate().isBefore(today) && !r.getDueDate().isAfter(threeDaysFromNow)).count();
        long totalPending = allPending.size();

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalPendingReports", totalPending);
        summary.put("overdueReports", overdue);
        summary.put("dueSoonReports", dueSoon);
        summary.put("expeditedComplianceRate", totalPending > 0 ? Math.max(0, 100 - (overdue * 100 / totalPending)) : 100);

        return summary;
    }
}
