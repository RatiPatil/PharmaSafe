# User access management (V12)

Public access requests reuse the six existing non-admin roles: Case Processor,
Medical Reviewer, Quality Reviewer, Safety Manager, Reporting Specialist and
Auditor / Viewer. There is no separate Reporter permission set in this application;
case intake remains with Case Processors. Legacy ROLE_USER continues to map to the
processing workspace. Public registration cannot request ROLE_ADMIN or ROLE_USER.

## Workflow

The public home is a dedicated page with a safety-workflow introduction and account-type cards. Sign-in has its own route (`#/login`), separate from the home page (`#/home`).
Selecting a card creates a signed role selection valid for 30 minutes. The backend
uses that signed role, never a client-supplied role assignment. A backend restart
invalidates outstanding selection forms; choose the account type again to resubmit.
Complete the grouped identity, qualification and account-security sections. Structured registration fields use dropdowns for department, designation, business unit, region, qualification, specialization, experience and training. Organization names, identifiers and application explanations remain free text. Public pages and role workspaces use a shared navy/teal palette, consistent control sizing and document scrolling; wide data tables retain horizontal scrolling.
Passwords require 12–72 UTF-8 bytes including letters and a number or symbol.
Registration persists a disabled PENDING account, an original application and its
role-specific details. Administrators use **User access management** to inspect,
filter, approve or reject applications. Only APPROVED and enabled users can log in.
Users may sign in with username or their registered email.

Suspension revokes existing sessions. Activation is allowed only from SUSPENDED;
rejected applications cannot be activated through the legacy status toggle.
Three consecutive failed logins now lock an account. An administrator-approved
password recovery clears the lock; the existing controlled admin recovery process
remains available. Original application evidence and security audit records are
protected against update/deletion by PostgreSQL triggers. This is design guidance
for data integrity, not a regulatory-certification claim.

## Password recovery and SMTP

Public recovery always returns a generic response. Eligible accounts generate an
admin-visible request (maximum three requests per account per hour). Approval sends
a 256-bit random single-use token to the account's registered email, valid for 30
minutes. Only its SHA-256 digest is stored, bound to the account authentication revision so subsequent password, role or access changes invalidate issued links. Neither admin APIs nor logs disclose the
token. Reset consumes the token, invalidates other issued links and revokes sessions.
Reset links place the token in the URL fragment; the frontend removes it from the
address bar after reading it and retains it only in memory.

Configure backend environment variables through Render, not in source code:

| Variable | Purpose |
| --- | --- |
| ACCESS_PUBLIC_URL | HTTPS frontend URL, e.g. https://pharmasafe-frontend.onrender.com |
| ACCESS_MAIL_FROM | Authorized sender address |
| SPRING_MAIL_HOST | SMTP server |
| SPRING_MAIL_PORT | SMTP port (typically 587) |
| SPRING_MAIL_USERNAME | SMTP username |
| SPRING_MAIL_PASSWORD | SMTP secret |
| SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH | true when authentication is required |
| SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE | true for STARTTLS delivery |
| SPRING_MAIL_PROPERTIES_MAIL_SMTP_CONNECTIONTIMEOUT | Recommended 10000 milliseconds |
| SPRING_MAIL_PROPERTIES_MAIL_SMTP_TIMEOUT | Recommended 10000 milliseconds |
| SPRING_MAIL_PROPERTIES_MAIL_SMTP_WRITETIMEOUT | Recommended 10000 milliseconds |

Without SMTP, recovery requests can be submitted and reviewed, but approval returns
an explicit configuration error and remains PENDING. For local development, use a
local SMTP capture server such as Mailpit with SPRING_MAIL_HOST=localhost,
SPRING_MAIL_PORT=1025, ACCESS_PUBLIC_URL=http://localhost:5173 and a development
sender. Retrieve the link only through that local mailbox; production never returns
a reset token through an API or admin screen. SMTP sending is synchronous; a rare
failure to commit after delivery can leave an unusable email link. Resubmit/review a
new request if this occurs. Approval/rejection status is shown in the admin panel;
automatic registration-decision email is not currently sent.

Historical users are preserved as APPROVED with their original enabled/locked state.
They have no invented email or employee ID. Recovery for a historical account without
an email must use the existing controlled administrator recovery process; no reset
email can be delivered until verified contact details are available. No production
usernames or passwords are hardcoded by this change.

## Database migration

Flyway **V12__user_access_approval.sql** is additive. It introduces:

- users.account_status, email, employee_id, mobile_number, designation, organization,
  approved_by and approved_at; normalized unique email and employee indexes.
- registration_requests linked to existing users and workspace_roles; preserved
  requested role, original reason, versioned review metadata and reviewer FK.
- registration_details, a keyed collection of role-specific application fields.
- password_reset_requests with reviewer metadata, hashed tokens, expiry and consumption
  state constraints; no plaintext password or raw reset token columns.
- access_rate_limits, durable atomic per-client rate buckets for public writes.
- indexes for request dates and account filtering; append-only audit and application
  evidence triggers.

Do not edit V1–V11 or run ad hoc production schema recreation. Deploy the backend
first so Flyway applies V12 before the frontend exposes registration.

## API reference

