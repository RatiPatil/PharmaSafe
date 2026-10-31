/**
 * PharmaSafe Centralized API and CSRF Configuration
 * Supports decoupled frontend/backend hosting (e.g. Render Static Site + Render Web Service)
 * as well as local development with Vite dev server proxy.
 */

// Base API URL from environment variable or production fallback
const envApiUrl = (import.meta.env.VITE_API_URL || '').replace(/\/+$/, '');
export const API_BASE_URL: string = envApiUrl || (
  typeof window !== 'undefined' && window.location?.hostname?.includes('pharmasafe-frontend.onrender.com')
    ? 'https://pharmasafe-qjm1.onrender.com'
    : ''
);

// Bounded timeout (75 seconds) designed to safely accommodate Render free-tier cold starts
// where the Java 17 Spring Boot JVM, Docker container, and Flyway migrations may take 30-50 seconds.
export const AUTH_REQUEST_TIMEOUT_MS = 75_000;

/** Bound authentication requests so an unavailable backend cannot trap the UI. */
export async function fetchWithTimeout(url: string, options: RequestInit = {}): Promise<Response> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), AUTH_REQUEST_TIMEOUT_MS);
  try {
    return await fetch(url, { ...options, signal: controller.signal });
  } finally {
    clearTimeout(timer);
  }
}

/**
 * Returns full URL for an API path.
 * In development / tests without VITE_API_URL, returns the relative path (e.g. "/api/cases").
 * In production with VITE_API_URL set, returns "https://pharmasafe-qjm1.onrender.com/api/cases".
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
    const response = await fetchWithTimeout(getApiUrl('/api/auth/csrf'), {
      method: 'GET',
      credentials: 'include',
      headers: {
        'Accept': 'application/json'
      }
    });

    if (response.status === 401 && typeof window !== 'undefined') {
      window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT));
      return null;
    }
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

export const SESSION_EXPIRED_EVENT = 'pharmasafe:session-expired';
export class ApiError extends Error {
  status: number;
  constructor(message: string, status: number) { super(message); this.name = 'ApiError'; this.status = status; }
}
/** JSON requests share session expiry handling. Failed writes are never automatically replayed. */
export async function apiRequest<T>(path: string, method = 'GET', body?: unknown, signal?: AbortSignal): Promise<T> {
  if (method !== 'GET') {
    const token = await refreshCsrfToken();
    if (!token) throw new ApiError('Could not verify request security. Refresh and try again.', 403);
  }
  const controller = new AbortController();
  const abort = () => controller.abort();
  if (signal?.aborted) abort();
  signal?.addEventListener('abort', abort, { once: true });
  const timer = setTimeout(abort, AUTH_REQUEST_TIMEOUT_MS);
  try {
    const response = await fetch(getApiUrl(path), { method, credentials: 'include', signal: controller.signal,
      headers: { Accept: 'application/json', ...(body === undefined ? {} : { 'Content-Type':'application/json' }),
        ...(method === 'GET' ? {} : { 'X-XSRF-TOKEN': getCsrfToken() || '' }) },
      ...(body === undefined ? {} : { body: JSON.stringify(body) }) });
    if (response.status === 401) {
      if (typeof window !== 'undefined') window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT));
      throw new ApiError('Your session expired. Sign in again to continue.', 401);
    }
    const data = await response.json().catch(() => null);
    if (!response.ok) throw new ApiError(data?.message || (response.status === 403 ? 'You do not have permission for this action.' : `Request failed (${response.status}).`), response.status);
    if (data === null && response.status !== 204) throw new ApiError('The server returned an invalid response. Refresh and try again.', 502);
    return data as T;
  } catch (error) {
    if (error instanceof ApiError || signal?.aborted) throw error;
    throw new ApiError(controller.signal.aborted ? 'The server did not respond in time. Try again.' : 'Could not reach the server. Check your connection and try again.', 0);
  } finally {
    clearTimeout(timer);
    signal?.removeEventListener('abort', abort);
  }
}
