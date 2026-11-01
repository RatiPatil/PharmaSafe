export type Section = 'overview' | 'cases' | 'new' | 'contacts' | 'tasks' | 'reports' | 'users' | 'audit' | 'profile';
export type Row = Record<string, unknown>;
export type Page<T> = { content: T[]; totalElements: number; totalPages: number };
export type Overview = {
  totalCases: number; seriousCases: number; expeditedCases: number; states: Record<string, number>;
  totalUsers?: number; enabledUsers?: number; auditEvents?: number; recentAudit?: Row[];
  workload?: Record<string, number>; totalReports?: number; overdueReports?: number;
  upcomingReports?: Row[]; openActions?: number; overdueActions?: number;
};
export const profiles: Record<string, { title: string; home: string; queue: string; description: string; sections: Section[] }> = {
  ROLE_ADMIN: { title: 'Administration', home: 'Access & system oversight', queue: 'All safety cases', description: 'Manage accounts and inspect system activity.', sections: ['overview', 'users', 'audit', 'cases', 'new', 'tasks', 'contacts', 'reports'] },
  ROLE_CASE_PROCESSOR: { title: 'Case processing', home: 'My processing desk', queue: 'My case worklist', description: 'Capture safety information and prepare cases for review.', sections: ['overview', 'cases', 'new', 'contacts', 'tasks'] },
  ROLE_MEDICAL_REVIEWER: { title: 'Medical review', home: 'Clinical review desk', queue: 'Medical review queue', description: 'Review clinical evidence and record medical decisions.', sections: ['overview', 'cases', 'tasks', 'contacts'] },
  ROLE_QUALITY_REVIEWER: { title: 'Quality review', home: 'Quality control desk', queue: 'Quality review queue', description: 'Check completeness and document the quality decision.', sections: ['overview', 'cases', 'tasks', 'contacts'] },
  ROLE_SAFETY_MANAGER: { title: 'Workflow management', home: 'Team operations', queue: 'Team case board', description: 'Balance assignments and monitor case progression.', sections: ['overview', 'cases', 'tasks', 'contacts', 'reports'] },
  ROLE_REPORTING_SPECIALIST: { title: 'Regulatory reporting', home: 'Reporting control desk', queue: 'Approved cases', description: 'Prepare report records and track regulatory deadlines.', sections: ['overview', 'reports', 'cases'] },
  ROLE_AUDITOR: { title: 'Audit workspace', home: 'Inspection desk', queue: 'Case explorer', description: 'Inspect case records and history with read-only access.', sections: ['overview', 'cases', 'audit', 'reports'] },
};
Object.values(profiles).forEach(profile => { if(!profile.sections.includes('reports')) profile.sections.push('reports'); profile.sections.push('profile'); });
export const canonicalRole = (role: string) => role === 'ROLE_USER' ? 'ROLE_CASE_PROCESSOR' : role;
export const labels: Record<Section, string> = { overview: 'Overview', cases: 'Case queue', profile: 'My profile', new: 'Create case', contacts: 'Contact logs', tasks: 'Action items', reports: 'Safety reports', users: 'Users & access', audit: 'Audit history' };
export const text = (v: unknown) => v == null || v === '' ? '—' : String(v);
export const date = (v: unknown) => {
 if (!v) return '—';
 const value=String(v), parsed=new Date(/^\d{4}-\d{2}-\d{2}$/.test(value)?`${value}T00:00:00`:value);
 return Number.isNaN(parsed.getTime())?'—':parsed.toLocaleDateString(undefined,{day:'2-digit',month:'short',year:'numeric'});
};
export const dateTime = (v: unknown) => v && !Number.isNaN(new Date(String(v)).getTime()) ? new Date(String(v)).toLocaleString() : '—';
export const roleName = (role: unknown) => profiles[canonicalRole(String(role))]?.title || text(role);
