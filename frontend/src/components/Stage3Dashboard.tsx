import React, { useState, useEffect } from 'react';
import { getXsrfToken, useAuth } from '../context/AuthContext';
import { getApiUrl } from '../config/api';
import { QuickLaunchToolbar } from './QuickLaunchToolbar';
import { CaseEntryForm } from './CaseEntryForm';
import type { AuditLogItem } from '../types/auth';
import type { SafetyCaseSummary, SafetyCaseDetails } from '../types/case';
import type { DashboardSummary, RecentActivity, PaginatedResponse } from '../types/dashboard';
import {
  ShieldCheck,
  LogOut,
  User as UserIcon,
  Clock,
  FileText,
  Layers,
  Database,
  RefreshCw,
  FolderPlus,
  ListOrdered,
  Search,
  MessageSquare,
  CheckSquare2,
  AlertCircle,
  Activity,
  SlidersHorizontal,
  Lock,
  Plus,
  Info,
  ChevronLeft,
  ChevronRight,
  Filter,
  ArrowUpDown
} from 'lucide-react';

interface DatabaseHealthDetails {
  database?: string;
  databaseUser?: string;
  databaseName?: string;
  databaseProduct?: string;
  databaseVersion?: string;
  javaVersion?: string;
  osName?: string;
}

interface AdminStatusDetails {
  totalUsers?: number;
  totalAuditEvents?: number;
  systemStatus?: string;
}

interface ContactLog {
  id: number;
  caseId: number;
  caseNumber: string;
  contactAt: string;
  contactType: string;
  description: string;
  responsibleUsername?: string;
}

interface ActionItem {
  id: number;
  caseId: number;
  caseNumber: string;
  description: string;
  assignedUsername: string;
  status: string;
  dueAt: string;
  overdue: boolean;
}

