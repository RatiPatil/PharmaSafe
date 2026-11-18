package com.pharmasafe.processing;

import com.pharmasafe.dto.*;
import com.pharmasafe.entity.*;
import com.pharmasafe.repository.SafetyCaseRepository;
import com.pharmasafe.service.CaseService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class ProcessingCaseWorkbench {
  private final ProcessingAccess access;
  private final ProcessingAssignmentRepository assignments;
  private final ProcessingCaseLinkRepository links;
  private final ProcessingCaseEvidenceRepository evidence;
  private final ProcessingMemberRepository members;
  private final SafetyCaseRepository cases;
  private final CaseService caseService;

  public ProcessingCaseWorkbench(
      ProcessingAccess a,
      ProcessingAssignmentRepository w,
      ProcessingCaseLinkRepository l,
      ProcessingCaseEvidenceRepository e,
      ProcessingMemberRepository m,
      SafetyCaseRepository c,
      CaseService s) {
    access = a;
    assignments = w;
    links = l;
    evidence = e;
    members = m;
    cases = c;
    caseService = s;
  }

  public record HandoffRequest(
      @NotNull @PositiveOrZero Long expectedCaseVersion,
      @NotBlank @Size(max = 2000) String reason) {}

  public record LinkRequest(@NotBlank @Size(max = 64) String caseNumber) {}

  public record EvidenceRequest(
      @NotNull @PositiveOrZero Long expectedCaseVersion,
      @NotBlank @Pattern(regexp = "DUPLICATE_REVIEW|CODING") String kind,
      @NotBlank @Pattern(regexp = "NO_DUPLICATE|POTENTIAL_DUPLICATE|DISTINCT_CASE|VERIFIED")
          String outcome,
      @NotBlank @Size(max = 5000) String notes,
      @NotNull @Size(max = 500) String dictionaryReference) {}

  public SafetyCase linked(ProcessingAssignment a) {
    return a.workOrder.safetyCase != null
        ? a.workOrder.safetyCase
        : links.findById(a.id).map(l -> l.safetyCase).orElse(null);
  }

  private ProcessingAssignment owned(Long id, User actor) {
    ProcessingAssignment a =
        assignments
            .lockById(id)
            .orElseThrow(() -> new EntityNotFoundException("Assignment not found"));
    access.assignment(actor, a);
    if (!"EMPLOYEE".equals(access.level(actor))
        || !a.team.active
        || !a.employee.getId().equals(actor.getId())
        || members
            .findByTeamIdAndUserId(a.team.id, actor.getId())
            .filter(m -> "ACTIVE".equals(m.status))
            .isEmpty()) throw new AccessDeniedException("An active assigned employee is required");
    if ("COMPLETED".equals(a.status))
      throw new IllegalArgumentException("Ask your Team Lead to reopen the completed task first");
    return a;
  }

  private void bind(ProcessingAssignment a, SafetyCase c, User actor) {
    if (linked(a) != null)
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "This assignment already has a clinical case. Refresh the task.");
    if (c.isLocked() || !Set.of("Draft", "Data Entry").contains(c.getWorkflowState()))
      throw new IllegalArgumentException("Only unlocked intake/data-entry cases can be linked");
    if (c.getAssignedToUser() == null || !actor.getId().equals(c.getAssignedToUser().getId()))
      throw new AccessDeniedException("Link a case currently assigned to your employee account");
    ProcessingCaseLink l = new ProcessingCaseLink();
    l.assignmentId = a.id;
    l.safetyCase = c;
    l.employee = actor;
    l.linkedAt = Instant.now();
    links.saveAndFlush(l);
  }

  @Transactional
  public CaseResponseDto create(
      Long id, CaseRequestDto d, String username, String ip, String agent) {
    User actor = access.actor(username);
    ProcessingAssignment a = owned(id, actor);
    if (linked(a) != null)
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "This assignment already has a clinical case. Refresh the task.");
    d.setAssignedToUsername(username);
    var created = caseService.createCase(d, username, ip, agent);
    bind(a, cases.findById(created.getId()).orElseThrow(), actor);
    return created;
  }

  @Transactional
  public CaseResponseDto link(Long id, LinkRequest d, String username) {
    User actor = access.actor(username);
    ProcessingAssignment a = owned(id, actor);
    var c =
        cases
            .findByCaseNumberIgnoreCase(d.caseNumber().trim())
            .orElseThrow(() -> new EntityNotFoundException("Case not found"));
    c = cases.findForUpdate(c.getId()).orElseThrow();
    bind(a, c, actor);
    return CaseResponseDto.fromEntity(c);
  }

  @Transactional
  public CaseResponseDto handoff(Long id, HandoffRequest d, String username) {
    User actor = access.actor(username);
    var a =
        assignments
            .lockById(id)
            .orElseThrow(() -> new EntityNotFoundException("Assignment not found"));
    access.assignment(actor, a);
    if (!"EMPLOYEE".equals(access.level(actor))
        || !a.team.active
        || members
            .findByTeamIdAndUserId(a.team.id, actor.getId())
            .filter(m -> "ACTIVE".equals(m.status))
            .isEmpty()) throw new AccessDeniedException("An active assigned employee is required");
    if (!"MEDICAL_HANDOFF".equals(a.workOrder.stage) || !"COMPLETED".equals(a.status))
      throw new IllegalArgumentException(
          "Complete the Medical review handoff task and its response PDF first");
    var linked = linked(a);
    if (linked == null) throw new IllegalArgumentException("Link the clinical case first");
    var c = cases.findForUpdate(linked.getId()).orElseThrow();
    if (c.getAssignedToUser() == null || !actor.getId().equals(c.getAssignedToUser().getId()))
      throw new AccessDeniedException("The case is no longer assigned to you");
    if (c.getRowVersion() != d.expectedCaseVersion())
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Case changed. Refresh before sending to Medical Review.");
    if (!"Data Entry".equals(c.getWorkflowState()))
      throw new IllegalArgumentException("Only Data Entry cases can be sent to Medical Review");
    var missing = missing(a, c);
    if (!missing.isEmpty()) throw new IllegalArgumentException(String.join(" ", missing));
    return caseService.transitionWorkflow(
        c.getId(), "Medical Review", d.reason(), username, actor.getRole());
  }

  private static boolean text(String s) {
    return s != null && !s.isBlank();
  }

  public Map<String, Boolean> facts(SafetyCase c) {
    Map<String, Boolean> f = new LinkedHashMap<>();
    f.put(
        "patient",
        c.getPatient() != null
            && (text(c.getPatient().getPatientIdentifier())
                || c.getPatient().getAgeValue() != null
                || c.getPatient().getDateOfBirth() != null));
    f.put(
        "reporter",
        c.getReporters().stream()
            .anyMatch(
                r -> text(r.getFirstName()) || text(r.getLastName()) || text(r.getInstitution())));
    f.put(
        "suspectProduct",
        c.getProducts().stream()
            .anyMatch(
                p -> "Suspect".equalsIgnoreCase(p.getProductType()) && text(p.getProductName())));
    f.put(
        "adverseEvent",
        !c.getEvents().isEmpty()
            && c.getEvents().stream()
                .allMatch(e -> text(e.getEventVerbatim()) && e.getOnsetDate() != null));
    f.put(
        "eventCoding",
        !c.getEvents().isEmpty()
            && c.getEvents().stream()
                .allMatch(
                    e ->
                        text(e.getMeddraPt()) && text(e.getMeddraLlt()) && text(e.getMeddraSoc())));
    f.put("narrative", text(c.getNarrative()));
    return f;
  }

  private boolean verified(
      ProcessingAssignment a, SafetyCase c, String kind, List<String> outcomes) {
    return evidence
        .findFirstByAssignmentIdAndSafetyCaseIdAndCaseRowVersionAndKindOrderByRecordedAtDescIdDesc(
            a.id, c.getId(), c.getRowVersion(), kind)
        .filter(e -> outcomes.contains(e.outcome))
        .isPresent();
  }

  public List<String> missing(ProcessingAssignment a, SafetyCase c) {
    if (c == null)
      return List.of(
          "Create or link the clinical case before completing clinical processing work.");
    List<String> m = new ArrayList<>();
    var f = facts(c);
    String stage = a.workOrder.stage;
    if (Set.of("INTAKE", "VALIDATION", "MEDICAL_HANDOFF", "QC_CORRECTION", "REPORTING_HANDOFF")
        .contains(stage)) {
      if (!f.get("patient"))
        m.add("Enter an identifiable patient (source identifier, age or date of birth).");
      if (!f.get("reporter"))
        m.add("Enter a reporter name or institution; qualification alone is insufficient.");
      if (!f.get("suspectProduct")) m.add("Enter a named suspect product.");
      if (!f.get("adverseEvent")) m.add("Enter every adverse event verbatim and onset date.");
    }
    if (Set.of("DUPLICATE_REVIEW", "MEDICAL_HANDOFF").contains(stage)
        && !verified(a, c, "DUPLICATE_REVIEW", List.of("NO_DUPLICATE", "DISTINCT_CASE")))
      m.add("Record a resolved duplicate review for the current saved case revision.");
    if (Set.of("CODING", "MEDICAL_HANDOFF").contains(stage)) {
      if (!f.get("eventCoding")) m.add("Record LLT, preferred term and SOC for every event.");
      if (!verified(a, c, "CODING", List.of("VERIFIED")))
        m.add(
            "Verify drug/event coding and document dictionary releases for the current saved"
                + " revision.");
    }
    if (Set.of("NARRATIVE", "MEDICAL_HANDOFF", "QC_CORRECTION", "REPORTING_HANDOFF").contains(stage)
        && !f.get("narrative")) m.add("Save the case narrative.");
    if ("MEDICAL_HANDOFF".equals(stage) && "Draft".equals(c.getWorkflowState()))
      m.add("Save a valid case in Data Entry before handoff.");
    return m;
  }

  public Map<String, Object> view(ProcessingAssignment a) {
    var c = linked(a);
    Map<String, Object> v = new LinkedHashMap<>();
    v.put("linked", c != null);
    v.put("facts", c == null ? Map.of() : facts(c));
    v.put("missing", missing(a, c));
    v.put("caseRowVersion", c == null ? null : c.getRowVersion());
    v.put(
        "evidence",
        evidence.findTop50ByAssignmentIdOrderByRecordedAtDescIdDesc(a.id).stream()
            .map(
                e -> {
                  Map<String, Object> x = new LinkedHashMap<>();
                  x.put("id", e.id);
                  x.put("kind", e.kind);
                  x.put("outcome", e.outcome);
                  x.put("notes", e.notes);
                  x.put("dictionaryReference", e.dictionaryReference);
                  x.put("recordedBy", e.recordedBy.getUsername());
                  x.put("recordedAt", e.recordedAt);
                  x.put("caseRowVersion", e.caseRowVersion);
                  x.put(
                      "current",
                      c != null
                          && e.safetyCase.getId().equals(c.getId())
                          && e.caseRowVersion == c.getRowVersion());
                  return x;
                })
            .toList());
    return v;
  }

  @Transactional
  public Map<String, Object> record(Long id, EvidenceRequest d, String username) {
    User actor = access.actor(username);
    var a = owned(id, actor);
    var linked = linked(a);
    if (linked == null)
      throw new IllegalArgumentException("Create or link the clinical case first");
    var c = cases.findForUpdate(linked.getId()).orElseThrow();
    if (c.getAssignedToUser() == null || !actor.getId().equals(c.getAssignedToUser().getId()))
      throw new AccessDeniedException("The case is no longer assigned to you");
    if (c.isLocked() || !Set.of("Draft", "Data Entry").contains(c.getWorkflowState()))
      throw new IllegalArgumentException(
          "Processing evidence is recorded during intake/data entry only");
    if (c.getRowVersion() != d.expectedCaseVersion())
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Case changed. Reload before recording processing evidence.");
    if ("CODING".equals(d.kind())) {
      if (!"VERIFIED".equals(d.outcome())
          || d.dictionaryReference().isBlank()
          || !facts(c).get("eventCoding"))
        throw new IllegalArgumentException(
            "Save every event's LLT, PT and SOC, then document the drug/event dictionary releases"
                + " used");
    } else if (!Set.of("NO_DUPLICATE", "POTENTIAL_DUPLICATE", "DISTINCT_CASE")
        .contains(d.outcome()))
      throw new IllegalArgumentException("Select a duplicate review outcome");
    ProcessingCaseEvidence e = new ProcessingCaseEvidence();
    e.assignment = a;
    e.safetyCase = c;
    e.caseRowVersion = c.getRowVersion();
    e.kind = d.kind();
    e.outcome = d.outcome();
    e.notes = d.notes().trim();
    e.dictionaryReference = d.dictionaryReference().trim();
    e.recordedBy = actor;
    e.recordedAt = Instant.now();
    evidence.saveAndFlush(e);
    return view(a);
  }
}
