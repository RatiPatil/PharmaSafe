import { useCallback, useEffect, useRef, useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { apiRequest as request } from '../config/api';
import { WorkflowBoard } from '../workspaces/WorkflowBoard';
import { WorkspaceHome } from '../workspaces/WorkspaceHome';
import { CaseRecordView } from '../workspaces/CaseRecordView';
import { RecordTable } from '../workspaces/RecordTable';
import { profiles, labels, canonicalRole, text } from '../workspaces/model';
import type { Row, Page, Section, Overview } from '../workspaces/model';
import { CaseActivities } from '../workspaces/CaseActivities';
import { CaseEntryForm } from './CaseEntryForm';
import type { SafetyCaseDetails, SafetyCaseSummary } from '../types/case';
import './RoleWorkspace.css';

function roleProfile(role: string) { return profiles[canonicalRole(role)]; }
export function RoleWorkspace() {
  const { user, logout } = useAuth();
  const profile = roleProfile(user?.role || '');
  useEffect(() => {
    document.body.classList.add('workspace-open');
    if(document.scrollingElement) document.scrollingElement.scrollTop = 0;
    const root = document.getElementById('root'); if(root) root.scrollTop = 0;
    return () => document.body.classList.remove('workspace-open');
  }, []);
  const route = useCallback((): Section => {
    const match = window.location.hash.match(/^#\/workspace\/[^/]+\/([^/?]+)/);
    const candidate = match?.[1] as Section;
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
  const [loading, setLoading] = useState(false);
  const [mutating, setMutating] = useState(false);
  const busy = loading || mutating;
  const [menuOpen, setMenuOpen] = useState(false);
  const [dirty, setDirty] = useState(false);
  const [activityDirty,setActivityDirty]=useState(false);
  const [closureReady,setClosureReady]=useState(false);
  const [overview, setOverview] = useState<Overview | null>(null);
  const lastRoute = useRef(window.location.hash);
  const caseLoad = useRef(0);
  const actionPending = useRef(false);
  const mainPanel = useRef<HTMLElement>(null);
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
  useEffect(() => {
    if(menuOpen) document.querySelector<HTMLElement>('#workspace-navigation a')?.focus();
  }, [menuOpen]);
  const confirmLeave = () => !(activityDirty || dirty || Object.values(form).some(Boolean) || Object.values(checks).some(Boolean)) || window.confirm('Discard unsaved changes and leave this page?');
  const routeUrl = (next: Section, id?: number) => `#/workspace/${canonicalRole(user?.role || '').replace('ROLE_', '').toLowerCase()}/${next}${id ? `/${id}` : ''}`;
  const go = (next: Section, saved = false) => {
    if (actionPending.current) { setError('Please wait for the current save to finish.'); return; }
    if (!saved && !confirmLeave()) return;
    caseLoad.current++; setMutating(false);
    setSelected(null); setPage(0); setSearch(''); setQuery(''); setError(''); setNotice(''); setForm({}); setChecks({}); setComments(''); setDirty(false); setMenuOpen(false);
    const url = routeUrl(next);
    window.history.pushState(null, '', url); lastRoute.current = url;
    setSection(next);
    mainPanel.current?.scrollTo?.(0,0);
  };
  useEffect(() => {
    const onHash = () => {
      const url = window.location.hash;
      if (url === lastRoute.current && profile?.sections.includes(route())) return;
      if (!confirmLeave()) { window.history.pushState(null, '', lastRoute.current); return; }
      const next = route();
      const idMatch = url.match(/\/(\d+)$/);
      const id = next === 'cases' && idMatch ? Number(idMatch[1]) : undefined;
      const normalized = routeUrl(next, id);
      window.history.replaceState(null, '', normalized); lastRoute.current = normalized;
      caseLoad.current++; setMutating(false);
      setSection(next); setSelected(null); setPage(0); setSearch(''); setQuery(''); setError(''); setNotice(''); setForm({}); setChecks({}); setComments(''); setDirty(false); setMenuOpen(false);
      if (id) void openCase(id, false);
    };
    const onUnload = (event: BeforeUnloadEvent) => { if (activityDirty || dirty || Object.values(form).some(Boolean) || Object.values(checks).some(Boolean)) { event.preventDefault(); event.returnValue = ''; } };
    const onEscape = (event: KeyboardEvent) => { if(event.key === 'Escape') setMenuOpen(false); };
    window.addEventListener('hashchange', onHash);
    window.addEventListener('popstate', onHash);
    window.addEventListener('beforeunload', onUnload);
    window.addEventListener('keydown', onEscape);
    return () => { window.removeEventListener('hashchange', onHash); window.removeEventListener('popstate', onHash); window.removeEventListener('beforeunload', onUnload); window.removeEventListener('keydown', onEscape); };
  }, [route, activityDirty, dirty, form, checks, user?.role]);
  useEffect(() => {
    const next = route();
    const id = next === 'cases' ? Number(window.location.hash.match(/\/(\d+)$/)?.[1]) : 0;
    const url = routeUrl(next, id || undefined);
    window.history.replaceState(null, '', url); lastRoute.current = url;
    if (id) void openCase(id, false);
  }, [user?.id, user?.role]);
  useEffect(() => {
    const id = ++generation.current;
    let active = true;
    const controller = new AbortController();
    setError(''); setLoading(true); setRows([]);
    const load = async () => {
      if (!profile) return;
      if (section === 'overview') {
        const [summary, queue] = await Promise.all([request<Overview>('/api/workspace/overview', 'GET', undefined, controller.signal), request<Page<SafetyCaseSummary>>('/api/workspace/cases?size=6', 'GET', undefined, controller.signal)]);
        if(active && id === generation.current) { setOverview(summary); setCases(queue); }
      } else if (section === 'cases') {
        const data = await request<Page<SafetyCaseSummary>>(`/api/workspace/cases?page=${page}&search=${encodeURIComponent(search)}`, 'GET', undefined, controller.signal);
        if (active && id === generation.current) setCases(data);
      } else if (section !== 'new') {
        const paths: Partial<Record<Section, string>> = { users: `/api/admin/users/paged?page=${page}&search=${encodeURIComponent(search)}`, audit: `/api/workspace/audit/paged?page=${page}&search=${encodeURIComponent(search)}`, reports: `/api/reports?page=${page}`, tasks: `/api/action-items?page=${page}&overdue=${overdue}`, contacts: `/api/contact-logs?page=${page}` };
        const data = await request<Row[] | Page<Row>>(paths[section]!, 'GET', undefined, controller.signal);
        if (active && id === generation.current) { setRows(Array.isArray(data) ? data : data.content); setTotalRows(Array.isArray(data) ? data.length : data.totalElements); }
      }
      if (section === 'contacts' || section === 'tasks') {
        const choices = await request<Page<SafetyCaseSummary>>('/api/workspace/cases?size=50', 'GET', undefined, controller.signal);
        if(active && id === generation.current) setCases(choices);
      }
    };
    load().catch(e => { if (active && id === generation.current) setError(e.message); }).finally(() => { if (active && id === generation.current) setLoading(false); });
    return () => { active = false; controller.abort(); };
  }, [section, page, search, refresh, profile, overdue]);
  useEffect(() => {
    const controller = new AbortController();
    if (manager) request<Row[]>('/api/workspace/assignees', 'GET', undefined, controller.signal).then(setAssignees).catch(e => { if(!controller.signal.aborted) setError(e.message); });
    return () => controller.abort();
  }, [manager, refresh]);
  async function act(path: string, method: string, body?: unknown) {
    if (activityDirty) {setError('Finish the open activity form first.');return false;}
    if (actionPending.current) return false;
    actionPending.current = true;
    setMutating(true); setError(''); setNotice('');
    try { await request(path, method, body); setRefresh(v => v + 1); setNotice('Saved successfully.'); return true; }
    catch (e) { setError(e instanceof Error ? e.message : 'Action failed.'); return false; }
    finally { actionPending.current = false; setMutating(false); }
  }
  async function openCase(id: number, navigate = true) {
    if(navigate && !confirmLeave()) return;
    const token = ++caseLoad.current;
    setMutating(true); setError(''); setForm({}); setChecks({}); setComments(''); setDirty(false);setActivityDirty(false);setClosureReady(false);
    try {
      const [c,h] = await Promise.all([request<SafetyCaseDetails>(`/api/cases/${id}`), request<Row[]>(`/api/cases/${id}/audit-history`)]);
      if(token !== caseLoad.current) return;
      setSelected(c); setHistory(h); setSection('cases');
      if(navigate) { const url=routeUrl('cases',id); window.history.pushState(null,'',url); lastRoute.current=url; }
      mainPanel.current?.scrollTo?.(0,0);
    } catch (e) { if(token === caseLoad.current) setError(e instanceof Error ? e.message : 'Could not open case.'); }
    finally { if(token === caseLoad.current) setMutating(false); }
  }
  async function transition(targetState: string) {
    if (!selected) return;
    const ok = await act(`/api/cases/${selected.id}/workflow-transition`, 'POST', {
      targetState, qcChecklist: targetState === 'Approved' ? ['patient','productsEvents','medical'] : [],
      comments: targetState === 'Approved' ? `QC checklist confirmed: patient/reporter; products/events; assessment/narrative. ${comments}` : comments
    });
    if (!ok) return;
    if (['ROLE_MEDICAL_REVIEWER','ROLE_QUALITY_REVIEWER'].includes(user?.role || '')) {
      go('cases',true); setNotice(`Case routed to ${targetState}. It has left your review queue.`);
    } else await openCase(selected.id,false);
  }
  if (!profile) return <main className="ws-shell"><h1>Access not configured</h1><p>Ask your administrator to assign a supported workspace role.</p><button onClick={() => logout()}>Log Out</button></main>;
  const caseEditor = processor && (!selected || (!selected.locked && ['Draft', 'Data Entry'].includes(selected.workflowState)));
  const reviewTargets = !selected ? [] : selected.workflowState === 'Medical Review' && ['ROLE_ADMIN', 'ROLE_MEDICAL_REVIEWER'].includes(user!.role) ? ['Quality Review', 'Data Entry']
    : selected.workflowState === 'Quality Review' && ['ROLE_ADMIN', 'ROLE_QUALITY_REVIEWER'].includes(user!.role) ? ['Approved', 'Medical Review']
    : manager && selected.workflowState === 'Approved' ? ['Locked'] : manager && selected.workflowState === 'Locked' ? ['Closed'] : [];
  return <div className="ws-shell">
    <aside role={menuOpen ? 'dialog' : undefined} aria-modal={menuOpen || undefined} aria-label={menuOpen ? 'Workspace navigation' : undefined} onKeyDown={e=>{if(e.key==='Tab' && menuOpen){const items=e.currentTarget.querySelectorAll<HTMLElement>('a[href],button:not([disabled])');const first=items[0],last=items[items.length-1];if(e.shiftKey&&document.activeElement===first){e.preventDefault();last?.focus();}else if(!e.shiftKey&&document.activeElement===last){e.preventDefault();first?.focus();}}}} id="workspace-navigation" className={`ws-sidebar ${menuOpen ? 'is-open' : ''}`}><a className="ws-brand" href={routeUrl('overview')} onClick={e=>{e.preventDefault();go('overview');}}>PharmaSafe<span>SAFETY WORKSPACE</span></a><p>{profile.title}</p>
      <nav aria-label={`${profile.title} navigation`}>{profile.sections.map(s => <button key={s} aria-current={section === s ? 'page' : undefined} onClick={() => go(s)}>{s === 'cases' ? profile.queue : labels[s]}</button>)}</nav>
      <div className="ws-person"><strong>{user?.username}</strong><span>{user?.role}</span><button onClick={() => { if(confirmLeave()) void logout(); }}>Log Out</button></div></aside>{menuOpen && <button className="ws-drawer-overlay" aria-label="Close navigation" onClick={()=>setMenuOpen(false)}/>}
    <div className="ws-main" inert={menuOpen || undefined}><header className="ws-topbar"><button className="ws-menu-toggle" aria-label="Toggle navigation" aria-expanded={menuOpen} aria-controls="workspace-navigation" onClick={()=>setMenuOpen(v=>!v)}>☰</button><span>{profile.title} / {labels[section]}</span><button disabled={busy} onClick={() => { if(selected) { if(confirmLeave()) void openCase(selected.id, false); } else setRefresh(v => v + 1); }}>Refresh</button></header>
    <main className="ws-content" ref={mainPanel} tabIndex={-1}><div className="ws-heading"><div><p className="ws-eyebrow">{profile.title}</p><h1>{selected ? selected.caseNumber : section === 'overview' ? profile.home : section === 'cases' ? profile.queue : labels[section]}</h1><p>{profile.description}</p></div>{canonicalRole(user?.role || '') === 'ROLE_CASE_PROCESSOR' && section === 'overview' && <button className="ws-primary" onClick={() => go('new')}>Create case</button>}</div>
    {error && <div className="ws-error" role="alert">{error}<button onClick={() => setRefresh(v => v + 1)}>Retry</button></div>}{notice && <div role="status" className="ws-notice">{notice}</div>}{busy && <p role="status">Loading…</p>}
    {selected ? <><button onClick={() => go('cases')}>← Back to {profile.queue}</button>
      {caseEditor ? <CaseEntryForm key={selected.id} initialCase={selected} onOpenCandidate={id=>void openCase(id)} onDirtyChange={setDirty} onCancel={() => go('cases')} onSaveSuccess={c => { setDirty(false); setSelected(c); setRefresh(v => v + 1); setNotice('Case saved.'); }} /> : <CaseRecordView key={selected.id} record={selected} role={user!.role}/>}
      {reviewTargets.length > 0 && <section className="ws-card"><h2>{user?.role === 'ROLE_QUALITY_REVIEWER' ? 'Quality review decision' : 'Workflow decision'}</h2>
        {selected.workflowState === 'Quality Review' && <div className="ws-checks">{['Patient and reporter reviewed', 'Products and events reviewed', 'Medical assessment and narrative reviewed'].map(c => <label key={c}><input type="checkbox" checked={!!checks[c]} onChange={e => setChecks(v => ({ ...v, [c]: e.target.checked }))} />{c}</label>)}</div>}
        <label>Decision comments / return reason<textarea value={comments} onChange={e => {setComments(e.target.value); setDirty(true);}} /></label><div className="ws-actions">{reviewTargets.map(target => <button key={target} disabled={busy || (target === 'Closed' && !closureReady) || (['Data Entry','Medical Review'].includes(target) && !comments.trim()) || (target === 'Approved' && Object.values(checks).filter(Boolean).length !== 3)} onClick={() => transition(target)}>{target === 'Data Entry' || target === 'Medical Review' ? `Return to ${target}` : `Route to ${target}`}</button>)}</div></section>}
      {selected.workflowState === 'Medical Review' && ['ROLE_ADMIN', 'ROLE_MEDICAL_REVIEWER'].includes(user!.role) && <form className="ws-card ws-form" onSubmit={async e => { e.preventDefault(); if (await act(`/api/cases/${selected.id}/assessments`, 'POST', form)) { setForm({}); await openCase(selected.id, false); } }}><h2>Medical assessment</h2><label>Product<select aria-label="Product" required value={form.productName||''} onChange={e=>setForm(f=>({...f,productName:e.target.value}))}><option value="">Select a case product</option>{selected.products.map((p,i)=><option key={p.id||i} value={p.productName}>{p.productName}</option>)}</select></label><label>Event<select aria-label="Event" required value={form.eventVerbatim||''} onChange={e=>setForm(f=>({...f,eventVerbatim:e.target.value}))}><option value="">Select a case event</option>{selected.events.map((ev,i)=><option key={ev.id||i} value={ev.eventVerbatim}>{ev.eventVerbatim}</option>)}</select></label><label>Company causality<select aria-label="Company causality" required value={form.causalityCompany || ''} onChange={e => setForm(f => ({ ...f, causalityCompany: e.target.value }))}><option value="">Select causality</option>{['Possible', 'Probable', 'Certain', 'Unlikely', 'Unassessable'].map(v => <option key={v}>{v}</option>)}</select></label><label>Expectedness<select aria-label="Expectedness" required value={form.expectedness||''} onChange={e=>setForm(f=>({...f,expectedness:e.target.value}))}><option value="">Select expectedness</option><option>Expected</option><option>Unexpected</option><option>Unknown</option></select></label>{input('assessmentComment', 'Assessment comment')}<button disabled={busy}>Save assessment</button></form>}
      {manager && selected.workflowState !== 'Closed' && <form className="ws-card ws-form" onSubmit={async e => { e.preventDefault(); if (await act(`/api/cases/${selected.id}/assign`, 'POST', { userId: Number(form.assignee) })) await openCase(selected.id, false); }}><h2>Case assignment</h2><label>Assign to<select required value={form.assignee || ''} onChange={e => setForm(f => ({ ...f, assignee: e.target.value }))}><option value="">Select a user</option>{assignees.map(a => <option key={text(a.id)} value={text(a.id)}>{text(a.username)} · {text(a.role)}</option>)}</select></label><button disabled={busy}>Assign case</button></form>}
      {manager && selected.workflowState !== 'Closed' && <section className="ws-card"><h2>Case controls</h2><div className="ws-actions"><label>Priority<select disabled={busy || selected.locked} value={selected.priority} onChange={async e => { if (await act(`/api/cases/${selected.id}/priority`, 'PATCH', { priority: e.target.value })) await openCase(selected.id, false); }}>{['Routine', 'Priority', 'Expedited'].map(p => <option key={p}>{p}</option>)}</select></label><button disabled={busy} onClick={async () => { if (await act(`/api/cases/${selected.id}/${selected.locked ? 'unlock' : 'lock'}`, 'POST')) await openCase(selected.id, false); }}>{selected.locked ? 'Unlock case' : 'Lock case'}</button></div></section>}
      <CaseActivities key={selected.id} record={selected} blocked={busy || dirty || Object.values(form).some(Boolean) || Object.values(checks).some(Boolean)} assignees={assignees} onReload={()=>void openCase(selected.id,false)} onDirtyChange={setActivityDirty} onReadiness={setClosureReady}/>
      <section className="ws-card"><h2>Case audit history</h2><div className="ws-timeline">{history.map(row=><div key={text(row.id)}><strong>{text(row.eventType).replaceAll('_',' ')}</strong><span>{text(row.username)} · {new Date(String(row.createdAt)).toLocaleString()}</span><p>{text(row.details)}</p></div>)}{!history.length&&<p>No case history recorded.</p>}</div></section></>
    : section === 'new' ? <CaseEntryForm onOpenCandidate={id=>void openCase(id)} onDirtyChange={setDirty} onCancel={() => go('cases')} onSaveSuccess={c => { go('cases',true); setSelected(c); const url=routeUrl('cases',c.id); window.history.replaceState(null,'',url); lastRoute.current=url; setRefresh(v => v + 1); }} />
    : section === 'overview' ? <WorkspaceHome role={user!.role} data={overview} cases={cases.content} go={go} open={openCase}/>
    : section === 'cases' ? <>
      <section className="ws-card"><div className="ws-heading"><h2>{profile.queue}</h2><form className="ws-search" onSubmit={e => { e.preventDefault(); setPage(0); setSearch(query); }}><label className="ws-sr" htmlFor="case-query">Search case number</label><input id="case-query" placeholder="Search case number" value={query} onChange={e => setQuery(e.target.value)} /><button>Search</button></form></div>{user?.role === 'ROLE_SAFETY_MANAGER' ? <WorkflowBoard cases={cases.content} open={openCase}/> : <div className="ws-table-wrap"><table><thead><tr>{['Case', 'Product', 'Event', 'State', 'Priority', 'Assignee', 'Action'].map(x => <th key={x}>{x}</th>)}</tr></thead><tbody>{cases.content.map(c => <tr key={c.id}><td>{c.caseNumber}</td><td>{c.primaryProduct}</td><td>{c.primaryEvent}</td><td><span className="ws-badge">{c.workflowState}</span></td><td>{c.priority}</td><td>{c.assignedToUsername || 'Unassigned'}</td><td><button disabled={busy} onClick={() => openCase(c.id)}>Open case</button></td></tr>)}</tbody></table></div>}{!busy && !cases.content.length && <p>No cases match this queue.</p>}<div className="ws-actions"><button disabled={page === 0 || busy} onClick={() => setPage(p => p - 1)}>Previous</button><span>Page {page + 1} · {cases.totalElements} cases</span><button disabled={page + 1 >= cases.totalPages || busy} onClick={() => setPage(p => p + 1)}>Next</button></div></section></>
    : <>
      {section === 'users' && <form className="ws-card ws-form" onSubmit={async e => { e.preventDefault(); if (await act('/api/admin/users', 'POST', { ...form, role: form.role || 'ROLE_CASE_PROCESSOR' })) setForm({}); }}><h2>Create user</h2>{input('username', 'Username')}{input('fullName', 'Full name')}{input('password', 'Initial password', 'password')}<label>Workspace role<select value={form.role || 'ROLE_CASE_PROCESSOR'} onChange={e => setForm(f => ({ ...f, role: e.target.value }))}>{Object.entries(profiles).map(([role, p]) => <option key={role} value={role}>{p.title}</option>)}</select></label><button disabled={busy}>Create user</button></form>}
      {section === 'reports' && reportWriter && <form className="ws-card ws-form" onSubmit={async e => { e.preventDefault(); if (await act('/api/reports', 'POST', { ...form, reportType: form.reportType || '15-day', relatedCaseNumbers: (form.caseNumbers || '').split(',').map(c => c.trim()).filter(Boolean) })) setForm({}); }}><h2>Prepare report record</h2><p>Internal report tracking. This action does not transmit a report to a regulator.</p>{input('product', 'Product')}{input('authority', 'Authority')}{input('dueDate', 'Due date', 'date')}{input('caseNumbers', 'Related case numbers (comma separated)')}<label>Report type<select value={form.reportType || '15-day'} onChange={e => setForm(f => ({ ...f, reportType: e.target.value }))}>{['7-day', '15-day', 'PSUR', 'DSUR', 'PBRER', 'PADER'].map(v => <option key={v}>{v}</option>)}</select></label>{input('summary', 'Summary')}<button disabled={busy}>Create report record</button></form>}
      {(section === 'contacts' || section === 'tasks') && !readOnly && <form className="ws-card ws-form" onSubmit={async e => { e.preventDefault(); const path = `/api/cases/${Number(form.caseId)}/${section === 'contacts' ? 'contact-logs' : 'action-items'}`; const body = section === 'contacts' ? { contactAt: new Date().toISOString(), contactType: 'Email', description: form.description } : { description: form.description, assignedUserId: Number(form.assignedUserId || user?.id), dueAt: new Date(form.dueAt).toISOString() }; if (await act(path, 'POST', body)) setForm({}); }}><h2>{section === 'contacts' ? 'Record contact' : 'Create action item'}</h2><label>Case<select required value={form.caseId||''} onChange={e=>setForm(f=>({...f,caseId:e.target.value}))}><option value="">Select an accessible case</option>{cases.content.map(c=><option key={c.id} value={c.id}>{c.caseNumber} · {c.workflowState}</option>)}</select><small>Showing up to 50 accessible cases.</small></label>{input('description', 'Description')}{section === 'tasks' && <>{input('dueAt', 'Due date and time', 'datetime-local')}{manager && <label>Assignee<select required value={form.assignedUserId || ''} onChange={e => setForm(f => ({ ...f, assignedUserId: e.target.value }))}><option value="">Select user</option>{assignees.map(a => <option key={text(a.id)} value={text(a.id)}>{text(a.username)}</option>)}</select></label>}</>}<button disabled={busy}>Save</button></form>}
      <section className="ws-card"><div className="ws-section-heading"><h2>{{users:'User directory',audit:'Recorded activity',reports:'Report register',contacts:'Contact history',tasks:'Task worklist'}[section as 'users'|'audit'|'reports'|'contacts'|'tasks'] || labels[section]}</h2>{['users','audit'].includes(section)&&<form className="ws-search" onSubmit={e=>{e.preventDefault();setPage(0);setSearch(query);}}><input aria-label={section==='users'?'Search users':'Search audit by actor or event'} placeholder={section==='users'?'Username or full name':'Actor or event'} value={query} onChange={e=>setQuery(e.target.value)}/><button>Search</button></form>}</div>{section === 'tasks' && <label className="ws-checks"><span><input style={{width:'auto', marginRight:8}} type="checkbox" checked={overdue} onChange={e => { setOverdue(e.target.checked); setPage(0); }} />Overdue only</span></label>}{!busy && !rows.length && <p>No records available.</p>}{<RecordTable section={section} rows={rows} actions={['users','tasks'].includes(section) || (section==='reports' && reportWriter) ? row=><div className="ws-actions">
        {section === 'users' && <><label>Workspace role<select aria-label={`Role for ${text(row.username)}`} value={text(row.role)} disabled={busy || row.username === user?.username} onChange={e => act(`/api/admin/users/${row.id}`, 'PUT', { role: e.target.value })}>{!profiles[text(row.role)] && <option value={text(row.role)}>{text(row.role)}</option>}{Object.entries(profiles).map(([role,p]) => <option key={role} value={role}>{p.title}</option>)}</select></label><button disabled={busy || row.username === user?.username} onClick={() => act(`/api/admin/users/${row.id}/toggle-status`, 'PATCH')}>{row.enabled ? 'Deactivate' : 'Activate'}</button></>}
        {section === 'tasks' && (row.assignedUsername === user?.username || user?.role === 'ROLE_ADMIN') && !['COMPLETED', 'CANCELLED'].includes(text(row.status)) && <button disabled={busy} onClick={() => row.actionCode === 'FOLLOW_UP' ? void openCase(Number(row.caseId)) : act(`/api/action-items/${row.id}/complete`, 'POST')}>{row.actionCode === 'FOLLOW_UP' ? 'Record follow-up' : 'Complete action'}</button>}
        {section === 'tasks' && manager && !['COMPLETED', 'CANCELLED'].includes(text(row.status)) && <label>Reassign action<select value={String(assignees.find(a => a.username === row.assignedUsername)?.id || '')} disabled={busy} onChange={e => act(`/api/action-items/${row.id}/assignment?assignedUserId=${Number(e.target.value)}`, 'PATCH')}><option value="">Select assignee</option>{assignees.map(a => <option key={text(a.id)} value={text(a.id)}>{text(a.username)}</option>)}</select></label>}
        {section === 'reports' && reportWriter && row.status !== 'Submitted' && <button disabled={busy} onClick={() => { if (window.confirm('Record this report as submitted? This does not transmit it externally.')) act(`/api/reports/${row.id}/submit`, 'POST'); }}>Record submission</button>}
      </div> : undefined}/> }{['reports', 'tasks', 'contacts', 'users', 'audit'].includes(section) && <div className="ws-actions"><button disabled={page === 0 || busy} onClick={() => setPage(p => p - 1)}>Previous</button><span>Page {page + 1}</span><button disabled={(page + 1) * 20 >= totalRows || busy} onClick={() => setPage(p => p + 1)}>Next</button></div>}</section>
    </>}
    </main></div></div>;
}
