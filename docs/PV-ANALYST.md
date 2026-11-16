# PV Analyst workspace

PV Analyst is the seventh employee role (`ROLE_PV_ANALYST`). It is a separate cross-functional team, not an extra analyst account required beneath every other role. Analysts study reviewed safety evidence and publish analyses for the Safety Manager. Case processing, medical review, QC and regulatory reporting keep their existing authorities.

## Getting access

1. Open **Home → PV Analyst → New registration**. Complete identity, organization, analytics department, business unit, region, analytics experience and training. The request is pending until approved.
2. The **global Super Admin** can approve the request. The assigned **PV Analyst Role Admin** can also approve PV Analyst requests. Other department admins cannot approve or inspect these users.
3. To appoint that Role Admin, the global Super Admin opens **Role management → PV Analyst → Role Admin assignments**. Create or assign the administrator using the existing controlled form. `pv.analyst.admin` is a suggested username, not a pre-created account. The Super Admin chooses and privately shares the password; passwords are never embedded in frontend code.
4. Employees use **PV Analyst → User login**. Their administrator uses **Role Admin login** on the same dedicated role page.

PV Analyst registration does not require a Case Processing Team Lead ID. That hierarchy remains specific to Case Processing.

## Using the workspace

- **Dashboard:** real counts of reviewed cases, serious/fatal cases, assessment coverage, workflow states, priorities, countries and receipt activity. Dedicated charts show suspect products, reactions by recorded wording, and event outcomes. PV trend charts refresh every 15 seconds while visible.
- **Reviewed safety cases:** read-only inspection of Approved, Locked and Closed cases. Unreviewed cases are excluded from both the worklist and direct case access. A reopened case leaves the current analyst case scope.
- **Case documents:** working case-form PDFs and preserved document downloads for accessible cases. PV Analysts cannot issue final documents or medical summaries.
- **PV analysis reports:** enter a title, objective, findings, recommendation and optionally a reviewed case number. Publishing atomically stores the analysis, trend snapshot and original PDF. Published evidence cannot be edited/deleted; publish another report for subsequent findings. A failed generation saves no partial report and keeps the form draft on screen.
- **Safety Manager:** open **PV analysis reports** to inspect published analyses and download the same original PDFs. This is an in-application review inbox; publishing does not send email or transmit a regulatory submission.
- **PV Analyst Role Admin:** inspect **PV analyst reports** across the role. **My role users → View work** narrows the list to one employee's permanent staff ID. The global Super Admin has the same supervisory access from role management.

An analyst sees only their own published analyses. The PV Role Admin, global Super Admin and Safety Manager can inspect them. Medical, Quality, Case Processing and Reporting Role Admins do not receive this supervisory access.

## How to interpret the charts

The source is the current reviewed-case population. Product/reaction/outcome categories count distinct cases and may overlap, so category totals need not equal the overall case count. Charts show up to 20 categories. Report counts do not measure incidence, prove causality or establish a validated safety signal. Medical assessment and formal signal evaluation require the appropriate specialist process.

No dummy approved cases or artificial analysis findings are seeded. Empty charts mean there are currently no reviewed records in the applicable population.

## V17 database changes

- Adds the `ROLE_PV_ANALYST` catalogue entry, seventh department-admin slot and permitted registration role.
- Corrects historical display labels for Case Processor/legacy Case Processor without changing any existing user's role, username, password, case ownership or team membership.
- Adds `pv_analysis_records`: UUID identity; analyst FK; title/objective/findings/recommendation; optional case FK; publication time; JSON evidence snapshot; original PDF bytes; SHA-256 digest.
- PostgreSQL validates that the author is an approved active PV Analyst and that any linked case is reviewed at publication. Foreign keys preserve relationships. Indexes support author/time and supervisor/time views.
- Update/delete protection keeps published evidence immutable. The application uses a repeatable-read transaction for publication and generates its PDF in that transaction. Downloads recheck access and the digest, and disable caching.
- Authentication, registration approval, employee identity, session revocation and administrative audit reuse the existing role-management model.

Flyway applies V17 during backend deployment. No manual account-setup SQL is needed.
