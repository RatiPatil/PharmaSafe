import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { beforeEach, it, expect, vi } from "vitest";
import { PvCaseSeriesWorkspace } from "./PvCaseSeriesWorkspace";
import { PvAnalysisReviews } from "./PvAnalysisReviews";
const mocks = vi.hoisted(() => ({
  api: vi.fn(),
  pdf: vi.fn(),
  user: { role: "ROLE_PV_ANALYST" },
}));
vi.mock("../config/api", () => ({
  apiRequest: mocks.api,
  downloadPdf: mocks.pdf,
}));
vi.mock("../context/AuthContext", () => ({
  useAuth: () => ({ user: mocks.user }),
}));
const series = {
  id: "series-one",
  name: "Focused safety population",
  purpose: "Evaluate a defined population",
  caseCount: 1,
  capturedAt: "2026-11-18T06:00:00Z",
  staffCode: "PS-9",
  criteria: { product: "Example", includeSynthetic: false },
  snapshot: {
    statistics: {
      reviewedCases: 1,
      seriousCases: 1,
      fatalCases: 0,
      products: { Example: 1 },
      reactions: { Rash: 1 },
      outcomes: { Unknown: 1 },
      interpretation: "Descriptive counts only",
    },
    caseListing: [
      {
        caseId: 1,
        caseNumber: "CASE-1",
        sourceRowVersion: 3,
        workflowState: "Approved",
        countryCode: "USA",
        initialReceiptDate: "2026-11-01",
        serious: true,
        fatal: false,
        syntheticRecord: false,
        products: ["Example"],
        reactions: ["Rash"],
        outcomes: ["Unknown"],
      },
    ],
  },
};
beforeEach(() => {
  vi.clearAllMocks();
  mocks.user.role = "ROLE_PV_ANALYST";
  mocks.api.mockImplementation(async (path: string, method?: string) =>
    path.includes("/preview")
      ? {
          content: [
            {
              id: 1,
              caseNumber: "CASE-1",
              primaryProduct: "Example",
              primaryEvent: "Rash",
              workflowState: "Approved",
              initialReceiptDate: "2026-11-01",
            },
          ],
          totalElements: 1,
          totalPages: 1,
        }
      : method === "POST"
        ? series
        : path.endsWith("/series/series-one")
          ? series
          : { content: [series], totalElements: 1, totalPages: 1 },
  );
});
it("queries reviewed cases, freezes exact criteria and opens saved line listing", async () => {
  const use = vi.fn();
  render(<PvCaseSeriesWorkspace onDirtyChange={() => {}} onUse={use} />);
  fireEvent.change(screen.getByLabelText("Suspect product contains"), {
    target: { value: "Example" },
  });
  fireEvent.click(
    screen.getByRole("button", { name: "Run reviewed-case query" }),
  );
  await screen.findByRole("heading", { name: "Query results · 1 cases" });
  fireEvent.change(screen.getByLabelText("Case-series name"), {
    target: { value: "Focused safety population" },
  });
  fireEvent.change(screen.getByLabelText("Population purpose"), {
    target: { value: "Evaluate source evidence" },
  });
  fireEvent.click(
    screen.getByRole("button", { name: "Freeze case series & generate PDF" }),
  );
  await screen.findByRole("heading", { name: "Case-series products" });
  expect(mocks.api).toHaveBeenCalledWith(
    "/api/pv-analyses/series",
    "POST",
    expect.objectContaining({
      criteria: expect.objectContaining({
        product: "Example",
        includeSynthetic: false,
      }),
    }),
  );
  expect(
    screen.getByRole("table", { name: /Frozen case line listing/ }),
  ).toHaveTextContent("CASE-1 · r3");
  fireEvent.click(
    screen.getByRole("button", { name: "Use case series in analysis" }),
  );
  expect(use).toHaveBeenCalledWith(
    expect.objectContaining({ id: "series-one" }),
  );
});
it("requires a new preview when filters change and preserves the population draft on save failure", async () => {
  render(<PvCaseSeriesWorkspace onDirtyChange={() => {}} onUse={() => {}} />);
  fireEvent.click(
    screen.getByRole("button", { name: "Run reviewed-case query" }),
  );
  await screen.findByLabelText("Case-series name");
  fireEvent.change(screen.getByLabelText("Case-series name"), {
    target: { value: "Unsaved cohort" },
  });
  fireEvent.change(screen.getByLabelText("Population purpose"), {
    target: { value: "Retain this purpose" },
  });
  fireEvent.change(screen.getByLabelText("Suspect product contains"), {
    target: { value: "Changed filter" },
  });
  fireEvent.click(
    screen.getByRole("button", { name: "Freeze case series & generate PDF" }),
  );
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "Run the query again",
  );
  expect(screen.getByLabelText("Case-series name")).toHaveValue(
    "Unsaved cohort",
  );
  expect(
    mocks.api.mock.calls.filter(
      (c) => c[0] === "/api/pv-analyses/series" && c[1] === "POST",
    ),
  ).toHaveLength(0);
});
it("supervisors inspect frozen series without authoring or query forms", async () => {
  mocks.user.role = "ROLE_SCOPED_ADMIN";
  render(
    <PvCaseSeriesWorkspace
      employeeId={9}
      onDirtyChange={() => {}}
      onUse={() => {}}
    />,
  );
  await screen.findByText("Focused safety population");
  fireEvent.click(screen.getByRole("button", { name: "Open case series" }));
  await screen.findByRole("heading", { name: "Case-series products" });
  expect(
    screen.queryByRole("button", { name: "Run reviewed-case query" }),
  ).not.toBeInTheDocument();
  expect(
    screen.queryByRole("button", { name: "Use case series in analysis" }),
  ).not.toBeInTheDocument();
  expect(mocks.api).toHaveBeenCalledWith(
    "/api/pv-analyses/series?page=0&userId=9",
    "GET",
    undefined,
    expect.any(AbortSignal),
  );
  fireEvent.click(
    screen.getByRole("button", { name: "Download case-series PDF" }),
  );
  await waitFor(() =>
    expect(mocks.pdf).toHaveBeenCalledWith(
      "/api/pv-analyses/series/series-one/pdf",
      "PV-case-series-series-one.pdf",
    ),
  );
});
it("manager submits feedback with the latest review token and due date", async () => {
  mocks.user.role = "ROLE_SAFETY_MANAGER";
  mocks.api.mockResolvedValue({ content: [], totalElements: 0, totalPages: 0 });
  render(
    <PvAnalysisReviews
      analysisId="analysis-one"
      superseded={false}
      onSaved={() => {}}
      onDirtyChange={() => {}}
    />,
  );
  await waitFor(() =>
    expect(
      screen.getByRole("button", {
        name: "Record review & generate response PDF",
      }),
    ).toBeEnabled(),
  );
  fireEvent.change(screen.getByLabelText("Analysis review decision"), {
    target: { value: "FOLLOW_UP_REQUIRED" },
  });
  fireEvent.change(screen.getByLabelText("Review comments"), {
    target: { value: "Review the source evidence and chronology" },
  });
  fireEvent.change(screen.getByLabelText("Follow-up due date"), {
    target: { value: "2026-12-01" },
  });
  fireEvent.click(
    screen.getByRole("button", {
      name: "Record review & generate response PDF",
    }),
  );
  await waitFor(() =>
    expect(mocks.api).toHaveBeenCalledWith(
      "/api/pv-analyses/analysis-one/reviews",
      "POST",
      {
        expectedLatestReviewId: 0,
        outcome: "FOLLOW_UP_REQUIRED",
        comments: "Review the source evidence and chronology",
        followUpDueDate: "2026-12-01",
      },
    ),
  );
});
it("analysts can read feedback and download responses without reviewer controls", async () => {
  mocks.api.mockResolvedValue({
    content: [
      {
        id: 4,
        outcome: "RETURNED",
        comments: "Clarify source interpretation",
        reviewer: "manager",
        reviewedAt: "2026-11-18T06:00:00Z",
        followUpDueDate: null,
      },
    ],
    totalElements: 1,
    totalPages: 1,
  });
  render(
    <PvAnalysisReviews
      analysisId="analysis-one"
      superseded={false}
      onSaved={() => {}}
      onDirtyChange={() => {}}
    />,
  );
  await screen.findByText("Clarify source interpretation");
  expect(screen.queryByLabelText("Review comments")).not.toBeInTheDocument();
  fireEvent.click(
    screen.getByRole("button", { name: "Download review response PDF" }),
  );
  await waitFor(() =>
    expect(mocks.pdf).toHaveBeenCalledWith(
      "/api/pv-analyses/analysis-one/reviews/4/pdf",
      "PV-review-4.pdf",
    ),
  );
});
it("stale manager review keeps comments and reloads the concurrency token before retry", async () => {
  mocks.user.role = "ROLE_SAFETY_MANAGER";
  let token = 4;
  mocks.api.mockImplementation(async (_path: string, method?: string) => {
    if (method === "POST")
      throw Error(
        "Another review was recorded. Reload the review history before deciding.",
      );
    return {
      content: [
        {
          id: token,
          outcome: "RETURNED",
          comments: "Previous feedback",
          reviewer: "manager",
          reviewedAt: "2026-11-18T06:00:00Z",
          followUpDueDate: null,
        },
      ],
      totalElements: 1,
      totalPages: 1,
    };
  });
  render(
    <PvAnalysisReviews
      analysisId="analysis-one"
      superseded={false}
      onSaved={() => {}}
      onDirtyChange={() => {}}
    />,
  );
  await waitFor(() =>
    expect(
      screen.getByRole("button", {
        name: "Record review & generate response PDF",
      }),
    ).toBeEnabled(),
  );
  fireEvent.change(screen.getByLabelText("Review comments"), {
    target: { value: "My unsaved managerial reasoning" },
  });
  fireEvent.click(
    screen.getByRole("button", {
      name: "Record review & generate response PDF",
    }),
  );
  expect(await screen.findByRole("alert")).toHaveTextContent("Another review");
  expect(screen.getByLabelText("Review comments")).toHaveValue(
    "My unsaved managerial reasoning",
  );
  token = 5;
  vi.spyOn(window, "confirm").mockReturnValue(true);
  fireEvent.click(
    screen.getByRole("button", { name: "Reload review history" }),
  );
  await waitFor(() =>
    expect(mocks.api.mock.calls.filter((c) => c[1] === "GET")).toHaveLength(2),
  );
  fireEvent.click(
    screen.getByRole("button", {
      name: "Record review & generate response PDF",
    }),
  );
  await waitFor(() =>
    expect(mocks.api).toHaveBeenLastCalledWith(
      "/api/pv-analyses/analysis-one/reviews",
      "POST",
      expect.objectContaining({
        expectedLatestReviewId: 5,
        comments: "My unsaved managerial reasoning",
      }),
    ),
  );
  vi.restoreAllMocks();
});
