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

});
