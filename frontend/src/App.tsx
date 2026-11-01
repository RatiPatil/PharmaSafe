import React, { Component, Suspense, lazy } from 'react';
import { AuthProvider, useAuth } from './context/AuthContext';
import { LoginPage } from './components/LoginPage';

// The sign-in screen does not download case editors, tables or role workspaces.
const RoleWorkspace=lazy(()=>import('./components/RoleWorkspace').then(module=>({default:module.RoleWorkspace})));
class WorkspaceLoadBoundary extends Component<{children:React.ReactNode},{failed:boolean}> {
 state={failed:false};
 static getDerivedStateFromError(){return {failed:true};}
 render(){return this.state.failed?<main className="min-h-screen flex items-center justify-center bg-[#F4F7FB] p-6"><section className="bg-white rounded-xl border border-[#DCE4EE] p-7 max-w-md"><h1 className="font-semibold text-lg text-[#102A43]">Workspace could not load</h1><p className="my-4 text-sm text-[#52657A]">Check your connection and reload to open your workspace.</p><button className="bg-[#102A43] text-white px-4 py-2 rounded-lg" onClick={()=>window.location.reload()}>Reload workspace</button></section></main>:this.props.children;}
}
const AppContent:React.FC=()=>{
 const {isAuthenticated}=useAuth();
 // Render immediately while session restoration runs in the background. Protected
 // content is still rendered only after the server verifies the current account.
 return isAuthenticated?<WorkspaceLoadBoundary><Suspense fallback={<main className="min-h-screen flex items-center justify-center bg-[#F4F7FB]"><p role="status" className="text-sm text-[#52657A]">Opening your workspace…</p></main>}><RoleWorkspace/></Suspense></WorkspaceLoadBoundary>:<LoginPage/>;
};
export const App:React.FC=()=> <AuthProvider><AppContent/></AuthProvider>;
export default App;
