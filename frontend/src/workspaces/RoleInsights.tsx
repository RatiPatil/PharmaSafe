import { useEffect, useState } from "react";
import { apiRequest } from "../config/api";
import type { Page, Row } from "./model";
import "./RoleInsights.css";
const palette = [
  "#5688dc",
  "#36a68e",
  "#9a73d7",
  "#e2ad50",
  "#d97b99",
  "#6e86ac",
  "#7cb7c4",
];
function colour(key: string) {
  const fixed: Record<string, string> = {
    Approved: "#36a68e",
    APPROVED: "#36a68e",
    Submitted: "#36a68e",
    Pending: "#e2ad50",
    PENDING: "#e2ad50",
    Rejected: "#d97b99",
    REJECTED: "#d97b99",
    Suspended: "#9a73d7",
    SUSPENDED: "#9a73d7",
    "Data Entry": "#5688dc",
    "Medical Review": "#9a73d7",
    "Quality Review": "#36a68e",
    Draft: "#7d91aa",
    Closed: "#398b83",
    Locked: "#617cba",
    Routine: "#5688dc",
    Priority: "#e2ad50",
    Expedited: "#d97b99",
    Overdue: "#d97b99",
  };
  return (
    fixed[key] ||
    palette[
      Array.from(key).reduce((n, c) => (n * 31 + c.charCodeAt(0)) >>> 0, 0) %
        palette.length
    ]
  );
}
type Analytics = {
  totalCases: number;
  totalReports: number;
  totalActivity: number;
  caseStates: Record<string, number>;
  casePriorities: Record<string, number>;
  reportStates: Record<string, number>;
  activity: Record<string, number>;
};
export function InsightChart({
  title,
  description,
  values,
  donut = false,
}: {
  title: string;
  description: string;
  values: Record<string, number>;
  donut?: boolean;
}) {
  const [selected, setSelected] = useState("");
  const active = Object.hasOwn(values, selected) ? selected : "";
  const entries = Object.entries(values)
    .filter(([, n]) => n > 0)
    .sort((a, b) => b[1] - a[1]);
  const total = entries.reduce((a, [, n]) => a + n, 0),
    peak = Math.max(1, ...entries.map(([, n]) => n));
  let offset = 0;
  return (
    <section className="ws-card insight-chart">
      <h3>{title}</h3>
      <p>{description}</p>
      {!total ? (
        <div className="insight-empty">No records in this category yet.</div>
      ) : donut ? (
        <div className="insight-donut-layout">
          <div className="insight-donut">
            <svg
              viewBox="0 0 160 160"
              role="img"
              aria-label={`${title}: ${entries.map(([k, n]) => `${k} ${n}`).join(", ")}`}
            >
              <circle
                cx="80"
                cy="80"
                r="58"
                fill="none"
                stroke="#edf2f8"
                strokeWidth="19"
              />
              {entries.map(([key, n]) => {
                const start = offset,
                  length = (n / total) * 364.425;
                offset += length;
                return (
                  <circle
                    key={key}
                    cx="80"
                    cy="80"
                    r="58"
                    fill="none"
                    stroke={colour(key)}
                    strokeWidth={selected === key ? 23 : 19}
                    strokeDasharray={`${length} ${364.425 - length}`}
                    strokeDashoffset={-start}
                    transform="rotate(-90 80 80)"
                    opacity={active && active !== key ? 0.35 : 1}
                  >
                    <title>
                      {key}: {n}
                    </title>
                  </circle>
                );
              })}
            </svg>
            <div>
              <strong>{active ? values[active] : total}</strong>
              <span>{active || "Records"}</span>
            </div>
          </div>
          <div className="insight-legend">
            {entries.map(([key, n]) => (
              <button
                key={key}
                type="button"
                aria-label={`${key} ${n}`}
                aria-pressed={selected === key}
                onClick={() => setSelected(selected === key ? "" : key)}
              >
                <i style={{ background: colour(key) }} />
                <span>{key.replaceAll("_", " ")}</span>
                <strong>{n}</strong>
              </button>
            ))}
          </div>
        </div>
      ) : (
        <div
          className="insight-bars"
          role="img"
          aria-label={`${title}: ${entries.map(([k, n]) => `${k} ${n}`).join(", ")}`}
        >
          {entries.slice(0, 6).map(([key, n]) => (
            <div key={key}>
              <span>{key.replaceAll("_", " ")}</span>
              <i>
                <b
                  style={{
                    width: `${(n / peak) * 100}%`,
                    background: colour(key),
                  }}
                />
              </i>
              <strong>{n}</strong>
            </div>
          ))}
          {entries.length > 6 && <small>Top six categories by count.</small>}
        </div>
      )}
    </section>
  );
}
export function RoleInsights({
  role,
  userId,
  people,
  refresh = 0,
}: {
  role: string;
  userId?: number;
  people?: Record<string, number>;
  refresh?: number;
}) {
  const [data, setData] = useState<Analytics | null>(null),
    [error, setError] = useState(""),
    [retry, setRetry] = useState(0);
  useEffect(() => {
    const c = new AbortController();
    setData(null);
    setError("");
    const params = new URLSearchParams({
      role,
      ...(userId ? { userId: String(userId) } : {}),
    });
    apiRequest<Analytics>(
      `/api/administration/analytics?${params}`,
      "GET",
      undefined,
      c.signal,
    )
      .then((value) => {
        if (c.signal.aborted) return;
        if (
          !value?.caseStates ||
          !value.reportStates ||
          !value.casePriorities ||
          !value.activity
        )
          throw new Error(
            "Work statistics are unavailable. Retry the dashboard.",
          );
        setData(value);
      })
      .catch((e) => {
        if (!c.signal.aborted) setError(e.message);
      });
    return () => c.abort();
  }, [role, userId, refresh, retry]);
  if (error)
    return (
      <p className="ws-error" role="alert">
        {error}
        <button onClick={() => setRetry((v) => v + 1)}>
          Retry dashboard charts
        </button>
      </p>
    );
  if (!data) return <p role="status">Loading work statistics…</p>;
  return (
    <section
      className="role-insights"
      aria-label={userId ? "Employee work analytics" : "Role work analytics"}
    >
      <div className="insight-kpis">
        {[
          ["Case work records", data.totalCases],
          ["Report work records", data.totalReports],
          ["Recorded activities", data.totalActivity],
        ].map(([label, n], i) => (
          <article key={label} style={{ borderTopColor: palette[i] }}>
            <span>{label}</span>
            <strong>{n}</strong>
          </article>
        ))}
      </div>
      <div className="insight-grid">
        {people && (
          <InsightChart
            title="Employee account status"
            description="Current account states in the selected role."
            values={people}
            donut
          />
        )}
        <InsightChart
          title="Case workflow"
          description="Cases created, assigned or reviewed by these employees."
          values={data.caseStates}
          donut
        />
        <InsightChart
          title="Report progress"
          description="Internal report records authored by these employees."
          values={data.reportStates}
          donut
        />
        <InsightChart
          title="Case priorities"
          description="Priority distribution across the same case work records."
          values={data.casePriorities}
        />
        <InsightChart
          title="Recorded employee activity"
          description="Recorded actions, including sign-ins; these counts are not productivity scores."
          values={data.activity}
        />
      </div>
    </section>
  );
}
export function RoleOverviewPreview({
  role,
  reporting,
  refresh,
  onEmployee,
  onUsers,
  onWork,
  onRequests,
}: {
  role: string;
  reporting: boolean;
  refresh: number;
  onEmployee: (row: Row) => void;
  onUsers: () => void;
  onWork: () => void;
  onRequests: () => void;
}) {
  const [users, setUsers] = useState<Page<Row> | null>(null),
    [work, setWork] = useState<Page<Row> | null>(null),
    [error, setError] = useState(""),
    [retry, setRetry] = useState(0);
  useEffect(() => {
    const c = new AbortController();
    setUsers(null);
    setWork(null);
    setError("");
    Promise.all([
      apiRequest<Page<Row>>(
        `/api/administration/users?role=${encodeURIComponent(role)}&size=5`,
        "GET",
        undefined,
        c.signal,
      ),
      apiRequest<Page<Row>>(
        `/api/administration/${reporting ? "report-work" : "work"}?role=${encodeURIComponent(role)}&size=5`,
        "GET",
        undefined,
        c.signal,
      ),
    ])
      .then(([employees, records]) => {
        if (c.signal.aborted) return;
        setUsers(employees);
        setWork(records);
      })
      .catch((e) => {
        if (!c.signal.aborted) setError(e.message);
      });
    return () => c.abort();
  }, [role, reporting, refresh, retry]);
  if (error)
    return (
      <p role="alert" className="ws-error">
        {error}
        <button onClick={() => setRetry((v) => v + 1)}>
          Retry role preview
        </button>
      </p>
    );
  return (
    <section className="insight-preview-grid">
      <article className="ws-card">
        <div className="ws-section-heading">
          <h3>Employees in this role</h3>
          <button onClick={onUsers}>Manage employees</button>
        </div>
        {error && <p role="alert">{error}</p>}
        {!users ? (
          <p role="status">Loading employees…</p>
        ) : (
          <>
            <p className="insight-caption">
              {users.totalElements} employee accounts · showing up to five
            </p>
            <div className="insight-employees">
              {users.content.map((row) => (
                <button
                  key={String(row.userId)}
                  onClick={() => onEmployee(row)}
                >
                  <span>
                    <strong>{String(row.fullName || row.username)}</strong>
                    <small>
                      {String(row.staffCode)} · @{String(row.username)}
                    </small>
                  </span>
                  <span className="ws-badge">{String(row.accountStatus)}</span>
                </button>
              ))}
              {!users.content.length && (
                <p>No employees registered in this role yet.</p>
              )}
            </div>
          </>
        )}
        <button className="insight-link" onClick={onRequests}>
          Review registration requests →
        </button>
      </article>
      <article className="ws-card">
        <div className="ws-section-heading">
          <h3>{reporting ? "Reporting team work" : "Employee case work"}</h3>
          <button onClick={onWork}>View all work</button>
        </div>
        {!work ? (
          <p role="status">Loading work records…</p>
        ) : (
          <>
            <p className="insight-caption">
              {work.totalElements} work records · showing up to five
            </p>
            <div className="insight-work">
              {work.content.map((row) => (
                <div key={String(row.id)}>
                  <strong>{String(row.caseNumber || row.reportId)}</strong>
                  <span>{String(row.workflowState || row.status)}</span>
                </div>
              ))}
              {!work.content.length && <p>No work records in this role yet.</p>}
            </div>
          </>
        )}
      </article>
    </section>
  );
}
