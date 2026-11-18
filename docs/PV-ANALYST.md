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

## V19 — practical analytical workflow

The PV Analyst workspace now has **Analysis reports & reviews** and **Case series & queries**. This adds a reproducible analytical population and a formal in-application manager feedback cycle; it does not grant clinical editing or approval authority.

### Analyst: query, freeze, analyse

1. Open **PV analysis reports → Case series & queries**.
2. Filter by initial receipt date range, country, report type, suspect product text, reaction wording/preferred term, and seriousness. Conditions combine with AND. Product/reaction matching is case-insensitive literal text, not SQL or arbitrary wildcard execution. Training cases are excluded unless explicitly selected.
3. **Run reviewed-case query** previews current Approved/Locked/Closed cases, 20 per page. Changing criteria requires a fresh preview before saving.
4. Enter a case-series name and population purpose, then **Freeze case series & generate PDF**. Capture re-runs the criteria in a repeatable-read transaction. It rejects empty populations and more than 200 matches, rather than silently dropping cases. Narrow larger populations.
5. Open the saved case series to inspect its frozen line listing, source case revisions, serious/fatal counts, product/reaction/outcome charts, saved criteria and original PDF. Line listings include case references, country, receipt date, state, source revision and relevant categories; patient identifiers and reporter contact details are not copied.
6. **Re-run saved query** uses the same criteria against current reviewed cases and prepares a new capture; it never changes the previous population. A reopened case leaves live query results while the previous captured listing remains historical evidence.
7. **Use case series in analysis** selects that frozen population in the report form. Publishing embeds that population and its frozen statistics in the analysis PDF, rather than unrelated global trends. An optional individual case reference must belong to the series and still meet the current reviewed-case access rules. Existing V17 reports and global-trend/single-case publication remain supported.
8. Explain the observed findings, source limitations and recommendation. Publishing creates an immutable report and original PDF. This remains descriptive analysis: it does not establish incidence, causality or a validated signal.

### Safety Manager: evaluate and respond

1. Open **PV analysis reports** and choose **Review history & response** on a report.
2. Choose **Acknowledge analysis**, **Return for correction**, or **Request follow-up analysis**. Comments are mandatory; follow-up requests require a due date today or later.
3. **Record review & generate response PDF** preserves a separate feedback record and original response PDF, accessible to the author, Safety Manager and PV administration. Acknowledgement is analytical report review, not clinical case approval or regulatory submission.
4. Stale decisions are rejected if another review arrived first. Reload review history to obtain the latest revision token; unsaved comments remain available for correction. Review history is paged and immutable.
5. The analyst reads feedback, downloads the same response PDF, and uses **Prepare revised analysis** for a returned/follow-up report. A new report links to the original. Only the original author can issue its replacement; one successor per report prevents competing revision chains. Later revisions can form a longer linked chain after subsequent manager feedback.
6. Superseded reports keep their original PDFs and review history, but new managerial decisions go to the replacement. The original report and original managerial response PDFs never change.

### Role administration and dashboards

- PV Analyst: own saved populations, publications and feedback; current reviewed-case inspection remains read-only.
- PV Analyst Role Admin: all PV employees' analytical populations/reports and feedback, including existing **View work** employee filtering; cannot impersonate an analyst or make managerial review decisions.
- Safety Manager and global Super Admin: oversight plus managerial analytical review.
- Other department admins and other employees cannot read another analyst's saved evidence by changing API IDs or filters.
- Dashboard counters query actual publications and latest review decisions. Awaiting/returned/follow-up/acknowledged counts exclude superseded reports; the total publication count includes the preserved versions. Live charts and work counters refresh while visible; saved populations retain capture-time statistics.

### Database safeguards

- `pv_case_series`: author FK, name/purpose, count, capture time, validated criteria JSON, frozen evidence JSON, original PDF bytes and SHA-256 digest.
- `pv_series_cases`: case-series/case FKs, unique membership, source case number/revision and frozen line snapshot. Deferred PostgreSQL constraint triggers require the complete membership count to match the parent population at transaction commit. Insertion verifies reviewed source revisions and exact membership of the frozen listing.
- `pv_analysis_records.series_id`: points to an own-author frozen population; optional individual case references must be members. `supersedes_id` is a unique self-FK; insert guards enforce author continuity and prior returned/follow-up feedback.
- `pv_analysis_reviews`: report/reviewer FKs, outcome, comments, follow-up due date, previous-review link, timestamp, snapshot, original PDF and digest. A composite FK keeps the previous review on the same report. Database guards serialize the report review chain, enforce current reviewer authority and reject stale/duplicate first reviews and decisions on superseded reports.
- Population, membership, published report and review update/delete protection preserve the evidence. Rendering and persistence are transactional: a PDF failure leaves no partial population/report/feedback record. Downloads recheck the reader's scope and digest and disable caching.
- V19 is additive. It does not reset passwords, create demo users, seed analytical findings, alter the Case Processing hierarchy or rewrite historical PDFs.

### Oracle reference and scope

Oracle Argus Insight describes retrieving a case series using queries and generating reports from that population. PharmaSafe implements its own bounded query/case-series/report workflow inspired by those concepts. It does not run Oracle software, provide an Oracle data mart/ETL integration, accept arbitrary advanced-condition SQL, or implement validated statistical signal detection.

References: [Generate Reports in Oracle Argus Insight](https://docs.oracle.com/en/industries/life-sciences/argus-safety/8.4.1/aiagx/generate-reports-argus-insight.html), [Work with Case Series](https://docs.oracle.com/en/industries/life-sciences/argus-safety/8.4.1/aiagx/work-case-series.html).
