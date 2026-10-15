/**
 * PharmaSafe Centralized API and CSRF Configuration
 * Supports decoupled frontend/backend hosting (e.g. Render Static Site + Render Web Service)
 * as well as local development with Vite dev server proxy.
 */

// Base API URL from environment variable (empty string for same-origin or local proxy)
export const API_BASE_URL: string = (import.meta.env.VITE_API_URL || '').replace(/\/+$/, '');

/**
 * Returns full URL for an API path.
 * In development / tests without VITE_API_URL, returns the relative path (e.g. "/api/cases").
 * In production with VITE_API_URL set, returns "https://backend.onrender.com/api/cases".
 */
export function getApiUrl(path: string): string {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`;
  return API_BASE_URL ? `${API_BASE_URL}${normalizedPath}` : normalizedPath;
}

// In-memory storage for CSRF token (required for cross-origin decoupled deployments
// where frontend JavaScript cannot read HttpOnly/cross-site cookies via document.cookie)
let csrfTokenMemory: string | null = null;

export function setCsrfToken(token: string | null): void {
  csrfTokenMemory = token;
}

/**
 * Retrieves the current CSRF token from memory or document cookie.
 */
export function getCsrfToken(): string | null {
  if (csrfTokenMemory) {
    return csrfTokenMemory;
  }
  if (typeof document !== 'undefined' && document.cookie) {
    const match = document.cookie.match(/(?:^|; )\s*XSRF-TOKEN=([^;]+)/);
    if (match) {
      return decodeURIComponent(match[1]);
    }
  }
  return null;
}

/**
 * Fetches fresh CSRF token from the backend /api/auth/csrf endpoint and stores it in memory.
 */
export async function refreshCsrfToken(): Promise<string | null> {
  try {
    const response = await fetch(getApiUrl('/api/auth/csrf'), {
      method: 'GET',
      credentials: 'include',
      headers: {
        'Accept': 'application/json'
      }
    });

    if (response.ok) {
      const data = await response.json();
      if (data && data.token) {
        setCsrfToken(data.token);
        return data.token;
      }
    }
  } catch {
    // Non-blocking in offline or mock environments
  }
  return null;
}
