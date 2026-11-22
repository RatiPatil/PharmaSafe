import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { beforeEach, it, expect, vi } from "vitest";
import { StudentRegistration } from "./StudentRegistration";
import { EducationWorkspace } from "./EducationWorkspace";
import catalogRoles from "../config/publicRoleCatalog.json";
const m = vi.hoisted(() => ({
  api: vi.fn(),
  user: {
    role: "ROLE_STUDENT",
    id: 18,
    username: "student.one",
    fullName: "Synthetic Student",
  },
  logout: vi.fn(),
}));
vi.mock("../config/api", () => ({ apiRequest: m.api, downloadPdf: vi.fn() }));
vi.mock("../context/AuthContext", () => ({
  useAuth: () => ({ user: m.user, logout: m.logout }),
}));
const domains = catalogRoles.map((d) => ({
  code: d.role,
  name: d.name,
  description: "Domain learning activity",
  stages: ["Source review"],
  evidenceFields: [],
}));
const colleges = [
  {
    id: 4,
    code: "SYN-A",
    name: "Synthetic College Alpha",
    active: true,
    rowVersion: 0,
  },
];
const stats = {
  total: 3,
  averageProgress: 50,
  overdue: 1,
  statusCounts: { NOT_STARTED: 1, IN_PROGRESS: 1, COMPLETED: 1 },
};
beforeEach(() => {
  vi.clearAllMocks();
  m.user.role = "ROLE_STUDENT";
  window.history.replaceState(null, "", "/#/education");
  m.api.mockImplementation(async (path: string) =>
    path === "/api/education/catalog"
      ? { colleges, domains }
      : path.startsWith("/api/education/colleges")
        ? colleges
        : path === "/api/education/student-id"
          ? {
              studentCode: "ST-000123",
              expiresAt: "2026-12-01T12:00:00Z",
            }
          : path === "/api/education/register"
            ? {
                studentCode: "SYN-A-0001",
                fullName: "Synthetic Student",
                collegeName: "Synthetic College Alpha",
                registrationStatus: "PENDING_COLLEGE_APPROVAL",
              }
            : path.startsWith("/api/education/overview")
              ? {
                  role: m.user.role,
                  fullName: m.user.fullName,
                  username: m.user.username,
                  collegeId: m.user.role === "ROLE_DOMAIN_ADMIN" ? null : 4,
                  studentCode:
                    m.user.role === "ROLE_STUDENT" ? "ST-000123" : null,
                  assignedDomain: null,
                  domains: domains.map((d, i) => ({
                    ...d,
                    authorized: m.user.role !== "ROLE_STUDENT" || i === 0,
                    status:
                      i === 0
                        ? "APPROVED"
                        : m.user.role === "ROLE_DOMAIN_ADMIN"
                          ? "OUTSIDE_SCOPE"
                          : "PENDING",
                    stats:
                      i === 0 || m.user.role !== "ROLE_STUDENT" ? stats : null,
                  })),
                  updatedAt: "2026-11-18T12:00:00Z",
                }
              : { content: [], totalElements: 0, totalPages: 0, page: 0 },
  );
});
it("requires all seven domains without exposing or reserving a Student ID", async () => {
  window.history.replaceState(null, "", "/#/students/register");
  render(<StudentRegistration />);
  await screen.findByRole("option", {
    name: "Synthetic College Alpha · SYN-A",
  });
  expect(
    screen.getByRole("button", { name: "Register student" }),
  ).toBeDisabled();
  fireEvent.change(screen.getByLabelText("College", { exact: true }), {
    target: { value: "4" },
  });
  expect(screen.queryByText("ST-000123")).not.toBeInTheDocument();
  expect(
    m.api.mock.calls.some(([path]) => path === "/api/education/student-id"),
  ).toBe(false);
  expect(
    screen.getByRole("button", { name: "Register student" }),
  ).toBeDisabled();
  fireEvent.click(
    screen.getByRole("checkbox", {
      name: new RegExp(domains[0].name.replace("/", "\\/")),
    }),
  );
  expect(
    screen.getByRole("button", { name: "Register student" }),
  ).toBeDisabled();
  for (const d of domains.slice(1))
    fireEvent.click(
      screen.getByRole("checkbox", {
        name: new RegExp(d.name.replace("/", "\\/")),
      }),
    );
  expect(
    screen.getByRole("button", { name: "Register student" }),
  ).toBeEnabled();
});
it("shows the backend-confirmed college identifier only after successful registration", async () => {
  window.history.replaceState(null, "", "/#/students/register");
  render(<StudentRegistration />);
  await screen.findByLabelText("College", { exact: true });
  fireEvent.change(screen.getByLabelText("College", { exact: true }), {
    target: { value: "4" },
  });
  expect(screen.queryByText("SYN-A-0001")).not.toBeInTheDocument();
  for (const [label, value] of Object.entries({
    "Student full name": "Synthetic Student",
    Username: "student.new",
    "Official email address": "student.new@example.test",
    Password: "SyntheticPassword123!",
    "Confirm password": "SyntheticPassword123!",
  }))
    fireEvent.change(screen.getByLabelText(label, { exact: true }), {
      target: { value },
    });
  for (const d of domains)
    fireEvent.click(
      screen.getByRole("checkbox", {
        name: new RegExp(d.name.replace("/", "\\/")),
      }),
    );
  fireEvent.submit(screen.getByRole("form", { name: "Student registration" }));
  await screen.findByRole("heading", { name: "Registration Successful" });
  expect(await screen.findByText("SYN-A-0001")).toBeVisible();
  expect(screen.getByRole("button", { name: "Copy Student ID" })).toBeVisible();
  expect(m.api).toHaveBeenCalledWith(
    "/api/education/register",
    "POST",
    expect.objectContaining({
      collegeId: 4,
      domains: domains.map((d) => d.code),
    }),
  );
});
it("student sees all seven domains but pending domains remain inaccessible", async () => {
  render(<EducationWorkspace />);
  await screen.findByRole("heading", { name: "Student dashboard" });
  await waitFor(() =>
    expect(screen.getAllByRole("button", { name: /^Open / })).toHaveLength(7),
  );
  const open = screen.getAllByRole("button", { name: /^Open / });
  expect(open.filter((b) => !(b as HTMLButtonElement).disabled)).toHaveLength(
    1,
  );
  expect(
    screen.queryByRole("button", { name: "Colleges & administrators" }),
  ).not.toBeInTheDocument();
  expect(screen.getByText("ST-000123")).toBeVisible();
  expect(
    screen.queryByRole("navigation", { name: "College workspace navigation" }),
  ).not.toBeInTheDocument();
  expect(
    screen.queryByRole("button", { name: "Toggle college navigation" }),
  ).not.toBeInTheDocument();
  expect(
    screen.getByRole("region", { name: "Your learning workspace" }),
  ).toBeVisible();
});
it("Domain Admin gets seven authorized domain workspaces", async () => {
  m.user.role = "ROLE_DOMAIN_ADMIN";
  render(<EducationWorkspace />);
  await screen.findByRole("heading", { name: "Domain Admin dashboard" });
  await waitFor(() =>
    expect(screen.getAllByRole("button", { name: /^Open / })).toHaveLength(7),
  );
  expect(
    screen
      .getAllByRole("button", { name: /^Open / })
      .filter((b) => (b as HTMLButtonElement).disabled),
  ).toHaveLength(0);
  expect(
    screen.queryByRole("navigation", { name: "College workspace navigation" }),
  ).not.toBeInTheDocument();
});
it("College Administration can open each domain through the same workspace and see real counts", async () => {
  m.user.role = "ROLE_COLLEGE_ADMIN";
  render(<EducationWorkspace />);
  await screen.findByRole("heading", { name: "College Administration dashboard" });
  await waitFor(() =>
    expect(screen.getAllByRole("button", { name: /^Open / })).toHaveLength(7),
  );
  expect(
    screen.queryByRole("navigation", { name: "College workspace navigation" }),
  ).not.toBeInTheDocument();
  fireEvent.click(
    screen.getByRole("button", { name: "Open " + domains[1].name }),
  );
  await screen.findByRole("heading", { name: "Work status distribution" });
  expect(
    screen.getByRole("button", { name: "Not started: 1 tasks" }),
  ).toBeVisible();
  expect(
    screen.getByRole("button", { name: "Assign student work" }),
  ).toBeVisible();
  expect(
    screen.queryByRole("button", { name: "Colleges & administrators" }),
  ).not.toBeInTheDocument();
});

