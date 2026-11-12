import { render, screen, fireEvent, waitFor, } from '@testing-library/react';
import { beforeEach, describe, it, expect, vi } from 'vitest';
import { PublicAccess } from './PublicAccess';
import { apiRequest } from '../config/api';
vi.mock('../config/api',()=>({apiRequest:vi.fn()}));
vi.mock('./LoginPage',()=>({LoginPage:()=> <div>Sign in screen</div>}));
const roles=[{role:'ROLE_MEDICAL_REVIEWER',name:'Medical Reviewer',fields:['medicalQualification','specialization']},{role:'ROLE_CASE_PROCESSOR',name:'Case Processor',fields:['training']}];
const request=vi.mocked(apiRequest);
beforeEach(()=>{vi.resetAllMocks();window.history.replaceState(null,'','/');request.mockImplementation(async(path)=>path==='/api/auth/access-roles'?roles:path==='/api/auth/role-selection'?{selectionToken:'server-signed-role'}:{message:'Request received. Administrator approval required.'});});
describe('Public user access',()=>{
 it('keeps sign in on its own route without account cards',()=>{window.history.replaceState(null,'','/#/login');render(<PublicAccess/>);expect(screen.getByText('Sign in screen')).toBeInTheDocument();expect(screen.queryByRole('button',{name:/Request access as/})).not.toBeInTheDocument();});
 it('offers supported roles without a public administrator registration',async()=>{render(<PublicAccess/>);expect(await screen.findByRole('button',{name:'Request access as Medical Reviewer'})).toBeInTheDocument();expect(screen.queryByRole('button',{name:/Administrator/})).not.toBeInTheDocument();expect(screen.queryByText('Sign in screen')).not.toBeInTheDocument();});
 it('binds the selected role to a server token, shows relevant fields and submits a real request',async()=>{
  render(<PublicAccess/>);fireEvent.click(await screen.findByRole('button',{name:'Request access as Medical Reviewer'}));
  await screen.findByRole('heading',{name:'Medical Reviewer registration'});
  expect(screen.queryByLabelText('Training')).not.toBeInTheDocument();expect(screen.getByLabelText('Department').tagName).toBe('SELECT');expect(screen.getByLabelText('Medical Qualification').tagName).toBe('SELECT');
  const entries={'Full name':'Synthetic Reviewer','Employee ID':'EMP-1','Official email address':'reviewer@example.test','Mobile number':'+12345678901','Department':'Safety','Designation':'Physician','Organization':'Synthetic Org','Username':'reviewer','Password':'SyntheticPassword123!','Confirm password':'SyntheticPassword123!','Medical Qualification':'MD','Specialization':'Clinical pharmacology','Reason for access':'Assigned review work'};
  for(const [name,value] of Object.entries(entries))fireEvent.change(screen.getByLabelText(new RegExp('^'+name+'$','i')),{target:{value}});
  fireEvent.click(screen.getByRole('button',{name:'Submit access request'}));await screen.findByRole('heading',{name:'Request submitted'});
  expect(request).toHaveBeenCalledWith('/api/auth/register','POST',expect.objectContaining({selectionToken:'server-signed-role',details:{medicalQualification:'MD',specialization:'Clinical pharmacology'}}));
 });
 it('shows password recovery and never places the reset token in the page',async()=>{
  window.history.replaceState(null,'','/#/reset-password?token=synthetic-single-use-token');render(<PublicAccess/>);
  expect(window.location.hash).toBe('#/reset-password');expect(screen.queryByText('synthetic-single-use-token')).not.toBeInTheDocument();
  fireEvent.change(screen.getByLabelText('New password'),{target:{value:'UpdatedPassword123!'}});fireEvent.change(screen.getByLabelText('Confirm password'),{target:{value:'UpdatedPassword123!'}});fireEvent.click(screen.getByRole('button',{name:'Change password'}));
  await waitFor(()=>expect(request).toHaveBeenCalledWith('/api/auth/reset-password','POST',{token:'synthetic-single-use-token',password:'UpdatedPassword123!',confirmPassword:'UpdatedPassword123!'}));
 });
 it('submits generic recovery requests',async()=>{
  window.history.replaceState(null,'','/#/forgot-password');render(<PublicAccess/>);fireEvent.change(screen.getByLabelText('Official email or username'),{target:{value:'unknown@example.test'}});fireEvent.click(screen.getByRole('button',{name:'Request administrator review'}));await waitFor(()=>expect(request).toHaveBeenCalledWith('/api/auth/forgot-password','POST',{identifier:'unknown@example.test'}));
 });
 it('shows registration API errors and permits retry',async()=>{render(<PublicAccess/>);await screen.findByRole('button',{name:'Request access as Medical Reviewer'});request.mockRejectedValueOnce(new Error('Service unavailable'));fireEvent.click(screen.getByRole('button',{name:'Request access as Medical Reviewer'}));expect(await screen.findByRole('alert')).toHaveTextContent('Service unavailable');expect(screen.getByRole('button',{name:'Request access as Medical Reviewer'})).toBeEnabled();});
});
