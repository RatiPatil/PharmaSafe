# Case follow-ups, amendments and closure

This increment implements an original PharmaSafe workflow informed by Oracle Argus user documentation. It does not reproduce Oracle's database schema or establish regulatory validation.

## Navigation

Open an accessible case → **Case activities**:

- **Follow-ups & amendments:** numbered received-information records, significance, receipt dates, source reference, author, justification and case-version change. Records have no edit/delete API.
- **Contact history:** inspect correspondence and record case contacts.
- **Action items:** inspect outstanding case work and create tasks. The main Action items worklist supports completion and reassignment.
- Managers and administrators can **Reopen for follow-up** from Approved, Locked or Closed. Password confirmation, an active processor, reason and future due date are required. Reopening keeps the case number, records a review event and creates a FOLLOW_UP task. Recording its linked receipt completes that task atomically.

Intake's General tab includes an advisory duplicate check. It uses a normalized exact patient identifier OR normalized exact product/event pair, excludes the currently edited case, searches only authorized cases and shows up to 20 newest matches. It is not fuzzy matching or automatic deduplication. Changing identifying inputs clears old suggestions.

## Schema

Flyway migration `V9__case_follow_up_records.sql` adds data without dropping or rewriting existing clinical rows. V1–V8 are unchanged.

| Table | Change | Purpose |
|---|---|---|
| case_follow_ups | New table | Immutable application-level receipt history linked to the original case |
| case_action_items | action_code: GENERAL or FOLLOW_UP | Separates ordinary tasks from receipt-dependent follow-up requests |
| cases | closed_at, closed_by_user_id | Records formal closure; cleared on reopening, with history retained in review/audit events |

`case_follow_ups` has unique `(case_id, follow_up_number)`, positive/increasing case versions, nonblank summary/justification, date-order checks and foreign keys to cases and users. A composite foreign key `(case_id, related_action_item_id)` prevents linking another case's task. Amendments cannot have a safety receipt date or complete a follow-up task. Incoming data is validated again in the service/API layer.

The service locks the parent case while allocating receipt numbers, recording receipt versions, reopening, closing or adding closure-blocking work. Requests include `expectedRowVersion`; a stale receipt/reopen receives 409 and must be refreshed. Clinical intake cannot move the initial date past existing receipts or the latest date before recorded follow-ups. After recorded report submission, the initial receipt date is protected.

Closure requires workflow state Locked, no open action items and no pending linked 7-day/15-day report records. Closing clears physical lock flags and records the actor/time. New expedited report records cannot be scheduled against Closed cases. These checks cover recorded work only: a human must identify required reports and expected follow-up. Submission records are internal tracking; no regulator transmission occurs.

Historical Closed cases that still have a lock flag are preserved by a NOT VALID constraint; reopening corrects their current state. Historical approvals and clinical rows are not rewritten.

## Inspect stored data in Supabase

Use the SQL Editor with an authorized database account. These read-only queries show structure and operational identifiers without password hashes or clinical narratives:

```sql
SELECT table_name, column_name, data_type, is_nullable
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name IN ('cases','case_follow_ups','case_action_items')
ORDER BY table_name, ordinal_position;

SELECT c.case_number, f.follow_up_number, f.kind, f.received_date,
       f.safety_received_date, f.significant, f.previous_case_version,
       f.case_version, f.created_by_username, f.created_at
FROM case_follow_ups f JOIN cases c ON c.id = f.case_id
ORDER BY f.created_at DESC, f.id DESC LIMIT 50;

SELECT c.case_number, a.action_code, a.status, u.username AS assignee,
       a.due_at, a.completed_at
FROM case_action_items a
JOIN cases c ON c.id = a.case_id JOIN users u ON u.id = a.assigned_user_id
ORDER BY a.due_at, a.id LIMIT 50;
```

## Verification and rollout

- Backend integration coverage includes receipt numbering, stale-write rejection, amendment rules, date ordering, cross-case task rejection, linked completion, reopen password confirmation, role/CSRF enforcement, duplicate scope and closure blockers.
- `scripts/verify-workspace-migration.mjs` applies V1–V9 to disposable PostgreSQL-compatible PGlite databases (fresh and populated), checks preserved data and rejects duplicate receipt numbers, cross-case links and invalid amendments.
- `scripts/verify-role-workflow.cjs` runs all seven roles through an isolated H2 backend and Chromium; no production credentials or database are loaded.
- Deploy backend before frontend. Flyway applies V9 during backend startup. Confirm Flyway success and `/api/health` before deploying the frontend. Take the normal database backup before production rollout. Keep the additive schema if rolling the application back; do not drop receipt history.

## Oracle references

- [Entering a New Case / duplicate search](https://docs.oracle.com/health-sciences/argus-safety-811/AEOAF/caseact.htm)
- [Case Form / follow-up received dates and amendments](https://docs.oracle.com/health-sciences/argus-safety-811/AEOAF/caseform.htm)
- [Reviewing cases / closure and reopening](https://docs.oracle.com/health-sciences/argus-safety-812/user-EN/reviewyourcases.htm)
