import { describe, it, expect, beforeEach } from 'vitest';
import { getApiUrl, getCsrfToken, setCsrfToken } from './api';

describe('API Configuration & URL Normalization', () => {
  beforeEach(() => {
    setCsrfToken(null);
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
