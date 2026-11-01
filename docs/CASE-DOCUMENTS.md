# PharmaSafe Safety Suite: case documents and role workspaces

This release adds original case-report functionality informed by Oracle Argus user documentation. PharmaSafe Safety Suite is independent software; these PDFs are internal case records, not validated regulatory submissions or signed regulatory forms.

## Use the application

- **My profile** shows the current account, department, actual server-assigned role and role-specific workflow responsibilities. Users can update their own display name and department. Username and role remain administrator-controlled. Profile changes use the existing account revision to reject stale writes.
- **Case documents / Safety reports** shows only cases allowed by the existing role and workflow scope. A reporting specialist sees approved, locked and closed cases; a medical reviewer sees the medical queue; quality reviewers see the QC queue; processors see their owned/assigned cases; auditors inspect accessible evidence read-only.
- Open a case, then use **Case PDF documents**. Save clinical edits first. Choose Complete case form or, where allowed, Medical summary. The complete form includes case administration, patient, products, events, assessments, medical history, narrative, reporters, references, attachment metadata, follow-ups, contacts, action items, workflow decisions, scheduled report metadata and audit history as of generation.
- Administrators, safety managers and reporting specialists can select **Issue final closed-case record** only after closure. Open actions and pending scheduled expedited reports block final issuance. Working copies are clearly marked for other stages.
- Generated PDFs appear in the archive table. Downloading an archived row retrieves the same bytes, even if a later follow-up reopens and changes the source case. Current case access is rechecked on every download.

Medical summaries contain clinical and administrative sections, not reporter/contact history. Generating that format is limited to medical reviewers, managers, administrators and reporting specialists. The complete case form remains available within normal case access.

Original uploaded attachment files are not available in the current application; exports identify attachment metadata honestly. PDFs contain an embedded `source-data.json` with the original UTF-8 snapshot. The embedded font supports Latin, Greek and Cyrillic; unsupported glyphs are rendered visibly as `[U+XXXX]`, with original text preserved in the JSON. No text is silently removed. Font fallback is not full CJK/Indic typesetting.

## Database and integrity

Flyway **V10__case_document_archive.sql** creates `case_document_exports`. Existing clinical/user rows and V1–V9 migrations are unchanged.

| Column / relationship | Meaning |
|---|---|
| id | UUID document identifier |
| case_id | Foreign key to the original case; deletion restricted |
| document_kind, final_document | Selected format and final/working designation |
| case_version, source_row_version, workflow_state | Source revision and workflow at generation |
| generated_by_user_id / username, generated_at | Original actor and UTC generation timestamp |
| template_version | Renderer template used |
| snapshot_json | Frozen selected data in PostgreSQL JSONB |
| pdf_bytes | Generated PDF stored as BYTEA, not a filesystem pointer or PostgreSQL large-object OID |
| pdf_sha256 | SHA-256 of the stored PDF, verified before download |
| file_name | Safe download name |

Database checks enforce valid formats, positive source versions and Closed state for final records. An UPDATE/DELETE trigger makes archive rows immutable. The service uses a repeatable-read transaction and locks the source case during snapshot generation. Creation and case audit logging commit together. Generation rejects stale source revisions; a failed render does not save an incomplete document. There is no archive edit/delete endpoint.

The snapshot limit is 5 MB and the renderer rejects documents over 400 pages rather than truncating content. Exported PDFs include timestamps, page numbering, repeating field/value table headers and continuation pages for long values. Generating an export does not change the clinical case version or workflow.

PDF downloads require the signed-in session, recheck case scope, use no-store response headers and log the download. A checksum mismatch blocks delivery. No public document URLs are created. As with any exported file, downloaded copies exist outside application access controls.

## Read-only inspection in Supabase

```sql
SELECT c.case_number, d.id, d.document_kind, d.final_document,
       d.case_version, d.source_row_version, d.workflow_state,
       d.generated_by_username, d.generated_at, d.pdf_sha256,
       octet_length(d.pdf_bytes) AS pdf_size_bytes
FROM case_document_exports d JOIN cases c ON c.id = d.case_id
ORDER BY d.generated_at DESC, d.id LIMIT 50;

SELECT username, full_name, department, role, account_row_version
FROM users ORDER BY username;
```

No new profile table duplicates existing user fields. Profile updates cannot change role, username, enabled status or password, and do not revoke a valid session just for changing display information.

## Stable navigation and login

The login uses actual grid header sizing and the dynamic viewport, rather than subtracting a guessed mobile header height. Normal sign-in fits without a document scrollbar. Short screens, large text and longer error messages scroll inside the access panel so controls remain reachable. Workspaces keep their existing sidebar/content scrolling, unsaved-change guards, case deep links and distinct role homes. Case queues add workflow and priority filters.

## Verification and deployment

Tests cover archived byte identity after source edits, embedded source equality, long narrative pagination, Unicode preservation, final issuance restrictions, scoped downloads, stale revisions, CSRF, profile privilege protection and archive immutability during fresh/populated V1–V10 migration checks. Browser checks exercise all seven profiles, role workflows, final PDF download and repeat download, login layouts and navigation. Synthetic PDF artifacts support visual inspection.

Deploy backend first and confirm Flyway V10 and health, then deploy frontend. Retain V10 and archive data when rolling application code back; do not remove document history.

Oracle references: [case-form and medical-summary printing](https://docs.oracle.com/health-sciences/argus-safety-812/user-EN/reviewyourcases.htm), [group-based access and workflow managers](https://docs.oracle.com/health-sciences/argus-safety-812/AADMN/userconfig.htm).
