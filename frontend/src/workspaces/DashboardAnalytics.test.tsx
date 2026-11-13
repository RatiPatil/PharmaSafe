import { render, screen, fireEvent, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { DashboardAnalytics, workflowValues } from './DashboardAnalytics';
import { WorkspaceHome } from './WorkspaceHome';
import type { Overview } from './model';
const data:Overview={totalCases:8,seriousCases:3,expeditedCases:2,states:{Draft:2,'Data Entry':3,'Medical Review':1,'Quality Review':1,Closed:1},totalUsers:12,enabledUsers:9,auditEvents:37};
describe('dashboard presentation of existing statistics',()=>{
 it('renders the actual case distribution and supports keyboard-accessible highlighting',()=>{
  render(<DashboardAnalytics data={data}/>);
  expect(screen.getByRole('img',{name:/Workflow distribution: Draft 2, Data Entry 3/})).toBeInTheDocument();
  const entry=screen.getByRole('button',{name:/Data Entry: 3 cases/});fireEvent.click(entry);expect(entry).toHaveAttribute('aria-pressed','true');
  const centre=document.querySelector('.premium-donut-centre')!;expect(centre).toHaveTextContent('3');expect(centre).toHaveTextContent('Data Entry');
  fireEvent.click(entry);expect(centre).toHaveTextContent('8');expect(centre).toHaveTextContent('Accessible cases');
  expect(screen.getByText('Seriousness and expedited priority may overlap.')).toBeInTheDocument();
 });
 it('handles empty scopes without invented segments or invalid SVG geometry',()=>{
  const {container}=render(<DashboardAnalytics data={{...data,totalCases:0,seriousCases:0,expeditedCases:0,states:{Draft:0,Closed:0}}}/>);
  expect(screen.getByText('No cases in your current access scope.')).toBeInTheDocument();expect(container.querySelectorAll('.premium-donut-segment')).toHaveLength(0);expect(container.innerHTML).not.toMatch(/NaN|Infinity/);
 });
 it('accounts for records outside the returned stage buckets without adding case totals',()=>{
  const values=workflowValues({...data,totalCases:10});expect(values.find(v=>v.label==='Other states')?.value).toBe(2);expect(values.reduce((n,v)=>n+v.value,0)).toBe(10);
 });
 it('keeps role KPIs and worklist actions connected to the original values and handlers',()=>{
  const go=vi.fn();render(<WorkspaceHome role="ROLE_ADMIN" data={data} cases={[]} go={go} open={vi.fn()}/>);
  const accounts=screen.getByText('User accounts').closest('.ws-metric') as HTMLElement;expect(within(accounts).getByText('12')).toBeInTheDocument();
  const enabled=screen.getByText('Enabled accounts').closest('.ws-metric') as HTMLElement;expect(within(enabled).getByText('9')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button',{name:/Explore your case worklist/}));expect(go).toHaveBeenCalledWith('cases');
 });
});
