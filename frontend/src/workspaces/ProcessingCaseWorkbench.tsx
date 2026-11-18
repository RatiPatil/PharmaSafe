import { useCallback, useEffect, useState } from "react";
import { apiRequest } from "../config/api";
import type { SafetyCaseDetails } from "../types/case";
import { CaseEntryForm } from "../components/CaseEntryForm";
import { CaseRecordView } from "./CaseRecordView";
import { CaseLaboratory } from "./CaseLaboratory";
import { CaseDocuments } from "./CaseDocuments";
import { CaseActivities } from "./CaseActivities";
import { DuplicateCheck } from "./DuplicateCheck";
import { dateTime } from "./model";
export type ProcessingReadiness = {
  linked: boolean;
  caseRowVersion: number | null;
  facts: Record<string, boolean>;
  missing: string[];
  evidence: {
    id: number;
    kind: string;
    outcome: string;
    notes: string;
    dictionaryReference: string;
    recordedBy: string;
    recordedAt: string;
    caseRowVersion: number;
    current: boolean;
  }[];
};
const factLabels: Record<string, string> = {
  patient: "Identifiable patient",
  reporter: "Identifiable reporter",
  suspectProduct: "Suspect product",
  adverseEvent: "Adverse event & onset",
  eventCoding: "Event coding",
  narrative: "Case narrative",
};
const tabs = {
  case: "Case entry",
  review: "Duplicate & coding review",
  followup: "Follow-up & contacts",
  laboratory: "Laboratory",
  documents: "Case PDFs",
};
export function ProcessingCaseWorkbench({
  assignmentId,
  caseId,
  canWork,
  canHandoff,
  employee,
  readiness,
  onSaved,
  onDirtyChange,
}: {
  assignmentId: number;
  caseId: number | null;
  canWork: boolean;
  canHandoff?: boolean;
  employee: boolean;
  readiness?: ProcessingReadiness;
  onSaved: () => void;
  onDirtyChange: (v: boolean) => void;
}) {
  const [record, setRecord] = useState<SafetyCaseDetails | null>(null),
    [loading, setLoading] = useState(false),
    [error, setError] = useState(""),
    [notice, setNotice] = useState(""),
    [reload, setReload] = useState(0);
  const [tab, setTab] = useState<keyof typeof tabs>("case"),
    [creating, setCreating] = useState(false),
    [caseNumber, setCaseNumber] = useState(""),
    [busy, setBusy] = useState(false),
    [handoffReason, setHandoffReason] = useState(""),
    [dirty, setDirty] = useState(false);
  const [review, setReview] = useState({
    kind: "DUPLICATE_REVIEW",
    outcome: "NO_DUPLICATE",
    notes: "",
    dictionaryReference: "",
  });
  const changeDirty = useCallback(
    (v: boolean) => {
      setDirty(v);
      onDirtyChange(v);
    },
    [onDirtyChange],
  );
  useEffect(() => () => onDirtyChange(false), [onDirtyChange]);
  useEffect(() => {
    if (!caseId || dirty) return;
    const c = new AbortController();
    setLoading(true);
    setError("");
    apiRequest<SafetyCaseDetails>(
      `/api/processing-teams/work/${assignmentId}/clinical-case`,
      "GET",
      undefined,
      c.signal,
    )
      .then((v) => {
        if (!c.signal.aborted) setRecord(v);
      })
      .catch((e) => {
        if (!c.signal.aborted) setError(e.message);
      })
      .finally(() => {
        if (!c.signal.aborted) setLoading(false);
      });
    return () => c.abort();
  }, [assignmentId, caseId, reload, readiness?.caseRowVersion, dirty]);
  const refresh = useCallback(() => {
    setReload((v) => v + 1);
    onSaved();
  }, [onSaved]);
  const ignoreReadiness = useCallback(() => {}, []);
  const saved = (v: SafetyCaseDetails) => {
    setRecord(v);
    setError("");
    setCreating(false);
    changeDirty(false);
    setNotice(
      `Saved ${v.caseNumber}. Continue the processing steps below, then submit your task response.`,
    );
    onSaved();
  };
  function choose(next: keyof typeof tabs) {
    if (dirty && !window.confirm("Leave unsaved clinical work?")) return;
    changeDirty(false);
    setReview({
      kind: "DUPLICATE_REVIEW",
      outcome: "NO_DUPLICATE",
      notes: "",
      dictionaryReference: "",
    });
    setTab(next);
    setError("");
    setNotice("");
    setReload((v) => v + 1);
  }
  const editable =
    employee &&
    canWork &&
    (!record ||
      (!record.locked &&
        ["Draft", "Data Entry"].includes(record.workflowState)));
  return (
    <section
      className="cp-clinical-workbench"
      data-validation-context="case"
      aria-label="Clinical processing workbench"
    >
      <section className="ws-card cp-clinical-intro">
        <div>
          <p className="cp-eyebrow">
            {employee
              ? "EMPLOYEE WORKSTATION"
              : "SUPERVISOR CLINICAL INSPECTION"}
          </p>
          <h2>Clinical case processing</h2>
          <p>
            {employee
              ? "1. Save case data. 2. Review duplicates and coding. 3. Record supporting evidence. 4. Submit your task response to your Team Lead."
              : "Inspect saved case data and revision-specific processing evidence. Clinical editing remains with the assigned employee."}
          </p>
        </div>
        {record && (
          <span className="ws-badge">
            {record.caseNumber} · {record.workflowState}
          </span>
        )}
      </section>
      {error && (
        <div role="alert" className="cp-error">
          {error}
          {caseId && (
            <button
              type="button"
              onClick={() => {
                if (
                  dirty &&
                  !window.confirm(
                    "Discard unsaved clinical changes and reload the saved case?",
                  )
                )
                  return;
                changeDirty(false);
                setReview((v) => ({
                  ...v,
                  notes: "",
                  dictionaryReference: "",
                }));
                setError("");
                refresh();
              }}
            >
              Reload saved case
            </button>
          )}
        </div>
      )}
      {notice && (
        <p role="status" className="cp-success">
          {notice}
        </p>
      )}
      {!caseId && !record && !creating && (
        <section className="ws-card">
          <h3>Start the actual case work</h3>
          <p>
            Create a case from the source report or link an existing case
            assigned to your account. The clinical record is shared with your
            Team Lead and Case Processing Admin through this task.
          </p>
          {canWork && employee ? (
            <>
              <button className="ws-primary" onClick={() => setCreating(true)}>
                Create clinical case for this task
              </button>
              <form
                className="cp-case-link-form"
                onSubmit={async (e) => {
                  e.preventDefault();
                  if (busy) return;
                  setBusy(true);
                  setError("");
                  try {
                    saved(
                      await apiRequest<SafetyCaseDetails>(
                        `/api/processing-teams/work/${assignmentId}/clinical-case/link`,
                        "POST",
                        { caseNumber },
                      ),
                    );
                    setCaseNumber("");
                  } catch (e) {
                    setError(
                      e instanceof Error ? e.message : "Could not link case.",
                    );
                  } finally {
                    setBusy(false);
                  }
                }}
              >
                <label>
                  Existing case number
                  <input
                    required
                    maxLength={64}
                    value={caseNumber}
                    onChange={(e) => setCaseNumber(e.target.value)}
                    placeholder="Case assigned to me"
                  />
                </label>
                <button disabled={busy}>Link my existing case</button>
              </form>
              <p className="cp-muted">
                For shared instructions or nonclinical activities, use the
                progress form below. Clinical stage checks apply once a case is
                linked. Case links preserve the original assignment and cannot
                be replaced.
              </p>
            </>
          ) : (
            <p>The employee has not linked a clinical case.</p>
          )}
        </section>
      )}
      {creating && (
        <CaseEntryForm
          hideWorkflowRouting
          createEndpoint={`/api/processing-teams/work/${assignmentId}/clinical-case`}
          onDirtyChange={changeDirty}
          onSaveSuccess={saved}
          onCancel={() => {
            if (
              !dirty ||
              window.confirm("Discard this unsaved clinical case?")
            ) {
              setCreating(false);
              changeDirty(false);
            }
          }}
        />
      )}
      {loading && <p role="status">Loading saved clinical record…</p>}
      {record && (
        <>
          {readiness &&
            ["Draft", "Data Entry"].includes(record.workflowState) && (
              <section className="ws-card">
                <h3>Saved case completeness</h3>
                <div className="cp-clinical-facts">
                  {Object.entries(factLabels).map(([k, label]) => (
                    <span
                      key={k}
                      className={
                        readiness.facts[k] ? "is-complete" : "is-missing"
                      }
                    >
                      {readiness.facts[k] ? "✓" : "○"} {label}
                    </span>
                  ))}
                </div>
                {readiness.missing.length ? (
                  <>
                    <h4>Before completing this assigned stage</h4>
                    <ul>
                      {readiness.missing.map((m) => (
                        <li key={m}>{m}</li>
                      ))}
                    </ul>
                  </>
                ) : (
                  <p className="cp-success">
                    The saved clinical checks for this task's stage are
                    complete. Submit your work summary below.
                  </p>
                )}
                <small>
                  Checks use saved data. A later case edit requires new
                  duplicate/coding verification for that revision.
                </small>
              </section>
            )}
          {employee && record.workflowState === "Medical Review" && (
            <p className="cp-success" role="status">
              This case is in Medical Review. Processing data entry is closed;
              the Medical Reviewer makes the clinical decision.
            </p>
          )}
          {employee && canHandoff && record.workflowState === "Data Entry" && (
            <form
              className="ws-card ws-form"
              data-validation-context="processing"
              onSubmit={async (e) => {
                e.preventDefault();
                if (busy) return;
                setBusy(true);
                setError("");
                try {
                  saved(
                    await apiRequest<SafetyCaseDetails>(
                      `/api/processing-teams/work/${assignmentId}/medical-handoff`,
                      "POST",
                      {
                        expectedCaseVersion: record.rowVersion,
                        reason: handoffReason,
                      },
                    ),
                  );
                  setHandoffReason("");
                  setNotice(
                    "Clinical case sent to Medical Review. Your task response PDF and processing reviews are preserved.",
                  );
                } catch (e) {
                  setError(
                    e instanceof Error ? e.message : "Medical handoff failed.",
                  );
                } finally {
                  setBusy(false);
                }
              }}
            >
              <h3>Send prepared case to Medical Review</h3>
              <p>
                The task response is complete. This separate action routes the
                clinical case to the Medical Reviewer queue.
              </p>
              <label className="cp-wide">
                Medical handoff reason
                <textarea
                  required
                  maxLength={2000}
                  value={handoffReason}
                  onChange={(e) => {
                    setHandoffReason(e.target.value);
                    changeDirty(true);
                  }}
                />
              </label>
              <button disabled={busy}>Send case to Medical Review</button>
            </form>
          )}
          {employee && (
            <nav
              className="cp-clinical-tabs"
              aria-label="Clinical work sections"
            >
              {Object.entries(tabs).map(([k, label]) => (
                <button
                  key={k}
                  aria-current={tab === k ? "page" : undefined}
                  onClick={() => choose(k as keyof typeof tabs)}
                >
                  {label}
                </button>
              ))}
            </nav>
          )}
          {(!employee || tab === "case") &&
            (editable ? (
              <CaseEntryForm
                hideWorkflowRouting
                key={record.id}
                initialCase={record}
                onDirtyChange={changeDirty}
                onSaveSuccess={saved}
                onCancel={() => {
                  if (
                    !dirty ||
                    window.confirm("Discard unsaved case changes?")
                  ) {
                    changeDirty(false);
                    refresh();
                  }
                }}
              />
            ) : (
              <CaseRecordView
                record={record}
                role={employee ? "ROLE_CASE_PROCESSOR" : "ROLE_CASE_TEAM_LEAD"}
              />
            ))}
          {employee && tab === "review" && (
            <section className="ws-card">
              <h3>Duplicate review & dictionary verification</h3>
              <DuplicateCheck
                patientIdentifier={record.patient?.patientIdentifier}
                productName={record.products[0]?.productName}
                eventTerm={record.events[0]?.eventVerbatim}
                excludeCaseId={record.id}
              />
              <p>
                Review source documents and possible matches. Search covers your
                accessible cases; ask your Team Lead to investigate broader
                matches. No automatic merge or licensed dictionary verification
                is performed.
              </p>
              {editable && (
                <form
                  className="ws-form"
                  data-validation-context="processing"
                  onSubmit={async (e) => {
                    e.preventDefault();
                    if (busy) return;
                    setBusy(true);
                    setError("");
                    try {
                      await apiRequest(
                        `/api/processing-teams/work/${assignmentId}/processing-evidence`,
                        "POST",
                        { ...review, expectedCaseVersion: record.rowVersion },
                      );
                      setReview((v) => ({
                        ...v,
                        notes: "",
                        dictionaryReference: "",
                      }));
                      changeDirty(false);
                      setNotice(
                        "Processing evidence recorded against this saved case revision.",
                      );
                      refresh();
                    } catch (e) {
                      setError(
                        e instanceof Error
                          ? e.message
                          : "Could not record review.",
                      );
                    } finally {
                      setBusy(false);
                    }
                  }}
                >
                  <label>
                    Review type
                    <select
                      aria-label="Review type"
                      value={review.kind}
                      onChange={(e) => {
                        changeDirty(true);
                        setReview((v) => ({
                          ...v,
                          kind: e.target.value,
                          outcome:
                            e.target.value === "CODING"
                              ? "VERIFIED"
                              : "NO_DUPLICATE",
                        }));
                      }}
                    >
                      <option value="DUPLICATE_REVIEW">Duplicate review</option>
                      <option value="CODING">
                        Drug & event coding verification
                      </option>
                    </select>
                  </label>
                  {review.kind === "DUPLICATE_REVIEW" ? (
                    <label>
                      Duplicate review outcome
                      <select
                        aria-label="Duplicate review outcome"
                        value={review.outcome}
                        onChange={(e) => {
                          changeDirty(true);
                          setReview((v) => ({ ...v, outcome: e.target.value }));
                        }}
                      >
                        <option value="NO_DUPLICATE">
                          No duplicate identified
                        </option>
                        <option value="POTENTIAL_DUPLICATE">
                          Potential duplicate — unresolved
                        </option>
                        <option value="DISTINCT_CASE">
                          Reviewed matches — distinct case
                        </option>
                      </select>
                    </label>
                  ) : (
                    <label>
                      Dictionary names, releases & product coding reference
                      <input
                        required
                        maxLength={500}
                        value={review.dictionaryReference}
                        onChange={(e) => {
                          changeDirty(true);
                          setReview((v) => ({
                            ...v,
                            dictionaryReference: e.target.value,
                          }));
                        }}
                        placeholder="Actual licensed sources, release versions and coded product reference"
                      />
                    </label>
                  )}
                  <label className="cp-wide">
                    Review rationale & source references
                    <textarea
                      required
                      maxLength={5000}
                      value={review.notes}
                      onChange={(e) => {
                        changeDirty(true);
                        setReview((v) => ({ ...v, notes: e.target.value }));
                      }}
                    />
                  </label>
                  <p className="cp-muted">
                    Save all case edits first. This evidence is immutable and
                    applies to revision {record.rowVersion}. Record a new review
                    to correct a previous decision.
                  </p>
                  <button disabled={busy || record.rowVersion == null}>
                    Record processing review
                  </button>
                </form>
              )}
            </section>
          )}
          {employee && tab === "followup" && (
            <CaseActivities
              record={record}
              blocked={busy}
              assignees={[]}
              onReload={refresh}
              onDirtyChange={changeDirty}
              onReadiness={ignoreReadiness}
            />
          )}
          {employee && tab === "laboratory" && (
            <CaseLaboratory
              record={record}
              blocked={busy}
              onReload={refresh}
              onDirtyChange={changeDirty}
            />
          )}
          {employee && tab === "documents" && (
            <CaseDocuments record={record} blocked={dirty || busy} />
          )}
          {readiness && (!employee || tab === "review") && (
            <section className="ws-card">
              <h3>Preserved processing reviews</h3>
              {!readiness.evidence.length && (
                <p>
                  No duplicate or coding reviews recorded for this task yet.
                </p>
              )}
              <ol className="cp-update-list">
                {readiness.evidence.map((e) => (
                  <li key={e.id}>
                    <strong>
                      {e.kind === "CODING"
                        ? "Coding verification"
                        : "Duplicate review"}{" "}
                      · {e.outcome.replaceAll("_", " ")}
                    </strong>
                    <p>
                      {e.recordedBy} · {dateTime(e.recordedAt)} · revision{" "}
                      {e.caseRowVersion} ·{" "}
                      {e.current ? "Current revision" : "Previous revision"}
                    </p>
                    <p className="cp-preserve">{e.notes}</p>
                    {e.dictionaryReference && <p>{e.dictionaryReference}</p>}
                  </li>
                ))}
              </ol>
            </section>
          )}
        </>
      )}
    </section>
  );
}
