import {
  PvCaseSeriesWorkspace,
  type SeriesSummary,
} from "./PvCaseSeriesWorkspace";
import { PvAnalysisReviews } from "./PvAnalysisReviews";
import "./PvAnalysisWorkspace.css";
import { useCallback, useEffect, useRef, useState } from "react";
import { apiRequest, downloadPdf } from "../config/api";
import { useAuth } from "../context/AuthContext";
import { InsightChart } from "./RoleInsights";
import { dateTime, type Page } from "./model";
type Trends = {
  scope: string;
  generatedAt: string;
  reviewedCases: number;
  products: Record<string, number>;
  reactions: Record<string, number>;
  outcomes: Record<string, number>;
  interpretation: string;
};
type Analysis = {
  id: string;
  title: string;
  objective: string;
  findings: string;
  recommendation: string;
  caseNumber: string | null;
  analyst: string;
  staffCode: string;
  username: string;
  createdAt: string;
  sha256: string;
  seriesId?: string;
  seriesName?: string;
  supersedesId?: string;
  supersededById?: string;
  reviewStatus?: string;
  latestReviewId?: number;
};
const blank = () => ({
  title: "",
  objective: "",
  findings: "",
  recommendation: "",
  caseNumber: "",
  seriesId: "",
  supersedesId: "",
});
export function PvAnalysisWorkspace({
  dashboardOnly = false,
  employeeId,
  onDirtyChange,
}: {
  dashboardOnly?: boolean;
  employeeId?: number;
  onDirtyChange?: (dirty: boolean) => void;
}) {
  const { user } = useAuth();
  const writer = user?.role === "ROLE_PV_ANALYST";
  const [trends, setTrends] = useState<Trends | null>(null),
    [records, setRecords] = useState<Page<Analysis>>({
      content: [],
      totalElements: 0,
      totalPages: 0,
    }),
    [page, setPage] = useState(0),
    [refresh, setRefresh] = useState(0),
    [error, setError] = useState(""),
    [loadError, setLoadError] = useState(""),
    [notice, setNotice] = useState(""),
    [busy, setBusy] = useState(false),
    [loading, setLoading] = useState(true),
    [form, setForm] = useState(blank);
  const [tab, setTab] = useState<"reports" | "series">("reports"),
    [series, setSeries] = useState<Page<SeriesSummary>>({
      content: [],
      totalElements: 0,
      totalPages: 0,
    }),
    [seriesDirty, setSeriesDirty] = useState(false),
    [reviewDirty, setReviewDirty] = useState(false),
    [reviewId, setReviewId] = useState<string | null>(null),
    [summary, setSummary] = useState<Record<string, number> | null>(null);
  const reviewDraft = useRef(false);
  const changeReviewDirty = useCallback((v: boolean) => {
    reviewDraft.current = v;
    setReviewDirty(v);
  }, []);
  const pending = useRef(false),
    dirty = Object.values(form).some(Boolean) || seriesDirty || reviewDirty;
  const refreshReports = useCallback(() => setRefresh((v) => v + 1), []);
  useEffect(() => {
    const c = new AbortController();
    apiRequest<Record<string, number>>(
      `/api/pv-analyses/work-summary${employeeId ? "?userId=" + employeeId : ""}`,
      "GET",
      undefined,
      c.signal,
    )
      .then((d) => {
        if (!c.signal.aborted && typeof d.totalReports === "number")
          setSummary(d);
      })
      .catch(() => {});
    return () => c.abort();
  }, [refresh, employeeId]);
  useEffect(() => {
    if (dashboardOnly || !writer) return;
    const c = new AbortController();
    apiRequest<Page<SeriesSummary>>(
      "/api/pv-analyses/series?page=0",
      "GET",
      undefined,
      c.signal,
    )
      .then((d) => {
        if (!c.signal.aborted) setSeries(d);
      })
      .catch((e) => {
        if (!c.signal.aborted) setLoadError(e.message);
      });
    return () => c.abort();
  }, [refresh, dashboardOnly, writer]);
  function switchTab(next: "reports" | "series") {
    if (
      (seriesDirty || reviewDirty) &&
      !window.confirm("Leave unsaved query or review changes?")
    )
      return;
    setSeriesDirty(false);
    setReviewDirty(false);
    setReviewId(null);
    setTab(next);
  }

  useEffect(() => {
    onDirtyChange?.(dirty);
    const leave = (e: BeforeUnloadEvent) => {
      if (dirty) {
        e.preventDefault();
        e.returnValue = "";
      }
    };
    window.addEventListener("beforeunload", leave);
    return () => {
      onDirtyChange?.(false);
      window.removeEventListener("beforeunload", leave);
    };
  }, [dirty, onDirtyChange]);
  useEffect(() => {
    const c = new AbortController();
    setLoading(true);
    Promise.all([
      apiRequest<Trends>("/api/pv-analyses/trends", "GET", undefined, c.signal),
      dashboardOnly
        ? Promise.resolve({
            content: [],
            totalElements: 0,
            totalPages: 0,
          } as Page<Analysis>)
        : apiRequest<Page<Analysis>>(
            `/api/pv-analyses?page=${page}${employeeId ? "&userId=" + employeeId : ""}`,
            "GET",
            undefined,
            c.signal,
          ),
    ])
      .then(([t, r]) => {
        if (!c.signal.aborted) {
          setTrends(t);
          if (!reviewDraft.current) setRecords(r);
          setLoadError("");
        }
      })
      .catch((e) => {
        if (!c.signal.aborted) setLoadError(e.message);
      })
      .finally(() => {
        if (!c.signal.aborted) setLoading(false);
      });
    return () => c.abort();
  }, [refresh, page, employeeId, dashboardOnly]);
  useEffect(() => {
    const timer = setInterval(() => {
      if (
        document.visibilityState === "visible" &&
        !pending.current &&
        !reviewDraft.current
      )
        setRefresh((v) => v + 1);
    }, 15000);
    return () => clearInterval(timer);
  }, []);
  async function save(e: React.FormEvent) {
    e.preventDefault();
    if (pending.current) return;
    pending.current = true;
    setBusy(true);
    setError("");
    setNotice("");
    try {
      await apiRequest("/api/pv-analyses", "POST", form);
      setForm(blank());
      setPage(0);
      setRefresh((v) => v + 1);
      setNotice(
        "Analysis published. Its original PDF is available to you, the PV Analyst administration and the Safety Manager.",
      );
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not publish analysis");
    } finally {
      pending.current = false;
      setBusy(false);
    }
  }
  async function pdf(record: Analysis) {
    if (pending.current) return;
    pending.current = true;
    setBusy(true);
    setError("");
    try {
      await downloadPdf(
        `/api/pv-analyses/${record.id}/pdf`,
        `PV-analysis-${record.id}.pdf`,
      );
    } catch (e) {
      setError(e instanceof Error ? e.message : "PDF download failed");
    } finally {
      pending.current = false;
      setBusy(false);
    }
  }
  return (
    <section
      className="pv-analysis-workspace"
      data-validation-context="pv"
      aria-label="PV safety analytics"
    >
      <div className="ws-section-heading">
        <div>
          <h2>
            {dashboardOnly
              ? "Reviewed-case safety trends"
              : "PV analysis reports"}
          </h2>
          <p>
            Descriptive analysis of reviewed safety evidence. Updates every 15
            seconds while visible.
          </p>
        </div>
        <button
          disabled={busy || loading || reviewDirty}
          onClick={() => {
            setError("");
            setLoadError("");
            setRefresh((v) => v + 1);
          }}
        >
          Refresh PV analytics
        </button>
      </div>
      {(error || loadError) && (
        <p role="alert" className="ws-error">
          {error || loadError}
        </p>
      )}
      {notice && (
        <p role="status" className="ws-notice">
          {notice}
        </p>
      )}
      {loading && !trends && (
        <p role="status">Loading reviewed safety trends…</p>
      )}
      {summary && (
        <div className="pv-cohort-kpis" aria-label="Analyst work status">
          {[
            ["Published reports", "totalReports"],
            ["Awaiting review", "awaitingReview"],
            ["Returned for correction", "returned"],
            ["Follow-up requested", "followUpRequired"],
            ["Acknowledged", "acknowledged"],
          ].map(([label, key]) => (
            <div key={key}>
              <strong>{summary[key]}</strong>
              <span>{label}</span>
            </div>
          ))}
        </div>
      )}
      {!dashboardOnly && (
        <nav className="pv-work-tabs" aria-label="PV analyst work modules">
          <button
            aria-current={tab === "reports" ? "page" : undefined}
            onClick={() => switchTab("reports")}
          >
            Analysis reports & reviews
          </button>
          <button
            aria-current={tab === "series" ? "page" : undefined}
            onClick={() => switchTab("series")}
          >
            Case series & queries
          </button>
        </nav>
      )}
      {!dashboardOnly && tab === "series" && (
        <PvCaseSeriesWorkspace
          employeeId={employeeId}
          onDirtyChange={setSeriesDirty}
          onUse={(s) => {
            setForm((v) => ({ ...v, seriesId: s.id }));
            setSeriesDirty(false);
            setTab("reports");
            refreshReports();
            setNotice(
              "Frozen case series selected. Prepare your findings against this captured population.",
            );
          }}
        />
      )}
      {trends && (dashboardOnly || tab === "reports") && (
        <>
          <p className="ws-muted">
            {trends.scope} · {trends.reviewedCases} cases · Updated{" "}
            {dateTime(trends.generatedAt)}
          </p>
          <div className="role-stat-grid">
            <InsightChart
              title="Suspected products"
              description="Distinct reviewed cases per suspect product. Top 20 categories; one case may include several products."
              values={trends.products}
            />
            <InsightChart
              title="Reported reactions"
              description="Distinct reviewed cases by recorded reaction wording. Top 20 categories."
              values={trends.reactions}
            />
            <InsightChart
              title="Reaction outcomes"
              description="Distinct reviewed cases per recorded event outcome; outcome categories may overlap."
              values={trends.outcomes}
              donut
            />
          </div>
          <p className="ws-muted">{trends.interpretation}</p>
        </>
      )}
      {!dashboardOnly && tab === "reports" && (
        <>
          {writer && (
            <form
              className="ws-card ws-form"
              onSubmit={save}
              inert={busy || undefined}
            >
              <h2>Prepare a safety analysis</h2>
              <p className="ws-full">
                Publishing preserves the report and its evidence snapshot.
                Create a new report for later findings. Reports inform
                evaluation; they do not approve cases or establish a safety
                signal.
              </p>
              {(
                [
                  "title",
                  "objective",
                  "findings",
                  "recommendation",
                  "caseNumber",
                ] as const
              ).map((key) => (
                <label
                  key={key}
                  className={
                    key === "title" || key === "caseNumber" ? "" : "ws-full"
                  }
                >
                  {
                    {
                      title: "Analysis title",
                      objective: "Analysis objective",
                      findings: "Observed findings",
                      recommendation: "Recommendation for Safety Manager",
                      caseNumber: "Reviewed case number (optional)",
                    }[key]
                  }
                  {key === "title" || key === "caseNumber" ? (
                    <input
                      name={key}
                      required={key === "title"}
                      maxLength={key === "title" ? 160 : 64}
                      value={form[key]}
                      onChange={(e) =>
                        setForm((v) => ({ ...v, [key]: e.target.value }))
                      }
                    />
                  ) : (
                    <textarea
                      name={key}
                      required
                      maxLength={key === "findings" ? 5000 : 3000}
                      value={form[key]}
                      onChange={(e) =>
                        setForm((v) => ({ ...v, [key]: e.target.value }))
                      }
                    />
                  )}
                </label>
              ))}
              <label className="ws-full">
                Frozen case series (optional)
                <select
                  aria-label="Analysis case series"
                  value={form.seriesId}
                  onChange={(e) =>
                    setForm((v) => ({ ...v, seriesId: e.target.value }))
                  }
                >
                  <option value="">
                    Global reviewed-case trends / single case
                  </option>
                  {form.seriesId &&
                    !series.content.some((s) => s.id === form.seriesId) && (
                      <option value={form.seriesId}>
                        Selected frozen case series · {form.seriesId}
                      </option>
                    )}
                  {series.content.map((s) => (
                    <option key={s.id} value={s.id}>
                      {s.name} · {s.caseCount} captured cases
                    </option>
                  ))}
                </select>
                <small>
                  Use Case series & queries to freeze a defined population.
                  Showing your latest 20; open an older series there to use it.
                </small>
              </label>
              {form.supersedesId && (
                <p className="ws-full">
                  New replacement for report {form.supersedesId}. The original
                  PDF and reviews remain preserved.
                  <button
                    type="button"
                    onClick={() => setForm((v) => ({ ...v, supersedesId: "" }))}
                  >
                    Start as a separate analysis
                  </button>
                </p>
              )}
              <button disabled={busy}>Publish analysis & generate PDF</button>
            </form>
          )}
          <section className="ws-card">
            <div className="ws-section-heading">
              <h2>
                {writer ? "My published analyses" : "Published analyst reports"}
              </h2>
              <span>{records.totalElements} records</span>
            </div>
            {!loading && !records.content.length && (
              <p>No analysis reports in this scope yet.</p>
            )}
            <div className="ws-detail-cards">
              {records.content.map((r) => (
                <article key={r.id}>
                  <h3>{r.title}</h3>
                  <span className="ws-badge">
                    {r.supersededById
                      ? "SUPERSEDED"
                      : (r.reviewStatus || "AWAITING_REVIEW").replaceAll(
                          "_",
                          " ",
                        )}
                  </span>
                  {r.seriesName && <p>Frozen case series: {r.seriesName}</p>}
                  {r.supersedesId && <p>Replaces report {r.supersedesId}</p>}
                  <p>
                    {r.analyst || r.username} · {r.staffCode} ·{" "}
                    {dateTime(r.createdAt)}
                  </p>
                  {r.caseNumber && <p>Linked reviewed case: {r.caseNumber}</p>}
                  <details>
                    <summary>Read analysis</summary>
                    {[
                      ["Objective", r.objective],
                      ["Findings", r.findings],
                      ["Recommendation", r.recommendation],
                    ].map(([label, value]) => (
                      <div key={label}>
                        <h4>{label}</h4>
                        <p className="ws-prose">{value}</p>
                      </div>
                    ))}
                    <small style={{ overflowWrap: "anywhere" }}>
                      SHA-256: {r.sha256}
                    </small>
                  </details>
                  <div className="ws-actions">
                    <button disabled={busy} onClick={() => void pdf(r)}>
                      Download analysis PDF
                    </button>
                    <button
                      disabled={busy}
                      onClick={() => {
                        if (
                          reviewDirty &&
                          !window.confirm("Leave unsaved review comments?")
                        )
                          return;
                        setReviewDirty(false);
                        setReviewId((v) => (v === r.id ? null : r.id));
                      }}
                    >
                      Review history & response
                    </button>
                    {writer &&
                      !r.supersededById &&
                      ["RETURNED", "FOLLOW_UP_REQUIRED"].includes(
                        r.reviewStatus || "",
                      ) && (
                        <button
                          disabled={busy}
                          onClick={() => {
                            if (
                              Object.values(form).some(Boolean) &&
                              !window.confirm(
                                "Replace your unsaved analysis draft?",
                              )
                            )
                              return;
                            setForm({
                              title: (r.title + " — follow-up").slice(0, 160),
                              objective: r.objective,
                              findings: "",
                              recommendation: "",
                              caseNumber: "",
                              seriesId: r.seriesId || "",
                              supersedesId: r.id,
                            });
                            setNotice(
                              "Prepare a new report addressing the manager response. The previous report remains unchanged.",
                            );
                          }}
                        >
                          Prepare revised analysis
                        </button>
                      )}
                  </div>
                  {reviewId === r.id && (
                    <PvAnalysisReviews
                      analysisId={r.id}
                      superseded={!!r.supersededById}
                      onSaved={refreshReports}
                      onDirtyChange={changeReviewDirty}
                    />
                  )}
                </article>
              ))}
            </div>
            <div className="ws-actions">
              <button
                disabled={!page || busy || reviewDirty}
                onClick={() => setPage((v) => v - 1)}
              >
                Previous analyses
              </button>
              <span>Page {page + 1}</span>
              <button
                disabled={page + 1 >= records.totalPages || busy || reviewDirty}
                onClick={() => setPage((v) => v + 1)}
              >
                Next analyses
              </button>
            </div>
          </section>
        </>
      )}
    </section>
  );
}
