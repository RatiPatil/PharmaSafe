import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { beforeEach, it, expect, vi } from "vitest";
import { LoginPage } from "./LoginPage";
import { ROLE_ADMIN_USERNAMES } from './roleAdminAccounts';
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
it.each(Object.entries(ROLE_ADMIN_USERNAMES))('suggests the admin username for %s without exposing a password', (applicationRole, username) => {
  render(<LoginPage applicationRole={applicationRole} adminLogin />);
  expect(screen.getByLabelText('Username or official email')).toHaveValue(username);
  expect(screen.getByLabelText('Password', {exact:true})).toHaveValue('');
  expect(screen.getByLabelText('Password', {exact:true})).toHaveAttribute('autocomplete', 'current-password');
});
it('clears credentials and validation when switching roles or returning to employee login', () => {
  const {rerender} = render(<LoginPage applicationRole="ROLE_CASE_PROCESSOR" adminLogin />);
  fireEvent.change(screen.getByLabelText('Password', {exact:true}), {target:{value:'PrivateEnteredPassword!'}});
  rerender(<LoginPage applicationRole="ROLE_MEDICAL_REVIEWER" adminLogin />);
  expect(screen.getByLabelText('Username or official email')).toHaveValue('pv.medical.admin');
  expect(screen.getByLabelText('Password', {exact:true})).toHaveValue('');
  rerender(<LoginPage applicationRole="ROLE_MEDICAL_REVIEWER" />);
  expect(screen.getByLabelText('Username or official email')).toHaveValue('');
});
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
