import { useCallback, useEffect, useRef, useState } from "react";
import {
  LayoutDashboard,
  Users,
  UserPlus,
  ClipboardList,
  Send,
  RefreshCw,
  ShieldCheck,
  LogOut,
  Menu,
  ArrowUpRight,
} from "lucide-react";
import { useAuth } from "../context/AuthContext";
import { apiRequest } from "../config/api";
import { AccessDialog } from "./AccessDialog";
import { ProcessingTaskDetail } from "./ProcessingTaskDetail";
import type {
  ProcessingPage,
  ProcessingSummary,
  ProcessingTeam,
  TeamPerson,
  WorkAssignment,
} from "./processingModels";
import {
  processingStages,
  statusColors,
  workStatusLabels,
} from "./processingModels";
import { dateTime } from "./model";
import "../components/RoleWorkspace.css";
import "./ProcessingTeamWorkspace.css";
type Tab =
  | "dashboard"
  | "teams"
  | "employees"
  | "approvals"
  | "assign"
  | "work";
type Member = TeamPerson & { membershipStatus: string };
type Registration = {
  id: number;
  fullName: string;
  staffCode: string;
  username: string;
  teamCode: string;
  accountStatus: string;
  email: string;
  employeeId: string;
  reason: string;
  details: Record<string, string>;
};
const emptyPage = <T,>(): ProcessingPage<T> => ({
  content: [],
  totalElements: 0,
  totalPages: 0,
});
const tabItems = [
  { id: "dashboard", label: "Dashboard", Icon: LayoutDashboard },
  { id: "teams", label: "Team Leads & teams", Icon: Users },
  { id: "employees", label: "Employees", Icon: Users },
  { id: "approvals", label: "Registration requests", Icon: UserPlus },
  { id: "assign", label: "Assign work", Icon: Send },
  { id: "work", label: "Work & responses", Icon: ClipboardList },
] as const;
function localTime(value: Date) {
  return new Date(value.getTime() - value.getTimezoneOffset() * 60000)
    .toISOString()
    .slice(0, 16);
}
function StatusChart({
  summary,
  status,
  onSelect,
}: {
  summary: ProcessingSummary;
  status: string;
  onSelect: (value: string) => void;
}) {
  let offset = 0;
  return (
    <section className="ws-card cp-status-chart">
      <div>
        <p className="cp-eyebrow">CURRENT WORKLOAD</p>
        <h2>Task progress</h2>
        <p>Click a status to open its worklist.</p>
      </div>
      <div className="cp-chart-body">
        <div className="cp-donut">
          <svg
            viewBox="0 0 140 140"
            role="img"
            aria-label={`${summary.totalAssignments} scoped work assignments`}
          >
            <circle
              cx="70"
              cy="70"
              r="53"
              fill="none"
              stroke="#edf1f7"
              strokeWidth="15"
            />
            {Object.entries(summary.statusCounts).map(([key, count]) => {
              const length = summary.totalAssignments
                ? (count / summary.totalAssignments) * 333.01
                : 0;
              const current = offset;
              offset += length;
              return (
                <circle
                  key={key}
                  cx="70"
                  cy="70"
                  r="53"
                  fill="none"
                  stroke={statusColors[key]}
                  strokeWidth="15"
                  strokeDasharray={`${length} ${333.01 - length}`}
                  strokeDashoffset={-current}
                  transform="rotate(-90 70 70)"
                  opacity={!status || status === key ? 1 : 0.3}
                />
              );
            })}
          </svg>
          <span>
            <strong>{summary.totalAssignments}</strong>
            <small>Assignments</small>
          </span>
        </div>
        <div className="cp-legend">
          {Object.entries(summary.statusCounts).map(([key, count]) => (
            <button
              key={key}
              aria-pressed={status === key}
              onClick={() => onSelect(status === key ? "" : key)}
            >
              <i style={{ background: statusColors[key] }} />
              <span>{workStatusLabels[key]}</span>
              <strong>{count}</strong>
            </button>
          ))}
        </div>
      </div>
      {!summary.totalAssignments && (
        <p>No work assigned yet. Counts will update from saved assignments.</p>
      )}
    </section>
  );
}
function CreateTeamForm({
  busy,
  onCreate,
}: {
  busy: boolean;
  onCreate: (body: Record<string, string>) => Promise<boolean>;
}) {
  const [form, setForm] = useState<Record<string, string>>({});
  const field = (name: string, label: string, type = "text", max = 128) => (
    <label>
      {label}
      <input
        name={name}
        type={type}
        required
        maxLength={max}
        value={form[name] || ""}
        onChange={(e) => setForm((v) => ({ ...v, [name]: e.target.value }))}
      />
    </label>
  );
  return (
    <form
      className="ws-card ws-form"
      inert={busy || undefined}
      onSubmit={(e) => {
        e.preventDefault();
        void onCreate(form).then((ok) => {
          if (ok) setForm({});
        });
      }}
    >
      <h2>Create a Team Lead and team</h2>
      <p>
        Up to 10 active Team Leads, with 10 reserved/active employee seats per
        team. Each Lead receives a separate restricted login.
      </p>
      {field("name", "Team name", "text", 100)}
      {field("fullName", "Team Lead full name")}
      {field("username", "Team Lead username", "text", 64)}
      {field("email", "Team Lead official email", "email", 254)}
      {field("password", "Initial Team Lead password", "password", 72)}
      <label>
        Creation reason
        <textarea
          name="reason"
          required
          maxLength={2000}
          value={form.reason || ""}
          onChange={(e) => setForm((v) => ({ ...v, reason: e.target.value }))}
        />
      </label>
      <p>
        Save and privately share the initial password before leaving this form.
        Passwords are stored as hashes and are not shown again.
      </p>
      <button disabled={busy}>Create Team Lead & team</button>
    </form>
  );
}
function AssignWorkForm({
  team,
  staffCode,
  busy,
  onAssign,
}: {
  team: ProcessingTeam;
  staffCode: string;
  busy: boolean;
  onAssign: (body: unknown) => Promise<boolean>;
}) {
  const [form, setForm] = useState(() => ({
    title: "",
    description: "",
    instructions: "",
    stage: "INTAKE",
    clinicalRequired: true,
    priority: "Routine",
    dueAt: localTime(new Date(Date.now() + 86400000)),
    recipientMode: "INDIVIDUAL",
    employeeStaffCode: staffCode,
    caseNumber: "",
  }));
  return (
    <form
      className="ws-card ws-form"
      inert={busy || undefined}
      onSubmit={(e) => {
        e.preventDefault();
        void onAssign({
          ...form,
          teamId: team.id,
          dueAt: new Date(form.dueAt).toISOString(),
          employeeStaffCode:
            form.recipientMode === "INDIVIDUAL"
              ? form.employeeStaffCode
              : undefined,
        }).then((ok) => {
          if (ok)
            setForm((v) => ({
              ...v,
              title: "",
              description: "",
              instructions: "",
              caseNumber: "",
            }));
        });
      }}
    >
      <h2>Assign work · {team.teamCode}</h2>
      <p>
        Every recipient gets an individual assignment, progress history and PDF.
        Use the permanent staff ID shown in the employee directory.
      </p>
      <label className="cp-wide">
        Task title
        <input
          name="title"
          required
          maxLength={160}
          value={form.title}
          onChange={(e) => setForm((v) => ({ ...v, title: e.target.value }))}
        />
      </label>
      <label>
        Work type
        <select aria-label="Work type" value={form.clinicalRequired ? 'CLINICAL' : 'INSTRUCTIONS'} onChange={e=>setForm(v=>({...v,clinicalRequired:e.target.value==='CLINICAL'}))}>
          <option value="CLINICAL">Clinical case work — saved case required</option>
          <option value="INSTRUCTIONS">General instructions / training task</option>
        </select>
        <small>Clinical work requires a linked case and saved stage evidence before completion.</small>
      </label>
      <label>
        Case Processing stage
        <select
          aria-label="Case Processing stage"
          value={form.stage}
          onChange={(e) => setForm((v) => ({ ...v, stage: e.target.value }))}
        >
          {Object.entries(processingStages).map(([key, label]) => (
            <option key={key} value={key}>
              {label}
            </option>
          ))}
        </select>
      </label>
      <label>
        Priority
        <select
          aria-label="Priority"
          value={form.priority}
          onChange={(e) => setForm((v) => ({ ...v, priority: e.target.value }))}
        >
          {["Routine", "Priority", "Expedited"].map((v) => (
            <option key={v}>{v}</option>
          ))}
        </select>
      </label>
      <label>
        Recipients
        <select
          aria-label="Recipients"
          value={form.recipientMode}
          onChange={(e) =>
            setForm((v) => ({
              ...v,
              recipientMode: e.target.value,
              caseNumber: e.target.value === "ALL_TEAM" ? "" : v.caseNumber,
            }))
          }
        >
          <option value="INDIVIDUAL">One employee by staff ID</option>
          <option value="ALL_TEAM">All active employees in this team</option>
        </select>
      </label>
      {form.recipientMode === "INDIVIDUAL" ? (
        <label>
          Employee staff ID
          <input
            name="employeeStaffCode"
            placeholder="PS-17"
            pattern="PS-[1-9][0-9]{0,17}"
            required
            maxLength={32}
            value={form.employeeStaffCode}
            onChange={(e) =>
              setForm((v) => ({
                ...v,
                employeeStaffCode: e.target.value.toUpperCase(),
              }))
            }
          />
        </label>
      ) : (
        <p>
          Whole-team tasks are common instructions. A single clinical case
          cannot be allocated to multiple employees at once.
        </p>
      )}
      <label>
        Task due date and time
        <input
          type="datetime-local"
          required
          value={form.dueAt}
          onChange={(e) => setForm((v) => ({ ...v, dueAt: e.target.value }))}
        />
      </label>
      {form.recipientMode === "INDIVIDUAL" && (
        <label>
          Clinical case number (optional)
          <input
            name="caseNumber"
            maxLength={64}
            value={form.caseNumber}
            onChange={(e) =>
              setForm((v) => ({ ...v, caseNumber: e.target.value }))
            }
          />
          <small>
            Only this team's existing intake/data-entry cases. Linking allocates
            the case to the selected employee.
          </small>
        </label>
      )}
      <label className="cp-wide">
        Assigned work description
        <textarea
          name="description"
          required
          maxLength={5000}
          value={form.description}
          onChange={(e) =>
            setForm((v) => ({ ...v, description: e.target.value }))
          }
        />
      </label>
      <label className="cp-wide">
        Instructions and expected evidence
        <textarea
          name="instructions"
          maxLength={5000}
          value={form.instructions}
          onChange={(e) =>
            setForm((v) => ({ ...v, instructions: e.target.value }))
          }
        />
      </label>
      <button disabled={busy || !team.active}>
        Assign work & generate PDFs
      </button>
    </form>
  );
}
export function ProcessingTeamWorkspace() {
  const { user, logout } = useAuth();
  const [tab, setTab] = useState<Tab>("dashboard"),
    [teamId, setTeamId] = useState(
      user?.processingTeamId ? String(user.processingTeamId) : "",
    ),
    [teams, setTeams] = useState<ProcessingTeam[]>([]),
    [summary, setSummary] = useState<ProcessingSummary | null>(null),
    [work, setWork] = useState<ProcessingPage<WorkAssignment>>(emptyPage),
    [members, setMembers] = useState<ProcessingPage<Member>>(emptyPage),
    [requests, setRequests] = useState<ProcessingPage<Registration>>(emptyPage),
    [unassigned, setUnassigned] =
      useState<ProcessingPage<TeamPerson>>(emptyPage);
  const [page, setPage] = useState(0),
    [directoryPage, setDirectoryPage] = useState(0),
    [requestPage, setRequestPage] = useState(0),
    [refresh, setRefresh] = useState(0),
    [error, setError] = useState(""),
    [notice, setNotice] = useState(""),
    [loading, setLoading] = useState(true),
    [busy, setBusy] = useState(false),
    [menu, setMenu] = useState(false),
    [status, setStatus] = useState(""),
    [staffQuery, setStaffQuery] = useState(""),
    [staffFilter, setStaffFilter] = useState(""),
    [presetStaff, setPresetStaff] = useState(""),
    [selectedTask, setSelectedTask] = useState<number | null>(null),
    [attachStaff, setAttachStaff] = useState(""),
    [attachReason, setAttachReason] = useState(""),
    [dirtyTask, setDirtyTask] = useState(false);
  const [decision, setDecision] = useState<{
      title: string;
      path: string;
      method: string;
      body: Record<string, unknown>;
    } | null>(null),
    [reason, setReason] = useState("");
  const level = summary?.level;
  const supervisor = level === "ADMIN" || level === "TEAM_LEAD";
  const selectedTeam = teams.find((t) => String(t.id) === teamId);
  const activeTeamCount = teams.filter((t) => t.active && (!teamId || String(t.id) === teamId)).length;
  const dirtyRef = useRef(false);
  useEffect(() => { dirtyRef.current = dirtyTask; }, [dirtyTask]);
  const previousRoute = useRef(window.location.hash);
  const refreshAll = useCallback(() => setRefresh((v) => v + 1), []);
  useEffect(() => {
    document.body.classList.add("workspace-open");
    if (!window.location.hash.startsWith("#/processing-teams"))
      window.location.hash = "/processing-teams";
    previousRoute.current = window.location.hash;
    return () => document.body.classList.remove("workspace-open");
  }, []);
  useEffect(() => {
    const poll = setInterval(() => {
      if (document.visibilityState === "visible") refreshAll();
    }, 15000);
    const focus = () => {
      if (document.visibilityState === "visible") refreshAll();
    };
    document.addEventListener("visibilitychange", focus);
    return () => {
      clearInterval(poll);
      document.removeEventListener("visibilitychange", focus);
    };
  }, [refreshAll]);
  useEffect(() => {
    const changed = () => {
      if (
        !window.location.hash.startsWith("#/processing-teams") &&
        dirtyRef.current &&
        !window.confirm("Leave the unsaved task response?")
      )
        window.history.pushState(null, "", previousRoute.current);
    };
    window.addEventListener("hashchange", changed);
    return () => window.removeEventListener("hashchange", changed);
  }, []);
  useEffect(() => {
    const c = new AbortController();
    const suffix = teamId ? `?teamId=${teamId}` : "";
    setLoading(true);
    Promise.all([
      apiRequest<ProcessingSummary>(
        `/api/processing-teams/overview${suffix}`,
        "GET",
        undefined,
        c.signal,
      ),
      apiRequest<ProcessingTeam[]>(
        "/api/processing-teams/teams",
        "GET",
        undefined,
        c.signal,
      ),
      apiRequest<ProcessingPage<WorkAssignment>>(
        `/api/processing-teams/work?page=${page}&size=20${teamId ? "&teamId=" + teamId : ""}${status ? "&status=" + status : ""}${staffFilter ? "&employeeStaffCode=" + encodeURIComponent(staffFilter) : ""}`,
        "GET",
        undefined,
        c.signal,
      ),
    ])
      .then(([s, t, w]) => {
        if (c.signal.aborted) return;
        setSummary(s);
        setTeams(t);
        setWork(w);
        if (s.level !== "ADMIN" && !teamId && t[0])
          setTeamId(String(t.find((team) => team.active)?.id || t[0].id));
      })
      .catch((e) => {
        if (!c.signal.aborted) setError(e.message);
      })
      .finally(() => {
        if (!c.signal.aborted) setLoading(false);
      });
    return () => c.abort();
  }, [teamId, page, status, staffFilter, refresh]);
  useEffect(() => {
    if (!supervisor) return;
    const c = new AbortController();
    if (tab === "employees" && teamId)
      apiRequest<ProcessingPage<Member>>(
        `/api/processing-teams/teams/${teamId}/members?page=${directoryPage}`,
        "GET",
        undefined,
        c.signal,
      )
        .then((d) => {
          if (!c.signal.aborted) setMembers(d);
        })
        .catch((e) => {
          if (!c.signal.aborted) setError(e.message);
        });
    if (tab === "approvals")
      apiRequest<ProcessingPage<Registration>>(
        `/api/processing-teams/registrations?page=${requestPage}${teamId ? "&teamId=" + teamId : ""}`,
        "GET",
        undefined,
        c.signal,
      )
        .then((d) => {
          if (!c.signal.aborted) setRequests(d);
        })
        .catch((e) => {
          if (!c.signal.aborted) setError(e.message);
        });
    if (tab === "employees" && level === "ADMIN")
      apiRequest<ProcessingPage<TeamPerson>>(
        "/api/processing-teams/unassigned-employees?size=50",
        "GET",
        undefined,
        c.signal,
      )
        .then((d) => {
          if (!c.signal.aborted) setUnassigned(d);
        })
        .catch((e) => {
          if (!c.signal.aborted) setError(e.message);
        });
    return () => c.abort();
  }, [tab, teamId, directoryPage, requestPage, refresh, supervisor, level]);
  async function mutate(path: string, method: string, body: unknown) {
    setBusy(true);
    setError("");
    setNotice("");
    try {
      await apiRequest(path, method, body);
      refreshAll();
      setNotice(
        "Saved successfully. Access and PDF evidence are scoped to the selected team and employee.",
      );
      return true;
    } catch (e) {
      setError(e instanceof Error ? e.message : "Action failed.");
      return false;
    } finally {
      setBusy(false);
    }
  }
  function navigate(next: Tab) {
    if (dirtyTask && !window.confirm("Leave the unsaved task response?"))
      return;
    setDirtyTask(false);
    setSelectedTask(null);
    setTab(next);
    setMenu(false);
    setError("");
    setNotice("");
  }
  function chooseTeam(id: string) {
    if (dirtyTask && !window.confirm("Leave the unsaved task response?"))
      return;
    setDirtyTask(false);
    setTeamId(id);
    setPage(0);
    setDirectoryPage(0);
    setRequestPage(0);
    setSelectedTask(null);
    setStaffFilter("");
    setStaffQuery("");
    setError("");
  }
  const home =
    user?.role === "ROLE_ADMIN"
      ? "#/workspace/admin/overview"
      : user?.role === "ROLE_SCOPED_ADMIN"
        ? "#/workspace/role-administration/overview"
        : "#/workspace/case_processor/overview";
  const title =
    !level ? "Case Processing workspace" : level === "ADMIN"
      ? "Case Processing Super Admin"
      : level === "TEAM_LEAD"
        ? "Team Lead workspace"
        : "My Case Processing work";
  const workTable = (
    <section className="ws-card">
      <div className="ws-section-heading">
        <div>
          <h2>
            {level === "EMPLOYEE"
              ? "My assignments"
              : "Team work & employee responses"}
          </h2>
          <p>
            Most recently updated first. Each assignment belongs to one
            employee.
          </p>
        </div>
        <span>{work.totalElements} assignments</span>
      </div>
      {supervisor && (
        <form
          className="cp-work-filter"
          onSubmit={(e) => {
            e.preventDefault();
            setPage(0);
            setStaffFilter(staffQuery.trim().toUpperCase());
          }}
        >
          <label>
            Filter by employee staff ID
            <input
              name="employeeStaffCode"
              placeholder="PS-17"
              value={staffQuery}
              onChange={(e) => setStaffQuery(e.target.value)}
              maxLength={32}
            />
          </label>
          <button>Find employee work</button>
          <button
            type="button"
            onClick={() => {
              setStaffFilter("");
              setStaffQuery("");
              setPage(0);
            }}
          >
            Clear employee filter
          </button>
        </form>
      )}
      <label className="cp-status-filter">
        Work status
        <select
          value={status}
          onChange={(e) => {
            setStatus(e.target.value);
            setPage(0);
          }}
        >
          <option value="">All statuses</option>
          {Object.entries(workStatusLabels).map(([v, label]) => (
            <option key={v} value={v}>
              {label}
            </option>
          ))}
        </select>
      </label>
      <div className="ws-table-wrap">
        <table>
          <thead>
            <tr>
              {[
                "Assignment",
                "Employee / staff ID",
                "Team",
                "Stage",
                "Due",
                "Progress",
                "Action",
              ].map((x) => (
                <th key={x}>{x}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {work.content.map((w) => (
              <tr key={w.id}>
                <td>
                  <strong>{w.title}</strong>
                  <small className="cp-row-meta">
                    CP-WORK-{w.id} · {w.priority}
                  </small>
                </td>
                <td>
                  {w.employee.fullName}
                  <small className="cp-row-meta">{w.employee.staffCode}</small>
                </td>
                <td>{w.teamCode}</td>
                <td>
                  {processingStages[w.stage as keyof typeof processingStages]}
                </td>
                <td>
                  {dateTime(w.dueAt)}
                  {w.overdue && (
                    <span className="ws-badge danger">Overdue</span>
                  )}
                </td>
                <td>
                  <span
                    className="cp-state-pill"
                    style={{
                      background: statusColors[w.status] + "18",
                      color: statusColors[w.status],
                    }}
                  >
                    {workStatusLabels[w.status]}
                  </span>
                  <small className="cp-row-meta">{w.progress}% complete</small>
                </td>
                <td>
                  <button
                    onClick={() => {
                      setSelectedTask(w.id);
                      setError("");
                    }}
                  >
                    Open assignment
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {!loading && !work.content.length && (
        <div className="cp-empty">
          <ClipboardList size={28} />
          <h3>No matching work yet</h3>
          <p>
            {level === "EMPLOYEE"
              ? "Assignments from your Team Lead will appear here."
              : "Assign an individual task or the same instructions to the whole team."}
          </p>
        </div>
      )}
      <div className="ws-actions">
        <button
          disabled={page === 0 || loading}
          onClick={() => setPage((v) => v - 1)}
        >
          Previous work
        </button>
        <span>Page {page + 1}</span>
        <button
          disabled={page + 1 >= work.totalPages || loading}
          onClick={() => setPage((v) => v + 1)}
        >
          Next work
        </button>
      </div>
    </section>
  );
  return (
    <div
      className="ws-shell cp-team-shell"
      data-validation-context="processing"
      data-testid={
        !level ? "processing-loading" : level === "ADMIN"
          ? "processing-admin-home"
          : level === "TEAM_LEAD"
            ? "processing-lead-home"
            : "processing-employee-home"
      }
    >
      <aside className={`ws-sidebar ${menu ? "is-open" : ""}`}>
        <a className="ws-brand" href="#/processing-teams">
          PharmaSafe<span>CASE PROCESSING</span>
        </a>
        <p>
          {level === "ADMIN"
            ? "Scope: Case Processing teams"
            : selectedTeam
              ? `${selectedTeam.teamCode} · ${selectedTeam.name}`
              : "Team work & evidence"}
        </p>
        <nav aria-label="Case Processing team navigation">
          {tabItems
            .filter((item) => item.id !== "teams" || level === "ADMIN")
            .filter(
              (item) => supervisor || ["dashboard", "work"].includes(item.id),
            )
            .map(({ id, label, Icon }) => (
              <button
                key={id}
                aria-current={tab === id ? "page" : undefined}
                onClick={() => navigate(id)}
              >
                <Icon size={17} />
                <span>
                  {level === "EMPLOYEE" && id === "work"
                    ? "My work & PDFs"
                    : label}
                </span>
                {id === "approvals" && !!summary?.pendingRequests && (
                  <b className="cp-nav-count">{summary.pendingRequests}</b>
                )}
              </button>
            ))}
        </nav>
        {user?.role !== "ROLE_CASE_TEAM_LEAD" && (
          <a
            className="cp-sidebar-link"
            href={home}
            onClick={(e) => {
              if (
                dirtyTask &&
                !window.confirm("Leave the unsaved task response?")
              )
                e.preventDefault();
            }}
          >
            Clinical / role workspace <ArrowUpRight size={15} />
          </a>
        )}
        <div className="ws-person">
          <strong>{user?.fullName || user?.username}</strong>
          <span>
            {user?.staffCode} · @{user?.username}
          </span>
          <span>
            {level === "ADMIN"
              ? "Case Processing Admin"
              : level === "TEAM_LEAD"
                ? "Team Lead"
                : "Case Processing employee"}
          </span>
          <button
            onClick={() => {
              if (
                !dirtyTask ||
                window.confirm(
                  "Sign out and discard the unsaved task response?",
                )
              )
                void logout();
            }}
          >
            <LogOut size={15} /> Log Out
          </button>
        </div>
      </aside>
      {menu && (
        <button
          className="ws-drawer-overlay"
          aria-label="Close navigation"
          onClick={() => setMenu(false)}
        />
      )}
      <div className="ws-main">
        <header className="ws-topbar">
          <button
            className="ws-menu-toggle"
            aria-label="Toggle navigation"
            onClick={() => setMenu((v) => !v)}
          >
            <Menu size={19} />
          </button>
          <span>
            Case Processing / {tabItems.find((i) => i.id === tab)?.label}
          </span>
          <span className="cp-live">
            <i />
            {loading ? "Updating…" : "Live · 15s refresh"}
          </span>
          <button
            disabled={busy || loading}
            onClick={() => {
              setError("");
              refreshAll();
            }}
          >
            <RefreshCw size={15} /> Refresh team data
          </button>
        </header>
        <main className="ws-content">
          <div className="ws-heading">
            <div>
              <p className="cp-eyebrow">
                <ShieldCheck size={13} /> TEAM-BASED CASE OPERATIONS
              </p>
              <h1>{title}</h1>
              <p>
                {level === "ADMIN"
                  ? "Follow Team Leads, approve registrations and inspect every employee’s work within Case Processing."
                  : level === "TEAM_LEAD"
                    ? "Manage your employees, assign work by staff ID and review their responses."
                    : "Read your assigned work, record progress and exchange preserved PDF evidence with your Team Lead."}
              </p>
            </div>
            {level === "ADMIN" && (
              <label>
                Team dashboard
                <select
                  aria-label="Team dashboard"
                  value={teamId}
                  onChange={(e) => chooseTeam(e.target.value)}
                >
                  <option value="">All Case Processing teams</option>
                  {teams.map((t) => (
                    <option key={t.id} value={t.id}>
                      {t.teamCode} · {t.name}
                    </option>
                  ))}
                </select>
              </label>
            )}
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
          {summary && (
            <div className="cp-kpis">
              {[
                ["Assigned work", summary.totalAssignments, "blue"],
                ["Completed", summary.statusCounts.COMPLETED || 0, "green"],
                [
                  "Pending information",
                  summary.statusCounts.BLOCKED || 0,
                  "amber",
                ],
                ["Overdue", summary.overdue, "rose"],
              ].map(([label, value, color]) => (
                <div key={label} className={`cp-kpi cp-${color}`}>
                  <span>{label}</span>
                  <strong>{value}</strong>
                  <small>From your authorized work records</small>
                </div>
              ))}
            </div>
          )}
          {selectedTask !== null && level ? (
            <ProcessingTaskDetail
              key={selectedTask}
              id={selectedTask}
              level={level}
              onClose={() => {
                setSelectedTask(null);
                setDirtyTask(false);
              }}
              onUpdated={refreshAll}
              onDirtyChange={setDirtyTask}
            />
          ) : (
            <>
              {tab === "dashboard" && summary && (
                <>
                  <div className="cp-dashboard-grid">
                    <StatusChart
                      summary={summary}
                      status={status}
                      onSelect={(v) => {
                        setStatus(v);
                        setPage(0);
                        setTab("work");
                      }}
                    />
                    <section className="ws-card cp-team-chart">
                      <p className="cp-eyebrow">TEAM DELIVERY</p>
                      <h2>
                        {level === "EMPLOYEE"
                          ? "My team context"
                          : "Work by Team Lead"}
                      </h2>
                      {!summary.teams.length && (
                        <p>
                          No team assigned yet. Contact the Case Processing
                          Admin.
                        </p>
                      )}
                      {summary.teams.map((t) => (
                        <button
                          key={t.teamId}
                          onClick={() => {
                            chooseTeam(String(t.teamId));
                            setTab("work");
                          }}
                        >
                          <span>
                            <strong>{t.teamCode}</strong>
                            <small>{t.name}</small>
                            <b>
                              {t.completed} / {t.assigned} complete
                            </b>
                          </span>
                          <i>
                            <em
                              style={{
                                width: `${t.assigned ? (t.completed / t.assigned) * 100 : 0}%`,
                              }}
                            />
                          </i>
                        </button>
                      ))}
                      <p>
                        Completion is based on stored task responses. Clinical
                        approval is tracked separately.
                      </p>
                    </section>
                  </div>
                  {supervisor && (
                    <div className="cp-operation-strip">
                      <span>
                        <strong>{activeTeamCount}</strong>{" "}
                        active {activeTeamCount === 1 ? "team" : "teams"} in scope
                      </span>
                      <span>
                        <strong>{summary.activeEmployees}</strong> approved
                        active employees
                      </span>
                      <span>
                        <strong>{summary.pendingRequests}</strong> registration
                        requests awaiting review
                      </span>
                    </div>
                  )}
                  {workTable}
                </>
              )}
              {(tab === "teams" || (tab === "dashboard" && supervisor)) && (
                <>
                  {tab === "teams" && level === "ADMIN" && (
                    <CreateTeamForm
                      busy={busy || teams.filter((t) => t.active).length >= 10}
                      onCreate={(body) =>
                        mutate("/api/processing-teams/teams", "POST", body)
                      }
                    />
                  )}
                  <section
                    className="cp-team-cards"
                    aria-label="Case Processing teams"
                  >
                    {teams
                      .filter((t) => !teamId || String(t.id) === teamId)
                      .map((t) => {
                        const link =
                          window.location.origin +
                          window.location.pathname +
                          t.registrationPath;
                        return (
                          <article className="ws-card" key={t.id}>
                            <div className="ws-section-heading">
                              <span className="cp-team-code">{t.teamCode}</span>
                              <span className="ws-badge">
                                {t.active ? "Active" : "Inactive"}
                              </span>
                            </div>
                            <h2>{t.name}</h2>
                            <p>
                              Team Lead: <strong>{t.lead.fullName}</strong>
                              <br />
                              {t.lead.staffCode} · @{t.lead.username}
                            </p>
                            <div className="cp-seat-meter">
                              <span>
                                {t.occupiedSeats} / {t.maxMembers} employee
                                seats reserved or active
                              </span>
                              <i>
                                <em
                                  style={{
                                    width: `${Math.min(100, (t.occupiedSeats / t.maxMembers) * 100)}%`,
                                  }}
                                />
                              </i>
                            </div>
                            <p className="cp-muted">Team Lead ID for employee registration: <strong>{t.teamCode}</strong></p>
                            <label>
                              Employee registration link
                              <input
                                aria-label={`Registration link for ${t.teamCode}`}
                                readOnly
                                value={link}
                                onFocus={(e) => e.target.select()}
                              />
                            </label>
                            <div className="ws-actions">
                              <button
                                onClick={() => {
                                  void navigator.clipboard
                                    ?.writeText(link)
                                    .then(() =>
                                      setNotice("Registration link copied."),
                                    )
                                    .catch(() =>
                                      setNotice(
                                        "Select the link above and copy it.",
                                      ),
                                    );
                                }}
                              >
                                Copy registration link
                              </button>
                              <a className="cp-inline-link" href={link} target="_blank" rel="noopener noreferrer">Open employee registration</a>
                              <button
                                onClick={() => {
                                  chooseTeam(String(t.id));
                                  setTab("employees");
                                }}
                              >
                                Open team employees
                              </button>
                            </div>
                            {level === "ADMIN" && (
                              <div className="ws-actions">
                                <button
                                  onClick={() => {
                                    setReason("");
                                    setDecision({
                                      title: t.registrationOpen
                                        ? "Pause employee registration"
                                        : "Open employee registration",
                                      path: `/api/processing-teams/teams/${t.id}`,
                                      method: "PUT",
                                      body: {
                                        expectedVersion: t.rowVersion,
                                        active: t.active,
                                        registrationOpen: !t.registrationOpen,
                                      },
                                    });
                                  }}
                                  disabled={!t.active}
                                >
                                  {t.registrationOpen
                                    ? "Pause registration"
                                    : "Open registration"}
                                </button>
                                <button
                                  onClick={() => {
                                    setReason("");
                                    setDecision({
                                      title: t.active
                                        ? "Deactivate team"
                                        : "Activate team",
                                      path: `/api/processing-teams/teams/${t.id}`,
                                      method: "PUT",
                                      body: {
                                        expectedVersion: t.rowVersion,
                                        active: !t.active,
                                        registrationOpen: !t.active,
                                      },
                                    });
                                  }}
                                >
                                  {t.active
                                    ? "Deactivate team"
                                    : "Activate team"}
                                </button>
                              </div>
                            )}
                            <small className="cp-muted">
                              {t.active && t.registrationOpen
                                ? "Registrations require Team Lead or Case Processing Admin approval."
                                : "New registrations are closed."}
                            </small>
                          </article>
                        );
                      })}
                  </section>
                  {!teams.length && (
                    <section className="ws-card cp-empty">
                      <Users size={28} />
                      <h2>Create the first Case Processing team</h2>
                      <p>
                        No Team Lead accounts or employee groups are invented.
                        Use Team Leads & teams to create approved Lead logins
                        and registration links.
                      </p>
                    </section>
                  )}
                </>
              )}
              {tab === "employees" &&
                (selectedTeam ? (
                  <>
                    <section className="ws-card">
                      <h2>{selectedTeam.teamCode} employee directory</h2>
                      <p>
                        Use the permanent PS staff ID when assigning work.
                        Pending registrations reserve a seat until approved or
                        rejected.
                      </p>
                      <div className="ws-table-wrap">
                        <table>
                          <thead>
                            <tr>
                              {[
                                "Employee",
                                "Staff ID",
                                "Username",
                                "Membership",
                                "Account",
                                "Work",
                              ].map((x) => (
                                <th key={x}>{x}</th>
                              ))}
                            </tr>
                          </thead>
                          <tbody>
                            {members.content.map((m) => (
                              <tr key={m.userId}>
                                <td>{m.fullName}</td>
                                <td>
                                  <strong>{m.staffCode}</strong>
                                </td>
                                <td>{m.username}</td>
                                <td>{m.membershipStatus}</td>
                                <td>{m.accountStatus}</td>
                                <td>
                                  <button
                                    onClick={() => {
                                      setStaffFilter(m.staffCode);
                                      setStaffQuery(m.staffCode);
                                      setPage(0);
                                      setTab("work");
                                    }}
                                  >
                                    View employee work
                                  </button>
                                  {m.membershipStatus === "ACTIVE" && (
                                    <button
                                      onClick={() => {
                                        setPresetStaff(m.staffCode);
                                        setTab("assign");
                                      }}
                                    >
                                      Assign by staff ID
                                    </button>
                                  )}
                                </td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </div>
                      {!members.content.length && (
                        <p>
                          No employees yet. Share this team's registration link
                          or attach an approved existing employee.
                        </p>
                      )}
                      <div className="ws-actions">
                        <button
                          disabled={directoryPage === 0}
                          onClick={() => setDirectoryPage((v) => v - 1)}
                        >
                          Previous employees
                        </button>
                        <span>
                          Page {directoryPage + 1} · {members.totalElements}{" "}
                          employees
                        </span>
                        <button
                          disabled={directoryPage + 1 >= members.totalPages}
                          onClick={() => setDirectoryPage((v) => v + 1)}
                        >
                          Next employees
                        </button>
                      </div>
                    </section>
                    {level === "ADMIN" && (
                      <form
                        className="ws-card ws-form"
                        inert={busy || undefined}
                        onSubmit={(e) => {
                          e.preventDefault();
                          void mutate(
                            `/api/processing-teams/teams/${selectedTeam.id}/members`,
                            "POST",
                            {
                              employeeStaffCode: attachStaff,
                              reason: attachReason,
                            },
                          ).then((ok) => {
                            if (ok) {
                              setAttachStaff("");
                              setAttachReason("");
                            }
                          });
                        }}
                      >
                        <h2>Attach an existing approved Case Processor</h2>
                        <p>
                          Only the Case Processing Admin can place existing
                          employees into a team. An employee cannot occupy two
                          active teams.
                        </p>
                        <label>
                          Existing employee staff ID
                          <input
                            name="employeeStaffCode"
                            list="cp-unassigned-employees"
                            required
                            pattern="PS-[1-9][0-9]{0,17}"
                            value={attachStaff}
                            onChange={(e) =>
                              setAttachStaff(e.target.value.toUpperCase())
                            }
                          />
                          <datalist id="cp-unassigned-employees">
                            {unassigned.content.map((u) => (
                              <option key={u.userId} value={u.staffCode}>
                                {u.fullName} · @{u.username}
                              </option>
                            ))}
                          </datalist>
                          <small>
                            Suggestions show up to 50 approved employees without
                            an active team. Any valid staff ID is checked on the
                            server.
                          </small>
                        </label>
                        <label>
                          Team placement reason
                          <textarea
                            name="reason"
                            required
                            maxLength={2000}
                            value={attachReason}
                            onChange={(e) => setAttachReason(e.target.value)}
                          />
                        </label>
                        <button
                          disabled={
                            busy ||
                            selectedTeam.occupiedSeats >=
                              selectedTeam.maxMembers
                          }
                        >
                          Add employee to team
                        </button>
                      </form>
                    )}
                  </>
                ) : (
                  <section className="ws-card">
                    <h2>Select a team</h2>
                    <p>
                      Choose a Team Lead from the Team dashboard selector to
                      inspect its employees.
                    </p>
                  </section>
                ))}
              {tab === "assign" &&
                (selectedTeam ? (
                  <AssignWorkForm
                    key={`${selectedTeam.id}-${presetStaff}`}
                    team={selectedTeam}
                    staffCode={presetStaff}
                    busy={busy}
                    onAssign={async (body) => {
                      const ok = await mutate(
                        "/api/processing-teams/work",
                        "POST",
                        body,
                      );
                      if (ok) {
                        setNotice(
                          "Work assigned. Individual instruction PDFs are ready in the recipients’ worklists.",
                        );
                        setTab("work");
                      }
                      return ok;
                    }}
                  />
                ) : (
                  <section className="ws-card">
                    <h2>Choose a team before assigning work</h2>
                    <p>Use the Team dashboard selector above.</p>
                  </section>
                ))}
              {tab === "approvals" && (
                <section className="ws-card">
                  <h2>Team registration requests</h2>
                  <p>
                    A Team Lead can decide requests for their own team. The Case
                    Processing Admin can decide requests across all Case
                    Processing teams.
                  </p>
                  <div className="cp-registration-list">
                    {requests.content.map((r) => (
                      <article key={r.id}>
                        <div className="ws-section-heading">
                          <h3>
                            {r.fullName} · {r.staffCode}
                          </h3>
                          <span className="ws-badge">{r.accountStatus}</span>
                        </div>
                        <p>
                          {r.teamCode} · @{r.username} · {r.email} · employee
                          number {r.employeeId}
                        </p>
                        <p className="cp-preserve">{r.reason}</p>
                        <dl className="cp-qualification-list">
                          {Object.entries(r.details).map(([k, v]) => (
                            <div key={k}>
                              <dt>{k.replace(/([a-z])([A-Z])/g, "$1 $2")}</dt>
                              <dd>{v}</dd>
                            </div>
                          ))}
                        </dl>
                        {r.accountStatus === "PENDING" && (
                          <div className="ws-actions">
                            {["approve", "reject"].map((action) => (
                              <button
                                disabled={busy}
                                key={action}
                                onClick={() => {
                                  setReason("");
                                  setDecision({
                                    title: `${action === "approve" ? "Approve" : "Reject"} ${r.staffCode}`,
                                    path: `/api/processing-teams/registrations/${r.id}/${action}`,
                                    method: "POST",
                                    body: {},
                                  });
                                }}
                              >
                                {action === "approve"
                                  ? "Approve employee"
                                  : "Reject request"}
                              </button>
                            ))}
                          </div>
                        )}
                      </article>
                    ))}
                  </div>
                  {!requests.content.length && (
                    <p>No team-linked registrations yet.</p>
                  )}
                  <div className="ws-actions">
                    <button
                      disabled={requestPage === 0}
                      onClick={() => setRequestPage((v) => v - 1)}
                    >
                      Previous registrations
                    </button>
                    <span>
                      Page {requestPage + 1} · {requests.totalElements} requests
                    </span>
                    <button
                      disabled={requestPage + 1 >= requests.totalPages}
                      onClick={() => setRequestPage((v) => v + 1)}
                    >
                      Next registrations
                    </button>
                  </div>
                </section>
              )}
              {tab === "work" && workTable}
            </>
          )}
          {summary && (
            <p className="cp-refresh-note">
              Updated {dateTime(summary.updatedAt)}. Dashboards refresh every 15
              seconds while this tab is visible; unsaved progress forms are
              preserved.
            </p>
          )}
        </main>
      </div>
      {decision && (
        <AccessDialog
          title={decision.title}
          blocked={busy}
          onClose={() => setDecision(null)}
        >
          <form
            className="cp-decision-form"
            onSubmit={(e) => {
              e.preventDefault();
              void mutate(decision.path, decision.method, {
                ...decision.body,
                reason,
              }).then((ok) => {
                if (ok) setDecision(null);
              });
            }}
          >
            {decision.title === "Deactivate team" && (
              <p>
                Deactivation signs out its Team Lead, closes new registrations
                and pauses employee task updates. Existing work and PDFs remain
                preserved.
              </p>
            )}
            <label>
              Decision reason
              <textarea
                name="reason"
                required
                maxLength={2000}
                value={reason}
                onChange={(e) => setReason(e.target.value)}
              />
            </label>
            <button disabled={busy}>Confirm decision</button>
          </form>
        </AccessDialog>
      )}
    </div>
  );
}
