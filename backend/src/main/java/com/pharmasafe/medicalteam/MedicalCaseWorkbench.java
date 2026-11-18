package com.pharmasafe.medicalteam;

import com.pharmasafe.dto.*;
import com.pharmasafe.entity.*;
import com.pharmasafe.repository.*;
import com.pharmasafe.service.*;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class MedicalCaseWorkbench {
  @org.springframework.beans.factory.annotation.Autowired
  private com.fasterxml.jackson.databind.ObjectMapper mapper;

  @org.springframework.beans.factory.annotation.Autowired private CaseLabResultRepository labs;
  private final MedicalAccess access;
  private final MedicalTeamRepository teams;
  private final MedicalAssignmentRepository assignments;
  private final MedicalMemberRepository members;
  private final MedicalCaseAllocationRepository allocations;
  private final MedicalAllocationEventRepository events;
  private final MedicalConclusionRepository conclusions;
  private final SafetyCaseRepository cases;
  private final CaseService clinical;
  private final AuditService audit;

  public MedicalCaseWorkbench(
      MedicalAccess a,
      MedicalTeamRepository t,
      MedicalAssignmentRepository w,
      MedicalMemberRepository m,
      MedicalCaseAllocationRepository l,
      MedicalAllocationEventRepository e,
      MedicalConclusionRepository n,
      SafetyCaseRepository c,
      CaseService cs,
      AuditService au) {
    access = a;
    teams = t;
    assignments = w;
    members = m;
    allocations = l;
    events = e;
    conclusions = n;
    cases = c;
    clinical = cs;
    audit = au;
  }

  public record Allocate(
      @NotBlank @Size(max = 64) String caseNumber,
      @NotNull @PositiveOrZero Long expectedCaseVersion,
      @NotBlank @Size(max = 2000) String reason) {}

  public record Assess(
      @NotNull @PositiveOrZero Long expectedCaseVersion,
      @NotBlank @Size(max = 128) String productName,
      @NotBlank @Size(max = 1000) String eventVerbatim,
      @NotBlank @Pattern(regexp = "Possible|Probable|Certain|Unlikely|Unassessable")
          String causalityCompany,
      @NotBlank @Pattern(regexp = "Expected|Unexpected|Unknown") String expectedness,
      @NotBlank @Size(max = 3000) String assessmentComment) {}

  public record Conclude(
      @NotNull @PositiveOrZero Long expectedCaseVersion,
      @NotBlank @Size(max = 5000) String medicalNarrative,
      @NotBlank @Size(max = 3000) String reviewSummary,
      @NotBlank @Pattern(regexp = "SERIOUS|NON_SERIOUS|UNDETERMINED") String assessedSeriousness,
      @NotBlank @Size(max = 1000) String severitySummary,
      boolean sourceReviewed,
      boolean timelineReviewed) {}

  public record Decide(
      @NotNull @PositiveOrZero Long expectedCaseVersion,
      @NotBlank @Pattern(regexp = "Quality Review|Data Entry") String targetState,
      @NotBlank @Size(max = 2000) String reason) {}

  public SafetyCase linked(MedicalAssignment a) {
    return a.workOrder.safetyCase;
  }

  public void requireAllocation(SafetyCase c, Long teamId) {
    var allocation =
        allocations
            .findById(c.getId())
            .orElseThrow(
                () ->
                    new AccessDeniedException(
                        "Ask the Medical Role Admin to allocate this case to your team first"));
    if (!allocation.team.id.equals(teamId))
      throw new AccessDeniedException("This case belongs to another medical team");
  }

  public List<com.pharmasafe.dto.LabResultDto> laboratorySnapshot(SafetyCase c) {
    return labs.findBySafetyCaseIdOrderByTestDateDescIdDesc(c.getId()).stream()
        .map(com.pharmasafe.dto.LabResultDto::from)
        .toList();
  }

  public List<com.pharmasafe.dto.LabResultDto> laboratory(String username, Long id) {
    var actor = access.actor(username);
    var a =
        assignments
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Assignment not found"));
    access.assignment(actor, a);
    var c = linked(a);
    if (c == null) throw new EntityNotFoundException("No linked medical case");
    canInspect(actor, a, c);
    return laboratorySnapshot(c);
  }

  public boolean canWork(User u, MedicalAssignment a) {
    var c = linked(a);
    return c == null
        || ("Medical Review".equals(c.getWorkflowState())
            && !c.isLocked()
            && c.getAssignedToUser() != null
            && c.getAssignedToUser().getId().equals(u.getId())
            && allocations
                .findById(c.getId())
                .filter(v -> v.team.id.equals(a.team.id) && v.team.active)
                .isPresent());
  }

  public void canInspect(User actor, MedicalAssignment a, SafetyCase c) {
    requireAllocation(c, a.team.id);
    if (!java.util.Set.of("Medical Review", "Quality Review", "Approved", "Locked", "Closed")
        .contains(c.getWorkflowState()))
      throw new AccessDeniedException(
          "The case is awaiting Case Processing corrections. Original task PDFs remain available.");
    if ("EMPLOYEE".equals(access.level(actor))
        && (c.getAssignedToUser() == null || !c.getAssignedToUser().getId().equals(actor.getId())))
      throw new AccessDeniedException("This case is no longer assigned to you");
  }

  private MedicalAssignment employee(Long id, User u, boolean allowCompleted) {
    var a =
        assignments
            .lockById(id)
            .orElseThrow(() -> new EntityNotFoundException("Medical assignment not found"));
    access.assignment(u, a);
    if (!"EMPLOYEE".equals(access.level(u))
        || !a.team.active
        || members
            .findByTeamIdAndUserId(a.team.id, u.getId())
            .filter(m -> "ACTIVE".equals(m.status))
            .isEmpty())
      throw new AccessDeniedException("Active assigned Medical Reviewer required");
    if (!allowCompleted && "COMPLETED".equals(a.status))
      throw new IllegalArgumentException("Ask your Medical Team Lead to reopen this task");
    if (linked(a) == null) throw new IllegalArgumentException("This task has no clinical case");
    return a;
  }

  private SafetyCase editable(MedicalAssignment a, User actor, long revision) {
    var c = cases.findForUpdate(linked(a).getId()).orElseThrow();
    requireAllocation(c, a.team.id);
    if (!"Medical Review".equals(c.getWorkflowState()) || c.isLocked())
      throw new IllegalArgumentException(
          "Medical editing is available only in unlocked Medical Review cases");
    if (c.getAssignedToUser() == null || !actor.getId().equals(c.getAssignedToUser().getId()))
      throw new AccessDeniedException("The case is no longer assigned to you");
    if (c.getRowVersion() != revision)
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Case changed. Reload before saving medical evidence.");
    return c;
  }

  public Page<Map<String, Object>> cases(String username, Long teamId, int page) {
    var u = access.actor(username);
    String level = access.level(u);
    if ("EMPLOYEE".equals(level))
      throw new AccessDeniedException("Medical intake is restricted to supervisors");
    if (teamId != null) access.team(u, teamId, false);
    Specification<SafetyCase> scope =
        (r, q, c) -> {
          var conditions = new ArrayList<jakarta.persistence.criteria.Predicate>();
          conditions.add(c.equal(r.get("workflowState"), "Medical Review"));
          conditions.add(c.isFalse(r.get("locked")));
          if (!"ADMIN".equals(level) || teamId != null) {
            var sub = q.subquery(Long.class);
            var a = sub.from(MedicalCaseAllocation.class);
            sub.select(a.get("caseId"))
                .where(
                    c.equal(a.get("caseId"), r.get("id")),
                    teamId == null
                        ? c.equal(a.get("team").get("lead").get("id"), u.getId())
                        : c.equal(a.get("team").get("id"), teamId));
            conditions.add(c.exists(sub));
          }
          return c.and(conditions.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    return cases
        .findAll(scope, PageRequest.of(Math.max(0, page), 20, Sort.by("id")))
        .map(
            c -> {
              var v =
                  new java.util.LinkedHashMap<String, Object>(
                      mapper.convertValue(
                          CaseSummaryDto.fromEntity(c),
                          new com.fasterxml.jackson.core.type.TypeReference<
                              Map<String, Object>>() {}));
              v.put("rowVersion", c.getRowVersion());
              var allocation = allocations.findById(c.getId()).orElse(null);
              v.put("allocatedTeamId", allocation == null ? null : allocation.team.id);
              v.put("allocatedTeamCode", allocation == null ? null : allocation.team.code());
              return v;
            });
  }

  public Map<String, Long> queueCounts(String username, Long teamId) {
    var u = access.actor(username);
    var level = access.level(u);
    if (teamId != null) access.team(u, teamId, false);
    var teamIds =
        "EMPLOYEE".equals(level)
            ? members.findByUserId(u.getId()).stream()
                .filter(m -> "ACTIVE".equals(m.status) && m.team.active)
                .map(m -> m.team.id)
                .toList()
            : List.<Long>of();
    Specification<SafetyCase> scope =
        (r, q, c) -> {
          var p = new ArrayList<jakarta.persistence.criteria.Predicate>();
          p.add(c.equal(r.get("workflowState"), "Medical Review"));
          p.add(c.isFalse(r.get("locked")));
          if (!"ADMIN".equals(level) || teamId != null) {
            var sub = q.subquery(Long.class);
            var a = sub.from(MedicalCaseAllocation.class);
            var f = new ArrayList<jakarta.persistence.criteria.Predicate>();
            f.add(c.equal(a.get("caseId"), r.get("id")));
            if (teamId != null) f.add(c.equal(a.get("team").get("id"), teamId));
            if ("TEAM_LEAD".equals(level))
              f.add(c.equal(a.get("team").get("lead").get("id"), u.getId()));
            if ("EMPLOYEE".equals(level)) {
              f.add(a.get("team").get("id").in(teamIds));
              p.add(c.equal(r.get("assignedToUser").get("id"), u.getId()));
            }
            sub.select(a.get("caseId"))
                .where(f.toArray(jakarta.persistence.criteria.Predicate[]::new));
            p.add(c.exists(sub));
          }
          return c.and(p.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    return Map.of(
        "total",
        cases.count(scope),
        "serious",
        cases.count(scope.and((r, q, c) -> c.isTrue(r.get("serious")))),
        "assessed",
        cases.count(scope.and((r, q, c) -> c.isNotEmpty(r.get("assessments")))));
  }

  @Transactional
  public Map<String, Object> allocate(String username, Long teamId, Allocate d) {
    var u = access.actor(username);
    access.admin(u);
    var team =
        teams
            .lockById(teamId)
            .orElseThrow(() -> new EntityNotFoundException("Medical team not found"));
    access.team(u, teamId, true);
    var candidate =
        cases
            .findByCaseNumberIgnoreCase(d.caseNumber().trim())
            .orElseThrow(() -> new EntityNotFoundException("Case not found"));
    var c = cases.findForUpdate(candidate.getId()).orElseThrow();
    if (!"Medical Review".equals(c.getWorkflowState()) || c.isLocked())
      throw new IllegalArgumentException("Only unlocked Medical Review cases can be allocated");
    if (c.getRowVersion() != d.expectedCaseVersion())
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Case changed. Refresh medical intake.");
    var a = allocations.findById(c.getId()).orElseGet(MedicalCaseAllocation::new);
    Long previous = a.team == null ? null : a.team.id;
    if (previous != null
        && assignments.count(
                (r, q, b) ->
                    b.and(
                        b.equal(r.get("workOrder").get("safetyCase").get("id"), c.getId()),
                        b.notEqual(r.get("status"), "COMPLETED")))
            > 0)
      throw new IllegalArgumentException(
          "Finish outstanding medical assignments before reallocating this case");
    if (a.returnProcessor == null) {
      var prior = c.getAssignedToUser();
      if (prior != null && Set.of("ROLE_CASE_PROCESSOR", "ROLE_USER").contains(prior.getRole()))
        a.returnProcessor = prior;
      else if (c.getCreatedByUser() != null
          && Set.of("ROLE_CASE_PROCESSOR", "ROLE_USER").contains(c.getCreatedByUser().getRole()))
        a.returnProcessor = c.getCreatedByUser();
    }
    a.caseId = c.getId();
    a.team = team;
    a.allocatedBy = u;
    a.allocatedAt = Instant.now();
    a.reason = d.reason().trim();
    allocations.saveAndFlush(a);
    var event = new MedicalAllocationEvent();
    event.safetyCase = c;
    event.previousTeamId = previous;
    event.team = team;
    event.actor = u;
    event.reason = a.reason;
    event.recordedAt = a.allocatedAt;
    event.sourceCaseVersion = c.getRowVersion();
    events.saveAndFlush(event);
    audit.logCaseEvent(
        c.getId(),
        "MR_CASE_ALLOCATED",
        username,
        "SUCCESS",
        "team=" + team.code() + "; reason=" + a.reason);
    return Map.of(
        "caseId",
        c.getId(),
        "caseNumber",
        c.getCaseNumber(),
        "teamId",
        team.id,
        "teamCode",
        team.code());
  }

  @Transactional
  public CaseResponseDto assess(String username, Long id, Assess d) {
    var u = access.actor(username);
    var a = employee(id, u, false);
    var c = editable(a, u, d.expectedCaseVersion());
    var assessment = new CaseRequestDto.AssessmentDto();
    assessment.setProductName(d.productName());
    assessment.setEventVerbatim(d.eventVerbatim());
    assessment.setCausalityCompany(d.causalityCompany());
    assessment.setExpectedness(d.expectedness());
    assessment.setAssessmentComment(d.assessmentComment());
    assessment.setCausalityReporter("Unassessable");
    return clinical.addAssessment(c.getId(), assessment, username);
  }

  private Map<String, Object> conclusionView(MedicalConclusion n) {
    var v = new LinkedHashMap<String, Object>();
    v.put("id", n.id);
    v.put("medicalNarrative", n.medicalNarrative);
    v.put("reviewSummary", n.reviewSummary);
    v.put("assessedSeriousness", n.assessedSeriousness);
    v.put("severitySummary", n.severitySummary);
    v.put("sourceReviewed", n.sourceReviewed);
    v.put("timelineReviewed", n.timelineReviewed);
    v.put("sourceCaseVersion", n.sourceCaseVersion);
    v.put("recordedAt", n.recordedAt);
    v.put("employee", n.employee.getUsername());
    return v;
  }

  @Transactional
  public Map<String, Object> conclude(String username, Long id, Conclude d) {
    var u = access.actor(username);
    var a = employee(id, u, false);
    var c = editable(a, u, d.expectedCaseVersion());
    if (!d.sourceReviewed() || !d.timelineReviewed())
      throw new IllegalArgumentException("Confirm source and clinical timeline review");
    var n = new MedicalConclusion();
    n.assignment = a;
    n.safetyCase = c;
    n.employee = u;
    n.sourceCaseVersion = c.getRowVersion();
    n.medicalNarrative = d.medicalNarrative().trim();
    n.reviewSummary = d.reviewSummary().trim();
    n.assessedSeriousness = d.assessedSeriousness();
    n.severitySummary = d.severitySummary().trim();
    n.sourceReviewed = d.sourceReviewed();
    n.timelineReviewed = d.timelineReviewed();
    n.recordedAt = Instant.now();
    conclusions.saveAndFlush(n);
    audit.logCaseEvent(
        c.getId(),
        "MR_CONCLUSION_RECORDED",
        username,
        "SUCCESS",
        "assignment=" + id + "; evidence=" + n.id);
    return conclusionView(n);
  }

  private boolean assessed(SafetyCase c) {
    var products =
        c.getProducts().stream().filter(p -> "Suspect".equals(p.getProductType())).toList();
    return !products.isEmpty()
        && !c.getEvents().isEmpty()
        && products.stream()
            .allMatch(
                p ->
                    c.getEvents().stream()
                        .allMatch(
                            e ->
                                c.getAssessments().stream()
                                    .anyMatch(
                                        n ->
                                            p.getProductName().equals(n.getProductName())
                                                && e.getEventVerbatim().equals(n.getEventVerbatim())
                                                && n.getCausalityCompany() != null
                                                && !n.getCausalityCompany().isBlank()
                                                && n.getExpectedness() != null
                                                && !n.getExpectedness().isBlank())));
  }

  public List<String> missing(MedicalAssignment a, SafetyCase c) {
    if (c == null) return List.of("Link an allocated medical case through the assignment form.");
    var result = new ArrayList<String>();
    if (!"Medical Review".equals(c.getWorkflowState()))
      result.add("Case must be in Medical Review.");
    var n =
        conclusions
            .findFirstByAssignmentIdAndSourceCaseVersionOrderByIdDesc(a.id, c.getRowVersion())
            .orElse(null);
    if (n == null)
      result.add(
          "Save your medical conclusion against the current case revision after all assessments.");
    if (Set.of("CLINICAL_ASSESSMENT", "CAUSALITY", "EXPECTEDNESS", "QC_RESPONSE", "QUALITY_HANDOFF")
            .contains(a.workOrder.stage)
        && !assessed(c))
      result.add("Record causality and expectedness for every suspect product / event pair.");
    if (n != null && Set.of("SERIOUSNESS_REVIEW", "QUALITY_HANDOFF").contains(a.workOrder.stage)) {
      if ("UNDETERMINED".equals(n.assessedSeriousness))
        result.add("Resolve assessed seriousness before completing this stage.");
      if ("QUALITY_HANDOFF".equals(a.workOrder.stage)
          && !n.assessedSeriousness.equals(c.isSerious() ? "SERIOUS" : "NON_SERIOUS"))
        result.add(
            "Assessed seriousness differs from case data. Request Case Processing correction before"
                + " QC handoff.");
    }
    return result;
  }

  public Map<String, Object> view(MedicalAssignment a) {
    var c = linked(a);
    var v = new LinkedHashMap<String, Object>();
    v.put("linked", c != null);
    v.put("caseRowVersion", c == null ? null : c.getRowVersion());
    v.put("workflowState", c == null ? null : c.getWorkflowState());
    v.put("assessmentCoverage", c != null && assessed(c));
    v.put("missing", missing(a, c));
    v.put(
        "conclusions",
        conclusions.findTop20ByAssignmentIdOrderByIdDesc(a.id).stream()
            .map(this::conclusionView)
            .toList());
    return v;
  }

  @Transactional
  public CaseResponseDto decide(String username, Long id, Decide d) {
    var u = access.actor(username);
    var a = employee(id, u, true);
    var c = editable(a, u, d.expectedCaseVersion());
    if ("Quality Review".equals(d.targetState())) {
      if (!"COMPLETED".equals(a.status) || !"QUALITY_HANDOFF".equals(a.workOrder.stage))
        throw new IllegalArgumentException(
            "Complete the Quality handoff task and response PDF first");
      var gaps = missing(a, c);
      if (!gaps.isEmpty()) throw new IllegalArgumentException(String.join(" ", gaps));
      return clinical.transitionWorkflow(
          c.getId(), "Quality Review", d.reason(), username, u.getRole());
    }
    if (!"BLOCKED".equals(a.status))
      throw new IllegalArgumentException(
          "Submit pending-information progress and its response PDF before returning for"
              + " correction");
    var allocation = allocations.findById(c.getId()).orElseThrow();
    var processor = allocation.returnProcessor;
    if (processor == null
        || !processor.isApproved()
        || !processor.isEnabled()
        || processor.isLocked()
        || !Set.of("ROLE_CASE_PROCESSOR", "ROLE_USER").contains(processor.getRole()))
      throw new IllegalArgumentException(
          "Ask the Safety Manager to designate an active Case Processor for corrections");
    clinical.transitionWorkflow(c.getId(), "Data Entry", d.reason(), username, u.getRole());
    c.setAssignedToUser(processor);
    c.setUpdatedByUser(u);
    cases.saveAndFlush(c);
    audit.logCaseEvent(
        c.getId(),
        "MR_PROCESSING_CORRECTION_REQUESTED",
        username,
        "SUCCESS",
        "processor=" + processor.getStaffCode() + "; reason=" + d.reason());
    return CaseResponseDto.fromEntity(c);
  }
}
