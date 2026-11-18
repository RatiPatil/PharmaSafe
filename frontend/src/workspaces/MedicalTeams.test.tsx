import {
  render,
  screen,
  fireEvent,
  waitFor,
  within,
} from "@testing-library/react";
import { beforeEach, it, expect, vi } from "vitest";
import { MedicalTeamWorkspace } from "./MedicalTeamWorkspace";
import { MedicalTaskDetail } from "./MedicalTaskDetail";
const mocks = vi.hoisted(() => ({
  api: vi.fn(),
  download: vi.fn(),
  logout: vi.fn(),
  user: {
    id: 5,
    role: "ROLE_SCOPED_ADMIN",
    managedRole: "ROLE_MEDICAL_REVIEWER",
    medicalTeamId: null as number | null,
    username: "cp.admin",
    staffCode: "PS-5",
    fullName: "Medical Admin",
  },
}));
vi.mock("../config/api", () => ({
  apiRequest: mocks.api,
  downloadPdf: mocks.download,
}));
vi.mock("../context/AuthContext", () => ({
  useAuth: () => ({ user: mocks.user, logout: mocks.logout }),
}));
const employee = {
  userId: 17,
  staffCode: "PS-17",
  username: "processor.one",
  fullName: "Medical Employee",
  accountStatus: "APPROVED",
  enabled: true,
};
const team = {
  id: 1,
  teamCode: "MR-TL-001",
  name: "Intake Team",
  lead: {
    ...employee,
    userId: 10,
    staffCode: "PS-10",
    username: "lead.one",
    fullName: "Team Lead One",
  },
  active: true,
  registrationOpen: true,
  maxMembers: 10,
  occupiedSeats: 1,
  registrationPath:
    "#/access/ROLE_MEDICAL_REVIEWER/register?team=aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
  rowVersion: 0,
};
const assignment = {
  id: 42,
  teamId: 1,
  teamCode: "MR-TL-001",
  teamName: "Intake Team",
  teamActive: true,
  employee,
  title: "Verify source criteria",
  stage: "SOURCE_REVIEW",
  priority: "Routine",
  dueAt: "2026-12-15T10:00:00Z",
  status: "NOT_STARTED",
  progress: 0,
  rowVersion: 3,
  updatedAt: "2026-11-15T12:00:00Z",
  overdue: false,
};
const doc = {
  id: "instruction-1",
  documentKind: "ASSIGNMENT",
  fileName: "assignment.pdf",
  generatedAt: "2026-11-15T12:00:00Z",
  generatedBy: "lead.one",
  sha256: "a".repeat(64),
};
const detail = {
  ...assignment,
  supervisorNote: "",
  reopenedAt: null,
  description: "Check patient, reporter, suspected drug and adverse event",
  instructions: "Record missing source information",
  caseId: null,
  caseNumber: null,
  canRespond: true,
  updates: { content: [], totalElements: 0, totalPages: 0 },
  documents: { content: [doc], totalElements: 1, totalPages: 1 },
};
let role = "ADMIN";
beforeEach(() => {
  vi.clearAllMocks();
  role = "ADMIN";
  mocks.user.role = "ROLE_SCOPED_ADMIN";
  mocks.user.medicalTeamId = null;
  window.history.replaceState(null, "", "/#/medical-teams");
  mocks.download.mockResolvedValue(undefined);
  mocks.api.mockImplementation(async (path: string, method?: string) => {
    if (method === "POST" || method === "PUT") return {};
    if (path.includes("/overview"))
      return {
        level: role,
        actor: employee,
        statusCounts: {
          NOT_STARTED: 1,
          IN_PROGRESS: 0,
          BLOCKED: 0,
          COMPLETED: 0,
        },
        totalAssignments: 1,
        overdue: 0,
        pendingRequests: 0,
        activeEmployees: 1,
        teams: [
          {
            teamId: 1,
            teamCode: team.teamCode,
            name: team.name,
            assigned: 1,
            completed: 0,
          },
        ],
        updatedAt: "2026-11-15T12:00:00Z",
      };
    if (path === "/api/medical-teams/teams") return [team];
    if (path.startsWith("/api/medical-teams/work/")) return detail;
    if (path.includes("/unassigned-employees"))
      return { content: [employee], totalElements: 1, totalPages: 1 };
    if (path.includes("/members"))
      return {
        content: [{ ...employee, membershipStatus: "ACTIVE" }],
        totalElements: 1,
        totalPages: 1,
      };
    return { content: [assignment], totalElements: 1, totalPages: 1 };
  });
});
it("shows scoped supervisor charts and opens a team employee directory", async () => {
  render(<MedicalTeamWorkspace />);
  await screen.findByTestId("medical-admin-home");
  expect(
    screen.getByRole("img", { name: "1 scoped work assignments" }),
  ).toBeVisible();
  fireEvent.change(screen.getByLabelText("Team dashboard"), {
    target: { value: "1" },
  });
  fireEvent.click(
    within(
      screen.getByRole("navigation", {
        name: "Medical Review team navigation",
      }),
    ).getByRole("button", { name: "Employees", exact: true }),
  );
  await screen.findByRole("heading", { name: "MR-TL-001 employee directory" });
  expect(await screen.findByRole("cell", { name: "PS-17" })).toBeVisible();
  expect(
    screen.getByRole("button", { name: "Assign by staff ID" }),
  ).toBeVisible();
});
it("employee dashboard offers only their assignments and no supervisor actions", async () => {
  role = "EMPLOYEE";
  mocks.user.role = "ROLE_MEDICAL_REVIEWER";
  mocks.user.medicalTeamId = 1;
  render(<MedicalTeamWorkspace />);
  await screen.findByTestId("medical-employee-home");
  const nav = screen.getByRole("navigation", {
    name: "Medical Review team navigation",
  });
  expect(
    within(nav).queryByRole("button", { name: "Assign work" }),
  ).not.toBeInTheDocument();
  expect(
    within(nav).queryByRole("button", { name: "Registration requests" }),
  ).not.toBeInTheDocument();
  expect(screen.queryByLabelText("Team dashboard")).not.toBeInTheDocument();
  expect(screen.getByRole("heading", { name: "My assignments" })).toBeVisible();
});
it("admin creates a separate Team Lead account through the controlled form", async () => {
  render(<MedicalTeamWorkspace />);
  await screen.findByTestId("medical-admin-home");
  fireEvent.click(
    screen.getByRole("button", { name: "Team Leads & teams", exact: true }),
  );
  await screen.findByRole("heading", { name: "Create a Team Lead and team" });
  for (const [label, value] of Object.entries({
    "Team name": "Review Team",
    "Team Lead full name": "Synthetic Team Lead",
    "Team Lead username": "lead.new",
    "Team Lead official email": "lead.new@example.test",
    "Initial Team Lead password": "SyntheticLeadPassword123!",
    "Creation reason": "Medical team provisioning approved",
  }))
    fireEvent.change(screen.getByLabelText(label), { target: { value } });
  fireEvent.click(
    screen.getByRole("button", {
      name: "Create Team Lead & team",
      exact: true,
    }),
  );
  await waitFor(() =>
    expect(mocks.api).toHaveBeenCalledWith(
      "/api/medical-teams/teams",
      "POST",
      expect.objectContaining({
        username: "lead.new",
        reason: "Medical team provisioning approved",
      }),
    ),
  );
});
it("only the employee can submit the progress form and the version is sent", async () => {
  render(
    <MedicalTaskDetail
      id={42}
      level="EMPLOYEE"
      onClose={() => {}}
      onUpdated={() => {}}
    />,
  );
  await screen.findByRole("heading", { name: "Verify source criteria" });
  fireEvent.change(screen.getByLabelText("Progress status"), {
    target: { value: "COMPLETED" },
  });
  fireEvent.change(screen.getByLabelText("Work completed"), {
    target: { value: "Verified all four criteria against source records" },
  });
  fireEvent.change(screen.getByLabelText("Update or correction reason"), {
    target: { value: "New source information is complete" },
  });
  for (const label of [
    "Source clinical evidence reviewed",
    "Seriousness criteria reviewed",
    "Causality assessments recorded",
    "Expectedness reviewed against source",
  ])
    fireEvent.click(screen.getByLabelText(label));
  fireEvent.click(
    screen.getByRole("button", {
      name: "Submit progress & generate response PDF",
    }),
  );
  await waitFor(() =>
    expect(mocks.api).toHaveBeenCalledWith(
      "/api/medical-teams/work/42/updates",
      "POST",
      expect.objectContaining({
        expectedVersion: 3,
        status: "COMPLETED",
        progress: 100,
        checklist: expect.objectContaining({
          sourceReviewed: true,
          expectednessReviewed: true,
        }),
      }),
    ),
  );
});
it("supervisor gets private PDF downloads without an employee response editor", async () => {
  render(
    <MedicalTaskDetail
      id={42}
      level="TEAM_LEAD"
      onClose={() => {}}
      onUpdated={() => {}}
    />,
  );
  await screen.findByRole("heading", { name: "Verify source criteria" });
  expect(
    screen.queryByRole("heading", { name: "Submit my progress" }),
  ).not.toBeInTheDocument();
  fireEvent.click(
    screen.getByRole("button", { name: "Download assignment PDF" }),
  );
  await waitFor(() =>
    expect(mocks.download).toHaveBeenCalledWith(
      "/api/medical-teams/work/42/documents/instruction-1/pdf",
      "assignment.pdf",
    ),
  );
});
it("stale progress errors preserve unsaved work and are never automatically retried", async () => {
  mocks.api.mockImplementation(async (_path: string, method?: string) => {
    if (method === "POST")
      throw Error("Assignment changed. Reload before submitting progress.");
    return detail;
  });
  render(
    <MedicalTaskDetail
      id={42}
      level="EMPLOYEE"
      onClose={() => {}}
      onUpdated={() => {}}
    />,
  );
  await screen.findByRole("heading", { name: "Verify source criteria" });
  fireEvent.change(screen.getByLabelText("Work completed"), {
    target: { value: "My unsaved source review" },
  });
  fireEvent.change(screen.getByLabelText("Update or correction reason"), {
    target: { value: "Documenting the source review" },
  });
  fireEvent.click(
    screen.getByRole("button", {
      name: "Submit progress & generate response PDF",
    }),
  );
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "Assignment changed",
  );
  expect(screen.getByLabelText("Work completed")).toHaveValue(
    "My unsaved source review",
  );
  expect(mocks.api.mock.calls.filter((c) => c[1] === "POST")).toHaveLength(1);
  expect(screen.getByRole("button", { name: "Refresh task" })).toBeEnabled();
});

it("keeps the active-team statistic aligned with the selected dashboard", async () => {
  const original = mocks.api.getMockImplementation()!;
  mocks.api.mockImplementation(async (path: string, ...args: unknown[]) =>
    path === "/api/medical-teams/teams"
      ? [team, { ...team, id: 2, teamCode: "MR-TL-002", name: "Second Team" }]
      : original(path, ...args),
  );
  const { container } = render(<MedicalTeamWorkspace />);
  await screen.findByTestId("medical-admin-home");
  expect(container.querySelector(".cp-operation-strip")).toHaveTextContent(
    "2 active teams in scope",
  );
  fireEvent.change(screen.getByLabelText("Team dashboard"), {
    target: { value: "1" },
  });
  await waitFor(() =>
    expect(container.querySelector(".cp-operation-strip")).toHaveTextContent(
      "1 active team in scope",
    ),
  );
});