it("keeps the student form visible while colleges await configuration", async () => {
  m.api.mockResolvedValue({ colleges: [], domains });
  render(<StudentRegistration />);
  expect(
    await screen.findByRole("form", { name: "Student registration" }),
  ).toBeVisible();
  expect(
    screen.getByRole("heading", {
      name: "Your college must be added before registration",
    }),
  ).toBeVisible();
  expect(screen.getByLabelText("College", { exact: true })).toBeDisabled();
  expect(
    screen.getByRole("button", { name: "Register student" }),
  ).toBeDisabled();
  expect(screen.getByLabelText("Student full name")).toBeVisible();
});

it("keeps Domain Admin college management inside the selected domain", async () => {
  m.user.role = "ROLE_DOMAIN_ADMIN";
  render(<EducationWorkspace />);
  await screen.findByRole("button", { name: "Open " + domains[0].name });
  fireEvent.click(
    screen.getByRole("button", { name: "Open " + domains[0].name }),
  );
  await screen.findByRole("heading", { name: "Work status distribution" });
  fireEvent.click(
    screen.getByRole("button", { name: "Colleges", exact: true }),
  );
  await screen.findByRole("heading", {
    name: "College management",
    exact: true,
  });
  expect(window.location.hash).toBe(
    "#/education/" + domains[0].code + "/colleges",
  );
});

