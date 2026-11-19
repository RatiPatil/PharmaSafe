import { useEffect, useState } from "react";
import { apiRequest } from "../config/api";
import { dateTime } from "../workspaces/model";
import { AccessDialog } from "../workspaces/AccessDialog";
import type { Page, Student } from "./types";
type PendingStudent = Student & {
  pendingDomains: { domain: string; name: string; rowVersion: number }[];
};
export function GrantStudentAccess({
  onClose,
  onSaved,
}: {
  onClose: () => void;
  onSaved: () => void;
}) {
  const [data, setData] = useState<Page<PendingStudent> | null>(null),
    [page, setPage] = useState(0),
    [refresh, setRefresh] = useState(0),
    [loading, setLoading] = useState(true),
    [error, setError] = useState(""),
    [notice, setNotice] = useState(""),
    [busy, setBusy] = useState(false),
    [decision, setDecision] = useState<{
      student: PendingStudent;
      status: "APPROVED" | "REJECTED";
    } | null>(null),
    [reason, setReason] = useState("");
  useEffect(() => {
    const c = new AbortController();
    setLoading(true);
    setError("");
    apiRequest<Page<PendingStudent>>(
      "/api/education/student-access?page=" + page,
      "GET",
      undefined,
      c.signal,
    )
      .then((result) => {
        if (c.signal.aborted) return;
        if (page > 0 && page >= result.totalPages)
          setPage(Math.max(0, result.totalPages - 1));
        else setData(result);
      })
      .catch((e) => {
        if (!c.signal.aborted) {
          setError(e.message);
          setData(null);
        }
      })
      .finally(() => {
        if (!c.signal.aborted) setLoading(false);
      });
    return () => c.abort();
  }, [page, refresh]);
  return (
    <AccessDialog
      title={decision ? "Confirm student access" : "Grant Student Access"}
      eyebrow="YOUR COLLEGE · STUDENT APPROVALS"
      blocked={busy}
      onClose={() => {
        if (decision) {
          setDecision(null);
          setReason("");
          setError("");
        } else onClose();
      }}
    >
      <div className="edu-access-review">
        <p>
          Review registrations from your college. Each decision applies to all
          currently pending domain requests shown for that student.
        </p>
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
        {decision ? (
          <form
            className="ws-form"
            aria-label="Confirm student access"
            onSubmit={async (e) => {
              e.preventDefault();
              setBusy(true);
              setError("");
              try {
                const result = await apiRequest<{ updatedDomains: number }>(
                  "/api/education/student-access/" +
                    decision.student.id +
                    "/decision",
                  "POST",
                  {
                    status: decision.status,
                    reason,
                    expectedVersions: Object.fromEntries(
                      decision.student.pendingDomains.map((d) => [
                        d.domain,
                        d.rowVersion,
                      ]),
                    ),
                  },
                );
                setNotice(
                  (decision.status === "APPROVED" ? "Approved" : "Rejected") +
                    " " +
                    result.updatedDomains +
                    " pending domain requests for " +
                    decision.student.fullName +
                    ".",
                );
                setDecision(null);
                setReason("");
                setRefresh((v) => v + 1);
                onSaved();
              } catch (e) {
                setError(
                  e instanceof Error
                    ? e.message
                    : "Could not save the decision.",
                );
              } finally {
                setBusy(false);
              }
            }}
          >
            <h3 className="edu-wide">
              {decision.status === "APPROVED" ? "Approve" : "Reject"} access for{" "}
              {decision.student.fullName}?
            </h3>
            <p className="edu-wide">
              {decision.student.studentCode} · {decision.student.email}
            </p>
            <ul className="edu-wide">
              {decision.student.pendingDomains.map((d) => (
                <li key={d.domain}>{d.name}</li>
              ))}
            </ul>
            <label className="edu-wide">
              Decision reason
              <textarea
                required
                minLength={3}
                maxLength={2000}
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                autoFocus
              />
            </label>
            <div className="edu-receipt-actions">
              <button className="ws-primary" disabled={busy}>
                {busy
                  ? "Saving decision…"
                  : "Confirm " +
                    (decision.status === "APPROVED" ? "approval" : "rejection")}
              </button>
              <button
                type="button"
                disabled={busy}
                onClick={() => {
                  setDecision(null);
                  setReason("");
                  setError("");
                }}
              >
                Cancel decision
              </button>
            </div>
          </form>
        ) : loading ? (
          <p role="status">Loading pending registrations…</p>
        ) : !data ? (
          <button onClick={() => setRefresh((v) => v + 1)}>
            Retry pending registrations
          </button>
        ) : (
          <>
            <div className="edu-review-heading">
              <strong>{data.totalElements} students awaiting access</strong>
              <button onClick={() => setRefresh((v) => v + 1)}>
                Refresh registrations
              </button>
            </div>
            {!data.content.length ? (
              <section className="edu-empty-state">
                <h3>All caught up</h3>
                <p>No students in your college are waiting for approval.</p>
              </section>
            ) : (
              <div className="ws-table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>Student</th>
                      <th>Student ID</th>
                      <th>Registered</th>
                      <th>Pending domains</th>
                      <th>Decision</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.content.map((s) => (
                      <tr key={s.id}>
                        <td>
                          <strong>{s.fullName}</strong>
                          <small>{s.email}</small>
                        </td>
                        <td>{s.studentCode}</td>
                        <td>{dateTime(s.createdAt)}</td>
                        <td>{s.pendingDomains.length} domain requests</td>
                        <td>
                          <div className="ws-actions">
                            <button
                              onClick={() => {
                                setDecision({ student: s, status: "APPROVED" });
                                setNotice("");
                              }}
                            >
                              Approve student
                            </button>
                            <button
                              onClick={() => {
                                setDecision({ student: s, status: "REJECTED" });
                                setNotice("");
                              }}
                            >
                              Reject student
                            </button>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
            <div className="edu-pager">
              <button
                disabled={page === 0}
                onClick={() => setPage((v) => v - 1)}
              >
                Previous registrations
              </button>
              <span>Page {page + 1}</span>
              <button
                disabled={page + 1 >= data.totalPages}
                onClick={() => setPage((v) => v + 1)}
              >
                Next registrations
              </button>
            </div>
          </>
        )}
      </div>
    </AccessDialog>
  );
}
