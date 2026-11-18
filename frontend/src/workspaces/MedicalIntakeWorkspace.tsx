import { useEffect, useState } from "react";
import { apiRequest } from "../config/api";
import type { MedicalLevel, MedicalPage } from "./medicalModels";
import type { SafetyCaseSummary } from "../types/case";
type IntakeCase = SafetyCaseSummary & {
  rowVersion: number;
  allocatedTeamId: number | null;
  allocatedTeamCode: string | null;
};
export function MedicalIntakeWorkspace({
  level,
  teamId,
  onSaved,
  onDirtyChange,
}: {
  level: MedicalLevel;
  teamId: string;
  onSaved: () => void;
  onDirtyChange: (dirty: boolean) => void;
}) {
  const [data, setData] = useState<MedicalPage<IntakeCase>>({
      content: [],
      totalElements: 0,
      totalPages: 0,
    }),
    [page, setPage] = useState(0),
    [refresh, setRefresh] = useState(0),
    [selected, setSelected] = useState<IntakeCase | null>(null),
    [reason, setReason] = useState(""),
    [busy, setBusy] = useState(false),
    [error, setError] = useState(""),
    [notice, setNotice] = useState("");
  useEffect(() => { onDirtyChange(!!selected); return () => onDirtyChange(false); }, [selected,onDirtyChange]);
  useEffect(() => {
    const c = new AbortController();
    setError("");
    apiRequest<MedicalPage<IntakeCase>>(
      `/api/medical-teams/cases?page=${page}${level === "ADMIN" ? "" : teamId ? "&teamId=" + teamId : ""}`,
      "GET",
      undefined,
      c.signal,
    )
      .then((d) => {
        if (!c.signal.aborted) setData(d);
      })
      .catch((e) => {
        if (!c.signal.aborted) setError(e.message);
      });
    return () => c.abort();
  }, [teamId, page, refresh, level]);
  return (
    <section className="ws-card" aria-label="Medical case intake">
      <div className="ws-section-heading">
        <div>
          <p className="ws-eyebrow">CLINICAL REVIEW INTAKE</p>
          <h2>
            {level === "ADMIN"
              ? "Allocate medical cases to teams"
              : "My team’s allocated medical cases"}
          </h2>
          <p>
            Only unlocked cases in Medical Review appear here. The department
            Admin allocates a case to a medical team; that Lead assigns it to an
            approved medical employee by staff ID.
          </p>
        </div>
        <button disabled={busy} onClick={() => setRefresh((v) => v + 1)}>
          Refresh medical intake
        </button>
      </div>
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
      {selected && level === "ADMIN" && (
        <form
          className="ws-form"
          onSubmit={async (e) => {
            e.preventDefault();
            setBusy(true);
            setError("");
            try {
              await apiRequest(
                `/api/medical-teams/teams/${teamId}/case-allocations`,
                "POST",
                {
                  caseNumber: selected.caseNumber,
                  expectedCaseVersion: selected.rowVersion,
                  reason,
                },
              );
              setNotice(
                `${selected.caseNumber} allocated to the selected medical team.`,
              );
              setSelected(null);
              setReason("");
              setRefresh((v) => v + 1);
              onSaved();
            } catch (e) {
              setError(e instanceof Error ? e.message : "Allocation failed");
            } finally {
              setBusy(false);
            }
          }}
        >
          <h3 className="mr-wide">Allocate {selected.caseNumber}</h3>
          <label className="mr-wide">
            Allocation reason
            <textarea
              required
              maxLength={2000}
              value={reason}
              onChange={(e) => setReason(e.target.value)}
            />
          </label>
          <button disabled={busy || !teamId}>
            Confirm medical case allocation
          </button>
          <button
            type="button"
            disabled={busy}
            onClick={() => {
              setSelected(null);
              setReason("");
            }}
          >
            Cancel allocation
          </button>
        </form>
      )}
      {!teamId && level === "ADMIN" && (
        <p>Select a medical team in Team dashboard before allocating a case.</p>
      )}
      <div className="ws-table-wrap">
        <table>
          <caption className="ws-sr-only">
            Cases ready for medical team allocation
          </caption>
          <thead>
            <tr>
              <th>Case</th>
              <th>Suspect product / event</th>
              <th>Priority</th>
              <th>Medical team</th>
              <th>Clinical assignee</th>
              {level === "ADMIN" && <th>Action</th>}
            </tr>
          </thead>
          <tbody>
            {data.content.map((c) => (
              <tr key={c.id}>
                <td>
                  {c.caseNumber}
                  <small>Revision {c.rowVersion}</small>
                </td>
                <td>
                  {c.primaryProduct}
                  <p>{c.primaryEvent}</p>
                </td>
                <td>
                  {c.serious ? "Serious · " : ""}
                  {c.priority}
                </td>
                <td>{c.allocatedTeamCode || "Not allocated"}</td>
                <td>{c.assignedToUsername || "Not assigned"}</td>
                {level === "ADMIN" && (
                  <td>
                    <button
                      disabled={busy || !teamId}
                      onClick={() => {
                        setSelected(c);
                        setReason("");
                        setNotice("");
                      }}
                    >
                      Allocate to selected team
                    </button>
                  </td>
                )}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {!data.content.length && <p>No Medical Review cases in this scope.</p>}
      <div className="ws-actions">
        <button
          disabled={busy || page === 0}
          onClick={() => setPage((v) => v - 1)}
        >
          Previous medical cases
        </button>
        <span>
          Page {page + 1} · {data.totalElements} cases
        </span>
        <button
          disabled={busy || page + 1 >= data.totalPages}
          onClick={() => setPage((v) => v + 1)}
        >
          Next medical cases
        </button>
      </div>
    </section>
  );
}
