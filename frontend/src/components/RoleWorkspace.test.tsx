import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { RoleWorkspace } from './RoleWorkspace';
const auth = vi.hoisted(() => ({ user: { id: 1, username: 'demo', role: 'ROLE_CASE_PROCESSOR' }, logout: vi.fn() }));
vi.mock('../context/AuthContext', () => ({ useAuth: () => auth, getXsrfToken: () => 'token' }));
vi.mock('./CaseEntryForm', () => ({ CaseEntryForm: () => <div>Case entry editor</div> }));
beforeEach(() => {
 window.location.hash = '';
 auth.user.role = 'ROLE_CASE_PROCESSOR';
 vi.stubGlobal('fetch', vi.fn(async () => ({ ok: true, json: async () => ({ content: [], totalPages: 0, totalElements: 0 }) })));
});
describe('role workspaces', () => {
 it.each([
 ['ROLE_ADMIN','Administration','Users & access'],
 ['ROLE_CASE_PROCESSOR','Case processing','Create case'],
 ['ROLE_MEDICAL_REVIEWER','Medical review','Contact logs'],
 ['ROLE_QUALITY_REVIEWER','Quality review','Action items'],
 ['ROLE_SAFETY_MANAGER','Workflow management','Safety reports'],
 ['ROLE_REPORTING_SPECIALIST','Regulatory reporting','Safety reports'],
 ['ROLE_AUDITOR','Audit workspace','Audit history'],
 ])('opens the correct workspace for %s', async (role,title,item) => {
   auth.user.role = role; render(<RoleWorkspace />);
   expect(screen.getByRole('navigation', {name:`${title} navigation`})).toBeInTheDocument();
   expect(screen.getAllByRole('button', {name: item})[0]).toBeInTheDocument();
   if (role !== 'ROLE_ADMIN') expect(screen.queryByRole('button',{name:'Users & access'})).not.toBeInTheDocument();
   await waitFor(() => expect(screen.queryByText('Loading…')).not.toBeInTheDocument());
 });
 it('does not offer write navigation to the auditor', async () => {
   auth.user.role='ROLE_AUDITOR'; render(<RoleWorkspace />);
   expect(screen.queryByRole('button',{name:'Create case'})).not.toBeInTheDocument();
   expect(screen.queryByRole('button',{name:'Action items'})).not.toBeInTheDocument();
   await waitFor(() => expect(screen.queryByText('Loading…')).not.toBeInTheDocument());
 });
 it('navigates to case entry and updates the workspace URL', async () => {
   render(<RoleWorkspace />);
   fireEvent.click(screen.getAllByRole('button',{name:'Create case'})[0]);
   await waitFor(() => expect(screen.getByText('Case entry editor')).toBeInTheDocument());
   expect(window.location.hash).toContain('/workspace/case_processor/new');
 });
 it('blocks unsupported roles instead of granting a default workspace', () => {
   auth.user.role='ROLE_UNKNOWN'; render(<RoleWorkspace />);
   expect(screen.getByText('Access not configured')).toBeInTheDocument();
 });
 it('auditor report navigation remains read-only', async () => {
   auth.user.role='ROLE_AUDITOR';
   vi.stubGlobal('fetch',vi.fn(async (url: string) => ({ok:true,json:async()=>url.includes('/reports') ? {content:[{id:1,reportId:'RPT-1',status:'Pending'}],totalElements:1} : {content:[],totalElements:0,totalPages:0}})));
   render(<RoleWorkspace />);
   fireEvent.click(screen.getByRole('button',{name:'Safety reports'}));
   await waitFor(()=>expect(screen.getByText('RPT-1')).toBeInTheDocument());
   expect(screen.queryByText('Prepare report record')).not.toBeInTheDocument();
   expect(screen.queryByRole('button',{name:'Record submission'})).not.toBeInTheDocument();
 });

 it('preserves an unfinished user form when navigation is cancelled',async()=>{
  auth.user.role='ROLE_ADMIN';const confirm=vi.spyOn(window,'confirm').mockReturnValue(false);
  vi.stubGlobal('fetch',vi.fn(async(url:string)=>({ok:true,status:200,json:async()=>url.includes('/assignees')?[]:url.includes('/overview')?{states:{},totalCases:0}:{content:[],totalElements:0,totalPages:0}})));
  render(<RoleWorkspace/>);fireEvent.click(screen.getByRole('button',{name:'Users & access',exact:true}));
  await waitFor(()=>expect(screen.getByLabelText('Username',{exact:true})).toBeInTheDocument());
  fireEvent.change(screen.getByLabelText('Username',{exact:true}),{target:{value:'unfinished-user'}});
  fireEvent.click(screen.getByRole('button',{name:'Audit history',exact:true}));
  expect(confirm).toHaveBeenCalledOnce();expect(screen.getByLabelText('Username',{exact:true})).toHaveValue('unfinished-user');
  expect(window.location.hash).toContain('/users');confirm.mockRestore();
 });
 it('restores a case detail deep link without requiring another queue click',async()=>{
  auth.user.role='ROLE_MEDICAL_REVIEWER';window.location.hash='#/workspace/medical_reviewer/cases/42';
  vi.stubGlobal('fetch',vi.fn(async(url:string)=>({ok:true,status:200,json:async()=>url.includes('/closure-readiness')?{canClose:false,openActionItems:0,pendingExpeditedReports:0,blockers:['Case is under medical review']}:url.includes('/audit-history')?[]:url.endsWith('/api/cases/42')?{id:42,caseNumber:'CASE-DEEP-LINK',workflowState:'Medical Review',reporters:[],products:[],events:[],assessments:[],medicalHistoryList:[],references:[],attachments:[]}:{content:[],totalElements:0,totalPages:0}})));
  render(<RoleWorkspace/>);await waitFor(()=>expect(screen.getByRole('heading',{name:'CASE-DEEP-LINK',level:1})).toBeInTheDocument());
  await waitFor(()=>expect(screen.queryByText('Loading activities…')).not.toBeInTheDocument());
  expect(screen.getByRole('tab',{name:'Events',exact:true})).toHaveAttribute('aria-selected','true');
 });

});
