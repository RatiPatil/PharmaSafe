import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { beforeEach, it, expect, vi } from "vitest";
import { LoginPage } from "./LoginPage";
const auth = vi.hoisted(() => ({
  login: vi.fn(),
  clearError: vi.fn(),
  error: null,
  isLoading: false,
  isConnectionError: false,
  isInitialLoading: false,
}));
vi.mock("../context/AuthContext", () => ({ useAuth: () => auth }));
beforeEach(() => vi.clearAllMocks());
it.each([false, true])(
  "submits role and account-type intent without assigning authority (admin=%s)",
  async (adminLogin) => {
    render(
      <LoginPage
        applicationRole="ROLE_CASE_PROCESSOR"
        roleName="Case Processor"
        adminLogin={adminLogin}
        registerHref="#/access/ROLE_CASE_PROCESSOR/register"
      />,
    );
    fireEvent.change(screen.getByLabelText("Username or official email"), {
      target: { value: "processor.account" },
    });
    fireEvent.change(screen.getByLabelText("Password", { exact: true }), {
      target: { value: "SyntheticPassword123!" },
    });
    fireEvent.click(
      screen.getByRole("button", { name: "Log In to PharmaSafe" }),
    );
    await waitFor(() =>
      expect(auth.login).toHaveBeenCalledWith(
        "processor.account",
        "SyntheticPassword123!",
        {
          applicationRole: "ROLE_CASE_PROCESSOR",
          loginMode: adminLogin ? "ROLE_ADMIN" : "USER",
        },
      ),
    );
    expect(
      screen.getByRole("link", { name: "Request access" }),
    ).toHaveAttribute("href", "#/access/ROLE_CASE_PROCESSOR/register");
  },
);
