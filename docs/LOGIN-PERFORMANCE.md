# Login loading fix

The old app rendered a full-page session-verification loader before mounting the login form. Its `/api/auth/me` request used the same 75-second cold-start budget as credential submission. A slow or waking backend therefore made the login page appear unavailable even though the static frontend had loaded.

The form now renders immediately while session restoration runs in the background. Users can type and submit without waiting for that check. A successful server session check remains mandatory before showing protected workspaces; no role or session is trusted from local storage.

Workspace code is dynamically imported after authentication. The measured production entry is about 243 KB / 76.6 KB gzip, with about 130 KB of workspace JavaScript deferred. Previously the entry included roughly 369 KB. These are bundle sizes, not a promised loading time on every connection. A recoverable boundary offers reload if the workspace chunk cannot load.

Starting a sign-in, logout or session-expiry action cancels obsolete checks and advances an authentication generation. Late responses from an earlier startup request cannot overwrite the newer account state. JSON requests now bound response-body reads as well as headers. Authentication keeps the existing 75-second budget for backend availability. The backend may still take time to answer credential requests; the login form no longer waits for it to appear.

A single CSRF prefetch runs in the background after session verification. Workspace mutations still fetch a fresh CSRF token before sending, and backend authorization is unchanged. CSRF prefetches are cancelled on account transitions/unmount. No credential requests are automatically replayed.

Regression checks cover an unresolved startup request with editable login, a late startup 401 after successful sign-in, delayed CSRF prefetch, stalled JSON body timeout, caller cancellation, cookie/session verification, failed credentials, role routing and logout. The browser verification holds startup session requests while checking that the login is visible and workspace modules have not downloaded, then completes the existing seven-role workflow.

This fix changes only frontend behavior and verification scripts. No database migration or backend change is required for the login fix. Render's deployment settings are unchanged; deploy the latest frontend build to make it live.
