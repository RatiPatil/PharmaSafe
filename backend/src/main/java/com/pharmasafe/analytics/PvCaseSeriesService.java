package com.pharmasafe.analytics;

import com.fasterxml.jackson.databind.*;
import com.pharmasafe.dto.CaseSummaryDto;
import com.pharmasafe.entity.*;
import com.pharmasafe.repository.*;
import com.pharmasafe.service.*;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(readOnly = true)
public class PvCaseSeriesService {
  private final PvAnalyticsAccess access;
  private final SafetyCaseRepository cases;
  private final PvCaseSeriesRepository series;
  private final PvSeriesCaseRepository members;
  private final ObjectMapper json;
  private final PvEvidenceFiles files;
  private final AuditService audit;

  public PvCaseSeriesService(
      PvAnalyticsAccess a,
      SafetyCaseRepository c,
      PvCaseSeriesRepository s,
      PvSeriesCaseRepository m,
      ObjectMapper j,
      PvEvidenceFiles f,
      AuditService au) {
    access = a;
    cases = c;
    series = s;
    members = m;
    json = j;
    files = f;
    audit = au;
  }

  public record Criteria(
      LocalDate receiptFrom,
      LocalDate receiptTo,
      @Size(max = 3) String countryCode,
      @Size(max = 32) String reportType,
      @Size(max = 128) String product,
      @Size(max = 200) String reaction,
      @Pattern(regexp = "ALL|SERIOUS|NON_SERIOUS") String seriousness,
      boolean includeSynthetic) {}

  public record Create(
      @NotBlank @Size(max = 160) String name,
      @NotBlank @Size(max = 3000) String purpose,
      @NotNull @Valid Criteria criteria) {}

  private String text(String s) {
    return s == null ? "" : s.trim();
  }

  private void validate(Criteria d) {
    if (d.receiptFrom() != null && d.receiptTo() != null && d.receiptFrom().isAfter(d.receiptTo()))
      throw new IllegalArgumentException("Receipt end date must not precede start date");
    if (!text(d.countryCode()).isEmpty() && !text(d.countryCode()).matches("[A-Z]{3}"))
      throw new IllegalArgumentException("Use a three-letter uppercase country code");
    if (!text(d.reportType()).isEmpty()
        && !Set.of("Spontaneous", "Clinical Trial", "Literature", "Other").contains(d.reportType()))
      throw new IllegalArgumentException("Select a supported report type");
  }

  private String contains(String s) {
    return "%"
        + text(s).toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_")
        + "%";
  }

  private Specification<SafetyCase> query(Criteria d) {
    validate(d);
    return (root, q, cb) -> {
      var p = new ArrayList<jakarta.persistence.criteria.Predicate>();
      p.add(root.get("workflowState").in("Approved", "Locked", "Closed"));
      if (!d.includeSynthetic()) p.add(cb.isFalse(root.get("syntheticRecord")));
      if (d.receiptFrom() != null)
        p.add(cb.greaterThanOrEqualTo(root.get("initialReceiptDate"), d.receiptFrom()));
      if (d.receiptTo() != null)
        p.add(cb.lessThanOrEqualTo(root.get("initialReceiptDate"), d.receiptTo()));
      if (!text(d.countryCode()).isEmpty())
        p.add(cb.equal(root.get("countryCode"), d.countryCode()));
      if (!text(d.reportType()).isEmpty()) p.add(cb.equal(root.get("reportType"), d.reportType()));
      if ("SERIOUS".equals(d.seriousness())) p.add(cb.isTrue(root.get("serious")));
      if ("NON_SERIOUS".equals(d.seriousness())) p.add(cb.isFalse(root.get("serious")));
      if (!text(d.product()).isEmpty()) {
        var sub = q.subquery(Long.class);
        var product = sub.from(CaseProduct.class);
        sub.select(product.get("safetyCase").get("id"))
            .where(
                cb.equal(product.get("safetyCase").get("id"), root.get("id")),
                cb.equal(product.get("productType"), "Suspect"),
                cb.like(cb.lower(product.get("productName")), contains(d.product()), '!'));
        p.add(cb.exists(sub));
      }
      if (!text(d.reaction()).isEmpty()) {
        var sub = q.subquery(Long.class);
        var event = sub.from(CaseEvent.class);
        sub.select(event.get("safetyCase").get("id"))
            .where(
                cb.equal(event.get("safetyCase").get("id"), root.get("id")),
                cb.or(
                    cb.like(cb.lower(event.get("eventVerbatim")), contains(d.reaction()), '!'),
                    cb.like(cb.lower(event.get("meddraPt")), contains(d.reaction()), '!')));
        p.add(cb.exists(sub));
      }
      return cb.and(p.toArray(jakarta.persistence.criteria.Predicate[]::new));
    };
  }

