// Public account suggestions only. Passwords must never be bundled with the app.
export const ROLE_ADMIN_USERNAMES: Readonly<Record<string, string>> = {
  ROLE_CASE_PROCESSOR: 'pv.processor.admin',
  ROLE_MEDICAL_REVIEWER: 'pv.medical.admin',
  ROLE_QUALITY_REVIEWER: 'pv.quality.admin',
  ROLE_SAFETY_MANAGER: 'pv.manager.admin',
  ROLE_REPORTING_SPECIALIST: 'pv.reporting.admin',
  ROLE_AUDITOR: 'pv.auditor.admin',
};
