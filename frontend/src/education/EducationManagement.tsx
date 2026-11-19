import { useEffect, useState } from "react";
import { apiRequest } from "../config/api";
import { AccessDialog } from "../workspaces/AccessDialog";
import type { College, Domain, Student, Task } from "./types";
type Order = {
  id: number;
  collegeId: number;
  collegeName: string;
  domain: string;
  stage: string;
  title: string;
  description: string;
  instructions: string;
  dueAt: string;
  requireDocument: boolean;
  rowVersion: number;
  archived: boolean;
  assignedStudents: number;
};
const localTime = (v: string) => {
  const d = new Date(v);
  return new Date(d.getTime() - d.getTimezoneOffset() * 60000)
    .toISOString()
    .slice(0, 16);
};
function useWriter(onSaved: () => void) {
  const [busy, setBusy] = useState(false),
    [error, setError] = useState(""),
    [notice, setNotice] = useState("");
  async function write(path: string, method: "POST" | "PUT", data: unknown) {
    setBusy(true);
    setError("");
    setNotice("");
    try {
      await apiRequest(path, method, data);
      setNotice("Saved successfully.");
      onSaved();
      return true;
    } catch (e) {
      setError(e instanceof Error ? e.message : "Unable to save");
      return false;
    } finally {
      setBusy(false);
    }
  }
  return { write, busy, error, notice };
}
function Messages({ error, notice }: { error: string; notice: string }) {
  return (
    <>
      {error && (
        <p className="ws-error" role="alert">
          {error}
        </p>
      )}
      {notice && (
        <p className="ws-notice" role="status">
          {notice}
        </p>
      )}
    </>
  );
}
export function CollegeTools({
  colleges,
  canManage,
  onSaved,
  onSelect,
  onDirty,
}: {
  colleges: College[];
  canManage: boolean;
  onSaved: () => void;
  onSelect: (id: string) => void;
  onDirty: (v: boolean) => void;
}) {
  const [query, setQuery] = useState(""),
    [editing, setEditing] = useState<College | null>(null),
    [remove, setRemove] = useState<College | null>(null),
    [form, setForm] = useState({ code: "", collegeName: "", reason: "" });
  const w = useWriter(onSaved);
  useEffect(() => {
    onDirty(
      editing
        ? form.collegeName !== editing.name || !!form.reason
        : !!form.code || !!form.collegeName,
    );
    return () => onDirty(false);
  }, [form, editing, onDirty]);
  function choose(c: College) {
    setEditing(c);
    setForm({ code: c.code, collegeName: c.name, reason: "" });
  }
  return (
    <section className="ws-card">
      <h2>College management</h2>
      <Messages {...w} />
      <label>
        Search colleges
        <input
          aria-label="Search colleges"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          maxLength={100}
        />
      </label>
      {canManage && (
        <form
          className="ws-form"
          aria-label="Manage college"
          inert={w.busy || undefined}
          onSubmit={async (e) => {
            e.preventDefault();
            const ok = editing
              ? await w.write("/api/education/colleges/" + editing.id, "PUT", {
                  expectedVersion: editing.rowVersion,
                  collegeName: form.collegeName,
                  active: editing.active,
                  reason: form.reason,
                })
              : await w.write("/api/education/colleges", "POST", {
                  code: form.code,
                  collegeName: form.collegeName,
                });
            if (ok) {
              setEditing(null);
              setForm({ code: "", collegeName: "", reason: "" });
            }
          }}
        >
          <label>
            College code
            <input
              required
              disabled={!!editing}
              pattern="[A-Z0-9][A-Z0-9-]{2,15}"
              maxLength={16}
              value={form.code}
              onChange={(e) =>
                setForm((v) => ({ ...v, code: e.target.value.toUpperCase() }))
              }
            />
          </label>
          <label>
            College name
            <input
              required
              maxLength={180}
              value={form.collegeName}
              onChange={(e) =>
                setForm((v) => ({ ...v, collegeName: e.target.value }))
              }
            />
          </label>
          {editing && (
            <label>
              Change reason
              <textarea
                required
                maxLength={2000}
                value={form.reason}
                onChange={(e) =>
                  setForm((v) => ({ ...v, reason: e.target.value }))
                }
              />
            </label>
          )}
          <button className="ws-primary" disabled={w.busy}>
            {editing ? "Save college" : "Add college"}
          </button>
          {editing && (
            <button
              type="button"
              onClick={() => {
                setEditing(null);
                setForm({ code: "", collegeName: "", reason: "" });
              }}
            >
              Cancel editing
            </button>
          )}
        </form>
      )}
      <div className="ws-table-wrap">
        <table>
          <thead>
            <tr>
              <th>College</th>
              <th>Students</th>
              <th>Status</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {colleges
              .filter((c) =>
                (c.name + " " + c.code)
                  .toLowerCase()
                  .includes(query.toLowerCase()),
              )
              .map((c) => (
                <tr key={c.id}>
                  <td>
                    {c.name}
                    <small>{c.code}</small>
                  </td>
                  <td>{c.students ?? 0}</td>
                  <td>{c.active ? "Active" : "Inactive"}</td>
                  <td>
                    <button onClick={() => onSelect(String(c.id))}>
                      View college students
                    </button>
                    {canManage && (
                      <>
                        <button onClick={() => choose(c)}>Edit college</button>
                        {c.active ? (
                          <button onClick={() => setRemove(c)}>
                            Remove college
                          </button>
                        ) : (
                          <button
                            onClick={() =>
                              void w.write(
                                "/api/education/colleges/" + c.id,
                                "PUT",
                                {
                                  expectedVersion: c.rowVersion,
                                  collegeName: c.name,
                                  active: true,
                                  reason: "Restore college access",
                                },
                              )
                            }
                          >
                            Restore college
                          </button>
                        )}
                      </>
                    )}
                  </td>
                </tr>
              ))}
          </tbody>
        </table>
      </div>
      {!colleges.length && <p>No colleges are configured yet.</p>}
      {remove && (
        <AccessDialog
          title="Confirm college removal"
          onClose={() => setRemove(null)}
        >
          <p>
            Deactivate {remove.name} across all seven domains? Existing work and
            reports are retained, and college access will stop.
          </p>
          <button
            disabled={w.busy}
            onClick={async () => {
              if (
                await w.write("/api/education/colleges/" + remove.id, "PUT", {
                  expectedVersion: remove.rowVersion,
                  collegeName: remove.name,
                  active: false,
                  reason: "College removed with administrator confirmation",
                })
              )
                setRemove(null);
            }}
          >
            Confirm college removal
          </button>
        </AccessDialog>
      )}
    </section>
  );
}
export function StudentTools({
  domain,
  colleges,
  collegeId,
  students,
  onSaved,
  onDirty,
}: {
  domain: Domain;
  colleges: College[];
  collegeId: string;
  students: Student[];
  onSaved: () => void;
  onDirty: (v: boolean) => void;
}) {
  const blank = () => ({
    collegeId,
    fullName: "",
    username: "",
    email: "",
    password: "",
    reason: "",
    active: true,
  });
  const [form, setForm] = useState(blank),
    [adding, setAdding] = useState(false),
    [selected, setSelected] = useState<Student | null>(null),
    [remove, setRemove] = useState(false);
  const w = useWriter(onSaved);
  useEffect(() => {
    onDirty(
      adding
        ? Object.entries(form).some(
            ([k, v]) => !["collegeId", "active"].includes(k) && !!v,
          )
        : !!selected &&
            (form.fullName !== selected.fullName ||
              form.email !== selected.email ||
              form.active !== (selected.active ?? true) ||
              !!form.reason),
    );
    return () => onDirty(false);
  }, [form, adding, selected, onDirty]);
  async function save(active = form.active, removing = false) {
    if (selected)
      return w.write("/api/education/students/" + selected.id, "PUT", {
        domain: domain.code,
        expectedVersion: selected.rowVersion,
        fullName: removing ? selected.fullName : form.fullName,
        email: removing ? selected.email : form.email,
        active,
        reason: form.reason || "Student access removed after confirmation",
      });
    return w.write("/api/education/students", "POST", {
      ...form,
      collegeId: Number(form.collegeId),
      domain: domain.code,
    });
  }
  return (
    <div className="edu-management-tools">
      <Messages {...w} />
      <div className="ws-actions">
        <button
          onClick={() => {
            setSelected(null);
            setAdding((v) => !v);
            setForm(blank());
          }}
        >
          Add student
        </button>
        <label>
          Student profile
          <select
            aria-label="Student profile"
            value={selected?.id || ""}
            onChange={(e) => {
              const s = students.find((x) => String(x.id) === e.target.value);
              setSelected(s || null);
              setAdding(false);
              if (s)
                setForm({
                  collegeId: String(s.collegeId),
                  fullName: s.fullName,
                  username: s.username,
                  email: s.email,
                  password: "",
                  reason: "",
                  active: s.active ?? true,
                });
            }}
          >
            <option value="">Select a student from this page</option>
            {students.map((s) => (
              <option key={s.id} value={s.id}>
                {s.fullName} · {s.studentCode}
              </option>
            ))}
          </select>
        </label>
      </div>
      {(adding || selected) && (
        <form
          className="ws-form"
          aria-label="Manage student"
          inert={w.busy || undefined}
          onSubmit={async (e) => {
            e.preventDefault();
            if (await save()) {
              setAdding(false);
              setSelected(null);
              setForm(blank());
            }
          }}
        >
          <h3 className="edu-wide">
            {selected
              ? selected.studentCode + " · " + selected.collegeName
              : "New student · All seven domain associations"}
          </h3>
          {!selected && (
            <label>
              Student college
              <select
                aria-label="Student college"
                required
                value={form.collegeId}
                onChange={(e) =>
                  setForm((v) => ({ ...v, collegeId: e.target.value }))
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
          )}
          <label>
            Student full name
            <input
              required
              maxLength={128}
              value={form.fullName}
              onChange={(e) =>
                setForm((v) => ({ ...v, fullName: e.target.value }))
              }
            />
          </label>
          <label>
            Student email
            <input
              required
              type="email"
              maxLength={128}
              value={form.email}
              onChange={(e) =>
                setForm((v) => ({ ...v, email: e.target.value }))
              }
            />
          </label>
          {!selected && (
            <>
              <label>
                Student username
                <input
                  required
                  pattern="[A-Za-z0-9._-]{3,64}"
                  minLength={3}
                  maxLength={64}
                  value={form.username}
                  onChange={(e) =>
                    setForm((v) => ({ ...v, username: e.target.value }))
                  }
                />
              </label>
              <label>
                Initial student password
                <input
                  required
                  type="password"
                  autoComplete="new-password"
                  minLength={12}
                  maxLength={72}
                  value={form.password}
                  onChange={(e) =>
                    setForm((v) => ({ ...v, password: e.target.value }))
                  }
                />
              </label>
            </>
          )}
          <label>
            Student change reason
            <textarea
              required
              maxLength={2000}
              value={form.reason}
              onChange={(e) =>
                setForm((v) => ({ ...v, reason: e.target.value }))
              }
            />
          </label>
          {selected && (
            <label className="edu-check">
              <input
                type="checkbox"
                checked={form.active}
                onChange={(e) =>
                  setForm((v) => ({ ...v, active: e.target.checked }))
                }
              />
              Student account active
            </label>
          )}
          {selected && (
            <p className="edu-wide">
              Progress: {selected.work?.averageProgress || 0}% ·{" "}
              {selected.work?.total || 0} assigned tasks. College and Student ID
              remain fixed. Name, email and account access apply across domains.
            </p>
          )}
          <button disabled={w.busy} className="ws-primary">
            {selected ? "Save student profile" : "Create student"}
          </button>
          <button
            type="button"
            onClick={() => {
              setAdding(false);
              setSelected(null);
              setForm(blank());
            }}
          >
            Close student form
          </button>
          {selected && (
            <button type="button" onClick={() => setRemove(true)}>
              Remove student
            </button>
          )}
        </form>
      )}
      {remove && selected && (
        <AccessDialog
          title="Confirm student removal"
          onClose={() => setRemove(false)}
        >
          <p>
            Disable {selected.fullName}'s access? All work and evidence remain
            available to authorized administrators.
          </p>
          <button
            disabled={w.busy}
            onClick={async () => {
              if (await save(false, true)) {
                setRemove(false);
                setSelected(null);
                setForm(blank());
              }
            }}
          >
            Confirm student removal
          </button>
        </AccessDialog>
      )}
    </div>
  );
}
const emptyWork = () => ({
  title: "",
  description: "",
  instructions: "",
  dueAt: "",
  requireDocument: false,
  reason: "",
});
export function WorkEditor({
  work,
  path,
  onSaved,
  onClose,
}: {
  work: Task | Order;
  path: string;
  onSaved: () => void;
  onClose: () => void;
}) {
  const [form, setForm] = useState({
      ...emptyWork(),
      title: work.title,
      description: work.description,
      instructions: work.instructions,
      dueAt: localTime(work.dueAt),
      requireDocument: work.requireDocument,
    }),
    [remove, setRemove] = useState(false);
  const w = useWriter(onSaved);
  async function save(archived: boolean) {
    if (
      await w.write(path, "PUT", {
        ...(archived
          ? {
              title: work.title,
              description: work.description,
              instructions: work.instructions,
              dueAt: work.dueAt,
              requireDocument: work.requireDocument,
              reason: form.reason,
            }
          : { ...form, dueAt: new Date(form.dueAt).toISOString() }),
        expectedVersion: work.rowVersion,
        archived,
      })
    ) {
      onClose();
    }
  }
  return (
    <AccessDialog title="Edit work" onClose={onClose}>
      <Messages {...w} />
      <p>
        Instructions can be changed before student activity begins. Removing
        work archives it and preserves evidence.
        {path.includes("/orders/") && (
          <span>
            {" "}
            Archiving college work stops new distribution; existing student
            assignments can be archived separately.
          </span>
        )}
      </p>
      <form
        className="ws-form"
        onSubmit={(e) => {
          e.preventDefault();
          void save(false);
        }}
      >
        {(
          ["title", "description", "instructions", "dueAt", "reason"] as const
        ).map((k) => (
          <label key={k}>
            {
              {
                title: "Work title",
                description: "Work description",
                instructions: "Work instructions",
                dueAt: "Work deadline",
                reason: "Work change reason",
              }[k]
            }
            {["description", "instructions", "reason"].includes(k) ? (
              <textarea
                required
                maxLength={k === "reason" ? 2000 : 5000}
                value={form[k] as string}
                onChange={(e) =>
                  setForm((v) => ({ ...v, [k]: e.target.value }))
                }
              />
            ) : (
              <input
                required
                type={k === "dueAt" ? "datetime-local" : "text"}
                maxLength={160}
                value={form[k] as string}
                onChange={(e) =>
                  setForm((v) => ({ ...v, [k]: e.target.value }))
                }
              />
            )}
          </label>
        ))}
        <label className="edu-check">
          <input
            type="checkbox"
            checked={form.requireDocument}
            onChange={(e) =>
              setForm((v) => ({ ...v, requireDocument: e.target.checked }))
            }
          />
          Require supporting PDF
        </label>
        <button disabled={w.busy}>Save work changes</button>
        <button type="button" onClick={() => setRemove(true)}>
          Remove work
        </button>
      </form>
      {remove && (
        <section role="alert">
          <p>Archive this work? Existing evidence and reports are preserved.</p>
          <button
            disabled={w.busy || !form.reason.trim()}
            onClick={() => void save(true)}
          >
            Confirm work removal
          </button>
        </section>
      )}
    </AccessDialog>
  );
}
export function WorkOrders({
  domain,
  colleges,
  collegeId,
  canManage,
  refresh,
  onSaved,
  onAssign,
  onDirty,
}: {
  domain: Domain;
  colleges: College[];
  collegeId: string;
  canManage: boolean;
  refresh: number;
  onSaved: () => void;
  onAssign: (o: Order) => void;
  onDirty: (v: boolean) => void;
}) {
  const [orders, setOrders] = useState<Order[]>([]),
    [form, setForm] = useState({
      ...emptyWork(),
      collegeId,
      stage: domain.stages[0],
    }),
    [editing, setEditing] = useState<Order | null>(null),
    [loading, setLoading] = useState(true),
    [loadError, setLoadError] = useState("");
  const w = useWriter(onSaved);
  useEffect(() => {
    onDirty(!!form.title || !!form.description || !!form.instructions);
    return () => onDirty(false);
  }, [form, onDirty]);
  useEffect(() => {
    const c = new AbortController();
    setLoading(true);
    setLoadError("");
    apiRequest<Order[]>(
      "/api/education/orders?domain=" +
        domain.code +
        (collegeId ? "&collegeId=" + collegeId : ""),
      "GET",
      undefined,
      c.signal,
    )
      .then(setOrders)
      .catch((e) => {
        if (!c.signal.aborted) {
          setOrders([]);
          setLoadError(
            e instanceof Error ? e.message : "Could not load college work",
          );
        }
      })
      .finally(() => {
        if (!c.signal.aborted) setLoading(false);
      });
    return () => c.abort();
  }, [domain.code, collegeId, refresh]);
  return (
    <section className="ws-card">
      <h2>College work orders</h2>
      <p>
        Domain Admin creates college work. College Admin assigns its original
        instructions to individual students, selected students or the approved
        cohort.
      </p>
      <Messages {...w} />
      {canManage && (
        <form
          className="ws-form"
          aria-label="Create college work"
          inert={w.busy || undefined}
          onSubmit={async (e) => {
            e.preventDefault();
            if (
              await w.write("/api/education/orders", "POST", {
                ...form,
                collegeId: Number(form.collegeId),
                domain: domain.code,
                dueAt: new Date(form.dueAt).toISOString(),
              })
            )
              setForm({ ...emptyWork(), collegeId, stage: domain.stages[0] });
          }}
        >
          <label>
            Work college
            <select
              aria-label="Work college"
              required
              value={form.collegeId}
              onChange={(e) =>
                setForm((v) => ({ ...v, collegeId: e.target.value }))
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
          <label>
            Work stage
            <select
              aria-label="Work stage"
              required
              value={form.stage}
              onChange={(e) =>
                setForm((v) => ({ ...v, stage: e.target.value }))
              }
            >
              {domain.stages.map((s) => (
                <option key={s}>{s}</option>
              ))}
            </select>
          </label>
          {(["title", "description", "instructions", "dueAt"] as const).map(
            (k) => (
              <label key={k}>
                {
                  {
                    title: "College work title",
                    description: "College work description",
                    instructions: "College work instructions",
                    dueAt: "College work deadline",
                  }[k]
                }
                {k === "description" || k === "instructions" ? (
                  <textarea
                    required
                    maxLength={5000}
                    value={form[k]}
                    onChange={(e) =>
                      setForm((v) => ({ ...v, [k]: e.target.value }))
                    }
                  />
                ) : (
                  <input
                    required
                    type={k === "dueAt" ? "datetime-local" : "text"}
                    maxLength={160}
                    value={form[k]}
                    onChange={(e) =>
                      setForm((v) => ({ ...v, [k]: e.target.value }))
                    }
                  />
                )}
              </label>
            ),
          )}
          <label className="edu-check">
            <input
              type="checkbox"
              checked={form.requireDocument}
              onChange={(e) =>
                setForm((v) => ({ ...v, requireDocument: e.target.checked }))
              }
            />
            Require supporting PDF
          </label>
          <button disabled={w.busy} className="ws-primary">
            Create college work
          </button>
        </form>
      )}
      {loadError && (
        <p role="alert" className="ws-error">
          {loadError}
          <button onClick={onSaved}>Retry college work</button>
        </p>
      )}
      {loading ? (
        <p role="status">Loading college work…</p>
      ) : !orders.length ? (
        <p>No college work orders for the current filters.</p>
      ) : (
        <div className="ws-table-wrap">
          <table>
            <thead>
              <tr>
                <th>Work / college</th>
                <th>Deadline</th>
                <th>Assigned students</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {orders.map((o) => (
                <tr key={o.id}>
                  <td>
                    {o.title}
                    <small>{o.collegeName}</small>
                    <small>{o.instructions}</small>
                  </td>
                  <td>{new Date(o.dueAt).toLocaleString()}</td>
                  <td>{o.assignedStudents}</td>
                  <td>
                    <button onClick={() => onAssign(o)}>
                      Assign college work to students
                    </button>
                    {canManage && (
                      <button onClick={() => setEditing(o)}>
                        Edit college work
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {editing && (
        <WorkEditor
          work={editing}
          path={"/api/education/orders/" + editing.id}
          onSaved={onSaved}
          onClose={() => setEditing(null)}
        />
      )}
    </section>
  );
}
export function ProfileSettings({ settings }: { settings: boolean }) {
  const [profile, setProfile] = useState<Record<string, unknown> | null>(null),
    [prefs, setPrefs] = useState({ compactView: false, reduceMotion: false }),
    [error, setError] = useState("");
  const w = useWriter(() => {});
  useEffect(() => {
    apiRequest<Record<string, unknown>>("/api/education/profile")
      .then(setProfile)
      .catch((e) => setError(e.message));
    apiRequest<typeof prefs>("/api/education/preferences")
      .then(setPrefs)
      .catch((e) => setError(e.message));
  }, []);
  return (
    <section className="ws-card">
      <h2>{settings ? "Workspace settings" : "My profile"}</h2>
      {error && (
        <p role="alert" className="ws-error">
          {error}
        </p>
      )}
      {settings ? (
        <form
          className="ws-form"
          onSubmit={async (e) => {
            e.preventDefault();
            if (await w.write("/api/education/preferences", "PUT", prefs)) {
              document.body.classList.toggle("edu-compact", prefs.compactView);
              document.body.classList.toggle(
                "edu-reduced-motion",
                prefs.reduceMotion,
              );
            }
          }}
        >
          <Messages {...w} />
          <label className="edu-check">
            <input
              type="checkbox"
              checked={prefs.compactView}
              onChange={(e) =>
                setPrefs((v) => ({ ...v, compactView: e.target.checked }))
              }
            />
            Compact table spacing
          </label>
          <label className="edu-check">
            <input
              type="checkbox"
              checked={prefs.reduceMotion}
              onChange={(e) =>
                setPrefs((v) => ({ ...v, reduceMotion: e.target.checked }))
              }
            />
            Reduce animations
          </label>
          <button disabled={w.busy}>Save workspace preferences</button>
        </form>
      ) : profile ? (
        <dl className="edu-profile">
          {[
            "fullName",
            "username",
            "email",
            "studentCode",
            "collegeName",
            "college",
            "role",
          ]
            .filter((k) => profile[k])
            .map((k) => (
              <div key={k}>
                <dt>
                  {k === "studentCode"
                    ? "Student ID"
                    : k === "collegeName"
                      ? "College"
                      : k.replace(/([A-Z])/g, " $1")}
                </dt>
                <dd>
                  {k === "role"
                    ? (
                        {
                          ROLE_ADMIN: "Super Admin",
                          ROLE_DOMAIN_ADMIN: "Domain Admin",
                          ROLE_COLLEGE_ADMIN: "College Admin",
                          ROLE_STUDENT: "Student",
                        } as Record<string, string>
                      )[String(profile[k])] || "Administrator"
                    : String(profile[k])}
                </dd>
              </div>
            ))}
        </dl>
      ) : (
        <p role="status">Loading profile…</p>
      )}
    </section>
  );
}
