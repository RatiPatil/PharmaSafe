import { ValidatedFields } from './components/ValidatedFields';
import React, { Component, Suspense, lazy, useEffect, useState } from 'react';
import { AuthProvider, useAuth } from './context/AuthContext';
import { PublicAccess } from './components/PublicAccess';

// The sign-in screen does not download case editors, tables or role workspaces.
const EducationWorkspace=lazy(()=>import('./education/EducationWorkspace').then(m=>({default:m.EducationWorkspace})));
const StudentRegistration=lazy(()=>import('./education/StudentRegistration').then(m=>({default:m.StudentRegistration})));
const MedicalTeamWorkspace=lazy(()=>import('./workspaces/MedicalTeamWorkspace').then(m=>({default:m.MedicalTeamWorkspace})));
const ProcessingTeamWorkspace=lazy(()=>import('./workspaces/ProcessingTeamWorkspace').then(m=>({default:m.ProcessingTeamWorkspace})));
const RoleWorkspace=lazy(()=>import('./components/RoleWorkspace').then(module=>({default:module.RoleWorkspace})));
class WorkspaceLoadBoundary extends Component<{children:React.ReactNode},{failed:boolean}> {
 state={failed:false};
 static getDerivedStateFromError(){return {failed:true};}
 render(){return this.state.failed?<main className="min-h-screen flex items-center justify-center bg-[#F4F7FB] p-6"><section className="bg-white rounded-xl border border-[#DCE4EE] p-7 max-w-md"><h1 className="font-semibold text-lg text-[#102A43]">Workspace could not load</h1><p className="my-4 text-sm text-[#52657A]">Check your connection and reload to open your workspace.</p><button className="bg-[#102A43] text-white px-4 py-2 rounded-lg" onClick={()=>window.location.reload()}>Reload workspace</button></section></main>:this.props.children;}
}
const AppContent:React.FC=()=>{
 const {isAuthenticated,user}=useAuth();
 const [route,setRoute]=useState(window.location.hash);
 useEffect(()=>{const change=()=>queueMicrotask(()=>setRoute(window.location.hash));window.addEventListener('hashchange',change);window.addEventListener('popstate',change);return()=>{window.removeEventListener('hashchange',change);window.removeEventListener('popstate',change);};},[]);
 const education=['ROLE_DOMAIN_ADMIN','ROLE_COLLEGE_ADMIN','ROLE_STUDENT'].includes(user?.role||'')||route.startsWith('#/education');
 if(route.split('?')[0]==='#/students/register')return <WorkspaceLoadBoundary><Suspense fallback={<main><p role="status">Opening student registration…</p></main>}><StudentRegistration/></Suspense></WorkspaceLoadBoundary>;
 const medical=user?.role==='ROLE_MEDICAL_TEAM_LEAD'||route.startsWith('#/medical-teams')||(!route.startsWith('#/workspace')&&!!user?.medicalTeamId);
 const processing=user?.role==='ROLE_CASE_TEAM_LEAD'||route.startsWith('#/processing-teams')||(!route.startsWith('#/workspace')&&(!!user?.processingTeamId));
 // Render immediately while session restoration runs in the background. Protected
 // content is still rendered only after the server verifies the current account.
 const employeeRegistration=['#/access/ROLE_CASE_PROCESSOR/register','#/access/ROLE_MEDICAL_REVIEWER/register'].includes(route.split('?')[0]);
 return isAuthenticated&&!employeeRegistration?<WorkspaceLoadBoundary><Suspense fallback={<main className="min-h-screen flex items-center justify-center bg-[#F4F7FB]"><p role="status" className="text-sm text-[#52657A]">Opening your workspace…</p></main>}>{education?<EducationWorkspace/>:medical?<MedicalTeamWorkspace/>:processing?<ProcessingTeamWorkspace/>:<RoleWorkspace/>}</Suspense></WorkspaceLoadBoundary>:<PublicAccess/>;
};
export const App:React.FC=()=> <AuthProvider><ValidatedFields/><AppContent/></AuthProvider>;
export default App;
