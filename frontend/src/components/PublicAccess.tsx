import { useEffect, useState } from 'react';
import { ShieldCheck, ClipboardList, Stethoscope, BadgeCheck, Users, FileCheck, Search } from 'lucide-react';
import { apiRequest } from '../config/api';
import { LoginPage } from './LoginPage';
import './PublicAccess.css';
type Role = {role:string; name:string; fields:string[]};
const descriptions:Record<string,string>={ROLE_CASE_PROCESSOR:'Capture and process assigned safety cases.',ROLE_MEDICAL_REVIEWER:'Review clinical evidence, causality and expectedness.',ROLE_QUALITY_REVIEWER:'Check completeness and review data quality.',ROLE_SAFETY_MANAGER:'Monitor workflows, assignments and safety operations.',ROLE_REPORTING_SPECIALIST:'Prepare safety reports and track reporting deadlines.',ROLE_AUDITOR:'Inspect permitted records and audit history with read-only access.'};
const icons={ROLE_CASE_PROCESSOR:ClipboardList,ROLE_MEDICAL_REVIEWER:Stethoscope,ROLE_QUALITY_REVIEWER:BadgeCheck,ROLE_SAFETY_MANAGER:Users,ROLE_REPORTING_SPECIALIST:FileCheck,ROLE_AUDITOR:Search};
export const fieldLabel=(value:string)=>value.replace(/([A-Z])/g,' $1').replace(/^./,s=>s.toUpperCase());
export function PublicAccess(){
 const [route,setRoute]=useState(window.location.hash);
 const [roles,setRoles]=useState<Role[]>([]);
 const [selection,setSelection]=useState<{role:Role;token:string}|null>(null);
 const [values,setValues]=useState<Record<string,string>>({});
 const [details,setDetails]=useState<Record<string,string>>({});
 const [error,setError]=useState('');const [notice,setNotice]=useState('');const [busy,setBusy]=useState(false);
 const [resetToken,setResetToken]=useState(()=>window.location.hash.startsWith('#/reset-password')?new URLSearchParams(window.location.hash.split('?')[1]).get('token')||'':'');
 useEffect(()=>{if(window.location.hash.startsWith('#/reset-password?'))window.history.replaceState(null,'',window.location.pathname+window.location.search+'#/reset-password');},[]);
 useEffect(()=>{const change=()=>{const next=window.location.hash;if(next.startsWith('#/reset-password?')){setResetToken(new URLSearchParams(next.split('?')[1]).get('token')||'');window.history.replaceState(null,'',window.location.pathname+window.location.search+'#/reset-password');}setRoute(window.location.hash);setError('');setNotice('');setValues({});setDetails({});};window.addEventListener('hashchange',change);return()=>window.removeEventListener('hashchange',change);},[]);
 useEffect(()=>{let active=true;apiRequest<Role[]>('/api/auth/access-roles').then(data=>{if(active&&Array.isArray(data))setRoles(data);}).catch(()=>{});return()=>{active=false;};},[]);
 async function choose(role:Role){setBusy(true);setError('');try{const result=await apiRequest<{selectionToken:string}>('/api/auth/role-selection','POST',{role:role.role});setSelection({role,token:result.selectionToken});window.location.hash=`/register/${role.role}`;}catch(e){setError(e instanceof Error?e.message:'Could not select account type');}finally{setBusy(false);}}
 async function submit(e:React.FormEvent){e.preventDefault();if(busy)return;setBusy(true);setError('');try{
  if(route.startsWith('#/register')){
   if(!selection)throw new Error('Select an account type again.');
   if(values.password!==values.confirmPassword)throw new Error('Passwords do not match.');
   const result=await apiRequest<{message:string}>('/api/auth/register','POST',{...values,details,selectionToken:selection.token});setNotice(result.message);setValues({});setDetails({});setSelection(null);
  }else if(route.startsWith('#/forgot-password')){
   const result=await apiRequest<{message:string}>('/api/auth/forgot-password','POST',{identifier:values.identifier});setNotice(result.message);setValues({});
  }else{
   if(values.password!==values.confirmPassword)throw new Error('Passwords do not match.');
   const result=await apiRequest<{message:string}>('/api/auth/reset-password','POST',{token:resetToken,password:values.password,confirmPassword:values.confirmPassword});setNotice(result.message);setValues({});
  }
 }catch(e){setError(e instanceof Error?e.message:'Unable to complete request.');}finally{setBusy(false);}}
 const registration=route.startsWith('#/register'),forgot=route.startsWith('#/forgot-password'),reset=route.startsWith('#/reset-password');
 const field=(key:string,label:string,type='text',maxLength=128,required=true)=><label key={key}>{label}<input name={key} type={type} required={required} maxLength={maxLength} minLength={key.includes('Password')||key==='password'?12:undefined} autoComplete={type==='password'?'new-password':undefined} value={values[key]||''} onChange={e=>setValues(v=>({...v,[key]:e.target.value}))}/></label>;
 const cards=<section id="account-types" className="pa-card"><p className="pa-eyebrow">JOIN YOUR SAFETY TEAM</p><h1>Request your PharmaSafe workspace</h1><p>Choose your account type. Every request is reviewed by an administrator before access is granted.</p>{!roles.length&&<p role="status">Account types are unavailable. <button onClick={async()=>{try{setRoles(await apiRequest<Role[]>('/api/auth/access-roles'));}catch{setError('Could not load account types. Try again shortly.');}}}>Retry</button></p>}<div className="pa-roles">{roles.map(r=>{const Icon=icons[r.role as keyof typeof icons]||ShieldCheck;return <article key={r.role}><Icon aria-hidden="true"/><h2>{r.name}</h2><p>{descriptions[r.role]}</p><button disabled={busy} onClick={()=>void choose(r)}>Request access as {r.name}</button></article>;})}</div></section>;
 if(!registration&&!forgot&&!reset)return <><LoginPage/><div className="pa-shell">{error&&<p role="alert" className="pa-error">{error}</p>}{cards}</div></>;
 return <main className="pa-shell"><header className="pa-header"><a href="#/home"><ShieldCheck aria-hidden="true"/>PharmaSafe</a><a href="#/login">Sign in</a><a href="#/home">Account types</a></header>{error&&<p role="alert" className="pa-error">{error}</p>}
 {notice?<section className="pa-card" role="status"><h1>{registration?'Request submitted':'Request complete'}</h1><p>{notice}</p><a href="#/login">Return to sign in</a></section>:registration&&!selection?cards:
 <section className="pa-card"><p className="pa-eyebrow">SECURE ACCESS</p><h1>{registration?`${selection?.role.name} registration`:forgot?'Request a password reset':'Set a new password'}</h1><p>{registration?'Your account type is fixed for this application. An administrator will review your details.':forgot?'Enter your official email or username. An administrator will review eligible requests.':'Use the single-use link from your email. Links expire after 30 minutes.'}</p>
 <form onSubmit={submit} className="pa-form" aria-busy={busy}>
 {registration?<>{field('fullName','Full name')}{field('employeeId','Employee ID','text',64)}{field('email','Official email address','email',254)}{field('mobileNumber','Mobile number','tel',32)}{field('department','Department','text',64)}{field('designation','Designation')}{field('organization','Organization')}{field('username','Username','text',64)}<p className="pa-wide">Username: 3–64 letters, numbers, dots, underscores or hyphens. Password: at least 12 characters, including letters and a number or symbol; maximum 72 bytes.</p>{field('password','Password','password',72)}{field('confirmPassword','Confirm password','password',72)}<h2 className="pa-wide">{selection?.role.name} details</h2>{selection?.role.fields.map(key=><label key={key}>{fieldLabel(key)}<input required maxLength={1000} value={details[key]||''} onChange={e=>setDetails(v=>({...v,[key]:e.target.value}))}/></label>)}<label className="pa-wide">Reason for access<textarea required maxLength={2000} value={values.reason||''} onChange={e=>setValues(v=>({...v,reason:e.target.value}))}/></label><label className="pa-wide">Additional information (optional)<textarea maxLength={2000} value={values.additionalInformation||''} onChange={e=>setValues(v=>({...v,additionalInformation:e.target.value}))}/></label></>:forgot?field('identifier','Official email or username','text',254):<>{field('password','New password','password',72)}{field('confirmPassword','Confirm password','password',72)}</>}
 <div className="pa-wide"><button disabled={busy||reset&&!resetToken} type="submit">{busy?'Submitting…':registration?'Submit access request':forgot?'Request administrator review':'Change password'}</button>{reset&&!resetToken&&<p role="alert">Open the complete reset link from your email.</p>}</div>
 </form></section>}</main>;
}
