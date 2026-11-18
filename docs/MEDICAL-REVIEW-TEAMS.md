# Medical Review teams and clinical work

Medical Review has a separate department hierarchy: Super Admin → Medical Reviewer Role Admin → Medical Team Leads → Medical Reviewer employees. Case Processing accounts cannot sign in through Medical Review portals, join Medical teams or receive Medical assignments. The existing Case Processing module keeps its own teams and APIs.

## Set up and use

1. Super Admin opens Role management, selects Medical Reviewer, and assigns its department administrator. The Medical Role Admin opens **Medical teams & review**.
2. In **Team Leads & teams**, create a named team and its Lead account. The system supplies an `MR-TL-...` ID and employee registration link. Up to ten active teams, each with ten pending/active employee seats, are supported independently of Case Processing.
3. Employees use that link, or Home → Medical Reviewer → New register and verify their Medical Team Lead ID. Complete the Medical qualification fields. The request appears only to that team's Lead, the Medical Role Admin and Super Admin. A Lead or either administrator can approve it. Login uses the Medical Reviewer card's separate User, Role Admin and Team Lead tabs.
4. The Medical Role Admin selects a team in **Team dashboard**, opens **Medical intake & allocation**, and allocates a case already in unlocked Medical Review. The Team Lead sees only their allocated cases.
5. The Lead selects **Assign work**, chooses the medical review stage, enters the employee's permanent `PS-...` staff ID and allocated case number, instructions and due time. Individual clinical assignments require a case. General training/instructions can be sent to the entire team as separate private assignments.
6. The employee opens **My work & PDFs → Open assignment**. Download the assignment PDF and inspect **Clinical source & labs**. Under **Causality & expectedness**, record product/event assessments. Under **Medical conclusion**, save the reviewer narrative, clinical reasoning, seriousness and severity with source/timeline confirmations. These conclusions do not replace the original processing narrative.
7. Submit progress, pending/remaining work, blockers and a reason. Every response creates an archived PDF visible to the employee and authorized supervisors. Completion uses actual saved clinical evidence, not only checkboxes. Assessment changes invalidate older conclusions by advancing the case revision.
8. For the **Quality handoff** stage, complete the task after all suspect product/event pairs are assessed and seriousness matches the source. Then enter a decision reason and **Send case to Quality Review**. To request source corrections, submit a blocked response first, then **Return case for processing correction**. The case returns to its recorded original active Case Processor; Medical access closes until it re-enters Medical Review.

Dashboards show scoped assignment status, completion, overdue work, employee counts, pending requests, medical queue counts and serious/assessed case counts. They refresh periodically; no artificial statistics or production demo records are inserted.

## Database and security

Migration `V20__medical_review_teams_and_work.sql` creates separate `mr_teams`, `mr_team_members`, work orders/assignments, progress updates, PDF archives, current case allocations, immutable allocation history and immutable medical conclusions. Registration requests gain an optional medical-team relationship. Existing accounts and case data are preserved.

Database foreign keys and triggers enforce department identity, team capacity, one active membership, fixed assignment recipients, current case allocation, reviewer ownership/revision and append-only evidence. Medical conclusions distinguish clinical severity from seriousness. PDFs retain their original bytes, snapshots and SHA-256 hashes. Assignments and clinical writes use transactions, row locks and stale-version checks. Ordinary case, laboratory, document and queue APIs also apply Medical employee scope.

Role Admin and Lead supervisory inspection is read-only for clinical assessments. Employees can submit only their own work. Super Admin retains cross-department administration. Legacy Medical Reviewer accounts without a team retain the existing unallocated Medical Review queue; allocated cases are restricted to the owning team/reviewer. Public team registration requires choosing a valid Medical team in the UI.

This implements the described workflow within PharmaSafe; it does not claim Oracle product certification, licensed dictionary integration or regulatory submission certification.

## Release

Deploy the backend first to apply V20, then the frontend. The repository CI runs frontend/backend tests, fresh/populated PostgreSQL-compatible migration checks and isolated browser workflows before Render deployment verification.
