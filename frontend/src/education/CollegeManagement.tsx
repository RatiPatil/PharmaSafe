import { useEffect, useState } from "react";
import { apiRequest } from "../config/api";
import { AccessDialog } from "../workspaces/AccessDialog";
import type { Admin, College, Domain } from "./types";
const newCollege = () => ({ code: "", collegeName: "" });
const newAdmin = () => ({
  kind: "COLLEGE_ADMIN",
  collegeId: "",
  domain: "",
  fullName: "",
  username: "",
  email: "",
  password: "",
  reason: "",
});
export function CollegeManagement({
  colleges,
  domains,
  refresh,
  onSaved,
  onDirty,
}: {
  colleges: College[];
  domains: Domain[];
  refresh: number;
  onSaved: () => void;
  onDirty: (v: boolean) => void;
}) {
  const [college, setCollege] = useState(newCollege),
    [admin, setAdmin] = useState(newAdmin),
    [grants, setGrants] = useState<Admin[]>([]),
    [busy, setBusy] = useState(false),
    [error, setError] = useState(""),
    [notice, setNotice] = useState(""),
    [action, setAction] = useState<
      { type: "college"; data: College } | { type: "admin"; data: Admin } | null
    >(null),
    [reason, setReason] = useState(""),
    [editName, setEditName] = useState(""),
    [editActive, setEditActive] = useState(true);
  const dirty =
    Object.values(college).some(Boolean) ||
    Object.entries(admin).some(([k, v]) => k !== "kind" && !!v) ||
    !!reason ||
    !!action;
  useEffect(() => {
    onDirty(dirty);
    return () => onDirty(false);
  }, [dirty, onDirty]);
  useEffect(() => {
    const c = new AbortController();
    apiRequest<Admin[]>(
      "/api/education/administrators",
      "GET",
      undefined,
      c.signal,
    )
      .then(setGrants)
      .catch((e) => {
        if (!c.signal.aborted) setError(e.message);
      });
    return () => c.abort();
  }, [refresh]);
  async function write(path: string, body: unknown, method = "POST") {
    if (busy) return false;
    setBusy(true);
    setError("");
    try {
      await apiRequest(path, method, body);
      setNotice(
        "Saved successfully. College and administrator access are persistent.",
      );
      onSaved();
      return true;
    } catch (e) {
      setError(e instanceof Error ? e.message : "Save failed");
      return false;
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="edu-management">
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
      <section className="ws-card">
        <h2>Colleges</h2>
        <p>
          Active colleges populate the student registration dropdown
          automatically.
        </p>
        <form
          className="ws-form"
          aria-label="Create college"
          inert={busy || undefined}
          onSubmit={async (e) => {
            e.preventDefault();
            if (await write("/api/education/colleges", college))
              setCollege(newCollege());
          }}
        >
          <label>
            College name
            <input
              name="collegeName"
              required
              maxLength={180}
              value={college.collegeName}
              onChange={(e) =>
                setCollege((v) => ({ ...v, collegeName: e.target.value }))
              }
            />
          </label>
          <label>
            College code
            <input
              name="code"
              required
              pattern="[A-Z0-9][A-Z0-9-]{2,15}"
              maxLength={16}
              placeholder="COL-ABC"
              value={college.code}
              onChange={(e) =>
                setCollege((v) => ({
                  ...v,
                  code: e.target.value.toUpperCase(),
                }))
              }
            />
          </label>
          <button className="ws-primary" disabled={busy}>
            Create college
          </button>
        </form>
        <div className="ws-table-wrap">
          <table>
            <thead>
              <tr>
                <th>College</th>
                <th>Code</th>
                <th>Status</th>
                <th>Manage</th>
              </tr>
            </thead>
            <tbody>
              {colleges.map((c) => (
                <tr key={c.id}>
                  <td>{c.name}</td>
                  <td>{c.code}</td>
                  <td>{c.active ? "Active" : "Inactive"}</td>
                  <td>
                    <button
                      disabled={busy}
                      onClick={() => {
                        setAction({ type: "college", data: c });
                        setEditName(c.name);
                        setEditActive(c.active);
                      }}
                    >
                      Edit college & access
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {!colleges.length && (
          <p>No colleges yet. Create your first college above.</p>
        )}
      </section>
      <section className="ws-card">
        <h2>College & Domain administrators</h2>
        <p>
          College Admins manage one college across all seven domains. Domain
          Admins inspect their assigned domain across colleges. These
          educational authorities do not grant clinical employee privileges.
        </p>
        <form
          className="ws-form"
          aria-label="Create education administrator"
          inert={busy || undefined}
          onSubmit={async (e) => {
            e.preventDefault();
            if (
              await write("/api/education/administrators", {
                ...admin,
                collegeId:
                  admin.kind === "COLLEGE_ADMIN"
                    ? Number(admin.collegeId)
                    : null,
                domain: admin.kind === "DOMAIN_ADMIN" ? admin.domain : null,
              })
            )
              setAdmin(newAdmin());
          }}
        >
          <label>
            Administrator authority
            <select
              aria-label="Administrator authority"
              value={admin.kind}
              onChange={(e) =>
                setAdmin((v) => ({ ...v, kind: e.target.value }))
              }
            >
              <option value="COLLEGE_ADMIN">College Admin</option>
              <option value="DOMAIN_ADMIN">Domain Admin</option>
            </select>
          </label>
          {admin.kind === "COLLEGE_ADMIN" ? (
            <label>
              Administrator college
              <select
                aria-label="Administrator college"
                required
                value={admin.collegeId}
                onChange={(e) =>
                  setAdmin((v) => ({ ...v, collegeId: e.target.value }))
                }
              >
                <option value="">Select a college</option>
                {colleges
                  .filter((c) => c.active)
                  .map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.name}
                    </option>
                  ))}
              </select>
            </label>
          ) : (
            <label>
              Assigned domain
              <select
                aria-label="Assigned domain"
                required
                value={admin.domain}
                onChange={(e) =>
                  setAdmin((v) => ({ ...v, domain: e.target.value }))
                }
              >
                <option value="">Select a domain</option>
                {domains.map((d) => (
                  <option key={d.code} value={d.code}>
                    {d.name}
                  </option>
                ))}
              </select>
            </label>
          )}
          {(["fullName", "username", "email", "password"] as const).map((k) => (
            <label key={k}>
              {
                {
                  fullName: "Administrator full name",
                  username: "Administrator username",
                  email: "Administrator email",
                  password: "Initial administrator password",
                }[k]
              }
              <input
                name={k}
                aria-label={
                  {
                    fullName: "Administrator full name",
                    username: "Administrator username",
                    email: "Administrator email",
                    password: "Initial administrator password",
                  }[k]
                }
                required
                type={
                  k === "password"
                    ? "password"
                    : k === "email"
                      ? "email"
                      : "text"
                }
                autoComplete={k === "password" ? "new-password" : "off"}
                minLength={
                  k === "password" ? 12 : k === "username" ? 3 : undefined
                }
                maxLength={k === "username" ? 64 : k === "password" ? 72 : 128}
                value={admin[k]}
                onChange={(e) =>
                  setAdmin((v) => ({ ...v, [k]: e.target.value }))
                }
              />
            </label>
          ))}
          <label className="edu-wide">
            Administrator creation reason
            <textarea
              required
              name="reason"
              maxLength={2000}
              value={admin.reason}
              onChange={(e) =>
                setAdmin((v) => ({ ...v, reason: e.target.value }))
              }
            />
          </label>
          <button disabled={busy} className="ws-primary">
            Create administrator account
          </button>
        </form>
        <div className="ws-table-wrap">
          <table>
            <thead>
              <tr>
                <th>Administrator</th>
                <th>Authority</th>
                <th>Assigned scope</th>
                <th>Status</th>
                <th>Manage</th>
              </tr>
            </thead>
            <tbody>
              {grants.map((g) => (
                <tr key={g.kind + "-" + g.id}>
                  <td>
                    {g.fullName}
                    <small>@{g.username}</small>
                  </td>
                  <td>
                    {g.kind === "COLLEGE_ADMIN"
                      ? "College Admin"
                      : "Domain Admin"}
                  </td>
                  <td>
                    {g.collegeId
                      ? colleges.find((c) => c.id === g.collegeId)?.name
                      : domains.find((d) => d.code === g.domain)?.name}
                  </td>
                  <td>{g.active ? "Active" : "Inactive"}</td>
                  <td>
                    <button
                      disabled={busy}
                      onClick={() => setAction({ type: "admin", data: g })}
                    >
                      {g.active
                        ? "Deactivate administrator"
                        : "Activate administrator"}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {!grants.length && (
          <p>No college or domain administrators assigned yet.</p>
        )}
      </section>
      {action && (
        <AccessDialog
          title="Confirm college access change"
          blocked={busy}
          onClose={() => {
            setAction(null);
            setReason("");
          }}
        >
          <form
            className="ws-form"
            onSubmit={async (e) => {
              e.preventDefault();
              const path =
                action.type === "college"
                  ? "/api/education/colleges/" + action.data.id
                  : `/api/education/administrators/${action.data.kind}/${action.data.id}`;
              if (
                await write(
                  path,
                  {
                    expectedVersion: action.data.rowVersion,
                    active:
                      action.type === "college"
                        ? editActive
                        : !action.data.active,
                    collegeName: editName,
                    reason,
                  },
                  "PUT",
                )
              ) {
                setAction(null);
                setReason("");
              }
            }}
          >
            <p>
              {action.type === "college"
                ? action.data.name
                : action.data.username}{" "}
              · Set {action.type === "college" ? (editActive ? "active" : "inactive") : (action.data.active ? "inactive" : "active")}. Inactive
              colleges cannot accept registration or access college work.
            </p>
            {error && (
              <p role="alert" className="ws-error">
                {error}
              </p>
            )}
            {action.type === "college" && (
              <label className="edu-wide">
                Updated college name
                <input
                  required
                  maxLength={180}
                  value={editName}
                  onChange={(e) => setEditName(e.target.value)}
                />
              </label>
            )}
            {action.type === "college" && (
              <label className="edu-checkbox">
                <input
                  type="checkbox"
                  checked={editActive}
                  onChange={(e) => setEditActive(e.target.checked)}
                />
                College is active
              </label>
            )}
            <label className="edu-wide">
              Decision reason
              <textarea
                required
                maxLength={2000}
                value={reason}
                onChange={(e) => setReason(e.target.value)}
              />
            </label>
            <button disabled={busy}>Confirm access change</button>
          </form>
        </AccessDialog>
      )}
    </div>
  );
}
