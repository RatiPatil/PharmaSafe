import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { CaseDocuments } from './CaseDocuments';
import { ProfilePanel } from './ProfilePanel';
import { apiRequest, downloadPdf } from '../config/api';
import type { SafetyCaseDetails } from '../types/case';
const auth=vi.hoisted(()=>({user:{id:1,username:'reporter',role:'ROLE_REPORTING_SPECIALIST'}}));
vi.mock('../context/AuthContext',()=>({useAuth:()=>auth}));
vi.mock('../config/api',()=>({apiRequest:vi.fn(),downloadPdf:vi.fn()}));
const api=vi.mocked(apiRequest);const download=vi.mocked(downloadPdf);
const record={id:7,caseNumber:'SYNTHETIC-7',rowVersion:4,versionNumber:2,workflowState:'Closed'} as SafetyCaseDetails;
const archived={id:'archive-1',caseId:7,documentKind:'CASE_FORM',finalDocument:true,caseVersion:2,sourceRowVersion:4,workflowState:'Closed',generatedByUsername:'reporter',generatedAt:'2026-11-01T10:00:00Z',fileName:'synthetic-final.pdf',pdfSha256:'a'.repeat(64)};
beforeEach(()=>{api.mockReset();download.mockReset();download.mockResolvedValue();auth.user.role='ROLE_REPORTING_SPECIALIST';api.mockResolvedValue({content:[],totalElements:0,totalPages:0});});
describe('case documents and profile',()=>{
 it('archives before downloading and sends the source revision',async()=>{
  api.mockImplementation(async(_path,method)=>method==='POST'?archived:{content:[],totalElements:0,totalPages:0});
  render(<CaseDocuments record={record}/>);await waitFor(()=>expect(screen.queryByText('Loading document history…')).not.toBeInTheDocument());fireEvent.click(screen.getByLabelText('Issue final closed-case record'));fireEvent.click(screen.getByRole('button',{name:'Generate & download PDF'}));
  await waitFor(()=>expect(download).toHaveBeenCalledWith('/api/cases/7/documents/archive-1/pdf','synthetic-final.pdf'));
  expect(api).toHaveBeenCalledWith('/api/cases/7/documents','POST',{documentKind:'CASE_FORM',finalDocument:true,expectedRowVersion:4});
 });
 it('does not offer final issuance or medical summaries to case processors',async()=>{
  auth.user.role='ROLE_CASE_PROCESSOR';render(<CaseDocuments record={record} blocked/>);expect(screen.queryByLabelText('Issue final closed-case record')).not.toBeInTheDocument();expect(screen.queryByRole('option',{name:'Medical summary - clinical sections'})).not.toBeInTheDocument();expect(screen.getByRole('button',{name:'Generate & download PDF'})).toBeDisabled();
  await waitFor(()=>expect(screen.queryByText('Loading document history…')).not.toBeInTheDocument());
 });
 it('downloads an archived copy without generating another PDF',async()=>{
  api.mockResolvedValue({content:[archived],totalElements:1,totalPages:1});render(<CaseDocuments record={record}/>);fireEvent.click(await screen.findByRole('button',{name:'Download PDF'}));await waitFor(()=>expect(download).toHaveBeenCalledOnce());expect(api.mock.calls.some(c=>c[1]==='POST')).toBe(false);
 });
 it('shows an account role and saves only personal profile fields',async()=>{
  const profile={id:1,username:'reporter',fullName:'Synthetic Reporter',department:'Safety',role:'ROLE_REPORTING_SPECIALIST',accountRowVersion:8,createdAt:'2026-10-01T00:00:00Z'};
  api.mockResolvedValue(profile);const dirty=vi.fn();render(<ProfilePanel refresh={0} onDirtyChange={dirty}/>);await screen.findByRole('heading',{name:'Synthetic Reporter'});expect(screen.getByText('ROLE_REPORTING_SPECIALIST')).toBeInTheDocument();expect(screen.getByText('Generate final case PDFs after case closure.')).toBeInTheDocument();fireEvent.change(screen.getByLabelText('Department'),{target:{value:'Reporting'}});fireEvent.click(screen.getByRole('button',{name:'Save profile details'}));await waitFor(()=>expect(api).toHaveBeenCalledWith('/api/profile','PATCH',{fullName:'Synthetic Reporter',department:'Reporting',expectedAccountRowVersion:8}));
 });
});
