import { PvAnalysisWorkspace } from './PvAnalysisWorkspace';
import { ReportEvidencePanel } from "./ReportEvidencePanel";
import { RoleInsights, RoleOverviewPreview } from "./RoleInsights";
import { useEffect, useRef, useState } from "react";
import {
  LayoutDashboard,
  Users,
  ClipboardCheck,
  KeyRound,
  ShieldCheck,
  ListTodo,
  ArrowUpRight,
  UserRoundCog,
} from "lucide-react";
import { useAuth } from "../context/AuthContext";
import { apiRequest } from "../config/api";
import { AccessDialog } from "./AccessDialog";
import { dateTime, text, type Page, type Row } from "./model";
import catalog from "../config/publicRoleCatalog.json";
import "./UserAccessManagement.css";
import "./RoleAdministration.css";
type RoleSummary = {
  role: string;
  name: string;
  totalUsers: number;
  caseRecords?: number;
  reportRecords?: number;
  pending: number;
  approved: number;
  rejected: number;
  suspended: number;
  pendingRequests: number;
  pendingResetRequests: number;
  assignmentVersion: number;
  administrator: Row | null;
  assignedAt: string | null;
};
type Summary = {
  administrativeLevel: string;
  managedRole: string;
  totalUsers: number;
  totalRoleAdmins?: number;
  activeRoleAdmins?: number;
  pending: number;
  approved: number;
  rejected: number;
  suspended: number;
  roles: RoleSummary[];
};
type Tab =
  | "overview"
  | "users"
  | "registrations"
  | "password-resets"
  | "assignments"
  | "work"
  | "audit"
  | "activity"
  | "report-work"
  | "pv-analysis";
const roleName = (code: unknown) =>
  catalog.find((r) => r.role === code)?.name ||
  (code === "ROLE_SCOPED_ADMIN"
    ? "Role Admin"
    : code === "ROLE_ADMIN"
      ? "Super Admin"
      : text(code));
export const employeeLabel = (row: Row) =>
  `${text(row.fullName)} · ${text(row.staffCode)} · @${text(row.username)}`;
