package com.pharmasafe.analytics;

import com.fasterxml.jackson.databind.*;
import com.pharmasafe.administration.AdminScope;
import com.pharmasafe.entity.*;
import com.pharmasafe.repository.*;
import com.pharmasafe.service.*;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(readOnly = true)
public class PvAnalysisService {
  public static final String ROLE = "ROLE_PV_ANALYST";
  private final AdminScope access;
  private final UserRepository users;
  private final SafetyCaseRepository cases;
  private final PvAnalysisRepository records;
  private final ObjectMapper json;
  private final CasePdfRenderer renderer;
  private final AuditService audit;
  @PersistenceContext private EntityManager em;

  public PvAnalysisService(
      AdminScope a,
      UserRepository u,
      SafetyCaseRepository c,
      PvAnalysisRepository r,
      ObjectMapper j,
      CasePdfRenderer p,
      AuditService au) {
    access = a;
    users = u;
    cases = c;
    records = r;
    json = j;
    renderer = p;
    audit = au;
  }

  public record Create(
      @NotBlank @Size(max = 160) String title,
      @NotBlank @Size(max = 3000) String objective,
      @NotBlank @Size(max = 5000) String findings,
      @NotBlank @Size(max = 3000) String recommendation,
      @Size(max = 64) String caseNumber) {}

  private User actor(String username) {
    User u = access.actor(username);
    if (!Set.of(ROLE, "ROLE_SAFETY_MANAGER", "ROLE_ADMIN", "ROLE_SCOPED_ADMIN")
        .contains(u.getRole())) throw new AccessDeniedException("PV analytics access is required");
    if (AdminScope.isRoleAdmin(u)) access.role(username, ROLE);
    return u;
  }

  private Specification<PvAnalysisRecord> scope(User actor, Long userId) {
    if (ROLE.equals(actor.getRole()) && userId != null && !userId.equals(actor.getId()))
      throw new AccessDeniedException("Only your own analysis records are available");
    if (userId != null) {
      var target =
          users
              .findById(userId)
              .orElseThrow(() -> new EntityNotFoundException("Analyst not found"));
      if (!ROLE.equals(target.getRole()))
        throw new AccessDeniedException("Select a PV Analyst employee");
    }
    return (r, q, c) ->
        ROLE.equals(actor.getRole())
            ? c.equal(r.get("analyst").get("id"), actor.getId())
            : userId == null ? c.conjunction() : c.equal(r.get("analyst").get("id"), userId);
  }

  private Map<String, Long> distribution(String association, String attribute) {
    var cb = em.getCriteriaBuilder();
    var query = cb.createTupleQuery();
    var root = query.from(SafetyCase.class);
    var node = root.join(association);
    query
        .multiselect(node.get(attribute), cb.countDistinct(root))
        .where(
            association.equals("products")
                ? cb.and(
                    root.get("workflowState").in("Approved", "Locked", "Closed"),
                    cb.equal(node.get("productType"), "Suspect"))
                : root.get("workflowState").in("Approved", "Locked", "Closed"))
        .groupBy(node.get(attribute))
        .orderBy(cb.desc(cb.countDistinct(root)), cb.asc(node.get(attribute)));
    Map<String, Long> result = new LinkedHashMap<>();
    for (var row : em.createQuery(query).setMaxResults(20).getResultList())
      result.put(
          row.get(0) == null ? "Not recorded" : row.get(0).toString(),
          ((Number) row.get(1)).longValue());
    return result;
  }

  public Map<String, Object> trends(String username) {
    actor(username);
    Map<String, Object> v = new LinkedHashMap<>();
    v.put("scope", "Reviewed cases: Approved, Locked and Closed");
    v.put("generatedAt", Instant.now());
    v.put(
        "reviewedCases",
        cases.count((r, q, c) -> r.get("workflowState").in("Approved", "Locked", "Closed")));
    v.put("products", distribution("products", "productName"));
    v.put("reactions", distribution("events", "eventVerbatim"));
    v.put("outcomes", distribution("events", "outcome"));
    v.put(
        "interpretation",
        "Distinct case counts per category; categories may overlap. Spontaneous report counts do"
            + " not measure incidence or establish causality. These are descriptive trends, not"
            + " validated signals.");
    return v;
  }

