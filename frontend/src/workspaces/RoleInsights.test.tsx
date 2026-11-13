import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { beforeEach, describe, it, expect, vi } from "vitest";
import { InsightChart, RoleInsights } from "./RoleInsights";
import { apiRequest } from "../config/api";
vi.mock("../config/api", () => ({ apiRequest: vi.fn() }));
beforeEach(() => vi.resetAllMocks());
it("shows actual chart values and supports legend selection", () => {
  render(
    <InsightChart
      title="Case workflow"
      description="Real work records"
      values={{ Draft: 3, Approved: 2 }}
      donut
    />,
  );
  expect(
    screen.getByRole("img", { name: "Case workflow: Draft 3, Approved 2" }),
  ).toBeInTheDocument();
  const approved = screen.getByRole("button", { name: "Approved 2" });
  fireEvent.click(approved);
  expect(approved).toHaveAttribute("aria-pressed", "true");
});
it("uses an empty state instead of fabricating chart records", () => {
  render(
    <InsightChart
      title="Report work"
      description="Reports"
      values={{ Pending: 0 }}
      donut
    />,
  );
  expect(
    screen.getByText("No records in this category yet."),
  ).toBeInTheDocument();
  expect(screen.queryByRole("img")).not.toBeInTheDocument();
});
it("loads analytics for the exact selected employee and role", async () => {
  vi.mocked(apiRequest).mockResolvedValue({
    totalCases: 2,
    totalReports: 1,
    totalActivity: 4,
    caseStates: { Draft: 2 },
    casePriorities: { Routine: 2 },
    reportStates: { Submitted: 1 },
    activity: { CASE_UPDATED: 4 },
  });
  render(<RoleInsights role="ROLE_CASE_PROCESSOR" userId={13} />);
  await screen.findByRole("region", { name: "Employee work analytics" });
  expect(apiRequest).toHaveBeenCalledWith(
    "/api/administration/analytics?role=ROLE_CASE_PROCESSOR&userId=13",
    "GET",
    undefined,
    expect.any(AbortSignal),
  );
});
it("shows a retry action when analytics cannot load", async () => {
  vi.mocked(apiRequest).mockRejectedValue(new Error("Service unavailable"));
  render(<RoleInsights role="ROLE_CASE_PROCESSOR" />);
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "Service unavailable",
  );
  expect(
    screen.getByRole("button", { name: "Retry dashboard charts" }),
  ).toBeInTheDocument();
});
