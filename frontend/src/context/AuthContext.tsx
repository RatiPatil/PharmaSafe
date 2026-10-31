import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import type { ReactNode } from 'react';
import type { User, AuthResponse } from '../types/auth';

import { getApiUrl, getCsrfToken, refreshCsrfToken, setCsrfToken, fetchWithTimeout, SESSION_EXPIRED_EVENT } from '../config/api';

interface AuthContextType {
  user: User | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  isInitialLoading: boolean;
  error: string | null;
  isConnectionError: boolean;
  login: (username: string, password: string) => Promise<boolean>;
  logout: () => Promise<void>;
  clearError: () => void;
  retryAuth: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

// Retained for backward compatibility with existing imports
export function getXsrfToken(): string | null {
  return getCsrfToken();
}

export const AuthProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<User | null>(null);
  const [isInitialLoading, setIsInitialLoading] = useState<boolean>(true);
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const [isConnectionError, setIsConnectionError] = useState<boolean>(false);

  // Initialize and restore session on page mount
  const restoreSession = useCallback(async () => {
    setIsInitialLoading(true);
    try {
      const res = await fetchWithTimeout(getApiUrl('/api/auth/me'), {
        method: 'GET',
        headers: { 'Accept': 'application/json' },
        credentials: 'include'
      });

      if (res.ok) {
        const userData: User = await res.json();
        setUser(userData);
        setError(null);
        setIsConnectionError(false);
        refreshCsrfToken().catch(() => {});
      } else {
        setUser(null);
        // A 401 from the session endpoint before login is an expected unauthenticated state;
        // show the login page normally without displaying an error alert.
        if (res.status !== 401) {
          setError('Session verification failed. Please try signing in again.');
          setIsConnectionError(false);
        } else {
          setError(null);
          setIsConnectionError(false);
        }
      }
    } catch {
      setUser(null);
      setError('The authentication service did not respond. Please try signing in again; the server may be starting.');
      setIsConnectionError(true);
    } finally {
      setIsInitialLoading(false);
    }
  }, []);

  useEffect(() => {
    restoreSession();
  }, [restoreSession]);

  useEffect(() => {
    const expire = () => {
      setUser(null); setCsrfToken(null); setIsLoading(false);
      setError('Your session expired or your account access changed. Sign in again to continue.');
      setIsConnectionError(false);
    };
    window.addEventListener(SESSION_EXPIRED_EVENT, expire);
    return () => window.removeEventListener(SESSION_EXPIRED_EVENT, expire);
  }, []);

  const login = async (username: string, password: string): Promise<boolean> => {
    setIsLoading(true);
    setError(null);
    setIsConnectionError(false);

    try {
      const xsrf = getCsrfToken();
      const headers: Record<string, string> = {
        'Content-Type': 'application/json',
        'Accept': 'application/json'
      };
      if (xsrf) {
        headers['X-XSRF-TOKEN'] = xsrf;
      }

      const res = await fetchWithTimeout(getApiUrl('/api/auth/login'), {
        method: 'POST',
        headers,
        body: JSON.stringify({ username, password }),
        credentials: 'include'
      });

      // Guard against non-JSON responses (e.g. static host fallback returning HTML on mismatched route)
      const contentType = res.headers && typeof res.headers.get === 'function' ? res.headers.get('content-type') : null;
      if (contentType && !contentType.includes('application/json')) {
        setError('Unable to reach authentication server. Please verify backend service.');
        setIsConnectionError(true);
        return false;
      }

      const data: AuthResponse = await res.json();

      if (res.ok && data.success && data.user) {
        const sessionCheck = await fetchWithTimeout(getApiUrl('/api/auth/me'), { credentials: 'include', headers: { Accept: 'application/json' } });
        if (!sessionCheck.ok) {
          setUser(null); setCsrfToken(null);
          setError('Your credentials were accepted, but the browser session could not be established. Check cookie settings or contact your administrator.');
          return false;
        }
        const verifiedUser: User = await sessionCheck.json();
        if (verifiedUser.id !== data.user.id) {
          setUser(null); setError('Could not verify the signed-in account. Sign in again.'); return false;
        }
        setCsrfToken(null);
        await refreshCsrfToken();
        setUser(verifiedUser);
        setError(null);
        setIsConnectionError(false);
        // Refresh CSRF token for the authenticated session
        refreshCsrfToken().catch(() => {});
        return true;
      } else {
        setIsConnectionError(false);
        setError(data.message || 'Authentication failed. Please check your credentials.');
        return false;
      }
    } catch {
      setIsConnectionError(true);
      setError('Unable to reach authentication server. Please verify backend service.');
      return false;
    } finally {
      setIsLoading(false);
    }
  };

  const logout = async (): Promise<void> => {
    setIsLoading(true);
    try {
      const xsrf = getCsrfToken();
      const headers: Record<string, string> = {
        'Accept': 'application/json'
      };
      if (xsrf) {
        headers['X-XSRF-TOKEN'] = xsrf;
      }

      await fetchWithTimeout(getApiUrl('/api/auth/logout'), {
        method: 'POST',
        headers,
        credentials: 'include'
      });
    } catch {
      // Ignore network errors on logout
    } finally {
      setUser(null);
      setError(null);
      setIsConnectionError(false);
      setCsrfToken(null);
      setIsLoading(false);
    }
  };

  const clearError = () => {
    setError(null);
    setIsConnectionError(false);
  };

  const retryAuth = async () => {
    await restoreSession();
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        isAuthenticated: !!user,
        isLoading,
        isInitialLoading,
        error,
        isConnectionError,
        login,
        logout,
        clearError,
        retryAuth
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = (): AuthContextType => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};
