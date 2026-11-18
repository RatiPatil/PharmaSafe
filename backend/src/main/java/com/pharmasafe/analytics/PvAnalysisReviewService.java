package com.pharmasafe.analytics;

import com.fasterxml.jackson.databind.*;
import com.pharmasafe.service.*;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class PvAnalysisReviewService {
  private final PvAnalyticsAccess access;
  private final PvAnalysisRepository analyses;
  private final PvAnalysisReviewRepository reviews;
  private final ObjectMapper json;
  private final PvEvidenceFiles files;
  private final AuditService audit;

  public PvAnalysisReviewService(
      PvAnalyticsAccess a,
      PvAnalysisRepository p,
      PvAnalysisReviewRepository r,
      ObjectMapper j,
      PvEvidenceFiles f,
      AuditService au) {
    access = a;
    analyses = p;
    reviews = r;
    json = j;
    files = f;
    audit = au;
  }

  public record Create(
      @NotNull @PositiveOrZero Long expectedLatestReviewId,
      @NotBlank @Pattern(regexp = "ACKNOWLEDGED|RETURNED|FOLLOW_UP_REQUIRED") String outcome,
      @NotBlank @Size(max = 3000) String comments,
      LocalDate followUpDueDate) {}

  private Map<String, Object> view(PvAnalysisReview r) {
    var v = new LinkedHashMap<String, Object>();
    v.put("id", r.id);
    v.put("analysisId", r.analysis.getId());
    v.put("outcome", r.outcome);
    v.put("comments", r.comments);
    v.put("followUpDueDate", r.followUpDueDate);
    v.put("reviewedAt", r.reviewedAt);
    v.put("reviewer", r.reviewer.getUsername());
    v.put("sha256", r.pdfSha256);
    return v;
  }

  public Page<Map<String, Object>> list(String username, String id, int page) {
    var a = access.actor(username);
    var p =
        analyses.findById(id).orElseThrow(() -> new EntityNotFoundException("Analysis not found"));
    access.read(a, p.analyst.getId());
    return reviews
        .findByAnalysisIdOrderByIdDesc(id, PageRequest.of(Math.max(0, page), 20))
        .map(this::view);
  }

  @Transactional
  public Map<String, Object> create(String username, String id, Create d) {
    var a = access.actor(username);
    access.review(a);
    var p =
        analyses.lockById(id).orElseThrow(() -> new EntityNotFoundException("Analysis not found"));
    var previous = reviews.findFirstByAnalysisIdOrderByIdDesc(id).orElse(null);
    long latest = previous == null ? 0 : previous.id;
    if (latest != d.expectedLatestReviewId())
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Another review was recorded. Reload the review history before deciding.");
    if (analyses.findFirstBySupersedesId(id).isPresent())
      throw new IllegalArgumentException(
          "This report has a newer replacement. Review the replacement report instead.");
    if ("FOLLOW_UP_REQUIRED".equals(d.outcome())
        && (d.followUpDueDate() == null || d.followUpDueDate().isBefore(LocalDate.now())))
      throw new IllegalArgumentException("Follow-up requires a due date today or later");
    if (!"FOLLOW_UP_REQUIRED".equals(d.outcome()) && d.followUpDueDate() != null)
      throw new IllegalArgumentException("Only follow-up requests have a due date");
    var r = new PvAnalysisReview();
    r.analysis = p;
    r.reviewer = a;
    r.previous = previous;
    r.outcome = d.outcome();
    r.comments = d.comments().trim();
    r.followUpDueDate = d.followUpDueDate();
    r.reviewedAt = Instant.now();
    var snapshot = json.createObjectNode();
    var control = snapshot.putObject("documentControl");
    control.put("documentKind", "PV_ANALYSIS_REVIEW");
    control.put("documentId", UUID.randomUUID().toString());
    control.put("reportId", "PV-REVIEW-" + id);
    control.put("generatedBy", username);
    control.put("generatedAtUtc", r.reviewedAt.toString());
    control.put("finalDocument", false);
    snapshot.put("analysisId", p.id);
    snapshot.put("analysisTitle", p.title);
    snapshot.put("originalAnalysisSha256", p.pdfSha256);
    snapshot.put("analystStaffCode", p.analyst.getStaffCode());
    snapshot.set("review", json.valueToTree(view(r)));
    snapshot.put(
        "meaning",
        "Analytical report review only. Acknowledgement does not approve a clinical case, validate"
            + " a signal or transmit a regulatory report.");
    try {
      r.snapshotJson = json.writeValueAsString(snapshot);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
    r.pdfBytes = files.render(snapshot);
    r.pdfSha256 = files.hash(r.pdfBytes);
    reviews.saveAndFlush(r);
    audit.logAccountEvent(
        "PV_ANALYSIS_REVIEWED", username, "analysis=" + id + "; outcome=" + r.outcome);
    return view(r);
  }

  @Transactional
  public CaseDocumentService.Download download(String username, String id, Long reviewId) {
    var a = access.actor(username);
    var r =
        reviews
            .findById(reviewId)
            .filter(v -> v.analysis.id.equals(id))
            .orElseThrow(() -> new EntityNotFoundException("Review not found"));
    access.read(a, r.analysis.getAnalyst().getId());
    audit.logAccountEvent(
        "PV_REVIEW_DOWNLOADED", username, "analysis=" + id + "; review=" + reviewId);
    return files.download("PV-review-" + reviewId + ".pdf", r.pdfBytes, r.pdfSha256);
  }
}
