import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { it, expect, vi } from "vitest";
import { WorkOrders, CollegeTools, WorkEditor } from "./EducationManagement";
import { apiRequest } from "../config/api";
vi.mock("../config/api", () => ({ apiRequest: vi.fn() }));
const college = {
  id: 4,
  code: "TEST-A",
  name: "Test College",
  active: true,
  rowVersion: 0,
};
const domain = {
  code: "ROLE_CASE_PROCESSOR",
  name: "Case Processor",
  description: "Intake learning",
  stages: ["Case intake"],
  evidenceFields: [],
};
it("creates actual college work from accessible controls", async () => {
  vi.mocked(apiRequest).mockResolvedValue([]);
  render(
    <WorkOrders
      domain={domain}
      colleges={[college]}
      collegeId=""
      canManage
      refresh={0}
      onSaved={vi.fn()}
      onAssign={vi.fn()}
      onDirty={vi.fn()}
    />,
  );
  fireEvent.change(screen.getByLabelText("Work college", { exact: true }), {
    target: { value: "4" },
  });
  for (const [name, value] of Object.entries({
    "College work title": "Source review",
    "College work description": "Review source data",
    "College work instructions": "Document all minimum criteria",
    "College work deadline": "2030-01-20T12:00",
  }))
    fireEvent.change(screen.getByLabelText(name, { exact: true }), {
      target: { value },
    });
  fireEvent.submit(screen.getByRole("form", { name: "Create college work" }));
  await waitFor(() =>
    expect(apiRequest).toHaveBeenCalledWith(
      "/api/education/orders",
      "POST",
      expect.objectContaining({
        collegeId: 4,
        domain: "ROLE_CASE_PROCESSOR",
        title: "Source review",
        stage: "Case intake",
      }),
    ),
  );
});
it("keeps college changes unavailable in the College Administration view", () => {
  render(
    <CollegeTools
      colleges={[college]}
      canManage={false}
      onSaved={vi.fn()}
      onSelect={vi.fn()}
      onDirty={vi.fn()}
    />,
  );
  expect(
    screen.getByRole("button", { name: "View college students" }),
  ).toBeVisible();
  expect(
    screen.queryByRole("button", { name: "Edit college" }),
  ).not.toBeInTheDocument();
  expect(
    screen.queryByRole("form", { name: "Manage college" }),
  ).not.toBeInTheDocument();
});

it("archives original work even when an unsaved deadline was cleared", async () => {
  vi.mocked(apiRequest).mockResolvedValue({});
  const work = {
    id: 8,
    collegeId: 4,
    collegeName: "Test College",
    domain: domain.code,
    stage: "Case intake",
    title: "Original source review",
    description: "Source data",
    instructions: "Check criteria",
    dueAt: "2030-01-20T12:00:00Z",
    requireDocument: false,
    rowVersion: 0,
    archived: false,
    assignedStudents: 0,
  };
  render(
    <WorkEditor
      work={work}
      path="/api/education/orders/8"
      onSaved={vi.fn()}
      onClose={vi.fn()}
    />,
  );
  fireEvent.change(screen.getByLabelText("Work deadline"), {
    target: { value: "" },
  });
  fireEvent.change(screen.getByLabelText("Work change reason"), {
    target: { value: "Withdraw unused order" },
  });
  fireEvent.click(
    screen.getByRole("button", { name: "Remove work", exact: true }),
  );
  fireEvent.click(
    screen.getByRole("button", { name: "Confirm work removal", exact: true }),
  );
  await waitFor(() =>
    expect(apiRequest).toHaveBeenCalledWith(
      "/api/education/orders/8",
      "PUT",
      expect.objectContaining({
        archived: true,
        dueAt: work.dueAt,
        title: work.title,
      }),
    ),
  );
});
