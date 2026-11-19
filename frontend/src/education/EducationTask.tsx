import { useEffect, useRef, useState } from "react";
import {
  apiRequest,
  downloadPdf,
  refreshCsrfToken,
  getCsrfToken,
  getApiUrl,
  fetchWithTimeout,
  SESSION_EXPIRED_EVENT,
} from "../config/api";
import { AccessDialog } from "../workspaces/AccessDialog";
import type { Domain, Task } from "./types";
import { statuses } from "./types";
import { dateTime } from "../workspaces/model";
const blank = () => ({
  status: "IN_PROGRESS",
  progress: 30,
  workDone: "",
  pendingWork: "",
  remainingWork: "",
  evidence: {} as Record<string, string>,
});
export function EducationTask({
  id,
  domain,
  student,
  manager,
  onClose,
  onSaved,
  onDirty,
}: {
  id: number;
  domain: Domain;
  student: boolean;
  manager: boolean;
  onClose: () => void;
  onSaved: () => void;
  onDirty: (v: boolean) => void;
}) {
  const [data, setData] = useState<Task | null>(null),
    [form, setForm] = useState(blank),
    [file, setFile] = useState<File | null>(null),
    [busy, setBusy] = useState(false),
    [error, setError] = useState(""),
    [notice, setNotice] = useState(""),
    [page, setPage] = useState(0),
    [refresh, setRefresh] = useState(0),
    [review, setReview] = useState(""),
    [feedback, setFeedback] = useState("");
  const fileInput = useRef<HTMLInputElement>(null);
  const dirty =
    Object.values(form.evidence).some(Boolean) ||
    !!form.workDone ||
    !!form.pendingWork ||
    !!form.remainingWork ||
    !!file ||
    form.status !== "IN_PROGRESS" ||
    form.progress !== 30 ||
    !!feedback;
  useEffect(() => {
    onDirty(dirty);
    return () => onDirty(false);
  }, [dirty, onDirty]);
  const dirtyRef = useRef(false);
  dirtyRef.current = dirty || busy;
  useEffect(() => {
    const timer = setInterval(() => {
      if (!dirtyRef.current && document.visibilityState === "visible")
        setRefresh((v) => v + 1);
    }, 15000);
    return () => clearInterval(timer);
  }, []);
  useEffect(() => {
    const c = new AbortController();
    apiRequest<Task>(
      `/api/education/tasks/${id}?page=${page}`,
      "GET",
      undefined,
      c.signal,
    )
      .then(setData)
      .catch((e) => {
        if (!c.signal.aborted) setError(e.message);
      });
    return () => c.abort();
  }, [id, page, refresh]);
  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!data || busy) return;
    setBusy(true);
    setError("");
    try {
      if (!(await refreshCsrfToken()))
        throw Error(
          "Request security could not be verified. Refresh and retry.",
        );
      const body = new FormData();
      body.append(
        "data",
        new Blob(
          [JSON.stringify({ ...form, expectedVersion: data.rowVersion })],
          { type: "application/json" },
        ),
      );
      if (file) body.append("file", file);
      const r = await fetchWithTimeout(
        getApiUrl(`/api/education/tasks/${id}/submissions`),
        {
          method: "POST",
          credentials: "include",
          headers: { "X-XSRF-TOKEN": getCsrfToken() || "" },
          body,
        },
      );
      const result = await r.json();
      if (r.status === 401)
        window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT));
      if (!r.ok) throw Error(result.message || "Submission failed");
      setData(result);
      setForm(blank());
      setFile(null);
      if (fileInput.current) fileInput.current.value = "";
      setNotice(
        "Your response and document were saved. Previous submissions remain preserved.",
      );
      onSaved();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Submission failed");
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="edu-task-detail">
      <div className="ws-section-heading">
        <button disabled={busy} onClick={onClose}>
          ← Back to worklist
        </button>
        <button
          disabled={busy}
          onClick={() => {
            if (dirty && !window.confirm("Discard unsaved work and refresh?"))
              return;
            setForm(blank());
            setFile(null);
            setFeedback("");
            setRefresh((v) => v + 1);
          }}
        >
          Refresh assignment
        </button>
      </div>
      {error && (
        <p role="alert" className="ws-error">
          {error}
        </p>
      )}
      {notice && (
        <p role="status" className="ws-notice">
          {notice}
        </p>
      )}
      {!data ? (
        <p role="status">Loading assigned work…</p>
      ) : (
        <>
          <section className="ws-card">
            <p className="ws-eyebrow">
              {domain.name} · {data.stage}
            </p>
            <h2>{data.title}</h2>
            <div className="edu-task-facts">
              <span>
                <small>Student</small>
                <strong>
                  {data.studentName} · {data.studentCode}
                </strong>
              </span>
              <span>
                <small>College</small>
                <strong>{data.collegeName}</strong>
              </span>
              <span>
                <small>Deadline</small>
                <strong>{dateTime(data.dueAt)}</strong>
              </span>
              <span>
                <small>Current status</small>
                <strong>
                  {statuses[data.status]} · {data.progress}%
                </strong>
              </span>
            </div>
            <progress
              max={100}
              value={data.progress}
              aria-label="Assignment progress"
            />
            <h3>Description</h3>
            <p className="edu-preserve">{data.description}</p>
            <h3>Instructions and required evidence</h3>
            <p className="edu-preserve">{data.instructions}</p>
            <p>
              {data.requireDocument
                ? "A PDF document is required with final submission."
                : "Supporting PDF documents are optional."}
            </p>
          </section>
          {data.archived&&<p role="status" className="ws-notice">This assignment is archived. Its submissions and documents are retained for reference. New responses and reviews are closed.</p>}
          {student && !data.archived && !["SUBMITTED", "COMPLETED"].includes(data.status) && (
            <form
              className="ws-card ws-form"
              aria-label="Student work response"
              inert={busy || undefined}
              onSubmit={submit}
            >
              <h2>Complete your domain work</h2>
              <label>
                Response status
                <select
                  aria-label="Response status"
                  value={form.status}
                  onChange={(e) =>
                    setForm((v) => ({
                      ...v,
                      status: e.target.value,
                      progress:
                        e.target.value === "SUBMITTED"
                          ? 100
                          : Math.min(v.progress, 99),
                    }))
                  }
                >
                  <option value="IN_PROGRESS">Save progress</option>
                  <option value="SUBMITTED">
                    Submit completed work for review
                  </option>
                </select>
              </label>
              <label>
                Completion percentage
                <input
                  type="number"
                  min={0}
                  max={form.status === "SUBMITTED" ? 100 : 99}
                  step={1}
                  required
                  disabled={form.status === "SUBMITTED"}
                  value={form.progress}
                  onChange={(e) =>
                    setForm((v) => ({ ...v, progress: Number(e.target.value) }))
                  }
                />
              </label>
              <label className="edu-wide">
                Work completed
                <textarea
                  name="workDone"
                  required
                  maxLength={5000}
                  value={form.workDone}
                  onChange={(e) =>
                    setForm((v) => ({ ...v, workDone: e.target.value }))
                  }
                />
              </label>
              <label>
                Pending work
                <textarea
                  name="pendingWork"
                  maxLength={3000}
                  value={form.pendingWork}
                  onChange={(e) =>
                    setForm((v) => ({ ...v, pendingWork: e.target.value }))
                  }
                />
              </label>
              <label>
                Remaining work
                <textarea
                  name="remainingWork"
                  maxLength={3000}
                  value={form.remainingWork}
                  onChange={(e) =>
                    setForm((v) => ({ ...v, remainingWork: e.target.value }))
                  }
                />
              </label>
              <fieldset className="edu-wide edu-evidence">
                <legend>Domain evidence · {domain.name}</legend>
                <div className="ws-form">
                  {domain.evidenceFields.map((f) => (
                    <label
                      key={f.key}
                      className={f.choices.length ? "" : "edu-wide"}
                    >
                      {f.label}
                      {f.choices.length ? (
                        <select
                          aria-label={f.label}
                          required={form.status === "SUBMITTED"}
                          value={form.evidence[f.key] || ""}
                          onChange={(e) =>
                            setForm((v) => ({
                              ...v,
                              evidence: {
                                ...v.evidence,
                                [f.key]: e.target.value,
                              },
                            }))
                          }
                        >
                          <option value="">Select a value</option>
                          {f.choices.map((c) => (
                            <option key={c}>{c}</option>
                          ))}
                        </select>
                      ) : (
                        <textarea
                          aria-label={f.label}
                          required={form.status === "SUBMITTED"}
                          maxLength={f.max}
                          value={form.evidence[f.key] || ""}
                          onChange={(e) =>
                            setForm((v) => ({
                              ...v,
                              evidence: {
                                ...v.evidence,
                                [f.key]: e.target.value,
                              },
                            }))
                          }
                        />
                      )}
                    </label>
                  ))}
                </div>
              </fieldset>
              <label className="edu-wide">
                Supporting PDF document
                <input
                  ref={fileInput}
                  type="file"
                  accept="application/pdf,.pdf"
                  required={form.status === "SUBMITTED" && data.requireDocument}
                  onChange={(e) => {
                    const f = e.target.files?.[0] || null;
                    if (f && f.size > 5 * 1024 * 1024) {
                      setError("Upload a PDF of at most 5 MB");
                      e.target.value = "";
                      setFile(null);
                    } else setFile(f);
                  }}
                />
                <small>
                  PDF only, maximum 5 MB. Documents are saved privately with
                  this response.
                </small>
              </label>
              <button disabled={busy} type="submit" className="ws-primary">
                {busy ? "Saving response…" : "Save student response"}
              </button>
            </form>
          )}
          {student && data.status === "SUBMITTED" && (
            <p className="ws-notice">
              Your completed work is submitted. Your College Admin will review
              it and provide feedback.
            </p>
          )}
          {data.status === "COMPLETED" && (
            <p className="ws-notice">
              This work is completed. Its submissions, feedback and supporting
              documents remain available.
            </p>
          )}
          {manager && !data.archived && data.status === "SUBMITTED" && (
            <section className="ws-card">
              <h2>Review student submission</h2>
              <p>
                Read the evidence and supporting PDF before recording your
                decision.
              </p>
              <div className="ws-actions">
                <button onClick={() => setReview("COMPLETED")}>
                  Accept completed work
                </button>
                <button onClick={() => setReview("RETURNED")}>
                  Return for correction
                </button>
              </div>
            </section>
          )}
          <section className="ws-card">
            <h2>Submission history & feedback</h2>
            {!data.submissions?.content.length && (
              <p>No responses have been submitted yet.</p>
            )}
            {data.submissions?.content.map((s) => (
              <article className="edu-submission" key={s.id}>
                <div className="ws-section-heading">
                  <h3>
                    {statuses[s.status]} · {s.progress}%
                  </h3>
                  <span>{dateTime(s.createdAt)}</span>
                </div>
                <h4>Work completed</h4>
                <p className="edu-preserve">{s.workDone}</p>
                <div className="edu-task-facts">
                  <span>
                    <small>Pending</small>
                    {s.pendingWork || "None recorded"}
                  </span>
                  <span>
                    <small>Remaining</small>
                    {s.remainingWork || "None recorded"}
                  </span>
                </div>
                <dl className="edu-domain-evidence">
                  {domain.evidenceFields.map((f) => (
                    <div key={f.key}>
                      <dt>{f.label}</dt>
                      <dd className="edu-preserve">
                        {s.evidence[f.key] || "Not recorded"}
                      </dd>
                    </div>
                  ))}
                </dl>
                {s.documents.map((doc) => (
                  <div key={doc.id} className="edu-document">
                    <span>
                      {doc.fileName}
                      <small>SHA-256: {doc.sha256}</small>
                    </span>
                    <button
                      disabled={busy}
                      onClick={async () => {
                        setBusy(true);
                        try {
                          await downloadPdf(
                            `/api/education/tasks/${id}/documents/${doc.id}/pdf`,
                            doc.fileName,
                          );
                        } catch (e) {
                          setError(
                            e instanceof Error ? e.message : "Download failed",
                          );
                        } finally {
                          setBusy(false);
                        }
                      }}
                    >
                      Download supporting PDF
                    </button>
                  </div>
                ))}
                {s.feedback.map((f) => (
                  <div className="edu-feedback" key={f.id}>
                    <strong>
                      {statuses[f.outcome]} · {f.reviewer}
                    </strong>
                    <p className="edu-preserve">{f.feedback}</p>
                    <small>{dateTime(f.createdAt)}</small>
                  </div>
                ))}
              </article>
            ))}
            <div className="ws-actions">
              <button
                disabled={busy || page === 0}
                onClick={() => setPage((v) => v - 1)}
              >
                Previous responses
              </button>
              <span>
                Page {page + 1} · {data.submissions?.totalElements || 0}{" "}
                responses
              </span>
              <button
                disabled={
                  busy || page + 1 >= (data.submissions?.totalPages || 0)
                }
                onClick={() => setPage((v) => v + 1)}
              >
                Next responses
              </button>
            </div>
          </section>
        </>
      )}
      {review && data && (
        <AccessDialog
          title="Review student work"
          blocked={busy}
          onClose={() => {
            setReview("");
            setFeedback("");
          }}
        >
          <form
            className="ws-form"
            onSubmit={async (e) => {
              e.preventDefault();
              setBusy(true);
              setError("");
              try {
                const result = await apiRequest<Task>(
                  `/api/education/tasks/${id}/review`,
                  "POST",
                  {
                    expectedVersion: data.rowVersion,
                    outcome: review,
                    feedback,
                  },
                );
                setData(result);
                setReview("");
                setFeedback("");
                setNotice("Review decision and feedback saved.");
                onSaved();
              } catch (e) {
                setError(e instanceof Error ? e.message : "Review failed");
              } finally {
                setBusy(false);
              }
            }}
          >
            <p>Decision: {statuses[review]}</p>
            {error && (
              <p role="alert" className="ws-error">
                {error}
              </p>
            )}
            <label className="edu-wide">
              Feedback for student
              <textarea
                name="feedback"
                required
                maxLength={3000}
                value={feedback}
                onChange={(e) => setFeedback(e.target.value)}
              />
            </label>
            <button disabled={busy}>Confirm review decision</button>
          </form>
        </AccessDialog>
      )}
    </div>
  );
}
