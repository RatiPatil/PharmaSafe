import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { ProcessingCaseWorkbench } from "./ProcessingCaseWorkbench";
const mocks = vi.hoisted(() => ({ api: vi.fn() }));
vi.mock("../config/api", () => ({ apiRequest: mocks.api }));
vi.mock("../components/CaseEntryForm", () => ({
  CaseEntryForm: ({
    createEndpoint,
    onSaveSuccess,
    onDirtyChange,
  }: {
    createEndpoint?: string;
    onSaveSuccess: (v: unknown) => void;
    onDirtyChange: (v: boolean) => void;
  }) => (
    <div data-testid="clinical-editor">
      {createEndpoint || "Existing case editor"}
      <button
        onClick={() => {
          onDirtyChange(true);
        }}
      >
        Edit clinical data
      </button>
      <button onClick={() => onSaveSuccess(record)}>
        Save clinical test case
      </button>
    </div>
  ),
}));
vi.mock("./CaseRecordView", () => ({
  CaseRecordView: () => <p>Read-only clinical inspection</p>,
}));
vi.mock("./DuplicateCheck", () => ({
  DuplicateCheck: () => <p>Scoped duplicate search</p>,
}));
vi.mock("./CaseLaboratory", () => ({
  CaseLaboratory: () => <p>Clinical laboratory module</p>,
}));
vi.mock("./CaseDocuments", () => ({
  CaseDocuments: () => <p>Clinical PDF archive</p>,
}));
vi.mock("./CaseActivities", () => ({
  CaseActivities: () => <p>Clinical follow-up module</p>,
}));
const record = {
  id: 19,
  caseNumber: "CASE-19",
  rowVersion: 4,
  workflowState: "Data Entry",
  locked: false,
  patient: { patientIdentifier: "SYNTHETIC-P1" },
  products: [{ productName: "Synthetic Drug" }],
  events: [{ eventVerbatim: "Rash" }],
};
const readiness = {
  linked: true,
  caseRowVersion: 4,
  facts: {
    patient: true,
    reporter: true,
    suspectProduct: true,
    adverseEvent: true,
    eventCoding: true,
    narrative: true,
  },
  missing: [],
  evidence: [
    {
      id: 1,
      kind: "DUPLICATE_REVIEW",
      outcome: "NO_DUPLICATE",
      notes: "Reviewed fictional source identifiers",
      dictionaryReference: "",
      recordedBy: "worker",
      recordedAt: "2026-11-18T03:30:00Z",
      caseRowVersion: 4,
      current: true,
    },
  ],
};
beforeEach(() => {
  vi.clearAllMocks();
  mocks.api.mockResolvedValue(record);
});
it("creates a real case through the task endpoint and reports saved work to its parent", async () => {
  const saved = vi.fn();
  render(
    <ProcessingCaseWorkbench
      assignmentId={2}
      caseId={null}
      canWork
      employee
      onSaved={saved}
      onDirtyChange={() => {}}
    />,
  );
  fireEvent.click(
    screen.getByRole("button", { name: "Create clinical case for this task" }),
  );
  expect(screen.getByTestId("clinical-editor")).toHaveTextContent(
    "/api/processing-teams/work/2/clinical-case",
  );
  fireEvent.click(
    screen.getByRole("button", { name: "Save clinical test case" }),
  );
  expect(saved).toHaveBeenCalled();
  expect(screen.getByText(/Saved CASE-19/)).toBeVisible();
});
it("links only through the scoped API and preserves failures for correction", async () => {
  mocks.api.mockRejectedValue(
    Error("Link a case currently assigned to your employee account"),
  );
  render(
    <ProcessingCaseWorkbench
      assignmentId={2}
      caseId={null}
      canWork
      employee
      onSaved={() => {}}
      onDirtyChange={() => {}}
    />,
  );
  fireEvent.change(screen.getByLabelText("Existing case number"), {
    target: { value: "OTHER-CASE" },
  });
  fireEvent.click(
    screen.getByRole("button", { name: "Link my existing case" }),
  );
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "currently assigned",
  );
  expect(screen.getByLabelText("Existing case number")).toHaveValue(
    "OTHER-CASE",
  );
  expect(mocks.api).toHaveBeenCalledWith(
    "/api/processing-teams/work/2/clinical-case/link",
    "POST",
    { caseNumber: "OTHER-CASE" },
  );
});
it("employee records revision-specific evidence and can open the actual supporting modules", async () => {
  render(
    <ProcessingCaseWorkbench
      assignmentId={2}
      caseId={19}
      canWork
      employee
      readiness={readiness}
      onSaved={() => {}}
      onDirtyChange={() => {}}
    />,
  );
  await screen.findByTestId("clinical-editor");
  fireEvent.click(
    screen.getByRole("button", { name: "Duplicate & coding review" }),
  );
  expect(screen.getByText("Scoped duplicate search")).toBeVisible();
  fireEvent.change(
    screen.getByLabelText("Review rationale & source references"),
    { target: { value: "Checked source identifiers and possible matches" } },
  );
  fireEvent.click(
    screen.getByRole("button", { name: "Record processing review" }),
  );
  await waitFor(() =>
    expect(mocks.api).toHaveBeenCalledWith(
      "/api/processing-teams/work/2/processing-evidence",
      "POST",
      expect.objectContaining({
        expectedCaseVersion: 4,
        kind: "DUPLICATE_REVIEW",
        outcome: "NO_DUPLICATE",
      }),
    ),
  );
  fireEvent.click(
    screen.getByRole("button", { name: "Laboratory", exact: true }),
  );
  expect(screen.getByText("Clinical laboratory module")).toBeVisible();
  fireEvent.click(screen.getByRole("button", { name: "Follow-up & contacts" }));
  expect(screen.getByText("Clinical follow-up module")).toBeVisible();
  fireEvent.click(screen.getByRole("button", { name: "Case PDFs" }));
  expect(screen.getByText("Clinical PDF archive")).toBeVisible();
});
it("supervisors inspect clinical data and preserved reviews without editing controls", async () => {
  render(
    <ProcessingCaseWorkbench
      assignmentId={2}
      caseId={19}
      canWork={false}
      employee={false}
      readiness={readiness}
      onSaved={() => {}}
      onDirtyChange={() => {}}
    />,
  );
  await screen.findByText("Read-only clinical inspection");
  expect(screen.queryByTestId("clinical-editor")).not.toBeInTheDocument();
  expect(
    screen.queryByRole("button", { name: "Record processing review" }),
  ).not.toBeInTheDocument();
  expect(
    screen.getByText("Reviewed fictional source identifiers"),
  ).toBeVisible();
});
it("keeps failed review text and its dirty state so a refresh cannot erase the response", async () => {
  const dirty = vi.fn();
  mocks.api.mockImplementation(async (_p: string, method?: string) => {
    if (method === "POST")
      throw Error("Case changed. Reload before recording processing evidence.");
    return record;
  });
  render(
    <ProcessingCaseWorkbench
      assignmentId={2}
      caseId={19}
      canWork
      employee
      readiness={readiness}
      onSaved={() => {}}
      onDirtyChange={dirty}
    />,
  );
  await screen.findByTestId("clinical-editor");
  fireEvent.click(
    screen.getByRole("button", { name: "Duplicate & coding review" }),
  );
  fireEvent.change(
    screen.getByLabelText("Review rationale & source references"),
    { target: { value: "Unsaved evidence rationale" } },
  );
  fireEvent.click(
    screen.getByRole("button", { name: "Record processing review" }),
  );
  expect(await screen.findByRole("alert")).toHaveTextContent("Case changed");
  expect(
    screen.getByLabelText("Review rationale & source references"),
  ).toHaveValue("Unsaved evidence rationale");
  expect(dirty).toHaveBeenLastCalledWith(true);
});
it("completed handoff task provides a guarded Medical Review action", async () => {
  render(
    <ProcessingCaseWorkbench
      assignmentId={2}
      caseId={19}
      canWork={false}
      canHandoff
      employee
      readiness={readiness}
      onSaved={() => {}}
      onDirtyChange={() => {}}
    />,
  );
  await screen.findByRole("heading", {
    name: "Send prepared case to Medical Review",
  });
  fireEvent.change(screen.getByLabelText("Medical handoff reason"), {
    target: { value: "Source verification and coding complete" },
  });
  fireEvent.click(
    screen.getByRole("button", { name: "Send case to Medical Review" }),
  );
  await waitFor(() =>
    expect(mocks.api).toHaveBeenCalledWith(
      "/api/processing-teams/work/2/medical-handoff",
      "POST",
      {
        expectedCaseVersion: 4,
        reason: "Source verification and coding complete",
      },
    ),
  );
});

