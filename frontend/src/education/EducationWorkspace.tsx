import { useCallback, useEffect, useRef, useState } from "react";
import {
  GraduationCap,
  Building2,
  ClipboardList,
  Stethoscope,
  BadgeCheck,
  Users,
  FileCheck,
  Search,
  ShieldCheck,
  ArrowUpRight,
  LogOut,
  Menu,
  RefreshCw,
  LockKeyhole,
  BookOpenCheck,
} from "lucide-react";
import { useAuth } from "../context/AuthContext";
import { apiRequest, downloadPdf } from "../config/api";
import { AccessDialog } from "../workspaces/AccessDialog";
import { dateTime } from "../workspaces/model";
import { GrantStudentAccess } from "./GrantStudentAccess";
import { EducationTask } from "./EducationTask";
import {
  CollegeTools,
  StudentTools,
  WorkOrders,
  WorkEditor,
  ProfileSettings,
} from "./EducationManagement";
import { CollegeManagement } from "./CollegeManagement";
import { emptyPage, statuses } from "./types";
import type {
  Catalog,
  College,
  Overview,
  Page,
  Request,
  Student,
  Task,
  Report,
  Stats,
} from "./types";
import "../components/RoleWorkspace.css";
import "./education.css";
const icons = [
  ClipboardList,
  Stethoscope,
  BadgeCheck,
  Users,
  FileCheck,
  ShieldCheck,
  Search,
];
const emptyAssignment = () => ({
  title: "",
  description: "",
  instructions: "",
  stage: "",
  dueAt: "",
  recipientMode: "INDIVIDUAL",
  studentCode: "",
  requireDocument: false,
  sourceOrderId: null as number | null,
  studentCodes: "",
});
function location() {
  const hash = window.location.hash.split("?")[0];
  const match = hash.match(
    /^#\/education\/(ROLE_[A-Z_]+)\/(dashboard|students|requests|work|assign|reports|colleges|profile|settings|completed|pending)$/,
  );
  return {
    domain: match?.[1] || "",
    section:
      match?.[2] || (hash === "#/education/colleges" ? "colleges" : "home"),
  };
}
function WorkChart({
  stats,
  onStatus,
}: {
  stats: Stats;
  onStatus: (s: string) => void;
}) {
  return (
    <section className="ws-card edu-chart">
      <h2>Work status distribution</h2>
      <p>Choose a bar to open the matching worklist.</p>
      {Object.entries(statuses).map(([s, label], i) => (
        <button
          className={`edu-chart-row edu-accent-${i}`}
          key={s}
          onClick={() => onStatus(s)}
          aria-label={`${label}: ${stats.statusCounts[s] || 0} tasks`}
        >
          <span>{label}</span>
          <i>
            <b
              style={{
                width: `${stats.total ? ((stats.statusCounts[s] || 0) / stats.total) * 100 : 0}%`,
              }}
            />
          </i>
          <strong>{stats.statusCounts[s] || 0}</strong>
        </button>
      ))}
    </section>
  );
}
export function EducationWorkspace() {
  const { user, logout } = useAuth();
  const [route, setRoute] = useState(location),
    [catalog, setCatalog] = useState<Catalog | null>(null),
    [colleges, setColleges] = useState<College[]>([]),
    [overview, setOverview] = useState<Overview | null>(null),
    [collegeId, setCollegeId] = useState(""),
    [studentCode, setStudentCode] = useState(""),
    [studentQuery, setStudentQuery] = useState(""),
    [status, setStatus] = useState(""),
    [accessStatus, setAccessStatus] = useState("PENDING"),
    [search, setSearch] = useState(""),
    [searchQuery, setSearchQuery] = useState(""),
    [students, setStudents] = useState<Page<Student>>(emptyPage),
    [requests, setRequests] = useState<Page<Request>>(emptyPage),
    [tasks, setTasks] = useState<Page<Task>>(emptyPage),
    [reports, setReports] = useState<Page<Report>>(emptyPage),
    [page, setPage] = useState(0),
    [selected, setSelected] = useState<number | null>(null),
    [refresh, setRefresh] = useState(0),
    [loading, setLoading] = useState(true),
    [sectionLoading, setSectionLoading] = useState(false),
    [busy, setBusy] = useState(false),
    [error, setError] = useState(""),
    [notice, setNotice] = useState(""),
    [menu, setMenu] = useState(false),
    [dirty, setDirty] = useState(false),
    [assignment, setAssignment] = useState(emptyAssignment),
    [decision, setDecision] = useState<{
      request: Request;
      status: string;
    } | null>(null),
    [reason, setReason] = useState(""),
    [editingWork, setEditingWork] = useState<Task | null>(null);
  const [grantOpen, setGrantOpen] = useState(false);
  const pendingOrder = useRef<ReturnType<typeof emptyAssignment> | null>(null);
  const dirtyRef = useRef(false),
    prior = useRef(window.location.hash),
    main = useRef<HTMLElement>(null);
  dirtyRef.current = dirty;
  const root = user?.role === "ROLE_ADMIN",
    student = user?.role === "ROLE_STUDENT",
    manager =
      root ||
      user?.role === "ROLE_COLLEGE_ADMIN" ||
      user?.role === "ROLE_DOMAIN_ADMIN";
  const selectionScreen = !root && !student && route.section === "home";
  const workSection = ["work", "completed", "pending"].includes(route.section);
  const domain = catalog?.domains.find((d) => d.code === route.domain);
  const card = overview?.domains.find((d) => d.code === route.domain);
  const title = root
    ? "College administration"
    : student
      ? "Student dashboard"
      : user?.role === "ROLE_DOMAIN_ADMIN"
        ? "Domain Admin dashboard"
        : "College Admin dashboard";
  const reload = useCallback(() => setRefresh((v) => v + 1), []);
  const leave = () =>
    !dirtyRef.current ||
    window.confirm("Discard unsaved college workspace changes?");
  const go = (section: string, code = route.domain) => {
    if (!leave()) return;
    if (section === "home") {
      setCollegeId("");
      setStudentCode("");
      setStudentQuery("");
      setStatus("");
      setSearch("");
      setSearchQuery("");
    }
    setDirty(false);
    setSelected(null);
    setPage(0);
    setMenu(false);
    setError("");
    setNotice("");
    window.location.hash =
      section === "home"
        ? "/education"
        : section === "colleges" && !code
          ? "/education/colleges"
          : `/education/${code}/${section}`;
  };
  useEffect(() => {
    const c = new AbortController();
    apiRequest<{ compactView: boolean; reduceMotion: boolean }>(
      "/api/education/preferences",
      "GET",
      undefined,
      c.signal,
    )
      .then((p) => {
        if (!c.signal.aborted) {
          document.body.classList.toggle("edu-compact", p.compactView);
          document.body.classList.toggle("edu-reduced-motion", p.reduceMotion);
        }
      })
      .catch(() => {});
    return () => {
      c.abort();
      document.body.classList.remove("edu-compact", "edu-reduced-motion");
    };
  }, []);
  useEffect(() => {
    document.body.classList.add("workspace-open");
    if (!window.location.hash.startsWith("#/education"))
      window.history.replaceState(null, "", "#/education");
    prior.current = window.location.hash;
    const hash = () => {
      if (
        dirtyRef.current &&
        !window.confirm("Discard unsaved college workspace changes?")
      ) {
        window.history.pushState(null, "", prior.current);
        return;
      }
      if (
        !window.location.hash.startsWith("#/education") &&
        user?.role !== "ROLE_ADMIN"
      )
        window.history.replaceState(null, "", "#/education");
      prior.current = window.location.hash;
      setRoute(location());
      setPage(0);
      setSelected(null);
      setAssignment(pendingOrder.current || emptyAssignment());
      pendingOrder.current = null;
      setDirty(false);
    };
    const before = (e: BeforeUnloadEvent) => {
      if (dirtyRef.current) {
        e.preventDefault();
        e.returnValue = "";
      }
    };
    window.addEventListener("hashchange", hash);
    window.addEventListener("beforeunload", before);
    return () => {
      document.body.classList.remove("workspace-open");
      window.removeEventListener("hashchange", hash);
      window.removeEventListener("beforeunload", before);
    };
  }, [user?.role]);
  useEffect(() => {
    const tick = setInterval(() => {
      if (!dirtyRef.current && document.visibilityState === "visible") reload();
    }, 15000);
    return () => clearInterval(tick);
  }, [reload]);
  useEffect(() => {
    if (route.section !== "assign") return;
    const changed = Object.entries(assignment).some(
      ([k, v]) => k !== "recipientMode" && k !== "stage" && !!v,
    );
    setDirty(changed);
  }, [assignment, route.section]);
  const params = useCallback(
    (domainRequired = true) => {
      const p = new URLSearchParams();
      if (domainRequired && route.domain) p.set("domain", route.domain);
      if (collegeId) p.set("collegeId", collegeId);
      if (studentCode) p.set("studentCode", studentCode);
      if (route.section === "completed") p.set("status", "COMPLETED");
      else if (route.section === "pending") p.set("status", "PENDING");
      else if (status) p.set("status", status);
      return p;
    },
    [route.domain, route.section, collegeId, studentCode, status],
  );
  useEffect(() => {
    const c = new AbortController();
    Promise.all([
      apiRequest<Catalog>("/api/education/catalog", "GET", undefined, c.signal),
      apiRequest<College[]>(
        "/api/education/colleges" +
          (route.domain ? "?domain=" + route.domain : ""),
        "GET",
        undefined,
        c.signal,
      ),
    ])
      .then(([nextCatalog, cols]) => {
        if (!c.signal.aborted) {
          setCatalog(nextCatalog);
          setColleges(cols);
        }
      })
      .catch((e) => {
        if (!c.signal.aborted) setError(e.message);
      });
    return () => c.abort();
  }, [refresh, route.domain]);
  useEffect(() => {
    const c = new AbortController();
    setError("");
    apiRequest<Overview>(
      `/api/education/overview?${params(false)}`,
      "GET",
      undefined,
      c.signal,
    )
      .then((view) => {
        if (!c.signal.aborted) {
          setOverview(view);
          setLoading(false);
        }
      })
      .catch((e) => {
        if (!c.signal.aborted) {
          setError(e.message);
          setOverview(null);
          setLoading(false);
        }
      });
    return () => c.abort();
  }, [refresh, params]);
  useEffect(() => {
    if (!domain || !card?.authorized || selected !== null) return;
    const c = new AbortController();
    setSectionLoading(true);
    setError("");
    const p = params();
    p.set("page", String(page));
    const name = workSection ? "work" : route.section;
    if (name === "requests") {
      p.delete("studentCode");
      p.set("status", accessStatus);
    }
    if (name === "students") {
      p.set("search", search);
    }
    let path = "";
    if (["students", "requests", "work", "reports", "dashboard"].includes(name))
      path = `/api/education/${name === "work" || name === "dashboard" ? "tasks" : name}?${p}`;
    if (!path) {
      setSectionLoading(false);
      return;
    }
    apiRequest<Page<Student> | Page<Request> | Page<Task> | Page<Report>>(
      path,
      "GET",
      undefined,
      c.signal,
    )
      .then((v) => {
        if (c.signal.aborted) return;
        if (name === "students") setStudents(v as Page<Student>);
        if (name === "requests") setRequests(v as Page<Request>);
        if (name === "work" || name === "dashboard") setTasks(v as Page<Task>);
        if (name === "reports") setReports(v as Page<Report>);
      })
      .catch((e) => {
        if (!c.signal.aborted) {
          setError(e.message);
          setStudents(emptyPage());
          setRequests(emptyPage());
          setTasks(emptyPage());
          setReports(emptyPage());
        }
      })
      .finally(() => {
        if (!c.signal.aborted) setSectionLoading(false);
      });
    return () => c.abort();
  }, [
    domain?.code,
    card?.authorized,
    selected,
    route.section,
    page,
    params,
    refresh,
    search,
    accessStatus,
  ]);
  async function mutate(path: string, body: unknown) {
    if (busy) return false;
    setBusy(true);
    setError("");
    try {
      await apiRequest(path, "POST", body);
      setNotice(
        "Saved successfully. Dashboard statistics will update from the stored records.",
      );
      reload();
      return true;
    } catch (e) {
      setError(e instanceof Error ? e.message : "Request failed");
      return false;
    } finally {
      setBusy(false);
    }
  }
  async function pdf(path: string, file: string) {
    setBusy(true);
    setError("");
    try {
      await downloadPdf(path, file);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Download failed");
    } finally {
      setBusy(false);
    }
  }
  function updateFilter(fn: () => void) {
    if (!leave()) return;
    setSelected(null);
    setPage(0);
    setDirty(false);
    fn();
  }
  const paged =
    route.section === "students"
      ? students
      : route.section === "requests"
        ? requests
        : route.section === "reports"
          ? reports
          : tasks;
  const pager = (
    <div className="ws-actions">
      <button
        disabled={busy || page === 0 || sectionLoading}
        onClick={() => setPage((v) => v - 1)}
      >
        Previous page
      </button>
      <span>
        Page {page + 1} · {paged.totalElements} records
      </span>
      <button
        disabled={busy || sectionLoading || page + 1 >= paged.totalPages}
        onClick={() => setPage((v) => v + 1)}
      >
        Next page
      </button>
    </div>
  );
  return (
    <div
      className={`ws-shell edu-shell ${selectionScreen ? "edu-selection" : ""} ${user?.role === "ROLE_COLLEGE_ADMIN" ? "edu-college-view" : ""}`}
      data-validation-context="education"
      data-testid="education-workspace"
    >
      {menu && (
        <button
          className="ws-drawer-overlay"
          aria-label="Close college navigation"
          onClick={() => setMenu(false)}
        />
      )}
      {!selectionScreen && (
        <aside
          id="education-navigation"
          className={`ws-sidebar ${menu ? "is-open" : ""}`}
        >
          <a
            className="ws-brand"
            href="#/education"
            onClick={(e) => {
              e.preventDefault();
              go("home");
            }}
          >
            PharmaSafe<span>COLLEGE LEARNING</span>
          </a>
          <p>
            {title}
            {overview?.studentCode && (
              <>
                <br />
                {overview.studentCode}
              </>
            )}
          </p>
          <nav aria-label="College workspace navigation">
            <button
              onClick={() => go("home")}
              aria-current={route.section === "home" ? "page" : undefined}
            >
              <GraduationCap size={17} /> All seven domains
            </button>
            {root && (
              <button
                onClick={() => go("colleges", "")}
                aria-current={route.section === "colleges" ? "page" : undefined}
              >
                <Building2 size={17} /> Colleges & administrators
              </button>
            )}
            {domain &&
              card?.authorized &&
              [
                "dashboard",
                ...(!student && (root || user?.role === "ROLE_DOMAIN_ADMIN")
                  ? ["colleges"]
                  : []),
                ...(!student ? ["students"] : []),
                "work",
                ...(manager ? ["assign"] : []),
                "completed",
                "pending",
                "reports",
                ...(user?.role !== "ROLE_DOMAIN_ADMIN" ? ["profile"] : []),
                "settings",
              ].map((section) => (
                <button
                  key={section}
                  onClick={() => {
                    if (section === "completed" || section === "pending") {
                      setStatus("");
                      go(section);
                    } else go(section);
                  }}
                  aria-current={route.section === section ? "page" : undefined}
                >
                  {
                    (
                      {
                        dashboard: "Dashboard",
                        colleges: "Colleges",
                        students: "Students",
                        work: student ? "My assigned work" : "Work Management",
                        assign: "Assign student work",
                        completed: "Completed Work",
                        pending: "Pending Work",
                        reports: "Reports & PDFs",
                        profile: "Profile",
                        settings: "Settings",
                      } as Record<string, string>
                    )[section]
                  }
                </button>
              ))}
          </nav>
          <div className="ws-person">
            {root && (
              <a
                href="#/workspace/admin/overview"
                onClick={(e) => {
                  if (!leave()) e.preventDefault();
                }}
              >
                Main Super Admin workspace <ArrowUpRight size={14} />
              </a>
            )}
            <strong>
              {overview?.fullName || user?.fullName || user?.username}
            </strong>
            <span>@{user?.username}</span>
            <button
              disabled={busy}
              onClick={() => {
                if (leave()) void logout();
              }}
            >
              <LogOut size={16} /> Sign out
            </button>
          </div>
        </aside>
      )}
      <div className="ws-main" inert={menu || undefined}>
        <header className="ws-topbar">
          {!selectionScreen && (
            <button
              className="ws-menu-toggle"
              aria-label="Toggle college navigation"
              aria-controls="education-navigation"
              aria-expanded={menu}
              onClick={() => setMenu((v) => !v)}
            >
              <Menu size={20} />
            </button>
          )}
          <span>
            {title}
            {domain ? " / " + domain.name : ""}
          </span>
          {!selectionScreen && (
            <button
              disabled={busy}
              onClick={() => {
                if (!leave()) return;
                setDirty(false);
                reload();
              }}
            >
              <RefreshCw size={15} /> Refresh college data
            </button>
          )}
          <div className="edu-selection-profile">
            <strong>
              {overview?.fullName || user?.fullName || user?.username}
            </strong>
            <span>
              @{user?.username}
              {overview?.collegeId
                ? " · " +
                  colleges.find((c) => c.id === overview.collegeId)?.name
                : ""}
            </span>
            <button
              onClick={() => {
                if (leave()) void logout();
              }}
            >
              Sign out
            </button>
          </div>
        </header>
        <main className="ws-content" ref={main} tabIndex={-1}>
          <div className="ws-heading">
            <div>
              <p className="ws-eyebrow">CONNECTED DOMAIN LEARNING</p>
              <h1>
                {route.section === "home"
                  ? title
                  : route.section === "colleges"
                    ? domain
                      ? domain.name + " · Colleges"
                      : "Colleges & administrators"
                    : domain?.name || "Domain workspace"}
              </h1>
              <p>
                {student
                  ? "Your learning, your evidence, your progress. Work is private to you and authorized administrators."
                  : "Manage authorized college activity and inspect real student progress across the safety domains."}
              </p>
            </div>
            {overview?.studentCode && (
              <strong className="edu-identity">
                <GraduationCap size={20} />
                {overview.studentCode}
              </strong>
            )}
          </div>
          {error && (
            <div role="alert" className="ws-error">
              {error}
              <button onClick={reload}>Retry college workspace</button>
            </div>
          )}
          {notice && (
            <p role="status" className="ws-notice">
              {notice}
            </p>
          )}
          {loading ? (
            <p role="status">Loading your authorized college workspace…</p>
          ) : !overview ? (
            <section className="ws-card">
              <h2>Workspace is unavailable</h2>
              <p>Check your account and college access, then retry.</p>
            </section>
          ) : route.section === "home" ? (
            <>
              {!selectionScreen && (
                <section className="edu-hero ws-card">
                  <div>
                    <p className="ws-eyebrow">ONE WORKSPACE · SEVEN DOMAINS</p>
                    <h2>Welcome, {overview.fullName || overview.username}.</h2>
                    <p>
                      {student
                        ? "Choose an approved domain to open your tasks. Pending and rejected selections show their approval status below."
                        : user?.role === "ROLE_DOMAIN_ADMIN"
                          ? "Open any of the seven domains to inspect colleges, students, assigned work, progress and reports. Your educational oversight does not grant clinical or college-management authority."
                          : "Choose a domain to manage students, assignments, progress and reports."}
                    </p>
                  </div>
                  <BookOpenCheck size={64} />
                </section>
              )}
              {user?.role === "ROLE_COLLEGE_ADMIN" && (
                <div className="edu-access-banner">
                  <div>
                    <h2>Student access requests</h2>
                    <p>
                      Review new registrations before students start their
                      domain work.
                    </p>
                  </div>
                  <button
                    className="ws-primary edu-grant-access"
                    aria-label="Grant Student Access"
                    onClick={() => setGrantOpen(true)}
                  >
                    Grant Student Access{" "}
                    <span aria-hidden="true">{overview.pendingStudentApprovals || 0}</span>
                  </button>
                </div>
              )}
              <div className="edu-domain-grid">
                {overview.domains.map((d, i) => {
                  const Icon = icons[i];
                  return (
                    <article
                      className={`ws-card edu-domain-card edu-accent-${i}`}
                      key={d.code}
                    >
                      <div className="edu-card-top">
                        <span className="edu-domain-icon">
                          <Icon size={23} />
                        </span>
                        <span className="ws-badge">
                          {d.status.replaceAll("_", " ")}
                        </span>
                      </div>
                      <h2>{d.name}</h2>
                      <p>{d.description}</p>
                      {d.stats ? (
                        <div className="edu-card-stats">
                          {!student && (
                            <>
                              {user?.role !== "ROLE_COLLEGE_ADMIN" && (
                                <span>
                                  <strong>{d.stats.colleges || 0}</strong>
                                  Colleges
                                </span>
                              )}
                              <span>
                                <strong>{d.stats.students || 0}</strong>Students
                              </span>
                            </>
                          )}
                          <span>
                            <strong>{d.stats.total}</strong>Tasks
                          </span>
                          {!selectionScreen && (
                            <>
                              <span>
                                <strong>
                                  {d.stats.statusCounts.COMPLETED || 0}
                                </strong>
                                Completed
                              </span>
                              <span>
                                <strong>{d.stats.averageProgress}%</strong>
                                Progress
                              </span>
                            </>
                          )}
                        </div>
                      ) : (
                        <p className="edu-lock">
                          <LockKeyhole size={15} />
                          {d.status === "PENDING"
                            ? "Awaiting college approval"
                            : d.status === "REJECTED"
                              ? "Access was rejected by your college"
                              : d.status === "NOT_SELECTED"
                                ? "Not selected at registration"
                                : "Outside your assigned domain"}
                        </p>
                      )}
                      <button
                        disabled={!d.authorized}
                        onClick={() => go("dashboard", d.code)}
                      >
                        Open {d.name} <ArrowUpRight size={16} />
                      </button>
                    </article>
                  );
                })}
              </div>
            </>
          ) : route.section === "colleges" && root && !domain ? (
            <CollegeManagement
              colleges={colleges}
              domains={catalog?.domains || []}
              refresh={refresh}
              onSaved={reload}
              onDirty={setDirty}
            />
          ) : !domain || !card?.authorized ? (
            <section className="ws-card">
              <h2>Domain access is restricted</h2>
              <p>Choose an authorized domain from your dashboard.</p>
              <button onClick={() => go("home")}>Return to domain cards</button>
            </section>
          ) : (
            <>
              {selected === null && (
                <section className="edu-filter-bar ws-card">
                  <h2>Workspace filters</h2>
                  <div className="edu-filter-grid">
                    {!overview.collegeId && (
                      <label>
                        College filter
                        <select
                          aria-label="College filter"
                          value={collegeId}
                          onChange={(e) =>
                            updateFilter(() => setCollegeId(e.target.value))
                          }
                        >
                          <option value="">All authorized colleges</option>
                          {colleges.map((c) => (
                            <option key={c.id} value={c.id}>
                              {c.name}
                              {c.active ? "" : " (inactive)"}
                            </option>
                          ))}
                        </select>
                      </label>
                    )}
                    {!student && (
                      <form
                        onSubmit={(e) => {
                          e.preventDefault();
                          updateFilter(() =>
                            setStudentCode(studentQuery.trim().toUpperCase()),
                          );
                        }}
                      >
                        <label>
                          Student ID filter
                          <input
                            aria-label="Student ID filter"
                            maxLength={32}
                            placeholder="COLLEGE-0001"
                            value={studentQuery}
                            onChange={(e) =>
                              setStudentQuery(e.target.value.toUpperCase())
                            }
                          />
                        </label>
                        <button>Apply Student ID</button>
                      </form>
                    )}
                    <label>
                      Work status filter
                      <select
                        aria-label="Work status filter"
                        value={
                          route.section === "completed"
                            ? "COMPLETED"
                            : route.section === "pending"
                              ? "PENDING"
                              : status
                        }
                        onChange={(e) => {
                          const next = e.target.value;
                          if (
                            route.section === "completed" ||
                            route.section === "pending"
                          ) {
                            if (!leave()) return;
                            dirtyRef.current = false;
                            setDirty(false);
                            setStatus(next);
                            go("work");
                          } else updateFilter(() => setStatus(next));
                        }}
                      >
                        <option value="">All work statuses</option>
                        <option value="PENDING">Pending work</option>
                        {Object.entries(statuses).map(([v, l]) => (
                          <option key={v} value={v}>
                            {l}
                          </option>
                        ))}
                      </select>
                    </label>
                    <button
                      onClick={() =>
                        updateFilter(() => {
                          setStudentCode("");
                          setStudentQuery("");
                          setCollegeId("");
                          setStatus("");
                          setSearch("");
                          setSearchQuery("");
                        })
                      }
                    >
                      Clear workspace filters
                    </button>
                  </div>
                </section>
              )}
              {selected !== null ? (
                <EducationTask
                  key={selected}
                  id={selected}
                  domain={domain}
                  student={student}
                  manager={manager}
                  onClose={() => {
                    if (leave()) {
                      setDirty(false);
                      setSelected(null);
                    }
                  }}
                  onSaved={reload}
                  onDirty={setDirty}
                />
              ) : route.section === "dashboard" ? (
                <>
                  <div className="edu-metrics">
                    {(student
                      ? [
                          ["Assigned tasks", card.stats?.total || 0],
                          [
                            "Completed",
                            card.stats?.statusCounts.COMPLETED || 0,
                          ],
                          [
                            "Awaiting review",
                            card.stats?.statusCounts.SUBMITTED || 0,
                          ],
                          ["Overdue", card.stats?.overdue || 0],
                        ]
                      : [
                          ...(root || user?.role === "ROLE_DOMAIN_ADMIN"
                            ? [["Total Colleges", card.stats?.colleges || 0]]
                            : []),
                          ["Total Students", card.stats?.students || 0],
                          ["Assigned tasks", card.stats?.total || 0],
                          [
                            "Completed",
                            card.stats?.statusCounts.COMPLETED || 0,
                          ],
                          [
                            "Pending Work",
                            (card.stats?.statusCounts.NOT_STARTED || 0) +
                              (card.stats?.statusCounts.RETURNED || 0) +
                              (card.stats?.statusCounts.SUBMITTED || 0),
                          ],
                          [
                            "In Progress Work",
                            card.stats?.statusCounts.IN_PROGRESS || 0,
                          ],
                        ]
                    ).map(([label, value], i) => (
                      <article
                        className={`edu-metric edu-accent-${i}`}
                        key={label}
                      >
                        <span>{label}</span>
                        <strong>{value}</strong>
                        <small>From authorized persisted work</small>
                      </article>
                    ))}
                  </div>
                  <WorkChart
                    stats={card.stats!}
                    onStatus={(s) => {
                      setStatus(s);
                      go("work");
                    }}
                  />
                  <section className="ws-card">
                    <h2>Recent student activity</h2>
                    {!tasks.content.length ? (
                      <p>No activity for the selected filters.</p>
                    ) : (
                      <div className="ws-table-wrap">
                        <table>
                          <thead>
                            <tr>
                              <th>Work / Student</th>
                              <th>Status</th>
                              <th>Last updated</th>
                            </tr>
                          </thead>
                          <tbody>
                            {tasks.content.slice(0, 5).map((t) => (
                              <tr key={t.id}>
                                <td>
                                  <button onClick={() => setSelected(t.id)}>
                                    {t.title}
                                  </button>
                                  <small>
                                    {t.studentName} · {t.studentCode}
                                  </small>
                                </td>
                                <td>
                                  {statuses[t.status]} · {t.progress}%
                                </td>
                                <td>{dateTime(t.updatedAt)}</td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </div>
                    )}
                  </section>
                  <section className="ws-card">
                    <h2>Domain workflow</h2>
                    <ol className="edu-stage-list">
                      {domain.stages.map((s) => (
                        <li key={s}>{s}</li>
                      ))}
                    </ol>
                    <p>
                      Assigned → In progress → Submitted → College review →
                      Completed or returned for correction.
                    </p>
                    <button className="ws-primary" onClick={() => go("work")}>
                      {student
                        ? "Open my assigned work"
                        : "Open domain worklist"}
                    </button>
                  </section>
                </>
              ) : route.section === "colleges" ? (
                <CollegeTools
                  colleges={colleges}
                  canManage={root || user?.role === "ROLE_DOMAIN_ADMIN"}
                  onSaved={reload}
                  onDirty={setDirty}
                  onSelect={(id) => {
                    setCollegeId(id);
                    go("students");
                  }}
                />
              ) : route.section === "profile" ||
                route.section === "settings" ? (
                <ProfileSettings settings={route.section === "settings"} />
              ) : sectionLoading ? (
                <p role="status">Loading scoped domain records…</p>
              ) : route.section === "students" ? (
                <section className="ws-card">
                  <h2>Student directory</h2>
                  {manager && (
                    <button onClick={() => go("requests")}>
                      Access requests
                    </button>
                  )}
                  {manager && (
                    <StudentTools
                      domain={domain}
                      colleges={colleges}
                      collegeId={collegeId || String(overview.collegeId || "")}
                      students={students.content}
                      onSaved={reload}
                      onDirty={setDirty}
                    />
                  )}
                  <form
                    className="edu-search"
                    onSubmit={(e) => {
                      e.preventDefault();
                      setPage(0);
                      setSearch(searchQuery.trim());
                    }}
                  >
                    <label>
                      Search students
                      <input
                        aria-label="Search students"
                        maxLength={100}
                        value={searchQuery}
                        onChange={(e) => setSearchQuery(e.target.value)}
                        placeholder="Student ID, name, email or username"
                      />
                    </label>
                    <button>Search directory</button>
                  </form>
                  <div className="ws-table-wrap">
                    <table>
                      <thead>
                        <tr>
                          <th>Student</th>
                          <th>ID / college</th>
                          <th>Account status</th>
                          <th>Selected domains</th>
                          <th>Progress</th>
                          <th>Work</th>
                        </tr>
                      </thead>
                      <tbody>
                        {students.content.map((s) => (
                          <tr key={s.id}>
                            <td>
                              {s.fullName}
                              <small>@{s.username}</small>
                              <small>{s.email}</small>
                            </td>
                            <td>
                              {s.studentCode}
                              <small>{s.collegeName}</small>
                            </td>
                            <td>
                              <span className="ws-badge">
                                {s.active === false ? "Inactive" : "Active"}
                              </span>
                            </td>
                            <td>
                              {s.domains?.map((d) => (
                                <small key={d.domain}>
                                  {
                                    catalog?.domains.find(
                                      (x) => x.code === d.domain,
                                    )?.name
                                  }
                                  : {d.status}
                                </small>
                              ))}
                            </td>
                            <td>
                              {s.work?.averageProgress || 0}% ·{" "}
                              {s.work?.total || 0} tasks
                            </td>
                            <td>
                              <button
                                onClick={() => {
                                  setStudentCode(s.studentCode);
                                  setStudentQuery(s.studentCode);
                                  go("work");
                                }}
                              >
                                View student work
                              </button>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                  {!students.content.length && (
                    <p>No students match this domain and college scope.</p>
                  )}
                  {pager}
                </section>
              ) : route.section === "requests" ? (
                <section className="ws-card">
                  <h2>Domain access requests</h2>
                  <label className="edu-small-filter">
                    Access status
                    <select
                      aria-label="Access status"
                      value={accessStatus}
                      onChange={(e) => {
                        setPage(0);
                        setAccessStatus(e.target.value);
                      }}
                    >
                      <option value="">All access statuses</option>
                      {["PENDING", "APPROVED", "REJECTED"].map((v) => (
                        <option key={v}>{v}</option>
                      ))}
                    </select>
                  </label>
                  <div className="ws-table-wrap">
                    <table>
                      <thead>
                        <tr>
                          <th>Student</th>
                          <th>College</th>
                          <th>Status</th>
                          <th>Decision reason</th>
                          {(root || user?.role === "ROLE_COLLEGE_ADMIN") && (
                            <th>Actions</th>
                          )}
                        </tr>
                      </thead>
                      <tbody>
                        {requests.content.map((r) => (
                          <tr key={r.id}>
                            <td>
                              {r.student.fullName}
                              <small>
                                {r.student.studentCode} · @{r.student.username}
                              </small>
                            </td>
                            <td>{r.student.collegeName}</td>
                            <td>
                              <span className="ws-badge">{r.status}</span>
                            </td>
                            <td>{r.reason || "Awaiting review"}</td>
                            {(root || user?.role === "ROLE_COLLEGE_ADMIN") && (
                              <td>
                                <div className="ws-actions">
                                  {r.status !== "APPROVED" && (
                                    <button
                                      onClick={() =>
                                        setDecision({
                                          request: r,
                                          status: "APPROVED",
                                        })
                                      }
                                    >
                                      Approve domain access
                                    </button>
                                  )}
                                  {r.status !== "REJECTED" && (
                                    <button
                                      onClick={() =>
                                        setDecision({
                                          request: r,
                                          status: "REJECTED",
                                        })
                                      }
                                    >
                                      {r.status === "APPROVED"
                                        ? "Revoke domain access"
                                        : "Reject domain access"}
                                    </button>
                                  )}
                                </div>
                              </td>
                            )}
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                  {!requests.content.length && (
                    <p>No access requests match this filter.</p>
                  )}
                  {pager}
                </section>
              ) : route.section === "assign" && manager ? (
                <section className="ws-card">
                  <h2>Assign {domain.name} work</h2>
                  <p>
                    Use a Student ID or create independent assignments for every
                    approved student in the selected college and domain.
                  </p>
                  {assignment.sourceOrderId && (
                    <p className="ws-notice">
                      College work instructions are fixed. Choose the recipients
                      and a future deadline no later than the college work
                      deadline.
                    </p>
                  )}
                  <form
                    className="ws-form"
                    aria-label="Assign student work"
                    inert={busy || undefined}
                    onSubmit={async (e) => {
                      e.preventDefault();
                      const c = overview.collegeId || Number(collegeId);
                      if (!c) {
                        setError("Select a college before assigning work.");
                        return;
                      }
                      if (
                        await mutate("/api/education/tasks", {
                          ...assignment,
                          studentCodes:
                            assignment.recipientMode === "SELECTED"
                              ? assignment.studentCodes
                                  .split(/[\s,]+/)
                                  .filter(Boolean)
                              : null,
                          collegeId: c,
                          domain: domain.code,
                          stage: assignment.stage || domain.stages[0],
                          dueAt: new Date(assignment.dueAt).toISOString(),
                          studentCode:
                            assignment.recipientMode === "INDIVIDUAL"
                              ? assignment.studentCode
                              : null,
                        })
                      ) {
                        setAssignment(emptyAssignment());
                        dirtyRef.current = false;
                        setDirty(false);
                        go("work");
                      }
                    }}
                  >
                    <label>
                      Task title
                      <input
                        required
                        name="title"
                        readOnly={!!assignment.sourceOrderId}
                        maxLength={160}
                        value={assignment.title}
                        onChange={(e) =>
                          setAssignment((v) => ({
                            ...v,
                            title: e.target.value,
                          }))
                        }
                      />
                    </label>
                    <label>
                      Domain stage
                      <select
                        disabled={!!assignment.sourceOrderId}
                        aria-label="Domain stage"
                        value={assignment.stage || domain.stages[0]}
                        onChange={(e) =>
                          setAssignment((v) => ({
                            ...v,
                            stage: e.target.value,
                          }))
                        }
                      >
                        {domain.stages.map((s) => (
                          <option key={s}>{s}</option>
                        ))}
                      </select>
                    </label>
                    <label>
                      Recipients
                      <select
                        aria-label="Recipients"
                        value={assignment.recipientMode}
                        onChange={(e) =>
                          setAssignment((v) => ({
                            ...v,
                            recipientMode: e.target.value,
                          }))
                        }
                      >
                        <option value="INDIVIDUAL">
                          Individual student by ID
                        </option>
                        <option value="SELECTED">
                          Selected students by ID
                        </option>
                        <option value="ALL_APPROVED">
                          All approved students in college/domain
                        </option>
                      </select>
                    </label>
                    {assignment.recipientMode === "SELECTED" && (
                      <label>
                        Selected Student IDs
                        <textarea
                          required
                          maxLength={3200}
                          placeholder="COLLEGE-0001, COLLEGE-0002"
                          value={assignment.studentCodes}
                          onChange={(e) =>
                            setAssignment((v) => ({
                              ...v,
                              studentCodes: e.target.value.toUpperCase(),
                            }))
                          }
                        />
                      </label>
                    )}
                    {assignment.recipientMode === "INDIVIDUAL" && (
                      <label>
                        Student ID
                        <input
                          name="studentCode"
                          required
                          pattern="(?:ST-[0-9]{6,18}|[A-Z0-9][A-Z0-9-]{2,15}-[0-9]{4,15})"
                          maxLength={32}
                          value={assignment.studentCode}
                          onChange={(e) =>
                            setAssignment((v) => ({
                              ...v,
                              studentCode: e.target.value.toUpperCase(),
                            }))
                          }
                        />
                      </label>
                    )}
                    <label>
                      Task deadline
                      <input
                        type="datetime-local"
                        aria-label="Task deadline"
                        required
                        value={assignment.dueAt}
                        onChange={(e) =>
                          setAssignment((v) => ({
                            ...v,
                            dueAt: e.target.value,
                          }))
                        }
                      />
                    </label>
                    <label className="edu-checkbox">
                      <input
                        type="checkbox"
                        disabled={!!assignment.sourceOrderId}
                        checked={assignment.requireDocument}
                        onChange={(e) =>
                          setAssignment((v) => ({
                            ...v,
                            requireDocument: e.target.checked,
                          }))
                        }
                      />
                      Require supporting PDF on final submission
                    </label>
                    <label className="edu-wide">
                      Task description
                      <textarea
                        name="description"
                        readOnly={!!assignment.sourceOrderId}
                        required
                        maxLength={5000}
                        value={assignment.description}
                        onChange={(e) =>
                          setAssignment((v) => ({
                            ...v,
                            description: e.target.value,
                          }))
                        }
                      />
                    </label>
                    <label className="edu-wide">
                      Instructions and expected evidence
                      <textarea
                        name="instructions"
                        readOnly={!!assignment.sourceOrderId}
                        required
                        maxLength={5000}
                        value={assignment.instructions}
                        onChange={(e) =>
                          setAssignment((v) => ({
                            ...v,
                            instructions: e.target.value,
                          }))
                        }
                      />
                    </label>
                    <button className="ws-primary" disabled={busy}>
                      Assign domain work
                    </button>
                  </form>
                </section>
              ) : workSection ? (
                <>
                  {manager && (
                    <WorkOrders
                      domain={domain}
                      colleges={colleges}
                      collegeId={collegeId || String(overview.collegeId || "")}
                      canManage={root || user?.role === "ROLE_DOMAIN_ADMIN"}
                      refresh={refresh}
                      onSaved={reload}
                      onDirty={setDirty}
                      onAssign={(o) => {
                        if (!leave()) return;
                        setCollegeId(String(o.collegeId));
                        pendingOrder.current = {
                          title: o.title,
                          description: o.description,
                          instructions: o.instructions,
                          stage: o.stage,
                          dueAt: new Date(
                            new Date(o.dueAt).getTime() -
                              new Date(o.dueAt).getTimezoneOffset() * 60000,
                          )
                            .toISOString()
                            .slice(0, 16),
                          requireDocument: o.requireDocument,
                          recipientMode: "INDIVIDUAL",
                          studentCode: "",
                          studentCodes: "",
                          sourceOrderId: o.id,
                        };
                        dirtyRef.current = false;
                        setDirty(false);
                        go("assign");
                      }}
                    />
                  )}
                  <section className="ws-card">
                    <h2>{student ? "My assigned work" : "Domain worklist"}</h2>
                    <div className="ws-table-wrap">
                      <table>
                        <thead>
                          <tr>
                            <th>Task / stage</th>
                            <th>Student / college</th>
                            <th>Deadline</th>
                            <th>Status</th>
                            <th>Progress</th>
                            <th>Details</th>
                          </tr>
                        </thead>
                        <tbody>
                          {tasks.content.map((t) => (
                            <tr key={t.id}>
                              <td>
                                {t.title}
                                <small>{t.stage}</small>
                              </td>
                              <td>
                                {t.studentName}
                                <small>
                                  {t.studentCode} · {t.collegeName}
                                </small>
                              </td>
                              <td>{dateTime(t.dueAt)}</td>
                              <td>{statuses[t.status]}</td>
                              <td>
                                <progress
                                  value={t.progress}
                                  max={100}
                                  aria-label={`${t.title} progress`}
                                />
                                {t.progress}%
                              </td>
                              <td>
                                <button
                                  onClick={() => {
                                    setDirty(false);
                                    setSelected(t.id);
                                  }}
                                >
                                  Open student assignment
                                </button>
                                {manager && (
                                  <button onClick={() => setEditingWork(t)}>
                                    Edit or remove work
                                  </button>
                                )}
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                    {!tasks.content.length && (
                      <p>No assigned tasks match this domain and filter.</p>
                    )}
                    {pager}
                  </section>
                </>
              ) : route.section === "reports" ? (
                <section className="ws-card">
                  <div className="ws-section-heading">
                    <div>
                      <h2>Progress reports & PDF history</h2>
                      <p>
                        Generate a preserved PDF from the current college,
                        Student ID, domain and work-status filters. Up to 200
                        matching tasks per report.
                      </p>
                    </div>
                    <button
                      className="ws-primary"
                      disabled={busy}
                      onClick={() =>
                        void mutate("/api/education/reports", {
                          domain: domain.code,
                          collegeId: collegeId ? Number(collegeId) : null,
                          studentCode: studentCode || null,
                          status: status || null,
                        })
                      }
                    >
                      Generate progress PDF
                    </button>
                  </div>
                  <div className="ws-table-wrap">
                    <table>
                      <thead>
                        <tr>
                          <th>Report</th>
                          <th>Student / status</th>
                          <th>Generated</th>
                          <th>Download</th>
                        </tr>
                      </thead>
                      <tbody>
                        {reports.content.map((r) => (
                          <tr key={r.id}>
                            <td>
                              {domain.name}
                              <small>SHA-256: {r.sha256}</small>
                            </td>
                            <td>
                              {r.studentCode || "All scoped students"}
                              <small>
                                {r.status ? statuses[r.status] : "All statuses"}
                              </small>
                            </td>
                            <td>
                              {dateTime(r.createdAt)}
                              <small>{r.generatedBy}</small>
                            </td>
                            <td>
                              <button
                                disabled={busy}
                                onClick={() =>
                                  void pdf(
                                    `/api/education/reports/${r.id}/pdf`,
                                    r.fileName,
                                  )
                                }
                              >
                                Download progress PDF
                              </button>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                  {!reports.content.length && (
                    <p>
                      No preserved reports match these filters. Generate one
                      from the current database records.
                    </p>
                  )}
                  {pager}
                </section>
              ) : null}
            </>
          )}
          {overview && (
            <p className="edu-refresh">
              Updated {dateTime(overview.updatedAt)}. Statistics refresh every
              15 seconds while this page is visible. Unsaved work is preserved.
            </p>
          )}
        </main>
      </div>
      {grantOpen && user?.role === "ROLE_COLLEGE_ADMIN" && (
        <GrantStudentAccess
          onClose={() => setGrantOpen(false)}
          onSaved={reload}
        />
      )}
      {editingWork && (
        <WorkEditor
          work={editingWork}
          path={"/api/education/tasks/" + editingWork.id}
          onSaved={reload}
          onClose={() => setEditingWork(null)}
        />
      )}
      {decision && (
        <AccessDialog
          title="Confirm student domain access"
          blocked={busy}
          onClose={() => {
            setDecision(null);
            setReason("");
          }}
        >
          <form
            className="ws-form"
            onSubmit={async (e) => {
              e.preventDefault();
              if (
                await mutate(
                  `/api/education/requests/${decision.request.id}/decision`,
                  {
                    expectedVersion: decision.request.rowVersion,
                    status: decision.status,
                    reason,
                  },
                )
              ) {
                setDecision(null);
                setReason("");
              }
            }}
          >
            <p>
              {decision.request.student.studentCode} · {domain?.name} ·{" "}
              {decision.status}
            </p>
            {error && (
              <p className="ws-error" role="alert">
                {error}
              </p>
            )}
            <label className="edu-wide">
              Decision reason
              <textarea
                name="reason"
                required
                maxLength={2000}
                value={reason}
                onChange={(e) => setReason(e.target.value)}
              />
            </label>
            <button disabled={busy}>Confirm domain access decision</button>
          </form>
        </AccessDialog>
      )}
    </div>
  );
}
