import { useMemo, useState } from 'react';
import { Activity, ArrowUpRight, BarChart3, CircleHelp, ShieldCheck, Sparkles } from 'lucide-react';
import type { Overview, Section } from './model';
import { canonicalRole, profiles } from './model';
export const stateColours:Record<string,string>={Draft:'#8b9bb4','Data Entry':'#5485ed','Medical Review':'#9568e9','Quality Review':'#e9ab46',Approved:'#28a995',Locked:'#dd7299',Closed:'#6375bd','Other states':'#a0aebc'};
export function workflowValues(data:Overview){
 const values=Object.entries(data.states||{}).map(([label,value])=>({label,value:Math.max(0,Number(value)||0),colour:stateColours[label]||'#849ab0'}));
 const other=Math.max(0,data.totalCases-values.reduce((n,v)=>n+v.value,0));
 if(other)values.push({label:'Other states',value:other,colour:stateColours['Other states']});
 return values;
}
export function DashboardHero({role,go}:{role:string;go:(section:Section)=>void}){
 const r=canonicalRole(role),title=profiles[r]?.title||'Safety workspace';
 return <section className="premium-welcome"><div><p className="premium-kicker"><Sparkles size={14}/> YOUR SAFETY WORKSPACE</p><h2>A clearer view.<br/><span>Better-informed decisions.</span></h2><p>Keep your team’s safety work in focus with a connected view of cases, reviews and recorded activity.</p><div className="premium-welcome-actions"><button onClick={()=>go('cases')}>Explore your case worklist <ArrowUpRight size={16}/></button><span><ShieldCheck size={14}/>{title}</span></div></div><div className="premium-welcome-photo"><img src="/images/clinical-team.webp" alt="Clinician using digital tools to support patient care" width="1200" height="800"/><span><Activity size={17}/>Connected safety, from every angle.</span></div></section>;
}
export function DashboardAnalytics({data}: {data:Overview}){
 const items=useMemo(()=>workflowValues(data),[data]);
 const [selected,setSelected]=useState<string|null>(null);
 const total=items.reduce((n,v)=>n+v.value,0),active=items.find(v=>v.label===selected);
 let offset=0;const circumference=2*Math.PI*70;
 const risk=[{label:'Serious cases',value:data.seriousCases,colour:'#e67783'},{label:'Expedited priority',value:data.expeditedCases,colour:'#e9ab46'}];
 return <div className="premium-analytics" aria-label="Case analytics">
 <section className="ws-card premium-chart"><div className="premium-chart-heading"><div><p className="premium-kicker">CASE PORTFOLIO</p><h2>Workflow distribution</h2></div><span className="premium-chart-icon"><BarChart3 size={18}/></span></div><p className="premium-chart-caption">Current stages across cases you can access.</p>
 <div className="premium-donut-layout"><div className="premium-donut"><svg viewBox="0 0 180 180" role="img" aria-label={`Workflow distribution: ${items.map(v=>`${v.label} ${v.value}`).join(', ')}`}><circle cx="90" cy="90" r="70" fill="none" stroke="#edf1f7" strokeWidth="20"/>{items.filter(v=>v.value>0).map(v=>{const length=total?v.value/total*circumference:0;const start=offset;offset+=length;return <circle key={v.label} className="premium-donut-segment" cx="90" cy="90" r="70" fill="none" stroke={v.colour} strokeWidth={selected===v.label?24:20} strokeDasharray={`${length} ${circumference-length}`} strokeDashoffset={-start} transform="rotate(-90 90 90)" opacity={selected&&selected!==v.label ? .45 : 1}><title>{v.label}: {v.value}</title></circle>;})}</svg><div className="premium-donut-centre"><strong>{active?.value??data.totalCases}</strong><span>{active?.label||'Accessible cases'}</span></div></div>
 <div className="premium-chart-legend">{items.map(v=><button key={v.label} aria-label={`Highlight ${v.label}: ${v.value} cases`} aria-pressed={selected===v.label} onClick={()=>setSelected(selected===v.label?null:v.label)}><span style={{background:v.colour}}/><span>{v.label}</span><strong>{v.value}</strong></button>)}</div></div>
 <p className="premium-chart-note"><CircleHelp size={13}/>{total?'Select a stage to highlight its count.':'No cases in your current access scope.'}</p></section>
 <section className="ws-card premium-chart"><div className="premium-chart-heading"><div><p className="premium-kicker">PROCESSING SNAPSHOT</p><h2>Cases by workflow stage</h2></div><span className="premium-chart-icon is-purple"><Activity size={18}/></span></div><p className="premium-chart-caption">Counts from the current dashboard response.</p><div className="premium-bars" role="img" aria-label={`Cases by workflow stage: ${items.map(v=>`${v.label} ${v.value}`).join(', ')}`}>{items.map(v=><div key={v.label} className={selected===v.label?'is-highlighted':''}><span>{v.label}</span><div className="premium-bar-track"><i style={{width:`${v.value/Math.max(1,...items.map(i=>i.value))*100}%`,background:v.colour}}/></div><strong>{v.value}</strong></div>)}</div><div className="premium-risk">{risk.map(v=><div key={v.label}><span><i style={{background:v.colour}}/>{v.label}</span><strong>{v.value??'—'}<small> / {data.totalCases}</small></strong></div>)}</div><p className="premium-chart-note">Seriousness and expedited priority may overlap.</p></section>
 </div>;
}