const empty: Page<Row> = { content: [], totalElements: 0, totalPages: 0 };
function RoleCards({
  roles,
  onOpen,
}: {
  roles: RoleSummary[];
  onOpen: (role: string) => void;
}) {
  return (
    <div className="admin-role-grid">
      {roles.map((r) => (
        <article key={r.role} className="ws-card admin-role-card">
          <div className="admin-role-heading">
            <span>
              <Users size={20} />
            </span>
            <h3>{r.name}</h3>
          </div>
          <p className="admin-role-owner">
            {r.administrator ? (
              <>
                Admin: <strong>{text(r.administrator.fullName)}</strong>
                <small>
                  {text(r.administrator.staffCode)} · @
                  {text(r.administrator.username)}
                </small>
              </>
            ) : (
              <span>No Role Admin assigned</span>
            )}
          </p>
          <div className="admin-role-counts">
            {[
              ["Users", r.totalUsers],
              ["Pending requests", r.pendingRequests],
              ["Approved", r.approved],
              ["Rejected", r.rejected],
              ["Suspended", r.suspended],
              [
                r.role === "ROLE_REPORTING_SPECIALIST"
                  ? "Report work records"
                  : "Case work records",
                (r.role === "ROLE_REPORTING_SPECIALIST"
                  ? r.reportRecords
                  : r.caseRecords) ?? "—",
              ],
            ].map(([label, value]) => (
              <div key={label}>
                <span>{label}</span>
                <strong>{value}</strong>
              </div>
            ))}
          </div>
          <button onClick={() => onOpen(r.role)}>
            Open role management <ArrowUpRight size={15} />
          </button>
        </article>
      ))}
    </div>
  );
}
export function AdminRoleDirectory({
  onOpen,
}: {
  onOpen: (role: string) => void;
}) {
  const [summary, setSummary] = useState<Summary | null>(null),
    [error, setError] = useState(""),
    [revision, setRevision] = useState(0);
  useEffect(() => {
    const c = new AbortController();
    apiRequest<Summary>(
      "/api/administration/overview",
      "GET",
      undefined,
      c.signal,
    )
      .then((data) => {
        if (!Array.isArray(data.roles))
          throw new Error("Role statistics are unavailable.");
        setSummary(data);
      })
      .catch((e) => {
        if (!c.signal.aborted) setError(e.message);
      });
    return () => c.abort();
  }, [revision]);
  return (
    <section
      className="admin-management"
      aria-label="Role-wise user management"
    >
      <div className="ws-section-heading">
        <div>
          <p className="premium-kicker">SUPER ADMIN</p>
          <h2>Role-wise user management</h2>
          <p>
            Employees, approvals and assigned administrators across your
            application.
          </p>
        </div>
      </div>
      {error ? (
        <p role="alert">
          {error}
          <button
            onClick={() => {
              setError("");
              setRevision((v) => v + 1);
            }}
          >
            Retry role statistics
          </button>
        </p>
      ) : summary ? (
        <RoleCards roles={summary.roles} onOpen={onOpen} />
      ) : (
        <p role="status">Loading role statistics…</p>
      )}
    </section>
  );
}
const navigation: { tab: Tab; label: string; Icon: typeof Users }[] = [
  { tab: "overview", label: "Dashboard", Icon: LayoutDashboard },
  { tab: "users", label: "My role users", Icon: Users },
  {
    tab: "registrations",
    label: "Registration requests",
    Icon: ClipboardCheck,
  },
  { tab: "password-resets", label: "Password reset requests", Icon: KeyRound },
  { tab: "pv-analysis", label: "PV analyst reports", Icon: ListTodo },
  { tab: "work", label: "Employee work", Icon: ListTodo },
  { tab: "report-work", label: "Reporting work", Icon: ListTodo },
  { tab: "audit", label: "Administrative audit", Icon: ShieldCheck },
  { tab: "activity", label: "Employee activity", Icon: ListTodo },
];
export function RoleAdministration({
  embedded = false,
  onDirtyChange,
}: {
  embedded?: boolean;
  onDirtyChange?: (v:boolean)=>void;
}) {
  const { user, logout } = useAuth(),
    superAdmin = user?.role === "ROLE_ADMIN";
  const [role, setRole] = useState(() =>
    superAdmin
      ? sessionStorage.getItem("pharmasafe-role-focus") || ""
      : user?.managedRole || "",
  );
  const [tab, setTab] = useState<Tab>(() =>
    !embedded
      ? navigation.find((n) => window.location.hash.endsWith("/" + n.tab))
          ?.tab || "overview"
      : "overview",
  );
  const [summary, setSummary] = useState<Summary | null>(null),
    [data, setData] = useState<Page<Row>>(empty),
    [eligible, setEligible] = useState<Row[]>([]);
  const [evidenceReport, setEvidenceReport] = useState<number | null>(null);
  const [page, setPage] = useState(0),
    [query, setQuery] = useState(""),
    [filters, setFilters] = useState<Record<string, string>>({}),
    [applied, setApplied] = useState<Record<string, string>>({}),
    [workUser, setWorkUser] = useState<Row | null>(null);
  useEffect(() => setEvidenceReport(null), [role, tab, workUser?.userId]);
  const [loading, setLoading] = useState(true),
    [saving, setSaving] = useState(false),
    [error, setError] = useState(""),
    [notice, setNotice] = useState(""),
    [refresh, setRefresh] = useState(0),
    [menu, setMenu] = useState(false);
  const [auditRecord, setAuditRecord] = useState<Row | null>(null);
  const [details, setDetails] = useState<Row | null>(null),
    [detailLoading, setDetailLoading] = useState(false),
    [detailError, setDetailError] = useState("");
  const [decision, setDecision] = useState<{
      row: Row;
      action: string;
      kind: Tab;
    } | null>(null),
    [reason, setReason] = useState("");
  const [assignmentMode, setAssignmentMode] = useState("create"),
    [form, setForm] = useState<Record<string, string>>({});
  const generation = useRef(0),
    detailGeneration = useRef(0),
    mutation = useRef(false);
  const [pvDirty,setPvDirty]=useState(false);
  useEffect(()=>{onDirtyChange?.(pvDirty);return()=>onDirtyChange?.(false);},[pvDirty,onDirtyChange]);
  const leavePv=()=>!pvDirty||window.confirm('Leave unsaved PV review comments?');
  const slot = summary?.roles.find((r) => r.role === role);
  const selectTab = (next: Tab) => {
    if(!leavePv())return;setPvDirty(false);
    setTab(next);
    setFilters({});
    setApplied({});
    setQuery("");
    setPage(0);
    setError("");
    setNotice("");
    setMenu(false);
    if (!embedded)
      window.history.replaceState(null, "", `#/workspace/role_admin/${next}`);
  };
  const selectRole = (next: string) => {
    if(!leavePv())return;setPvDirty(false);
    setRole(next);
    setPage(0);
    setForm({});
    setWorkUser(null);
    setFilters({});
    setApplied({});
    setQuery("");
    setError("");
  };
  useEffect(() => {
    if (embedded) return;
    document.body.classList.add("workspace-open");
    return () => document.body.classList.remove("workspace-open");
  }, [embedded]);
  useEffect(() => {
    const id = ++generation.current,
      c = new AbortController();
    setLoading(true);
    setError("");
    setData(empty);
    const params = new URLSearchParams({
      role,
      page: String(page),
      search: query,
      ...applied,
    });
    if (workUser && (tab === "work" || tab === "report-work"))
      params.set("userId", String(workUser.userId));
    const run = async () => {
      const summaryRequest = apiRequest<Summary>(
        `/api/administration/overview?role=${encodeURIComponent(role)}`,
        "GET",
        undefined,
        c.signal,
      );
      const listing =
        tab === "overview" || tab === "assignments" || tab === "pv-analysis"
          ? Promise.resolve(empty)
          : apiRequest<Page<Row>>(
              `/api/administration/${tab}?${params}`,
              "GET",
              undefined,
              c.signal,
            );
      const candidates =
        tab === "assignments" && role
          ? apiRequest<Page<Row>>(
              `/api/administration/eligible-administrators?role=${encodeURIComponent(role)}&size=50`,
              "GET",
              undefined,
              c.signal,
            )
          : Promise.resolve(empty);
      const [stats, records, choices] = await Promise.all([
        summaryRequest,
        listing,
        candidates,
      ]);
      if (id === generation.current && !c.signal.aborted) {
        setSummary(stats);
        setData(records);
        setEligible(choices.content);
      }
    };
    void run()
      .catch((e) => {
        if (!c.signal.aborted && id === generation.current) setError(e.message);
      })
      .finally(() => {
        if (!c.signal.aborted && id === generation.current) setLoading(false);
      });
    return () => c.abort();
  }, [role, tab, page, query, applied, workUser, refresh]);
  const mutate = async (path: string, method: string, body: unknown) => {
    if (mutation.current) return false;
    mutation.current = true;
    setSaving(true);
    setError("");
    try {
      await apiRequest(path, method, body);
      setNotice("Saved. The latest database records are shown below.");
      setRefresh((v) => v + 1);
      return true;
    } catch (e) {
      setError(
        e instanceof Error ? e.message : "The action could not be completed.",
      );
      return false;
    } finally {
      mutation.current = false;
      setSaving(false);
    }
  };
  const viewDetails = async (row: Row) => {
    const token = ++detailGeneration.current;
    setDetails(row);
    setDetailError("");
    setDetailLoading(true);
    try {
      const value = await apiRequest<Row>(
        `/api/administration/${tab === "registrations" ? "registrations" : "users"}/${tab === "registrations" ? row.id : row.userId}`,
      );
      if (token === detailGeneration.current) setDetails(value);
    } catch (e) {
      if (token === detailGeneration.current)
        setDetailError(e instanceof Error ? e.message : "Details unavailable.");
    } finally {
      if (token === detailGeneration.current) setDetailLoading(false);
    }
  };
  const decide = (row: Row, action: string) => {
    setReason("");
    setDecision({ row, action, kind: tab });
    setError("");
  };
  const showWork = (row: Row) => {
    setWorkUser(row);
    selectTab(
      row.role === "ROLE_PV_ANALYST" ? "pv-analysis" : row.role === "ROLE_REPORTING_SPECIALIST" ? "report-work" : "work",
    );
  };
  const Heading = embedded ? "h2" : "h1";
  const header = (
    <div className="admin-page-heading">
      <div>
        <p className="premium-kicker">
          {superAdmin ? "SUPER ADMIN" : "ROLE ADMIN"} · CONTROLLED ACCESS
        </p>
        <Heading>
          {superAdmin
            ? "Role-wise administration"
            : `${roleName(user?.managedRole || role)} administration`}
        </Heading>
        <p>
          {superAdmin
            ? "Manage role ownership and follow each employee’s work."
            : "Your users, approvals and work records are restricted to your assigned application role."}
        </p>
      </div>
      {(superAdmin||user?.managedRole==='ROLE_CASE_PROCESSOR')&&<a className="cp-team-launch" href="#/processing-teams">Processing teams & work</a>}
      <button
        disabled={loading || saving}
        onClick={() => setRefresh((v) => v + 1)}
      >
        Refresh administration
      </button>
    </div>
  );
  const metrics = summary ? (
    <div className="admin-metrics">
      {[
        ["Total users", summary.totalUsers],
        ...(superAdmin && summary.totalRoleAdmins != null
          ? [
              ["Role Admin accounts", summary.totalRoleAdmins],
              ["Assigned Role Admins", summary.activeRoleAdmins ?? "—"],
            ]
          : []),
        ["Pending users", summary.pending],
        ["Approved users", summary.approved],
        ["Rejected users", summary.rejected],
        ["Suspended users", summary.suspended],
      ].map(([label, value], i) => (
        <article
          key={String(label)}
          style={{
            borderTopColor: [
              "#5385c6",
              "#8e74c7",
              "#d3a554",
              "#44a58d",
              "#cf7e8c",
              "#7e8bb7",
            ][i % 6],
          }}
        >
          <span>{label}</span>
          <strong>{value}</strong>
        </article>
      ))}
    </div>
  ) : null;
  const actions = (row: Row) => (
    <div className="access-row-actions">
      {tab === "users" && (
        <>
          <button disabled={saving} onClick={() => void viewDetails(row)}>
            View details
          </button>
          {catalog.some((r) => r.role === row.role) ||
          row.role === "ROLE_USER" ? (
            <button onClick={() => showWork(row)}>View work</button>
          ) : null}
          {!["ROLE_ADMIN", "ROLE_SCOPED_ADMIN"].includes(String(row.role)) &&
            ["APPROVED", "SUSPENDED"].includes(String(row.accountStatus)) && (
              <button
                disabled={saving}
                onClick={() =>
                  decide(
                    row,
                    row.accountStatus === "SUSPENDED" ? "activate" : "suspend",
                  )
                }
              >
                {row.accountStatus === "SUSPENDED" ? "Activate" : "Suspend"}
              </button>
            )}
        </>
      )}
      {tab === "registrations" && (
        <>
          <button onClick={() => void viewDetails(row)}>View details</button>
          {row.accountStatus === "PENDING" && !row.reviewedAt && (
            <>
              <button disabled={saving} onClick={() => decide(row, "approve")}>
                Approve
              </button>
              <button disabled={saving} onClick={() => decide(row, "reject")}>
                Reject
              </button>
            </>
          )}
        </>
      )}
      {tab === "password-resets" && row.status === "PENDING" && (
        <>
          <button disabled={saving} onClick={() => decide(row, "approve")}>
            Approve reset
          </button>
          <button disabled={saving} onClick={() => decide(row, "reject")}>
            Reject reset
          </button>
        </>
      )}
    </div>
  );
  const filtersUI = ["users", "registrations", "audit"].includes(tab) ? (
    <form
      className="ws-card admin-filters"
      onSubmit={(e) => {
        e.preventDefault();
        setQuery(filters.search || "");
        setApplied(
          Object.fromEntries(
            Object.entries(filters).filter(([k, v]) => k !== "search" && v),
          ),
        );
        setPage(0);
      }}
    >
      <label>
        {tab === "audit"
          ? "Actor or administrative action"
          : "Name, staff code, username, employee ID or email"}
        <input
          type="search"
          maxLength={200}
          value={filters.search || ""}
          onChange={(e) =>
            setFilters((f) => ({ ...f, search: e.target.value }))
          }
        />
      </label>
      {tab !== "audit" && (
        <>
          <label>
            Account status
            <select
              aria-label="Account status"
              value={filters.status || ""}
              onChange={(e) =>
                setFilters((f) => ({ ...f, status: e.target.value }))
              }
            >
              <option value="">All statuses</option>
              {["PENDING", "APPROVED", "REJECTED", "SUSPENDED"].map((s) => (
                <option key={s}>{s}</option>
              ))}
            </select>
          </label>
          {["department", "organization"].map((k) => (
            <label key={k}>
              {k === "department" ? "Department" : "Organization"}
              <input
                maxLength={128}
                value={filters[k] || ""}
                onChange={(e) =>
                  setFilters((f) => ({ ...f, [k]: e.target.value }))
                }
              />
            </label>
          ))}
          {["from", "to"].map((k) => (
            <label key={k}>
              {k === "from" ? "Created from" : "Created through"}
              <input
                type="date"
                value={filters[k] || ""}
                onChange={(e) =>
                  setFilters((f) => ({ ...f, [k]: e.target.value }))
                }
              />
            </label>
          ))}
          <label>
            Sort by
            <select
              value={
                filters.sort ||
                (tab === "registrations" ? "createdAt" : "username")
              }
              onChange={(e) =>
                setFilters((f) => ({ ...f, sort: e.target.value }))
              }
            >
              {(tab === "registrations"
                ? ["createdAt", "fullName", "role", "status"]
                : ["username", "fullName", "createdAt", "accountStatus"]
              ).map((v) => (
                <option key={v} value={v}>
                  {v.replace(/([A-Z])/g, " $1")}
                </option>
              ))}
            </select>
          </label>
          <label>
            Order
            <select
              aria-label="Order"
              value={filters.direction || (tab === "users" ? "asc" : "desc")}
              onChange={(e) =>
                setFilters((f) => ({ ...f, direction: e.target.value }))
              }
            >
              <option value="desc">Newest / descending</option>
              <option value="asc">Oldest / ascending</option>
            </select>
          </label>
          {superAdmin && tab === "registrations" && (
            <label>
              Assigned Role Admin
              <select
                aria-label="Assigned Role Admin"
                value={filters.administratorId || ""}
                onChange={(e) =>
                  setFilters((f) => ({ ...f, administratorId: e.target.value }))
                }
              >
                <option value="">All assigned administrators</option>
                {summary?.roles
                  .filter((r) => r.administrator)
                  .map((r) => (
                    <option key={r.role} value={text(r.administrator!.userId)}>
                      {employeeLabel(r.administrator!)}
                    </option>
                  ))}
              </select>
            </label>
          )}
        </>
      )}
      <div className="ws-actions">
        <button disabled={loading || saving}>Apply filters</button>
        <button
          type="button"
          onClick={() => {
            setFilters({});
            setApplied({});
            setQuery("");
            setPage(0);
          }}
        >
          Clear filters
        </button>
      </div>
    </form>
  ) : null;
  const content = (
    <div
      className="admin-management"
      data-testid={superAdmin ? "super-admin-management" : "role-admin-home"}
    >
      {header}
      {tab === "report-work" && evidenceReport !== null && (
        <ReportEvidencePanel
          key={evidenceReport}
          reportId={evidenceReport}
          writer={superAdmin}
          onClose={() => setEvidenceReport(null)}
        />
      )}
      {superAdmin && (
        <div className="admin-role-selector">
          <label>
            Application role
            <select
              aria-label="Application role"
              value={role}
              onChange={(e) => selectRole(e.target.value)}
            >
              <option value="">All application roles</option>
              {catalog.map((r) => (
                <option key={r.role} value={r.role}>
                  {r.name}
                </option>
              ))}
            </select>
          </label>
          <p>
            Staff codes are unique and permanent. Existing usernames and
            employee names are preserved.
          </p>
        </div>
      )}
      {embedded && (
        <nav className="admin-tabs" aria-label="Role management modules">
          {[
            ...navigation,
            ...(superAdmin
              ? [
                  {
                    tab: "assignments" as Tab,
                    label: "Role Admin assignments",
                    Icon: UserRoundCog,
                  },
                ]
              : []),
          ].filter(n=>n.tab!=="pv-analysis"||role==="ROLE_PV_ANALYST").map((n) => (
            <button
              key={n.tab}
              aria-current={tab === n.tab ? "page" : undefined}
              onClick={() => selectTab(n.tab)}
            >
              {n.label === "My role users" ? "Role users" : n.label}
            </button>
          ))}
        </nav>
      )}
      {error && !decision && (
        <p className="ws-error" role="alert">
          {error}
        </p>
      )}
      {notice && (
        <p className="ws-notice" role="status">
          {notice}
        </p>
      )}
      {loading && <p role="status">Loading administration records…</p>}
      {metrics}
      {tab === "overview" && summary && (
        <>
          {!role ? (
            <RoleCards
              roles={summary.roles}
              onOpen={(r) => {
                selectRole(r);
                selectTab("overview");
              }}
            />
          ) : (
            <section className="ws-card insight-role-owner">
              <div>
                <p className="premium-kicker">ROLE OPERATIONS</p>
                <h3>{roleName(role)} dashboard</h3>
                <p>
                  {slot?.administrator
                    ? `Assigned Role Admin: ${employeeLabel(slot.administrator)}`
                    : "Super Admin is managing this role until a Role Admin is assigned."}
                </p>
              </div>
              <div className="ws-actions">
                <button onClick={() => selectTab("users")}>
                  Manage employees
                </button>
                <button
                  onClick={() => {
                    selectTab("registrations");
                    setApplied({ status: "PENDING" });
                    setFilters({ status: "PENDING" });
                  }}
                >
                  Pending approvals
                </button>
              </div>
            </section>
          )}
          <RoleInsights
            role={role}
            refresh={refresh}
            people={{
              Pending: summary.pending,
              Approved: summary.approved,
              Rejected: summary.rejected,
              Suspended: summary.suspended,
            }}
          />
          {role && (
            <RoleOverviewPreview
              role={role}
              reporting={role === "ROLE_REPORTING_SPECIALIST"}
              refresh={refresh}
              onEmployee={showWork}
              onUsers={() => selectTab("users")}
              onWork={() =>
                selectTab(
                  role === "ROLE_REPORTING_SPECIALIST" ? "report-work" : "work",
                )
              }
              onRequests={() => selectTab("registrations")}
            />
          )}
        </>
      )}
      {filtersUI}
      {tab === "assignments" &&
        superAdmin &&
        (!role ? (
          <p className="ws-empty">
            Select an application role to manage its primary administrator.
          </p>
        ) : (
          <section className="ws-card">
            <h3>{roleName(role)} · Primary Role Admin</h3>
            <p>
              {slot?.administrator
                ? `Current administrator: ${employeeLabel(slot.administrator)}`
                : "No administrator is currently assigned."}
            </p>
            <p className="admin-help">
              Replacing an administrator suspends the previous account and
              revokes its sessions. The reason and assignment history are
              retained.
            </p>
            <form
              className="admin-assignment-form"
              onSubmit={async (e) => {
                e.preventDefault();
                const path = `/api/administration/assignments/${role}`;
                const payload = {
                  ...form,
                  expectedVersion: slot?.assignmentVersion || 0,
                  ...(assignmentMode === "existing"
                    ? { userId: Number(form.userId) }
                    : {}),
                };
                if (
                  await mutate(
                    path,
                    assignmentMode === "existing" ? "PUT" : "POST",
                    payload,
                  )
                )
                  setForm({});
              }}
            >
              <label>
                Provisioning method
                <select
                  aria-label="Provisioning method"
                  value={assignmentMode}
                  onChange={(e) => {
                    setAssignmentMode(e.target.value);
                    setForm({});
                  }}
                >
                  <option value="create">
                    Create a dedicated Role Admin account
                  </option>
                  <option value="existing">
                    Assign an existing eligible account
                  </option>
                </select>
              </label>
              {assignmentMode === "create" ? (
                <>
                  {[
                    ["fullName", "Full name", "text"],
                    ["username", "Unique username", "text"],
                    ["email", "Official email", "email"],
                    ["password", "Initial password", "password"],
                  ].map(([key, label, type]) => (
                    <label key={key}>
                      {label}
                      <input
                        name={key}
                        type={type}
                        required
                        autoComplete={
                          type === "password" ? "new-password" : "off"
                        }
                        maxLength={
                          key === "email"
                            ? 254
                            : key === "username"
                              ? 64
                              : key === "password"
                                ? 72
                                : 128
                        }
                        minLength={key === "password" ? 12 : undefined}
                        value={form[key] || ""}
                        onChange={(e) =>
                          setForm((v) => ({ ...v, [key]: e.target.value }))
                        }
                      />
                    </label>
                  ))}
                  <p className="admin-help">
                    Provide the initial password privately. Use at least 12
                    characters with letters and a number or symbol; passwords
                    are stored only as hashes.
                  </p>
                </>
              ) : (
                <label>
                  Eligible employee
                  <select
                    aria-label="Eligible employee"
                    required
                    value={form.userId || ""}
                    onChange={(e) =>
                      setForm((v) => ({ ...v, userId: e.target.value }))
                    }
                  >
                    <option value="">Select an approved employee</option>
                    {eligible.map((u) => (
                      <option key={text(u.userId)} value={text(u.userId)}>
                        {employeeLabel(u)}
                      </option>
                    ))}
                  </select>
                  <small>
                    Up to 50 eligible accounts. Active administrators of another
                    role are excluded.
                  </small>
                </label>
              )}
              <label className="admin-wide">
                Assignment reason
                <textarea
                  required
                  maxLength={2000}
                  value={form.reason || ""}
                  onChange={(e) =>
                    setForm((v) => ({ ...v, reason: e.target.value }))
                  }
                />
              </label>
              <button className="ws-primary" disabled={saving || loading}>
                Save Role Admin assignment
              </button>
            </form>
            {slot?.administrator && (
              <button
                className="admin-release"
                disabled={saving}
                onClick={() => {
                  setReason("");
                  setDecision({
                    kind: "assignments",
                    action: "release",
                    row: slot.administrator!,
                  });
                }}
              >
                Release current assignment
              </button>
            )}
          </section>
        ))}
      {(tab === "work" || tab === "report-work") && (
        <div className="ws-card admin-work-heading">
          <div>
            <h3>
              {workUser
                ? `Work for ${text(workUser.fullName)}`
                : tab === "report-work"
                  ? "Role reporting work"
                  : "Role employee work"}
            </h3>
            <p>
              {workUser
                ? employeeLabel(workUser)
                : tab === "report-work"
                  ? "Internal report records created by employees in this role. This view does not transmit reports."
                  : "Cases created, assigned or clinically reviewed by employees in this role. Shared case owners outside the role are not exposed."}
            </p>
          </div>
          {workUser && (
            <button
              onClick={() => {
                setWorkUser(null);
                setPage(0);
              }}
            >
              Show all role work
            </button>
          )}
        </div>
      )}
      {workUser && (tab === "work" || tab === "report-work" || tab === "pv-analysis") && (
        <RoleInsights
          role={role}
          userId={Number(workUser.userId)}
          refresh={refresh}
        />
      )}
      {tab==="pv-analysis"&&<PvAnalysisWorkspace onDirtyChange={setPvDirty} employeeId={workUser?.userId as number|undefined}/>}
      {!["overview", "assignments", "pv-analysis"].includes(tab) && (
        <>
          <div className="ws-card">
            <div
              className="ws-table-wrap admin-table-wrap"
              role="region"
              aria-label="Role administration records"
              tabIndex={0}
            >
              <table>
                <thead>
                  <tr>
                    {(tab === "report-work"
                      ? [
                          "Report",
                          "Type",
                          "Product",
                          "Authority",
                          "Status",
                          "Created by",
                          "Due",
                          "Submitted",
                          "Evidence",
                        ]
                      : tab === "work"
                        ? [
                            "Case",
                            "Workflow",
                            "Priority",
                            "Assigned employee",
                            "Created by",
                            "Updated",
                          ]
                        : tab === "audit" || tab === "activity"
                          ? [
                              "Action",
                              "Actor",
                              "Employee / case",
                              "Outcome",
                              "Recorded",
                              "Reason",
                            ]
                          : [
                              "Employee",
                              "Role",
                              "Department / organization",
                              tab === "password-resets"
                                ? "Requested"
                                : "Created",
                              "Status",
                              "Actions",
                            ]
                    ).map((h) => (
                      <th key={h}>{h}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {data.content.map((row) => (
                    <tr key={text(row.id || row.userId)}>
                      {tab === "report-work" ? (
                        <>
                          <td>{text(row.reportId)}</td>
                          <td>{text(row.reportType)}</td>
                          <td>{text(row.product)}</td>
                          <td>{text(row.authority)}</td>
                          <td>
                            <span className="ws-badge">{text(row.status)}</span>
                          </td>
                          <td>
                            {row.createdBy
                              ? employeeLabel(row.createdBy as Row)
                              : "Not recorded"}
                          </td>
                          <td>{text(row.dueDate)}</td>
                          <td>{dateTime(row.submittedDate)}</td>
                          <td>
                            <button
                              onClick={() => setEvidenceReport(Number(row.id))}
                            >
                              Report details & PDF
                            </button>
                          </td>
                        </>
                      ) : tab === "work" ? (
                        <>
                          <td>{text(row.caseNumber)}</td>
                          <td>{text(row.workflowState)}</td>
                          <td>{text(row.priority)}</td>
                          <td>
                            {row.assignedTo
                              ? employeeLabel(row.assignedTo as Row)
                              : "Other workflow team / unassigned"}
                          </td>
                          <td>
                            {row.createdBy
                              ? employeeLabel(row.createdBy as Row)
                              : "Other workflow team"}
                          </td>
                          <td>{dateTime(row.updatedAt)}</td>
                        </>
                      ) : tab === "audit" || tab === "activity" ? (
                        <>
                          <td>{text(row.action).replaceAll("_", " ")}</td>
                          <td>
                            {text(row.actorUsername)}
                            <small>{text(row.actorLevel)}</small>
                          </td>
                          <td>{text(row.targetUsername || row.caseNumber)}</td>
                          <td>
                            <span className="ws-badge">
                              {text(row.outcome)}
                            </span>
                          </td>
                          <td>{dateTime(row.createdAt)}</td>
                          <td>
                            {text(row.reason)}
                            {tab === "audit" && (
                              <button onClick={() => setAuditRecord(row)}>
                                View evidence
                              </button>
                            )}
                          </td>
                        </>
                      ) : (
                        <>
                          <td>
                            <strong>{text(row.fullName)}</strong>
                            <span>
                              {text(row.staffCode)} · @{text(row.username)}
                            </span>
                            <small>
                              {text(row.email)} · Employee ID{" "}
                              {text(row.employeeId)}
                            </small>
                          </td>
                          <td>{roleName(row.requestedRole || row.role)}</td>
                          <td>
                            {text(row.department)}
                            <small>{text(row.organization)}</small>
                          </td>
                          <td>{dateTime(row.createdAt)}</td>
                          <td>
                            <span
                              className={`ws-badge access-status status-${String(row.status || row.accountStatus).toLowerCase()}`}
                            >
                              {text(row.status || row.accountStatus)}
                            </span>
                          </td>
                          <td>{actions(row)}</td>
                        </>
                      )}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            {!loading && !data.content.length && (
              <p className="ws-empty">No matching records in this role.</p>
            )}
          </div>
          <div className="ws-actions admin-pagination">
            <button
              disabled={loading || saving || page === 0}
              onClick={() => setPage((v) => v - 1)}
            >
              Previous
            </button>
            <span>
              Page {page + 1} · {data.totalElements} records
            </span>
            <button
              disabled={loading || saving || page + 1 >= data.totalPages}
              onClick={() => setPage((v) => v + 1)}
            >
              Next
            </button>
          </div>
        </>
      )}
      {auditRecord && (
        <AccessDialog
          title="Administrative evidence"
          onClose={() => setAuditRecord(null)}
        >
          <dl className="admin-details">
            {[
              ["Action", text(auditRecord.action).replaceAll("_", " ")],
              [
                "Actor",
                `@${text(auditRecord.actorUsername)} · ${text(auditRecord.actorLevel)}`,
              ],
              [
                "Actor staff code",
                auditRecord.actorId ? `PS-${auditRecord.actorId}` : "—",
              ],
              ["Application role", roleName(auditRecord.managedRole)],
              ["Affected account", text(auditRecord.targetUsername)],
              [
                "Affected staff code",
                auditRecord.targetUserId
                  ? `PS-${auditRecord.targetUserId}`
                  : "—",
              ],
              [
                "Previous administrator",
                auditRecord.previousAdministratorId
                  ? `PS-${auditRecord.previousAdministratorId}`
                  : "—",
              ],
              ["Previous status", text(auditRecord.previousStatus)],
              ["New status", text(auditRecord.newStatus)],
              ["Request ID", text(auditRecord.requestId)],
              ["Recorded", dateTime(auditRecord.createdAt)],
              ["Outcome", text(auditRecord.outcome)],
              ["Reason", text(auditRecord.reason)],
            ].map(([label, value]) => (
              <div key={label}>
                <dt>{label}</dt>
                <dd>{value}</dd>
              </div>
            ))}
          </dl>
        </AccessDialog>
      )}
      {details && (
        <AccessDialog
          title="Employee access details"
          onClose={() => {
            detailGeneration.current++;
            setDetails(null);
          }}
        >
          {detailLoading ? (
            <p role="status">Loading employee details…</p>
          ) : detailError ? (
            <p role="alert">
              {detailError}
              <button onClick={() => void viewDetails(details)}>
                Retry employee details
              </button>
            </p>
          ) : (
            <dl className="admin-details">
              {Object.entries(details)
                .filter(([key]) => key !== "details")
                .map(([key, value]) => (
                  <div key={key}>
                    <dt>{key.replace(/([A-Z])/g, " $1")}</dt>
                    <dd>{text(value)}</dd>
                  </div>
                ))}
              {details.details && typeof details.details === "object"
                ? Object.entries(details.details as Row).map(([key, value]) => (
                    <div key={key}>
                      <dt>{key.replace(/([A-Z])/g, " $1")}</dt>
                      <dd>{text(value)}</dd>
                    </div>
                  ))
                : null}
            </dl>
          )}
        </AccessDialog>
      )}
      {decision && (
        <AccessDialog
          title="Confirm administrative action"
          blocked={saving}
          onClose={() => setDecision(null)}
        >
          <form
            onSubmit={async (e) => {
              e.preventDefault();
              const path =
                decision.kind === "assignments"
                  ? `/api/administration/assignments/${role}/release`
                  : `/api/administration/${decision.kind === "users" ? "users" : decision.kind}/${decision.kind === "users" ? decision.row.userId : decision.row.id}/${decision.action}`;
              if (
                await mutate(
                  path,
                  decision.kind === "assignments" ? "POST" : "PUT",
                  {
                    reason,
                    ...(decision.kind === "assignments"
                      ? { expectedVersion: slot?.assignmentVersion || 0 }
                      : {}),
                  },
                )
              )
                setDecision(null);
            }}
          >
            <h3>
              {decision.action.replace(/^./, (s) => s.toUpperCase())} ·{" "}
              {text(decision.row.fullName)}
            </h3>
            <p>{employeeLabel(decision.row)}</p>
            <label className="admin-reason">
              Decision reason
              <textarea
                required
                maxLength={2000}
                value={reason}
                onChange={(e) => setReason(e.target.value)}
              />
            </label>
            {error && (
              <p role="alert" className="ws-error">
                {error}
              </p>
            )}
            <div className="ws-actions">
              <button disabled={saving}>Confirm action</button>
              <button
                type="button"
                disabled={saving}
                onClick={() => setDecision(null)}
              >
                Cancel
              </button>
            </div>
          </form>
        </AccessDialog>
      )}
    </div>
  );
  if (embedded) return content;
  return (
    <div className={`ws-shell ${menu ? "ws-menu-open" : ""}`}>
      <aside
        className={`ws-sidebar ${menu ? "is-open" : ""}`}
        id="workspace-navigation"
      >
        <div className="ws-brand">
          PharmaSafe<span>SAFETY SUITE</span>
        </div>
        <p>{roleName(user?.managedRole)} · Role Admin</p>
        <nav aria-label="Role Admin navigation">
          {navigation
            .filter(n=>n.tab!=="pv-analysis"||role==="ROLE_PV_ANALYST")
            .filter(
              (n) =>
                n.tab !== "report-work" ||
                superAdmin ||
                ["ROLE_REPORTING_SPECIALIST", "ROLE_SAFETY_MANAGER"].includes(
                  user?.managedRole || "",
                ),
            )
            .map((n) => (
              <button
                key={n.tab}
                aria-current={tab === n.tab ? "page" : undefined}
                onClick={() => selectTab(n.tab)}
              >
                <n.Icon size={17} />
                <span>{n.label}</span>
              </button>
            ))}
        </nav>
        <div className="ws-person">
          <strong>{user?.fullName || user?.username}</strong>
          <span>
            {user?.staffCode} · @{user?.username}
          </span>
          <span>ROLE ADMIN</span>
          <button onClick={() => void logout()}>Log Out</button>
        </div>
      </aside>
      {menu && (
        <button
          className="ws-drawer-overlay"
          aria-label="Close navigation"
          onClick={() => setMenu(false)}
        />
      )}
      <div className="ws-main" inert={menu || undefined}>
        <header className="ws-topbar">
          <button
            className="ws-menu-toggle"
            aria-label="Toggle navigation"
            onClick={() => setMenu((v) => !v)}
          >
            Menu
          </button>
          <span>{roleName(user?.managedRole)} / Role administration</span>
        </header>
        <main className="ws-content">{content}</main>
      </div>
    </div>
  );
}
