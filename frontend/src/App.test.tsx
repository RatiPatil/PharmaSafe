import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import App from './App';

describe('PharmaSafe Frontend - Stage 2 Authentication', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it('shows a recoverable login screen when session restoration cannot reach the server', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));
    render(<App />);
    await waitFor(() => {
      expect(screen.getByRole('button', { name: /Log In to PharmaSafe/i })).toBeEnabled();
      expect(screen.getByRole('alert')).toHaveTextContent('authentication service did not respond');
    });
    expect(screen.queryByText('Verifying PharmaSafe Session Security...')).not.toBeInTheDocument();
  });

  it('renders login page with username, password, and required indicators when unauthenticated', async () => {
    // Mock /api/auth/csrf and /api/auth/me as 401 unauthenticated
    const mockFetch = vi.fn().mockImplementation((url: string) => {
      if (url.includes('/api/auth/csrf')) {
        return Promise.resolve({
          ok: true,
          json: async () => ({ token: 'mock-csrf', headerName: 'X-XSRF-TOKEN', parameterName: '_csrf' })
        });
      }
      return Promise.resolve({ ok: false, status: 401 });
    });
    vi.stubGlobal('fetch', mockFetch);

    render(<App />);

    await waitFor(() => {
      expect(screen.getByText(/System User Authentication/i)).toBeInTheDocument();
    });

    expect(screen.getByLabelText(/Username/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/^Password/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Log In to PharmaSafe/i })).toBeInTheDocument();
  });

  it('validates required fields on empty submit', async () => {
    const mockFetch = vi.fn().mockResolvedValue({ ok: false, status: 401 });
    vi.stubGlobal('fetch', mockFetch);

    render(<App />);

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /Log In to PharmaSafe/i })).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole('button', { name: /Log In to PharmaSafe/i }));

    expect(screen.getByText('Username is required')).toBeInTheDocument();
    expect(screen.getByText('Password is required')).toBeInTheDocument();
  });

  it('toggles password visibility with show/hide control', async () => {
    const mockFetch = vi.fn().mockResolvedValue({ ok: false, status: 401 });
    vi.stubGlobal('fetch', mockFetch);

    render(<App />);

    await waitFor(() => {
      expect(screen.getByLabelText(/^Password/i)).toBeInTheDocument();
    });

    const passwordInput = screen.getByLabelText(/^Password/i) as HTMLInputElement;
    expect(passwordInput.type).toBe('password');

    const toggleButton = screen.getByRole('button', { name: /Show password/i });
    fireEvent.click(toggleButton);

    expect(passwordInput.type).toBe('text');

    fireEvent.click(screen.getByRole('button', { name: /Hide password/i }));
    expect(passwordInput.type).toBe('password');
  });

  it('displays invalid credentials alert on 401 response', async () => {
    const mockFetch = vi.fn().mockImplementation((url: string, options?: RequestInit) => {
      if (url.includes('/api/auth/me') || url.includes('/api/auth/csrf')) {
        return Promise.resolve({ ok: false, status: 401 });
      }
      if (url.includes('/api/auth/login') && options?.method === 'POST') {
        return Promise.resolve({
          ok: false,
          status: 401,
          json: async () => ({ success: false, message: 'Invalid username or password' })
        });
      }
      return Promise.resolve({ ok: false, status: 404 });
    });
    vi.stubGlobal('fetch', mockFetch);

    render(<App />);

    await waitFor(() => {
      expect(screen.getByLabelText(/Username/i)).toBeInTheDocument();
    });

    fireEvent.change(screen.getByLabelText(/Username/i), { target: { value: 'testuser' } });
    fireEvent.change(screen.getByLabelText(/^Password/i), { target: { value: 'WrongPass' } });
    fireEvent.click(screen.getByRole('button', { name: /Log In to PharmaSafe/i }));

    await waitFor(() => {
      expect(screen.getByText('Authentication Failure')).toBeInTheDocument();
      expect(screen.getByText('Invalid username or password')).toBeInTheDocument();
    });
  });

  it('displays backend connection failure alert with retry button when login times out or fails network', async () => {
    let attempts = 0;
    const mockFetch = vi.fn().mockImplementation((url: string, options?: RequestInit) => {
      if (url.includes('/api/auth/me') || url.includes('/api/auth/csrf')) {
        return Promise.resolve({ ok: false, status: 401 });
      }
      if (url.includes('/api/auth/login') && options?.method === 'POST') {
        attempts++;
        if (attempts === 1) {
          return Promise.reject(new TypeError('Failed to fetch'));
        }
        return Promise.resolve({
          ok: false,
          status: 401,
          json: async () => ({ success: false, message: 'Invalid username or password' })
        });
      }
      return Promise.resolve({ ok: false, status: 404 });
    });
    vi.stubGlobal('fetch', mockFetch);

    render(<App />);

    await waitFor(() => {
      expect(screen.getByLabelText(/Username/i)).toBeInTheDocument();
    });

    fireEvent.change(screen.getByLabelText(/Username/i), { target: { value: 'testuser' } });
    fireEvent.change(screen.getByLabelText(/^Password/i), { target: { value: 'Pass123!' } });
    fireEvent.click(screen.getByRole('button', { name: /Log In to PharmaSafe/i }));

    await waitFor(() => {
      expect(screen.getByText(/Unable to reach authentication server\. Please verify backend service\./i)).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /Retry Authentication/i })).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole('button', { name: /Retry Authentication/i }));

    await waitFor(() => {
      expect(attempts).toBe(2);
      expect(screen.getByText('Invalid username or password')).toBeInTheDocument();
    });
  });

  it('displays locked account warning when account lockout is returned', async () => {
    const mockFetch = vi.fn().mockImplementation((url: string, options?: RequestInit) => {
      if (url.includes('/api/auth/me') || url.includes('/api/auth/csrf')) {
        return Promise.resolve({ ok: false, status: 401 });
      }
      if (url.includes('/api/auth/login') && options?.method === 'POST') {
        return Promise.resolve({
          ok: false,
          status: 423,
          json: async () => ({
            success: false,
            message: 'Account has been locked due to 3 consecutive failed login attempts. Please contact an administrator.'
          })
        });
      }
      return Promise.resolve({ ok: false, status: 404 });
    });
    vi.stubGlobal('fetch', mockFetch);

    render(<App />);

    await waitFor(() => {
      expect(screen.getByLabelText(/Username/i)).toBeInTheDocument();
    });

    fireEvent.change(screen.getByLabelText(/Username/i), { target: { value: 'lockeduser' } });
    fireEvent.change(screen.getByLabelText(/^Password/i), { target: { value: 'AnyPass' } });
    fireEvent.click(screen.getByRole('button', { name: /Log In to PharmaSafe/i }));

    await waitFor(() => {
      expect(screen.getByText('Account Locked')).toBeInTheDocument();
      expect(screen.getByText(/3 consecutive failed login attempts/i)).toBeInTheDocument();
    });
  });

  it('navigates to dashboard on successful login and logs out cleanly', async () => {
    let isAuthenticated = false;

    const mockFetch = vi.fn().mockImplementation((url: string, options?: RequestInit) => {
      if (url.includes('/api/auth/csrf')) {
        return Promise.resolve({ ok: true, json: async () => ({ token: 'mock-csrf' }) });
      }
      if (url.includes('/api/auth/me')) {
        if (isAuthenticated) {
          return Promise.resolve({
            ok: true,
            json: async () => ({
              id: 1,
              username: 'sysadmin',
              role: 'ROLE_ADMIN',
              enabled: true,
              locked: false,
              createdAt: new Date().toISOString()
            })
          });
        }
        return Promise.resolve({ ok: false, status: 401 });
      }
      if (url.includes('/api/auth/login') && options?.method === 'POST') {
        isAuthenticated = true;
        return Promise.resolve({
          ok: true,
          json: async () => ({
            success: true,
            message: 'Authentication successful',
            user: {
              id: 1,
              username: 'sysadmin',
              role: 'ROLE_ADMIN',
              enabled: true,
              locked: false,
              createdAt: new Date().toISOString()
            }
          })
        });
      }
      if (url.includes('/api/admin/audit-logs')) {
        return Promise.resolve({ ok: true, json: async () => [] });
      }
      if (url.includes('/api/auth/logout') && options?.method === 'POST') {
        isAuthenticated = false;
        return Promise.resolve({
          ok: true,
          json: async () => ({ success: true, message: 'Logout successful' })
        });
      }
      return Promise.resolve({ ok: false, status: 404 });
    });
    vi.stubGlobal('fetch', mockFetch);

    render(<App />);

    await waitFor(() => {
      expect(screen.getByLabelText(/Username/i)).toBeInTheDocument();
    });

    // Login
    fireEvent.change(screen.getByLabelText(/Username/i), { target: { value: 'sysadmin' } });
    fireEvent.change(screen.getByLabelText(/^Password/i), { target: { value: 'ValidPass123!' } });
    fireEvent.click(screen.getByRole('button', { name: /Log In to PharmaSafe/i }));

    // Verify navigation to dashboard
    await waitFor(() => {
      expect(screen.getByText('STAGE 2 AUTHENTICATED')).toBeInTheDocument();
      expect(screen.getAllByText('ROLE_ADMIN')[0]).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /Log Out/i })).toBeInTheDocument();
    });

    // Logout
    fireEvent.click(screen.getByRole('button', { name: /Log Out/i }));

    // Verify return to login page
    await waitFor(() => {
      expect(screen.getByText(/System User Authentication/i)).toBeInTheDocument();
    });
  });

  it('restores existing session on initial mount if authenticated', async () => {
    const mockFetch = vi.fn().mockImplementation((url: string) => {
      if (url.includes('/api/auth/csrf')) {
        return Promise.resolve({ ok: true, json: async () => ({ token: 'mock-csrf' }) });
      }
      if (url.includes('/api/auth/me')) {
        return Promise.resolve({
          ok: true,
          json: async () => ({
            id: 2,
            username: 'caseanalyst',
            role: 'ROLE_USER',
            enabled: true,
            locked: false,
            createdAt: new Date().toISOString()
          })
        });
      }
      return Promise.resolve({ ok: false, status: 404 });
    });
    vi.stubGlobal('fetch', mockFetch);

    render(<App />);

    await waitFor(() => {
      expect(screen.getByText('STAGE 2 AUTHENTICATED')).toBeInTheDocument();
      expect(screen.getAllByText('caseanalyst')[0]).toBeInTheDocument();
      expect(screen.getAllByText('ROLE_USER')[0]).toBeInTheDocument();
    });
  });
});
