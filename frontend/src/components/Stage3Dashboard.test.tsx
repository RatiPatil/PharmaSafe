import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import React from 'react';
import { AuthProvider } from '../context/AuthContext';
import { Stage3Dashboard } from './Stage3Dashboard';

describe('PharmaSafe Stage 4 Enterprise Dashboard & Case Management', () => {
  const mockAdminUser = {
    id: 1,
    username: 'admin',
    role: 'ROLE_ADMIN',
    enabled: true,
    locked: false,
    createdAt: new Date().toISOString()
  };

  const mockAuditLogs = [
    {
      id: 1,
      eventType: 'LOGIN_SUCCESS',
      username: 'admin',
      ipAddress: '127.0.0.1',
      userAgent: 'Mozilla/5.0',
      status: 'SUCCESS',
      details: 'User authenticated successfully',
      createdAt: '2026-10-15T12:00:00Z'
    },
    {
      id: 2,
      eventType: 'CASE_CREATED',
      username: 'admin',
      ipAddress: '127.0.0.1',
      userAgent: 'Mozilla/5.0',
      status: 'SUCCESS',
      details: 'Created safety case PS-2026-USA-01001',
      createdAt: '2026-10-15T12:30:00Z'
    }
  ];

  const mockDbHealth = {
    status: 'UP',
    details: {
      database: 'UP',
      databaseName: 'pharmasafe_db',
      databaseUser: 'pharmasafe_user',
      databaseProduct: 'PostgreSQL',
      databaseVersion: '18.4'
    }
  };

  const mockAdminStatus = {
    totalUsers: 1,
    totalAuditEvents: 2,
    systemStatus: 'OPERATIONAL'
  };

  const mockAssignedCases = [
    {
      id: 101,
      caseNumber: 'PS-2026-USA-01001',
      countryCode: 'USA',
      reportType: 'Spontaneous',
      workflowState: 'Data Entry',
      priority: 'Routine',
      serious: true,
      fatal: false,
      primaryProduct: 'CardioGuard 50mg',
      primaryEvent: 'Angioedema',
      initialReceiptDate: '2026-10-10',
      latestReceivedDate: '2026-10-15',
      versionNumber: 1,
      assignedToUsername: 'admin',
      createdAt: '2026-10-15T10:00:00Z',
      updatedAt: '2026-10-15T10:00:00Z'
    }
  ];

  beforeEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();

    const mockFetch = vi.fn().mockImplementation((url: string) => {
      if (url.includes('/api/auth/me')) {
        return Promise.resolve({ ok: true, json: async () => mockAdminUser });
      }
      if (url.includes('/api/health')) {
        return Promise.resolve({ ok: true, json: async () => mockDbHealth });
      }
      if (url.includes('/api/admin/audit-logs')) {
        return Promise.resolve({ ok: true, json: async () => mockAuditLogs });
      }
      if (url.includes('/api/admin/status')) {
        return Promise.resolve({ ok: true, json: async () => mockAdminStatus });
      }
      if (url.includes('/api/auth/csrf')) {
        return Promise.resolve({ ok: true, json: async () => ({ token: 'mock-csrf' }) });
      }
      if (url.includes('/api/cases/assigned')) {
        return Promise.resolve({ ok: true, json: async () => mockAssignedCases });
      }
      if (url.includes('/api/cases?search=')) {
        return Promise.resolve({ ok: true, json: async () => mockAssignedCases });
      }
      if (url.includes('/api/cases/101')) {
        return Promise.resolve({
          ok: true,
          json: async () => ({
            ...mockAssignedCases[0],
            reporters: [{ primary: true, qualification: 'Physician', lastName: 'Smith', countryCode: 'USA' }],
            products: [{ productType: 'Suspect', productName: 'CardioGuard 50mg' }],
            events: [{ eventVerbatim: 'Angioedema', onsetDate: '2026-10-10', outcome: 'Recovering/Resolving', serious: true }],
            medicalHistoryList: [],
            references: [],
            attachments: []
          })
        });
      }
      if (url.includes('/api/cases/by-number/PS-2026-USA-01001')) {
        return Promise.resolve({
          ok: true,
          json: async () => ({
            ...mockAssignedCases[0],
            reporters: [{ primary: true, qualification: 'Physician', lastName: 'Smith', countryCode: 'USA' }],
            products: [{ productType: 'Suspect', productName: 'CardioGuard 50mg' }],
            events: [{ eventVerbatim: 'Angioedema', onsetDate: '2026-10-10', outcome: 'Recovering/Resolving', serious: true }],
            medicalHistoryList: [],
            references: [],
            attachments: []
          })
        });
      }
      if (url.includes('/api/cases/by-number/')) {
        return Promise.resolve({ ok: false, status: 404 });
      }
      if (url.includes('/api/cases')) {
        return Promise.resolve({ ok: true, json: async () => mockAssignedCases });
      }
      return Promise.resolve({ ok: false, status: 404 });
    });
    vi.stubGlobal('fetch', mockFetch);
  });

  it('renders the Argus-inspired top header and quick launch toolbar', async () => {
    render(
      <AuthProvider>
        <Stage3Dashboard />
      </AuthProvider>
    );

    await waitFor(() => {
      expect(screen.getByText('PharmaSafe')).toBeInTheDocument();
      expect(screen.getAllByText(/Quick Launch/i)[0]).toBeInTheDocument();
    });

    // Verify Quick Launch buttons exist
    expect(screen.getAllByRole('button', { name: 'New Case' })[0]).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Open Case' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Worklist' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Validation Check' })).toBeInTheDocument();
  });

  it('renders Section A: Case Quick Launch / Search Case with live query execution', async () => {
    render(
      <AuthProvider>
        <Stage3Dashboard />
      </AuthProvider>
    );

    await waitFor(() => {
      expect(screen.getByText(/Search Case • Case Quick Launch/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/Case Quick Launch:/i)).toBeInTheDocument();
    });

    const searchInput = screen.getByLabelText(/Case Quick Launch:/i);
    const searchButton = screen.getByRole('button', { name: /Quick Search/i });

    // Submit search without entering text
    fireEvent.click(searchButton);
    expect(screen.getByText(/Please enter a case number or search query/i)).toBeInTheDocument();

    // Submit search with query
    fireEvent.change(searchInput, { target: { value: 'PS-2026' } });
    fireEvent.click(searchButton);

    await waitFor(() => {
      expect(screen.getByText(/Found 1 case\(s\) matching/i)).toBeInTheDocument();
      expect(screen.getAllByText(/CardioGuard 50mg/i)[0]).toBeInTheDocument();
    });
  });

  it('renders Section B: Cases Assigned with exact Argus columns and live PostgreSQL data', async () => {
    render(
      <AuthProvider>
        <Stage3Dashboard />
      </AuthProvider>
    );

    await waitFor(() => {
      expect(screen.getByText('Cases Assigned')).toBeInTheDocument();
    });

    // Check table headers
    const countryCaseHeaders = screen.getAllByText('Country / Case Number');
    expect(countryCaseHeaders.length).toBeGreaterThanOrEqual(1);
    expect(countryCaseHeaders[0]).toBeInTheDocument();
    expect(screen.getByText('Report Type')).toBeInTheDocument();
    expect(screen.getByText('Product')).toBeInTheDocument();
    expect(screen.getByText('Workflow State')).toBeInTheDocument();
    expect(screen.getByText('Event')).toBeInTheDocument();

    // Verify assigned case row loaded from PostgreSQL mock
    await waitFor(() => {
      expect(screen.getByText('PS-2026-USA-01001')).toBeInTheDocument();
      expect(screen.getByText('CardioGuard 50mg')).toBeInTheDocument();
      expect(screen.getByText('Angioedema')).toBeInTheDocument();
    });
  });

  it('renders Section C (Contact Log Entries) & Section D (Action Item Entries) with exact columns', async () => {
    render(
      <AuthProvider>
        <Stage3Dashboard />
      </AuthProvider>
    );

    await waitFor(() => {
      expect(screen.getByText('Contact Log Entries')).toBeInTheDocument();
      expect(screen.getByText('Action Item Entries')).toBeInTheDocument();
    });

    // Contact log columns and empty state
    expect(screen.getByText('Contact Date')).toBeInTheDocument();
    expect(screen.getByText(/No contact log entries recorded yet/i)).toBeInTheDocument();

    // Action item columns and empty state
    expect(screen.getByText('Due On')).toBeInTheDocument();
    expect(screen.getByText(/No pending action items/i)).toBeInTheDocument();
  });

  it('renders Section E: Live PostgreSQL Database & Audit Trail data for Admin', async () => {
    render(
      <AuthProvider>
        <Stage3Dashboard />
      </AuthProvider>
    );

    await waitFor(() => {
      expect(screen.getByText(/AUTHENTICATION AUDIT TRAIL • LIVE POSTGRESQL/i)).toBeInTheDocument();
      expect(screen.getByText('LOGIN_SUCCESS')).toBeInTheDocument();
      expect(screen.getByText('CASE_CREATED')).toBeInTheDocument();
    });

    // Verify verified database status info is displayed
    expect(screen.getAllByText('pharmasafe_db')[0]).toBeInTheDocument();
    expect(screen.getAllByText('pharmasafe_user')[0]).toBeInTheDocument();
  });

  it('navigates to Initial Case Entry and renders ICH E2B tabs', async () => {
    render(
      <AuthProvider>
        <Stage3Dashboard />
      </AuthProvider>
    );

    await waitFor(() => {
      expect(screen.getByRole('tab', { name: /Case Entry/i })).toBeInTheDocument();
    });

    // Navigate to Case Entry tab
    fireEvent.click(screen.getByRole('tab', { name: /Case Entry/i }));

    // Verify Case Entry Form renders with Argus tabs
    await waitFor(() => {
      expect(screen.getByText(/Initial Case Entry \(Argus Safety Format\)/i)).toBeInTheDocument();
      expect(screen.getByText('1. General Info')).toBeInTheDocument();
      expect(screen.getByText('2. Patient')).toBeInTheDocument();
      expect(screen.getByText(/3. Reporters/i)).toBeInTheDocument();
      expect(screen.getByText(/4. Products/i)).toBeInTheDocument();
      expect(screen.getByText(/5. Adverse Events/i)).toBeInTheDocument();
    });
  });

  it('clicking a planned action (Print Case) displays a clear feedback message', async () => {
    render(
      <AuthProvider>
        <Stage3Dashboard />
      </AuthProvider>
    );

    await waitFor(() => {
      expect(screen.getByRole('button', { name: 'Print Case' })).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole('button', { name: 'Print Case' }));

    expect(screen.getByText(/Scheduled for Stage 5. Action is currently disabled./i)).toBeInTheDocument();
  });

  it('clicking New Case in Quick Launch opens the Case Entry form', async () => {
    render(
      <AuthProvider>
        <Stage3Dashboard />
      </AuthProvider>
    );

    await waitFor(() => {
      expect(screen.getByTestId('quick-launch-new-case')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByTestId('quick-launch-new-case'));

    await waitFor(() => {
      expect(screen.getByText(/Initial Case Entry \(Argus Safety Format\)/i)).toBeInTheDocument();
      expect(screen.getByText('1. General Info')).toBeInTheDocument();
      expect(screen.getAllByRole('button', { name: /Save Case/i })[0]).toBeInTheDocument();
    });
  });
});
