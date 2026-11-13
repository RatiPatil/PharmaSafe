# Super Admin and role-scoped administration

The existing administrator account is now presented as **Super Admin**. Its
username, password hash, user ID, case relationships and existing dashboard layout
are preserved. Role management and role cards are added to that workspace; public
home and sign-in pages are unchanged in this phase.

## Authorities and boundaries

- Existing `ROLE_ADMIN` is a reserved compatibility code for Super Admin. New
  authenticated sessions receive both `ROLE_SUPER_ADMIN` and the legacy
  `ROLE_ADMIN` authority so existing global case and administration permissions
  keep working. Ordinary administrators never receive either authority.
- Role Admin accounts use `ROLE_SCOPED_ADMIN`, with one active application-role
  assignment in `role_admin_assignments`. They do not inherit a normal clinical
  role or global administrator access. The active assignment is read from the
  database, not from request parameters, frontend routing or a department name.
- Normal application roles and case workflow permissions are preserved.

The six existing application roles are Case Processor, Medical Reviewer, Quality
Reviewer, Safety Manager, Reporting Specialist and Auditor / Viewer. `ROLE_USER`
remains a legacy Case Processor alias. Reporter is not currently a separate
operational role; this upgrade does not invent a Reporter workflow or modify the
public account cards. A future Reporter feature needs its own permissions and
workflow definition before it can be administered.

`AdminScope` checks the authenticated database account, active assignment and
object ownership. Scope restrictions apply to users, registration details and
decisions, password resets, account suspension, statistics, work and audit queries.
The existing `/api/admin/**` endpoints remain Super Admin only. Provisioning and
assignment routes are also rejected at the URL security filter before request
body validation. Service checks provide a second authorization boundary.

## Identity and employee work

Every existing and new user has a generated permanent staff code, `PS-<user ID>`.
The database generates and uniquely indexes it; clients cannot write it. Employee
lists show **full name · staff code · unique username**. Duplicate human names are
valid and distinguishable; no names, usernames, passwords or supplied employee IDs
are rewritten. Search supports the permanent staff code as well as existing
identity fields.

Role Admins can follow cases created, assigned or reviewed by their role's
employees, with an individual employee filter. These are read-only supervisory
summaries. Reporting supervisors can inspect report records created by their team,
including due dates and recorded submission status, without acquiring report-editing rights. Shared-case owner account details outside their role are redacted.
Normal case editing, clinical decisions, PDF permissions and regulatory actions
are not granted to Role Admin accounts. Super Admin retains its previous global
case permissions.

User and case counts are queried from the database. Dashboard user counts and
pending requests are grouped queries; work counts use the same role membership
conditions as the supervisory work list. Pending requests and pending account
counts are distinct labels. No sample counts or dummy users are seeded.

## Database migration V13

`V13__scoped_role_administration.sql` is additive and retains existing data:

- Renames the existing role catalogue display name to Super Admin and marks
  administrator authorities as unavailable for ordinary assignment/public signup.
- Adds the `ROLE_SCOPED_ADMIN` catalogue entry and generated unique `users.staff_code`.
- Adds `role_admin_assignments`, keyed by application role, with a unique admin
  user FK, assigning administrator FK, timestamp and optimistic row version.
  Six empty role slots are seeded; no administrator credentials are seeded.
- Adds `administrative_events`, with actor ID/name/authority level, managed role,
  affected identity/request, predecessor administrator, action, previous/new state,
  reason, outcome and timestamp. Updates and deletion are blocked by a database
  trigger. Existing security and case audit records remain intact.
- Validates assignment identity in the database and prevents changing an assigned
  Role Admin's authority through a direct user update. Adds indexes for employee
  case ownership, assessment and review queries.
- Adds a public registration role constraint excluding all administrative roles.

Administrative actor ID/name are immutable snapshots. The actor ID is deliberately
not a cascading FK: denied-access evidence must survive rollback of the attempted
operation and retain the actual actor identity. Assignment and affected-user
relationships use foreign keys. Denied administrative access is recorded in its
own transaction without recording passwords or reset tokens.

## Approval and assignment concurrency

Requests are locked and refreshed before a decision. Only one administrator can
process a pending registration; the second receives HTTP 409 with an already
processed message. The reviewed-by identity and timestamp identify the actual
Super Admin or Role Admin who acted.