export const Stage3Dashboard: React.FC = () => {
  const { user, logout, isLoading } = useAuth();

  // Navigation tab state
  const [activeTab, setActiveTab] = useState<
    'dashboard' | 'case-entry' | 'worklist' | 'contact-log' | 'action-items' | 'reports' | 'admin'
  >('dashboard');

  // Case Quick Launch input state
  const [caseSearchInput, setCaseSearchInput] = useState('');
  const [searchFeedback, setSearchFeedback] = useState<string | null>(null);
  const [searchResults, setSearchResults] = useState<SafetyCaseSummary[] | null>(null);
  const [searchingCases, setSearchingCases] = useState(false);

  // Live Assigned Cases Data
  const [assignedCases, setAssignedCases] = useState<SafetyCaseSummary[]>([]);
  const [loadingAssignedCases, setLoadingAssignedCases] = useState(false);
  const [assignedCasesError, setAssignedCasesError] = useState<string | null>(null);
  const [contactLogs, setContactLogs] = useState<ContactLog[]>([]);
  const [actionItems, setActionItems] = useState<ActionItem[]>([]);
  const [interactionLoading, setInteractionLoading] = useState(false);
  const [interactionError, setInteractionError] = useState<string | null>(null);
  const [interactionCaseId, setInteractionCaseId] = useState('');
  const [contactType, setContactType] = useState('Email');
  const [contactDescription, setContactDescription] = useState('');
  const [actionDescription, setActionDescription] = useState('');
  const [actionDueAt, setActionDueAt] = useState('');
  const [showOverdueOnly, setShowOverdueOnly] = useState(false);

  // Dashboard KPI Summary Data
  const [dashboardSummary, setDashboardSummary] = useState<DashboardSummary | null>(null);
  const [loadingSummary, setLoadingSummary] = useState(false);
  const [summaryError, setSummaryError] = useState<string | null>(null);

  // Recent Case Activity Feed
  const [recentActivity, setRecentActivity] = useState<RecentActivity[]>([]);
  const [loadingActivity, setLoadingActivity] = useState(false);

  // Paginated Worklist State
  const [worklistData, setWorklistData] = useState<PaginatedResponse<SafetyCaseSummary> | null>(null);
  const [worklistPage, setWorklistPage] = useState(0);
  const [worklistSize, setWorklistSize] = useState(10);
  const [worklistSortBy, setWorklistSortBy] = useState('createdAt');
  const [worklistDirection, setWorklistDirection] = useState<'asc' | 'desc'>('desc');
  const [worklistWorkflowFilter, setWorklistWorkflowFilter] = useState('');
  const [worklistPriorityFilter, setWorklistPriorityFilter] = useState('');
  const [worklistAssignedOnly, setWorklistAssignedOnly] = useState(false);
  const [worklistSearch, setWorklistSearch] = useState('');
  const [loadingWorklist, setLoadingWorklist] = useState(false);
  const [worklistError, setWorklistError] = useState<string | null>(null);

  // Current Active Case for Editing / Viewing
  const [currentEditingCase, setCurrentEditingCase] = useState<SafetyCaseDetails | null>(null);
  const [quickLaunchSaveTrigger, setQuickLaunchSaveTrigger] = useState(0);

  // Live Backend Data
  const [auditLogs, setAuditLogs] = useState<AuditLogItem[]>([]);
  const [loadingAudit, setLoadingAudit] = useState(false);
  const [auditError, setAuditError] = useState<string | null>(null);

  const [dbHealth, setDbHealth] = useState<DatabaseHealthDetails | null>(null);
  const [adminStatus, setAdminStatus] = useState<AdminStatusDetails | null>(null);

  // Fetch real PostgreSQL dashboard summary counts
  const fetchDashboardSummary = async () => {
    setLoadingSummary(true);
    setSummaryError(null);
    try {
      const res = await fetch(getApiUrl('/api/dashboard/summary'), {
        credentials: 'include',
        headers: { Accept: 'application/json' }
      });
      if (res.ok) {
        const data: DashboardSummary = await res.json();
        setDashboardSummary(data);
      } else {
        setSummaryError('Failed to load dashboard summary metrics.');
      }
    } catch {
      setSummaryError('Network error connecting to summary service.');
    } finally {
      setLoadingSummary(false);
    }
  };

  // Fetch real PostgreSQL recent case activity
  const fetchRecentActivity = async () => {
    setLoadingActivity(true);
    try {
      const res = await fetch(getApiUrl('/api/dashboard/activity'), {
        credentials: 'include',
        headers: { Accept: 'application/json' }
      });
      if (res.ok) {
        const data: RecentActivity[] = await res.json();
        setRecentActivity(data);
      }
    } catch {
      // Non-blocking
    } finally {
      setLoadingActivity(false);
    }
  };

  // Fetch paginated, filtered, and sorted worklist cases
  const fetchPaginatedWorklist = async (page = worklistPage) => {
    setLoadingWorklist(true);
    setWorklistError(null);
    try {
      const params = new URLSearchParams({
        page: page.toString(),
        size: worklistSize.toString(),
        sortBy: worklistSortBy,
        direction: worklistDirection,
        assignedOnly: worklistAssignedOnly ? 'true' : 'false'
      });
      if (worklistWorkflowFilter) params.append('workflowState', worklistWorkflowFilter);
      if (worklistPriorityFilter) params.append('priority', worklistPriorityFilter);
      if (worklistSearch.trim()) params.append('search', worklistSearch.trim());

      const res = await fetch(getApiUrl(`/api/dashboard/worklist?${params.toString()}`), {
        credentials: 'include',
        headers: { Accept: 'application/json' }
      });
      if (res.ok) {
        const data: PaginatedResponse<SafetyCaseSummary> = await res.json();
        setWorklistData(data);
      } else {
        setWorklistError('Failed to load paginated cases from database.');
      }
    } catch {
      setWorklistError('Network error loading worklist.');
    } finally {
      setLoadingWorklist(false);
    }
  };

  // Fetch real PostgreSQL cases assigned to current user (for backwards compatibility & Section B)
  const fetchAssignedCases = async () => {
    setLoadingAssignedCases(true);
    setAssignedCasesError(null);
    try {
      const res = await fetch(getApiUrl('/api/cases/assigned/paged?page=0&size=20'), {
        credentials: 'include',
        headers: { Accept: 'application/json' }
      });
      if (res.ok) {
        const data: PaginatedResponse<SafetyCaseSummary> | SafetyCaseSummary[] = await res.json();
        setAssignedCases(Array.isArray(data) ? data : (data.content || []));
      } else {
        setAssignedCasesError('Unable to load assigned cases from database.');
      }
    } catch {
      setAssignedCasesError('Network error loading assigned cases.');
    } finally {
      setLoadingAssignedCases(false);
    }
  };

  const fetchInteractions = async () => {
    setInteractionLoading(true);
    setInteractionError(null);
    try {
      const [contactsResponse, actionsResponse] = await Promise.all([
        fetch(getApiUrl('/api/contact-logs?page=0&size=20'), { credentials: 'include', headers: { Accept: 'application/json' } }),
        fetch(getApiUrl(`/api/action-items?page=0&size=20&overdue=${showOverdueOnly}`), { credentials: 'include', headers: { Accept: 'application/json' } })
      ]);
      if (!contactsResponse.ok || !actionsResponse.ok) throw new Error('Unable to load personal interaction data.');
      const contacts = await contactsResponse.json();
      const actions = await actionsResponse.json();
      setContactLogs(contacts.content || []);
      setActionItems(actions.content || []);
    } catch (error) {
      setInteractionError(error instanceof Error ? error.message : 'Unable to load interaction data.');
    } finally {
      setInteractionLoading(false);
    }
  };

  const createInteraction = async (kind: 'contact' | 'action') => {
    if (!interactionCaseId.trim()) {
      setInteractionError('Enter a case ID before creating a case-linked record.');
      return;
    }
    const xsrf = getXsrfToken();
    const headers: Record<string, string> = { 'Content-Type': 'application/json', Accept: 'application/json' };
    if (xsrf) headers['X-XSRF-TOKEN'] = xsrf;
    const url = kind === 'contact'
      ? getApiUrl(`/api/cases/${interactionCaseId}/contact-logs`)
      : getApiUrl(`/api/cases/${interactionCaseId}/action-items`);
    const body = kind === 'contact'
      ? { contactAt: new Date().toISOString(), contactType, description: contactDescription }
      : { description: actionDescription, assignedUserId: user?.id, dueAt: new Date(actionDueAt).toISOString() };
    try {
      const response = await fetch(url, { method: 'POST', headers, credentials: 'include', body: JSON.stringify(body) });
      if (!response.ok) {
        const data = await response.json().catch(() => null);
        throw new Error(data?.message || `Unable to save ${kind} entry.`);
      }
      setContactDescription('');
      setActionDescription('');
      setActionDueAt('');
      await fetchInteractions();
    } catch (error) {
      setInteractionError(error instanceof Error ? error.message : `Unable to save ${kind} entry.`);
    }
  };

  const completeActionItem = async (id: number) => {
    const xsrf = getXsrfToken();
    const headers: Record<string, string> = { Accept: 'application/json' };
    if (xsrf) headers['X-XSRF-TOKEN'] = xsrf;
    const response = await fetch(getApiUrl(`/api/action-items/${id}/complete`), {
      method: 'POST', credentials: 'include', headers
    });
    if (response.ok) await fetchInteractions();
    else setInteractionError('Unable to complete this action item.');
  };

  // Open existing case by ID
  const handleOpenCase = async (caseId: number) => {
    try {
      const res = await fetch(getApiUrl(`/api/cases/${caseId}`), {
        credentials: 'include',
        headers: { Accept: 'application/json' }
      });
      if (res.ok) {
        const details: SafetyCaseDetails = await res.json();
        setCurrentEditingCase(details);
        setActiveTab('case-entry');
        setSearchFeedback(`Loaded case ${details.caseNumber} (v${details.versionNumber}).`);
      } else {
        setSearchFeedback(`Failed to load case #${caseId} from server.`);
      }
    } catch {
      setSearchFeedback(`Network error loading case #${caseId}.`);
    }
  };

  // Open existing case directly by case number
  const handleOpenCaseByNumber = async (num: string): Promise<boolean> => {
    try {
      const res = await fetch(getApiUrl(`/api/cases/by-number/${encodeURIComponent(num.trim())}`), {
        credentials: 'include',
        headers: { Accept: 'application/json' }
      });
      if (res.ok) {
        const details: SafetyCaseDetails = await res.json();
        setCurrentEditingCase(details);
        setActiveTab('case-entry');
        setSearchFeedback(`Loaded case ${details.caseNumber} (v${details.versionNumber}).`);
        return true;
      }
    } catch {
      // Ignore
    }
    return false;
  };

  // Case creation click handler
  const handleNewCaseClick = () => {
    setCurrentEditingCase(null);
    setActiveTab('case-entry');
    setSearchFeedback('Opened Initial Case Entry dialog for new safety report.');
  };

  // Successful save / update callback from CaseEntryForm
  const handleSaveSuccess = (savedCase: SafetyCaseDetails) => {
    setCurrentEditingCase(savedCase);
    fetchDashboardSummary();
    fetchRecentActivity();
    fetchAssignedCases();
    fetchPaginatedWorklist(0);
    if (user?.role === 'ROLE_ADMIN') {
      fetchAuditLogs();
      fetchAdminStatus();
    }
    setSearchFeedback(`Successfully committed safety case ${savedCase.caseNumber} (v${savedCase.versionNumber}) to PostgreSQL.`);
  };

  // Case close handler
  const handleCloseCase = () => {
    setCurrentEditingCase(null);
    setActiveTab('dashboard');
    setSearchFeedback('Closed active case workspace.');
  };

  // Fetch real PostgreSQL audit logs from /api/admin/audit-logs
  const fetchAuditLogs = async () => {
    if (!user || user.role !== 'ROLE_ADMIN') return;
    setLoadingAudit(true);
    setAuditError(null);
    try {
      const res = await fetch(getApiUrl('/api/admin/audit-logs'), {
        credentials: 'include',
        headers: { Accept: 'application/json' }
      });
      if (res.ok) {
        const data: AuditLogItem[] = await res.json();
        setAuditLogs(data);
      } else {
        setAuditError('Failed to load audit logs from PostgreSQL.');
      }
    } catch {
      setAuditError('Network error loading audit trail.');
    } finally {
      setLoadingAudit(false);
    }
  };

  // Fetch real database health from /api/health
  const fetchDbHealth = async () => {
    try {
      const res = await fetch(getApiUrl('/api/health'), {
        credentials: 'include',
        headers: { Accept: 'application/json' }
      });
      if (res.ok) {
        const data = await res.json();
        if (data.details) {
          setDbHealth(data.details);
        }
      }
    } catch {
      // Ignored
    }
  };

  // Fetch real admin status from /api/admin/status
  const fetchAdminStatus = async () => {
    if (!user || user.role !== 'ROLE_ADMIN') return;
    try {
      const res = await fetch(getApiUrl('/api/admin/status'), {
        credentials: 'include',
        headers: { Accept: 'application/json' }
      });
      if (res.ok) {
        const data = await res.json();
        setAdminStatus(data);
      }
    } catch {
      // Ignored
    }
  };

  useEffect(() => {
    fetchDbHealth();
    fetchDashboardSummary();
    fetchRecentActivity();
    fetchAssignedCases();
    fetchPaginatedWorklist(0);
    if (user?.role === 'ROLE_ADMIN') {
      fetchAuditLogs();
      fetchAdminStatus();
    }
  }, [user]);

  useEffect(() => {
    if (activeTab === 'dashboard' || activeTab === 'contact-log' || activeTab === 'action-items') {
      fetchInteractions();
    }
  }, [activeTab, showOverdueOnly]);

  const handleCaseSearchSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!caseSearchInput.trim()) {
      setSearchFeedback('Please enter a case number or search query.');
      setSearchResults(null);
      return;
    }

    const query = caseSearchInput.trim();
    setSearchingCases(true);
    setSearchFeedback(null);

    try {
      // 1. Try direct case number lookup first
      const opened = await handleOpenCaseByNumber(query);
      if (opened) {
        setSearchingCases(false);
        return;
      }

      // 2. Query search endpoint
      const res = await fetch(getApiUrl(`/api/cases?search=${encodeURIComponent(query)}`), {
        credentials: 'include',
        headers: { Accept: 'application/json' }
      });
      if (res.ok) {
        const list: SafetyCaseSummary[] = await res.json();
        setSearchResults(list);
        if (list.length === 0) {
          setSearchFeedback(`No safety cases found matching "${query}".`);
        } else {
          setSearchFeedback(`Found ${list.length} case(s) matching "${query}". Click a row below to open.`);
        }
      } else {
        setSearchFeedback('Case search failed on the server.');
      }
    } catch {
      setSearchFeedback('Network error during case search.');
    } finally {
      setSearchingCases(false);
    }
  };

  return (
    <div className="min-h-screen bg-[#E9EEF4] flex flex-col font-sans text-slate-800 antialiased overflow-x-hidden">
      {/* 1. Argus-Style Primary Enterprise Top Header Bar */}
      <header className="argus-header-bar px-4 py-2 flex flex-wrap items-center justify-between gap-3 shadow-sm select-none shrink-0 border-b border-[#0A2D50]">
        <div className="flex items-center space-x-3 min-w-0">
          <div className="bg-white/10 p-1.5 rounded border border-white/20 shrink-0">
            <ShieldCheck className="w-5 h-5 text-sky-300" />
          </div>
          <div className="min-w-0">
            <div className="flex items-center space-x-2 flex-wrap">
              <span className="font-bold text-base tracking-wide text-white">PharmaSafe</span>
              <span className="text-[10px] bg-sky-500/30 text-sky-100 border border-sky-400/40 px-2 py-0.5 rounded font-mono font-semibold">
                STAGE 2 AUTHENTICATED
              </span>
              <span className="text-[10px] bg-emerald-500/30 text-emerald-100 border border-emerald-400/40 px-2 py-0.5 rounded font-mono font-semibold hidden md:inline">
                STAGE 3 DASHBOARD
              </span>
            </div>
            <p className="text-[11px] text-sky-200/80 font-normal truncate">
              Educational Pharmacovigilance Case Management System (Inspired by Oracle Argus Safety 7.0.3)
            </p>
          </div>
        </div>

        {/* User Badge, Security Controls & Logout */}
        <div className="flex items-center space-x-3 text-xs shrink-0 pl-2">
          <div className="hidden sm:flex items-center space-x-2 bg-black/25 px-2.5 py-1 rounded border border-white/15 text-sky-100">
            <UserIcon className="w-3.5 h-3.5 text-sky-300" />
            <span className="font-semibold text-white">{user?.username}</span>
            <span className="text-[10px] bg-sky-600/70 text-sky-100 px-1.5 py-0.2 rounded font-mono border border-sky-400/30">
              {user?.role}
            </span>
          </div>

          <button
            onClick={() => logout()}
            disabled={isLoading}
            className="flex items-center space-x-1.5 bg-rose-700 hover:bg-rose-600 active:bg-rose-800 text-white px-3 py-1 rounded text-xs transition border border-rose-500/40 cursor-pointer shadow-sm disabled:opacity-50"
            title="Log Out of PharmaSafe and terminate session"
          >
            <LogOut className="w-3.5 h-3.5" />
            <span>Log Out</span>
          </button>
        </div>
      </header>

      {/* 2. Quick Launch Toolbar Strip (Matching Argus Quick Launch Toolbar) */}
      <section className="bg-[#E4EDF8] w-full px-4 py-1.5 border-b border-[#B8CCE4] flex flex-wrap items-center justify-between gap-2 shadow-xs shrink-0">
        <div className="flex-1 min-w-0 max-w-full">
          <QuickLaunchToolbar
            onSelectAction={(action) => {
              if (action.id === 'new-case') {
                handleNewCaseClick();
              } else if (action.id === 'open-case') {
                setActiveTab('dashboard');
                setTimeout(() => {
                  const input = document.getElementById('caseSearchInput');
                  if (input) input.focus();
                }, 50);
              } else if (action.id === 'save-case') {
                if (activeTab === 'case-entry') {
                  setQuickLaunchSaveTrigger((prev) => prev + 1);
                } else {
                  setSearchFeedback('Save Case action is available while editing an active case.');
                }
              } else if (action.id === 'close-case') {
                handleCloseCase();
              } else if (action.id === 'worklist') {
                setActiveTab('worklist');
              }
            }}
          />
        </div>
        <div className="flex items-center space-x-3 text-[11px] text-[#0E3D6B] shrink-0">
          <span className="flex items-center space-x-1 font-mono">
            <Database className="w-3.5 h-3.5 text-blue-700" />
            <span>PostgreSQL:</span>
            <strong className="text-emerald-700 font-bold">{dbHealth?.database || 'CONNECTED'}</strong>
          </span>
          <span className="text-slate-400">|</span>
          <span className="flex items-center space-x-1 font-mono">
            <Clock className="w-3.5 h-3.5 text-slate-500" />
            <span>Session:</span>
            <strong className="text-slate-800 font-semibold">{user?.username}</strong>
          </span>
        </div>
      </section>

      {/* 3. Sub-header with Breadcrumbs and Module Info */}
      <div className="argus-sub-header w-full px-4 py-1.5 text-xs font-semibold flex items-center justify-between border-b border-[#B8CCE4] shrink-0">
        <div className="flex items-center space-x-2 text-[#0E3D6B] min-w-0">
          <span className="text-slate-500 font-normal shrink-0">PharmaSafe Safety &gt;</span>
          <span className="font-bold uppercase tracking-wider text-[#0E3D6B] truncate">
            {activeTab === 'dashboard' ? 'Argus Home Page Dashboard' : activeTab.replace('-', ' ')}
          </span>
        </div>
        <div className="text-[11px] text-slate-600 font-mono hidden md:flex items-center space-x-3 pr-2 shrink-0">
          <span>Active View: <strong className="text-[#0E3D6B] uppercase font-bold">{activeTab}</strong></span>
          <span className="text-slate-400">|</span>
          <span>User Role: <strong className="text-emerald-700 font-bold">{user?.role}</strong></span>
          <span className="text-slate-400">|</span>
          <span>Standards: <strong className="text-slate-800">ICH E2B(R3) &bull; 21 CFR Part 11</strong></span>
        </div>
      </div>

      {/* 4. Enterprise Workspace (Left Sidebar Navigation + Main Content Area) */}
      <div className="flex-1 flex w-full max-w-full overflow-hidden min-h-0">
        {/* Compact Left Sidebar Navigation */}
        <aside
          aria-label="Enterprise Navigation Sidebar"
          className="w-56 min-w-[14rem] max-w-[14rem] bg-[#E8F1FA] border-r border-[#B8CCE4] flex flex-col justify-between p-2.5 shrink-0 select-none shadow-xs"
        >
          <div className="space-y-3">
            <div>
              <div className="px-2 py-1 text-[10px] font-bold text-[#0E3D6B] uppercase tracking-wider border-b border-[#C8D9ED] mb-1.5 flex items-center justify-between">
                <span>Enterprise Modules</span>
                <span className="text-[9px] font-mono text-slate-500">Argus 7.0.3</span>
              </div>

              <nav role="tablist" aria-label="Enterprise Modules" className="flex flex-col space-y-1">
                <button
                  role="tab"
                  aria-selected={activeTab === 'dashboard'}
                  onClick={() => setActiveTab('dashboard')}
                  className={`w-full flex items-center space-x-2 px-2.5 py-1.5 rounded text-xs text-left transition font-medium border cursor-pointer ${
                    activeTab === 'dashboard'
                      ? 'bg-[#19589D] text-white border-[#0E3D6B] shadow-xs font-semibold'
                      : 'bg-white/70 text-[#0E3D6B] hover:bg-white border-[#C8D9ED]'
                  }`}
                >
                  <Layers className="w-3.5 h-3.5 flex-shrink-0" />
                  <span className="flex-1">Dashboard</span>
                </button>

                <button
                  role="tab"
                  aria-selected={activeTab === 'case-entry'}
                  onClick={() => setActiveTab('case-entry')}
                  className={`w-full flex items-center space-x-2 px-2.5 py-1.5 rounded text-xs text-left transition font-medium border cursor-pointer ${
                    activeTab === 'case-entry'
                      ? 'bg-[#19589D] text-white border-[#0E3D6B] shadow-xs font-semibold'
                      : 'bg-white/70 text-[#0E3D6B] hover:bg-white border-[#C8D9ED]'
                  }`}
                >
                  <FolderPlus className="w-3.5 h-3.5 flex-shrink-0" />
                  <span className="flex-1">Case Entry</span>
                </button>

                <button
                  data-testid="sidebar-worklist-tab"
                  role="tab"
                  aria-selected={activeTab === 'worklist'}
                  onClick={() => {
                    setActiveTab('worklist');
                    fetchPaginatedWorklist();
                  }}
                  className={`w-full flex items-center space-x-2 px-2.5 py-1.5 rounded text-xs text-left transition font-medium border cursor-pointer ${
                    activeTab === 'worklist'
                      ? 'bg-[#19589D] text-white border-[#0E3D6B] shadow-xs font-semibold'
                      : 'bg-white/70 text-[#0E3D6B] hover:bg-white border-[#C8D9ED]'
                  }`}
                >
                  <ListOrdered className="w-3.5 h-3.5 flex-shrink-0" />
                  <span className="flex-1">Worklist / Queue</span>
                </button>

                <button
                  role="tab"
                  aria-selected={activeTab === 'contact-log'}
                  onClick={() => setActiveTab('contact-log')}
                  className={`w-full flex items-center space-x-2 px-2.5 py-1.5 rounded text-xs text-left transition font-medium border cursor-pointer ${
                    activeTab === 'contact-log'
                      ? 'bg-[#19589D] text-white border-[#0E3D6B] shadow-xs font-semibold'
                      : 'bg-white/70 text-[#0E3D6B] hover:bg-white border-[#C8D9ED]'
                  }`}
                >
                  <MessageSquare className="w-3.5 h-3.5 flex-shrink-0" />
                  <span className="flex-1">Contact Log</span>
                  <span className="text-[9px] text-slate-400 font-mono">Stage 5</span>
                </button>

                <button
                  role="tab"
                  aria-selected={activeTab === 'action-items'}
                  onClick={() => setActiveTab('action-items')}
                  className={`w-full flex items-center space-x-2 px-2.5 py-1.5 rounded text-xs text-left transition font-medium border cursor-pointer ${
                    activeTab === 'action-items'
                      ? 'bg-[#19589D] text-white border-[#0E3D6B] shadow-xs font-semibold'
                      : 'bg-white/70 text-[#0E3D6B] hover:bg-white border-[#C8D9ED]'
                  }`}
                >
                  <CheckSquare2 className="w-3.5 h-3.5 flex-shrink-0" />
                  <span className="flex-1">Action Items</span>
                  <span className="text-[9px] text-slate-400 font-mono">Stage 5</span>
                </button>

                <button
                  role="tab"
                  aria-selected={activeTab === 'reports'}
                  onClick={() => setActiveTab('reports')}
                  className={`w-full flex items-center space-x-2 px-2.5 py-1.5 rounded text-xs text-left transition font-medium border cursor-pointer ${
                    activeTab === 'reports'
                      ? 'bg-[#19589D] text-white border-[#0E3D6B] shadow-xs font-semibold'
                      : 'bg-white/70 text-[#0E3D6B] hover:bg-white border-[#C8D9ED]'
                  }`}
                >
                  <FileText className="w-3.5 h-3.5 flex-shrink-0" />
                  <span className="flex-1">Reports</span>
                  <span className="text-[9px] text-slate-400 font-mono">Stage 6</span>
                </button>

                {user?.role === 'ROLE_ADMIN' && (
                  <button
                    role="tab"
                    aria-selected={activeTab === 'admin'}
                    onClick={() => setActiveTab('admin')}
                    className={`w-full flex items-center space-x-2 px-2.5 py-1.5 rounded text-xs text-left transition font-medium border cursor-pointer ${
                      activeTab === 'admin'
                        ? 'bg-[#19589D] text-white border-[#0E3D6B] shadow-xs font-semibold'
                        : 'bg-white/70 text-[#0E3D6B] hover:bg-white border-[#C8D9ED]'
                    }`}
                  >
                    <SlidersHorizontal className="w-3.5 h-3.5 flex-shrink-0 text-blue-700" />
                    <span className="flex-1">Administration</span>
                  </button>
                )}
              </nav>
            </div>

            {/* Quick Case Queue Summary */}
            <div className="bg-white/90 p-2.5 rounded border border-[#C8D9ED] text-[11px] space-y-1.5 shadow-2xs">
              <span className="font-bold text-[#0E3D6B] uppercase tracking-wider block text-[10px] border-b border-[#E0EBF5] pb-1">
                Case Queue Summary
              </span>
              <div className="flex items-center justify-between text-slate-600">
                <span>Assigned Cases Queue</span>
                <span className="font-mono font-bold text-slate-800 bg-slate-100 px-1.5 py-0.2 rounded text-[10px]">
                  {dashboardSummary ? dashboardSummary.assignedCases : assignedCases.length}
                </span>
              </div>
              <div className="flex items-center justify-between text-slate-600">
                <span>Awaiting Review</span>
                <span className="font-mono font-bold text-amber-800 bg-amber-50 px-1.5 py-0.2 rounded text-[10px]">
                  {dashboardSummary ? dashboardSummary.userAwaitingReviewCases : 0}
                </span>
              </div>
              <div className="flex items-center justify-between text-slate-600">
                <span>Action Items Due</span>
                <span className="font-mono text-slate-500 bg-slate-100 px-1 py-0.2 rounded text-[9px]">
                  Planned
                </span>
              </div>
            </div>
          </div>

          {/* PV Standards & Database Compliance Badge */}
          <div className="bg-[#DFEAF5] p-2 rounded border border-[#C2D7ED] text-[10px] text-[#0E3D6B] space-y-1 mt-2">
            <span className="font-bold uppercase tracking-wider block text-[9px] text-blue-900">
              PV Architecture
            </span>
            <div className="flex items-center space-x-1.5 text-slate-700">
              <span className="w-1.5 h-1.5 rounded-full bg-emerald-600"></span>
              <span>PostgreSQL 18.4 (Active)</span>
            </div>
            <div className="flex items-center space-x-1.5 text-slate-700">
              <span className="w-1.5 h-1.5 rounded-full bg-emerald-600"></span>
              <span>ICH E2B(R3) Aligned (Educational)</span>
            </div>
            <div className="flex items-center space-x-1.5 text-slate-700">
              <span className="w-1.5 h-1.5 rounded-full bg-emerald-600"></span>
              <span>21 CFR Part 11 Architecture</span>
            </div>
          </div>
        </aside>

        {/* Main Workspace Content Area */}
        <main className="flex-1 min-w-0 max-w-full overflow-y-auto overflow-x-hidden p-4 space-y-4">
        {/* VIEW 1: HOME PAGE / DASHBOARD (Summary Cards, Sections A, B, C, D, E) */}
        {activeTab === 'dashboard' && (
          <div className="space-y-4">
            {/* Error Banner if Dashboard Summary API fails */}
            {summaryError && (
              <div className="p-2.5 bg-rose-50 border border-rose-200 rounded text-xs text-rose-800 flex items-center space-x-2">
                <AlertCircle className="w-4 h-4 text-rose-600 shrink-0" />
                <span>{summaryError}</span>
              </div>
            )}

            {/* KPI Summary Cards */}
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
              {/* Card 1: Total Cases */}
              <div className="bg-white rounded border border-[#B8CCE4] p-3.5 shadow-2xs flex flex-col justify-between">
                <div className="flex items-center justify-between text-[#0E3D6B]">
                  <span className="text-[11px] font-bold uppercase tracking-wider text-slate-600">Total Safety Cases</span>
                  <FileText className="w-4 h-4 text-blue-700" />
                </div>
                <div className="mt-2 flex items-baseline justify-between">
                  <span className="text-2xl font-bold font-mono text-[#0E3D6B]">
                    {loadingSummary ? '...' : (dashboardSummary ? dashboardSummary.totalCases : assignedCases.length)}
                  </span>
                  <span className="text-[10px] text-slate-500 font-mono">System-wide</span>
                </div>
                <div className="mt-1 text-[10px] text-slate-500 border-t border-slate-100 pt-1 flex justify-between">
                  <span>Serious Cases:</span>
                  <strong className="text-rose-700 font-mono font-bold">
                    {dashboardSummary?.seriousCases ?? '-'}
                  </strong>
                </div>
              </div>

              {/* Card 2: Assigned Cases */}
              <div className="bg-white rounded border border-[#B8CCE4] p-3.5 shadow-2xs flex flex-col justify-between">
                <div className="flex items-center justify-between text-[#0E3D6B]">
                  <span className="text-[11px] font-bold uppercase tracking-wider text-slate-600">Cases Assigned to Me</span>
                  <UserIcon className="w-4 h-4 text-blue-700" />
                </div>
                <div className="mt-2 flex items-baseline justify-between">
                  <span className="text-2xl font-bold font-mono text-emerald-800">
                    {loadingSummary ? '...' : (dashboardSummary ? dashboardSummary.assignedCases : assignedCases.length)}
                  </span>
                  <span className="text-[10px] text-emerald-700 bg-emerald-50 px-1.5 py-0.2 rounded font-semibold border border-emerald-200">
                    My Queue
                  </span>
                </div>
                <div className="mt-1 text-[10px] text-slate-500 border-t border-slate-100 pt-1 flex justify-between">
                  <span>User Account:</span>
                  <strong className="text-slate-700 font-mono">{user?.username}</strong>
                </div>
              </div>

              {/* Card 3: Awaiting Review */}
              <div className="bg-white rounded border border-[#B8CCE4] p-3.5 shadow-2xs flex flex-col justify-between">
                <div className="flex items-center justify-between text-[#0E3D6B]">
                  <span className="text-[11px] font-bold uppercase tracking-wider text-slate-600">Cases Awaiting Review</span>
                  <Activity className="w-4 h-4 text-amber-600" />
                </div>
                <div className="mt-2 flex items-baseline justify-between">
                  <span className="text-2xl font-bold font-mono text-amber-800">
                    {loadingSummary ? '...' : (dashboardSummary ? dashboardSummary.awaitingReviewCases : 0)}
                  </span>
                  <span className="text-[10px] text-amber-700 bg-amber-50 px-1.5 py-0.2 rounded font-semibold border border-amber-200">
                    Review State
                  </span>
                </div>
                <div className="mt-1 text-[10px] text-slate-500 border-t border-slate-100 pt-1 truncate" title="Quality Review or Medical Review">
                  <span>Assigned to you: </span>
                  <strong className="text-amber-900 font-mono font-bold">
                    {dashboardSummary?.userAwaitingReviewCases ?? 0}
                  </strong>
                  <span className="text-slate-400"> (Quality/Medical)</span>
                </div>
              </div>

              {/* Card 4: Overdue Action Items */}
              <div className="bg-white rounded border border-[#B8CCE4] p-3.5 shadow-2xs flex flex-col justify-between">
                <div className="flex items-center justify-between text-[#0E3D6B]">
                  <span className="text-[11px] font-bold uppercase tracking-wider text-slate-600">Overdue Action Items</span>
                  <Clock className="w-4 h-4 text-slate-400" />
                </div>
                <div className="mt-2 flex items-baseline justify-between">
                  <span className="text-sm font-semibold text-slate-500 font-mono">
                    Not Configured
                  </span>
                  <span className="text-[10px] text-slate-600 bg-slate-100 px-1.5 py-0.5 rounded font-mono border border-slate-300">
                    Planned (Stage 5)
                  </span>
                </div>
                <div className="mt-1 text-[10px] text-slate-500 border-t border-slate-100 pt-1 truncate" title={dashboardSummary?.actionItemsNote || 'Action items module not configured in schema (Planned for Stage 5)'}>
                  <span>Schema Status: </span>
                  <strong className="text-slate-700 font-mono">UNCONFIGURED</strong>
                </div>
              </div>
            </div>

            {/* Section A: Case Quick Launch / Search Case */}
            <div className="argus-panel rounded border border-[#B8CCE4] overflow-hidden">
              <div className="argus-section-header">
                <span className="flex items-center space-x-1.5">
                  <Search className="w-3.5 h-3.5 text-blue-700" />
                  <span>Search Case &bull; Case Quick Launch</span>
                </span>
                <span className="text-[10px] font-normal text-slate-500 font-mono lowercase">
                  [Section A: Argus Home Page Reference]
                </span>
              </div>
              <div className="p-3 bg-white">
                <form onSubmit={handleCaseSearchSubmit} className="flex flex-wrap items-center gap-3">
                  <div className="flex items-center space-x-2 flex-1 min-w-[280px]">
                    <label
                      htmlFor="caseSearchInput"
                      className="text-xs font-semibold text-[#0E3D6B] whitespace-nowrap"
                    >
                      Case Quick Launch:
                    </label>
                    <input
                      id="caseSearchInput"
                      type="text"
                      value={caseSearchInput}
                      onChange={(e) => setCaseSearchInput(e.target.value)}
                      placeholder="Enter Case Number to search (e.g. US-2026-0001)..."
                      className="flex-1 px-2.5 py-1 text-xs border border-[#9BB5D6] rounded-xs bg-[#FDFEFF] text-slate-800 placeholder-slate-400 focus:outline-none focus:border-[#19589D] focus:ring-1 focus:ring-[#19589D]"
                    />
                  </div>
                  <button
                    type="submit"
                    disabled={searchingCases}
                    className="argus-toolbar-btn px-3 py-1 font-semibold flex items-center space-x-1.5 bg-gradient-to-b from-white to-[#E4EDF8] text-[#0E3D6B] disabled:opacity-50"
                  >
                    <Search className={`w-3.5 h-3.5 ${searchingCases ? 'animate-spin' : ''}`} />
                    <span>{searchingCases ? 'Searching...' : 'Quick Search'}</span>
                  </button>
                  <span className="text-[11px] text-slate-500 bg-slate-50 px-2 py-1 rounded border border-slate-200">
                    Format: <code className="font-mono text-slate-700 font-semibold">(Country) Case Number</code>
                  </span>
                </form>

                {searchFeedback && (
                  <div className="mt-2.5 p-2 bg-blue-50 border border-blue-200 rounded text-xs text-blue-900 flex items-start space-x-2">
                    <Info className="w-4 h-4 text-blue-700 flex-shrink-0 mt-0.5" />
                    <div className="flex-1">
                      <span>{searchFeedback}</span>
                    </div>
                    <button
                      onClick={() => setSearchFeedback(null)}
                      className="text-blue-700 hover:text-blue-900 font-bold cursor-pointer text-xs"
                    >
                      &times;
                    </button>
                  </div>
                )}

                {/* Live Search Results Table */}
                {searchResults && searchResults.length > 0 && (
                  <div className="mt-3 border border-[#B8CCE4] rounded overflow-hidden">
                    <div className="bg-[#E4EDF8] px-2.5 py-1 text-xs font-bold text-[#0E3D6B] flex items-center justify-between">
                      <span>Matching Safety Cases ({searchResults.length})</span>
                      <button
                        onClick={() => setSearchResults(null)}
                        className="text-xs text-blue-700 hover:text-blue-900 cursor-pointer font-medium"
                      >
                        Clear Results
                      </button>
                    </div>
                    <div className="overflow-x-auto max-h-48 overflow-y-auto">
                      <table className="argus-table text-xs">
                        <thead>
                          <tr>
                            <th style={{ width: '25%' }}>Country / Case Number</th>
                            <th style={{ width: '20%' }}>Report Type</th>
                            <th style={{ width: '25%' }}>Primary Product</th>
                            <th style={{ width: '15%' }}>Workflow State</th>
                            <th style={{ width: '15%' }}>Action</th>
                          </tr>
                        </thead>
                        <tbody>
                          {searchResults.map((sc) => (
                            <tr
                              key={sc.id}
                              className="hover:bg-blue-50/70 cursor-pointer"
                              onClick={() => handleOpenCase(sc.id)}
                            >
                              <td className="font-semibold text-blue-900">
                                ({sc.countryCode}) {sc.caseNumber}
                                <span className="ml-1 text-[10px] text-slate-500 font-mono">v{sc.versionNumber}</span>
                              </td>
                              <td>{sc.reportType}</td>
                              <td>{sc.primaryProduct}</td>
                              <td>
                                <span className={`px-1.5 py-0.2 rounded text-[10px] font-bold ${
                                  sc.workflowState === 'Draft' ? 'bg-amber-100 text-amber-800' : 'bg-emerald-100 text-emerald-800'
                                }`}>
                                  {sc.workflowState}
                                </span>
                              </td>
                              <td>
                                <button
                                  type="button"
                                  onClick={(e) => { e.stopPropagation(); handleOpenCase(sc.id); }}
                                  className="px-2 py-0.5 text-[11px] font-semibold bg-[#19589D] text-white rounded hover:bg-[#124B8A]"
                                >
                                  Open Case
                                </button>
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  </div>
                )}
              </div>
            </div>

            {/* Section B: Cases Assigned */}
            <div className="argus-panel rounded border border-[#B8CCE4] overflow-hidden">
              <div className="argus-section-header">
                <span className="flex items-center space-x-1.5">
                  <ListOrdered className="w-3.5 h-3.5 text-blue-700" />
                  <span>Cases Assigned</span>
                  <span className="bg-blue-100 text-blue-900 text-[10px] font-bold px-1.5 py-0.2 rounded ml-1">
                    {assignedCases.length}
                  </span>
                </span>
                <div className="flex items-center space-x-2">
                  <button
                    onClick={handleNewCaseClick}
                    className="argus-toolbar-btn px-2 py-0.5 text-[11px] font-semibold flex items-center space-x-1 bg-white text-[#0E3D6B]"
                  >
                    <Plus className="w-3 h-3 text-emerald-600" />
                    <span>New Case</span>
                  </button>
                  <button
                    onClick={() => {
                      fetchAssignedCases();
                      fetchDashboardSummary();
                    }}
                    disabled={loadingAssignedCases}
                    className="argus-toolbar-btn px-2 py-0.5 text-[11px] font-semibold flex items-center space-x-1 bg-white text-[#0E3D6B]"
                    title="Refresh assigned cases from PostgreSQL"
                  >
                    <RefreshCw className={`w-3 h-3 ${loadingAssignedCases ? 'animate-spin text-blue-600' : ''}`} />
                    <span>Refresh</span>
                  </button>
                </div>
              </div>

              {assignedCasesError && (
                <div className="p-2.5 bg-rose-50 border-b border-rose-200 text-xs text-rose-800 flex items-center space-x-2">
                  <AlertCircle className="w-4 h-4 text-rose-600 shrink-0" />
                  <span>{assignedCasesError}</span>
                </div>
              )}

              {loadingAssignedCases ? (
                <div className="p-6 text-center text-xs text-slate-500 flex items-center justify-center space-x-2 bg-slate-50">
                  <RefreshCw className="w-4 h-4 animate-spin text-blue-600" />
                  <span>Loading assigned safety cases from PostgreSQL...</span>
                </div>
              ) : assignedCases.length === 0 ? (
                <div className="text-center py-6 text-slate-500 bg-slate-50">
                  <div className="flex flex-col items-center justify-center space-y-1">
                    <AlertCircle className="w-5 h-5 text-slate-400" />
                    <span className="font-medium text-xs">No assigned cases available yet.</span>
                    <span className="text-[11px] text-slate-400">
                      Click "New Case" above or press <kbd className="font-mono bg-slate-200 px-1 rounded text-slate-700">Ctrl+Alt+N</kbd> to enter an adverse event case.
                    </span>
                  </div>
                </div>
              ) : (
                <div className="overflow-x-auto max-h-72 overflow-y-auto">
                  <table className="argus-table">
                    <thead>
                      <tr>
                        <th style={{ width: '22%' }}>Country / Case Number</th>
                        <th style={{ width: '18%' }}>Report Type</th>
                        <th style={{ width: '22%' }}>Product</th>
                        <th style={{ width: '18%' }}>Workflow State</th>
                        <th style={{ width: '20%' }}>Event</th>
                      </tr>
                    </thead>
                    <tbody>
                      {assignedCases.map((c) => (
                        <tr
                          key={c.id}
                          onClick={() => handleOpenCase(c.id)}
                          className="hover:bg-blue-50/70 cursor-pointer transition-colors"
                          title={`Click to open Case ${c.caseNumber} in Initial Case Entry`}
                        >
                          <td className="font-semibold text-blue-900 whitespace-nowrap">
                            <span className="text-slate-500 font-normal">({c.countryCode})</span>{' '}
                            <span className="underline decoration-blue-300">{c.caseNumber}</span>
                            <span className="ml-1 text-[10px] text-slate-500 font-mono">v{c.versionNumber}</span>
                          </td>
                          <td className="whitespace-nowrap">{c.reportType}</td>
                          <td className="font-medium text-slate-800 truncate max-w-[200px]" title={c.primaryProduct}>
                            {c.primaryProduct}
                          </td>
                          <td>
                            <span
                              className={`px-1.5 py-0.2 rounded text-[10px] font-bold ${
                                c.workflowState === 'Draft'
                                  ? 'bg-amber-100 text-amber-800 border border-amber-300'
                                  : c.workflowState === 'Quality Review'
                                  ? 'bg-blue-100 text-blue-800 border border-blue-300'
                                  : c.workflowState === 'Medical Review'
                                  ? 'bg-purple-100 text-purple-800 border border-purple-300'
                                  : 'bg-emerald-100 text-emerald-800 border border-emerald-300'
                              }`}
                            >
                              {c.workflowState}
                            </span>
                          </td>
                          <td className="text-slate-700 truncate max-w-[200px]" title={c.primaryEvent}>
                            {c.primaryEvent}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>

            {/* Recent Case Activity Feed */}
            <div className="argus-panel rounded border border-[#B8CCE4] overflow-hidden bg-white">
              <div className="argus-section-header flex items-center justify-between">
                <span className="flex items-center space-x-1.5">
                  <Activity className="w-3.5 h-3.5 text-blue-700" />
                  <span>Recent Case Activity &bull; Live Audit Events</span>
                </span>
                <span className="text-[10px] font-normal text-slate-500 font-mono lowercase">
                  [audit_events: CASE_CREATED / CASE_UPDATED]
                </span>
              </div>
              <div className="p-3">
                {loadingActivity ? (
                  <div className="p-4 text-center text-xs text-slate-500 flex items-center justify-center space-x-2">
                    <RefreshCw className="w-4 h-4 animate-spin text-blue-600" />
                    <span>Loading recent activity feed...</span>
                  </div>
                ) : recentActivity.length === 0 ? (
                  <div className="p-4 text-center text-xs text-slate-500 bg-slate-50 border border-dashed border-slate-300 rounded">
                    No recent case activity recorded in the database yet.
                  </div>
                ) : (
                  <div className="space-y-1.5 max-h-48 overflow-y-auto">
                    {recentActivity.map((act) => (
                      <div key={act.id} className="flex items-center justify-between text-xs p-2 bg-slate-50 rounded border border-slate-200 hover:bg-blue-50/40 transition">
                        <div className="flex items-center space-x-2 min-w-0">
                          <span className={`px-1.5 py-0.2 rounded text-[10px] font-bold font-mono ${
                            act.eventType === 'CASE_CREATED' ? 'bg-emerald-100 text-emerald-800 border border-emerald-300' : 'bg-blue-100 text-blue-800 border border-blue-300'
                          }`}>
                            {act.eventType}
                          </span>
                          <span className="text-slate-700 font-medium truncate" title={act.details}>
                            {act.details}
                          </span>
                        </div>
                        <div className="flex items-center space-x-2 shrink-0 text-[11px] text-slate-500 font-mono">
                          <span className="text-slate-700 font-semibold">{act.username}</span>
                          <span>&bull;</span>
                          <span>{new Date(act.createdAt).toLocaleTimeString()}</span>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>

            {/* Sections C & D: database-backed personal interactions */}
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
              {/* Section C: Contact Log Entries */}
              <div className="argus-panel rounded border border-[#B8CCE4] overflow-hidden">
                <div className="argus-section-header">
                  <span className="flex items-center space-x-1.5">
                    <MessageSquare className="w-3.5 h-3.5 text-blue-700" />
                    <span>Contact Log Entries</span>
                  </span>
                  <span className="text-[10px] font-normal text-slate-500 font-mono">
                    [Section C: Argus Home Page Reference]
                  </span>
                </div>
                <div className="overflow-x-auto">
                  <table className="argus-table">
                    <thead>
                      <tr>
                        <th style={{ width: '35%' }}>Country / Case Number</th>
                        <th style={{ width: '25%' }}>Contact Date</th>
                        <th style={{ width: '40%' }}>Description</th>
                      </tr>
                    </thead>
                    <tbody>
                      {contactLogs.length === 0 ? (
                        <tr><td colSpan={3} className="text-center py-5 text-slate-500 bg-slate-50">No contact log entries recorded yet.</td></tr>
                      ) : contactLogs.map(log => (
                        <tr key={log.id}><td>{log.caseNumber}</td><td>{new Date(log.contactAt).toLocaleString()}</td><td>{log.description}</td></tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>

              {/* Section D: Action Item Entries */}
              <div className="argus-panel rounded border border-[#B8CCE4] overflow-hidden">
                <div className="argus-section-header">
                  <span className="flex items-center space-x-1.5">
                    <CheckSquare2 className="w-3.5 h-3.5 text-blue-700" />
                    <span>Action Item Entries</span>
                  </span>
                  <span className="text-[10px] font-normal text-slate-500 font-mono">
                    [Section D: Argus Home Page Reference]
                  </span>
                </div>
                <div className="overflow-x-auto">
                  <table className="argus-table">
                    <thead>
                      <tr>
                        <th style={{ width: '35%' }}>Country / Case Number</th>
                        <th style={{ width: '25%' }}>Due On</th>
                        <th style={{ width: '40%' }}>Description</th>
                      </tr>
                    </thead>
                    <tbody>
                      {actionItems.length === 0 ? (
                        <tr><td colSpan={3} className="text-center py-5 text-slate-500 bg-slate-50">No pending action items.</td></tr>
                      ) : actionItems.map(item => (
                        <tr key={item.id}><td>{item.caseNumber}</td><td className={item.overdue ? 'text-rose-700 font-bold' : ''}>{new Date(item.dueAt).toLocaleString()}</td><td>{item.description}</td></tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>

            {/* Section E: Live Backend Status & Subsystem Health */}
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              {/* Box 1: Verified Database Connection */}
              <div className="argus-panel rounded border border-[#B8CCE4] p-3">
                <div className="flex items-center justify-between pb-2 border-b border-slate-200">
                  <span className="text-xs font-bold text-[#0E3D6B] flex items-center space-x-1.5">
                    <Database className="w-4 h-4 text-blue-700" />
                    <span>PostgreSQL Database</span>
                  </span>
                  <span className="text-[10px] bg-emerald-100 text-emerald-800 font-bold px-1.5 py-0.5 rounded border border-emerald-300">
                    {dbHealth?.database || 'UP'}
                  </span>
                </div>
                <div className="mt-2 space-y-1 text-xs text-slate-600">
                  <div className="flex justify-between">
                    <span className="text-slate-500">Database Name:</span>
                    <span className="font-mono font-semibold text-slate-800">
                      {dbHealth?.databaseName || 'pharmasafe_db'}
                    </span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-slate-500">Connected User:</span>
                    <span className="font-mono font-semibold text-slate-800">
                      {dbHealth?.databaseUser || 'pharmasafe_user'}
                    </span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-slate-500">Database Version:</span>
                    <span className="font-mono text-slate-800">
                      {dbHealth?.databaseProduct} {dbHealth?.databaseVersion || '18.4'}
                    </span>
                  </div>
                </div>
              </div>

              {/* Box 2: Session & Security Compliance */}
              <div className="argus-panel rounded border border-[#B8CCE4] p-3">
                <div className="flex items-center justify-between pb-2 border-b border-slate-200">
                  <span className="text-xs font-bold text-[#0E3D6B] flex items-center space-x-1.5">
                    <Lock className="w-4 h-4 text-indigo-700" />
                    <span>Session Security</span>
                  </span>
                  <span className="text-[10px] bg-sky-100 text-sky-800 font-bold px-1.5 py-0.5 rounded border border-sky-300">
                    ACTIVE
                  </span>
                </div>
                <div className="mt-2 space-y-1 text-xs text-slate-600">
                  <div className="flex justify-between">
                    <span className="text-slate-500">Session Cookie:</span>
                    <span className="font-mono font-semibold text-slate-800">JSESSIONID (HttpOnly)</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-slate-500">CSRF Token:</span>
                    <span className="font-mono font-semibold text-slate-800">X-XSRF-TOKEN (Cookie)</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-slate-500">Lockout Defense:</span>
                    <span className="font-semibold text-amber-700">3-Attempt Pessimistic Lock</span>
                  </div>
                </div>
              </div>

              {/* Box 3: Enterprise Case Management Roadmap */}
              <div className="argus-panel rounded border border-[#B8CCE4] p-3">
                <div className="flex items-center justify-between pb-2 border-b border-slate-200">
                  <span className="text-xs font-bold text-[#0E3D6B] flex items-center space-x-1.5">
                    <Activity className="w-4 h-4 text-emerald-700" />
                    <span>PV Case Architecture</span>
                  </span>
                  <span className="text-[10px] bg-slate-100 text-slate-800 font-bold px-1.5 py-0.5 rounded border border-slate-300">
                    STAGE 3
                  </span>
                </div>
                <div className="mt-2 space-y-1 text-xs text-slate-600">
                  <div className="flex justify-between">
                    <span className="text-slate-500">Next Phase:</span>
                    <span className="font-semibold text-[#0E3D6B]">Stage 4 (Initial Case Entry)</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-slate-500">Schema Design:</span>
                    <span className="font-mono text-emerald-800 font-semibold">docs/database-design.md</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-slate-500">Compliance:</span>
                    <span className="font-semibold text-slate-700">ICH E2B(R3), 21 CFR Part 11</span>
                  </div>
                </div>
              </div>
            </div>

            {/* Section F: Live PostgreSQL Audit Trail (when ROLE_ADMIN) */}
            {user?.role === 'ROLE_ADMIN' && (
              <div className="argus-panel rounded border border-[#B8CCE4] overflow-hidden">
                <div className="argus-section-header">
                  <span className="flex items-center space-x-2">
                    <FileText className="w-3.5 h-3.5 text-blue-700" />
                    <span>AUTHENTICATION AUDIT TRAIL &bull; LIVE POSTGRESQL (audit_events)</span>
                  </span>
                  <div className="flex items-center space-x-2">
                    {adminStatus?.totalAuditEvents !== undefined && (
                      <span className="text-[10px] bg-white/70 px-2 py-0.5 rounded border border-[#B8CCE4] text-[#0E3D6B] font-mono">
                        Total Audit Events: <strong>{adminStatus.totalAuditEvents}</strong>
                      </span>
                    )}
                    <button
                      onClick={fetchAuditLogs}
                      disabled={loadingAudit}
                      className="argus-toolbar-btn px-2 py-0.5 text-[10px] font-semibold flex items-center space-x-1"
                      title="Reload latest audit log entries from PostgreSQL"
                    >
                      <RefreshCw className={`w-3 h-3 ${loadingAudit ? 'animate-spin' : ''}`} />
                      <span>Refresh</span>
                    </button>
                  </div>
                </div>

                <div className="p-3 bg-white">
                  {auditError && (
                    <div className="p-2.5 bg-amber-50 border border-amber-200 rounded text-xs text-amber-800 mb-3">
                      {auditError}
                    </div>
                  )}

                  {loadingAudit ? (
                    <div className="p-6 text-center text-xs text-slate-500 flex items-center justify-center space-x-2">
                      <RefreshCw className="w-4 h-4 animate-spin text-blue-600" />
                      <span>Fetching real-time audit event rows from PostgreSQL...</span>
                    </div>
                  ) : auditLogs.length === 0 ? (
                    <div className="p-6 text-center text-xs text-slate-500">
                      No audit events recorded yet in the PostgreSQL database.
                    </div>
                  ) : (
                    <div className="overflow-x-auto max-h-72 overflow-y-auto border border-[#B8CCE4] rounded w-full max-w-full">
                      <table className="argus-table text-xs w-full">
                        <thead>
                          <tr>
                            <th style={{ width: '8%' }}>ID</th>
                            <th style={{ width: '22%' }}>Event Type</th>
                            <th style={{ width: '15%' }}>Account</th>
                            <th style={{ width: '15%' }}>Origin IP</th>
                            <th style={{ width: '10%' }}>Status</th>
                            <th style={{ width: '15%' }}>Details</th>
                            <th style={{ width: '15%' }}>Timestamp</th>
                          </tr>
                        </thead>
                        <tbody>
                          {auditLogs.map((log) => (
                            <tr key={log.id}>
                              <td className="font-mono text-slate-500 whitespace-nowrap">#{log.id}</td>
                              <td className="font-semibold text-slate-800 whitespace-nowrap">{log.eventType}</td>
                              <td className="font-mono text-slate-900 whitespace-nowrap">{log.username || '-'}</td>
                              <td className="font-mono text-slate-600 whitespace-nowrap">{log.ipAddress || 'local'}</td>
                              <td className="whitespace-nowrap">
                                <span
                                  className={`px-1.5 py-0.2 rounded text-[10px] font-bold ${
                                    log.status === 'SUCCESS'
                                      ? 'bg-emerald-100 text-emerald-800 border border-emerald-300'
                                      : log.status === 'WARNING'
                                      ? 'bg-amber-100 text-amber-800 border border-amber-300'
                                      : 'bg-rose-100 text-rose-800 border border-rose-300'
                                  }`}
                                >
                                  {log.status}
                                </span>
                              </td>
                              <td className="text-slate-600 max-w-[200px] truncate" title={log.details || ''}>
                                {log.details || '-'}
                              </td>
                              <td className="font-mono text-slate-500 text-[11px] whitespace-nowrap">
                                {new Date(log.createdAt).toLocaleString()}
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  )}
                </div>
              </div>
            )}
          </div>
        )}

        {/* VIEW 2: INTERACTIVE PAGINATED WORKLIST / QUEUE VIEW */}
        {activeTab === 'worklist' && (
          <div className="argus-panel rounded border border-[#B8CCE4] p-4 space-y-3 bg-white">
            <div className="flex flex-wrap items-center justify-between border-b border-slate-200 pb-2.5 gap-2">
              <div className="flex items-center space-x-2">
                <ListOrdered className="w-4 h-4 text-blue-700" />
                <span className="text-sm font-bold text-[#0E3D6B]">Worklist / User Case Queue</span>
                <span className="text-xs bg-blue-100 text-blue-900 px-2 py-0.5 rounded font-mono font-semibold">
                  {worklistData ? `${worklistData.totalElements} Total Cases` : 'Loading...'}
                </span>
              </div>
              <div className="flex items-center space-x-2">
                <button
                  onClick={() => fetchPaginatedWorklist(worklistPage)}
                  disabled={loadingWorklist}
                  className="argus-toolbar-btn px-2.5 py-1 text-xs font-semibold flex items-center space-x-1 bg-white text-[#0E3D6B]"
                >
                  <RefreshCw className={`w-3 h-3 ${loadingWorklist ? 'animate-spin' : ''}`} />
                  <span>Refresh Queue</span>
                </button>
                <button
                  onClick={handleNewCaseClick}
                  className="px-2.5 py-1 bg-[#19589D] hover:bg-[#124B8A] text-white rounded font-semibold text-xs flex items-center space-x-1 cursor-pointer shadow-xs"
                >
                  <Plus className="w-3.5 h-3.5" />
                  <span>Create Case</span>
                </button>
              </div>
            </div>

            {/* Filter and Sorting Controls */}
            <div className="bg-[#F4F8FC] p-3 rounded border border-[#C8D9ED] space-y-2.5 text-xs">
              <div className="flex flex-wrap items-center justify-between gap-3">
                {/* Search Bar */}
                <div className="flex items-center space-x-2 flex-1 min-w-[240px]">
                  <Search className="w-3.5 h-3.5 text-slate-500" />
                  <input
                    type="text"
                    value={worklistSearch}
                    onChange={(e) => setWorklistSearch(e.target.value)}
                    onKeyDown={(e) => { if (e.key === 'Enter') { setWorklistPage(0); fetchPaginatedWorklist(0); } }}
                    placeholder="Search by case number or narrative..."
                    className="flex-1 px-2.5 py-1 text-xs border border-[#9BB5D6] rounded bg-white text-slate-800 placeholder-slate-400 focus:outline-none focus:border-[#19589D]"
                  />
                  <button
                    onClick={() => { setWorklistPage(0); fetchPaginatedWorklist(0); }}
                    className="argus-toolbar-btn px-2.5 py-1 text-xs font-medium bg-white text-[#0E3D6B]"
                  >
                    Filter
                  </button>
                </div>

                {/* Scope Toggle: Assigned Only vs All Cases */}
                <div className="flex items-center space-x-1 bg-white p-1 rounded border border-[#B8CCE4]">
                  <button
                    onClick={() => { setWorklistAssignedOnly(false); setWorklistPage(0); setTimeout(() => fetchPaginatedWorklist(0), 10); }}
                    className={`px-2.5 py-0.5 rounded text-[11px] font-semibold transition cursor-pointer ${
                      !worklistAssignedOnly ? 'bg-[#19589D] text-white' : 'text-slate-600 hover:text-slate-900'
                    }`}
                  >
                    All Cases
                  </button>
                  <button
                    onClick={() => { setWorklistAssignedOnly(true); setWorklistPage(0); setTimeout(() => fetchPaginatedWorklist(0), 10); }}
                    className={`px-2.5 py-0.5 rounded text-[11px] font-semibold transition cursor-pointer ${
                      worklistAssignedOnly ? 'bg-[#19589D] text-white' : 'text-slate-600 hover:text-slate-900'
                    }`}
                  >
                    My Cases Only
                  </button>
                </div>
              </div>

              {/* State and Priority Selectors */}
              <div className="flex flex-wrap items-center gap-3 pt-1 border-t border-[#DFEAF5]">
                <div className="flex items-center space-x-1.5">
                  <Filter className="w-3 h-3 text-slate-500" />
                  <span className="text-slate-600 font-medium">State:</span>
                  <select
                    value={worklistWorkflowFilter}
                    onChange={(e) => {
                      setWorklistWorkflowFilter(e.target.value);
                      setWorklistPage(0);
                    }}
                    className="px-2 py-0.5 rounded border border-[#B8CCE4] bg-white text-xs text-slate-700"
                  >
                    <option value="">All States</option>
                    <option value="Data Entry">Data Entry</option>
                    <option value="Quality Review">Quality Review</option>
                    <option value="Medical Review">Medical Review</option>
                    <option value="Draft">Draft</option>
                    <option value="Closed">Closed</option>
                  </select>
                </div>

                <div className="flex items-center space-x-1.5">
                  <span className="text-slate-600 font-medium">Priority:</span>
                  <select
                    value={worklistPriorityFilter}
                    onChange={(e) => {
                      setWorklistPriorityFilter(e.target.value);
                      setWorklistPage(0);
                    }}
                    className="px-2 py-0.5 rounded border border-[#B8CCE4] bg-white text-xs text-slate-700"
                  >
                    <option value="">All Priorities</option>
                    <option value="Routine">Routine</option>
                    <option value="Priority">Priority</option>
                    <option value="Expedited">Expedited</option>
                  </select>
                </div>

                <div className="flex items-center space-x-1.5">
                  <ArrowUpDown className="w-3 h-3 text-slate-500" />
                  <span className="text-slate-600 font-medium">Sort:</span>
                  <select
                    value={worklistSortBy}
                    onChange={(e) => {
                      setWorklistSortBy(e.target.value);
                    }}
                    className="px-2 py-0.5 rounded border border-[#B8CCE4] bg-white text-xs text-slate-700"
                  >
                    <option value="createdAt">Date Created</option>
                    <option value="updatedAt">Last Updated</option>
                    <option value="caseNumber">Case Number</option>
                    <option value="priority">Priority</option>
                    <option value="workflowState">Workflow State</option>
                  </select>
                  <select
                    value={worklistDirection}
                    onChange={(e) => setWorklistDirection(e.target.value as 'asc' | 'desc')}
                    className="px-2 py-0.5 rounded border border-[#B8CCE4] bg-white text-xs text-slate-700"
                  >
                    <option value="desc">Desc</option>
                    <option value="asc">Asc</option>
                  </select>
                </div>

                <button
                  onClick={() => fetchPaginatedWorklist(0)}
                  className="px-2.5 py-0.5 bg-[#0E3D6B] text-white rounded text-[11px] font-semibold cursor-pointer ml-auto"
                >
                  Apply Filters
                </button>
              </div>
            </div>

            {worklistError && (
              <div className="p-2.5 bg-rose-50 border border-rose-200 text-xs text-rose-800 rounded flex items-center space-x-2">
                <AlertCircle className="w-4 h-4 text-rose-600 shrink-0" />
                <span>{worklistError}</span>
              </div>
            )}

            {/* Worklist Case Table */}
            {loadingWorklist ? (
              <div className="p-8 text-center text-xs text-slate-500 flex items-center justify-center space-x-2 bg-slate-50 rounded">
                <RefreshCw className="w-4 h-4 animate-spin text-blue-600" />
                <span>Querying paginated safety cases from PostgreSQL...</span>
              </div>
            ) : (!worklistData || worklistData.content.length === 0) ? (
              <div className="p-8 text-center text-xs text-slate-600 bg-slate-50 border border-dashed border-slate-300 rounded">
                <ListOrdered className="w-8 h-8 text-slate-400 mx-auto mb-2" />
                <p className="font-semibold text-slate-700">No safety cases match the selected worklist filters.</p>
                <p className="text-slate-500 text-[11px] mt-1 max-w-md mx-auto">
                  Adjust your search keyword or workflow filter above, or create a new safety case.
                </p>
                <button
                  onClick={handleNewCaseClick}
                  className="mt-3 px-3 py-1 bg-[#19589D] text-white rounded font-semibold text-xs inline-flex items-center space-x-1 cursor-pointer"
                >
                  <Plus className="w-3.5 h-3.5" />
                  <span>Create Safety Case</span>
                </button>
              </div>
            ) : (
              <div className="overflow-x-auto border border-[#B8CCE4] rounded">
                <table className="argus-table text-xs">
                  <thead>
                    <tr>
                      <th style={{ width: '18%' }}>Country / Case Number</th>
                      <th style={{ width: '12%' }}>Report Type</th>
                      <th style={{ width: '18%' }}>Primary Product</th>
                      <th style={{ width: '18%' }}>Primary Event</th>
                      <th style={{ width: '12%' }}>Workflow State</th>
                      <th style={{ width: '10%' }}>Priority</th>
                      <th style={{ width: '12%' }}>Assigned User</th>
                      <th style={{ width: '10%' }}>Action</th>
                    </tr>
                  </thead>
                  <tbody>
                    {worklistData.content.map((c) => (
                      <tr key={c.id} className="hover:bg-blue-50/70 cursor-pointer" onClick={() => handleOpenCase(c.id)}>
                        <td className="font-semibold text-blue-900 whitespace-nowrap">
                          ({c.countryCode}) {c.caseNumber}{' '}
                          <span className="text-[10px] text-slate-500 font-mono">v{c.versionNumber}</span>
                        </td>
                        <td className="whitespace-nowrap">{c.reportType}</td>
                        <td className="font-medium text-slate-800 truncate max-w-[180px]" title={c.primaryProduct}>
                          {c.primaryProduct}
                        </td>
                        <td className="text-slate-700 truncate max-w-[180px]" title={c.primaryEvent}>
                          {c.primaryEvent}
                        </td>
                        <td>
                          <span className={`px-1.5 py-0.2 rounded text-[10px] font-bold ${
                            c.workflowState === 'Draft' ? 'bg-amber-100 text-amber-800 border border-amber-300' :
                            c.workflowState === 'Quality Review' ? 'bg-blue-100 text-blue-800 border border-blue-300' :
                            c.workflowState === 'Medical Review' ? 'bg-purple-100 text-purple-800 border border-purple-300' :
                            'bg-emerald-100 text-emerald-800 border border-emerald-300'
                          }`}>
                            {c.workflowState}
                          </span>
                        </td>
                        <td>
                          <span className={`px-1.5 py-0.2 rounded text-[10px] font-bold ${
                            c.priority === 'Expedited' ? 'bg-rose-100 text-rose-800 border border-rose-300' :
                            c.priority === 'Priority' ? 'bg-amber-100 text-amber-800 border border-amber-300' :
                            'bg-slate-100 text-slate-700 border border-slate-300'
                          }`}>
                            {c.priority}
                          </span>
                        </td>
                        <td className="font-mono text-slate-700">{c.assignedToUsername || '-'}</td>
                        <td>
                          <button
                            type="button"
                            onClick={(e) => { e.stopPropagation(); handleOpenCase(c.id); }}
                            className="px-2 py-0.5 text-[11px] font-semibold bg-[#19589D] text-white rounded hover:bg-[#124B8A]"
                          >
                            Open Case
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}

            {/* Pagination Controls */}
            {worklistData && worklistData.totalPages > 1 && (
              <div className="flex flex-wrap items-center justify-between gap-2 pt-2 border-t border-slate-200 text-xs text-slate-600">
                <div className="flex items-center space-x-2">
                  <span>Showing page <strong>{worklistData.number + 1}</strong> of <strong>{worklistData.totalPages}</strong> ({worklistData.totalElements} cases)</span>
                  <select
                    value={worklistSize}
                    onChange={(e) => {
                      const newSize = Number(e.target.value);
                      setWorklistSize(newSize);
                      setWorklistPage(0);
                    }}
                    className="px-2 py-0.5 rounded border border-[#B8CCE4] bg-white text-xs"
                  >
                    <option value="5">5 per page</option>
                    <option value="10">10 per page</option>
                    <option value="20">20 per page</option>
                  </select>
                </div>

                <div className="flex items-center space-x-1">
                  <button
                    onClick={() => {
                      const prevPage = Math.max(0, worklistPage - 1);
                      setWorklistPage(prevPage);
                      fetchPaginatedWorklist(prevPage);
                    }}
                    disabled={worklistData.first}
                    className="argus-toolbar-btn px-2.5 py-1 text-xs font-semibold flex items-center space-x-1 disabled:opacity-40"
                  >
                    <ChevronLeft className="w-3.5 h-3.5" />
                    <span>Previous</span>
                  </button>

                  <span className="px-2.5 py-1 font-mono font-bold text-[#0E3D6B] bg-[#E8F1FA] rounded border border-[#B8CCE4]">
                    {worklistData.number + 1}
                  </span>

                  <button
                    onClick={() => {
                      const nextPage = Math.min(worklistData.totalPages - 1, worklistPage + 1);
                      setWorklistPage(nextPage);
                      fetchPaginatedWorklist(nextPage);
                    }}
                    disabled={worklistData.last}
                    className="argus-toolbar-btn px-2.5 py-1 text-xs font-semibold flex items-center space-x-1 disabled:opacity-40"
                  >
                    <span>Next</span>
                    <ChevronRight className="w-3.5 h-3.5" />
                  </button>
                </div>
              </div>
            )}
          </div>
        )}

        {/* VIEW 3: CASE ENTRY WORKSPACE */}
        {activeTab === 'case-entry' && (
          <CaseEntryForm
            initialCase={currentEditingCase}
            onSaveSuccess={handleSaveSuccess}
            onCancel={handleCloseCase}
            saveTrigger={quickLaunchSaveTrigger}
          />
        )}

        {/* VIEW 4: CONTACT LOG VIEW */}
        {activeTab === 'contact-log' && (
          <div className="argus-panel rounded border border-[#B8CCE4] p-4 space-y-3 bg-white">
            <div className="flex items-center justify-between border-b border-slate-200 pb-2">
              <span className="text-sm font-bold text-[#0E3D6B] flex items-center space-x-2">
                <MessageSquare className="w-4 h-4 text-blue-700" />
                <span>Contact Log Management</span>
              </span>
              <span className="text-xs font-mono text-slate-500">Argus Reference Section C</span>
            </div>
            <p className="text-xs text-slate-600">
              Tracks all outbound communications (telephone, email, queries) with healthcare professionals and consumers regarding adverse event follow-up inquiries.
            </p>
            <p className="text-xs text-slate-600">Personal records are shown when you created them or are responsible for them.</p>
            {interactionError && <p className="text-xs text-rose-700">{interactionError}</p>}
            <div className="grid gap-2 md:grid-cols-4 text-xs">
              <input value={interactionCaseId} onChange={e => setInteractionCaseId(e.target.value)} placeholder="Case ID" className="border p-1 rounded" />
              <select value={contactType} onChange={e => setContactType(e.target.value)} className="border p-1 rounded"><option>Email</option><option>Telephone</option><option>Portal</option><option>Letter</option><option>Other</option></select>
              <input value={contactDescription} onChange={e => setContactDescription(e.target.value)} placeholder="Description" className="border p-1 rounded md:col-span-2" />
              <button type="button" onClick={() => createInteraction('contact')} className="argus-toolbar-btn px-2 py-1 text-xs">Save contact log</button>
            </div>
            <div className="flex gap-2"><button type="button" onClick={fetchInteractions} className="argus-toolbar-btn px-2 py-1 text-xs">Refresh</button>{interactionLoading && <span className="text-xs">Loading...</span>}</div>
            {contactLogs.length === 0 ? <div className="p-4 text-center text-xs text-slate-500 bg-slate-50">No personal contact logs.</div> : <div className="space-y-1">{contactLogs.map(log => <div key={log.id} className="border p-2 text-xs"><b>{log.caseNumber}</b> · {log.contactType} · {new Date(log.contactAt).toLocaleString()}<div>{log.description}</div></div>)}</div>}
          </div>
        )}

        {/* VIEW 5: ACTION ITEMS VIEW */}
        {activeTab === 'action-items' && (
          <div className="argus-panel rounded border border-[#B8CCE4] p-4 space-y-3 bg-white">
            <div className="flex items-center justify-between border-b border-slate-200 pb-2">
              <span className="text-sm font-bold text-[#0E3D6B] flex items-center space-x-2">
                <CheckSquare2 className="w-4 h-4 text-blue-700" />
                <span>Action Items &amp; Case Tasks</span>
              </span>
              <span className="text-xs font-mono text-slate-500">Argus Reference Section D</span>
            </div>
            <p className="text-xs text-slate-600">
              Assigns action items, queries, targeted questionnaires, and medical review milestones with due dates.
            </p>
            <p className="text-xs text-slate-600">Overdue is derived from due date and non-completed/non-cancelled status; it is not stored as a stale flag.</p>
            {interactionError && <p className="text-xs text-rose-700">{interactionError}</p>}
            <div className="grid gap-2 md:grid-cols-4 text-xs">
              <input value={interactionCaseId} onChange={e => setInteractionCaseId(e.target.value)} placeholder="Case ID" className="border p-1 rounded" />
              <input value={actionDescription} onChange={e => setActionDescription(e.target.value)} placeholder="Description" className="border p-1 rounded" />
              <input type="datetime-local" value={actionDueAt} onChange={e => setActionDueAt(e.target.value)} className="border p-1 rounded" />
              <button type="button" onClick={() => createInteraction('action')} className="argus-toolbar-btn px-2 py-1 text-xs">Save action item</button>
            </div>
            <div className="flex gap-2"><button type="button" onClick={fetchInteractions} className="argus-toolbar-btn px-2 py-1 text-xs">Refresh</button><button type="button" onClick={() => { setShowOverdueOnly(true); }} className="argus-toolbar-btn px-2 py-1 text-xs">Overdue only</button></div>
            {actionItems.length === 0 ? <div className="p-4 text-center text-xs text-slate-500 bg-slate-50">No matching action items.</div> : <div className="space-y-1">{actionItems.map(item => <div key={item.id} className={`border p-2 text-xs ${item.overdue ? 'border-rose-300 bg-rose-50' : ''}`}><b>{item.caseNumber}</b> · due {new Date(item.dueAt).toLocaleString()}<div>{item.description} ({item.status}) <button type="button" onClick={() => completeActionItem(item.id)} className="underline">Complete</button></div></div>)}</div>}
          </div>
        )}

        {/* VIEW 6: REPORTS VIEW */}
        {activeTab === 'reports' && (
          <div className="argus-panel rounded border border-[#B8CCE4] p-4 space-y-3 bg-white">
            <div className="flex items-center justify-between border-b border-slate-200 pb-2">
              <span className="text-sm font-bold text-[#0E3D6B] flex items-center space-x-2">
                <FileText className="w-4 h-4 text-blue-700" />
                <span>Regulatory Reports &amp; Expedited Generation (Stage 5/6)</span>
              </span>
              <span className="text-xs font-mono bg-slate-100 text-slate-800 px-2 py-0.5 rounded border border-slate-300">
                Scheduled: Stage 6
              </span>
            </div>
            <p className="text-xs text-slate-600">
              Enables generation of electronic regulatory submissions including ICH E2B(R3) XML transmissions, CIOMS I forms, and FDA MedWatch 3500A documents.
            </p>
          </div>
        )}

        {/* VIEW 7: ADMINISTRATION VIEW (FOR ADMINS) */}
        {activeTab === 'admin' && user?.role === 'ROLE_ADMIN' && (
          <div className="space-y-4">
            <div className="argus-panel rounded border border-[#B8CCE4] p-4 bg-white">
              <div className="flex items-center justify-between border-b border-slate-200 pb-2 mb-3">
                <span className="text-sm font-bold text-[#0E3D6B] flex items-center space-x-2">
                  <SlidersHorizontal className="w-4 h-4 text-blue-700" />
                  <span>Argus Console &bull; Security &amp; Audit Administration</span>
                </span>
                <span className="text-xs text-emerald-800 font-semibold bg-emerald-50 px-2 py-0.5 rounded border border-emerald-200">
                  System: {adminStatus?.systemStatus || 'OPERATIONAL'}
                </span>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-3 gap-3 text-xs mb-4">
                <div className="p-3 bg-slate-50 rounded border border-slate-200 flex justify-between items-center">
                  <span className="text-slate-600">Registered Users (PostgreSQL):</span>
                  <span className="font-bold text-slate-800 font-mono text-sm">
                    {adminStatus?.totalUsers ?? 1}
                  </span>
                </div>
                <div className="p-3 bg-slate-50 rounded border border-slate-200 flex justify-between items-center">
                  <span className="text-slate-600">Total Audit Events (audit_events):</span>
                  <span className="font-bold text-slate-800 font-mono text-sm">
                    {adminStatus?.totalAuditEvents ?? auditLogs.length}
                  </span>
                </div>
                <div className="p-3 bg-slate-50 rounded border border-slate-200 flex justify-between items-center">
                  <span className="text-slate-600">PostgreSQL Schema Migrations:</span>
                  <span className="font-bold text-emerald-700 font-mono text-sm">V1, V2 &amp; V3 Applied</span>
                </div>
              </div>

              {/* Comprehensive Audit Table */}
              <div className="border border-[#B8CCE4] rounded overflow-hidden">
                <div className="bg-[#E4EDF8] px-3 py-1.5 border-b border-[#B8CCE4] flex justify-between items-center">
                  <span className="font-bold text-xs text-[#0E3D6B]">IMMUTABLE AUDIT TRAIL LOGS</span>
                  <button
                    onClick={fetchAuditLogs}
                    className="argus-toolbar-btn px-2 py-0.5 text-[10px]"
                  >
                    Refresh
                  </button>
                </div>
                <div className="overflow-x-auto max-h-96 overflow-y-auto border border-[#B8CCE4] rounded w-full max-w-full">
                  <table className="argus-table text-xs w-full">
                    <thead>
                      <tr>
                        <th style={{ width: '8%' }}>ID</th>
                        <th style={{ width: '22%' }}>Event Type</th>
                        <th style={{ width: '14%' }}>Account</th>
                        <th style={{ width: '14%' }}>IP Address</th>
                        <th style={{ width: '10%' }}>Status</th>
                        <th style={{ width: '18%' }}>Details</th>
                        <th style={{ width: '14%' }}>Recorded At</th>
                      </tr>
                    </thead>
                    <tbody>
                      {auditLogs.map((log) => (
                        <tr key={log.id}>
                          <td className="font-mono text-slate-500 whitespace-nowrap">#{log.id}</td>
                          <td className="font-semibold text-slate-800 whitespace-nowrap">{log.eventType}</td>
                          <td className="font-mono text-slate-900 whitespace-nowrap">{log.username || '-'}</td>
                          <td className="font-mono text-slate-600 whitespace-nowrap">{log.ipAddress || 'local'}</td>
                          <td className="whitespace-nowrap">
                            <span
                              className={`px-1.5 py-0.2 rounded text-[10px] font-bold ${
                                log.status === 'SUCCESS'
                                  ? 'bg-emerald-100 text-emerald-800 border border-emerald-300'
                                  : log.status === 'WARNING'
                                  ? 'bg-amber-100 text-amber-800 border border-amber-300'
                                  : 'bg-rose-100 text-rose-800 border border-rose-300'
                              }`}
                            >
                              {log.status}
                            </span>
                          </td>
                          <td className="text-slate-600 max-w-[200px] truncate" title={log.details || ''}>
                            {log.details || '-'}
                          </td>
                          <td className="font-mono text-slate-500 text-[11px] whitespace-nowrap">
                            {new Date(log.createdAt).toLocaleString()}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          </div>
        )}
      </main>
      </div>

      {/* 5. Enterprise Argus-Style Footer */}
      <footer className="bg-slate-200 border-t border-[#B8CCE4] w-full px-4 py-1.5 text-[11px] text-slate-600 flex flex-wrap items-center justify-between gap-2 shrink-0">
        <div className="flex items-center space-x-2">
          <span>PharmaSafe Pharmacovigilance Case Management System</span>
          <span>&bull;</span>
          <span>Inspired by Oracle Argus Safety 7.0.3</span>
          <span>&bull;</span>
          <span className="font-mono text-slate-500">ICH E2B(R3) &bull; 21 CFR Part 11 Architecture (Educational)</span>
        </div>
        <div className="font-mono text-slate-500 text-[10px] flex items-center space-x-3">
          <span>
            Database: <strong className="text-slate-700">{dbHealth?.databaseName || 'pharmasafe_db'}</strong> (
            {dbHealth?.databaseProduct || 'PostgreSQL'} {dbHealth?.databaseVersion || '18.4'})
          </span>
          <span>&bull;</span>
          <span>
            User: <strong className="text-slate-700">{user?.username}</strong> ({user?.role})
          </span>
        </div>
      </footer>
    </div>
  );
};

export default Stage3Dashboard;
