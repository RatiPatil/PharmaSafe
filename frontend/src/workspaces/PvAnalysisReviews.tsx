import { useEffect, useState } from "react";
import { apiRequest, downloadPdf } from "../config/api";
import { useAuth } from "../context/AuthContext";
import { dateTime, type Page } from "./model";
type Review = {
  id: number;
  outcome: string;
  comments: string;
  reviewer: string;
  reviewedAt: string;
  followUpDueDate: string | null;
};
export function PvAnalysisReviews({
  analysisId,
  superseded,
  onSaved,
  onDirtyChange,
}: {
  analysisId: string;
  superseded: boolean;
  onSaved: () => void;
  onDirtyChange: (v: boolean) => void;
}) {
  const { user } = useAuth();
  const reviewer = ["ROLE_ADMIN", "ROLE_SAFETY_MANAGER"].includes(
    user?.role || "",
  );
  const [data, setData] = useState<Page<Review>>({
      content: [],
      totalElements: 0,
      totalPages: 0,
    }),
    [page, setPage] = useState(0),
    [latest, setLatest] = useState<number | null>(null),
    [outcome, setOutcome] = useState("ACKNOWLEDGED"),
    [comments, setComments] = useState(""),
    [due, setDue] = useState(""),
    [busy, setBusy] = useState(false),
    [error, setError] = useState(""),
    [notice, setNotice] = useState(""),
    [reload, setReload] = useState(0);
  useEffect(() => {
    onDirtyChange(!!comments || !!due);
    return () => onDirtyChange(false);
  }, [comments, due, onDirtyChange]);
  useEffect(() => {
    const c = new AbortController();
    apiRequest<Page<Review>>(
      `/api/pv-analyses/${analysisId}/reviews?page=${page}`,
      "GET",
      undefined,
      c.signal,
    )
      .then((r) => {
        if (!c.signal.aborted) {
          setData(r);
          if (page === 0) setLatest(r.content[0]?.id || 0);
        }
      })
      .catch((e) => {
        if (!c.signal.aborted) setError(e.message);
      });
    return () => c.abort();
  }, [analysisId, page, reload]);
  async function save(e: React.FormEvent) {
    e.preventDefault();
    if (busy) return;
    setBusy(true);
    setError("");
    try {
      await apiRequest(`/api/pv-analyses/${analysisId}/reviews`, "POST", {
        expectedLatestReviewId: latest,
        outcome,
        comments,
        followUpDueDate: outcome === "FOLLOW_UP_REQUIRED" ? due : null,
      });
      setComments("");
      setDue("");
      setPage(0);
      setReload((v) => v + 1);
      setNotice(
        "Review recorded. The analyst can read this response and download its original PDF.",
      );
      onSaved();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not record review");
    } finally {
      setBusy(false);
    }
  }
  async function pdf(id: number) {
    setBusy(true);
    setError("");
    try {
      await downloadPdf(
        `/api/pv-analyses/${analysisId}/reviews/${id}/pdf`,
        `PV-review-${id}.pdf`,
      );
    } catch (e) {
      setError(e instanceof Error ? e.message : "Review download failed");
    } finally {
      setBusy(false);
    }
  }
  return (
    <section
      className="pv-review-panel"
      aria-label="Analysis review history"
      data-validation-context="pv"
    >
      <h3>Manager review & follow-up</h3>
      <p>
        Acknowledgement records analytical review. Clinical case approvals and
        formal signal evaluation retain their separate workflows.
      </p>
      {error && (
        <div role="alert" className="ws-error">
          {error}
          <button
            disabled={busy}
            onClick={() => {
              if (
                (comments || due) &&
                !window.confirm("Keep your draft and reload review history?")
              )
                return;
              setPage(0);
              setReload((v) => v + 1);
            }}
          >
            Reload review history
          </button>
        </div>
      )}
      {notice && <p role="status">{notice}</p>}
      {reviewer && !superseded && (
        <form className="ws-form" onSubmit={save} inert={busy || undefined}>
          <label>
            Review decision
            <select
              aria-label="Analysis review decision"
              value={outcome}
              onChange={(e) => {
                setOutcome(e.target.value);
                if (e.target.value !== "FOLLOW_UP_REQUIRED") setDue("");
              }}
            >
              <option value="ACKNOWLEDGED">Acknowledge analysis</option>
              <option value="RETURNED">Return for correction</option>
              <option value="FOLLOW_UP_REQUIRED">
                Request follow-up analysis
              </option>
            </select>
          </label>
          <label className="ws-full">
            Review comments
            <textarea
              required
              maxLength={3000}
              value={comments}
              onChange={(e) => setComments(e.target.value)}
            />
          </label>
          {outcome === "FOLLOW_UP_REQUIRED" && (
            <label>
              Follow-up due date
              <input
                type="date"
                required
                min={new Date().toLocaleDateString("en-CA")}
                value={due}
                onChange={(e) => setDue(e.target.value)}
              />
            </label>
          )}
          <button disabled={busy || latest === null || page !== 0}>
            Record review & generate response PDF
          </button>
        </form>
      )}
      {!data.content.length && <p>No managerial reviews recorded yet.</p>}
      <ol className="pv-review-history">
        {data.content.map((r) => (
          <li key={r.id}>
            <strong>{r.outcome.replaceAll("_", " ")}</strong>
            <p>
              {r.reviewer} · {dateTime(r.reviewedAt)}
              {r.followUpDueDate ? " · Due " + r.followUpDueDate : ""}
            </p>
            <p className="ws-prose">{r.comments}</p>
            <button disabled={busy} onClick={() => void pdf(r.id)}>
              Download review response PDF
            </button>
          </li>
        ))}
      </ol>
      <div className="ws-actions">
        <button
          disabled={busy || page === 0}
          onClick={() => setPage((v) => v - 1)}
        >
          Previous reviews
        </button>
        <span>Page {page + 1}</span>
        <button
          disabled={busy || page + 1 >= data.totalPages}
          onClick={() => setPage((v) => v + 1)}
        >
          Next reviews
        </button>
      </div>
    </section>
  );
}
