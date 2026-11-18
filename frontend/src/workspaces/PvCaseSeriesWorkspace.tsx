import { useEffect, useState } from "react";
import { apiRequest, downloadPdf } from "../config/api";
import { useAuth } from "../context/AuthContext";
import { InsightChart } from "./RoleInsights";
import { dateTime, type Page } from "./model";
import type { SafetyCaseSummary } from "../types/case";
export type SeriesSummary = {
  id: string;
  name: string;
  purpose: string;
  caseCount: number;
  capturedAt: string;
  staffCode: string;
};
type Listing = {
  caseId: number;
  caseNumber: string;
  sourceRowVersion: number;
  workflowState: string;
  countryCode: string;
  initialReceiptDate: string;
  serious: boolean;
  fatal: boolean;
  syntheticRecord: boolean;
  products: string[];
  reactions: string[];
  outcomes: string[];
};
type SeriesDetail = SeriesSummary & {
  criteria: Record<string, unknown>;
  snapshot: {
    caseListing: Listing[];
    statistics: {
      reviewedCases: number;
      seriousCases: number;
      fatalCases: number;
      products: Record<string, number>;
      reactions: Record<string, number>;
      outcomes: Record<string, number>;
      interpretation: string;
    };
  };
};
const emptyCriteria = () => ({
  receiptFrom: "",
  receiptTo: "",
  countryCode: "",
  reportType: "",
  product: "",
  reaction: "",
  seriousness: "ALL",
  includeSynthetic: false,
});
export function PvCaseSeriesWorkspace({
  employeeId,
  onDirtyChange,
  onUse,
}: {
  employeeId?: number;
  onDirtyChange: (v: boolean) => void;
  onUse: (series: SeriesSummary) => void;
}) {
  const { user } = useAuth();
  const writer = user?.role === "ROLE_PV_ANALYST";
  const [criteria, setCriteria] = useState(emptyCriteria),
    [applied, setApplied] = useState<ReturnType<typeof emptyCriteria> | null>(
      null,
    ),
    [name, setName] = useState(""),
    [purpose, setPurpose] = useState(""),
    [preview, setPreview] = useState<Page<SafetyCaseSummary> | null>(null),
    [previewPage, setPreviewPage] = useState(0),
    [page, setPage] = useState(0),
    [list, setList] = useState<Page<SeriesSummary>>({
      content: [],
      totalElements: 0,
      totalPages: 0,
    }),
    [selected, setSelected] = useState<SeriesDetail | null>(null),
    [busy, setBusy] = useState(false),
    [error, setError] = useState(""),
    [notice, setNotice] = useState(""),
    [refresh, setRefresh] = useState(0);
  const dirty =
    !!name ||
    !!purpose ||
    JSON.stringify(criteria) !== JSON.stringify(emptyCriteria());
  useEffect(() => {
    onDirtyChange(dirty);
    return () => onDirtyChange(false);
  }, [dirty, onDirtyChange]);
  useEffect(() => {
    const c = new AbortController();
    apiRequest<Page<SeriesSummary>>(
      `/api/pv-analyses/series?page=${page}${employeeId ? "&userId=" + employeeId : ""}`,
      "GET",
      undefined,
      c.signal,
    )
      .then((d) => {
        if (!c.signal.aborted) setList(d);
      })
      .catch((e) => {
        if (!c.signal.aborted) setError(e.message);
      });
    return () => c.abort();
  }, [page, refresh, employeeId]);
  const payload = (v: ReturnType<typeof emptyCriteria>) => ({
    ...v,
    receiptFrom: v.receiptFrom || null,
    receiptTo: v.receiptTo || null,
  });
  async function search(v = criteria, p = 0) {
    if (busy) return;
    setBusy(true);
    setError("");
    try {
      const d = await apiRequest<Page<SafetyCaseSummary>>(
        `/api/pv-analyses/series/preview?page=${p}`,
        "POST",
        payload(v),
      );
      setPreview(d);
      setApplied({ ...v });
      setPreviewPage(p);
    } catch (e) {
      setError(
        e instanceof Error ? e.message : "Could not search reviewed cases",
      );
    } finally {
      setBusy(false);
    }
  }
  async function save(e: React.FormEvent) {
    e.preventDefault();
    if (busy) return;
    if (!applied || JSON.stringify(applied) !== JSON.stringify(criteria)) {
      setError("Run the query again after changing its criteria.");
      return;
    }
    setBusy(true);
    setError("");
    try {
      const r = await apiRequest<SeriesSummary>(
        "/api/pv-analyses/series",
        "POST",
        { name, purpose, criteria: payload(applied) },
      );
      setSelected(
        await apiRequest<SeriesDetail>(`/api/pv-analyses/series/${r.id}`),
      );
      setName("");
      setPurpose("");
      setCriteria(emptyCriteria());
      setApplied(null);
      setPreview(null);
      setRefresh((v) => v + 1);
      setNotice(
        "Case series frozen with its source revisions and original line-listing PDF.",
      );
    } catch (e) {
      setError(e instanceof Error ? e.message : "Case-series capture failed");
    } finally {
      setBusy(false);
    }
  }
  async function open(id: string) {
    if (busy) return;
    setBusy(true);
    setError("");
    try {
      setSelected(
        await apiRequest<SeriesDetail>(`/api/pv-analyses/series/${id}`),
      );
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not load case series");
    } finally {
      setBusy(false);
    }
  }
  async function pdf(id: string) {
    setBusy(true);
    setError("");
    try {
      await downloadPdf(
        `/api/pv-analyses/series/${id}/pdf`,
        `PV-case-series-${id}.pdf`,
      );
    } catch (e) {
      setError(e instanceof Error ? e.message : "PDF download failed");
    } finally {
      setBusy(false);
    }
  }
  return (
    <section
      className="pv-series-workspace"
      data-validation-context="pv"
      aria-label="Case series and queries"
    >
      <section className="ws-card">
        <p className="ws-eyebrow">DEFINE → SEARCH → FREEZE → ANALYSE</p>
        <h2>Case series & saved queries</h2>
        <p>
          Build a reproducible population from Approved, Locked and Closed
          cases. Filters use AND; product searches suspect products, and
          reaction searches the recorded wording or preferred term. Saved
          snapshots remain historical evidence when source cases change.
        </p>
      </section>
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
      {writer && (
        <form
          className="ws-card ws-form"
          inert={busy || undefined}
          onSubmit={(e) => {
            e.preventDefault();
            void search();
          }}
        >
          <h3 className="ws-full">Find reviewed cases</h3>
          <label>
            Initial receipt from
            <input
              type="date"
              value={criteria.receiptFrom}
              onChange={(e) =>
                setCriteria((v) => ({ ...v, receiptFrom: e.target.value }))
              }
            />
          </label>
          <label>
            Initial receipt to
            <input
              type="date"
              min={criteria.receiptFrom || undefined}
              value={criteria.receiptTo}
              onChange={(e) =>
                setCriteria((v) => ({ ...v, receiptTo: e.target.value }))
              }
            />
          </label>
          <label>
            Country
            <select
              aria-label="Series country"
              value={criteria.countryCode}
              onChange={(e) =>
                setCriteria((v) => ({ ...v, countryCode: e.target.value }))
              }
            >
              <option value="">All countries</option>
              {["USA", "GBR", "DEU", "FRA", "JPN", "CAN", "AUS", "IND"].map(
                (v) => (
                  <option key={v}>{v}</option>
                ),
              )}
            </select>
          </label>
          <label>
            Report type
            <select
              aria-label="Series report type"
              value={criteria.reportType}
              onChange={(e) =>
                setCriteria((v) => ({ ...v, reportType: e.target.value }))
              }
            >
              <option value="">All report types</option>
              {["Spontaneous", "Clinical Trial", "Literature", "Other"].map(
                (v) => (
                  <option key={v}>{v}</option>
                ),
              )}
            </select>
          </label>
          <label>
            Suspect product contains
            <input
              maxLength={128}
              value={criteria.product}
              onChange={(e) =>
                setCriteria((v) => ({ ...v, product: e.target.value }))
              }
            />
          </label>
          <label>
            Reaction wording / PT contains
            <input
              maxLength={200}
              value={criteria.reaction}
              onChange={(e) =>
                setCriteria((v) => ({ ...v, reaction: e.target.value }))
              }
            />
          </label>
          <label>
            Seriousness
            <select
              aria-label="Series seriousness"
              value={criteria.seriousness}
              onChange={(e) =>
                setCriteria((v) => ({ ...v, seriousness: e.target.value }))
              }
            >
              <option value="ALL">All cases</option>
              <option value="SERIOUS">Serious cases</option>
              <option value="NON_SERIOUS">Non-serious cases</option>
            </select>
          </label>
          <label className="pv-inline-check">
            <input
              type="checkbox"
              checked={criteria.includeSynthetic}
              onChange={(e) =>
                setCriteria((v) => ({
                  ...v,
                  includeSynthetic: e.target.checked,
                }))
              }
            />
            Include labelled synthetic training cases
          </label>
          <p className="ws-full ws-muted">
            Training cases are excluded by default. No patient identifiers or
            reporter contact details are copied into the line listing.
          </p>
          <button disabled={busy}>Run reviewed-case query</button>
        </form>
      )}
      {preview && (
        <section className="ws-card">
          <h3>Query results · {preview.totalElements} cases</h3>
          <p>
            A live preview is not frozen. Capturing re-runs the saved criteria
            in one database snapshot; counts can change if source cases change.
          </p>
          <div className="ws-table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Case</th>
                  <th>Product</th>
                  <th>Reaction</th>
                  <th>State</th>
                  <th>Receipt date</th>
                </tr>
              </thead>
              <tbody>
                {preview.content.map((c) => (
                  <tr key={c.id}>
                    <td>
                      {c.caseNumber}
                      {c.syntheticRecord ? " · Training" : ""}
                    </td>
                    <td>{c.primaryProduct}</td>
                    <td>{c.primaryEvent}</td>
                    <td>{c.workflowState}</td>
                    <td>{c.initialReceiptDate}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="ws-actions">
            <button
              disabled={busy || previewPage === 0}
              onClick={() => applied && void search(applied, previewPage - 1)}
            >
              Previous results
            </button>
            <span>Page {previewPage + 1}</span>
            <button
              disabled={busy || previewPage + 1 >= preview.totalPages}
              onClick={() => applied && void search(applied, previewPage + 1)}
            >
              Next results
            </button>
          </div>
          <form className="ws-form" onSubmit={save} inert={busy || undefined}>
            <h3 className="ws-full">Freeze this case series</h3>
            <label>
              Case-series name
              <input
                required
                maxLength={160}
                value={name}
                onChange={(e) => setName(e.target.value)}
              />
            </label>
            <label>
              Population purpose
              <textarea
                required
                maxLength={3000}
                value={purpose}
                onChange={(e) => setPurpose(e.target.value)}
              />
            </label>
            <p className="ws-full">
              Capture up to 200 matching cases, with all revisions, criteria,
              category counts and a preserved PDF. Narrow larger populations; no
              silent truncation.
            </p>
            <button
              disabled={
                busy || !preview.totalElements || preview.totalElements > 200
              }
            >
              Freeze case series & generate PDF
            </button>
          </form>
        </section>
      )}
      <section className="ws-card">
        <div className="ws-section-heading">
          <h3>Saved case series</h3>
          <button disabled={busy} onClick={() => setRefresh((v) => v + 1)}>
            Refresh case series
          </button>
        </div>
        {!list.content.length && <p>No saved case series in this scope yet.</p>}
        <div className="ws-detail-cards">
          {list.content.map((s) => (
            <article key={s.id}>
              <h4>{s.name}</h4>
              <p>
                {s.caseCount} cases · {s.staffCode} · {dateTime(s.capturedAt)}
              </p>
              <button disabled={busy} onClick={() => void open(s.id)}>
                Open case series
              </button>
            </article>
          ))}
        </div>
        <div className="ws-actions">
          <button
            disabled={busy || page === 0}
            onClick={() => setPage((v) => v - 1)}
          >
            Previous series
          </button>
          <span>Page {page + 1}</span>
          <button
            disabled={busy || page + 1 >= list.totalPages}
            onClick={() => setPage((v) => v + 1)}
          >
            Next series
          </button>
        </div>
      </section>
      {selected && (
        <section className="ws-card" aria-label="Frozen case-series evidence">
          <h2>{selected.name}</h2>
          <p>{selected.purpose}</p>
          <p>
            {selected.caseCount} cases captured {dateTime(selected.capturedAt)}{" "}
            · {selected.staffCode}
          </p>
          <div className="pv-cohort-kpis">
            <div>
              <strong>{selected.caseCount}</strong>
              <span>Captured cases</span>
            </div>
            <div>
              <strong>{selected.snapshot.statistics.seriousCases}</strong>
              <span>Serious</span>
            </div>
            <div>
              <strong>{selected.snapshot.statistics.fatalCases}</strong>
              <span>Fatal</span>
            </div>
          </div>
          <div className="role-stat-grid">
            <InsightChart
              title="Case-series products"
              description="Distinct captured cases per suspect product."
              values={selected.snapshot.statistics.products}
            />
            <InsightChart
              title="Case-series reactions"
              description="Distinct captured cases per preferred term or verbatim wording."
              values={selected.snapshot.statistics.reactions}
            />
            <InsightChart
              title="Case-series outcomes"
              description="Distinct captured cases per outcome; categories may overlap."
              values={selected.snapshot.statistics.outcomes}
              donut
            />
          </div>
          <p>{selected.snapshot.statistics.interpretation}</p>
          <details>
            <summary>Saved query criteria</summary>
            <dl className="ws-detail-grid">
              {Object.entries(selected.criteria).map(([k, v]) => (
                <div key={k}>
                  <dt>{k}</dt>
                  <dd>
                    {v == null || v === "" ? "All / not specified" : String(v)}
                  </dd>
                </div>
              ))}
            </dl>
          </details>
          <div className="ws-table-wrap">
            <table>
              <caption>
                Frozen case line listing · captured values, not current case
                state
              </caption>
              <thead>
                <tr>
                  <th>Case / revision</th>
                  <th>Country / receipt</th>
                  <th>Suspect products</th>
                  <th>Reactions</th>
                  <th>Outcomes</th>
                  <th>Risk flags</th>
                </tr>
              </thead>
              <tbody>
                {selected.snapshot.caseListing.map((c) => (
                  <tr key={c.caseId}>
                    <td>
                      {c.caseNumber} · r{c.sourceRowVersion}
                      <small>
                        {c.workflowState}
                        {c.syntheticRecord ? " · Training" : ""}
                      </small>
                    </td>
                    <td>
                      {c.countryCode} · {c.initialReceiptDate}
                    </td>
                    <td>{c.products.join(", ") || "—"}</td>
                    <td>{c.reactions.join(", ") || "—"}</td>
                    <td>{c.outcomes.join(", ") || "—"}</td>
                    <td>
                      {c.fatal
                        ? "Fatal"
                        : c.serious
                          ? "Serious"
                          : "Non-serious"}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="ws-actions">
            <button disabled={busy} onClick={() => void pdf(selected.id)}>
              Download case-series PDF
            </button>
            {writer && (
              <button
                disabled={busy}
                onClick={() => {
                  if (
                    dirty &&
                    !window.confirm(
                      "Replace the unsaved query draft with the saved criteria?",
                    )
                  )
                    return;
                  const next = {
                    ...emptyCriteria(),
                    ...Object.fromEntries(
                      Object.entries(selected.criteria).map(([k, v]) => [
                        k,
                        v ?? "",
                      ]),
                    ),
                  } as ReturnType<typeof emptyCriteria>;
                  setCriteria(next);
                  setName((selected.name + " — refreshed").slice(0, 160));
                  setPurpose(selected.purpose);
                  void search(next, 0);
                }}
              >
                Re-run saved query
              </button>
            )}
            {writer && (
              <button
                disabled={busy}
                onClick={() => {
                  if (
                    dirty &&
                    !window.confirm("Leave this unsaved query draft?")
                  )
                    return;
                  onUse(selected);
                }}
              >
                Use case series in analysis
              </button>
            )}
          </div>
        </section>
      )}
    </section>
  );
}