All requests use the existing cookie session and CSRF mechanism. Public writes
require an X-XSRF-TOKEN obtained from GET /api/auth/csrf. Except for the existing
login endpoint, public does not mean CSRF-exempt. Admin APIs require ROLE_ADMIN.

| Method | Endpoint | Payload / behavior |
| --- | --- | --- |
| GET | /api/auth/access-roles | Supported public roles and required detail keys |
| POST | /api/auth/role-selection | {role}; signed selectionToken |
| POST | /api/auth/register | selectionToken, fullName, employeeId, email, mobileNumber, department, designation, organization, username, password, confirmPassword, reason, optional additionalInformation, details keyed by catalog fields |
| POST | /api/auth/login | Existing {username,password}; username accepts official email |
| POST | /api/auth/forgot-password | {identifier}; generic acknowledgement |
| POST | /api/auth/reset-password | {token,password,confirmPassword}; consumes token |
| GET | /api/admin/registration-requests | Paginated original application views |
| GET | /api/admin/registration-requests/{id} | Complete application and review details |
| PUT | /api/admin/registration-requests/{id}/approve | {reason}; enables original requested role |
| PUT | /api/admin/registration-requests/{id}/reject | {reason}; rejects pending request |
| GET | /api/admin/access-users | Paginated account status views with creation dates, no credentials |
| GET | /api/admin/access-users/{id} | Complete account profile plus original registration details when available |
| PUT | /api/admin/users/{id}/suspend | {reason}; disables access and revokes sessions |
| PUT | /api/admin/users/{id}/activate | {reason}; reactivates a suspended account |
| GET | /api/admin/password-reset-requests | Paginated requests; optional status filter |
| PUT | /api/admin/password-reset-requests/{id}/approve | {reason}; delivers reset email |
| PUT | /api/admin/password-reset-requests/{id}/reject | {reason}; rejects request |
| GET | /api/admin/audit-logs | Existing security audit access |

Registration filters: role, status, department, organization, search (name, employee
ID or email), from/to (inclusive UTC dates), page, size (maximum 50), sort
(createdAt/fullName/role/status) and direction (asc/desc). Account views support role,
status, department, organization, search, page and size. Password requests support
status, page and size. Admin decisions require a reason of at most 2000 characters.

## Administrator bootstrap

Keep using the existing protected bootstrap: INIT_ADMIN_USERNAME and
INIT_ADMIN_PASSWORD backend environment variables on the initial deployment. The
existing initializer refuses to replace an existing administrator. Do not put these
values into Git, a Flyway seed or a public registration form. Existing administrator
accounts, roles, case assignments and workspaces are preserved.

## CI/CD

Main pushes and PRs run the frontend suite/build, backend tests, PostgreSQL migration
checks and the seven-role browser workflow. A successful main push deploys through
the existing Render services. Configure repository Actions secrets:

- RENDER_BACKEND_DEPLOY_HOOK
- RENDER_FRONTEND_DEPLOY_HOOK

Optional repository variables RENDER_BACKEND_URL and RENDER_FRONTEND_URL override
the current public service URLs. Hook calls select the tested commit via ref. The
job verifies /api/health/release returns that commit after backend startup/migrations,
smoke-tests the role catalog, then deploys the frontend and verifies /release.json.
Keep Render autoDeploy disabled to avoid deploying before CI or before migrations.
Missing hooks or a commit mismatch fail the deployment job; they do not report a
successful deployment. Manual workflow dispatch with trigger_deploy=yes remains
available for retrying a failed deployment. Deployments are serialized.

## Changed source areas

Frontend: App.tsx, LoginPage.tsx, PublicAccess.tsx/CSS, UserAccessManagement.tsx,
RoleWorkspace.tsx, workspace model, tests and build release stamping script.
Backend: User, authentication and current-account checks, user repository, legacy
admin guards, public/admin access controller, registration DTO/entities/repositories,
role-selection, rate-limit, mail and user-access services, exception handling,
release health endpoint, mail dependency and configuration. See the commit diff for
the complete file list. No case-management permissions were expanded.

Access worklists use six readable columns and a horizontally scrollable table when space is limited. View details opens a native modal with immediate loading feedback, error/retry handling, keyboard Escape dismissal and restored row focus. Decision confirmation also opens a dialog. Sidebar navigation scrolls independently within its reserved space above the account footer. The dedicated login route fits normal desktop viewport heights; compact screens retain accessible scrolling.


## Dashboard presentation

The dashboard UI presents the existing `/api/workspace/overview` response in role-specific KPI cards, a selectable workflow doughnut chart, workflow-stage bars and seriousness/priority counts. Counts retain the existing role scope and labels. Highlighting a legend entry affects only the chart presentation. Seriousness and expedited priority are labelled as overlapping indicators; they are not added together. Empty scopes display zero and an empty-state explanation. No synthetic trend data is generated. The existing dashboard refresh and operational action handlers are reused.

The shared theme includes local medical photography, navigation icons, consistent controls and subtle entry/bar transitions. Reduced-motion preferences disable the decorative transitions. Photography sources are recorded in `frontend/public/images/CREDITS.md`. This is a frontend presentation update; authentication, API behaviour, permissions, database schemas and case workflows are unchanged.
