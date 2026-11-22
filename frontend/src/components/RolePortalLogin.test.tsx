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
it('separates employee and administrator form identities and autofill hints', () => {
  const {rerender}=render(<LoginPage applicationRole="ROLE_AUDITOR" />);
  const employeeUsername=screen.getByLabelText('Username or official email');
  const employeeId=employeeUsername.id;
  expect(employeeUsername).toHaveValue('');
  expect(employeeUsername).toHaveAttribute('autocomplete','off');
  expect(employeeUsername.closest('form')).toHaveAttribute('autocomplete','off');
  expect(screen.getByLabelText('Password',{exact:true})).toHaveAttribute('autocomplete','off');
  rerender(<LoginPage applicationRole="ROLE_AUDITOR" adminLogin />);
  expect(screen.getByLabelText('Username or official email').id).not.toBe(employeeId);
  expect(screen.getByLabelText('Username or official email')).toHaveAttribute('autocomplete','section-pharmasafe-role-auditor-role-admin username');
});
it.each(['input','animation','submit'])('clears browser-inserted admin credentials from employee login on %s', (trigger) => {
  render(<LoginPage applicationRole="ROLE_AUDITOR" />);
  const username=screen.getByLabelText('Username or official email') as HTMLInputElement;
  const password=screen.getByLabelText('Password',{exact:true}) as HTMLInputElement;
  // Simulate a password manager changing DOM values without React change events.
  username.value='pv.auditor.admin';password.value='SavedAdminPassword123!';
  if(trigger==='input')fireEvent.input(password);
  if(trigger==='submit')fireEvent.submit(username.closest('form')!);
  if(trigger==='animation'){
    const event=new Event('animationstart',{bubbles:true});
    Object.defineProperty(event,'animationName',{value:'login-autofill-detected'});
    fireEvent(username,event);
  }
  expect(username).toHaveValue('');expect(password).toHaveValue('');
  expect(screen.queryByText('Use the Role Admin login tab for this account.')).not.toBeInTheDocument();
  expect(auth.login).not.toHaveBeenCalled();
});
it('keeps saved admin credentials usable in the Admin tab', async () => {
  render(<LoginPage applicationRole="ROLE_AUDITOR" adminLogin />);
  fireEvent.change(screen.getByLabelText('Password',{exact:true}),{target:{value:'SavedAdminPassword123!'}});
  fireEvent.click(screen.getByRole('button',{name:'Log In to PharmaSafe'}));
  await waitFor(()=>expect(auth.login).toHaveBeenCalledWith('pv.auditor.admin','SavedAdminPassword123!',{applicationRole:'ROLE_AUDITOR',loginMode:'ROLE_ADMIN'}));
});
it('silently clears a delayed admin password and still permits a subsequent employee login', async () => {
  render(<LoginPage applicationRole="ROLE_AUDITOR" />);
  const username=screen.getByLabelText('Username or official email') as HTMLInputElement;
  const password=screen.getByLabelText('Password',{exact:true}) as HTMLInputElement;
  username.value='pv.auditor.admin';fireEvent.input(username);
  password.value='DelayedSavedAdminPassword123!';fireEvent.input(password);
  expect(username).toHaveValue('');expect(password).toHaveValue('');
  expect(username).toHaveAttribute('aria-invalid','false');
  expect(screen.queryByText('Use the Role Admin login tab for this account.')).not.toBeInTheDocument();
  fireEvent.change(username,{target:{value:'approved.auditor'}});
  fireEvent.change(password,{target:{value:'EmployeePassword123!'}});
  fireEvent.click(screen.getByRole('button',{name:'Log In to PharmaSafe'}));
  await waitFor(()=>expect(auth.login).toHaveBeenCalledWith('approved.auditor','EmployeePassword123!',{applicationRole:'ROLE_AUDITOR',loginMode:'USER'}));
});
it.each(Object.entries(ROLE_ADMIN_USERNAMES))('suggests the admin username for %s without exposing a password', (applicationRole, username) => {
  render(<LoginPage applicationRole={applicationRole} adminLogin />);
  expect(screen.getByLabelText('Username or official email')).toHaveValue(username);
  expect(screen.getByLabelText('Password', {exact:true})).toHaveValue('');
  expect(screen.getByLabelText('Password', {exact:true})).toHaveAttribute('autocomplete', `section-pharmasafe-${applicationRole.toLowerCase().replaceAll('_', '-')}-role-admin current-password`);
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
it('Case Processing Team Lead login sends Team Lead intent without role-admin authority',async()=>{render(<LoginPage applicationRole="ROLE_CASE_PROCESSOR" roleName="Case Processor" teamLeadLogin/>);fireEvent.change(screen.getByLabelText('Username or official email'),{target:{value:'lead.one'}});fireEvent.change(screen.getByLabelText('Password',{exact:true}),{target:{value:'SyntheticLeadPassword123!'}});fireEvent.click(screen.getByRole('button',{name:'Log In to PharmaSafe'}));await waitFor(()=>expect(auth.login).toHaveBeenCalledWith('lead.one','SyntheticLeadPassword123!',{applicationRole:'ROLE_CASE_PROCESSOR',loginMode:'TEAM_LEAD'}));});
it('switches user and college administration login without carrying credentials or stale portal errors',async()=>{
 const {rerender}=render(<LoginPage />);
 expect(screen.getByRole('link',{name:'User Login',exact:true})).toHaveAttribute('aria-current','page');
 fireEvent.change(screen.getByLabelText('Username or official email'),{target:{value:'student.one'}});fireEvent.change(screen.getByLabelText('Password',{exact:true}),{target:{value:'SyntheticPassword123!'}});
 fireEvent.submit(screen.getByLabelText('Password',{exact:true}).closest('form')!);
 await waitFor(()=>expect(auth.login).toHaveBeenCalledWith('student.one','SyntheticPassword123!',{loginPortal:'USER'}));
 const oldId=screen.getByLabelText('Username or official email').id;
 rerender(<LoginPage collegeAdministration />);
 expect(screen.getByLabelText('Username or official email')).toHaveValue('');expect(screen.getByLabelText('Password',{exact:true})).toHaveValue('');expect(screen.getByLabelText('Username or official email').id).not.toBe(oldId);
 expect(auth.clearError).toHaveBeenCalled();expect(screen.getByRole('heading',{name:'College Administration Login',exact:true})).toBeVisible();
 fireEvent.change(screen.getByLabelText('Username or official email'),{target:{value:'college.one'}});fireEvent.change(screen.getByLabelText('Password',{exact:true}),{target:{value:'SyntheticPassword123!'}});fireEvent.submit(screen.getByLabelText('Password',{exact:true}).closest('form')!);
 await waitFor(()=>expect(auth.login).toHaveBeenCalledWith('college.one','SyntheticPassword123!',{loginPortal:'COLLEGE_ADMIN'}));
});
