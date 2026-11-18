# Case Processing teams and work delivery

This module adds a department hierarchy to the existing Case Processing role. It does not change medical review, QC approval or regulatory reporting permissions. The existing theme and the other five role workspaces remain in place.

## Use the module

1. Sign in with the existing **Case Processing Role Admin** account. Open **Processing teams & work**. The global Super Admin can open the same module from administration. Within this module, “Case Processing Super Admin” means department-wide management; it does not grant global Super Admin authority.
2. Open **Team Leads & teams**. Create a team with the Lead's name, unique username, official email, initial password and creation reason. Privately share the credentials with the Lead. Passwords are hashed and cannot be retrieved later. Create only the teams you need, up to ten active teams.
3. Employees can open **Case Processor → New registration**, enter the **Team Lead ID** (for example `CP-TL-001`) and click **Verify Team Lead**. The form confirms the Lead and team; editing the ID requires verification again. Alternatively, copy the team's **Registration link** and send it to its employees. It opens only employee registration with the team already selected, including when previewed from an existing signed-in session. Administrator/login tabs are not shown on this form. Each team has ten reserved/active seats; pending registrations reserve seats. An invalid, closed or full invitation does not silently register the employee outside the team.
4. The Lead uses **Case Processor → Team Lead login**. New employees appear under **Registration requests** for both that Lead and the department Admin. Either can approve or reject, with a reason. Approval by one is sufficient. Employees receive a permanent `PS-<user ID>` identifier; team identities are `CP-TL-<team ID>`. Names may repeat; these identifiers do not.
5. An existing approved Case Processor can instead be attached by the department Admin under **Employees**, using their permanent staff ID. One employee cannot occupy two current teams.
6. The Lead opens **Assign work**, enters an employee's staff ID from the directory, stage, title, instructions, priority and due time. Alternatively choose **All active employees in this team**. Bulk work creates separate assignments, progress histories and original PDFs for each employee. A single clinical case can only be linked to an individual assignment.
7. The employee signs in on **User login**. Team members land in **My Case Processing work**. Open **My work & PDFs → Open assignment** to read instructions and download the assignment PDF. Submit work completed, progress, pending dependencies, remaining work, blockers and a reason. Completion requires 100%, no pending/remaining work and the stage's evidence checklist. Coding completion also needs the actual dictionary/version reference.
8. Each submission immediately creates an archived response PDF visible to the employee, their Lead and department Admin. Leads review it in **Work & responses** and may reopen completed work with a correction reason. Original responses and PDFs remain unchanged.
9. The department Admin selects a team in **Team dashboard** and can inspect its employees, assignments, progress and PDFs. Leads see only their own team; employees see only their own work. Dashboards use stored task counts, status charts, overdue counts, completion bars and pending approvals. They refresh every 15 seconds while visible and have a manual refresh control.

PDF delivery is through the application's authenticated work inbox, not email. New Leads and teams are deliberately not auto-created with public shared passwords. Existing cases and accounts are preserved.

## Clinical workflow

Assignment stages cover receipt/intake, minimum-criteria validation, duplicate review, medical/drug coding, narrative, medical handoff, QC corrections and reporting handoff. The employee can open a linked clinical case in the existing editor. Linking requires an existing Draft/Data Entry case within the team's ownership and allocates it to the selected employee. Supervisors can inspect the linked clinical record read-only through the assignment.

Completing a task records employee delivery; it does not approve the clinical case, send a regulatory submission or replace medical/QC review. MedDRA/WHODrug coding references document the licensed dictionary actually used; this module does not supply licensed dictionary data or an external authority submission connector.

## Authority and request scope

| Authority | Team management | Registration decisions | Work visibility | Work actions |
|---|---|---|---|---|
| Global Super Admin | All CP teams | All CP teams | All CP teams | Assign/reopen |
| Current Case Processing Role Admin | All CP teams | All CP teams | All CP teams | Assign/reopen |
| `ROLE_CASE_TEAM_LEAD` | Own team's invitation and roster, no new Leads | Own team's requests | Own team | Assign/reopen |
| Case Processor employee | None | None | Own assignments | Own progress/response |
| Other department roles/admins | None | None | None | None |