  public Page<CaseSummaryDto> preview(String username, Criteria d, int page) {
    access.actor(username);
    return cases
        .findAll(query(d), PageRequest.of(Math.max(0, page), 20, Sort.by("id")))
        .map(CaseSummaryDto::fromEntity);
  }

  private Map<String, Object> line(SafetyCase c) {
    var v = new LinkedHashMap<String, Object>();
    v.put("caseId", c.getId());
    v.put("caseNumber", c.getCaseNumber());
    v.put("sourceRowVersion", c.getRowVersion());
    v.put("workflowState", c.getWorkflowState());
    v.put("countryCode", c.getCountryCode());
    v.put("reportType", c.getReportType());
    v.put("initialReceiptDate", c.getInitialReceiptDate());
    v.put("serious", c.isSerious());
    v.put("fatal", c.isFatal());
    v.put("syntheticRecord", c.isSyntheticRecord());
    v.put(
        "products",
        c.getProducts().stream()
            .filter(p -> "Suspect".equals(p.getProductType()))
            .map(CaseProduct::getProductName)
            .distinct()
            .sorted()
            .toList());
    v.put(
        "reactions",
        c.getEvents().stream()
            .map(
                e ->
                    e.getMeddraPt() != null && !e.getMeddraPt().isBlank()
                        ? e.getMeddraPt()
                        : e.getEventVerbatim())
            .filter(Objects::nonNull)
            .distinct()
            .sorted()
            .toList());
    v.put(
        "outcomes",
        c.getEvents().stream()
            .map(CaseEvent::getOutcome)
            .filter(Objects::nonNull)
            .distinct()
            .sorted()
            .toList());
    return v;
  }

  private Map<String, Long> distribution(List<Map<String, Object>> rows, String field) {
    var counts = new TreeMap<String, Long>();
    for (var row : rows) {
      var values = row.get(field);
      if (values instanceof Collection<?> items)
        items.stream()
            .map(Object::toString)
            .distinct()
            .forEach(v -> counts.merge(v, 1L, Long::sum));
    }
    return counts;
  }

  private Map<String, Object> metadata(PvCaseSeries s) {
    var v = new LinkedHashMap<String, Object>();
    v.put("id", s.id);
    v.put("name", s.name);
    v.put("purpose", s.purpose);
    v.put("caseCount", s.caseCount);
    v.put("capturedAt", s.capturedAt);
    v.put("username", s.analyst.getUsername());
    v.put("staffCode", s.analyst.getStaffCode());
    v.put("sha256", s.pdfSha256);
    return v;
  }

  public PvCaseSeries readable(String id, User actor) {
    var s =
        series.findById(id).orElseThrow(() -> new EntityNotFoundException("Case series not found"));
    access.read(actor, s.analyst.getId());
    return s;
  }

  public Page<Map<String, Object>> list(String username, Long userId, int page) {
    var a = access.actor(username);
    if (userId != null) access.read(a, userId);
    Specification<PvCaseSeries> scope =
        (r, q, c) ->
            "ROLE_PV_ANALYST".equals(a.getRole())
                ? c.equal(r.get("analyst").get("id"), a.getId())
                : userId == null ? c.conjunction() : c.equal(r.get("analyst").get("id"), userId);
    return series
        .findAll(
            scope,
            PageRequest.of(
                Math.max(0, page),
                20,
                Sort.by(Sort.Order.desc("capturedAt"), Sort.Order.asc("id"))))
        .map(this::metadata);
  }

