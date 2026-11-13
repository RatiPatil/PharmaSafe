# Role dashboards and input validation

Seven roles have dashboards: administrator, case processor, medical reviewer,
quality reviewer, safety manager, reporting specialist and auditor. The public
home continues to offer six registration cards. Administrator access remains
provisioned by an authorized administrator; public signup cannot grant that role.
ROLE_USER remains a legacy processor alias, rather than an eighth role.

The Dashboard navigation item retains the existing overview route. New aggregates
use the same case visibility specification as the worklist. Account and approval
statistics are administrator-only. Report counts remain available to roles already
permitted to review reports. Distributions count accessible cases, not adverse-event
rows. Receipt activity groups cases by their current latest receipt date for the
last 14 UTC calendar dates; it is not an immutable history of new cases.
Assessment/narrative indicators mean presence, not validated clinical completeness.
Stale cases have a latest receipt at least 14 days old and exclude Closed/Submitted.
Empty scopes display zero or an empty state, never fabricated numbers.

## Shared rules

`frontend/src/config/inputRules.json` is canonical. Its identical backend resource
is checked by `node scripts/verify-input-rules.mjs` in CI. After an intentional
change, run that command with `--sync`. Existing DTO required-field checks,
role-specific registration requirements and final-case validation still apply.

All JSON controller request bodies are checked before persistence. Frontend API
preflight applies the same format, length, range and enumeration rules; native
fields also receive appropriate size/range limits and feedback when identifiable.
Server checks remain authoritative even when browser validation is bypassed.

- Names allow Unicode letters and common name punctuation; identifiers have
  their own patterns. Clinical narrative is free text and accepts numbers,
  line breaks, comparison symbols and medical units.
- Emails, phone numbers, usernames, postal codes and ISO country codes have
  field-appropriate checks. Registration choices match the role dropdowns.
- Dates must be real calendar dates; related end dates cannot precede starts,
  birth dates cannot be future dates and reference ranges cannot be inverted.
- Age, dose, height, weight, IDs and lab reference values have numeric limits
  and decimal precision appropriate to their stored fields. Known workflow,
  product, assessment and report values are restricted to supported options.
- New passwords retain the strength requirement and bcrypt's 72-byte bound.
  Existing short passwords remain usable for login; login accepts username or
  email. Passwords are never trimmed. Validation errors do not echo values.
- Search/query text is bounded to 200 characters. Paging and assignment IDs
  must be valid whole numbers. Unexpected control characters, oversized arrays
  and deeply nested inputs are rejected.

Drafts may remain incomplete. A malformed supplied value must be corrected even
when saving a draft; final-save requirements are unchanged. This adds request
validation without a database migration or changes to role permissions.

The login now uses a centered, compact sign-in card. Public home and registration
remain separate screens. Small/short viewports retain accessible page scrolling.
