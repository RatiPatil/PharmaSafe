import { useEffect, useState } from "react";
import { BarChart3, Download, Filter, RotateCcw } from "lucide-react";
import { apiRequest, downloadPdf } from "../config/api";
import { dateTime } from "../workspaces/model";
import { statuses, emptyPage } from "./types";
import type { College, Domain, Page, Report, Stats, Task } from "./types";
import "./EducationReports.css";
type Choice = {
  studentCode: string;
  fullName: string;
  collegeId: number;
  collegeName: string;
};
type Scope = {
  colleges: College[];
  domains: Domain[];
  collegeId: number;
  students: Page<Choice>;
};
type Selection = {
  collegeId: string;
  studentCode: string;
  domains: string[];
  status: string;
};
type Preview = { summary: Stats; records: Page<Task>; domains: string[] };
export function EducationReports({
  collegeOnly,
  superAdmin = false,
  initialDomain = "",
  initialCollege = "",
  initialStudent = "",
  initialStatus = "",
}: {
  collegeOnly: boolean;
  superAdmin?: boolean;
  initialDomain?: string;
  initialCollege?: string;
  initialStudent?: string;
  initialStatus?: string;
}) {
  const initial = (): Selection => ({
    collegeId: initialCollege,
    studentCode: initialStudent,
    domains: initialDomain ? [initialDomain] : [],
    status: initialStatus,
  });
  const [draft, setDraft] = useState<Selection>(initial),
    [applied, setApplied] = useState<Selection>(initial),
    [scope, setScope] = useState<Scope | null>(null),
    [choices, setChoices] = useState<Choice[]>([]),
    [studentSearch, setStudentSearch] = useState(""),
    [search, setSearch] = useState(""),
    [optionPage, setOptionPage] = useState(0),
    [optionPages, setOptionPages] = useState(0),
    [optionBusy, setOptionBusy] = useState(true),
    [optionError, setOptionError] = useState(""),
    [optionAttempt, setOptionAttempt] = useState(0),
    [preview, setPreview] = useState<Preview | null>(null),
    [history, setHistory] = useState<Page<Report>>(emptyPage),
    [page, setPage] = useState(0),
    [historyPage, setHistoryPage] = useState(0),
    [refresh, setRefresh] = useState(0),
    [busy, setBusy] = useState(true),
    [saving, setSaving] = useState(false),
    [error, setError] = useState(""),
    [notice, setNotice] = useState("");
  const query = (s: Selection) => {
    const p = new URLSearchParams();
    if (s.collegeId) p.set("collegeId", s.collegeId);
    if (s.studentCode) p.set("studentCode", s.studentCode);
    if (s.status) p.set("status", s.status);
    s.domains.forEach((d) => p.append("domains", d));
    return p;
  };
  const changed = JSON.stringify(draft) !== JSON.stringify(applied);
  useEffect(() => {
    const c = new AbortController();
    const timer = setTimeout(() => {
      setOptionBusy(true);
      setOptionError("");
      const p = new URLSearchParams({ search, page: String(optionPage) });
      if (draft.collegeId) p.set("collegeId", draft.collegeId);
      apiRequest<Scope>(
        "/api/education/report-options?" + p,
        "GET",
        undefined,
        c.signal,
      )
        .then((v) => {
          if (c.signal.aborted) return;
          setScope(v);
          setChoices((old) =>
            optionPage ? [...old, ...v.students.content] : v.students.content,
          );
          setOptionPages(v.students.totalPages);
        })
        .catch((e) => {
          if (!c.signal.aborted) {
            setOptionError(e.message);
            setChoices([]);
          }
        })
        .finally(() => {
          if (!c.signal.aborted) setOptionBusy(false);
        });
    }, 150);
    return () => {
      clearTimeout(timer);
      c.abort();
    };
  }, [draft.collegeId, search, optionPage, optionAttempt]);
  useEffect(() => {
    const c = new AbortController();
    setBusy(true);
    setError("");
    const p = query(applied);
    Promise.all([
      apiRequest<Preview>(
        "/api/education/report-preview?" + p + "&page=" + page,
        "GET",
        undefined,
        c.signal,
      ),
      apiRequest<Page<Report>>(
        "/api/education/reports?" + p + "&page=" + historyPage,
        "GET",
        undefined,
        c.signal,
      ),
    ])
      .then(([v, h]) => {
        if (!c.signal.aborted) {
          setPreview(v);
          setHistory(h);
        }
      })
      .catch((e) => {
        if (!c.signal.aborted) {
          setPreview(null);
          setHistory(emptyPage());
          setError(e.message);
        }
      })
      .finally(() => {
        if (!c.signal.aborted) setBusy(false);
      });
    return () => c.abort();
  }, [applied, page, historyPage, refresh]);
  const name = (d: string) =>
    scope?.domains.find((v) => v.code === d)?.name || d;
  const selectedChoice = choices.find(
    (s) => s.studentCode === draft.studentCode,
  );
  function reset() {
    const s = { collegeId: "", studentCode: "", domains: [], status: "" };
    setDraft(s);
    setApplied(s);
    setSearch("");
    setStudentSearch("");
    setOptionPage(0);
    setChoices([]);
    setPage(0);
    setHistoryPage(0);
    setNotice("");
  }
  return (
    <div className="edu-report-center">
      <section
        className="ws-card edu-report-filters"
        aria-label="Advanced report filters"
      >
        <div className="edu-report-title">
          <div>
            <p className="ws-eyebrow">REPORTING & INSIGHTS</p>
            <h2>
              {collegeOnly
                ? "College progress reports"
                : superAdmin
                  ? "Super Admin reports across all colleges"
                  : "College-wise progress reports"}
            </h2>
            <p>
              Combine student and domain filters to inspect current assignments
              and preserve a progress PDF.
            </p>
          </div>
          <BarChart3 size={30} />
        </div>
        {optionError && (
          <div role="alert" className="ws-error">
            {optionError}
            <button onClick={() => setOptionAttempt((v) => v + 1)}>
              Retry report options
            </button>
          </div>
        )}
        <form
          aria-label="Advanced report filters"
          className="ws-form"
          onSubmit={(e) => {
            e.preventDefault();
            setApplied({ ...draft, domains: [...draft.domains] });
            setPage(0);
            setHistoryPage(0);
            setNotice("");
          }}
        >
          {!collegeOnly && (
            <label>
              College
              <select
                aria-label="Report college"
                value={draft.collegeId}
                disabled={!scope}
                onChange={(e) => {
                  setDraft((v) => ({
                    ...v,
                    collegeId: e.target.value,
                    studentCode: "",
                  }));
                  setChoices([]);
                  setOptionPage(0);
                  setSearch("");
                  setStudentSearch("");
                }}
              >
                <option value="">All authorized colleges</option>
                {scope?.colleges.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name}
                    {c.active ? "" : " (inactive)"}
                  </option>
                ))}
              </select>
            </label>
          )}
          {collegeOnly && (
            <div className="edu-report-college">
              <span>College scope</span>
              <strong>
                {scope?.colleges[0]?.name || "Loading your college…"}
              </strong>
              <small>Reports stay within your college.</small>
            </div>
          )}
          <label>
            Find a student
            <input
              aria-label="Search report students"
              type="search"
              maxLength={100}
              value={studentSearch}
              placeholder="Name or Student ID"
              onChange={(e) => {
                setStudentSearch(e.target.value);
                setSearch(e.target.value);
                setOptionPage(0);
              }}
            />
          </label>
          <label>
            Student
            <select
              aria-label="Report student"
              disabled={optionBusy || !!optionError}
              value={draft.studentCode}
              onChange={(e) =>
                setDraft((v) => ({ ...v, studentCode: e.target.value }))
              }
            >
              <option value="">
                All students
                {draft.collegeId ? " in selected college" : " in scope"}
              </option>
              {draft.studentCode && !selectedChoice && (
                <option value={draft.studentCode}>{draft.studentCode}</option>
              )}
              {choices.map((s) => (
                <option key={s.studentCode} value={s.studentCode}>
                  {s.fullName} · {s.studentCode}
                  {!collegeOnly && !draft.collegeId
                    ? " · " + s.collegeName
                    : ""}
                </option>
              ))}
            </select>
          </label>
          <label>
            Work status
            <select
              aria-label="Report work status"
              value={draft.status}
              onChange={(e) =>
                setDraft((v) => ({ ...v, status: e.target.value }))
              }
            >
              <option value="">All work statuses</option>
              <option value="PENDING">All unfinished work</option>
              {Object.entries(statuses).map(([v, label]) => (
                <option key={v} value={v}>
                  {label}
                </option>
              ))}
            </select>
          </label>
          <fieldset className="edu-report-domains">
            <legend>Learning domains</legend>
            <label className="edu-report-check">
              <input
                type="checkbox"
                checked={draft.domains.length === 0}
                onChange={(e) =>
                  setDraft((v) => ({
                    ...v,
                    domains: e.target.checked
                      ? []
                      : scope?.domains[0]
                        ? [scope.domains[0].code]
                        : [],
                  }))
                }
              />
              All authorized domains
            </label>
            <div>
              {scope?.domains.map((d) => (
                <label className="edu-report-check" key={d.code}>
                  <input
                    type="checkbox"
                    aria-label={"Report domain: " + d.name}
                    checked={draft.domains.includes(d.code)}
                    onChange={(e) =>
                      setDraft((v) => ({
                        ...v,
                        domains: e.target.checked
                          ? [...v.domains, d.code]
                          : v.domains.filter((x) => x !== d.code),
                      }))
                    }
                  />
                  {d.name}
                </label>
              ))}
            </div>
            <small>
              Leave All authorized domains selected for a complete domain
              overview, or choose one or more domains.
            </small>
          </fieldset>
          <div className="edu-report-filter-actions">
            <button className="ws-primary" disabled={!scope || busy}>
              <Filter size={15} />
              Apply Filters
            </button>
            <button type="button" onClick={reset}>
              <RotateCcw size={15} />
              Reset Filters
            </button>
            {changed && (
              <span role="status">Filter changes are not applied yet.</span>
            )}
          </div>
        </form>
        <div className="edu-report-option-status">
          {optionBusy ? (
            <span role="status">Loading student options…</span>
          ) : !choices.length && !optionError ? (
            <span>No students match this search.</span>
          ) : null}
          {optionPage + 1 < optionPages && (
            <button
              disabled={optionBusy}
              onClick={() => setOptionPage((p) => p + 1)}
            >
              Load more students
            </button>
          )}
        </div>
      </section>
      {error && (
        <div className="ws-error" role="alert">
          {error}
          <button onClick={() => setRefresh((v) => v + 1)}>
            Retry reports
          </button>
        </div>
      )}
      {notice && (
        <p className="ws-notice" role="status">
          {notice}
        </p>
      )}
      {busy ? (
        <section className="ws-card" role="status">
          Loading report results…
        </section>
      ) : (
        preview && (
          <>
            <section className="edu-report-summary" aria-label="Report summary">
              {[
                ["Matching assignments", preview.summary.total],
                ["Students with matching work", preview.summary.students || 0],
                ["Completed", preview.summary.statusCounts.COMPLETED || 0],
                ["Average progress", preview.summary.averageProgress + "%"],
              ].map(([label, value]) => (
                <div className="ws-card" key={label}>
                  <span>{label}</span>
                  <strong>{value}</strong>
                </div>
              ))}
            </section>
            <section className="ws-card">
              <div className="edu-report-title">
                <div>
                  <h2>Detailed report records</h2>
                  <p>
                    {applied.collegeId
                      ? scope?.colleges.find(
                          (c) => String(c.id) === applied.collegeId,
                        )?.name || "Selected college"
                      : collegeOnly
                        ? scope?.colleges[0]?.name || "Your college"
                        : "All authorized colleges"}{" "}
                    · {applied.studentCode || "All students"} ·{" "}
                    {applied.domains.length
                      ? applied.domains.map(name).join(", ")
                      : "All authorized domains"}{" "}
                    ·{" "}
                    {applied.status
                      ? statuses[applied.status] || "All unfinished work"
                      : "All statuses"}
                  </p>
                </div>
                <button
                  className="ws-primary"
                  disabled={saving || changed}
                  onClick={async () => {
                    setSaving(true);
                    setError("");
                    try {
                      const result = await apiRequest<Report>(
                        "/api/education/reports",
                        "POST",
                        {
                          collegeId: applied.collegeId
                            ? Number(applied.collegeId)
                            : null,
                          studentCode: applied.studentCode || null,
                          domains: applied.domains.length
                            ? applied.domains
                            : null,
                          status: applied.status || null,
                        },
                      );
                      setNotice(
                        "Progress PDF saved. Download it from PDF history below.",
                      );
                      setHistoryPage(0);
                      setRefresh((v) => v + 1);
                      await downloadPdf(
                        "/api/education/reports/" + result.id + "/pdf",
                        result.fileName,
                      );
                    } catch (e) {
                      setError(
                        e instanceof Error
                          ? e.message
                          : "Could not generate the report.",
                      );
                    } finally {
                      setSaving(false);
                    }
                  }}
                >
                  <Download size={16} />
                  {saving ? "Preparing PDF…" : "Generate progress PDF"}
                </button>
              </div>
              <p className="edu-report-help">
                PDFs preserve the applied filters and all matching records, up
                to 200 assignments per export. Narrow the filters for larger
                populations.
              </p>
              {!preview.records.content.length ? (
                <div className="edu-empty-state">
                  <h3>No matching assignments</h3>
                  <p>
                    Try another college, student or domain selection. Students
                    without assigned work have no progress records yet.
                  </p>
                </div>
              ) : (
                <div className="ws-table-wrap">
                  <table>
                    <thead>
                      <tr>
                        <th>Student / college</th>
                        <th>Domain</th>
                        <th>Assignment / stage</th>
                        <th>Status</th>
                        <th>Progress</th>
                        <th>Due</th>
                        <th>Student report</th>
                      </tr>
                    </thead>
                    <tbody>
                      {preview.records.content.map((t) => (
                        <tr key={t.id}>
                          <td>
                            {t.studentName}
                            <small>
                              {t.studentCode} · {t.collegeName}
                            </small>
                          </td>
                          <td>{name(t.domain)}</td>
                          <td>
                            {t.title}
                            <small>{t.stage}</small>
                          </td>
                          <td>{statuses[t.status]}</td>
                          <td>{t.progress}%</td>
                          <td>{dateTime(t.dueAt)}</td>
                          <td>
                            <button
                              onClick={() => {
                                const s = {
                                  ...applied,
                                  collegeId: String(t.collegeId),
                                  studentCode: t.studentCode,
                                };
                                setDraft(s);
                                setApplied(s);
                                setPage(0);
                                setHistoryPage(0);
                                setOptionPage(0);
                                setSearch("");
                                setStudentSearch("");
                              }}
                            >
                              View student report
                            </button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
              <div className="edu-pager">
                <button disabled={!page} onClick={() => setPage((p) => p - 1)}>
                  Previous records
                </button>
                <span>
                  Page {page + 1} · {preview.records.totalElements} records
                </span>
                <button
                  disabled={page + 1 >= preview.records.totalPages}
                  onClick={() => setPage((p) => p + 1)}
                >
                  Next records
                </button>
              </div>
            </section>
            <section className="ws-card">
              <h2>Progress reports & PDF history</h2>
              <p>
                Preserved exports within the applied college, student and domain
                scope. A PDF’s contents stay fixed after generation.
              </p>
              {!history.content.length ? (
                <p>No preserved reports match these filters.</p>
              ) : (
                <div className="ws-table-wrap">
                  <table>
                    <thead>
                      <tr>
                        <th>Domains / student</th>
                        <th>Generated</th>
                        <th>PDF</th>
                      </tr>
                    </thead>
                    <tbody>
                      {history.content.map((r) => (
                        <tr key={r.id}>
                          <td>
                            {(r.domains || [r.domain])
                              .filter((d): d is string => !!d)
                              .map(name)
                              .join(", ")}
                            <small>
                              {r.collegeName || "All authorized colleges"} ·{" "}
                              {r.studentCode || "All scoped students"} ·{" "}
                              {r.status
                                ? statuses[r.status] || "All unfinished work"
                                : "All statuses"}
                            </small>
                          </td>
                          <td>
                            {dateTime(r.createdAt)}
                            <small>{r.generatedBy}</small>
                          </td>
                          <td>
                            <button
                              disabled={saving}
                              onClick={async () => {
                                setSaving(true);
                                setError("");
                                try {
                                  await downloadPdf(
                                    "/api/education/reports/" + r.id + "/pdf",
                                    r.fileName,
                                  );
                                } catch (e) {
                                  setError(
                                    e instanceof Error
                                      ? e.message
                                      : "Download failed. Please try again.",
                                  );
                                } finally {
                                  setSaving(false);
                                }
                              }}
                            >
                              Download progress PDF
                            </button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
              <div className="edu-pager">
                <button
                  disabled={!historyPage}
                  onClick={() => setHistoryPage((p) => p - 1)}
                >
                  Previous PDFs
                </button>
                <span>
                  Page {historyPage + 1} · {history.totalElements} exports
                </span>
                <button
                  disabled={historyPage + 1 >= history.totalPages}
                  onClick={() => setHistoryPage((p) => p + 1)}
                >
                  Next PDFs
                </button>
              </div>
            </section>
          </>
        )
      )}
    </div>
  );
}
