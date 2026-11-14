# Report evidence, PDF archives and small synthetic dataset

This extends PharmaSafe's existing internal reporting register. It is inspired by
Oracle Argus Safety's [scheduled report review](https://docs.oracle.com/en/industries/life-sciences/argus-safety/8.4.2/aeoaf/review-your-scheduled-reports.html),
[acknowledgement information](https://docs.oracle.com/en/industries/life-sciences/argus-safety/8.4.4/aeobb/view-acknowledgement-information.html)
and [report generation/archive concepts](https://docs.oracle.com/health-sciences/argus-safety-811/AEOAF/argusreps.htm).
This implementation records external outcomes manually. It does not implement an
E2B gateway, automatic acknowledgement parsing, regulator transmission, certified
MedWatch/CIOMS forms or electronic signatures.

## Use

Open **Safety reports → Report details & PDF**. A report's details show its stored
counts, period, due date, data lock point, narrative, archived PDFs and receiver
acknowledgements. Reporting-period dates, conclusions and recommendations are now
available on the preparation form. Stored case counts describe scheduling time;
the PDF's linked-case listing describes generation time.

**Generate report PDF** freezes a report snapshot, its linked-case listing and
complete acknowledgement history into an archive before downloading. The original
UTF-8 JSON is embedded in the PDF. Downloading an existing copy never regenerates
it from current data. Each archive has a SHA-256 digest checked at download,
recorded author/time/source report revision, an opaque document ID and a safe file
name. PDFs are private authenticated downloads with no-store headers. The archive
is an internal working record, not an official transmitted form. Existing case-form
and medical-summary PDF routes and archives remain available.

After a submission has been manually recorded, authorized reporting writers can
record an accepted/rejected receiver outcome, reference, received time, message
and mandatory recording/correction reason. Received time cannot precede the
recorded submission (second precision) or be in the future. Corrections append a
new entry; they do not overwrite the receiver's earlier outcome. Submission status
and receiver outcome are separate facts. Repeating the submission action preserves
its original timestamp. Recording submission does not transmit a report.

The details screen lists the newest 50 documents and 50 acknowledgement entries,
with total history counts. The database retains the complete history and each PDF
includes all acknowledgements. PDF snapshots have a 5 MB source limit and the
renderer rejects documents beyond 400 pages instead of silently truncating them.

## Authorization

Super Admin, Safety Manager and Reporting Specialist retain reporting write
permissions. Auditor can inspect/generate/download internal reports but cannot
record receiver outcomes. Ordinary clinical users cannot read this report API.
Role Admin can read/generate/download only reports whose creator belongs to their
assigned role; database identity and assignment are checked on every request.
Their **Reporting work** screen exposes the same evidence panel without write
controls. Manipulating a report ID or a document ID cannot bypass scope checks.

| API | Purpose |
| --- | --- |
| `GET /api/reports/{id}/evidence` | Details, newest history and total counts |
| `POST /api/reports/{id}/acknowledgements` | Append receiver outcome with `expectedVersion` |
| `POST /api/reports/{id}/documents` | Archive PDF with `expectedVersion` |
| `GET /api/reports/{id}/documents/{documentId}/pdf` | Download that report's original PDF |

## Database migrations

V14 adds `safety_reports.row_version`, counts/date constraints, permanent
`cases.synthetic_record`, and two normalized child tables:

- `report_acknowledgements`: report/actor foreign keys, outcome enum, reference,
  received/recorded times, message/reason and source revision. An index supports
  report history in recorded-time order.
- `report_document_exports`: report/actor foreign keys, JSONB snapshot, BYTEA PDF,
  digest, file name, author/time and source revision. An index supports archive
  history in generation-time order.

Database triggers reject UPDATE/DELETE of either evidence table. No report/case
relationships are replaced. Report mutations use a pessimistic row lock; new
acknowledgement/archive requests also check the expected optimistic revision and
return 409 for stale requests. New counts must satisfy `0 <= fatal <= serious <=
total`; period end cannot precede start and the data lock point cannot precede
period end. Constraints use NOT VALID for historical rows so migration preserves
legacy data while enforcing constraints on new writes.

V15 adds exactly three labelled intake cases (`DEMO-PS-001` through `003`) to an
existing installation with an approved Super Admin. They have fictional patient,
reporter, product and event records; distinct priorities; one serious example; and
no invented assessment, approval or submission. They are assigned to the oldest
active Case Processor if one exists. Dashboard values reflect these actual stored
records, not decorative numbers. Other roles receive work as it moves through the
normal workflow. No accounts or passwords are seeded.

The seed skips reserved case numbers already present and is idempotent. A fresh
installation with no accounts at Flyway time remains empty. The synthetic identity
cannot be changed after insertion, appears in case summaries/details/PDF headers,
and prevents linked reports from being recorded as regulator submissions. The
database also prevents mixing synthetic and real cases in the same report. The
three labelled samples are an explicit user-requested exception to the original
no-production-samples policy. Existing real cases and statistics are preserved.

## Verification

Tests cover PDF parsing, embedded source data, byte-identical archive downloads,
stale versions, invalid periods/counts, append-only corrections, timestamps,
clinical/auditor permissions, Role Admin boundaries and synthetic submission denial.
PostgreSQL-compatible migration checks exercise preserved upgrades, evidence
immutability and repeatable three-case seeding. Browser checks download both case
and report PDFs, retrieve the same report archive again, enter a receiver response,
check mobile widths, and download the report through the assigned Role Admin.
