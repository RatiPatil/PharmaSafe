import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { getApiUrl, getCsrfToken, setCsrfToken, fetchWithTimeout, AUTH_REQUEST_TIMEOUT_MS } from './api';

describe('API Configuration & URL Normalization', () => {
  beforeEach(() => {
    setCsrfToken(null);
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it('has a 75-second timeout window to accommodate Render free-tier cold starts', () => {
    expect(AUTH_REQUEST_TIMEOUT_MS).toBe(75_000);
  });

  it('aborts a stalled authentication request instead of waiting indefinitely', async () => {
    vi.useFakeTimers();
    vi.stubGlobal('fetch', vi.fn((_url, options: RequestInit) => new Promise((_resolve, reject) => {
      options.signal?.addEventListener('abort', () => reject(new DOMException('Timed out', 'AbortError')));
    })));
    const result = expect(fetchWithTimeout('/api/auth/me')).rejects.toMatchObject({ name: 'AbortError' });
    await vi.advanceTimersByTimeAsync(AUTH_REQUEST_TIMEOUT_MS);
    await result;
    expect(vi.getTimerCount()).toBe(0);
  });

  it('clears the timeout after a successful response', async () => {
    vi.useFakeTimers();
    const response = new Response(null, { status: 401 });
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response));
    expect(await fetchWithTimeout('/api/auth/me')).toBe(response);
    expect(vi.getTimerCount()).toBe(0);
  });

  it('normalizes relative API paths correctly', () => {
    expect(getApiUrl('/api/health')).toBe('/api/health');
    expect(getApiUrl('api/cases')).toBe('/api/cases');
  });

  it('stores and retrieves CSRF token in memory', () => {
    expect(getCsrfToken()).toBeNull();
    setCsrfToken('test-csrf-token-12345');
    expect(getCsrfToken()).toBe('test-csrf-token-12345');
  });

  it('clears CSRF token when set to null', () => {
    setCsrfToken('temp-token');
    expect(getCsrfToken()).toBe('temp-token');
    setCsrfToken(null);
    expect(getCsrfToken()).toBeNull();
  });
});