  private Map<String, Object> view(PvAnalysisRecord r) {
    var v = new LinkedHashMap<String, Object>();
    v.put("id", r.id);
    v.put("title", r.title);
    v.put("objective", r.objective);
    v.put("findings", r.findings);
    v.put("recommendation", r.recommendation);
    v.put("analyst", r.analyst.getFullName());
    v.put("username", r.analyst.getUsername());
    v.put("staffCode", r.analyst.getStaffCode());
    v.put("createdAt", r.createdAt);
    v.put("caseNumber", r.safetyCase == null ? null : r.safetyCase.getCaseNumber());
    v.put("sha256", r.pdfSha256);
    return v;
  }

  public Page<Map<String, Object>> list(String username, Long userId, int page) {
    var a = actor(username);
    return records
        .findAll(
            scope(a, userId),
            PageRequest.of(
                Math.max(0, page), 20, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"))))
        .map(this::view);
  }

  @Transactional(isolation = Isolation.REPEATABLE_READ)
  public Map<String, Object> create(String username, Create d) {
    var a = actor(username);
    if (!ROLE.equals(a.getRole()))
      throw new AccessDeniedException("Only a PV Analyst can author an analysis");
    var r = new PvAnalysisRecord();
    r.id = UUID.randomUUID().toString();
    r.analyst = a;
    r.title = d.title().trim();
    r.objective = d.objective().trim();
    r.findings = d.findings().trim();
    r.recommendation = d.recommendation().trim();
    r.createdAt = Instant.now();
    if (d.caseNumber() != null && !d.caseNumber().isBlank()) {
      r.safetyCase =
          cases
              .findByCaseNumberIgnoreCase(d.caseNumber().trim())
              .orElseThrow(() -> new EntityNotFoundException("Case not found"));
      if (!Set.of("Approved", "Locked", "Closed").contains(r.safetyCase.getWorkflowState()))
        throw new AccessDeniedException("Only reviewed cases may be linked");
    }
    var snapshot = json.createObjectNode();
    var control = snapshot.putObject("documentControl");
    control.put("documentKind", "PV_ANALYSIS");
    control.put("documentId", r.id);
    control.put("reportId", "PV-" + r.id);
    control.put("generatedBy", username);
    control.put("generatedAtUtc", r.createdAt.toString());
    control.put("finalDocument", false);
    snapshot.set("analysis", json.valueToTree(view(r)));
    snapshot.set("reviewedCaseTrends", json.valueToTree(trends(username)));
    snapshot.put(
        "reviewRoute",
        "Available to the Safety Manager and PV Analyst administration for evaluation; not a"
            + " clinical decision or regulatory submission.");
    try {
      r.snapshotJson = json.writeValueAsString(snapshot);
      byte[] source = r.snapshotJson.getBytes(StandardCharsets.UTF_8);
      r.pdfBytes = renderer.render(snapshot, source);
      r.pdfSha256 = hash(r.pdfBytes);
    } catch (java.io.IOException e) {
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
          "Analysis PDF generation failed; no analysis was saved",
          e);
    }
    records.saveAndFlush(r);
    audit.logAccountEvent(
        "PV_ANALYSIS_CREATED", username, "PV analysis " + r.id + "; staff=" + a.getStaffCode());
    return view(r);
  }

  private String hash(byte[] bytes) {
    try {
      return HexFormat.of()
          .formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @Transactional
  public CaseDocumentService.Download download(String username, String id) {
    var a = actor(username);
    var r =
        records.findById(id).orElseThrow(() -> new EntityNotFoundException("Analysis not found"));
    if (ROLE.equals(a.getRole()) && !r.analyst.getId().equals(a.getId()))
      throw new AccessDeniedException("Analysis is outside your scope");
    if (!hash(r.pdfBytes).equals(r.pdfSha256))
      throw new IllegalStateException("Analysis PDF integrity verification failed");
    audit.logAccountEvent("PV_ANALYSIS_DOWNLOADED", username, "PV analysis " + id);
    return new CaseDocumentService.Download("PV-analysis-" + id + ".pdf", r.pdfBytes, r.pdfSha256);
  }
}
