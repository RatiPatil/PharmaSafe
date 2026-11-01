import { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { apiRequest } from '../config/api';
import { useAuth } from '../context/AuthContext';
import type { SafetyCaseDetails } from '../types/case';
import type { Page, Row } from './model';
import { text } from './model';

type Receipt = { id:number; followUpNumber:number; kind:string; receivedDate:string; safetyReceivedDate?:string; significant:boolean; summary:string; justification:string; sourceReference?:string; previousCaseVersion:number; caseVersion:number; createdByUsername:string; createdAt:string };
type Readiness = { canClose:boolean; openActionItems:number; pendingExpeditedReports:number; blockers:string[] };
type Mode = 'receipt'|'reopen'|'contact'|'task';
function Dialog({title,children,onClose,busy}:{title:string;children:React.ReactNode;onClose:()=>void;busy:boolean}) {
 const panel=useRef<HTMLDivElement>(null);
 useEffect(()=>{
  const previous=document.activeElement as HTMLElement|null; const root=document.getElementById('root');const wasInert=root?.inert;
  if(root)root.inert=true;
  panel.current?.querySelector<HTMLElement>('input,select,textarea,button')?.focus();
  return ()=>{if(root)root.inert=!!wasInert;previous?.focus();};
 },[]);
 return createPortal(<div className="ws-modal-backdrop"><div ref={panel} className="ws-modal ws-card" role="dialog" aria-modal="true" aria-labelledby="activity-dialog-title" onKeyDown={e=>{
  if(e.key==='Escape'&&!busy){e.stopPropagation();onClose();}
  if(e.key==='Tab'){const items=panel.current?.querySelectorAll<HTMLElement>('input:not([disabled]),select:not([disabled]),textarea:not([disabled]),button:not([disabled])');if(!items?.length)return;const first=items[0],last=items[items.length-1];if(e.shiftKey&&document.activeElement===first){e.preventDefault();last.focus();}else if(!e.shiftKey&&document.activeElement===last){e.preventDefault();first.focus();}}
 }}><div className="ws-section-heading"><h2 id="activity-dialog-title">{title}</h2><button type="button" disabled={busy} onClick={onClose}>Cancel</button></div>{children}</div></div>,document.body);
}
export function CaseActivities({record,blocked,assignees,onReload,onDirtyChange,onReadiness}:{record:SafetyCaseDetails;blocked:boolean;assignees:Row[];onReload:()=>void;onDirtyChange:(dirty:boolean)=>void;onReadiness:(ready:boolean)=>void}) {
 const {user}=useAuth();const manager=['ROLE_ADMIN','ROLE_SAFETY_MANAGER'].includes(user?.role||'');
 const canRecord=['ROLE_ADMIN','ROLE_SAFETY_MANAGER','ROLE_CASE_PROCESSOR','ROLE_USER'].includes(user?.role||'')&&!record.locked&&['Draft','Data Entry'].includes(record.workflowState);
 const operational=['ROLE_ADMIN','ROLE_SAFETY_MANAGER','ROLE_CASE_PROCESSOR','ROLE_USER','ROLE_MEDICAL_REVIEWER','ROLE_QUALITY_REVIEWER'].includes(user?.role||'');
 const [tab,setTab]=useState<'receipts'|'contacts'|'tasks'>('receipts');const [receipts,setReceipts]=useState<Page<Receipt>>({content:[],totalElements:0,totalPages:0});
 const [rows,setRows]=useState<Row[]>([]);const [total,setTotal]=useState(0);const [page,setPage]=useState(0);const [ready,setReady]=useState<Readiness|null>(null);
 const [mode,setMode]=useState<Mode|null>(null);const [form,setForm]=useState<Record<string,string>>({});const [significant,setSignificant]=useState(false);
 const [error,setError]=useState('');const [loading,setLoading]=useState(false);const [saving,setSaving]=useState(false);const [refresh,setRefresh]=useState(0);const pending=useRef(false);
 const [followTasks,setFollowTasks]=useState<Row[]>([]);
 useEffect(()=>{onDirtyChange(mode!==null);return ()=>onDirtyChange(false);},[mode,onDirtyChange]);
 useEffect(()=>{
  const controller=new AbortController();setLoading(true);setError('');onReadiness(false);
  const load=async()=>{
   const readiness=await apiRequest<Readiness>(`/api/cases/${record.id}/closure-readiness`,'GET',undefined,controller.signal);
   if(!controller.signal.aborted){setReady(readiness);onReadiness(readiness.canClose);}
   if(tab==='receipts'){
    const data=await apiRequest<Page<Receipt>>(`/api/cases/${record.id}/follow-ups?page=${page}`,'GET',undefined,controller.signal);
    if(!controller.signal.aborted)setReceipts(data);
   }else{
    const data=await apiRequest<Page<Row>>(`/api/${tab==='tasks'?'action-items':'contact-logs'}?caseId=${record.id}&page=${page}`,'GET',undefined,controller.signal);
    if(!controller.signal.aborted){setRows(data.content);setTotal(data.totalElements);}
   }
  };
  load().catch(e=>{if(!controller.signal.aborted)setError(e.message);}).finally(()=>{if(!controller.signal.aborted)setLoading(false);});return ()=>controller.abort();
 },[record.id,record.rowVersion,tab,page,refresh,onReadiness]);
 useEffect(()=>{
  if(mode!=='receipt')return;const controller=new AbortController();
  apiRequest<Page<Row>>(`/api/action-items?caseId=${record.id}&size=50`,'GET',undefined,controller.signal).then(data=>{if(!controller.signal.aborted)setFollowTasks(data.content.filter(t=>t.actionCode==='FOLLOW_UP'&&!['COMPLETED','CANCELLED'].includes(text(t.status))));}).catch(e=>{if(!controller.signal.aborted)setError(e.message);});return ()=>controller.abort();
 },[mode,record.id]);
 const open=(next:Mode)=>{if(blocked)return;setError('');setForm({kind:'FOLLOW_UP',receivedDate:new Date().toISOString().slice(0,10),contactType:'Email'});setSignificant(false);setMode(next);};
 const close=()=>{if(!pending.current){setMode(null);setForm({});setError('');}};
 const field=(name:string,label:string,type='text',required=true)=><label>{label}<input required={required} type={type} maxLength={name==='sourceReference'?255:undefined} value={form[name]||''} onChange={e=>setForm(f=>({...f,[name]:e.target.value}))}/></label>;
 const area=(name:string,label:string)=><label className="ws-full">{label}<textarea required maxLength={10000} value={form[name]||''} onChange={e=>setForm(f=>({...f,[name]:e.target.value}))}/></label>;
 async function save(e:React.FormEvent){
  e.preventDefault();if(pending.current||!mode)return;pending.current=true;setSaving(true);setError('');
  try{
   const base=`/api/cases/${record.id}`;
   if(mode==='receipt')await apiRequest(`${base}/follow-ups`,'POST',{expectedRowVersion:record.rowVersion,kind:form.kind,receivedDate:form.receivedDate,safetyReceivedDate:form.kind==='AMENDMENT'?null:form.safetyReceivedDate||null,significant,justification:form.justification,summary:form.summary,sourceReference:form.sourceReference||null,relatedActionItemId:form.kind==='AMENDMENT'?null:Number(form.relatedActionItemId)||null});
   if(mode==='reopen')await apiRequest(`${base}/reopen`,'POST',{expectedRowVersion:record.rowVersion,reason:form.reason,assignedUserId:Number(form.assignedUserId),dueAt:new Date(form.dueAt).toISOString(),password:form.password});
   if(mode==='contact')await apiRequest(`${base}/contact-logs`,'POST',{contactType:form.contactType,description:form.description,contactAt:new Date(form.contactAt).toISOString()});
   if(mode==='task')await apiRequest(`${base}/action-items`,'POST',{description:form.description,assignedUserId:Number(form.assignedUserId||user?.id),dueAt:new Date(form.dueAt).toISOString()});
   const reload=mode==='receipt'||mode==='reopen';setMode(null);setForm({});setRefresh(v=>v+1);onDirtyChange(false);if(reload)onReload();
  }catch(e){setError(e instanceof Error?e.message:'Could not save. Your entries have been kept.');}finally{pending.current=false;setSaving(false);}
 }
 const count=tab==='receipts'?receipts.totalElements:total;
 return <section className="ws-card ws-case-activities" aria-label="Case activities">
  <div className="ws-section-heading"><div><h2>Case activities</h2><p>Receipts, correspondence and outstanding work for this case.</p></div>{manager&&['Approved','Locked','Closed'].includes(record.workflowState)&&<button disabled={blocked||loading} onClick={()=>open('reopen')}>Reopen for follow-up</button>}</div>
  {ready&&<div className="ws-closure-summary"><strong>{record.workflowState==='Closed'?'Case closed':ready.canClose?'Ready for formal closure':'Closure checklist'}</strong><span>{ready.openActionItems} open actions · {ready.pendingExpeditedReports} pending expedited report records</span>{record.workflowState!=='Closed'&&ready.blockers.map(b=><p key={b}>{b}</p>)}<small>Checks recorded tasks and scheduled 7-day / 15-day reports. Regulatory obligations still require human review.</small></div>}
  <div className="ws-actions" role="group" aria-label="Activity views">{(['receipts','contacts','tasks'] as const).map(t=><button key={t} aria-pressed={tab===t} onClick={()=>{setTab(t);setPage(0);setRows([]);}}>{t==='receipts'?'Follow-ups & amendments':t==='contacts'?'Contact history':'Action items'}</button>)}</div>
  <div className="ws-actions ws-activity-actions">{tab==='receipts'&&canRecord&&<button disabled={blocked||loading} onClick={()=>open('receipt')}>Record received information</button>}{tab==='contacts'&&operational&&<button disabled={blocked||loading} onClick={()=>open('contact')}>Record case contact</button>}{tab==='tasks'&&operational&&record.workflowState!=='Closed'&&<button disabled={blocked||loading} onClick={()=>open('task')}>Add case action</button>}{blocked&&<small>Save or discard other case edits before adding activities.</small>}</div>
  {error&&!mode&&<div role="alert" className="ws-error">{error}<button onClick={()=>setRefresh(v=>v+1)}>Retry activities</button></div>}
  {loading?<p role="status">Loading activities…</p>:tab==='receipts'?<div className="ws-timeline">{receipts.content.map(f=><article key={f.id}><strong>#{f.followUpNumber} · {f.kind==='AMENDMENT'?'Amendment':'Follow-up'}{f.significant?' · Significant':''}</strong><span>Received {f.receivedDate}{f.safetyReceivedDate?` · Safety received ${f.safetyReceivedDate}`:''} · Version {f.previousCaseVersion} → {f.caseVersion}</span><p>{f.summary}</p><p><b>Justification:</b> {f.justification}</p>{f.sourceReference&&<p>Reference: {f.sourceReference}</p>}<small>{f.createdByUsername} · {new Date(f.createdAt).toLocaleString()}</small></article>)}{!receipts.content.length&&<p>No follow-up receipts or amendments recorded.</p>}</div>:<div className="ws-table-wrap"><table><thead><tr><th>{tab==='tasks'?'Due':'Contact date'}</th><th>Description</th><th>{tab==='tasks'?'Status / assignee':'Type / author'}</th></tr></thead><tbody>{rows.map(row=><tr key={text(row.id)}><td>{new Date(String(tab==='tasks'?row.dueAt:row.contactAt)).toLocaleString()}</td><td>{text(row.description)}</td><td>{tab==='tasks'?`${text(row.status)} · ${text(row.assignedUsername)}`:`${text(row.contactType)} · ${text(row.createdByUsername)}`}</td></tr>)}</tbody></table>{!rows.length&&<p>No records in this view.</p>}</div>}
  <div className="ws-actions"><button disabled={loading||page===0} onClick={()=>setPage(p=>p-1)}>Previous activities</button><span>Page {page+1} · {count} records</span><button disabled={loading||(page+1)*20>=count} onClick={()=>setPage(p=>p+1)}>Next activities</button></div>
  {mode&&<Dialog title={{receipt:'Record received information',reopen:'Reopen case for follow-up',contact:'Record case contact',task:'Add case action'}[mode]} busy={saving} onClose={close}><form className="ws-form" onSubmit={save}>
   {error&&<div role="alert" className="ws-error ws-full">{error}</div>}
   {mode==='receipt'&&<><p className="ws-full">Adds an immutable receipt to this case and advances its version. Amendments preserve the receipt clock.</p><label>Information type<select value={form.kind} onChange={e=>setForm(f=>({...f,kind:e.target.value,safetyReceivedDate:'',relatedActionItemId:''}))}><option value="FOLLOW_UP">Follow-up</option><option value="AMENDMENT">Amendment</option></select></label>{field('receivedDate','Received date','date')}{form.kind!=='AMENDMENT'&&field('safetyReceivedDate','Safety department received date','date',false)}<label className="ws-inline-check"><input type="checkbox" checked={significant} onChange={e=>setSignificant(e.target.checked)}/>Significant information</label>{form.kind!=='AMENDMENT'&&<label>Complete a follow-up request (optional)<select value={form.relatedActionItemId||''} onChange={e=>setForm(f=>({...f,relatedActionItemId:e.target.value}))}><option value="">No linked request</option>{followTasks.map(t=><option key={text(t.id)} value={text(t.id)}>{text(t.description)}</option>)}</select><small>Up to 50 case actions are shown.</small></label>}{field('sourceReference','Source reference','text',false)}{area('summary','Information received')}{area('justification','Significance / amendment justification')}</>}
   {mode==='reopen'&&<><p className="ws-full">Keeps the original case number and history, returns the case to Data Entry and creates a follow-up action.</p><label>Case processor<select required value={form.assignedUserId||''} onChange={e=>setForm(f=>({...f,assignedUserId:e.target.value}))}><option value="">Select processor</option>{assignees.filter(a=>['ROLE_ADMIN','ROLE_CASE_PROCESSOR','ROLE_USER'].includes(text(a.role))).map(a=><option key={text(a.id)} value={text(a.id)}>{text(a.username)}</option>)}</select></label>{field('dueAt','Follow-up due','datetime-local')}{area('reason','Reason for reopening')}{field('password','Confirm your password','password')}</>}
   {mode==='contact'&&<>{field('contactAt','Contact date and time','datetime-local')}<label>Contact type<select value={form.contactType} onChange={e=>setForm(f=>({...f,contactType:e.target.value}))}>{['Telephone','Email','Portal','Letter','Other'].map(t=><option key={t}>{t}</option>)}</select></label>{area('description','Contact details')}</>}
   {mode==='task'&&<>{field('dueAt','Due date and time','datetime-local')}{manager&&<label>Assignee<select required value={form.assignedUserId||''} onChange={e=>setForm(f=>({...f,assignedUserId:e.target.value}))}><option value="">Select user</option>{assignees.map(a=><option key={text(a.id)} value={text(a.id)}>{text(a.username)}</option>)}</select></label>}{area('description','Action required')}</>}
   <div className="ws-actions ws-full"><button className="ws-primary" disabled={saving}>{saving?'Saving…':'Save activity'}</button></div>
  </form></Dialog>}
 </section>;
}
