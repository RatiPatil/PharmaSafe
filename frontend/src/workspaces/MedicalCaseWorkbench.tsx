import { useCallback, useEffect, useRef, useState } from "react";
import { apiRequest } from "../config/api";
import type { SafetyCaseDetails } from "../types/case";
import { CaseRecordView } from "./CaseRecordView";
import { CaseDocuments } from "./CaseDocuments";
import { dateTime } from "./model";
export type MedicalReadiness = {
  linked: boolean;
  caseRowVersion: number | null;
  workflowState: string | null;
  assessmentCoverage: boolean;
  missing: string[];
  conclusions: {
    id: number;
    medicalNarrative: string;
    reviewSummary: string;
    assessedSeriousness: string;
    severitySummary: string;
    sourceCaseVersion: number;
    recordedAt: string;
    employee: string;
  }[];
};
const emptyAssessment = () => ({
  productName: "",
  eventVerbatim: "",
  causalityCompany: "",
  expectedness: "",
  assessmentComment: "",
});
const emptyConclusion = () => ({
  medicalNarrative: "",
  reviewSummary: "",
  assessedSeriousness: "UNDETERMINED",
  severitySummary: "",
  sourceReviewed: false,
  timelineReviewed: false,
});
export function MedicalCaseWorkbench({
  assignmentId,
  caseId,
  employee,
  canWork,
  stage,
  status,
  readiness,
  onSaved,
  onDirtyChange,
}: {
  assignmentId: number;
  caseId: number | null;
  employee: boolean;
  canWork: boolean;
  canHandoff?: boolean;
  stage: string;
  status: string;
  readiness?: MedicalReadiness;
  onSaved: () => void;
  onDirtyChange: (v: boolean) => void;
}) {
  const [record, setRecord] = useState<SafetyCaseDetails | null>(null),
    [labs, setLabs] = useState<Record<string, unknown>[]>([]),
    [tab, setTab] = useState<
      "source" | "assessment" | "conclusion" | "documents"
    >("source"),
    [loading, setLoading] = useState(false),
    [busy, setBusy] = useState(false),
    [error, setError] = useState(""),
    [notice, setNotice] = useState(""),
    [reload, setReload] = useState(0),
    [assessment, setAssessment] = useState(emptyAssessment),
    [conclusion, setConclusion] = useState(emptyConclusion),
    [decisionReason, setDecisionReason] = useState("");
  const dirty =
    Object.values(assessment).some(Boolean) ||
    conclusion.assessedSeriousness !== "UNDETERMINED" ||
    conclusion.medicalNarrative !== "" ||
    conclusion.reviewSummary !== "" ||
    conclusion.severitySummary !== "" ||
    conclusion.sourceReviewed ||
    conclusion.timelineReviewed ||
    decisionReason !== "";
  const dirtyRef = useRef(false);
  useEffect(() => {
    dirtyRef.current = dirty;
    onDirtyChange(dirty);
    return () => onDirtyChange(false);
  }, [dirty, onDirtyChange]);
  useEffect(() => {
    if (!caseId || dirtyRef.current) return;
    const c = new AbortController();
    setLoading(true);
    setError("");
    Promise.all([
      apiRequest<SafetyCaseDetails>(
        `/api/medical-teams/work/${assignmentId}/clinical-case`,
        "GET",
        undefined,
        c.signal,
      ),
      apiRequest<Record<string, unknown>[]>(
        `/api/medical-teams/work/${assignmentId}/laboratory`,
        "GET",
        undefined,
        c.signal,
      ),
    ])
      .then(([r, l]) => {
        if (!c.signal.aborted && !dirtyRef.current) {
          setRecord(r);
          setLabs(l);
        }
      })
      .catch((e) => {
        if (!c.signal.aborted) setError(e.message);
      })
      .finally(() => {
        if (!c.signal.aborted) setLoading(false);
      });
    return () => c.abort();
  }, [caseId, assignmentId, reload, readiness?.caseRowVersion]);
  useEffect(() => () => onDirtyChange(false), [onDirtyChange]);
  const changed = useCallback(() => {
    setReload((v) => v + 1);
    onSaved();
  }, [onSaved]);
  function choose(next: typeof tab) {
    if (dirty && !window.confirm("Discard unsaved medical review changes?"))
      return;
    setAssessment(emptyAssessment());
    setConclusion(emptyConclusion());
    setDecisionReason("");
    setTab(next);
    setError("");
    setReload((v) => v + 1);
  }
  async function write(
    path: string,
    body: unknown,
    kind: "assessment" | "conclusion" | "decision",
  ) {
    if (busy) return;
    setBusy(true);
    setError("");
    setNotice("");
    try {
      const result = await apiRequest<SafetyCaseDetails>(path, "POST", body);
      if (kind === "assessment") setAssessment(emptyAssessment());
      if (kind === "conclusion") setConclusion(emptyConclusion());
      if (kind === "decision") {
        setRecord(result);
        setDecisionReason("");
      }
      setNotice(
        kind === "decision"
          ? `Clinical case routed to ${result.workflowState}. Original medical task evidence remains preserved.`
          : "Medical evidence saved. Review the remaining stage checks and submit your work response.",
      );
      changed();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Medical review save failed");
    } finally {
      setBusy(false);
    }
  }
  if (!caseId)
    return (
      <section className="ws-card">
        <h2>General medical team instructions</h2>
        <p>
          This assignment is not a clinical case review. Read the instructions,
          then submit your progress and response PDF below. Clinical work must
          be assigned with a case already allocated to this medical team.
        </p>
      </section>
    );
  const editable =
    employee &&
    canWork &&
    record?.workflowState === "Medical Review" &&
    !record.locked;
  return (
    <section
      className="mr-medical-workbench"
      data-validation-context="medical"
      aria-label="Medical clinical workstation"
    >
      <section className="ws-card">
        <p className="ws-eyebrow">
          {employee
            ? "ASSIGNED MEDICAL REVIEW"
            : "MEDICAL SUPERVISORY INSPECTION"}
        </p>
        <h2>Clinical medical review</h2>
        <p>
          Review the source, record product/event causality and expectedness,
          and save a medical conclusion. Medical narratives are preserved
          separately from the processor’s original case narrative.
        </p>
        {record && (
          <span className="ws-badge">
            {record.caseNumber} · {record.workflowState}
          </span>
        )}
      </section>
      {error && (
        <div className="ws-error" role="alert">
          {error}
          <button
            disabled={busy}
            onClick={() => {
              if (
                dirty &&
                !window.confirm("Discard unsaved medical changes and reload?")
              )
                return;
              setAssessment(emptyAssessment());
              setConclusion(emptyConclusion());
              setDecisionReason("");
              setReload((v) => v + 1);
            }}
          >
            Reload medical evidence
          </button>
        </div>
      )}
      {notice && (
        <p role="status" className="ws-notice">
          {notice}
        </p>
      )}
      {loading && !record && (
        <p role="status">Loading medical source evidence…</p>
      )}
      {readiness && (
        <section className="ws-card">
          <h3>Medical stage readiness</h3>
          <div className="mr-clinical-kpis">
            <div>
              <strong>
                {readiness.assessmentCoverage ? "Complete" : "Pending"}
              </strong>
              <span>Suspect product / event assessment coverage</span>
            </div>
            <div>
              <strong>{readiness.workflowState || "Not available"}</strong>
              <span>Current clinical workflow state</span>
            </div>
          </div>
          {readiness.workflowState !== "Medical Review" ? (<p>Medical editing is closed in {readiness.workflowState}. Saved conclusions and original task PDFs remain available below.</p>) : readiness.missing.length ? (
            <ul>
              {readiness.missing.map((s) => (
                <li key={s}>{s}</li>
              ))}
            </ul>
          ) : (
            <p className="ws-notice">
              Saved clinical checks are complete for this medical stage.
            </p>
          )}
          {readiness.workflowState === "Medical Review" && <small>
            Checks use saved data and the current case revision. After adding or
            correcting an assessment, record a fresh conclusion.
          </small>}
        </section>
      )}
      {record && (
        <>
          <nav
            className="mr-work-tabs"
            aria-label="Medical clinical work sections"
          >
            <button
              aria-current={tab === "source" ? "page" : undefined}
              onClick={() => choose("source")}
            >
              Clinical source & labs
            </button>
            {employee && (
              <>
                <button
                  aria-current={tab === "assessment" ? "page" : undefined}
                  onClick={() => choose("assessment")}
                >
                  Causality & expectedness
                </button>
                <button
                  aria-current={tab === "conclusion" ? "page" : undefined}
                  onClick={() => choose("conclusion")}
                >
                  Medical conclusion
                </button>
                {record.workflowState === "Medical Review" && (
                  <button
                    aria-current={tab === "documents" ? "page" : undefined}
                    onClick={() => choose("documents")}
                  >
                    Medical case PDFs
                  </button>
                )}
              </>
            )}
          </nav>
          {tab === "source" && (
            <>
              <CaseRecordView
                record={record}
                role={
                  employee ? "ROLE_MEDICAL_REVIEWER" : "ROLE_MEDICAL_TEAM_LEAD"
                }
              />
              <section className="ws-card">
                <h3>Laboratory source evidence</h3>
                {!labs.length ? (
                  <p>No laboratory records in the source case.</p>
                ) : (
                  <div className="ws-table-wrap">
                    <table>
                      <thead>
                        <tr>
                          <th>Test / date</th>
                          <th>Result / unit</th>
                          <th>Reference range</th>
                          <th>Interpretation</th>
                          <th>Source status</th>
                        </tr>
                      </thead>
                      <tbody>
                        {labs.map((l) => (
                          <tr key={String(l.id)}>
                            <td>
                              {String(l.testName)} · {String(l.testDate)}
                            </td>
                            <td>
                              {String(l.resultText)} {String(l.unit || "")}
                            </td>
                            <td>
                              {String(l.normalLow ?? "—")} to{" "}
                              {String(l.normalHigh ?? "—")}
                            </td>
                            <td>{String(l.interpretation)}</td>
                            <td>
                              {l.voidedAt
                                ? "Voided: " + String(l.voidReason)
                                : "Recorded source result"}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </section>
            </>
          )}
          {tab === "assessment" && (
            <section className="ws-card">
              <h3>Product / event medical assessment</h3>
              <p>
                Record an explicit clinical assessment for each suspect product
                and event. Assessments are attributed to your individual
                reviewer account.
              </p>
              {editable ? (
                <form
                  className="ws-form"
                  inert={busy || undefined}
                  onSubmit={(e) => {
                    e.preventDefault();
                    void write(
                      `/api/medical-teams/work/${assignmentId}/assessments`,
                      { ...assessment, expectedCaseVersion: record.rowVersion },
                      "assessment",
                    );
                  }}
                >
                  <label>
                    Reviewed product
                    <select
                      aria-label="Reviewed product"
                      required
                      value={assessment.productName}
                      onChange={(e) =>
                        setAssessment((v) => ({
                          ...v,
                          productName: e.target.value,
                        }))
                      }
                    >
                      <option value="">Select a case product</option>
                      {record.products.map((p, i) => (
                        <option key={p.id || i} value={p.productName}>
                          {p.productName} · {p.productType}
                        </option>
                      ))}
                    </select>
                  </label>
                  <label>
                    Reviewed event
                    <select
                      aria-label="Reviewed event"
                      required
                      value={assessment.eventVerbatim}
                      onChange={(e) =>
                        setAssessment((v) => ({
                          ...v,
                          eventVerbatim: e.target.value,
                        }))
                      }
                    >
                      <option value="">Select a case event</option>
                      {record.events.map((ev, i) => (
                        <option key={ev.id || i} value={ev.eventVerbatim}>
                          {ev.eventVerbatim}
                        </option>
                      ))}
                    </select>
                  </label>
                  <label>
                    Company causality
                    <select
                      aria-label="Medical company causality"
                      required
                      value={assessment.causalityCompany}
                      onChange={(e) =>
                        setAssessment((v) => ({
                          ...v,
                          causalityCompany: e.target.value,
                        }))
                      }
                    >
                      <option value="">Select causality</option>
                      {[
                        "Possible",
                        "Probable",
                        "Certain",
                        "Unlikely",
                        "Unassessable",
                      ].map((v) => (
                        <option key={v}>{v}</option>
                      ))}
                    </select>
                  </label>
                  <label>
                    Expectedness
                    <select
                      aria-label="Medical expectedness"
                      required
                      value={assessment.expectedness}
                      onChange={(e) =>
                        setAssessment((v) => ({
                          ...v,
                          expectedness: e.target.value,
                        }))
                      }
                    >
                      <option value="">Select expectedness</option>
                      {["Expected", "Unexpected", "Unknown"].map((v) => (
                        <option key={v}>{v}</option>
                      ))}
                    </select>
                  </label>
                  <label className="mr-wide">
                    Assessment reasoning and source references
                    <textarea
                      required
                      maxLength={3000}
                      value={assessment.assessmentComment}
                      onChange={(e) =>
                        setAssessment((v) => ({
                          ...v,
                          assessmentComment: e.target.value,
                        }))
                      }
                    />
                  </label>
                  <button disabled={busy}>Save medical assessment</button>
                </form>
              ) : (
                <p>
                  Medical assessment editing is closed for this assignment or
                  case state.
                </p>
              )}
              <CaseRecordView record={record} role="ROLE_MEDICAL_REVIEWER" />
            </section>
          )}
          {tab === "conclusion" && (
            <section className="ws-card">
              <h3>Medical reviewer conclusion</h3>
              <p>
                Distinguish seriousness criteria from clinical severity. If the
                reviewed seriousness differs from the recorded case, request
                processing correction before QC handoff.
              </p>
              {editable ? (
                <form
                  className="ws-form"
                  inert={busy || undefined}
                  onSubmit={(e) => {
                    e.preventDefault();
                    void write(
                      `/api/medical-teams/work/${assignmentId}/conclusions`,
                      { ...conclusion, expectedCaseVersion: record.rowVersion },
                      "conclusion",
                    );
                  }}
                >
                  <label>
                    Assessed seriousness
                    <select
                      aria-label="Assessed seriousness"
                      value={conclusion.assessedSeriousness}
                      onChange={(e) =>
                        setConclusion((v) => ({
                          ...v,
                          assessedSeriousness: e.target.value,
                        }))
                      }
                    >
                      <option value="UNDETERMINED">
                        Undetermined — needs information
                      </option>
                      <option value="SERIOUS">Serious</option>
                      <option value="NON_SERIOUS">Non-serious</option>
                    </select>
                  </label>
                  <label>
                    Clinical severity summary
                    <textarea
                      required
                      maxLength={1000}
                      value={conclusion.severitySummary}
                      onChange={(e) =>
                        setConclusion((v) => ({
                          ...v,
                          severitySummary: e.target.value,
                        }))
                      }
                    />
                  </label>
                  <label className="mr-wide">
                    Medical narrative
                    <textarea
                      required
                      maxLength={5000}
                      value={conclusion.medicalNarrative}
                      onChange={(e) =>
                        setConclusion((v) => ({
                          ...v,
                          medicalNarrative: e.target.value,
                        }))
                      }
                    />
                  </label>
                  <label className="mr-wide">
                    Medical conclusion and outstanding clinical questions
                    <textarea
                      required
                      maxLength={3000}
                      value={conclusion.reviewSummary}
                      onChange={(e) =>
                        setConclusion((v) => ({
                          ...v,
                          reviewSummary: e.target.value,
                        }))
                      }
                    />
                  </label>
                  <label className="mr-field-check">
                    <input
                      type="checkbox"
                      required
                      checked={conclusion.sourceReviewed}
                      onChange={(e) =>
                        setConclusion((v) => ({
                          ...v,
                          sourceReviewed: e.target.checked,
                        }))
                      }
                    />
                    Source clinical evidence reviewed
                  </label>
                  <label className="mr-field-check">
                    <input
                      type="checkbox"
                      required
                      checked={conclusion.timelineReviewed}
                      onChange={(e) =>
                        setConclusion((v) => ({
                          ...v,
                          timelineReviewed: e.target.checked,
                        }))
                      }
                    />
                    Clinical timeline reviewed
                  </label>
                  <button disabled={busy}>Save medical conclusion</button>
                </form>
              ) : (
                <p>
                  Conclusion entry is closed. Preserved medical conclusions
                  remain available below.
                </p>
              )}
            </section>
          )}
          {tab === "documents" && (
            <CaseDocuments record={record} blocked={dirty || busy} />
          )}
          {employee &&
            record.workflowState === "Medical Review" &&
            ((status === "COMPLETED" && stage === "QUALITY_HANDOFF") ||
              status === "BLOCKED") && (
              <form
                className="ws-card ws-form"
                inert={busy || undefined}
                onSubmit={(e) => e.preventDefault()}
              >
                <h3>Medical review decision</h3>
                <p>
                  {status === "BLOCKED"
                    ? "Your pending-work response PDF is preserved. Return the case to its original Case Processor with a correction reason."
                    : "Your completed medical response PDF is preserved. Route the clinically reviewed case into the Quality Review queue."}
                </p>
                <label className="mr-wide">
                  Medical decision reason
                  <textarea
                    required
                    maxLength={2000}
                    value={decisionReason}
                    onChange={(e) => setDecisionReason(e.target.value)}
                  />
                </label>
                <button
                  type="button"
                  disabled={busy || !decisionReason.trim()}
                  onClick={() =>
                    void write(
                      `/api/medical-teams/work/${assignmentId}/decision`,
                      {
                        expectedCaseVersion: record.rowVersion,
                        targetState:
                          status === "BLOCKED"
                            ? "Data Entry"
                            : "Quality Review",
                        reason: decisionReason,
                      },
                      "decision",
                    )
                  }
                >
                  {status === "BLOCKED"
                    ? "Return case for processing correction"
                    : "Send case to Quality Review"}
                </button>
              </form>
            )}
        </>
      )}
      {readiness && (
        <section className="ws-card">
          <h3>Preserved medical conclusions</h3>
          {!readiness.conclusions.length && (
            <p>No medical conclusions recorded for this task yet.</p>
          )}
          <ol className="mr-update-list">
            {readiness.conclusions.map((c) => (
              <li key={c.id}>
                <strong>
                  {c.assessedSeriousness.replaceAll("_", " ")} · revision{" "}
                  {c.sourceCaseVersion}
                </strong>
                <p>
                  {c.employee} · {dateTime(c.recordedAt)}
                </p>
                <h4>Medical narrative</h4>
                <p className="mr-preserve">{c.medicalNarrative}</p>
                <h4>Clinical conclusion</h4>
                <p className="mr-preserve">{c.reviewSummary}</p>
                <p>Severity: {c.severitySummary}</p>
              </li>
            ))}
          </ol>
        </section>
      )}
    </section>
  );
}