it("records coding releases and source rationale against the saved revision", async () => {
  render(
    <ProcessingCaseWorkbench
      assignmentId={2}
      caseId={19}
      canWork
      employee
      readiness={readiness}
      onSaved={() => {}}
      onDirtyChange={() => {}}
    />,
  );
  await screen.findByTestId("clinical-editor");
  fireEvent.click(
    screen.getByRole("button", { name: "Duplicate & coding review" }),
  );
  fireEvent.change(screen.getByLabelText("Review type", { exact: true }), {
    target: { value: "CODING" },
  });
  fireEvent.change(
    screen.getByLabelText(
      "Dictionary names, releases & product coding reference",
      { exact: true },
    ),
    {
      target: {
        value: "Documented source dictionary releases and product reference",
      },
    },
  );
  fireEvent.change(
    screen.getByLabelText("Review rationale & source references", {
      exact: true,
    }),
    { target: { value: "Checked source terms and coded product reference" } },
  );
  fireEvent.click(
    screen.getByRole("button", { name: "Record processing review" }),
  );
  await waitFor(() =>
    expect(mocks.api).toHaveBeenCalledWith(
      "/api/processing-teams/work/2/processing-evidence",
      "POST",
      expect.objectContaining({
        expectedCaseVersion: 4,
        kind: "CODING",
        outcome: "VERIFIED",
        dictionaryReference:
          "Documented source dictionary releases and product reference",
      }),
    ),
  );
});
