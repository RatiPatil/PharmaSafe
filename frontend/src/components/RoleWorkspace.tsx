import { useCallback, useEffect, useRef, useState } from 'react';
import { useAuth, getXsrfToken } from '../context/AuthContext';
import { getApiUrl, fetchWithTimeout, setCsrfToken } from '../config/api';
import { CaseEntryForm } from './CaseEntryForm';
import type { SafetyCaseDetails, SafetyCaseSummary } from '../types/case';
import './RoleWorkspace.css';

type Row = Record<string, unknown>;
type Page<T> = { content: T[]; totalElements: number; totalPages: number };
type Section = 'overview' | 'cases' | 'new' | 'contacts' | 'tasks' | 'reports' | 'users' | 'audit';
const profiles: Record<string, { title: string; description: string; sections: Section[] }> = {
  ROLE_ADMIN: { title: 'Administration', description: 'Manage access, people, and system oversight.', sections: ['overview', 'users', 'audit', 'cases', 'new', 'tasks', 'contacts', 'reports'] },
  ROLE_CASE_PROCESSOR: { title: 'Case processing', description: 'Capture safety information and resolve returned cases.', sections: ['overview', 'cases', 'new', 'contacts', 'tasks'] },
  ROLE_MEDICAL_REVIEWER: { title: 'Medical review', description: 'Review clinical evidence, record assessments, and route cases.', sections: ['overview', 'cases', 'tasks', 'contacts'] },
  ROLE_QUALITY_REVIEWER: { title: 'Quality review', description: 'Check case completeness and approve or return for correction.', sections: ['overview', 'cases', 'tasks', 'contacts'] },
  ROLE_SAFETY_MANAGER: { title: 'Workflow management', description: 'Assign cases, manage priorities, and oversee case progression.', sections: ['overview', 'cases', 'tasks', 'contacts', 'reports'] },
  ROLE_REPORTING_SPECIALIST: { title: 'Regulatory reporting', description: 'Prepare report records and track deadlines for approved cases.', sections: ['overview', 'reports', 'cases'] },
  ROLE_AUDITOR: { title: 'Audit workspace', description: 'Inspect case records and audit history with read-only access.', sections: ['overview', 'cases', 'audit', 'reports'] },
};
const labels: Record<Section, string> = { overview: 'Overview', cases: 'Case queue', new: 'Create case', contacts: 'Contact logs', tasks: 'Action items', reports: 'Safety reports', users: 'Users & access', audit: 'Audit history' };
function roleProfile(role: string) { return profiles[role === 'ROLE_USER' ? 'ROLE_CASE_PROCESSOR' : role]; }
const text = (v: unknown) => v == null || v === '' ? '—' : String(v);
const readable = (key: string) => key.replace(/([A-Z])/g, ' $1').replace(/^./, c => c.toUpperCase());
async function request<T>(path: string, method = 'GET', body?: unknown): Promise<T> {
  if (method !== 'GET' && !getXsrfToken()) {
    const tokenResponse = await fetchWithTimeout(getApiUrl('/api/auth/csrf'), { credentials: 'include' });
    if (!tokenResponse.ok) throw new Error('Could not verify session security. Please sign in again.');
    const token = await tokenResponse.json();
    if (token.token) setCsrfToken(token.token);
  }
  const response = await fetchWithTimeout(getApiUrl(path), { method, credentials: 'include',
    headers: { Accept: 'application/json', ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
      ...(method === 'GET' ? {} : { 'X-XSRF-TOKEN': getXsrfToken() || '' }) },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }) });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(data.message || (response.status === 403 ? 'You do not have permission for this action.' : `Request failed (${response.status}).`));
  return data as T;
}
function Records({ rows }: { rows: Row[] }) {
  return rows.length ? <div className="ws-records">{rows.map((row, i) => <dl key={text(row.id ?? i)}>{Object.entries(row).filter(([k]) => !['passwordHash', 'id'].includes(k)).map(([k, v]) => <div key={k}><dt>{readable(k)}</dt><dd>{Array.isArray(v) ? (v.every(x => x && typeof x === 'object') ? <Records rows={v as Row[]} /> : v.map(text).join(', ')) : typeof v === 'object' && v !== null ? <Records rows={[v as Row]} /> : typeof v === 'boolean' ? (v ? 'Yes' : 'No') : text(v)}</dd></div>)}</dl>)}</div> : <p className="ws-muted">No records.</p>;
}
export function RoleWorkspace() {
  const { user, logout } = useAuth();
  const profile = roleProfile(user?.role || '');
  const route = useCallback((): Section => {
    const candidate = window.location.hash.split('/').pop() as Section;
    return profile?.sections.includes(candidate) ? candidate : 'overview';
  }, [profile]);
  const [section, setSection] = useState<Section>(route);
  const [page, setPage] = useState(0);
  const [query, setQuery] = useState('');
  const [search, setSearch] = useState('');
  const [overdue, setOverdue] = useState(false);
  const [cases, setCases] = useState<Page<SafetyCaseSummary>>({ content: [], totalElements: 0, totalPages: 0 });
  const [rows, setRows] = useState<Row[]>([]);
  const [totalRows, setTotalRows] = useState(0);
  const [selected, setSelected] = useState<SafetyCaseDetails | null>(null);
  const [history, setHistory] = useState<Row[]>([]);
  const [assignees, setAssignees] = useState<Row[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [comments, setComments] = useState('');
  const [form, setForm] = useState<Record<string, string>>({});
  const [checks, setChecks] = useState<Record<string, boolean>>({});
  const [refresh, setRefresh] = useState(0);
  const generation = useRef(0);
  const manager = ['ROLE_ADMIN', 'ROLE_SAFETY_MANAGER'].includes(user?.role || '');
  const processor = ['ROLE_ADMIN', 'ROLE_CASE_PROCESSOR', 'ROLE_USER'].includes(user?.role || '');
  const readOnly = user?.role === 'ROLE_AUDITOR';
  const reportWriter = ['ROLE_ADMIN', 'ROLE_SAFETY_MANAGER', 'ROLE_REPORTING_SPECIALIST'].includes(user?.role || '');
  const input = (name: string, label: string, type = 'text', required = true) => <label>{label}<input type={type} required={required} value={form[name] || ''} onChange={e => setForm(f => ({ ...f, [name]: e.target.value }))} /></label>;
  const go = (next: Section) => {
    setSelected(null); setPage(0); setError(''); setNotice(''); setForm({});
    window.history.pushState(null, '', `#/workspace/${(user?.role || '').replace('ROLE_', '').toLowerCase()}/${next}`);
    setSection(next);
  };
  useEffect(() => {
    const onHash = () => { setSection(route()); setSelected(null); setPage(0); setForm({}); };
    window.addEventListener('hashchange', onHash);
    window.addEventListener('popstate', onHash);
    return () => { window.removeEventListener('hashchange', onHash); window.removeEventListener('popstate', onHash); };
  }, [route]);
  useEffect(() => {
    const id = ++generation.current;
    let active = true;
    setError(''); setBusy(true); setRows([]);
    const load = async () => {
      if (!profile) return;
      if (section === 'overview' || section === 'cases') {
        const data = await request<Page<SafetyCaseSummary>>(`/api/workspace/cases?page=${page}&search=${encodeURIComponent(search)}`);
        if (active && id === generation.current) setCases(data);
      } else if (section !== 'new') {
        const paths: Partial<Record<Section, string>> = { users: '/api/admin/users', audit: '/api/workspace/audit', reports: `/api/reports?page=${page}`, tasks: `/api/action-items?page=${page}&overdue=${overdue}`, contacts: `/api/contact-logs?page=${page}` };
        const data = await request<Row[] | Page<Row>>(paths[section]!);
        if (active && id === generation.current) { setRows(Array.isArray(data) ? data : data.content); setTotalRows(Array.isArray(data) ? data.length : data.totalElements); }
      }
    };
    load().catch(e => { if (active && id === generation.current) setError(e.message); }).finally(() => { if (active && id === generation.current) setBusy(false); });
    return () => { active = false; };
  }, [section, page, search, refresh, profile, overdue]);
  useEffect(() => {
    if (manager) request<Row[]>('/api/workspace/assignees').then(setAssignees).catch(e => setError(e.message));
  }, [manager, refresh]);
  async function act(path: string, method: string, body?: unknown) {
    setBusy(true); setError(''); setNotice('');
    try { await request(path, method, body); setRefresh(v => v + 1); setNotice('Saved successfully.'); return true; }
    catch (e) { setError(e instanceof Error ? e.message : 'Action failed.'); return false; }
    finally { setBusy(false); }
  }
  async function openCase(id: number) {
    setBusy(true); setError(''); setForm({}); setChecks({}); setComments('');
    try {
      const c = await request<SafetyCaseDetails>(`/api/cases/${id}`);
      const h = await request<Row[]>(`/api/cases/${id}/audit-history`);
      setSelected(c); setHistory(h);
    } catch (e) { setError(e instanceof Error ? e.message : 'Could not open case.'); }
    finally { setBusy(false); }
  }
  async function transition(targetState: string) {
    if (selected && await act(`/api/cases/${selected.id}/workflow-transition`, 'POST', { targetState, comments: targetState === 'Approved' ? `QC checklist confirmed: patient/reporter; products/events; assessment/narrative. ${comments}` : comments })) await openCase(selected.id);
  }
  if (!profile) return <main className="ws-shell"><h1>Access not configured</h1><p>Ask your administrator to assign a supported workspace role.</p><button onClick={() => logout()}>Log Out</button></main>;
  const caseEditor = processor && (!selected || (!selected.locked && ['Draft', 'Data Entry'].includes(selected.workflowState)));
  const reviewTargets = !selected ? [] : selected.workflowState === 'Medical Review' && ['ROLE_ADMIN', 'ROLE_MEDICAL_REVIEWER'].includes(user!.role) ? ['Quality Review', 'Data Entry']
    : selected.workflowState === 'Quality Review' && ['ROLE_ADMIN', 'ROLE_QUALITY_REVIEWER'].includes(user!.role) ? ['Approved', 'Medical Review']
    : manager && selected.workflowState === 'Approved' ? ['Locked'] : manager && selected.workflowState === 'Locked' ? ['Closed'] : [];
  return <div className="ws-shell">
    <aside className="ws-sidebar"><a className="ws-brand" href="#/">PharmaSafe<span>SAFETY WORKSPACE</span></a><p>{profile.title}</p>
      <nav aria-label={`${profile.title} navigation`}>{profile.sections.map(s => <button key={s} aria-current={section === s ? 'page' : undefined} onClick={() => go(s)}>{labels[s]}</button>)}</nav>
      <div className="ws-person"><strong>{user?.username}</strong><span>{user?.role}</span><button onClick={() => logout()}>Log Out</button></div></aside>
    <div className="ws-main"><header className="ws-topbar"><span>{profile.title} / {labels[section]}</span><button disabled={busy} onClick={() => setRefresh(v => v + 1)}>Refresh</button></header>
    <main className="ws-content"><div className="ws-heading"><div><p className="ws-eyebrow">{profile.title}</p><h1>{selected ? selected.caseNumber : labels[section]}</h1><p>{profile.description}</p></div>{processor && section === 'overview' && <button className="ws-primary" onClick={() => go('new')}>Create case</button>}</div>
    {error && <div className="ws-error" role="alert">{error}<button onClick={() => setRefresh(v => v + 1)}>Retry</button></div>}{notice && <div role="status" className="ws-notice">{notice}</div>}{busy && <p role="status">Loading…</p>}
    {selected ? <><button onClick={() => setSelected(null)}>← Back to {labels[section]}</button>
      {caseEditor ? <CaseEntryForm key={selected.id} initialCase={selected} onCancel={() => setSelected(null)} onSaveSuccess={c => { setSelected(c); setRefresh(v => v + 1); setNotice('Case saved.'); }} /> : <section className="ws-card"><h2>Case record · {selected.workflowState}</h2><Records rows={[selected as unknown as Row]} /></section>}
      {reviewTargets.length > 0 && <section className="ws-card"><h2>{user?.role === 'ROLE_QUALITY_REVIEWER' ? 'Quality review decision' : 'Workflow decision'}</h2>
        {selected.workflowState === 'Quality Review' && <div className="ws-checks">{['Patient and reporter reviewed', 'Products and events reviewed', 'Medical assessment and narrative reviewed'].map(c => <label key={c}><input type="checkbox" checked={!!checks[c]} onChange={e => setChecks(v => ({ ...v, [c]: e.target.checked }))} />{c}</label>)}</div>}
        <label>Decision comments / return reason<textarea value={comments} onChange={e => setComments(e.target.value)} /></label><div className="ws-actions">{reviewTargets.map(target => <button key={target} disabled={busy || (target === 'Approved' && Object.values(checks).filter(Boolean).length !== 3)} onClick={() => transition(target)}>{target === 'Data Entry' || target === 'Medical Review' ? `Return to ${target}` : `Route to ${target}`}</button>)}</div></section>}
      {selected.workflowState === 'Medical Review' && ['ROLE_ADMIN', 'ROLE_MEDICAL_REVIEWER'].includes(user!.role) && <form className="ws-card ws-form" onSubmit={async e => { e.preventDefault(); if (await act(`/api/cases/${selected.id}/assessments`, 'POST', { causalityCompany: 'Possible', expectedness: 'Unexpected', ...form })) { setForm({}); await openCase(selected.id); } }}><h2>Medical assessment</h2>{input('productName', 'Product')}{input('eventVerbatim', 'Event')}<label>Company causality<select value={form.causalityCompany || 'Possible'} onChange={e => setForm(f => ({ ...f, causalityCompany: e.target.value }))}>{['Possible', 'Probable', 'Certain', 'Unlikely', 'Unassessable'].map(v => <option key={v}>{v}</option>)}</select></label>{input('assessmentComment', 'Assessment comment')}<button disabled={busy}>Save assessment</button></form>}
      {manager && <form className="ws-card ws-form" onSubmit={async e => { e.preventDefault(); if (await act(`/api/cases/${selected.id}/assign`, 'POST', { userId: Number(form.assignee) })) await openCase(selected.id); }}><h2>Case assignment</h2><label>Assign to<select required value={form.assignee || ''} onChange={e => setForm(f => ({ ...f, assignee: e.target.value }))}><option value="">Select a user</option>{assignees.map(a => <option key={text(a.id)} value={text(a.id)}>{text(a.username)} · {text(a.role)}</option>)}</select></label><button disabled={busy}>Assign case</button></form>}
      {manager && <section className="ws-card"><h2>Case controls</h2><div className="ws-actions"><label>Priority<select disabled={busy || selected.locked} value={selected.priority} onChange={async e => { if (await act(`/api/cases/${selected.id}/priority`, 'PATCH', { priority: e.target.value })) await openCase(selected.id); }}>{['Routine', 'Priority', 'Expedited'].map(p => <option key={p}>{p}</option>)}</select></label><button disabled={busy} onClick={async () => { if (await act(`/api/cases/${selected.id}/${selected.locked ? 'unlock' : 'lock'}`, 'POST')) await openCase(selected.id); }}>{selected.locked ? 'Unlock case' : 'Lock case'}</button></div></section>}
      <section className="ws-card"><h2>Case audit history</h2><Records rows={history} /></section></>
    : section === 'new' ? <CaseEntryForm onCancel={() => go('cases')} onSaveSuccess={c => { go('cases'); setSelected(c); setRefresh(v => v + 1); }} />
    : section === 'overview' || section === 'cases' ? <>
      {section === 'overview' && <section className="ws-card"><h2>Your workspace</h2><p><strong>{cases.totalElements}</strong> accessible cases in your queue.</p><div className="ws-actions">{profile.sections.filter(s => s !== 'overview').map(s => <button key={s} onClick={() => go(s)}>{labels[s]} →</button>)}</div></section>}
      <section className="ws-card"><div className="ws-heading"><h2>{profile.title} queue</h2><form className="ws-search" onSubmit={e => { e.preventDefault(); setPage(0); setSearch(query); }}><label className="ws-sr" htmlFor="case-query">Search case number</label><input id="case-query" placeholder="Search case number" value={query} onChange={e => setQuery(e.target.value)} /><button>Search</button></form></div><div className="ws-table-wrap"><table><thead><tr>{['Case', 'Product', 'Event', 'State', 'Priority', 'Assignee', 'Action'].map(x => <th key={x}>{x}</th>)}</tr></thead><tbody>{cases.content.map(c => <tr key={c.id}><td>{c.caseNumber}</td><td>{c.primaryProduct}</td><td>{c.primaryEvent}</td><td><span className="ws-badge">{c.workflowState}</span></td><td>{c.priority}</td><td>{c.assignedToUsername || 'Unassigned'}</td><td><button disabled={busy} onClick={() => openCase(c.id)}>Open case</button></td></tr>)}</tbody></table></div>{!busy && !cases.content.length && <p>No cases match this queue.</p>}<div className="ws-actions"><button disabled={page === 0 || busy} onClick={() => setPage(p => p - 1)}>Previous</button><span>Page {page + 1} · {cases.totalElements} cases</span><button disabled={page + 1 >= cases.totalPages || busy} onClick={() => setPage(p => p + 1)}>Next</button></div></section></>
    : <>
      {section === 'users' && <form className="ws-card ws-form" onSubmit={async e => { e.preventDefault(); if (await act('/api/admin/users', 'POST', { ...form, role: form.role || 'ROLE_CASE_PROCESSOR' })) setForm({}); }}><h2>Create user</h2>{input('username', 'Username')}{input('fullName', 'Full name')}{input('password', 'Initial password', 'password')}<label>Workspace role<select value={form.role || 'ROLE_CASE_PROCESSOR'} onChange={e => setForm(f => ({ ...f, role: e.target.value }))}>{Object.entries(profiles).map(([role, p]) => <option key={role} value={role}>{p.title}</option>)}</select></label><button disabled={busy}>Create user</button></form>}
      {section === 'reports' && reportWriter && <form className="ws-card ws-form" onSubmit={async e => { e.preventDefault(); if (await act('/api/reports', 'POST', { ...form, reportType: form.reportType || '15-day', relatedCaseNumbers: (form.caseNumbers || '').split(',').map(c => c.trim()).filter(Boolean) })) setForm({}); }}><h2>Prepare report record</h2><p>Internal report tracking. This action does not transmit a report to a regulator.</p>{input('product', 'Product')}{input('authority', 'Authority')}{input('dueDate', 'Due date', 'date')}{input('caseNumbers', 'Related case numbers (comma separated)')}<label>Report type<select value={form.reportType || '15-day'} onChange={e => setForm(f => ({ ...f, reportType: e.target.value }))}>{['7-day', '15-day', 'PSUR', 'DSUR', 'PBRER', 'PADER'].map(v => <option key={v}>{v}</option>)}</select></label>{input('summary', 'Summary')}<button disabled={busy}>Create report record</button></form>}
      {(section === 'contacts' || section === 'tasks') && !readOnly && <form className="ws-card ws-form" onSubmit={async e => { e.preventDefault(); const path = `/api/cases/${Number(form.caseId)}/${section === 'contacts' ? 'contact-logs' : 'action-items'}`; const body = section === 'contacts' ? { contactAt: new Date().toISOString(), contactType: 'Email', description: form.description } : { description: form.description, assignedUserId: Number(form.assignedUserId || user?.id), dueAt: new Date(form.dueAt).toISOString() }; if (await act(path, 'POST', body)) setForm({}); }}><h2>{section === 'contacts' ? 'Record contact' : 'Create action item'}</h2>{input('caseId', 'Case ID', 'number')}{input('description', 'Description')}{section === 'tasks' && <>{input('dueAt', 'Due date and time', 'datetime-local')}{manager && <label>Assignee<select required value={form.assignedUserId || ''} onChange={e => setForm(f => ({ ...f, assignedUserId: e.target.value }))}><option value="">Select user</option>{assignees.map(a => <option key={text(a.id)} value={text(a.id)}>{text(a.username)}</option>)}</select></label>}</>}<button disabled={busy}>Save</button></form>}
      <section className="ws-card"><h2>{labels[section]}</h2>{section === 'tasks' && <label className="ws-checks"><span><input style={{width:'auto', marginRight:8}} type="checkbox" checked={overdue} onChange={e => { setOverdue(e.target.checked); setPage(0); }} />Overdue only</span></label>}{!busy && !rows.length && <p>No records available.</p>}{rows.map(row => <article className="ws-item" key={text(row.id)}><Records rows={[row]} /><div className="ws-actions">
        {section === 'users' && <><label>Workspace role<select aria-label={`Role for ${text(row.username)}`} value={text(row.role)} disabled={busy || row.username === user?.username} onChange={e => act(`/api/admin/users/${row.id}`, 'PUT', { role: e.target.value })}>{!profiles[text(row.role)] && <option value={text(row.role)}>{text(row.role)}</option>}{Object.entries(profiles).map(([role,p]) => <option key={role} value={role}>{p.title}</option>)}</select></label><button disabled={busy || row.username === user?.username} onClick={() => act(`/api/admin/users/${row.id}/toggle-status`, 'PATCH')}>{row.enabled ? 'Deactivate' : 'Activate'}</button></>}
        {section === 'tasks' && (row.assignedUsername === user?.username || user?.role === 'ROLE_ADMIN') && !['COMPLETED', 'CANCELLED'].includes(text(row.status)) && <button disabled={busy} onClick={() => act(`/api/action-items/${row.id}/complete`, 'POST')}>Complete action</button>}
        {section === 'tasks' && manager && !['COMPLETED', 'CANCELLED'].includes(text(row.status)) && <label>Reassign action<select value={String(assignees.find(a => a.username === row.assignedUsername)?.id || '')} disabled={busy} onChange={e => act(`/api/action-items/${row.id}/assignment?assignedUserId=${Number(e.target.value)}`, 'PATCH')}><option value="">Select assignee</option>{assignees.map(a => <option key={text(a.id)} value={text(a.id)}>{text(a.username)}</option>)}</select></label>}
        {section === 'reports' && reportWriter && row.status !== 'Submitted' && <button disabled={busy} onClick={() => { if (window.confirm('Record this report as submitted? This does not transmit it externally.')) act(`/api/reports/${row.id}/submit`, 'POST'); }}>Record submission</button>}
      </div></article>)}{['reports', 'tasks', 'contacts'].includes(section) && <div className="ws-actions"><button disabled={page === 0 || busy} onClick={() => setPage(p => p - 1)}>Previous</button><span>Page {page + 1}</span><button disabled={(page + 1) * 20 >= totalRows || busy} onClick={() => setPage(p => p + 1)}>Next</button></div>}</section>
    </>}
    </main></div></div>;
}
