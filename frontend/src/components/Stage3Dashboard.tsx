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
  ArrowUpDown,
  Menu,
  X
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

  // Responsive sidebar toggle for mobile / narrow viewports
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

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

  // Fetch real PostgreSQL cases assigned to current user
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

  const handleNewCaseClick = () => {
    setCurrentEditingCase(null);
    setActiveTab('case-entry');
    setSearchFeedback('Opened Initial Case Entry dialog for new safety report.');
  };

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

  const handleCloseCase = () => {
    setCurrentEditingCase(null);
    setActiveTab('dashboard');
    setSearchFeedback('Closed active case workspace.');
  };

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
      const opened = await handleOpenCaseByNumber(query);
      if (opened) {
        setSearchingCases(false);
        return;
      }

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
    <div className="min-h-screen bg-[#F4F7FB] flex flex-col font-sans text-[#172B4D] antialiased overflow-x-hidden">
      {/* 1. Deep Navy Primary Header Bar */}
      <header className="bg-[#0B1F33] text-white px-4 sm:px-6 py-2.5 flex items-center justify-between gap-4 border-b border-[#1A3654] select-none shrink-0 shadow-xs">
        <div className="flex items-center space-x-3 min-w-0">
          <button
            type="button"
            onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
            className="md:hidden p-1.5 text-slate-300 hover:text-white rounded-md hover:bg-white/10 cursor-pointer"
            aria-label="Toggle navigation menu"
          >
            {mobileMenuOpen ? <X className="w-5 h-5" /> : <Menu className="w-5 h-5" />}
          </button>

          <div className="bg-[#102A43] p-1.5 rounded-lg border border-[#234567] shrink-0">
            <ShieldCheck className="w-5 h-5 text-[#60A5FA]" />
          </div>
          <div className="min-w-0">
            <div className="flex items-center space-x-2 flex-wrap">
              <span className="font-bold text-base tracking-tight text-white">PharmaSafe</span>
              <span className="text-[11px] bg-[#1E3A5F] text-[#93C5FD] border border-[#2563EB]/40 px-2 py-0.5 rounded font-mono font-medium">
                STAGE 2 AUTHENTICATED
              </span>
              <span className="text-[11px] bg-emerald-950/60 text-emerald-300 border border-emerald-500/30 px-2 py-0.5 rounded font-mono font-medium hidden lg:inline">
                STAGE 3 DASHBOARD
              </span>
            </div>
            <p className="text-xs text-[#94A3B8] font-normal truncate hidden sm:block">
              Educational Pharmacovigilance Case Management System (Inspired by Oracle Argus Safety 7.0.3)
            </p>
          </div>
        </div>

        {/* User Badge, Security Controls & Logout */}
        <div className="flex items-center space-x-3 text-xs shrink-0">
          <div className="hidden sm:flex items-center space-x-2 bg-[#102A43] px-3 py-1.5 rounded-lg border border-[#234567] text-[#CBD5E1]">
            <UserIcon className="w-3.5 h-3.5 text-[#60A5FA]" />
            <span className="font-medium text-white">{user?.username}</span>
            <span className="text-[10px] bg-[#2563EB]/30 text-[#93C5FD] px-1.5 py-0.5 rounded font-mono border border-[#2563EB]/40">
              {user?.role}
            </span>
          </div>

          <button
            onClick={() => logout()}
            disabled={isLoading}
            className="flex items-center space-x-1.5 bg-[#DC2626] hover:bg-[#B91C1C] active:bg-[#991B1B] text-white px-3 py-1.5 rounded-lg text-xs font-semibold transition-colors cursor-pointer shadow-xs disabled:opacity-50"
            title="Log Out of PharmaSafe and terminate session"
          >
            <LogOut className="w-3.5 h-3.5" />
            <span>Log Out</span>
          </button>
        </div>
      </header>

      {/* 2. Top Bar with Quick Launch and Status */}
      <section className="bg-white w-full px-4 sm:px-6 py-2 border-b border-[#DCE4EE] flex flex-wrap items-center justify-between gap-3 shrink-0 shadow-2xs">
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
        <div className="flex items-center space-x-3 text-xs text-[#52657A] shrink-0">
          <span className="flex items-center space-x-1 font-mono">
            <Database className="w-3.5 h-3.5 text-[#2563EB]" />
            <span>PostgreSQL:</span>
            <strong className="text-emerald-700 font-semibold">{dbHealth?.database || 'CONNECTED'}</strong>
          </span>
          <span className="text-[#DCE4EE]">|</span>
          <span className="flex items-center space-x-1 font-mono">
            <Clock className="w-3.5 h-3.5 text-[#7A8B9E]" />
            <span>Session:</span>
            <strong className="text-[#102A43] font-medium">{user?.username}</strong>
          </span>
        </div>
      </section>

      {/* 3. Compact White Breadcrumb Bar */}
      <div className="bg-white w-full px-4 sm:px-6 py-2 text-xs font-medium flex items-center justify-between border-b border-[#DCE4EE] shrink-0">
        <div className="flex items-center space-x-2 text-[#52657A] min-w-0">
          <span className="text-[#7A8B9E] shrink-0">PharmaSafe Safety &gt;</span>
          <span className="font-semibold uppercase tracking-wider text-[#102A43] truncate">
            {activeTab === 'dashboard' ? 'Argus Home Page Dashboard' : activeTab.replace('-', ' ')}
          </span>
        </div>
        <div className="text-xs text-[#52657A] font-mono hidden md:flex items-center space-x-3 pr-2 shrink-0">
          <span>Active View: <strong className="text-[#102A43] uppercase font-bold">{activeTab}</strong></span>
          <span className="text-[#DCE4EE]">|</span>
          <span>User Role: <strong className="text-emerald-700 font-bold">{user?.role}</strong></span>
          <span className="text-[#DCE4EE]">|</span>
          <span>Standards: <strong className="text-[#172B4D]">ICH E2B(R3) &bull; 21 CFR Part 11</strong></span>
        </div>
      </div>

      {/* 4. Application Shell (Navy Sidebar Navigation + Content Area) */}
      <div className="flex-1 flex w-full max-w-full overflow-hidden min-h-0 relative">
        {/* Navy Sidebar Navigation */}
        <aside
          aria-label="Enterprise Navigation Sidebar"
          className={`${
            mobileMenuOpen ? 'translate-x-0' : '-translate-x-full md:translate-x-0'
          } fixed md:static inset-y-0 left-0 z-40 w-60 min-w-[15rem] max-w-[15rem] bg-[#102A43] border-r border-[#1E3A5F] flex flex-col justify-between p-3 shrink-0 select-none transition-transform duration-200 ease-in-out shadow-sm md:shadow-none`}
        >
          <div className="space-y-4">
            <div>
              <div className="px-2.5 py-1.5 text-xs font-semibold text-[#93C5FD] uppercase tracking-wider border-b border-[#1E3A5F] mb-2 flex items-center justify-between">
                <span>Enterprise Modules</span>
                <span className="text-[10px] font-mono text-[#64748B]">Argus 7.0.3</span>
              </div>

              <nav role="tablist" aria-label="Enterprise Modules" className="flex flex-col space-y-1">
                <button
                  role="tab"
                  aria-selected={activeTab === 'dashboard'}
                  onClick={() => {
                    setActiveTab('dashboard');
                    setMobileMenuOpen(false);
                  }}
                  className={`w-full flex items-center space-x-2.5 px-3 py-2 rounded-lg text-xs text-left transition font-medium cursor-pointer ${
                    activeTab === 'dashboard'
                      ? 'bg-[#2563EB] text-white shadow-xs font-semibold'
                      : 'text-[#CBD5E1] hover:text-white hover:bg-white/10'
                  }`}
                >
                  <Layers className="w-4 h-4 flex-shrink-0" />
                  <span className="flex-1">Dashboard</span>
                </button>

                <button
                  role="tab"
                  aria-selected={activeTab === 'case-entry'}
                  onClick={() => {
                    setActiveTab('case-entry');
                    setMobileMenuOpen(false);
                  }}
                  className={`w-full flex items-center space-x-2.5 px-3 py-2 rounded-lg text-xs text-left transition font-medium cursor-pointer ${
                    activeTab === 'case-entry'
                      ? 'bg-[#2563EB] text-white shadow-xs font-semibold'
                      : 'text-[#CBD5E1] hover:text-white hover:bg-white/10'
                  }`}
                >
                  <FolderPlus className="w-4 h-4 flex-shrink-0" />
                  <span className="flex-1">Case Entry</span>
                </button>

                <button
                  data-testid="sidebar-worklist-tab"
                  role="tab"
                  aria-selected={activeTab === 'worklist'}
                  onClick={() => {
                    setActiveTab('worklist');
                    fetchPaginatedWorklist();
                    setMobileMenuOpen(false);
                  }}
                  className={`w-full flex items-center space-x-2.5 px-3 py-2 rounded-lg text-xs text-left transition font-medium cursor-pointer ${
                    activeTab === 'worklist'
                      ? 'bg-[#2563EB] text-white shadow-xs font-semibold'
                      : 'text-[#CBD5E1] hover:text-white hover:bg-white/10'
                  }`}
                >
                  <ListOrdered className="w-4 h-4 flex-shrink-0" />
                  <span className="flex-1">Worklist / Queue</span>
                </button>

                <button
                  role="tab"
                  aria-selected={activeTab === 'contact-log'}
                  onClick={() => {
                    setActiveTab('contact-log');
                    setMobileMenuOpen(false);
                  }}
                  className={`w-full flex items-center space-x-2.5 px-3 py-2 rounded-lg text-xs text-left transition font-medium cursor-pointer ${
                    activeTab === 'contact-log'
                      ? 'bg-[#2563EB] text-white shadow-xs font-semibold'
                      : 'text-[#CBD5E1] hover:text-white hover:bg-white/10'
                  }`}
                >
                  <MessageSquare className="w-4 h-4 flex-shrink-0" />
                  <span className="flex-1">Contact Log</span>
                  <span className="text-[10px] text-[#64748B] font-mono">Stage 5</span>
                </button>

                <button
                  role="tab"
                  aria-selected={activeTab === 'action-items'}
                  onClick={() => {
                    setActiveTab('action-items');
                    setMobileMenuOpen(false);
                  }}
                  className={`w-full flex items-center space-x-2.5 px-3 py-2 rounded-lg text-xs text-left transition font-medium cursor-pointer ${
                    activeTab === 'action-items'
                      ? 'bg-[#2563EB] text-white shadow-xs font-semibold'
                      : 'text-[#CBD5E1] hover:text-white hover:bg-white/10'
                  }`}
                >
                  <CheckSquare2 className="w-4 h-4 flex-shrink-0" />
                  <span className="flex-1">Action Items</span>
                  <span className="text-[10px] text-[#64748B] font-mono">Stage 5</span>
                </button>

                <button
                  role="tab"
                  aria-selected={activeTab === 'reports'}
                  onClick={() => {
                    setActiveTab('reports');
                    setMobileMenuOpen(false);
                  }}
                  className={`w-full flex items-center space-x-2.5 px-3 py-2 rounded-lg text-xs text-left transition font-medium cursor-pointer ${
                    activeTab === 'reports'
                      ? 'bg-[#2563EB] text-white shadow-xs font-semibold'
                      : 'text-[#CBD5E1] hover:text-white hover:bg-white/10'
                  }`}
                >
                  <FileText className="w-4 h-4 flex-shrink-0" />
                  <span className="flex-1">Reports</span>
                  <span className="text-[10px] text-[#64748B] font-mono">Stage 6</span>
                </button>

                {user?.role === 'ROLE_ADMIN' && (
                  <button
                    role="tab"
                    aria-selected={activeTab === 'admin'}
                    onClick={() => {
                      setActiveTab('admin');
                      setMobileMenuOpen(false);
                    }}
                    className={`w-full flex items-center space-x-2.5 px-3 py-2 rounded-lg text-xs text-left transition font-medium cursor-pointer ${
                      activeTab === 'admin'
                        ? 'bg-[#2563EB] text-white shadow-xs font-semibold'
                        : 'text-[#CBD5E1] hover:text-white hover:bg-white/10'
                    }`}
                  >
                    <SlidersHorizontal className="w-4 h-4 flex-shrink-0 text-[#60A5FA]" />
                    <span className="flex-1">Administration</span>
                  </button>
                )}
              </nav>
            </div>

            {/* Quick Case Queue Summary */}
            <div className="bg-[#0B1F33] p-3 rounded-lg border border-[#1E3A5F] text-xs space-y-2">
              <span className="font-semibold text-[#93C5FD] uppercase tracking-wider block text-[11px] border-b border-[#1E3A5F] pb-1.5">
                Case Queue Summary
              </span>
              <div className="flex items-center justify-between text-[#CBD5E1]">
                <span>Assigned Cases Queue</span>
                <span className="font-mono font-bold text-white bg-[#102A43] px-2 py-0.5 rounded text-[11px] border border-[#1E3A5F]">
                  {dashboardSummary ? dashboardSummary.assignedCases : assignedCases.length}
                </span>
              </div>
              <div className="flex items-center justify-between text-[#CBD5E1]">
                <span>Awaiting Review</span>
                <span className="font-mono font-bold text-[#FDE68A] bg-[#78350F]/40 px-2 py-0.5 rounded text-[11px] border border-[#D97706]/30">
                  {dashboardSummary ? dashboardSummary.userAwaitingReviewCases : 0}
                </span>
              </div>
              <div className="flex items-center justify-between text-[#CBD5E1]">
                <span>Action Items Due</span>
                <span className="font-mono text-[#94A3B8] bg-[#102A43] px-1.5 py-0.5 rounded text-[10px] border border-[#1E3A5F]">
                  Planned
                </span>
              </div>
            </div>
          </div>

          {/* PV Standards & Database Compliance Badge */}
          <div className="bg-[#0B1F33] p-3 rounded-lg border border-[#1E3A5F] text-xs text-[#94A3B8] space-y-1.5 mt-3">
            <span className="font-semibold uppercase tracking-wider block text-[10px] text-[#93C5FD]">
              PV Architecture
            </span>
            <div className="flex items-center space-x-2 text-[#CBD5E1]">
              <span className="w-2 h-2 rounded-full bg-emerald-500"></span>
              <span>PostgreSQL 18.4 (Active)</span>
            </div>
            <div className="flex items-center space-x-2 text-[#CBD5E1]">
              <span className="w-2 h-2 rounded-full bg-emerald-500"></span>
              <span>ICH E2B(R3) Aligned</span>
            </div>
            <div className="flex items-center space-x-2 text-[#CBD5E1]">
              <span className="w-2 h-2 rounded-full bg-emerald-500"></span>
              <span>21 CFR Part 11 Architecture</span>
            </div>
          </div>
        </aside>

        {/* Backdrop for mobile sidebar */}
        {mobileMenuOpen && (
          <div
            onClick={() => setMobileMenuOpen(false)}
            className="fixed inset-0 bg-black/40 z-30 md:hidden"
            aria-hidden="true"
          />
        )}

        {/* Main Content Area */}
        <main className="flex-1 min-w-0 max-w-full overflow-y-auto overflow-x-hidden p-4 sm:p-6 space-y-6">
          {/* VIEW 1: HOME PAGE / DASHBOARD */}
          {activeTab === 'dashboard' && (
            <div className="space-y-6">
              {/* Error Banner if Dashboard Summary API fails */}
              {summaryError && (
                <div className="p-3 bg-[#FEF2F2] border border-[#FECACA] rounded-lg text-xs text-[#991B1B] flex items-center space-x-2.5">
                  <AlertCircle className="w-4 h-4 text-[#DC2626] shrink-0" />
                  <span className="font-medium">{summaryError}</span>
                </div>
              )}

              {/* KPI Summary Cards */}
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
                {/* Card 1: Total Cases */}
                <div className="bg-white rounded-xl border border-[#DCE4EE] p-5 shadow-xs flex flex-col justify-between">
                  <div className="flex items-center justify-between text-[#102A43]">
                    <span className="text-xs font-semibold uppercase tracking-wider text-[#52657A]">Total Safety Cases</span>
                    <FileText className="w-4 h-4 text-[#2563EB]" />
                  </div>
                  <div className="mt-3 flex items-baseline justify-between">
                    <span className="text-3xl font-bold font-mono text-[#102A43]">
                      {loadingSummary ? '...' : (dashboardSummary ? dashboardSummary.totalCases : assignedCases.length)}
                    </span>
                    <span className="text-xs text-[#7A8B9E] font-medium">System-wide</span>
                  </div>
                  <div className="mt-2 text-xs text-[#52657A] border-t border-[#F1F5F9] pt-2 flex justify-between">
                    <span>Serious Cases:</span>
                    <strong className="text-[#DC2626] font-mono font-bold">
                      {dashboardSummary?.seriousCases ?? '-'}
                    </strong>
                  </div>
                </div>

                {/* Card 2: Assigned Cases */}
                <div className="bg-white rounded-xl border border-[#DCE4EE] p-5 shadow-xs flex flex-col justify-between">
                  <div className="flex items-center justify-between text-[#102A43]">
                    <span className="text-xs font-semibold uppercase tracking-wider text-[#52657A]">Cases Assigned to Me</span>
                    <UserIcon className="w-4 h-4 text-[#2563EB]" />
                  </div>
                  <div className="mt-3 flex items-baseline justify-between">
                    <span className="text-3xl font-bold font-mono text-emerald-800">
                      {loadingSummary ? '...' : (dashboardSummary ? dashboardSummary.assignedCases : assignedCases.length)}
                    </span>
                    <span className="text-xs text-emerald-800 bg-emerald-50 px-2 py-0.5 rounded font-semibold border border-emerald-200">
                      My Queue
                    </span>
                  </div>
                  <div className="mt-2 text-xs text-[#52657A] border-t border-[#F1F5F9] pt-2 flex justify-between">
                    <span>User Account:</span>
                    <strong className="text-[#102A43] font-mono">{user?.username}</strong>
                  </div>
                </div>

                {/* Card 3: Awaiting Review */}
                <div className="bg-white rounded-xl border border-[#DCE4EE] p-5 shadow-xs flex flex-col justify-between">
                  <div className="flex items-center justify-between text-[#102A43]">
                    <span className="text-xs font-semibold uppercase tracking-wider text-[#52657A]">Cases Awaiting Review</span>
                    <Activity className="w-4 h-4 text-[#D97706]" />
                  </div>
                  <div className="mt-3 flex items-baseline justify-between">
                    <span className="text-3xl font-bold font-mono text-[#B45309]">
                      {loadingSummary ? '...' : (dashboardSummary ? dashboardSummary.awaitingReviewCases : 0)}
                    </span>
                    <span className="text-xs text-[#92400E] bg-[#FFFBEB] px-2 py-0.5 rounded font-semibold border border-[#FDE68A]">
                      Review State
                    </span>
                  </div>
                  <div className="mt-2 text-xs text-[#52657A] border-t border-[#F1F5F9] pt-2 truncate" title="Quality Review or Medical Review">
                    <span>Assigned to you: </span>
                    <strong className="text-[#92400E] font-mono font-bold">
                      {dashboardSummary?.userAwaitingReviewCases ?? 0}
                    </strong>
                    <span className="text-[#7A8B9E]"> (Quality/Medical)</span>
                  </div>
                </div>

                {/* Card 4: Overdue Action Items */}
                <div className="bg-white rounded-xl border border-[#DCE4EE] p-5 shadow-xs flex flex-col justify-between">
                  <div className="flex items-center justify-between text-[#102A43]">
                    <span className="text-xs font-semibold uppercase tracking-wider text-[#52657A]">Overdue Action Items</span>
                    <Clock className="w-4 h-4 text-[#7A8B9E]" />
                  </div>
                  <div className="mt-3 flex items-baseline justify-between">
                    <span className="text-base font-semibold text-[#52657A] font-mono">
                      Not Configured
                    </span>
                    <span className="text-xs text-[#475569] bg-[#F1F5F9] px-2 py-0.5 rounded font-mono border border-[#CBD5E1]">
                      Planned (Stage 5)
                    </span>
                  </div>
                  <div className="mt-2 text-xs text-[#52657A] border-t border-[#F1F5F9] pt-2 truncate" title={dashboardSummary?.actionItemsNote || 'Action items module not configured in schema (Planned for Stage 5)'}>
                    <span>Schema Status: </span>
                    <strong className="text-[#102A43] font-mono">UNCONFIGURED</strong>
                  </div>
                </div>
              </div>

              {/* Section A: Case Quick Launch / Search Case */}
              <div className="bg-white rounded-xl border border-[#DCE4EE] overflow-hidden shadow-xs">
                <div className="bg-[#F8FAFD] px-5 py-3 border-b border-[#DCE4EE] flex items-center justify-between">
                  <span className="flex items-center space-x-2 text-sm font-semibold text-[#102A43]">
                    <Search className="w-4 h-4 text-[#2563EB]" />
                    <span>Search Case &bull; Case Quick Launch</span>
                  </span>
                  <span className="text-xs text-[#7A8B9E] font-mono">
                    [Section A: Argus Home Page Reference]
                  </span>
                </div>
                <div className="p-5">
                  <form onSubmit={handleCaseSearchSubmit} className="flex flex-wrap items-center gap-3">
                    <div className="flex items-center space-x-3 flex-1 min-w-[280px]">
                      <label
                        htmlFor="caseSearchInput"
                        className="text-xs font-semibold text-[#102A43] whitespace-nowrap"
                      >
                        Case Quick Launch:
                      </label>
                      <input
                        id="caseSearchInput"
                        type="text"
                        value={caseSearchInput}
                        onChange={(e) => setCaseSearchInput(e.target.value)}
                        placeholder="Enter Case Number to search (e.g. US-2026-0001)..."
                        className="flex-1 px-3 py-2 text-sm border border-[#DCE4EE] rounded-lg bg-white text-[#172B4D] placeholder-[#94A3B8] focus:outline-none focus:border-[#2563EB] focus:ring-2 focus:ring-[#2563EB]/20 transition"
                      />
                    </div>
                    <button
                      type="submit"
                      disabled={searchingCases}
                      className="bg-[#2563EB] hover:bg-[#1D4ED8] text-white px-4 py-2 rounded-lg text-xs font-semibold flex items-center space-x-2 transition-colors cursor-pointer disabled:opacity-50 shadow-xs"
                    >
                      <Search className={`w-3.5 h-3.5 ${searchingCases ? 'animate-spin' : ''}`} />
                      <span>{searchingCases ? 'Searching...' : 'Quick Search'}</span>
                    </button>
                    <span className="text-xs text-[#52657A] bg-[#F4F7FB] px-2.5 py-1.5 rounded-md border border-[#DCE4EE]">
                      Format: <code className="font-mono text-[#102A43] font-semibold">(Country) Case Number</code>
                    </span>
                  </form>

                  {searchFeedback && (
                    <div className="mt-3 p-3 bg-[#EFF6FF] border border-[#BFDBFE] rounded-lg text-xs text-[#1E40AF] flex items-start space-x-2.5">
                      <Info className="w-4 h-4 text-[#2563EB] flex-shrink-0 mt-0.5" />
                      <div className="flex-1">
                        <span>{searchFeedback}</span>
                      </div>
                      <button
                        onClick={() => setSearchFeedback(null)}
                        className="text-[#2563EB] hover:text-[#1E40AF] font-bold cursor-pointer text-xs"
                      >
                        &times;
                      </button>
                    </div>
                  )}

                  {/* Search Results Table */}
                  {searchResults && searchResults.length > 0 && (
                    <div className="mt-4 border border-[#DCE4EE] rounded-lg overflow-hidden">
                      <div className="bg-[#F8FAFD] px-4 py-2.5 text-xs font-semibold text-[#102A43] flex items-center justify-between border-b border-[#DCE4EE]">
                        <span>Matching Safety Cases ({searchResults.length})</span>
                        <button
                          onClick={() => setSearchResults(null)}
                          className="text-xs text-[#2563EB] hover:underline cursor-pointer font-medium"
                        >
                          Clear Results
                        </button>
                      </div>
                      <div className="overflow-x-auto max-h-56 overflow-y-auto">
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
                                className="hover:bg-[#F0F6FD] cursor-pointer"
                                onClick={() => handleOpenCase(sc.id)}
                              >
                                <td className="font-semibold text-[#102A43]">
                                  ({sc.countryCode}) {sc.caseNumber}
                                  <span className="ml-1 text-[11px] text-[#7A8B9E] font-mono">v{sc.versionNumber}</span>
                                </td>
                                <td>{sc.reportType}</td>
                                <td>{sc.primaryProduct}</td>
                                <td>
                                  <span className={`px-2 py-0.5 rounded text-[11px] font-semibold ${
                                    sc.workflowState === 'Draft' ? 'bg-[#FFFBEB] text-[#92400E] border border-[#FDE68A]' : 'bg-emerald-50 text-emerald-800 border border-emerald-200'
                                  }`}>
                                    {sc.workflowState}
                                  </span>
                                </td>
                                <td>
                                  <button
                                    type="button"
                                    onClick={(e) => { e.stopPropagation(); handleOpenCase(sc.id); }}
                                    className="px-2.5 py-1 text-xs font-semibold bg-[#2563EB] text-white rounded-md hover:bg-[#1D4ED8]"
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
              <div className="bg-white rounded-xl border border-[#DCE4EE] overflow-hidden shadow-xs">
                <div className="bg-[#F8FAFD] px-5 py-3 border-b border-[#DCE4EE] flex items-center justify-between">
                  <span className="flex items-center space-x-2 text-sm font-semibold text-[#102A43]">
                    <ListOrdered className="w-4 h-4 text-[#2563EB]" />
                    <span>Cases Assigned</span>
                    <span className="bg-[#EFF6FF] text-[#1E40AF] text-xs font-semibold px-2 py-0.5 rounded border border-[#BFDBFE]">
                      {assignedCases.length}
                    </span>
                  </span>
                  <div className="flex items-center space-x-2">
                    <button
                      onClick={handleNewCaseClick}
                      className="bg-white hover:bg-[#F8FAFD] border border-[#DCE4EE] text-[#102A43] px-3 py-1.5 text-xs font-semibold rounded-lg flex items-center space-x-1.5 transition-colors cursor-pointer shadow-2xs"
                    >
                      <Plus className="w-3.5 h-3.5 text-emerald-600" />
                      <span>New Case</span>
                    </button>
                    <button
                      onClick={() => {
                        fetchAssignedCases();
                        fetchDashboardSummary();
                      }}
                      disabled={loadingAssignedCases}
                      className="bg-white hover:bg-[#F8FAFD] border border-[#DCE4EE] text-[#102A43] px-3 py-1.5 text-xs font-semibold rounded-lg flex items-center space-x-1.5 transition-colors cursor-pointer shadow-2xs"
                      title="Refresh assigned cases from PostgreSQL"
                    >
                      <RefreshCw className={`w-3.5 h-3.5 ${loadingAssignedCases ? 'animate-spin text-[#2563EB]' : ''}`} />
                      <span>Refresh</span>
                    </button>
                  </div>
                </div>

                {assignedCasesError && (
                  <div className="p-3 bg-[#FEF2F2] border-b border-[#FECACA] text-xs text-[#991B1B] flex items-center space-x-2">
                    <AlertCircle className="w-4 h-4 text-[#DC2626] shrink-0" />
                    <span>{assignedCasesError}</span>
                  </div>
                )}

                {loadingAssignedCases ? (
                  <div className="p-8 text-center text-xs text-[#52657A] flex items-center justify-center space-x-2 bg-white">
                    <RefreshCw className="w-4 h-4 animate-spin text-[#2563EB]" />
                    <span>Loading assigned safety cases from PostgreSQL...</span>
                  </div>
                ) : assignedCases.length === 0 ? (
                  <div className="text-center py-8 text-[#52657A] bg-white">
                    <div className="flex flex-col items-center justify-center space-y-1.5">
                      <AlertCircle className="w-5 h-5 text-[#94A3B8]" />
                      <span className="font-semibold text-xs text-[#172B4D]">No assigned cases available yet.</span>
                      <span className="text-xs text-[#7A8B9E]">
                        Click "New Case" above or press <kbd className="font-mono bg-[#F1F5F9] px-1.5 py-0.5 rounded border border-[#CBD5E1] text-[#334155]">Ctrl+Alt+N</kbd> to enter an adverse event case.
                      </span>
                    </div>
                  </div>
                ) : (
                  <div className="overflow-x-auto max-h-80 overflow-y-auto">
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
                            className="hover:bg-[#F0F6FD] cursor-pointer transition-colors"
                            title={`Click to open Case ${c.caseNumber} in Initial Case Entry`}
                          >
                            <td className="font-semibold text-[#102A43] whitespace-nowrap">
                              <span className="text-[#52657A] font-normal">({c.countryCode})</span>{' '}
                              <span className="text-[#2563EB] hover:underline">{c.caseNumber}</span>
                              <span className="ml-1 text-[11px] text-[#7A8B9E] font-mono">v{c.versionNumber}</span>
                            </td>
                            <td className="whitespace-nowrap">{c.reportType}</td>
                            <td className="font-medium text-[#172B4D] truncate max-w-[200px]" title={c.primaryProduct}>
                              {c.primaryProduct}
                            </td>
                            <td>
                              <span
                                className={`px-2 py-0.5 rounded text-[11px] font-semibold ${
                                  c.workflowState === 'Draft'
                                    ? 'bg-[#FFFBEB] text-[#92400E] border border-[#FDE68A]'
                                    : c.workflowState === 'Quality Review'
                                    ? 'bg-[#EFF6FF] text-[#1E40AF] border border-[#BFDBFE]'
                                    : c.workflowState === 'Medical Review'
                                    ? 'bg-[#FAF5FF] text-[#6B21A8] border border-[#E9D5FF]'
                                    : 'bg-emerald-50 text-emerald-800 border border-emerald-200'
                                }`}
                              >
                                {c.workflowState}
                              </span>
                            </td>
                            <td className="text-[#52657A] truncate max-w-[200px]" title={c.primaryEvent}>
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
              <div className="bg-white rounded-xl border border-[#DCE4EE] overflow-hidden shadow-xs">
                <div className="bg-[#F8FAFD] px-5 py-3 border-b border-[#DCE4EE] flex items-center justify-between">
                  <span className="flex items-center space-x-2 text-sm font-semibold text-[#102A43]">
                    <Activity className="w-4 h-4 text-[#2563EB]" />
                    <span>Recent Case Activity &bull; Live Audit Events</span>
                  </span>
                  <span className="text-xs text-[#7A8B9E] font-mono">
                    [audit_events: CASE_CREATED / CASE_UPDATED]
                  </span>
                </div>
                <div className="p-4">
                  {loadingActivity ? (
                    <div className="p-4 text-center text-xs text-[#52657A] flex items-center justify-center space-x-2">
                      <RefreshCw className="w-4 h-4 animate-spin text-[#2563EB]" />
                      <span>Loading recent activity feed...</span>
                    </div>
                  ) : recentActivity.length === 0 ? (
                    <div className="p-6 text-center text-xs text-[#52657A] bg-[#F8FAFD] border border-dashed border-[#DCE4EE] rounded-lg">
                      No recent case activity recorded in the database yet.
                    </div>
                  ) : (
                    <div className="space-y-2 max-h-56 overflow-y-auto">
                      {recentActivity.map((act) => (
                        <div key={act.id} className="flex items-center justify-between text-xs p-2.5 bg-[#F8FAFD] rounded-lg border border-[#DCE4EE] hover:bg-[#F0F6FD] transition">
                          <div className="flex items-center space-x-2.5 min-w-0">
                            <span className={`px-2 py-0.5 rounded text-[11px] font-semibold font-mono ${
                              act.eventType === 'CASE_CREATED' ? 'bg-emerald-50 text-emerald-800 border border-emerald-200' : 'bg-[#EFF6FF] text-[#1E40AF] border border-[#BFDBFE]'
                            }`}>
                              {act.eventType}
                            </span>
                            <span className="text-[#172B4D] font-medium truncate" title={act.details}>
                              {act.details}
                            </span>
                          </div>
                          <div className="flex items-center space-x-2 shrink-0 text-xs text-[#7A8B9E] font-mono">
                            <span className="text-[#102A43] font-medium">{act.username}</span>
                            <span>&bull;</span>
                            <span>{new Date(act.createdAt).toLocaleTimeString()}</span>
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              </div>

              {/* Sections C & D: Contact Log & Action Items */}
              <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
                {/* Section C: Contact Log Entries */}
                <div className="bg-white rounded-xl border border-[#DCE4EE] overflow-hidden shadow-xs">
                  <div className="bg-[#F8FAFD] px-5 py-3 border-b border-[#DCE4EE] flex items-center justify-between">
                    <span className="flex items-center space-x-2 text-sm font-semibold text-[#102A43]">
                      <MessageSquare className="w-4 h-4 text-[#2563EB]" />
                      <span>Contact Log Entries</span>
                    </span>
                    <span className="text-xs text-[#7A8B9E] font-mono">
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
                          <tr><td colSpan={3} className="text-center py-6 text-[#7A8B9E] bg-white">No contact log entries recorded yet.</td></tr>
                        ) : contactLogs.map(log => (
                          <tr key={log.id}><td className="font-semibold text-[#102A43]">{log.caseNumber}</td><td>{new Date(log.contactAt).toLocaleString()}</td><td>{log.description}</td></tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>

                {/* Section D: Action Item Entries */}
                <div className="bg-white rounded-xl border border-[#DCE4EE] overflow-hidden shadow-xs">
                  <div className="bg-[#F8FAFD] px-5 py-3 border-b border-[#DCE4EE] flex items-center justify-between">
                    <span className="flex items-center space-x-2 text-sm font-semibold text-[#102A43]">
                      <CheckSquare2 className="w-4 h-4 text-[#2563EB]" />
                      <span>Action Item Entries</span>
                    </span>
                    <span className="text-xs text-[#7A8B9E] font-mono">
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
                          <tr><td colSpan={3} className="text-center py-6 text-[#7A8B9E] bg-white">No pending action items.</td></tr>
                        ) : actionItems.map(item => (
                          <tr key={item.id}><td className="font-semibold text-[#102A43]">{item.caseNumber}</td><td className={item.overdue ? 'text-[#DC2626] font-semibold' : ''}>{new Date(item.dueAt).toLocaleString()}</td><td>{item.description}</td></tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>
              </div>

              {/* Section E: Live Backend Status */}
              <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                <div className="bg-white rounded-xl border border-[#DCE4EE] p-5 shadow-xs">
                  <div className="flex items-center justify-between pb-3 border-b border-[#F1F5F9]">
                    <span className="text-xs font-semibold text-[#102A43] flex items-center space-x-2">
                      <Database className="w-4 h-4 text-[#2563EB]" />
                      <span>PostgreSQL Database</span>
                    </span>
                    <span className="text-[11px] bg-emerald-50 text-emerald-800 font-semibold px-2 py-0.5 rounded border border-emerald-200">
                      {dbHealth?.database || 'UP'}
                    </span>
                  </div>
                  <div className="mt-3 space-y-2 text-xs text-[#52657A]">
                    <div className="flex justify-between">
                      <span className="text-[#7A8B9E]">Database Name:</span>
                      <span className="font-mono font-medium text-[#102A43]">
                        {dbHealth?.databaseName || 'pharmasafe_db'}
                      </span>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-[#7A8B9E]">Connected User:</span>
                      <span className="font-mono font-medium text-[#102A43]">
                        {dbHealth?.databaseUser || 'pharmasafe_user'}
                      </span>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-[#7A8B9E]">Database Version:</span>
                      <span className="font-mono text-[#172B4D]">
                        {dbHealth?.databaseProduct} {dbHealth?.databaseVersion || '18.4'}
                      </span>
                    </div>
                  </div>
                </div>

                <div className="bg-white rounded-xl border border-[#DCE4EE] p-5 shadow-xs">
                  <div className="flex items-center justify-between pb-3 border-b border-[#F1F5F9]">
                    <span className="text-xs font-semibold text-[#102A43] flex items-center space-x-2">
                      <Lock className="w-4 h-4 text-[#2563EB]" />
                      <span>Session Security</span>
                    </span>
                    <span className="text-[11px] bg-[#EFF6FF] text-[#1E40AF] font-semibold px-2 py-0.5 rounded border border-[#BFDBFE]">
                      ACTIVE
                    </span>
                  </div>
                  <div className="mt-3 space-y-2 text-xs text-[#52657A]">
                    <div className="flex justify-between">
                      <span className="text-[#7A8B9E]">Session Cookie:</span>
                      <span className="font-mono font-medium text-[#102A43]">JSESSIONID (HttpOnly)</span>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-[#7A8B9E]">CSRF Token:</span>
                      <span className="font-mono font-medium text-[#102A43]">X-XSRF-TOKEN (Cookie)</span>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-[#7A8B9E]">Lockout Defense:</span>
                      <span className="font-medium text-[#B45309]">3-Attempt Pessimistic Lock</span>
                    </div>
                  </div>
                </div>

                <div className="bg-white rounded-xl border border-[#DCE4EE] p-5 shadow-xs">
                  <div className="flex items-center justify-between pb-3 border-b border-[#F1F5F9]">
                    <span className="text-xs font-semibold text-[#102A43] flex items-center space-x-2">
                      <Activity className="w-4 h-4 text-emerald-600" />
                      <span>PV Case Architecture</span>
                    </span>
                    <span className="text-[11px] bg-[#F1F5F9] text-[#334155] font-semibold px-2 py-0.5 rounded border border-[#CBD5E1]">
                      STAGE 3
                    </span>
                  </div>
                  <div className="mt-3 space-y-2 text-xs text-[#52657A]">
                    <div className="flex justify-between">
                      <span className="text-[#7A8B9E]">Next Phase:</span>
                      <span className="font-medium text-[#102A43]">Stage 4 (Initial Case Entry)</span>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-[#7A8B9E]">Schema Design:</span>
                      <span className="font-mono text-emerald-700 font-medium">docs/database-design.md</span>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-[#7A8B9E]">Compliance:</span>
                      <span className="font-medium text-[#172B4D]">ICH E2B(R3), 21 CFR Part 11</span>
                    </div>
                  </div>
                </div>
              </div>

              {/* Section F: Live PostgreSQL Audit Trail */}
              {user?.role === 'ROLE_ADMIN' && (
                <div className="bg-white rounded-xl border border-[#DCE4EE] overflow-hidden shadow-xs">
                  <div className="bg-[#F8FAFD] px-5 py-3 border-b border-[#DCE4EE] flex items-center justify-between">
                    <span className="flex items-center space-x-2 text-sm font-semibold text-[#102A43]">
                      <FileText className="w-4 h-4 text-[#2563EB]" />
                      <span>AUTHENTICATION AUDIT TRAIL &bull; LIVE POSTGRESQL (audit_events)</span>
                    </span>
                    <div className="flex items-center space-x-2">
                      {adminStatus?.totalAuditEvents !== undefined && (
                        <span className="text-xs bg-white px-2.5 py-1 rounded-md border border-[#DCE4EE] text-[#102A43] font-mono">
                          Total Audit Events: <strong>{adminStatus.totalAuditEvents}</strong>
                        </span>
                      )}
                      <button
                        onClick={fetchAuditLogs}
                        disabled={loadingAudit}
                        className="bg-white hover:bg-[#F8FAFD] border border-[#DCE4EE] text-[#102A43] px-2.5 py-1 text-xs font-semibold rounded-md flex items-center space-x-1 cursor-pointer"
                        title="Reload latest audit log entries from PostgreSQL"
                      >
                        <RefreshCw className={`w-3.5 h-3.5 ${loadingAudit ? 'animate-spin' : ''}`} />
                        <span>Refresh</span>
                      </button>
                    </div>
                  </div>

                  <div className="p-4 bg-white">
                    {auditError && (
                      <div className="p-3 bg-[#FFFBEB] border border-[#FDE68A] rounded-lg text-xs text-[#92400E] mb-3">
                        {auditError}
                      </div>
                    )}

                    {loadingAudit ? (
                      <div className="p-8 text-center text-xs text-[#52657A] flex items-center justify-center space-x-2">
                        <RefreshCw className="w-4 h-4 animate-spin text-[#2563EB]" />
                        <span>Fetching real-time audit event rows from PostgreSQL...</span>
                      </div>
                    ) : auditLogs.length === 0 ? (
                      <div className="p-8 text-center text-xs text-[#7A8B9E]">
                        No audit events recorded yet in the PostgreSQL database.
                      </div>
                    ) : (
                      <div className="overflow-x-auto max-h-80 overflow-y-auto border border-[#DCE4EE] rounded-lg w-full max-w-full">
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
                                <td className="font-mono text-[#7A8B9E] whitespace-nowrap">#{log.id}</td>
                                <td className="font-semibold text-[#102A43] whitespace-nowrap">{log.eventType}</td>
                                <td className="font-mono text-[#172B4D] whitespace-nowrap">{log.username || '-'}</td>
                                <td className="font-mono text-[#52657A] whitespace-nowrap">{log.ipAddress || 'local'}</td>
                                <td className="whitespace-nowrap">
                                  <span
                                    className={`px-2 py-0.5 rounded text-[11px] font-semibold ${
                                      log.status === 'SUCCESS'
                                        ? 'bg-emerald-50 text-emerald-800 border border-emerald-200'
                                        : log.status === 'WARNING'
                                        ? 'bg-[#FFFBEB] text-[#92400E] border border-[#FDE68A]'
                                        : 'bg-[#FEF2F2] text-[#991B1B] border border-[#FECACA]'
                                    }`}
                                  >
                                    {log.status}
                                  </span>
                                </td>
                                <td className="text-[#52657A] max-w-[200px] truncate" title={log.details || ''}>
                                  {log.details || '-'}
                                </td>
                                <td className="font-mono text-[#7A8B9E] text-xs whitespace-nowrap">
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
            <div className="bg-white rounded-xl border border-[#DCE4EE] p-5 sm:p-6 space-y-4 shadow-xs">
              <div className="flex flex-wrap items-center justify-between border-b border-[#DCE4EE] pb-4 gap-3">
                <div className="flex items-center space-x-2.5">
                  <ListOrdered className="w-5 h-5 text-[#2563EB]" />
                  <h2 className="text-base font-bold text-[#102A43]">Worklist / User Case Queue</h2>
                  <span className="text-xs bg-[#EFF6FF] text-[#1E40AF] px-2.5 py-0.5 rounded-full font-mono font-medium border border-[#BFDBFE]">
                    {worklistData ? `${worklistData.totalElements} Total Cases` : 'Loading...'}
                  </span>
                </div>
                <div className="flex items-center space-x-2">
                  <button
                    onClick={() => fetchPaginatedWorklist(worklistPage)}
                    disabled={loadingWorklist}
                    className="bg-white hover:bg-[#F8FAFD] border border-[#DCE4EE] text-[#102A43] px-3 py-1.5 text-xs font-semibold rounded-lg flex items-center space-x-1.5 transition-colors cursor-pointer"
                  >
                    <RefreshCw className={`w-3.5 h-3.5 ${loadingWorklist ? 'animate-spin text-[#2563EB]' : ''}`} />
                    <span>Refresh Queue</span>
                  </button>
                  <button
                    onClick={handleNewCaseClick}
                    className="px-3.5 py-1.5 bg-[#2563EB] hover:bg-[#1D4ED8] text-white rounded-lg font-semibold text-xs flex items-center space-x-1.5 cursor-pointer shadow-xs transition-colors"
                  >
                    <Plus className="w-3.5 h-3.5" />
                    <span>Create Case</span>
                  </button>
                </div>
              </div>

              {/* Filter and Sorting Controls */}
              <div className="bg-[#F8FAFD] p-4 rounded-xl border border-[#DCE4EE] space-y-3 text-xs">
                <div className="flex flex-wrap items-center justify-between gap-3">
                  {/* Search Bar */}
                  <div className="flex items-center space-x-2 flex-1 min-w-[240px]">
                    <Search className="w-4 h-4 text-[#7A8B9E]" />
                    <input
                      type="text"
                      value={worklistSearch}
                      onChange={(e) => setWorklistSearch(e.target.value)}
                      onKeyDown={(e) => { if (e.key === 'Enter') { setWorklistPage(0); fetchPaginatedWorklist(0); } }}
                      placeholder="Search by case number or narrative..."
                      className="flex-1 px-3 py-1.5 text-xs border border-[#DCE4EE] rounded-lg bg-white text-[#172B4D] placeholder-[#94A3B8] focus:outline-none focus:border-[#2563EB] focus:ring-2 focus:ring-[#2563EB]/20"
                    />
                    <button
                      onClick={() => { setWorklistPage(0); fetchPaginatedWorklist(0); }}
                      className="bg-white hover:bg-[#F4F7FB] border border-[#DCE4EE] text-[#102A43] px-3 py-1.5 text-xs font-semibold rounded-lg cursor-pointer"
                    >
                      Filter
                    </button>
                  </div>

                  {/* Scope Toggle: Assigned Only vs All Cases */}
                  <div className="flex items-center space-x-1 bg-white p-1 rounded-lg border border-[#DCE4EE]">
                    <button
                      onClick={() => { setWorklistAssignedOnly(false); setWorklistPage(0); setTimeout(() => fetchPaginatedWorklist(0), 10); }}
                      className={`px-3 py-1 rounded-md text-xs font-semibold transition cursor-pointer ${
                        !worklistAssignedOnly ? 'bg-[#2563EB] text-white shadow-2xs' : 'text-[#52657A] hover:text-[#102A43]'
                      }`}
                    >
                      All Cases
                    </button>
                    <button
                      onClick={() => { setWorklistAssignedOnly(true); setWorklistPage(0); setTimeout(() => fetchPaginatedWorklist(0), 10); }}
                      className={`px-3 py-1 rounded-md text-xs font-semibold transition cursor-pointer ${
                        worklistAssignedOnly ? 'bg-[#2563EB] text-white shadow-2xs' : 'text-[#52657A] hover:text-[#102A43]'
                      }`}
                    >
                      My Cases Only
                    </button>
                  </div>
                </div>

                {/* State and Priority Selectors */}
                <div className="flex flex-wrap items-center gap-3 pt-2 border-t border-[#DCE4EE]">
                  <div className="flex items-center space-x-2">
                    <Filter className="w-3.5 h-3.5 text-[#7A8B9E]" />
                    <span className="text-[#52657A] font-medium">State:</span>
                    <select
                      value={worklistWorkflowFilter}
                      onChange={(e) => {
                        setWorklistWorkflowFilter(e.target.value);
                        setWorklistPage(0);
                      }}
                      className="px-2.5 py-1.5 rounded-lg border border-[#DCE4EE] bg-white text-xs text-[#172B4D] focus:outline-none focus:border-[#2563EB]"
                    >
                      <option value="">All States</option>
                      <option value="Data Entry">Data Entry</option>
                      <option value="Quality Review">Quality Review</option>
                      <option value="Medical Review">Medical Review</option>
                      <option value="Draft">Draft</option>
                      <option value="Closed">Closed</option>
                    </select>
                  </div>

                  <div className="flex items-center space-x-2">
                    <span className="text-[#52657A] font-medium">Priority:</span>
                    <select
                      value={worklistPriorityFilter}
                      onChange={(e) => {
                        setWorklistPriorityFilter(e.target.value);
                        setWorklistPage(0);
                      }}
                      className="px-2.5 py-1.5 rounded-lg border border-[#DCE4EE] bg-white text-xs text-[#172B4D] focus:outline-none focus:border-[#2563EB]"
                    >
                      <option value="">All Priorities</option>
                      <option value="Routine">Routine</option>
                      <option value="Priority">Priority</option>
                      <option value="Expedited">Expedited</option>
                    </select>
                  </div>

                  <div className="flex items-center space-x-2">
                    <ArrowUpDown className="w-3.5 h-3.5 text-[#7A8B9E]" />
                    <span className="text-[#52657A] font-medium">Sort:</span>
                    <select
                      value={worklistSortBy}
                      onChange={(e) => {
                        setWorklistSortBy(e.target.value);
                      }}
                      className="px-2.5 py-1.5 rounded-lg border border-[#DCE4EE] bg-white text-xs text-[#172B4D] focus:outline-none focus:border-[#2563EB]"
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
                      className="px-2.5 py-1.5 rounded-lg border border-[#DCE4EE] bg-white text-xs text-[#172B4D] focus:outline-none focus:border-[#2563EB]"
                    >
                      <option value="desc">Desc</option>
                      <option value="asc">Asc</option>
                    </select>
                  </div>

                  <button
                    onClick={() => fetchPaginatedWorklist(0)}
                    className="px-3.5 py-1.5 bg-[#102A43] hover:bg-[#0B1F33] text-white rounded-lg text-xs font-semibold cursor-pointer ml-auto transition-colors"
                  >
                    Apply Filters
                  </button>
                </div>
              </div>

              {worklistError && (
                <div className="p-3 bg-[#FEF2F2] border border-[#FECACA] text-xs text-[#991B1B] rounded-lg flex items-center space-x-2">
                  <AlertCircle className="w-4 h-4 text-[#DC2626] shrink-0" />
                  <span>{worklistError}</span>
                </div>
              )}

              {/* Worklist Case Table */}
              {loadingWorklist ? (
                <div className="p-10 text-center text-xs text-[#52657A] flex items-center justify-center space-x-2 bg-white rounded-lg">
                  <RefreshCw className="w-4 h-4 animate-spin text-[#2563EB]" />
                  <span>Querying paginated safety cases from PostgreSQL...</span>
                </div>
              ) : (!worklistData || worklistData.content.length === 0) ? (
                <div className="p-10 text-center text-xs text-[#52657A] bg-[#F8FAFD] border border-dashed border-[#DCE4EE] rounded-xl">
                  <ListOrdered className="w-8 h-8 text-[#94A3B8] mx-auto mb-2" />
                  <p className="font-semibold text-[#102A43]">No safety cases match the selected worklist filters.</p>
                  <p className="text-[#52657A] text-xs mt-1 max-w-md mx-auto">
                    Adjust your search keyword or workflow filter above, or create a new safety case.
                  </p>
                  <button
                    onClick={handleNewCaseClick}
                    className="mt-4 px-4 py-2 bg-[#2563EB] hover:bg-[#1D4ED8] text-white rounded-lg font-semibold text-xs inline-flex items-center space-x-1.5 cursor-pointer shadow-xs transition-colors"
                  >
                    <Plus className="w-3.5 h-3.5" />
                    <span>Create Safety Case</span>
                  </button>
                </div>
              ) : (
                <div className="overflow-x-auto border border-[#DCE4EE] rounded-xl shadow-2xs">
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
                        <tr key={c.id} className="hover:bg-[#F0F6FD] cursor-pointer" onClick={() => handleOpenCase(c.id)}>
                          <td className="font-semibold text-[#102A43] whitespace-nowrap">
                            ({c.countryCode}) {c.caseNumber}{' '}
                            <span className="text-[11px] text-[#7A8B9E] font-mono">v{c.versionNumber}</span>
                          </td>
                          <td className="whitespace-nowrap">{c.reportType}</td>
                          <td className="font-medium text-[#172B4D] truncate max-w-[180px]" title={c.primaryProduct}>
                            {c.primaryProduct}
                          </td>
                          <td className="text-[#52657A] truncate max-w-[180px]" title={c.primaryEvent}>
                            {c.primaryEvent}
                          </td>
                          <td>
                            <span className={`px-2 py-0.5 rounded text-[11px] font-semibold ${
                              c.workflowState === 'Draft' ? 'bg-[#FFFBEB] text-[#92400E] border border-[#FDE68A]' :
                              c.workflowState === 'Quality Review' ? 'bg-[#EFF6FF] text-[#1E40AF] border border-[#BFDBFE]' :
                              c.workflowState === 'Medical Review' ? 'bg-[#FAF5FF] text-[#6B21A8] border border-[#E9D5FF]' :
                              'bg-emerald-50 text-emerald-800 border border-emerald-200'
                            }`}>
                              {c.workflowState}
                            </span>
                          </td>
                          <td>
                            <span className={`px-2 py-0.5 rounded text-[11px] font-semibold ${
                              c.priority === 'Expedited' ? 'bg-[#FEF2F2] text-[#991B1B] border border-[#FECACA]' :
                              c.priority === 'Priority' ? 'bg-[#FFFBEB] text-[#92400E] border border-[#FDE68A]' :
                              'bg-[#F1F5F9] text-[#334155] border border-[#CBD5E1]'
                            }`}>
                              {c.priority}
                            </span>
                          </td>
                          <td className="font-mono text-[#52657A]">{c.assignedToUsername || '-'}</td>
                          <td>
                            <button
                              type="button"
                              onClick={(e) => { e.stopPropagation(); handleOpenCase(c.id); }}
                              className="px-3 py-1 text-xs font-semibold bg-[#2563EB] hover:bg-[#1D4ED8] text-white rounded-md transition-colors"
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
                <div className="flex flex-wrap items-center justify-between gap-3 pt-3 border-t border-[#DCE4EE] text-xs text-[#52657A]">
                  <div className="flex items-center space-x-3">
                    <span>Showing page <strong>{worklistData.number + 1}</strong> of <strong>{worklistData.totalPages}</strong> ({worklistData.totalElements} cases)</span>
                    <select
                      value={worklistSize}
                      onChange={(e) => {
                        const newSize = Number(e.target.value);
                        setWorklistSize(newSize);
                        setWorklistPage(0);
                      }}
                      className="px-2.5 py-1 rounded-lg border border-[#DCE4EE] bg-white text-xs text-[#172B4D]"
                    >
                      <option value="5">5 per page</option>
                      <option value="10">10 per page</option>
                      <option value="20">20 per page</option>
                    </select>
                  </div>

                  <div className="flex items-center space-x-1.5">
                    <button
                      onClick={() => {
                        const prevPage = Math.max(0, worklistPage - 1);
                        setWorklistPage(prevPage);
                        fetchPaginatedWorklist(prevPage);
                      }}
                      disabled={worklistData.first}
                      className="bg-white hover:bg-[#F8FAFD] border border-[#DCE4EE] text-[#102A43] px-3 py-1.5 text-xs font-semibold rounded-lg flex items-center space-x-1 disabled:opacity-40 cursor-pointer"
                    >
                      <ChevronLeft className="w-3.5 h-3.5" />
                      <span>Previous</span>
                    </button>

                    <span className="px-3 py-1.5 font-mono font-bold text-[#102A43] bg-[#EFF6FF] rounded-lg border border-[#BFDBFE]">
                      {worklistData.number + 1}
                    </span>

                    <button
                      onClick={() => {
                        const nextPage = Math.min(worklistData.totalPages - 1, worklistPage + 1);
                        setWorklistPage(nextPage);
                        fetchPaginatedWorklist(nextPage);
                      }}
                      disabled={worklistData.last}
                      className="bg-white hover:bg-[#F8FAFD] border border-[#DCE4EE] text-[#102A43] px-3 py-1.5 text-xs font-semibold rounded-lg flex items-center space-x-1 disabled:opacity-40 cursor-pointer"
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
            <div className="bg-white rounded-xl border border-[#DCE4EE] p-5 sm:p-6 space-y-4 shadow-xs">
              <div className="flex items-center justify-between border-b border-[#DCE4EE] pb-3">
                <span className="text-base font-bold text-[#102A43] flex items-center space-x-2.5">
                  <MessageSquare className="w-5 h-5 text-[#2563EB]" />
                  <span>Contact Log Management</span>
                </span>
                <span className="text-xs font-mono text-[#7A8B9E]">Argus Reference Section C</span>
              </div>
              <p className="text-xs text-[#52657A]">
                Tracks all outbound communications (telephone, email, queries) with healthcare professionals and consumers regarding adverse event follow-up inquiries.
              </p>
              <p className="text-xs text-[#7A8B9E]">Personal records are shown when you created them or are responsible for them.</p>
              {interactionError && <p className="text-xs text-[#DC2626] font-medium">{interactionError}</p>}
              <div className="grid gap-3 md:grid-cols-4 text-xs">
                <input value={interactionCaseId} onChange={e => setInteractionCaseId(e.target.value)} placeholder="Case ID" className="border border-[#DCE4EE] p-2 rounded-lg bg-white text-[#172B4D]" />
                <select value={contactType} onChange={e => setContactType(e.target.value)} className="border border-[#DCE4EE] p-2 rounded-lg bg-white text-[#172B4D]"><option>Email</option><option>Telephone</option><option>Portal</option><option>Letter</option><option>Other</option></select>
                <input value={contactDescription} onChange={e => setContactDescription(e.target.value)} placeholder="Description" className="border border-[#DCE4EE] p-2 rounded-lg md:col-span-2 bg-white text-[#172B4D]" />
                <button type="button" onClick={() => createInteraction('contact')} className="bg-[#2563EB] hover:bg-[#1D4ED8] text-white px-3 py-2 rounded-lg text-xs font-semibold cursor-pointer">Save contact log</button>
              </div>
              <div className="flex gap-2">
                <button type="button" onClick={fetchInteractions} className="bg-white hover:bg-[#F8FAFD] border border-[#DCE4EE] text-[#102A43] px-3 py-1.5 rounded-lg text-xs font-semibold cursor-pointer">Refresh</button>
                {interactionLoading && <span className="text-xs text-[#7A8B9E] flex items-center">Loading...</span>}
              </div>
              {contactLogs.length === 0 ? <div className="p-6 text-center text-xs text-[#7A8B9E] bg-[#F8FAFD] border border-dashed border-[#DCE4EE] rounded-lg">No personal contact logs.</div> : <div className="space-y-2">{contactLogs.map(log => <div key={log.id} className="border border-[#DCE4EE] p-3 rounded-lg text-xs bg-white"><b>{log.caseNumber}</b> &bull; {log.contactType} &bull; {new Date(log.contactAt).toLocaleString()}<div className="mt-1 text-[#52657A]">{log.description}</div></div>)}</div>}
            </div>
          )}

          {/* VIEW 5: ACTION ITEMS VIEW */}
          {activeTab === 'action-items' && (
            <div className="bg-white rounded-xl border border-[#DCE4EE] p-5 sm:p-6 space-y-4 shadow-xs">
              <div className="flex items-center justify-between border-b border-[#DCE4EE] pb-3">
                <span className="text-base font-bold text-[#102A43] flex items-center space-x-2.5">
                  <CheckSquare2 className="w-5 h-5 text-[#2563EB]" />
                  <span>Action Items &amp; Case Tasks</span>
                </span>
                <span className="text-xs font-mono text-[#7A8B9E]">Argus Reference Section D</span>
              </div>
              <p className="text-xs text-[#52657A]">
                Assigns action items, queries, targeted questionnaires, and medical review milestones with due dates.
              </p>
              <p className="text-xs text-[#7A8B9E]">Overdue is derived from due date and non-completed/non-cancelled status; it is not stored as a stale flag.</p>
              {interactionError && <p className="text-xs text-[#DC2626] font-medium">{interactionError}</p>}
              <div className="grid gap-3 md:grid-cols-4 text-xs">
                <input value={interactionCaseId} onChange={e => setInteractionCaseId(e.target.value)} placeholder="Case ID" className="border border-[#DCE4EE] p-2 rounded-lg bg-white text-[#172B4D]" />
                <input value={actionDescription} onChange={e => setActionDescription(e.target.value)} placeholder="Description" className="border border-[#DCE4EE] p-2 rounded-lg bg-white text-[#172B4D]" />
                <input type="datetime-local" value={actionDueAt} onChange={e => setActionDueAt(e.target.value)} className="border border-[#DCE4EE] p-2 rounded-lg bg-white text-[#172B4D]" />
                <button type="button" onClick={() => createInteraction('action')} className="bg-[#2563EB] hover:bg-[#1D4ED8] text-white px-3 py-2 rounded-lg text-xs font-semibold cursor-pointer">Save action item</button>
              </div>
              <div className="flex gap-2">
                <button type="button" onClick={fetchInteractions} className="bg-white hover:bg-[#F8FAFD] border border-[#DCE4EE] text-[#102A43] px-3 py-1.5 rounded-lg text-xs font-semibold cursor-pointer">Refresh</button>
                <button type="button" onClick={() => { setShowOverdueOnly(true); }} className="bg-white hover:bg-[#F8FAFD] border border-[#DCE4EE] text-[#102A43] px-3 py-1.5 rounded-lg text-xs font-semibold cursor-pointer">Overdue only</button>
              </div>
              {actionItems.length === 0 ? <div className="p-6 text-center text-xs text-[#7A8B9E] bg-[#F8FAFD] border border-dashed border-[#DCE4EE] rounded-lg">No matching action items.</div> : <div className="space-y-2">{actionItems.map(item => <div key={item.id} className={`border p-3 rounded-lg text-xs bg-white ${item.overdue ? 'border-[#FECACA] bg-[#FEF2F2]' : 'border-[#DCE4EE]'}`}><b>{item.caseNumber}</b> &bull; due {new Date(item.dueAt).toLocaleString()}<div className="mt-1 text-[#52657A]">{item.description} ({item.status}) <button type="button" onClick={() => completeActionItem(item.id)} className="text-[#2563EB] hover:underline ml-1 font-semibold cursor-pointer">Complete</button></div></div>)}</div>}
            </div>
          )}

          {/* VIEW 6: REPORTS VIEW */}
          {activeTab === 'reports' && (
            <div className="bg-white rounded-xl border border-[#DCE4EE] p-5 sm:p-6 space-y-4 shadow-xs">
              <div className="flex items-center justify-between border-b border-[#DCE4EE] pb-3">
                <span className="text-base font-bold text-[#102A43] flex items-center space-x-2.5">
                  <FileText className="w-5 h-5 text-[#2563EB]" />
                  <span>Regulatory Reports &amp; Expedited Generation (Stage 5/6)</span>
                </span>
                <span className="text-xs font-mono bg-[#F1F5F9] text-[#334155] px-2.5 py-1 rounded-md border border-[#CBD5E1]">
                  Scheduled: Stage 6
                </span>
              </div>
              <p className="text-xs text-[#52657A] leading-relaxed">
                Enables generation of electronic regulatory submissions including ICH E2B(R3) XML transmissions, CIOMS I forms, and FDA MedWatch 3500A documents.
              </p>
            </div>
          )}

          {/* VIEW 7: ADMINISTRATION VIEW */}
          {activeTab === 'admin' && user?.role === 'ROLE_ADMIN' && (
            <div className="space-y-6">
              <div className="bg-white rounded-xl border border-[#DCE4EE] p-5 sm:p-6 shadow-xs">
                <div className="flex items-center justify-between border-b border-[#DCE4EE] pb-3 mb-4">
                  <span className="text-base font-bold text-[#102A43] flex items-center space-x-2.5">
                    <SlidersHorizontal className="w-5 h-5 text-[#2563EB]" />
                    <span>Argus Console &bull; Security &amp; Audit Administration</span>
                  </span>
                  <span className="text-xs text-emerald-800 font-semibold bg-emerald-50 px-2.5 py-1 rounded-md border border-emerald-200">
                    System: {adminStatus?.systemStatus || 'OPERATIONAL'}
                  </span>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-3 gap-4 text-xs mb-5">
                  <div className="p-4 bg-[#F8FAFD] rounded-xl border border-[#DCE4EE] flex justify-between items-center">
                    <span className="text-[#52657A]">Registered Users (PostgreSQL):</span>
                    <span className="font-bold text-[#102A43] font-mono text-sm">
                      {adminStatus?.totalUsers ?? 1}
                    </span>
                  </div>
                  <div className="p-4 bg-[#F8FAFD] rounded-xl border border-[#DCE4EE] flex justify-between items-center">
                    <span className="text-[#52657A]">Total Audit Events (audit_events):</span>
                    <span className="font-bold text-[#102A43] font-mono text-sm">
                      {adminStatus?.totalAuditEvents ?? auditLogs.length}
                    </span>
                  </div>
                  <div className="p-4 bg-[#F8FAFD] rounded-xl border border-[#DCE4EE] flex justify-between items-center">
                    <span className="text-[#52657A]">PostgreSQL Schema Migrations:</span>
                    <span className="font-bold text-emerald-700 font-mono text-sm">V1, V2 &amp; V3 Applied</span>
                  </div>
                </div>

                {/* Comprehensive Audit Table */}
                <div className="border border-[#DCE4EE] rounded-xl overflow-hidden shadow-2xs">
                  <div className="bg-[#F8FAFD] px-4 py-2.5 border-b border-[#DCE4EE] flex justify-between items-center">
                    <span className="font-bold text-xs text-[#102A43]">IMMUTABLE AUDIT TRAIL LOGS</span>
                    <button
                      onClick={fetchAuditLogs}
                      className="bg-white hover:bg-[#F4F7FB] border border-[#DCE4EE] text-[#102A43] px-2.5 py-1 text-xs font-semibold rounded-md cursor-pointer"
                    >
                      Refresh
                    </button>
                  </div>
                  <div className="overflow-x-auto max-h-96 overflow-y-auto w-full max-w-full">
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
                            <td className="font-mono text-[#7A8B9E] whitespace-nowrap">#{log.id}</td>
                            <td className="font-semibold text-[#102A43] whitespace-nowrap">{log.eventType}</td>
                            <td className="font-mono text-[#172B4D] whitespace-nowrap">{log.username || '-'}</td>
                            <td className="font-mono text-[#52657A] whitespace-nowrap">{log.ipAddress || 'local'}</td>
                            <td className="whitespace-nowrap">
                              <span
                                className={`px-2 py-0.5 rounded text-[11px] font-semibold ${
                                  log.status === 'SUCCESS'
                                    ? 'bg-emerald-50 text-emerald-800 border border-emerald-200'
                                    : log.status === 'WARNING'
                                    ? 'bg-[#FFFBEB] text-[#92400E] border border-[#FDE68A]'
                                    : 'bg-[#FEF2F2] text-[#991B1B] border border-[#FECACA]'
                                }`}
                              >
                                {log.status}
                              </span>
                            </td>
                            <td className="text-[#52657A] max-w-[200px] truncate" title={log.details || ''}>
                              {log.details || '-'}
                            </td>
                            <td className="font-mono text-[#7A8B9E] text-xs whitespace-nowrap">
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

      {/* 5. Clean Workspace Footer */}
      <footer className="bg-white border-t border-[#DCE4EE] w-full px-4 sm:px-6 py-2.5 text-xs text-[#52657A] flex flex-wrap items-center justify-between gap-3 shrink-0">
        <div className="flex items-center space-x-2">
          <span>PharmaSafe Pharmacovigilance Case Management System</span>
          <span>&bull;</span>
          <span>Inspired by Oracle Argus Safety 7.0.3</span>
          <span className="hidden sm:inline">&bull;</span>
          <span className="font-mono text-[#7A8B9E] hidden sm:inline">ICH E2B(R3) &bull; 21 CFR Part 11 Architecture</span>
        </div>
        <div className="font-mono text-[#7A8B9E] text-[11px] flex items-center space-x-3">
          <span>
            Database: <strong className="text-[#102A43]">{dbHealth?.databaseName || 'pharmasafe_db'}</strong> (
            {dbHealth?.databaseProduct || 'PostgreSQL'} {dbHealth?.databaseVersion || '18.4'})
          </span>
          <span>&bull;</span>
          <span>
            User: <strong className="text-[#102A43]">{user?.username}</strong> ({user?.role})
          </span>
        </div>
      </footer>
    </div>
  );
};

export default Stage3Dashboard;
