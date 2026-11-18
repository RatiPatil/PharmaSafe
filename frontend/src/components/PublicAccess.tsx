import { useEffect, useRef, useState } from 'react';
import { ShieldCheck, ClipboardList, Stethoscope, BadgeCheck, Users, FileCheck, Search, ArrowRight, CheckCircle2, LockKeyhole } from 'lucide-react';
import { apiRequest } from '../config/api';
import { LoginPage } from './LoginPage';
import './PublicAccess.css';
import publicRoleCatalog from '../config/publicRoleCatalog.json';
import { registrationOptions } from '../config/registrationOptions';
type TeamInvitation = {teamCode:string;name:string;leadName:string;remainingSeats:number;registrationKey?:string};
type Role = {role:string; name:string; fields:string[]};
const descriptions:Record<string,string>={ROLE_PV_ANALYST:'Analyse reviewed safety cases, explore trends and prepare safety analysis reports.',ROLE_CASE_PROCESSOR:'Capture and process assigned safety cases.',ROLE_MEDICAL_REVIEWER:'Review clinical evidence, causality and expectedness.',ROLE_QUALITY_REVIEWER:'Check completeness and review data quality.',ROLE_SAFETY_MANAGER:'Monitor workflows, assignments and safety operations.',ROLE_REPORTING_SPECIALIST:'Prepare safety reports and track reporting deadlines.',ROLE_AUDITOR:'Inspect permitted records and audit history with read-only access.'};
const icons={ROLE_PV_ANALYST:Search,ROLE_CASE_PROCESSOR:ClipboardList,ROLE_MEDICAL_REVIEWER:Stethoscope,ROLE_QUALITY_REVIEWER:BadgeCheck,ROLE_SAFETY_MANAGER:Users,ROLE_REPORTING_SPECIALIST:FileCheck,ROLE_AUDITOR:Search};
export const fieldLabel=(value:string)=>value.replace(/([A-Z])/g,' $1').replace(/^./,s=>s.toUpperCase());
export function PublicAccess(){
 const [route,setRoute]=useState(window.location.hash);
 const portalMatch=route.split('?')[0].match(/^#\/access\/(ROLE_[A-Z_]+)\/(user|admin|team-lead|register)$/);
 const requiresCatalog=route.startsWith('#/register')||!!portalMatch;
 const [roles,setRoles]=useState<Role[]>(publicRoleCatalog);
 const portalRole=roles.find(r=>r.role===portalMatch?.[1])||publicRoleCatalog.find(r=>r.role===portalMatch?.[1]);
 const portalTab=portalMatch?.[2];
 const portalBase=portalRole?`#/access/${portalRole.role}`:'';
 const teamKey=new URLSearchParams(route.split('?')[1]||'').get('team');
 const teamPortal=['ROLE_CASE_PROCESSOR','ROLE_MEDICAL_REVIEWER'].includes(portalRole?.role||'');
 const medicalPortal=portalRole?.role==='ROLE_MEDICAL_REVIEWER';
 const teamApi=medicalPortal?'/api/medical-teams':'/api/processing-teams';
 const teamPrefix=medicalPortal?'MR':'CP';
 const teamDepartment=medicalPortal?'Medical Review':'Case Processing';
 const employeeRegistration=teamPortal&&portalTab==='register';
 const [teamCode,setTeamCode]=useState('');
 const [resolvedTeamKey,setResolvedTeamKey]=useState('');
 const [findingTeam,setFindingTeam]=useState(false);
 const teamLookup=useRef<AbortController|null>(null);
 const [teamInfo,setTeamInfo]=useState<{teamCode:string;name:string;leadName:string;remainingSeats:number}|null>(null);
 const portalTabs=portalRole&&!employeeRegistration?<section className="role-access-header" aria-label={`${portalRole.name} access`}><div><span className="pa-eyebrow">YOUR ROLE WORKSPACE</span><h2>{portalRole.name}</h2></div><nav aria-label="Role access tabs">{[['user','User login'],['admin','Role Admin login'],...(teamPortal?[['team-lead','Team Lead login']]:[]),['register','New registration']].map(([key,label])=><a key={key} href={`${portalBase}/${key}${teamKey?'?team='+encodeURIComponent(teamKey):''}`} aria-current={portalTab===key?'page':undefined}>{label}</a>)}</nav></section>:null;
 const [catalogState,setCatalogState]=useState<'checking'|'ready'|'unavailable'>('checking');
 const [catalogAttempt,setCatalogAttempt]=useState(0);
 const [selection,setSelection]=useState<{role:Role;token:string}|null>(null);
 const [values,setValues]=useState<Record<string,string>>({});
 const [details,setDetails]=useState<Record<string,string>>({});
 const [error,setError]=useState('');const [notice,setNotice]=useState('');const [busy,setBusy]=useState(false);
 const [resetToken,setResetToken]=useState(()=>window.location.hash.startsWith('#/reset-password')?new URLSearchParams(window.location.hash.split('?')[1]).get('token')||'':'');
 useEffect(()=>{if(window.location.hash.startsWith('#/reset-password?'))window.history.replaceState(null,'',window.location.pathname+window.location.search+'#/reset-password');},[]);
 useEffect(()=>{const change=()=>{const next=window.location.hash;if(next.startsWith('#/reset-password?')){setResetToken(new URLSearchParams(next.split('?')[1]).get('token')||'');window.history.replaceState(null,'',window.location.pathname+window.location.search+'#/reset-password');}setRoute(window.location.hash);if(document.scrollingElement)document.scrollingElement.scrollTop=0;setError('');setNotice('');setValues({});setDetails({});};window.addEventListener('hashchange',change);return()=>window.removeEventListener('hashchange',change);},[]);
 useEffect(()=>{
  if(!requiresCatalog)return;
  let active=true;const controller=new AbortController();const timer=setTimeout(()=>controller.abort(),20000);setCatalogState('checking');
  apiRequest<Role[]>('/api/auth/access-roles','GET',undefined,controller.signal).then(data=>{
   if(!Array.isArray(data)||!data.length||data.some(r=>!publicRoleCatalog.some(known=>known.role===r.role)||typeof r.name!=='string'||!Array.isArray(r.fields)))throw new Error('Invalid account catalog');
   if(active){setRoles(data);setCatalogState('ready');}
  }).catch(()=>{if(active)setCatalogState('unavailable');}).finally(()=>clearTimeout(timer));
  return()=>{active=false;clearTimeout(timer);controller.abort();};
 },[catalogAttempt,requiresCatalog]);
 useEffect(()=>{
  teamLookup.current?.abort();setFindingTeam(false);
  if(!portalRole||portalTab!=='register'){setBusy(false);return;}
  const c=new AbortController();setBusy(true);setSelection(null);setTeamInfo(null);setResolvedTeamKey('');setTeamCode('');setError('');
  // Resolve the public invitation before acquiring the mutation CSRF token.
  // Parallel first-visit GETs can each set a different CSRF cookie.
  (async()=>{const info=teamKey?await apiRequest<{teamCode:string;name:string;leadName:string;remainingSeats:number}>(`${teamApi}/invitations/${encodeURIComponent(teamKey)}`,'GET',undefined,c.signal):null;const result=await apiRequest<{selectionToken:string}>('/api/auth/role-selection','POST',{role:portalRole.role},c.signal);return {result,info};})().then(({result,info})=>{if(info&&!teamPortal)throw new Error('This account type does not support team registration.');if(info&&info.remainingSeats<1)throw new Error(`This team is full. Contact the ${teamDepartment} Admin.`);if(!c.signal.aborted){setTeamInfo(info);setResolvedTeamKey(teamKey||'');setSelection({role:portalRole,token:result.selectionToken});}}).catch(e=>{if(!c.signal.aborted)setError(e instanceof Error?e.message:'Could not prepare registration');}).finally(()=>{if(!c.signal.aborted)setBusy(false);});
  return()=>{c.abort();teamLookup.current?.abort();};
 },[portalRole?.role,portalTab,catalogAttempt,teamKey]);
 async function findTeam(e:React.FormEvent){e.preventDefault();if(findingTeam)return;const c=new AbortController();teamLookup.current=c;setFindingTeam(true);setError('');setResolvedTeamKey('');setTeamInfo(null);try{
  const result=await apiRequest<TeamInvitation>(`${teamApi}/invitations/code/${encodeURIComponent(teamCode.trim().toUpperCase())}`,'GET',undefined,c.signal);
  if(c.signal.aborted)return;
  if(!result.registrationKey)throw new Error('Could not verify this Team Lead.');
  if(result.remainingSeats<1)throw new Error(`This team is full. Contact the ${teamDepartment} Admin.`);
  setTeamInfo(result);setResolvedTeamKey(result.registrationKey);
 }catch(e){if(!c.signal.aborted)setError(e instanceof Error?e.message:'Unable to find this Team Lead.');}finally{if(!c.signal.aborted)setFindingTeam(false);}}
 async function submit(e:React.FormEvent){e.preventDefault();if(busy)return;setBusy(true);setError('');try{
  if(route.startsWith('#/register')||portalRole&&portalTab==='register'){
   if(!selection)throw new Error('Select an account type again.');
   if(employeeRegistration&&!resolvedTeamKey)throw new Error('Enter and verify your Team Lead ID before submitting.');
   if(values.password!==values.confirmPassword)throw new Error('Passwords do not match.');
   const result=await apiRequest<{message:string}>('/api/auth/register','POST',{...values,details,selectionToken:selection.token,...(resolvedTeamKey?{teamRegistrationKey:resolvedTeamKey}:{})});setNotice(result.message);setValues({});setDetails({});setSelection(null);
  }else if(route.startsWith('#/forgot-password')){
   const result=await apiRequest<{message:string}>('/api/auth/forgot-password','POST',{identifier:values.identifier});setNotice(result.message);setValues({});
  }else{
   if(values.password!==values.confirmPassword)throw new Error('Passwords do not match.');
   const result=await apiRequest<{message:string}>('/api/auth/reset-password','POST',{token:resetToken,password:values.password,confirmPassword:values.confirmPassword});setNotice(result.message);setValues({});
  }
 }catch(e){setError(e instanceof Error?e.message:'Unable to complete request.');}finally{setBusy(false);}}
 const registration=route.startsWith('#/register')||!!portalRole&&portalTab==='register',forgot=route.startsWith('#/forgot-password'),reset=route.startsWith('#/reset-password');
 const login=route.startsWith('#/login')||route.startsWith('#/workspace')||route.startsWith('#/processing-teams')||route.startsWith('#/medical-teams')||!!portalRole&&!registration;
 const field=(key:string,label:string,type='text',maxLength=128,required=true,isDetail=false)=>{
  const state=isDetail?details:values, update=isDetail?setDetails:setValues;
  const options=registrationOptions[key];
  return <label key={key}>{label}{!required&&<span className="pa-optional">Optional</span>}{options?<select aria-label={label} name={key} required={required} value={state[key]||''} onChange={e=>update(v=>({...v,[key]:e.target.value}))}><option value="">Select {label.toLowerCase()}</option>{options.map(value=><option key={value}>{value}</option>)}</select>:<input aria-label={label} name={key} type={type} required={required} maxLength={maxLength} minLength={key.includes('Password')||key==='password'?12:undefined} autoComplete={type==='password'?'new-password':key==='email'?'email':key==='fullName'?'name':undefined} value={state[key]||''} onChange={e=>update(v=>({...v,[key]:e.target.value}))}/> }</label>;
 };
 const header=<header className="pa-header"><a className="pa-brand" href="#/home" aria-label="PharmaSafe home"><span><ShieldCheck size={23}/></span><strong>PharmaSafe<small>SAFETY SUITE</small></strong></a><nav aria-label="Public site"><a className="pa-nav-register" href="#/students/register">Student registration</a><a className="pa-nav-login" href="#/login" aria-current={login?'page':undefined}>Login <ArrowRight size={15}/></a></nav></header>;
 const cards=<section id="account-types" className="pa-accounts"><div className="pa-section-intro"><div><p className="pa-eyebrow">YOUR ROLE. YOUR WORKSPACE.</p><h2>Find your place in the safety workflow.</h2></div><p>Select your account type to request access.<br/>Every application is reviewed by an administrator.</p></div>{catalogState==='unavailable'&&<div className="pa-catalog-notice" role="status"><p>Account types are shown below. The access service is temporarily unavailable. Registration requires a connection to the service.</p><button type="button" onClick={()=>setCatalogAttempt(n=>n+1)}>Retry connection</button></div>}{catalogState==='checking'&&catalogAttempt>0&&<p role="status">Reconnecting to the access service…</p>}<div className="pa-roles">{roles.map(r=>{const Icon=icons[r.role as keyof typeof icons]||ShieldCheck;return <article key={r.role}><span className="pa-role-icon"><Icon size={23}/></span><h3>{r.name}</h3><p>{descriptions[r.role]}</p><div className="pa-role-access-actions"><a href={`#/access/${r.role}/user`} aria-label={`Open ${r.name} login`}>Sign in <ArrowRight size={16}/></a><a href={`#/access/${r.role}/register`} aria-label={`New registration for ${r.name}`}>New register</a></div><button className="pa-legacy-request" disabled={busy} onClick={()=>{window.location.hash=`/access/${r.role}/register`;}} aria-label={`Request access as ${r.name}`}>Request access <ArrowRight size={16}/></button></article>;})}</div></section>;
 const footer=<footer className="pa-footer"><span>PharmaSafe · Pharmacovigilance case management</span><span><LockKeyhole size={13}/> Controlled access. Connected care.</span></footer>;
 if(login)return <div className={`pa-site public-login ${portalRole?'role-portal':''}`}>{header}{portalTabs}<LoginPage key={`${portalRole?.role||'global'}-${portalTab}`} applicationRole={portalRole?.role} roleName={portalRole?.name} adminLogin={portalTab==='admin'} teamLeadLogin={portalTab==='team-lead'} registerHref={portalRole?`${portalBase}/register${teamKey?'?team='+encodeURIComponent(teamKey):''}`:undefined}/>{footer}</div>;
 const home=!registration&&!forgot&&!reset;
 return <div className={`pa-site ${forgot||reset?'public-recovery':''} ${portalRole?'role-register':''} ${home?'pa-home':''}`}>{header}{portalTabs}<main className="pa-shell">{error&&<p role="alert" className="pa-error">{error}</p>}
 {employeeRegistration&&!teamKey&&!notice&&<section className="pa-card pa-team-picker"><h2>Choose your Team Lead</h2><p>Enter the ID supplied by your Team Lead, for example {teamPrefix}-TL-001. Your application will go to that Lead and the {teamDepartment} Admin.</p><form onSubmit={findTeam}><label>Team Lead ID<input aria-label="Team Lead ID" disabled={findingTeam} required maxLength={24} pattern={`${teamPrefix}-TL-[0-9]{3,18}`} placeholder={`${teamPrefix}-TL-001`} value={teamCode} onChange={e=>{setTeamCode(e.target.value.toUpperCase());setResolvedTeamKey('');setTeamInfo(null);}}/></label><button disabled={findingTeam||busy} type="submit">{findingTeam?'Checking team…':'Verify Team Lead'}</button></form>{teamInfo&&resolvedTeamKey&&<p role="status">Selected: {teamInfo.teamCode} · {teamInfo.name} · Team Lead: {teamInfo.leadName}</p>}</section>}
 {employeeRegistration&&teamKey&&error&&<p><a href={`${portalBase}/register`}>Use a Team Lead ID instead</a></p>}
 {home?<section className="pa-hero pa-home-hero"><div className="pa-home-copy"><p className="pa-eyebrow"><span className="pa-dot"/> CONNECTED PHARMACOVIGILANCE</p><h1>Safer decisions.<br/><em>Stronger together.</em></h1><p className="pa-hero-copy">A connected space for safety teams and students to turn clinical information into meaningful work.</p><p className="pa-home-guidance">One login. Your own workspace.<br/>Sign in with your account details to continue, or register as a student to join your college.</p><div className="pa-trust"><span><CheckCircle2 size={15}/> Secure access</span><span><CheckCircle2 size={15}/> Connected learning</span></div></div><figure className="pa-home-visual"><img src="/images/safety-laboratory.webp" alt="Clinical laboratory team working together with diagnostic instruments" width="1400" height="933" fetchPriority="high"/><figcaption><span className="pa-eyebrow">PURPOSE IN EVERY DETAIL</span><p>From knowledge to confident care.</p></figcaption></figure></section>:
 notice?<section className="pa-card pa-success" role="status"><CheckCircle2 size={42}/><p className="pa-eyebrow">NEXT STEP</p><h1>{registration?'Request submitted':'Request complete'}</h1><p>{notice}</p><a className="pa-primary-link" href={portalRole?`${portalBase}/user`:"#/login"}>Return to sign in <ArrowRight size={16}/></a></section>:registration&&(!selection||!!portalRole&&selection.role.role!==portalRole.role)?(portalRole?<section className="pa-card"><p role="status">{busy?'Preparing your role registration…':'Registration is not ready. Retry the connection to continue.'}</p>{!busy&&<button onClick={()=>setCatalogAttempt(v=>v+1)}>Retry registration</button>}</section>:cards):
 <><div className="pa-page-intro"><a href="#/home">← Back to home</a><p className="pa-eyebrow">{registration?'WORKSPACE ACCESS':'ACCOUNT RECOVERY'}</p><h1>{registration?`${selection?.role.name} registration`:forgot?'Request a password reset':'Set a new password'}</h1><p>{registration?'Tell us about yourself and your safety experience. We’ll send your application for administrator review.':forgot?'Enter your official email or username to request administrator review.':'Choose a new password using your approved, single-use reset link.'}</p></div>
 <div className={`pa-application ${registration?'':'pa-recovery'}`}>
 {!registration&&<aside className="access-photo recovery-photo" aria-label="Safety research"><img src="/images/safety-laboratory.webp" alt="Medical laboratory team working with diagnostic instruments" width="1400" height="933"/><div className="access-photo-copy"><span><ShieldCheck size={17}/> PROTECTED WORKSPACE</span><h2>Your work matters.<br/>Let’s reconnect.</h2><p>Account recovery with administrator review and a single-use reset link.</p></div></aside>}
 {registration&&<aside className="pa-application-aside"><span className="pa-role-icon"><ShieldCheck size={24}/></span><p className="pa-eyebrow">SELECTED WORKSPACE</p><h2>{selection?.role.name}</h2><p>{descriptions[selection!.role.role]}</p><ol><li><span>1</span>Identity & organization</li><li><span>2</span>Role & qualifications</li><li><span>3</span>Account security</li></ol><div className="pa-aside-note"><LockKeyhole size={17}/><p>Your requested role stays fixed throughout this application. Access begins only after approval.</p></div></aside>}
 <form onSubmit={submit} className="pa-card pa-registration" aria-busy={busy}>
 {registration&&resolvedTeamKey&&teamInfo&&<section className="pa-team-invite"><strong>{teamInfo.name} · {teamInfo.teamCode}</strong><p>Team Lead: {teamInfo.leadName}. Your request will be available to this Team Lead and the {teamDepartment} Admin.</p></section>}
 {registration?<><fieldset><legend><span>01</span>Identity & organization</legend><p>Use your official workplace information.</p><div className="pa-form">{field('fullName','Full name')}{field('employeeId','Employee ID','text',64)}{field('email','Official email address','email',254)}{field('mobileNumber','Mobile number','tel',32)}{field('department','Department','text',64)}{field('designation','Designation')}{field('organization','Organization')}</div></fieldset><fieldset><legend><span>02</span>Role & qualifications</legend><p>Select the closest match. Use additional information for details not listed.</p><div className="pa-form">{selection?.role.fields.map(key=>field(key,fieldLabel(key),'text',1000,true,true))}</div></fieldset><fieldset><legend><span>03</span>Account security</legend><p>Choose a unique username and a strong password.</p><div className="pa-form">{field('username','Username','text',64)}<p className="pa-field-help">3–64 letters, numbers, dots, underscores or hyphens.</p>{field('password','Password','password',72)}{field('confirmPassword','Confirm password','password',72)}<p className="pa-wide pa-field-help">At least 12 characters with letters and a number or symbol. Maximum 72 bytes.</p><label className="pa-wide">Reason for access<textarea required maxLength={2000} value={values.reason||''} onChange={e=>setValues(v=>({...v,reason:e.target.value}))}/></label><label className="pa-wide">Additional information <span className="pa-optional">Optional</span><textarea maxLength={2000} value={values.additionalInformation||''} onChange={e=>setValues(v=>({...v,additionalInformation:e.target.value}))}/></label></div></fieldset></>:<div className="pa-form">{forgot?field('identifier','Official email or username','text',254):<>{field('password','New password','password',72)}{field('confirmPassword','Confirm password','password',72)}</>}</div>}
 <div className="pa-form-actions"><p><ShieldCheck size={16}/>{registration?'Reviewed by your administrator before activation.':'Your account information stays protected.'}</p><button disabled={busy||reset&&!resetToken||employeeRegistration&&!resolvedTeamKey} type="submit">{busy?'Submitting…':registration?'Submit access request':forgot?'Request administrator review':'Change password'}<ArrowRight size={16}/></button>{reset&&!resetToken&&<p role="alert">Open the complete reset link from your email.</p>}</div>
 </form></div></>}
 </main>{footer}</div>;
}