Every request rechecks the current database account and assignment. Team IDs, employee IDs, document IDs and filter parameters never grant access. Leads have a separate authority and cannot use global administrative or ordinary clinical case APIs. Replacement/release of a department Admin takes effect through existing current-assignment checks. Deactivating a team revokes its Lead's sessions and freezes new task updates; preserved evidence remains available to supervisors. Closing registration only closes signup and keeps the Lead's session active.

## V16 database migration

V16 is additive and runs through Flyway when the backend starts. Do not manually recreate these tables or rerun older account bootstrap SQL. All V1–V15 migrations remain unchanged.

| Relation | Purpose and integrity |
|---|---|
| `cp_teams` | Unique Lead FK, random registration key, immutable numeric identity, active/open flags, seat limit and optimistic version. PostgreSQL checks at most ten active teams and the Lead's authority. |
| `cp_team_members` | Team/user FKs, membership state and history timestamps. Unique team/user pair, partial unique current user membership, serialized seat reservations capped at ten. |
| `registration_requests.cp_team_id` | Routes signup to a team. Team signup is restricted to Case Processor; a deferred composite FK requires the matching membership in the same transaction. |
| `cp_work_orders` | Original assignment instructions, stage, priority, due time, optional case link, creator and individual/bulk mode. Immutable. |
| `cp_work_assignments` | One recipient per order, team/member composite FKs, current progress/status and optimistic version. Recipient, order and team cannot be changed after creation. Completed status requires 100%. |
| `cp_work_updates` | Append-only employee responses with stage evidence, coding reference, source assignment version and timestamp. Composite FK ties responder to the assigned employee; unique source version prevents duplicate/stale submissions. |
| `cp_work_documents` | Original PDF bytes, SHA-256, original JSON snapshot, generator and time. Composite FKs bind response documents to the correct assignment/update. One original assignment document and one document per response. Immutable. |
| `administrative_events` | Existing audit extended with explicit TEAM_LEAD actor level; provisioning, approval, assignment, progress, reopening and clinical inspection are audited. |

Team/member locks serialize creation, seat reservation and approval. Account-state triggers synchronize membership and reject overcapacity reactivation. Role-change guards prevent converting an active Lead/member into another authority while its relationship remains active. Task response writes lock/check the assignment version; PDF rendering and evidence persistence occur in one transaction, so generation failure rolls back the entire operation. Archived PDF downloads verify SHA-256 and use authenticated, non-cacheable responses.

Entity associations between public-field processing entities are eagerly loaded to avoid reading uninitialized proxy fields; user and clinical-case references use accessors. Lists and evidence histories are paginated. Existing clinical case locking/auditing continues to apply in the case editor.

## Verification

- Backend integration tests exercise role scoping, team invitations/approvals, capacity, broadcasts, private PDFs, stale updates, minimum-criteria completion, linked clinical records and atomic PDF failure rollback.
- PostgreSQL migration checks run all migrations on fresh/populated databases and exercise hierarchy constraints, account/membership synchronization and immutable evidence.
- Frontend tests cover role-specific controls, team registration routing, Lead login intent, progress drafts and stale-save behavior.
- The isolated browser workflow exercises existing roles plus real team creation, approvals by both authorities, staff-ID assignment, completion/PDF exchange, broadcast isolation and responsive team screens.

## Employee clinical workstation (V18)

New assignments default to **Clinical case work** in the Team Lead's Assign work form. A saved clinical case is mandatory before these assignments can be completed. Select **General instructions / training task** for nonclinical shared work. Historical assignments retain their original behavior.

