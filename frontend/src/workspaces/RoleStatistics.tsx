import { Activity, Globe2, Layers3, ShieldCheck, Users, CalendarDays, ClipboardCheck } from 'lucide-react';
import type { Overview } from './model';
import { canonicalRole, roleName } from './model';
const colours=['#5485ed','#9568e9','#28a995','#e9ab46','#dd7299','#6375bd'];
function Distribution({title,values,description}:{title:string;values?:Record<string,number>;description:string}){
 const entries=Object.entries(values||{}).sort((a,b)=>b[1]-a[1]),max=Math.max(1,...entries.map(([,n])=>n));
 return <section className="ws-card role-stat-chart"><div className="role-stat-title"><h2>{title}</h2><Layers3 size={17}/></div><p>{description}</p>{entries.some(([,n])=>n>0)?<div className="role-stat-bars" role="img" aria-label={`${title}: ${entries.map(([key,n])=>`${key} ${n}`).join(', ')}`}>{entries.slice(0,6).map(([key,n],i)=><div key={key}><span title={key}>{key.startsWith('ROLE_')?roleName(key):key}</span><i><b style={{width:`${n/max*100}%`,background:colours[i%colours.length]}}/></i><strong>{n}</strong></div>)}</div>:<div className="role-stat-empty">No records in this category yet.</div>}{entries.length>6&&<small>Top six categories by count.</small>}</section>;
}
export function RoleStatistics({role,data}:{role:string;data:Overview}){
 const r=canonicalRole(role),admin=r==='ROLE_ADMIN',manager=r==='ROLE_SAFETY_MANAGER',reporting=r==='ROLE_REPORTING_SPECIALIST',auditor=r==='ROLE_AUDITOR',clinical=r==='ROLE_MEDICAL_REVIEWER',quality=r==='ROLE_QUALITY_REVIEWER';
 const tiles=admin?[
 ['Pending approvals',data.accountStatusDistribution?.PENDING,Users],['Pending reset requests',data.pendingResetRequests,ShieldCheck],['Locked accounts',data.lockedAccounts,ShieldCheck],['Unassigned cases',data.unassignedCases,ClipboardCheck]
 ]:reporting?[
 ['Submitted reports',data.submittedReports,ClipboardCheck],['Pending report records',data.pendingReports,Layers3],['Fatal cases in scope',data.fatalCases,Activity],['Locked cases in scope',data.lockedCases,ShieldCheck]
 ]:auditor?[
 ['Fatal case records',data.fatalCases,Activity],['Cases with assessments',data.assessedCases,ClipboardCheck],['Cases with narratives',data.narrativeCases,Layers3],['Locked case records',data.lockedCases,ShieldCheck]
 ]:clinical||quality?[
 ['Fatal cases in review',data.fatalCases,Activity],['Cases with assessments',data.assessedCases,ClipboardCheck],['Cases with narratives',data.narrativeCases,Layers3],['Latest receipt in 7 days',data.receivedLast7Days,CalendarDays]
 ]:manager?[
 ['Unassigned cases',data.unassignedCases,Users],['Cases with assessments',data.assessedCases,ClipboardCheck],['Latest receipt in 7 days',data.receivedLast7Days,CalendarDays],['No receipt update in 14+ days',data.staleOpenCases,CalendarDays]
 ]:[
 ['Latest receipt in 7 days',data.receivedLast7Days,CalendarDays],['Cases with narratives',data.narrativeCases,Layers3],['Cases with assessments',data.assessedCases,ClipboardCheck],['No receipt update in 14+ days',data.staleOpenCases,CalendarDays]
 ];
 const receipts=Object.entries(data.receiptActivity||{}),peak=Math.max(1,...receipts.map(([,n])=>n));
 return <section className="role-statistics" aria-label="Role dashboard statistics"><div className="role-stat-section-heading"><div><p className="premium-kicker">YOUR ROLE IN FOCUS</p><h2>{admin?'Access & operational statistics':reporting?'Reporting dashboard':clinical?'Medical review dashboard':quality?'Quality review dashboard':manager?'Team operations dashboard':auditor?'Inspection dashboard':'Case processing dashboard'}</h2></div><span><Globe2 size={14}/> Your permitted records</span></div>
 <div className="role-stat-tiles">{tiles.map(([label,value,Icon],i)=>{const Symbol=Icon as typeof Activity;return <article key={String(label)}><span style={{color:colours[i]}}><Symbol size={19}/></span><div><strong>{typeof value==='number'?value.toLocaleString():'—'}</strong><p>{String(label)}</p></div></article>;})}</div>
 <div className="role-stat-grid"><Distribution title={admin?'Accounts by approval status':'Case priorities'} values={admin?data.accountStatusDistribution:data.priorityDistribution} description={admin?'Approval state of all registered accounts.':'Routine, priority and expedited cases in your scope.'}/><Distribution title={admin?'Accounts by role':'Countries in your worklist'} values={admin?data.roleDistribution:data.countryDistribution} description={admin?'Assigned account roles, including legacy aliases.':'Country codes from the cases you may access.'}/><Distribution title="Case report types" values={data.reportTypeDistribution} description="Report type breakdown across your accessible cases."/>
 <section className="ws-card role-stat-chart"><div className="role-stat-title"><h2>Latest receipt activity</h2><CalendarDays size={17}/></div><p>Cases by latest receipt date, over the last 14 calendar dates (UTC). This includes follow-up receipt dates.</p>{receipts.length?<><div className="role-receipt-chart" role="img" aria-label={`Latest receipt activity: ${receipts.map(([date,n])=>`${date}: ${n}`).join(', ')}`}>{receipts.map(([date,n],i)=><div key={date} title={`${date}: ${n} cases`}><strong>{n>0?n:''}</strong><i style={{height:`${n/peak*100}%`,background:colours[i%colours.length]}}/><span>{date.slice(8)}</span></div>)}</div><div className="role-receipt-range"><span>{receipts[0][0]}</span><span>{receipts.at(-1)?.[0]}</span></div></>:<div className="role-stat-empty">Receipt activity is unavailable.</div>}</section></div>
 <p className="role-stat-footnote">Assessment and narrative indicators show whether a record exists; they do not certify clinical completeness. No receipt update in 14+ days excludes closed or submitted cases.</p>
 </section>;
}