  public Map<String, Object> detail(String username, String id) {
    var a = access.actor(username);
    var s = readable(id, a);
    var v = metadata(s);
    try {
      v.put("criteria", json.readTree(s.criteriaJson));
      v.put("snapshot", json.readTree(s.snapshotJson));
    } catch (Exception e) {
      throw new IllegalStateException("Stored series evidence cannot be read", e);
    }
    return v;
  }

  @Transactional(isolation = Isolation.REPEATABLE_READ)
  public Map<String, Object> create(String username, Create d) {
    var a = access.actor(username);
    access.author(a);
    var population = cases.findAll(query(d.criteria()), PageRequest.of(0, 201, Sort.by("id")));
    if (population.getTotalElements() > 200)
      throw new IllegalArgumentException(
          "More than 200 cases match. Narrow the search before freezing a case series.");
    if (population.isEmpty())
      throw new IllegalArgumentException("No reviewed cases match these criteria");
    var s = new PvCaseSeries();
    s.id = UUID.randomUUID().toString();
    s.analyst = a;
    s.name = d.name().trim();
    s.purpose = d.purpose().trim();
    s.capturedAt = Instant.now();
    s.caseCount = population.getNumberOfElements();
    var rows = population.stream().map(this::line).toList();
    var snapshot = json.createObjectNode();
    var control = snapshot.putObject("documentControl");
    control.put("documentKind", "PV_CASE_SERIES");
    control.put("documentId", s.id);
    control.put("reportId", "PV-SERIES-" + s.id);
    control.put("generatedBy", username);
    control.put("generatedAtUtc", s.capturedAt.toString());
    control.put("finalDocument", false);
    snapshot.put("name", s.name);
    snapshot.put("purpose", s.purpose);
    snapshot.set("criteria", json.valueToTree(d.criteria()));
    snapshot.set("caseListing", json.valueToTree(rows));
    var stats = snapshot.putObject("statistics");
    stats.put("reviewedCases", s.caseCount);
    stats.put("seriousCases", population.stream().filter(SafetyCase::isSerious).count());
    stats.put("fatalCases", population.stream().filter(SafetyCase::isFatal).count());
    stats.set("products", json.valueToTree(distribution(rows, "products")));
    stats.set("reactions", json.valueToTree(distribution(rows, "reactions")));
    stats.set("outcomes", json.valueToTree(distribution(rows, "outcomes")));
    stats.put("scope", "Frozen reviewed-case series captured at " + s.capturedAt);
    stats.put(
        "interpretation",
        "Distinct cases per category; categories may overlap. Descriptive report counts do not"
            + " measure incidence, establish causality or validate a safety signal.");
    try {
      s.criteriaJson = json.writeValueAsString(d.criteria());
      s.snapshotJson = json.writeValueAsString(snapshot);
      s.pdfBytes = files.render(snapshot);
      s.pdfSha256 = files.hash(s.pdfBytes);
    } catch (java.io.IOException e) {
      throw new IllegalStateException(e);
    }
    series.saveAndFlush(s);
    for (int i = 0; i < population.getNumberOfElements(); i++) {
      var c = population.getContent().get(i);
      var m = new PvSeriesCase();
      m.series = s;
      m.safetyCase = c;
      m.sourceRowVersion = c.getRowVersion();
      m.caseNumber = c.getCaseNumber();
      try {
        m.snapshotJson = json.writeValueAsString(rows.get(i));
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
      members.save(m);
    }
    members.flush();
    audit.logAccountEvent(
        "PV_CASE_SERIES_CREATED", username, "series=" + s.id + "; cases=" + s.caseCount);
    return metadata(s);
  }

  @Transactional
  public CaseDocumentService.Download download(String username, String id) {
    var a = access.actor(username);
    var s = readable(id, a);
    audit.logAccountEvent("PV_CASE_SERIES_DOWNLOADED", username, "series=" + id);
    return files.download("PV-case-series-" + id + ".pdf", s.pdfBytes, s.pdfSha256);
  }
}
