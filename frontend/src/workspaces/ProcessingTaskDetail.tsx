import { useEffect, useRef, useState } from "react";
import { apiRequest, downloadPdf } from "../config/api";
import type {
  ProcessingLevel,
  ProcessingPage,
  WorkAssignment,
} from "./processingModels";
import {
  checklistLabels,
  processingStages,
  workStatusLabels,
} from "./processingModels";
import { CaseRecordView } from "./CaseRecordView";
import type { SafetyCaseDetails } from "../types/case";
import { dateTime } from "./model";
type WorkUpdate = {
  id: number;
  status: string;
  progress: number;
  workDone: string;
  pendingWork: string;
  remainingWork: string;
  blockers: string;
  reason: string;
  recordedAt: string;
  checklist: Record<string, boolean>;
  codingReference: string;
};
type WorkDocument = {
  id: string;
  documentKind: string;
  fileName: string;
  generatedAt: string;
  generatedBy: string;
  sha256: string;
};
type Detail = WorkAssignment & {
  supervisorNote: string;
  reopenedAt: string | null;
  description: string;
  instructions: string;
  caseId: number | null;
  caseNumber: string | null;
  canRespond: boolean;
  updates: ProcessingPage<WorkUpdate>;
  documents: ProcessingPage<WorkDocument>;
};
type ProgressForm = {
  status: string;
  progress: number;
  workDone: string;
  pendingWork: string;
  remainingWork: string;
  blockers: string;
  reason: string;
  codingReference: string;
  checklist: Record<string, boolean>;
};
const empty = (): ProgressForm => ({
  status: "IN_PROGRESS",
  progress: 0,
  workDone: "",
  pendingWork: "",
  remainingWork: "",
  blockers: "",
  reason: "",
  codingReference: "",
  checklist: {},
});
export function ProcessingTaskDetail({
  id,
  level,
  onClose,
  onUpdated,
  onDirtyChange,
}: {
  id: number;
  level: ProcessingLevel;
  onClose: () => void;
  onUpdated: () => void;
  onDirtyChange?: (dirty: boolean) => void;
}) {
  const [data, setData] = useState<Detail | null>(null),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false),
    [reload, setReload] = useState(0),
    [page, setPage] = useState(0),
    [notice, setNotice] = useState("");
  const [clinical, setClinical] = useState<SafetyCaseDetails | null>(null);
  const [form, setForm] = useState<ProgressForm>(empty),
    [dirty, setDirty] = useState(false),
    [reopenReason, setReopenReason] = useState("");
  const dirtyRef = useRef(false);
  useEffect(() => { dirtyRef.current = dirty; }, [dirty]);
  useEffect(() => {
    onDirtyChange?.(dirty);
    return () => onDirtyChange?.(false);
  }, [dirty, onDirtyChange]);
  const root = useRef<HTMLElement>(null);
  useEffect(() => {
    root.current?.scrollIntoView?.({ block: "start" });
  }, [id]);
  useEffect(() => {
    const c = new AbortController();
    apiRequest<Detail>(
      `/api/processing-teams/work/${id}?page=${page}`,
      "GET",
      undefined,
      c.signal,
    )
      .then((d) => {
        if (c.signal.aborted || dirtyRef.current) return;
        setData(d);
        if (!dirtyRef.current)
          setForm((v) => ({
            ...v,
            status: d.status === "BLOCKED" ? "BLOCKED" : "IN_PROGRESS",
            progress: Math.min(d.progress, 99),
          }));
      })
      .catch((e) => {
        if (!c.signal.aborted) setError(e.message);
      });
    return () => c.abort();
  }, [id, reload, page]);
  useEffect(() => {
    const poll = setInterval(() => {
      if (!dirtyRef.current && !busy && document.visibilityState === "visible")
        setReload((v) => v + 1);
    }, 15000);
    return () => clearInterval(poll);
  }, [busy]);
  useEffect(() => {
    const leave = (e: BeforeUnloadEvent) => {
      if (dirtyRef.current) {
        e.preventDefault();
        e.returnValue = "";
      }
    };
    window.addEventListener("beforeunload", leave);
    return () => window.removeEventListener("beforeunload", leave);
  }, []);
  function change<K extends keyof ProgressForm>(
    key: K,
    value: ProgressForm[K],
  ) {
    setDirty(true);
    setForm((v) => ({ ...v, [key]: value }));
  }
  async function mutate(path: string, body: unknown) {
    setError("");
    setNotice("");
    setBusy(true);
    try {
      await apiRequest(path, "POST", body);
      setDirty(false);
      setForm(empty());
      setReopenReason("");
      setPage(0);
      setReload((v) => v + 1);
      onUpdated();
      setNotice("Saved successfully. The original PDF evidence is preserved.");
      return true;
    } catch (e) {
      setError(
        e instanceof Error ? e.message : "Could not save task evidence.",
      );
      return false;
    } finally {
      setBusy(false);
    }
  }
  async function download(d: WorkDocument) {
    setBusy(true);
    setError("");
    try {
      await downloadPdf(
        `/api/processing-teams/work/${id}/documents/${d.id}/pdf`,
        d.fileName,
      );
    } catch (e) {
      setError(e instanceof Error ? e.message : "PDF download failed.");
    } finally {
      setBusy(false);
    }
  }
  const maxPages = data
    ? Math.max(data.updates.totalPages, data.documents.totalPages)
    : 1;
  return (
    <section
      ref={root}
      className="cp-task-detail"
      aria-label="Case Processing assignment details"
    >
      <div className="ws-section-heading">
        <button
          disabled={busy}
          onClick={() => {
            if (!dirty || window.confirm("Leave this unsaved progress form?"))
              onClose();
          }}
        >
          ← Back to worklist
        </button>
        <button
          disabled={busy}
          onClick={() => {
            if (
              dirty &&
              !window.confirm(
                "Discard unsaved progress and reload this assignment?",
              )
            )
              return;
            setDirty(false);
            setForm(empty());
            setError("");
            setReload((v) => v + 1);
          }}
        >
          Refresh task
        </button>
      </div>
      {error && (
        <div className="cp-error" role="alert">
          {error}
        </div>
      )}
      {notice && (
        <p className="cp-success" role="status">
          {notice}
        </p>
      )}
      {!data && !error && (
        <p role="status">Loading assignment and PDF evidence…</p>
      )}
      {data && (
        <>
          <section className="ws-card">
            <p className="cp-eyebrow">
              {data.teamCode} · CP-WORK-{data.id}
            </p>
            <h2>{data.title}</h2>
            <div className="cp-assignment-facts">
              <span>
                <small>Employee</small>
                <strong>
                  {data.employee.fullName} · {data.employee.staffCode}
                </strong>
              </span>
              <span>
                <small>Processing stage</small>
                <strong>
                  {
                    processingStages[
                      data.stage as keyof typeof processingStages
                    ]
                  }
                </strong>
              </span>
              <span>
                <small>Due</small>
                <strong>{dateTime(data.dueAt)}</strong>
              </span>
              <span>
                <small>Status</small>
                <strong>
                  {workStatusLabels[data.status]} · {data.progress}%
                </strong>
              </span>
            </div>
            <div
              className="cp-progress-track"
              aria-label={`Task progress ${data.progress}%`}
            >
              <span style={{ width: `${data.progress}%` }} />
            </div>
            <h3>Assigned work</h3>
            <p className="cp-preserve">{data.description}</p>
            <h3>Instructions</h3>
            <p className="cp-preserve">
              {data.instructions || "No additional instructions"}
            </p>
            {data.caseNumber && (
              <p>
                Clinical case: <strong>{data.caseNumber}</strong>
                {level === "EMPLOYEE" && (
                  <a
                    className="cp-inline-link"
                    target="_blank"
                    rel="noopener noreferrer"
                    href={`#/workspace/case_processor/cases/${data.caseId}`}
                  >
                    Open clinical case →
                  </a>
                )}
              </p>
            )}
            <p className="cp-muted">
              Work completion records task delivery. Medical review, QC approval
              and regulatory submission use the existing clinical workflow.
            </p>
          </section>
          {data.supervisorNote && (
            <section className="cp-success">
              <strong>Correction requested by the supervisor</strong>
              <p className="cp-preserve">{data.supervisorNote}</p>
              <small>{dateTime(data.reopenedAt)}</small>
            </section>
          )}
          {level === "EMPLOYEE" &&
            data.canRespond &&
            data.status !== "COMPLETED" && (
              <form
                className="ws-card ws-form"
                aria-busy={busy}
                inert={busy || undefined}
                onSubmit={(e) => {
                  e.preventDefault();
                  void mutate(`/api/processing-teams/work/${id}/updates`, {
                    ...form,
                    expectedVersion: data.rowVersion,
                  });
                }}
              >
                <h2>Submit my progress</h2>
                <p>
                  Your Team Lead and Case Processing Admin will receive this
                  response and its archived PDF.
                </p>
                <label>
                  Progress status
                  <select
                    aria-label="Progress status"
                    value={form.status}
                    onChange={(e) => {
                      const status = e.target.value;
                      change("status", status);
                      change(
                        "progress",
                        status === "COMPLETED"
                          ? 100
                          : Math.min(form.progress, 99),
                      );
                    }}
                  >
                    <option value="IN_PROGRESS">In progress</option>
                    <option value="BLOCKED">
                      Pending information / blocked
                    </option>
                    <option value="COMPLETED">Completed</option>
                  </select>
                </label>
                <label>
                  Completion percentage
                  <input
                    name="progress"
                    type="number"
                    min={0}
                    max={form.status === "COMPLETED" ? 100 : 99}
                    step={1}
                    required
                    value={form.progress}
                    readOnly={form.status === "COMPLETED"}
                    onChange={(e) => change("progress", Number(e.target.value))}
                  />
                </label>
                <label className="cp-wide">
                  Work completed
                  <textarea
                    name="workDone"
                    required
                    maxLength={5000}
                    value={form.workDone}
                    onChange={(e) => change("workDone", e.target.value)}
                  />
                </label>
                <label>
                  Pending dependencies
                  <textarea
                    name="pendingWork"
                    maxLength={3000}
                    value={form.pendingWork}
                    onChange={(e) => change("pendingWork", e.target.value)}
                  />
                </label>
                <label>
                  Remaining work
                  <textarea
                    name="remainingWork"
                    maxLength={3000}
                    value={form.remainingWork}
                    onChange={(e) => change("remainingWork", e.target.value)}
                  />
                </label>
                <label>
                  Blockers or missing information
                  <textarea
                    name="blockers"
                    required={form.status === "BLOCKED"}
                    maxLength={2000}
                    value={form.blockers}
                    onChange={(e) => change("blockers", e.target.value)}
                  />
                </label>
                <label>
                  Update or correction reason
                  <textarea
                    name="reason"
                    required
                    maxLength={2000}
                    value={form.reason}
                    onChange={(e) => change("reason", e.target.value)}
                  />
                </label>
                <fieldset className="cp-wide cp-checklist">
                  <legend>Processing evidence checklist</legend>
                  {Object.entries(checklistLabels).map(([key, label]) => (
                    <label key={key}>
                      <input
                        type="checkbox"
                        checked={!!form.checklist[key]}
                        onChange={(e) =>
                          change("checklist", {
                            ...form.checklist,
                            [key]: e.target.checked,
                          })
                        }
                      />
                      <span>{label}</span>
                    </label>
                  ))}
                </fieldset>
                <label className="cp-wide">
                  Dictionary / version and coding evidence reference
                  <input
                    name="codingReference"
                    maxLength={500}
                    value={form.codingReference}
                    required={
                      data.stage === "CODING" && form.status === "COMPLETED"
                    }
                    onChange={(e) => change("codingReference", e.target.value)}
                  />
                  <small>
                    For coding tasks, document the actual MedDRA / licensed
                    WHODrug source and release used.
                  </small>
                </label>
                {form.status === "COMPLETED" && (
                  <p>
                    Complete tasks require 100% progress, no pending/remaining
                    work, and the checklist evidence required for this
                    processing stage.
                  </p>
                )}
                <button disabled={busy}>
                  Submit progress & generate response PDF
                </button>
              </form>
            )}
          {data.status === "COMPLETED" && (
            <section className="cp-success">
              <strong>Task completed</strong>
              <p>The employee response and its PDF remain available below.</p>
            </section>
          )}
          {level !== "EMPLOYEE" &&
            data.status === "COMPLETED" &&
            data.teamActive && (
              <form
                className="ws-card ws-form"
                aria-busy={busy}
                inert={busy || undefined}
                onSubmit={(e) => {
                  e.preventDefault();
                  void mutate(`/api/processing-teams/work/${id}/reopen`, {
                    reason: reopenReason,
                  });
                }}
              >
                <h3 className="cp-wide">Request a correction</h3>
                <label className="cp-wide">
                  Reopening reason
                  <textarea
                    name="reason"
                    required
                    maxLength={2000}
                    value={reopenReason}
                    onChange={(e) => {
                      setReopenReason(e.target.value);
                      setDirty(true);
                    }}
                  />
                </label>
                <button disabled={busy}>Reopen task for employee</button>
              </form>
            )}
          {data.caseId && level !== "EMPLOYEE" && (
            <section className="ws-card">
              <h3>Linked clinical work</h3>
              <p>
                Supervisory access is read-only. Medical and QC decisions retain
                their existing role permissions.
              </p>
              <button
                disabled={busy}
                onClick={() => {
                  setBusy(true);
                  setError("");
                  void apiRequest<SafetyCaseDetails>(
                    `/api/processing-teams/work/${id}/clinical-case`,
                  )
                    .then(setClinical)
                    .catch((e) => setError(e.message))
                    .finally(() => setBusy(false));
                }}
              >
                View linked clinical record
              </button>
            </section>
          )}
          {clinical && (
            <CaseRecordView record={clinical} role="ROLE_CASE_TEAM_LEAD" />
          )}
          <section className="ws-card">
            <h3>Assignment and response PDFs</h3>
            <p>
              Original copies are preserved. Instruction PDFs are visible to the
              assigned employee; response PDFs are shared with their Team Lead
              and Case Processing Admin.
            </p>
            <ul className="cp-document-list">
              {data.documents.content.map((d) => (
                <li key={d.id}>
                  <div>
                    <strong>
                      {d.documentKind === "ASSIGNMENT"
                        ? "Task instructions"
                        : "Employee response"}
                    </strong>
                    <p>
                      {dateTime(d.generatedAt)} · {d.generatedBy}
                    </p>
                    <small>SHA-256: {d.sha256}</small>
                  </div>
                  <button disabled={busy} onClick={() => void download(d)}>
                    {d.documentKind === "ASSIGNMENT"
                      ? "Download assignment PDF"
                      : "Download response PDF"}
                  </button>
                </li>
              ))}
            </ul>
          </section>
          <section className="ws-card">
            <h3>Progress history</h3>
            {!data.updates.content.length && <p>No employee responses yet.</p>}
            <ol className="cp-update-list">
              {data.updates.content.map((u) => (
                <li key={u.id}>
                  <div className="ws-section-heading">
                    <strong>
                      {workStatusLabels[u.status]} · {u.progress}%
                    </strong>
                    <span>{dateTime(u.recordedAt)}</span>
                  </div>
                  <dl className="cp-update-facts">
                    {[
                      ["Work completed", u.workDone],
                      ["Pending dependencies", u.pendingWork],
                      ["Remaining work", u.remainingWork],
                      ["Blockers", u.blockers],
                      ["Reason", u.reason],
                      ["Coding reference", u.codingReference],
                    ].map(([label, value]) => (
                      <div key={label}>
                        <dt>{label}</dt>
                        <dd className="cp-preserve">
                          {value || "None recorded"}
                        </dd>
                      </div>
                    ))}
                  </dl>
                  <div className="cp-check-tags">
                    {Object.entries(u.checklist)
                      .filter(([, yes]) => yes)
                      .map(([key]) => (
                        <span key={key}>{checklistLabels[key]}</span>
                      ))}
                  </div>
                </li>
              ))}
            </ol>
            <div className="ws-actions">
              <button
                disabled={page === 0 || busy || dirty}
                onClick={() => setPage((v) => v - 1)}
              >
                Previous evidence
              </button>
              <span>Evidence page {page + 1}</span>
              <button
                disabled={page + 1 >= maxPages || busy || dirty}
                onClick={() => setPage((v) => v + 1)}
              >
                Next evidence
              </button>
            </div>
          </section>
        </>
      )}
    </section>
  );
}