Role Admin mutations acquire their assignment row before request/user locks. This
serializes an in-flight decision with administrator replacement or release, using
a consistent lock order. Membership and target identity are checked again against
fresh locked data. Replacement/release suspends the old administrator and increases
its authentication revision, invalidating existing sessions. Explicit reassignment
can reactivate a released Role Admin with a new reason and audit entry.

Assignments require the current `expectedVersion`; a stale management screen
cannot silently replace a more recent assignment. One primary administrator per
role and one active role per administrator are enforced by database uniqueness.

## APIs

All new endpoints start with `/api/administration` and require a real administrative
identity. Role Admin scope is enforced even if the caller changes role or user IDs.

| Endpoint                                           | Purpose                                                   |
| -------------------------------------------------- | --------------------------------------------------------- |
| `GET /overview?role=`                              | Global or assigned-role statistics and owner information  |
| `GET /users` and `GET /users/{id}`                 | Scoped, paginated employee lists and details              |
| `PUT /users/{id}/suspend` or `/activate`           | Scoped account status decision with reason                |
| `GET /registrations` and `GET /registrations/{id}` | Scoped requests, details and filters                      |
| `PUT /registrations/{id}/approve` or `/reject`     | Transactional ownership-checked decision                  |
| `GET /password-resets`                             | Scoped reset requests without tokens or hashes            |
| `PUT /password-resets/{id}/approve` or `/reject`   | Existing secure mail/reset workflow                       |
| `GET /report-work?role=&userId=`                   | Scoped, paginated team report supervision                 |
| `GET /work?role=&userId=`                          | Scoped, paginated supervisory case summaries              |
| `GET /audit`                                       | Immutable administrative decisions and assignment history |
| `GET /activity`                                    | Workflow activity by current role members                 |
| `GET /eligible-administrators?role=`               | Super Admin only: eligible existing accounts              |
| `POST /assignments/{role}`                         | Super Admin only: create and assign a dedicated admin     |
| `PUT /assignments/{role}`                          | Super Admin only: assign an existing eligible account     |
| `POST /assignments/{role}/release`                 | Super Admin only: release/suspend and revoke sessions     |

User/request lists support search, status, department, organization, date range,
whitelisted sorting and pagination. Registration requests additionally support
assigned administrator filtering. Page size is capped at 50. Application role
restrictions are combined with filters on the server, never replaced by them.

## Provisioning and operation

Existing `INIT_ADMIN_USERNAME` / `INIT_ADMIN_PASSWORD` bootstrap and controlled
administrator recovery remain unchanged. Existing production administrators are
recognized as Super Admin without recreating their account or changing passwords.

1. Sign in with the existing administrator account.
2. Open **Role management**, select an application role, then **Role Admin assignments**.
3. Create a dedicated account using a unique username, official email, employee
   name, a strong initial password and a mandatory assignment reason; or select
   an eligible approved employee / released administrator.
4. Send the initial password privately. The server stores a BCrypt hash and never
   returns the password in lists, details or history.
5. The assigned account signs in through the existing login screen and receives
   its role-scoped administration workspace. Public users cannot select this authority.

Until a role has an administrator, Super Admin can continue to process all its
requests. No shared default credentials or automatic production admin users are
created. Reset approval still requires working SMTP configuration; failures leave
the request unprocessed rather than pretending mail was sent.

The existing CI/CD pipeline deploys the backend first so Flyway applies V13, then
verifies the role catalogue and deploys the frontend at the same commit. No live
database reset or manual credential SQL is required.

## Validation and limits

Backend tests cover direct object-ID and role-filter manipulation, global endpoint
and provisioning denial, real login identities, scoped lists and statistics,
approval races, replacement during an in-flight decision, account state checks,
password reset ownership, session revocation, assignment reuse and stale versions,
unique staff identity and shared-case owner redaction. PostgreSQL-compatible tests
cover fresh/populated migrations and database constraints/immutability.

The isolated browser harness exercises normal workflows plus real provisioning,
registration approvals by both administrative levels, employee identity, work
inspection, direct API denial, replacement, release and session revocation. Its H2
rate-limit adapter is compiled only from test sources and refuses non-memory H2;
production retains the PostgreSQL rate limiter. No security endpoints or fixture
users are added to the production application.

Employee activity is scoped using current role membership. Administrative decisions
store the role at the time of the action, preserving assignment and decision history.
This implementation does not add a Reporter workflow, configurable permission
editor, public admin signup, invitation delivery system or Oracle certification.