it("College Administration grants access separately from seven cards without college counts", async () => {
  m.user.role = "ROLE_COLLEGE_ADMIN";
  const base = m.api.getMockImplementation()!;
  const pending = {
    id: 91,
    fullName: "Pending Student",
    email: "pending@example.test",
    studentCode: "SYN-A-0001",
    createdAt: "2026-11-19T10:00:00Z",
    pendingDomains: domains.map((d) => ({
      domain: d.code,
      name: d.name,
      rowVersion: 0,
    })),
  };
  m.api.mockImplementation(async (path: string, ...args: unknown[]) =>
    path.startsWith("/api/education/student-access?")
      ? { content: [pending], totalElements: 1, totalPages: 1, page: 0 }
      : path.endsWith("/91/decision")
        ? { updatedDomains: 7 }
        : base(path, ...args),
  );
  const { container } = render(<EducationWorkspace />);
  await screen.findByRole("button", {
    name: "Grant Student Access",
    exact: true,
  });
  expect(container.querySelectorAll(".edu-domain-card")).toHaveLength(7);
  for (const card of container.querySelectorAll(".edu-domain-card"))
    expect(card.textContent).not.toContain("Colleges");
  fireEvent.click(
    screen.getByRole("button", { name: "Grant Student Access", exact: true }),
  );
  await screen.findByText("pending@example.test");
  fireEvent.click(screen.getByRole("button", { name: "Approve student" }));
  expect(
    m.api.mock.calls.some(([p]) => String(p).endsWith("/91/decision")),
  ).toBe(false);
  fireEvent.change(screen.getByLabelText("Decision reason"), {
    target: { value: "Enrollment verified by college" },
  });
  fireEvent.submit(
    screen.getByRole("form", { name: "Confirm student access" }),
  );
  await screen.findByText(
    "Approved 7 pending domain requests for Pending Student.",
  );
  expect(m.api).toHaveBeenCalledWith(
    "/api/education/student-access/91/decision",
    "POST",
    expect.objectContaining({
      status: "APPROVED",
      expectedVersions: Object.fromEntries(domains.map((d) => [d.code, 0])),
    }),
  );
});

