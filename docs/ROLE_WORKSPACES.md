# PharmaSafe role workspaces

The existing session login remains shared. The authenticated user's primary role selects a workspace. No role selector or alternate login accounts are built into the login screen. Administrators create individual accounts through Users & access; no shared demonstration passwords are seeded.

## Access matrix

| Role | Case scope | Main operations |
| --- | --- | --- |
| Administrator | All cases | Users, account status, roles, audit, intake, workflow, assignment, reports |
| PV Analyst | Created or assigned cases | Intake and editing in Draft/Data Entry, submit for medical review, personal contacts and actions |
| Medical Reviewer | Medical Review queue | Read case, record medical assessment, advance to QC or return to Data Entry |
| Quality Reviewer | Quality Review queue | Read case, confirm QC checklist, approve or return to medical review |
| Safety Manager | All cases | Assignment, priorities, lock/unlock, approved-to-locked-to-closed routing, report tracking |
| Reporting Specialist | Approved/Locked/Closed cases | Prepare report records, track due dates, record submission |
| Auditor / Viewer | All cases | Read-only case details, case history, recent system audit, report records |

`ROLE_USER` is retained as a legacy PV Analyst alias. Unsupported historical roles are preserved in the catalog as non-assignable/restricted; administrators must explicitly assign a supported role before those users can use a workspace.

This implementation uses **one primary role per user**, matching the existing authentication contract. It does not yet implement Oracle's configurable multi-group permission union, per-country/product restrictions, or workspace switching for multi-role users. The seven functional workspaces are an application design, not Oracle's mandated role list.

## Database design

V7 is additive. V1–V6 are unchanged.

- `workspace_roles.code` is the role catalog primary key. `users.role` now references it by a foreign key.
- Seven roles are assignable. Existing legacy/custom role strings are retained without automatic privilege elevation.
- Existing `users`, `cases`, patient, reporter, product, event, assessment, report, contact, action, review-history and audit relationships remain shared. There are no per-workspace case copies.
- `(workflow_state, updated_at, id)` indexes workflow queues.
- `cases.row_version` is a separate JPA optimistic-lock counter. It is not the displayed clinical case version. Concurrent writes receive HTTP 409 instead of silently overwriting a concurrent transaction.
- Successful case-specific audit events now participate in the case transaction. Authentication events retain the existing independent transaction behavior.

The catalog constrains role identity. Operation permissions are enforced in Spring Security and the shared case-scope policy; the catalog is not an editable permission engine.

## API and navigation

- Hash URLs identify the workspace section and support reload/back navigation. Unauthorized section hashes fall back to the authorized overview.
- `/api/workspace/cases` applies role scope before database pagination. The existing dashboard worklist uses the same scoped query.
- `/api/workspace/assignees` exposes only eligible enabled/unlocked user identifiers to managers/admins, not authentication secrets.
- `/api/workspace/audit` allows administrators and auditors to read the latest 50 events.
- Case creation always starts in Draft or Data Entry. Updates cannot assign a different owner or bypass dedicated workflow transitions.
- Case details/history enforce matching case scope. Case writes, report writes, administrative actions, and interaction writes have server-side role checks.
- Global dashboard summary counts remain an oversight endpoint limited to admin/manager/auditor roles.

Role changes should be followed by logout/login: the existing session authentication retains granted authorities until reauthentication. Coordinate account access changes accordingly. Session-wide revocation is not introduced by this change.

## Verification

Run frontend `npm test`, `npm run lint`, `npm run build`; run backend `mvn test`.

In environments where Mockito self-attachment is prohibited, run Maven tests with the already downloaded Byte Buddy agent in the Surefire `argLine`. This is an environment-only test invocation, not a production Java option.

A reproducible migration check uses isolated PGlite (PostgreSQL compiled to WebAssembly):

```sh
npm install --prefix /tmp/pharmasafe-pgtest @electric-sql/pglite
node scripts/verify-workspace-migration.mjs /tmp/pharmasafe-pgtest
```

It executes V1–V7 for fresh and populated databases and verifies seven assignable roles, invalid-role rejection, and preservation of users, case ownership, and patient links. This does not replace a deployment rehearsal with native PostgreSQL and Flyway.

## Release limits

- Reports are internal preparation/submission-tracking records. Recording submission does not generate validated E2B XML or transmit to an agency.
- Existing attachment storage and medical dictionary limitations remain.
- The QC checklist is recorded in the decision comments/audit trail; this is not a Part 11 electronic signature implementation.
- Automated DOM tests do not establish visual correctness at all viewport sizes. Browser visual QA remains necessary.
- Deploy the backend (which runs Flyway V7) before the frontend. Do not manually execute V7 against a live database outside Flyway.
