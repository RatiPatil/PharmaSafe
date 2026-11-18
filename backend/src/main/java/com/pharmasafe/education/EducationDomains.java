package com.pharmasafe.education;

import com.pharmasafe.service.AccessRoleCatalog;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class EducationDomains {
  public record Field(String key, String label, List<String> choices, int max) {}

  public record Domain(
      String code,
      String name,
      String description,
      List<String> stages,
      List<Field> evidenceFields) {}

  private Field text(String k, String l) {
    return new Field(k, l, List.of(), 3000);
  }

  private Field choice(String k, String l, String... choices) {
    return new Field(k, l, List.of(choices), 64);
  }

  public final List<Domain> all;

  public EducationDomains() {
    all =
        List.of(
            d(
                "ROLE_CASE_PROCESSOR",
                "Practice structured case intake, validation, coding and narrative preparation.",
                List.of(
                    "Case intake",
                    "Minimum criteria validation",
                    "Duplicate review",
                    "Medical coding",
                    "Case narrative"),
                text("sourceSummary", "Patient, reporter, drug and event source summary"),
                text("validationFindings", "Minimum-criteria and duplicate-check findings"),
                text("codingNarrative", "Coding references and processing narrative")),
            d(
                "ROLE_MEDICAL_REVIEWER",
                "Review clinical evidence, causality, seriousness and expectedness.",
                List.of(
                    "Clinical source review",
                    "Causality assessment",
                    "Expectedness review",
                    "Medical conclusion"),
                text("clinicalSummary", "Clinical evidence and timeline"),
                choice(
                    "causality",
                    "Company causality",
                    "Certain",
                    "Probable",
                    "Possible",
                    "Unlikely",
                    "Unassessable"),
                choice(
                    "seriousness",
                    "Assessed seriousness",
                    "Serious",
                    "Non-serious",
                    "Undetermined"),
                choice("expectedness", "Expectedness", "Expected", "Unexpected", "Unknown"),
                text("medicalConclusion", "Medical reasoning and conclusion")),
            d(
                "ROLE_QUALITY_REVIEWER",
                "Check data quality, trace findings and document corrective recommendations.",
                List.of(
                    "Completeness review",
                    "Source verification",
                    "Quality findings",
                    "Quality conclusion"),
                text("checksPerformed", "Completeness and source checks performed"),
                text("findings", "Quality findings and discrepancies"),
                choice(
                    "recommendation",
                    "Quality recommendation",
                    "Acceptable",
                    "Corrections required"),
                text("correctiveActions", "Corrective actions and rationale")),
            d(
                "ROLE_SAFETY_MANAGER",
                "Practice workload planning, risk prioritization and operational oversight.",
                List.of(
                    "Workload planning",
                    "Risk review",
                    "Escalation planning",
                    "Operations summary"),
                text("workloadSummary", "Workload and timeline review"),
                choice("riskLevel", "Operational risk", "Low", "Medium", "High"),
                text("actionPlan", "Action plan, owners and escalation")),
            d(
                "ROLE_REPORTING_SPECIALIST",
                "Prepare reporting evidence, timelines and submission checklists.",
                List.of(
                    "Reporting eligibility",
                    "Timeline assessment",
                    "Report preparation",
                    "Submission checklist"),
                text("reportingBasis", "Reporting eligibility and reference basis"),
                text("timelineAssessment", "Reporting timelines and due-date rationale"),
                text("submissionChecklist", "Submission checklist and reporting summary")),
            d(
                "ROLE_AUDITOR",
                "Trace evidence, document control observations and propose audit findings.",
                List.of(
                    "Audit planning", "Evidence tracing", "Control evaluation", "Audit findings"),
                text("auditScope", "Audit scope and control criteria"),
                text("evidenceReviewed", "Evidence reviewed and traceability"),
                text("observations", "Findings and recommendations")),
            d(
                "ROLE_PV_ANALYST",
                "Explore safety data, assess trends and prepare evidence-based analysis.",
                List.of(
                    "Analysis planning",
                    "Case-series review",
                    "Trend analysis",
                    "Analysis conclusion"),
                text("analysisQuestion", "Analysis question and scope"),
                text("methodology", "Data selection and analysis methodology"),
                text("findings", "Observed trends and supporting evidence"),
                text("limitations", "Limitations and interpretation")));
  }

  private Domain d(String code, String desc, List<String> stages, Field... f) {
    return new Domain(code, AccessRoleCatalog.NAMES.get(code), desc, stages, List.of(f));
  }

  public Domain get(String code) {
    return all.stream()
        .filter(d -> d.code.equals(code))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Select a supported domain"));
  }

  public void evidence(String domain, Map<String, String> values, boolean complete) {
    var descriptor = get(domain);
    if (values == null || values.size() > descriptor.evidenceFields.size())
      throw new IllegalArgumentException("Invalid domain evidence fields");
    var keys = descriptor.evidenceFields.stream().map(Field::key).toList();
    for (var key : values.keySet())
      if (!keys.contains(key))
        throw new IllegalArgumentException("Evidence belongs to another domain");
    for (var f : descriptor.evidenceFields) {
      String v = values.getOrDefault(f.key, "");
      if (v == null
          || v.length() > f.max
          || v.chars().anyMatch(c -> c < 32 && c != 10 && c != 13 && c != 9))
        throw new IllegalArgumentException("Invalid " + f.label);
      if (complete && v.isBlank()) throw new IllegalArgumentException("Complete " + f.label);
      if (!v.isBlank() && !f.choices.isEmpty() && !f.choices.contains(v))
        throw new IllegalArgumentException("Select a valid " + f.label);
    }
  }
}
