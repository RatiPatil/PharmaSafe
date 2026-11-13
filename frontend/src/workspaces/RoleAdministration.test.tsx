import {
  render,
  screen,
  fireEvent,
  waitFor,
  within,
} from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { RoleAdministration, AdminRoleDirectory } from "./RoleAdministration";
import { apiRequest } from "../config/api";
const auth = vi.hoisted(() => ({
  user: {
    id: 1,
    username: "processor-admin",
    fullName: "Processor Administrator",
    role: "ROLE_SCOPED_ADMIN",
    managedRole: "ROLE_CASE_PROCESSOR",
    staffCode: "PS-1",
  },
  logout: vi.fn(),
}));
vi.mock("../context/AuthContext", () => ({ useAuth: () => auth }));
vi.mock("../config/api", () => ({ apiRequest: vi.fn() }));
const request = vi.mocked(apiRequest);
const role = {
  role: "ROLE_CASE_PROCESSOR",
  name: "Case Processor",
  totalUsers: 2,
  pending: 1,
  approved: 1,
  rejected: 0,
  suspended: 0,
  pendingRequests: 1,
  pendingResetRequests: 0,
  assignmentVersion: 1,
  administrator: {
    userId: 1,
    username: "processor-admin",
    fullName: "Processor Administrator",
    staffCode: "PS-1",
  },
  assignedAt: null,
};
const employees = [
  {
    userId: 2,
    id: 20,
    username: "processor.a",
    fullName: "Alex Taylor",
    staffCode: "PS-2",
    role: "ROLE_CASE_PROCESSOR",
    accountStatus: "PENDING",
  },
  {
    userId: 3,
    id: 21,
    username: "processor.b",
    fullName: "Alex Taylor",
    staffCode: "PS-3",
    role: "ROLE_CASE_PROCESSOR",
    accountStatus: "APPROVED",
  },
];
beforeEach(() => {
  vi.resetAllMocks();
  sessionStorage.clear();
  window.history.replaceState(null, "", "/");
  auth.user.role = "ROLE_SCOPED_ADMIN";
  auth.user.managedRole = "ROLE_CASE_PROCESSOR";
  request.mockImplementation(async (path) =>
    path.startsWith("/api/administration/overview")
      ? {
          administrativeLevel: "ROLE_ADMIN",
          managedRole: role.role,
          totalUsers: 2,
          pending: 1,
          approved: 1,
          rejected: 0,
          suspended: 0,
          roles: [role],
        }
      : { content: employees, totalElements: 2, totalPages: 1 },
  );
});
describe("scoped administration screens", () => {
  it("shows only Role Admin modules and server statistics for its assigned role", async () => {
    render(<RoleAdministration />);
    await screen.findByText("Case Processor administration");
    expect(screen.queryByLabelText("Application role")).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Role Admin assignments" }),
    ).not.toBeInTheDocument();
    await waitFor(() =>
      expect(request).toHaveBeenCalledWith(
        "/api/administration/overview?role=ROLE_CASE_PROCESSOR",
        "GET",
        undefined,
        expect.any(AbortSignal),
      ),
    );
    expect(
      screen.getByRole("navigation", { name: "Role Admin navigation" }),
    ).toBeInTheDocument();
  });
  it("distinguishes employees sharing a full name using stable staff codes and usernames", async () => {
    render(<RoleAdministration />);
    fireEvent.click(
      screen.getByRole("button", { name: "My role users", exact: true }),
    );
    await screen.findByText("PS-2 · @processor.a");
    expect(screen.getByText("PS-3 · @processor.b")).toBeInTheDocument();
    expect(screen.getAllByText("Alex Taylor")).toHaveLength(2);
  });
  it("confirms the exact employee and reason before sending an approval", async () => {
    render(<RoleAdministration />);
    fireEvent.click(
      screen.getByRole("button", {
        name: "Registration requests",
        exact: true,
      }),
    );
    await screen.findByText("PS-2 · @processor.a");
    fireEvent.click(
      screen.getByRole("button", { name: "Approve", exact: true }),
    );
    const dialog = screen.getByRole("dialog", {
      name: "Confirm administrative action",
    });
    expect(
      within(dialog).getByText("Alex Taylor · PS-2 · @processor.a"),
    ).toBeInTheDocument();
    fireEvent.change(within(dialog).getByLabelText("Decision reason"), {
      target: { value: "Training verified" },
    });
    fireEvent.click(
      within(dialog).getByRole("button", { name: "Confirm action" }),
    );
    await waitFor(() =>
      expect(request).toHaveBeenCalledWith(
        "/api/administration/registrations/20/approve",
        "PUT",
        { reason: "Training verified" },
      ),
    );
  });
  it("binds employee work requests to the selected unique user ID", async () => {
    render(<RoleAdministration />);
    fireEvent.click(
      screen.getByRole("button", { name: "My role users", exact: true }),
    );
    await screen.findByText("PS-2 · @processor.a");
    const row = screen.getByText("PS-2 · @processor.a").closest("tr")!;
    fireEvent.click(within(row).getByRole("button", { name: "View work" }));
    await waitFor(() =>
      expect(
        request.mock.calls.some(
          ([url]) =>
            url.startsWith("/api/administration/work?") &&
            url.includes("userId=2"),
        ),
      ).toBe(true),
    );
  });
  it("uses database role cards to open the selected Super Admin management view", async () => {
    auth.user.role = "ROLE_ADMIN";
    const open = vi.fn();
    render(<AdminRoleDirectory onOpen={open} />);
    await screen.findByRole("button", { name: "Open role management" });
    fireEvent.click(
      screen.getByRole("button", { name: "Open role management" }),
    );
    expect(open).toHaveBeenCalledWith("ROLE_CASE_PROCESSOR");
    expect(screen.getByText("PS-1 · @processor-admin")).toBeInTheDocument();
  });
  it("keeps provisioning controls exclusive to the existing Super Admin identity", async () => {
    auth.user.role = "ROLE_ADMIN";
    render(<RoleAdministration embedded />);
    fireEvent.change(screen.getByLabelText("Application role"), {
      target: { value: "ROLE_CASE_PROCESSOR" },
    });
    fireEvent.click(
      screen.getByRole("button", { name: "Role Admin assignments" }),
    );
    await screen.findByRole("button", { name: "Save Role Admin assignment" });
    expect(screen.getByLabelText("Unique username")).toBeInTheDocument();
    expect(screen.getByLabelText("Assignment reason")).toBeRequired();
  });
});

it("provides report work for reporting supervisors while retaining their assigned scope", async () => {
  auth.user.managedRole = "ROLE_REPORTING_SPECIALIST";
  render(<RoleAdministration />);
  fireEvent.click(
    screen.getByRole("button", { name: "Reporting work", exact: true }),
  );
  await waitFor(() =>
    expect(
      request.mock.calls.some(
        ([url]) =>
          url.startsWith("/api/administration/report-work?") &&
          url.includes("role=ROLE_REPORTING_SPECIALIST"),
      ),
    ).toBe(true),
  );
});
