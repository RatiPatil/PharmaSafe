import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { CaseActivities } from './CaseActivities';
import { DuplicateCheck } from './DuplicateCheck';
import type { SafetyCaseDetails } from '../types/case';
import { apiRequest } from '../config/api';
vi.mock('../config/api',()=>({apiRequest:vi.fn()}));
vi.mock('../context/AuthContext',()=>({useAuth:()=>({user:{id:1,username:'manager',role:'ROLE_SAFETY_MANAGER'}})}));
const api=vi.mocked(apiRequest);
const record={id:7,caseNumber:'SYNTHETIC-7',workflowState:'Closed',rowVersion:3,versionNumber:2,locked:false} as SafetyCaseDetails;
beforeEach(()=>{api.mockReset();api.mockImplementation(async path=>path.includes('closure-readiness')?{canClose:false,openActionItems:0,pendingExpeditedReports:0,blockers:[]}: {content:[],totalElements:0,totalPages:0});});
describe('case activity safeguards',()=>{
 it('opens a separate confirmation form and preserves entries after a failed reopen',async()=>{
  render(<CaseActivities record={record} blocked={false} assignees={[{id:2,username:'processor',role:'ROLE_CASE_PROCESSOR'}]} onReload={vi.fn()} onDirtyChange={vi.fn()} onReadiness={vi.fn()}/>);
  const open=screen.getByRole('button',{name:'Reopen for follow-up'});await waitFor(()=>expect(open).not.toBeDisabled());fireEvent.click(open);
  expect(screen.getByRole('dialog')).toBeInTheDocument();fireEvent.change(screen.getByLabelText('Case processor'),{target:{value:'2'}});fireEvent.change(screen.getByLabelText('Follow-up due'),{target:{value:'2026-12-01T09:00'}});fireEvent.change(screen.getByLabelText('Reason for reopening'),{target:{value:'Additional information'}});fireEvent.change(screen.getByLabelText('Confirm your password'),{target:{value:'Synthetic-password'}});
  api.mockRejectedValueOnce(new Error('Password confirmation failed'));fireEvent.click(screen.getByRole('button',{name:'Save activity'}));await screen.findByRole('alert');expect(screen.getByLabelText('Reason for reopening')).toHaveValue('Additional information');
  fireEvent.click(screen.getByRole('button',{name:'Cancel'}));expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
 });
 it('prevents activity mutations while clinical edits are unsaved',async()=>{
  render(<CaseActivities record={record} blocked={true} assignees={[]} onReload={vi.fn()} onDirtyChange={vi.fn()} onReadiness={vi.fn()}/>);expect(screen.getByRole('button',{name:'Reopen for follow-up'})).toBeDisabled();
 });
 it('clears duplicate suggestions when identifying data changes',async()=>{
  api.mockResolvedValue({content:[],totalElements:0,totalPages:0});const {rerender}=render(<DuplicateCheck patientIdentifier="PATIENT-A"/>);fireEvent.click(screen.getByRole('button',{name:'Check existing cases'}));await screen.findByRole('status');rerender(<DuplicateCheck patientIdentifier="PATIENT-B"/>);expect(screen.queryByRole('status')).not.toBeInTheDocument();
 });
});
