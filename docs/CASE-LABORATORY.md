# Case laboratory evidence

Based on Oracle Argus Safety User Guide, Enter patient information / Enter lab data: https://docs.oracle.com/health-sciences/argus-safety-812/user-EN/caseact.htm

Open an existing case and find Laboratory evidence. Case processors (including legacy USER), administrators and safety managers can add results during unlocked Draft/Data Entry. Medical/quality reviewers, reporting specialists and auditors can read within their existing case access scope. New evidence cannot be added to locked or reviewed cases without the existing return/reopen workflow.

Each result stores test name, full test date, reported result as text (supports qualitative or numeric values), optional units and numeric lower/upper reference limits, explicit human-selected interpretation and notes. Interpretation is not automatically inferred and no licensed test dictionary is bundled. Partial dates and grouped test panels are not implemented. The per-case record limit is 1500, including voided evidence.

## Database

Flyway V11 creates `case_lab_results`, keyed by generated ID with a required case foreign key, chronological case index, creator username/time, and correction metadata. Case and actor deletion is restricted to preserve evidence. Existing tables and data are untouched. Decimal ranges use NUMERIC(18,6); database checks reject inverted ranges, blank essential text, invalid interpretations and incomplete void metadata. Future dates are rejected at the API boundary.

The original evidence cannot be changed or deleted in PostgreSQL. A single reasoned void is allowed; a trigger prevents modifying original values or revising an already voided record. Add a new result to replace incorrect evidence. User-visible records and PDF snapshots retain voided values and reasons. There is no destructive edit/delete API. Before applying production migrations, retain a normal database backup; never edit applied Flyway files.

Writes lock the case and require its current rowVersion. Every add/void increments the case version, flushes its optimistic row version and records a transactional audit event. Stale writes return 409. Reload before retrying a conflict; no requests are replayed automatically. Read endpoints enforce the existing case scope; CSRF and backend role checks protect writes.

## API

- GET /api/cases/{id}/laboratory: chronologically ordered results, including voided history.
- POST /api/cases/{id}/laboratory: add evidence with expectedRowVersion.
- POST /api/cases/{id}/laboratory/{labId}/void: expectedRowVersion and mandatory reason.

Both newly generated case-form PDFs and medical summaries archive laboratory evidence as part of their immutable source snapshot. Existing archived PDFs remain unchanged. This is an internal case-management feature, not an E2B submission implementation.

## Deployment and verification

Deploy backend first: Flyway applies V11 to the configured PostgreSQL database. Then deploy frontend. No manual table creation or seed data is needed. Automatic Render deployment remains disabled.

Verification covers fresh and populated PostgreSQL migration paths; evidence immutability and range checks; role and case scope; CSRF; stale version rejection; round-trip persistence, void history and PDF snapshots; UI error preservation; and real browser entry before the seven-role clinical workflow.
