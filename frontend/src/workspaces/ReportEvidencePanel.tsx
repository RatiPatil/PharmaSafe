import { useEffect, useState, useRef } from "react";
import { apiRequest, downloadPdf } from "../config/api";
import { date, dateTime } from "./model";
import "./ReportEvidencePanel.css";
type Report = {
  id: number;
  reportId: string;
  reportType: string;
  product: string;
  authority: string;
  status: string;
  dueDate: string;
  rowVersion: number;
  totalCases: number;
  seriousCases: number;
  fatalCases: number;
  periodStart?: string;
  periodEnd?: string;
  dataLockPoint?: string;
  summary?: string;
  conclusions?: string;
  recommendations?: string;
};
type Document = {
  id: string;
  fileName: string;
  generatedAt: string;
  generatedBy: string;
  sha256: string;
};
type Receipt = {
  id: number;
  outcome: string;
  reference: string;
  receivedAt: string;
  recordedAt: string;
  recordedBy: string;
  message: string;
  reason: string;
};
type Evidence = {
  report: Report;
  documents: Document[];
  acknowledgements: Receipt[];
  documentCount: number;
  acknowledgementCount: number;
};
export function ReportEvidencePanel({
  reportId,
  writer,
  onClose,
}: {
  reportId: number;
  writer: boolean;
  onClose: () => void;
}) {
  const container = useRef<HTMLElement>(null);
  useEffect(() => {
    container.current?.scrollIntoView?.({ block: "start" });
  }, [reportId]);
  const [data, setData] = useState<Evidence | null>(null),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false),
    [refresh, setRefresh] = useState(0);
  const [outcome, setOutcome] = useState("ACCEPTED"),
    [reference, setReference] = useState(""),
    [receivedAt, setReceivedAt] = useState(""),
    [message, setMessage] = useState(""),
    [reason, setReason] = useState("");
  useEffect(() => {
    const c = new AbortController();
    setData(null);
    apiRequest<Evidence>(
      `/api/reports/${reportId}/evidence`,
      "GET",
      undefined,
      c.signal,
    )
      .then((d) => {
        if (!c.signal.aborted) setData(d);
      })
      .catch((e) => {
        if (!c.signal.aborted) setError(e.message);
      });
    return () => c.abort();
  }, [reportId, refresh]);
  async function action(run: () => Promise<void>) {
    setBusy(true);
    setError("");
    try {
      await run();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Report action failed");
    } finally {
      setBusy(false);
    }
  }
  const r = data?.report;
  return (
    <section
      ref={container}
      className="ws-card report-evidence"
      aria-label="Report evidence and PDF archive"
    >
      <div className="ws-section-heading">
        <div>
          <p className="report-evidence-eyebrow">REPORT EVIDENCE</p>
          <h2>{r?.reportId || "Report details"}</h2>
        </div>
        <button disabled={busy} onClick={onClose}>
          Close report details
        </button>
      </div>
      {error && (
        <div role="alert">
          <p>{error}</p>
          <button
            disabled={busy}
            onClick={() => {
              setError("");
              setRefresh((x) => x + 1);
            }}
          >
            Reload report details
          </button>
        </div>
      )}
      {!data && !error && <p role="status">Loading report evidence…</p>}
      {r && data && (
        <>
          <div className="report-evidence-stats">
            {[
              ["Scheduled cases", r.totalCases],
              ["Serious cases", r.seriousCases],
              ["Fatal cases", r.fatalCases],
              ["Report status", r.status],
            ].map(([label, value]) => (
              <div key={label}>
                <span>{label}</span>
                <strong>{value}</strong>
              </div>
            ))}
          </div>
          <dl className="report-evidence-facts">
            <div>
              <dt>Product / authority</dt>
              <dd>
                {r.product} · {r.authority}
              </dd>
            </div>
            <div>
              <dt>Type / due date</dt>
              <dd>
                {r.reportType} · {date(r.dueDate)}
              </dd>
            </div>
            <div>
              <dt>Reporting period</dt>
              <dd>
                {date(r.periodStart)} – {date(r.periodEnd)}
              </dd>
            </div>
            <div>
              <dt>Data lock point</dt>
              <dd>{date(r.dataLockPoint)}</dd>
            </div>
          </dl>
          <p>
            Internal tracking only. Recording a submission or acknowledgement
            does not send anything to a regulator.
          </p>
          <h3>Report narrative</h3>
          {[
            ["Summary", r.summary],
            ["Conclusions", r.conclusions],
            ["Recommendations", r.recommendations],
          ].map(([label, value]) => (
            <div key={label}>
              <strong>{label}</strong>
              <p className="report-evidence-copy">{value || "Not recorded"}</p>
            </div>
          ))}
          <div className="ws-section-heading">
            <div>
              <h3>PDF archive</h3>
              <p>
                Showing {data.documents.length} of {data.documentCount} archived copies. Each
                preserves its original report, case listing and acknowledgement
                history.
              </p>
            </div>
            <button
              disabled={busy}
              onClick={() =>
                void action(async () => {
                  const d = await apiRequest<Document>(
                    `/api/reports/${reportId}/documents`,
                    "POST",
                    { expectedVersion: r.rowVersion },
                  );
                  setRefresh((x) => x + 1);
                  await downloadPdf(
                    `/api/reports/${reportId}/documents/${d.id}/pdf`,
                    d.fileName,
                  );
                })
              }
            >
              Generate report PDF
            </button>
          </div>
          {data.documents.length ? (
            <ul className="report-evidence-documents">
              {data.documents.map((d) => (
                <li key={d.id}>
                  <div>
                    <strong>{dateTime(d.generatedAt)}</strong>
                    <p>Generated by {d.generatedBy}</p>
                    <small>SHA-256: {d.sha256}</small>
                  </div>
                  <button
                    disabled={busy}
                    onClick={() =>
                      void action(() =>
                        downloadPdf(
                          `/api/reports/${reportId}/documents/${d.id}/pdf`,
                          d.fileName,
                        ),
                      )
                    }
                  >
                    Download report PDF
                  </button>
                </li>
              ))}
            </ul>
          ) : (
            <p>No report PDFs generated yet.</p>
          )}
          <h3>External acknowledgement history</h3>
          <p>
            Showing {data.acknowledgements.length} of {data.acknowledgementCount} entries. Rejections and
            later corrections remain in the history.
          </p>
          {data.acknowledgements.length ? (
            <ol className="report-evidence-receipts">
              {data.acknowledgements.map((a) => (
                <li key={a.id}>
                  <span
                    className={`ws-badge ${a.outcome === "REJECTED" ? "danger" : ""}`}
                  >
                    {a.outcome}
                  </span>
                  <strong>{a.reference}</strong>
                  <p>
                    Received {dateTime(a.receivedAt)} · recorded by{" "}
                    {a.recordedBy} on {dateTime(a.recordedAt)}
                  </p>
                  <p className="report-evidence-copy">
                    {a.message || "No receiver message recorded"}
                  </p>
                  <p>Reason: {a.reason}</p>
                </li>
              ))}
            </ol>
          ) : (
            <p>No external acknowledgements recorded.</p>
          )}
          {writer && r.status === "Submitted" && (
            <form
              className="ws-form"
              onSubmit={(e) => {
                e.preventDefault();
                void action(async () => {
                  await apiRequest(
                    `/api/reports/${reportId}/acknowledgements`,
                    "POST",
                    {
                      expectedVersion: r.rowVersion,
                      outcome,
                      reference: reference.trim(),
                      receivedAt: new Date(receivedAt).toISOString(),
                      message,
                      reason: reason.trim(),
                    },
                  );
                  setReference("");
                  setMessage("");
                  setReason("");
                  setReceivedAt("");
                  setRefresh((x) => x + 1);
                });
              }}
            >
              <h3>Record an acknowledgement</h3>
              <label>
                Receiver outcome
                <select
                  value={outcome}
                  onChange={(e) => setOutcome(e.target.value)}
                >
                  <option value="ACCEPTED">Accepted</option>
                  <option value="REJECTED">Rejected</option>
                </select>
              </label>
              <label>
                Acknowledgement reference
                <input
                  required
                  maxLength={128}
                  value={reference}
                  onChange={(e) => setReference(e.target.value)}
                />
              </label>
              <label>
                Acknowledgement received at
                <input
                  type="datetime-local"
                  step="1"
                  required
                  value={receivedAt}
                  onChange={(e) => setReceivedAt(e.target.value)}
                />
              </label>
              <label>
                Receiver message
                <textarea
                  maxLength={2000}
                  value={message}
                  onChange={(e) => setMessage(e.target.value)}
                />
              </label>
              <label>
                Recording or correction reason
                <textarea
                  required
                  maxLength={2000}
                  value={reason}
                  onChange={(e) => setReason(e.target.value)}
                />
              </label>
              <button disabled={busy}>Save acknowledgement</button>
            </form>
          )}
        </>
      )}
    </section>
  );
}
