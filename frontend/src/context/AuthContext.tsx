import React, { createContext, useContext, useState, useEffect } from 'react';
import type { ReactNode } from 'react';
import type { User, AuthResponse } from '../types/auth';

import { getApiUrl, getCsrfToken, refreshCsrfToken, setCsrfToken, fetchWithTimeout } from '../config/api';

interface AuthContextType {
  user: User | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  error: string | null;
  login: (username: string, password: string) => Promise<boolean>;
  logout: () => Promise<void>;
  clearError: () => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

// Retained for backward compatibility with existing imports
export function getXsrfToken(): string | null {
  return getCsrfToken();
}

export const AuthProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  // Initialize and restore session on page mount
  useEffect(() => {
    const restoreSession = async () => {
      try {
        const res = await fetchWithTimeout(getApiUrl('/api/auth/me'), {
          method: 'GET',
          headers: { 'Accept': 'application/json' },
          credentials: 'include'
        });

        if (res.ok) {
          const userData: User = await res.json();
          setUser(userData);
          refreshCsrfToken().catch(() => {});
        } else {
          setUser(null);
          if (res.status !== 401) {
            setError('Session verification failed. Please try signing in again.');
          }
        }
      } catch {
        setUser(null);
        setError('The authentication service did not respond. Please try signing in again; the server may be starting.');
      } finally {
        setIsLoading(false);
      }
    };

    restoreSession();
  }, []);

  const login = async (username: string, password: string): Promise<boolean> => {
    setIsLoading(true);
    setError(null);

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

      const data: AuthResponse = await res.json();

      if (res.ok && data.success && data.user) {
        setUser(data.user);
        setError(null);
        // Refresh CSRF token for the authenticated session
        refreshCsrfToken().catch(() => {});
        return true;
      } else {
        setError(data.message || 'Authentication failed. Please check your credentials.');
        return false;
      }
    } catch {
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
      setCsrfToken(null);
      setIsLoading(false);
    }
  };

  const clearError = () => setError(null);

  return (
    <AuthContext.Provider
      value={{
        user,
        isAuthenticated: !!user,
        isLoading,
        error,
        login,
        logout,
        clearError
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