1. Employee signs in using Case Processor → User login. Open **Processing teams & work → My work & PDFs → Open assignment**.
2. Read the task and download its assignment PDF. In **Clinical case processing**, choose **Create clinical case for this task**, or enter the number of an existing unlocked Draft/Data Entry case currently assigned to the same employee and choose **Link my existing case**.
3. Enter and save actual case data in General, Patient, Reporters, Products, Adverse Events, Medical History, Narrative/References and attachment metadata. Incomplete information can be saved as a draft. This reuses the existing clinical case editor, validation, revision checks and access rules; clinical data is not copied into a parallel task form.
4. Use **Duplicate & coding review** to run the existing exact-match search over accessible cases. Record the review outcome and source rationale. An unresolved potential duplicate blocks a duplicate-review/medical-handoff task. The latest review for that assignment and saved case revision determines readiness; earlier decisions remain preserved. The search does not establish absence of duplicates outside the employee's accessible cases.
5. Enter actual MedDRA LLT, PT and SOC in Adverse Events. Record the drug/event dictionary names, releases and product-code reference plus source verification rationale. This records manual verification; it does not supply licensed MedDRA/WHODrug content or perform automatic dictionary validation.
6. Use **Follow-up & contacts**, **Laboratory**, and **Case PDFs** for existing case-specific functionality without leaving the task. Laboratory corrections retain original evidence. Attachments remain metadata, not file-content storage.
7. The completeness panel evaluates saved fields for the assigned stage. Intake/validation checks identifiable patient, reporter name/institution, a named suspect drug and adverse-event dates. Coding checks every event's LLT/PT/SOC and a current verification. Narrative checks saved narrative. Medical handoff requires the minimum facts, resolved duplicate review, coding verification, narrative, and a valid Data Entry case. A case edit invalidates earlier revision-specific reviews. Stage checks are processing controls, not a regulatory validation certification.
8. Submit progress or report missing information with blockers. To complete, enter 100% progress, completed work, reason and required evidence, with no pending/remaining work. The archived response PDF now includes the linked clinical case snapshot and processing reviews available at response time. Original instruction PDFs remain unchanged even if a case is linked later.
9. For a **Medical review handoff** task, first complete the response. Then enter **Medical handoff reason → Send case to Medical Review**. The server rechecks membership, case ownership, case revision and preparation evidence and uses the existing clinical workflow transition. Medical/QC decisions stay with their existing reviewers. A subsequent case change requires the Lead to reopen the task before the employee can record fresh processing evidence.
10. Team Lead opens Work & responses; Case Processing Admin selects the team then the same task. Both inspect the saved clinical record and preserved processing reviews read-only and download the same original response PDF. Global Super Admin retains overall access.

### Database and access

- `cp_assignment_cases`: immutable employee-to-assignment clinical link, with assignment PK, case FK, employee FK and timestamp. A composite FK requires the link author to be the assigned employee. Cases already linked in the immutable original work order cannot be replaced.
- `cp_processing_evidence`: append-only duplicate/coding decisions with assignment/case FKs, source case row revision, outcome, source notes, dictionary reference, author and timestamp. Index supports current-revision lookups and the latest decision; UI shows the latest 50 reviews while older reviews remain in the database and archived response snapshots.
- PostgreSQL insert guards require the actual active assigned employee, active membership/team, current clinical revision and editable owned case. Update/delete triggers protect the links and reviews. No existing clinical record, account, assignment, review or PDF is overwritten by V18.
- Assignment locks serialize case creation/linking, evidence, completion and handoff; case locks/revision checks reject stale evidence. Creating a case and binding it to the task is one transaction, so a link failure does not leave an orphan case.
- Clinical assignments cannot finish using checklist attestations alone. Supporting evidence must exist in the saved clinical record. General training tasks are explicitly identified and are not clinical case completion.

### Oracle workflow references

The workstation follows the concepts of case data entry, worklists and responsibility by workflow stage described in Oracle's documentation. It is PharmaSafe functionality, not an Oracle integration or a complete replacement for a validated Argus installation:

- [Oracle Argus Safety Quickstart: General, Patient, Products and Events](https://docs.oracle.com/health-sciences/argus-safety-811/QKSRT/qksrt.htm)
- [Oracle Argus Safety: Review your cases / worklists](https://docs.oracle.com/health-sciences/argus-safety-812/user-EN/reviewyourcases.htm)
- [Oracle Argus Safety 8.4.4: Configuring workflow](https://docs.oracle.com/en/industries/life-sciences/argus-safety/8.4.4/aadmn/configuring-workflow.html)