it("failed registration keeps the draft and never displays a success or invented ID", async () => {
  const base = m.api.getMockImplementation()!;
  m.api.mockImplementation(async (path: string, ...args: unknown[]) => {
    if (path === "/api/education/register")
      throw new Error("Email already registered");
    return base(path, ...args);
  });
  render(<StudentRegistration />);
  await screen.findByRole("option", {
    name: "Synthetic College Alpha · SYN-A",
  });
  fireEvent.change(screen.getByLabelText("College", { exact: true }), {
    target: { value: "4" },
  });
  for (const [label, value] of Object.entries({
    "Student full name": "Synthetic Student",
    Username: "student.new",
    "Official email address": "student.new@example.test",
    Password: "SyntheticPassword123!",
    "Confirm password": "SyntheticPassword123!",
  }))
    fireEvent.change(screen.getByLabelText(label, { exact: true }), {
      target: { value },
    });
  for (const d of domains)
    fireEvent.click(
      screen.getByRole("checkbox", {
        name: new RegExp(d.name.replace("/", "\\/")),
      }),
    );
  fireEvent.submit(screen.getByRole("form", { name: "Student registration" }));
  await screen.findByText("Email already registered");
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  expect(screen.getByLabelText("Student full name")).toHaveValue(
    "Synthetic Student",
  );
  expect(screen.queryByText("SYN-A-0001")).not.toBeInTheDocument();
});

it("completed work uses a refreshable domain URL and server-side status filter", async () => {
  m.user.role = "ROLE_DOMAIN_ADMIN";
  window.history.replaceState(
    null,
    "",
    "/#/education/ROLE_CASE_PROCESSOR/completed",
  );
  render(<EducationWorkspace />);
  await waitFor(() =>
    expect(
      m.api.mock.calls.some(
        ([p]) =>
          String(p).startsWith("/api/education/tasks?") &&
          String(p).includes("status=COMPLETED") &&
          String(p).includes("domain=ROLE_CASE_PROCESSOR"),
      ),
    ).toBe(true),
  );
  expect(window.location.hash).toBe(
    "#/education/ROLE_CASE_PROCESSOR/completed",
  );
});

for (const role of [
  "ROLE_STUDENT",
  "ROLE_COLLEGE_ADMIN",
  "ROLE_DOMAIN_ADMIN",
]) {
  it(
    role +
      " keeps workspace filters out of profile and settings while preserving data filters",
    async () => {
      m.user.role = role;
      const base = m.api.getMockImplementation()!;
      m.api.mockImplementation(async (path: string, ...args: unknown[]) =>
        path === "/api/education/preferences"
          ? { compactView: false, reduceMotion: false }
          : path === "/api/education/profile"
            ? {
                fullName: "Synthetic Student",
                email: "synthetic@example.test",
                username: "student.one",
                rowVersion: 0,
              }
            : base(path, ...args),
      );
      window.history.replaceState(
        null,
        "",
        `/#/education/${domains[0].code}/work`,
      );
      render(<EducationWorkspace />);
      await screen.findByLabelText("Work status filter");
      fireEvent.change(screen.getByLabelText("Work status filter"), {
        target: { value: "IN_PROGRESS" },
      });
      if (role !== "ROLE_DOMAIN_ADMIN") {
        fireEvent.click(
          screen.getByRole("button", { name: "Profile", exact: true }),
        );
        await screen.findByRole("heading", { name: "My profile", exact: true });
        expect(
          screen.queryByRole("region", { name: "Workspace filters" }),
        ).not.toBeInTheDocument();
        expect(
          screen.queryByLabelText("Work status filter"),
        ).not.toBeInTheDocument();
        await waitFor(() =>
          expect(
            m.api.mock.calls
              .filter(([p]) => String(p).startsWith("/api/education/overview?"))
              .at(-1)?.[0],
          ).not.toContain("status="),
        );
      }
      fireEvent.click(
        screen.getByRole("button", { name: "Settings", exact: true }),
      );
      await screen.findByRole("heading", {
        name: "Workspace settings",
        exact: true,
      });
      expect(
        screen.queryByLabelText("Work status filter"),
      ).not.toBeInTheDocument();
      fireEvent.click(
        screen.getByRole("button", {
          name:
            role === "ROLE_STUDENT" ? "My assigned work" : "Work Management",
          exact: true,
        }),
      );
      expect(await screen.findByLabelText("Work status filter")).toHaveValue(
        "IN_PROGRESS",
      );
    },
  );
}
