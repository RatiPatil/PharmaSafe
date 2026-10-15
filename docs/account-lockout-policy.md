# PharmaSafe Account Lockout Policy & Development Configuration

## Overview

In enterprise pharmacovigilance systems (aligned with ICH E2B(R3) and 21 CFR Part 11 security architecture), automatic account lockout protects against brute-force and credential-stuffing attacks by locking accounts after three consecutive failed authentication attempts.

---

## Current Status: Temporarily Disabled for Development

> [!IMPORTANT]
> Automatic account lockout after consecutive failed login attempts is **temporarily disabled** in the development environment.
> This prevents developer and tester lockouts during UI layout iterations, browser session testing, and automated test suite execution.

### Policy Details in Development:
1. **No Automatic Account Lockout**:
   - Multiple invalid password attempts do not set `user.locked = true`.
   - The user receives an HTTP 401 Unauthorized response with the standard message: `Invalid username or password`.
   - Failed attempts are still recorded in `failed_login_attempts` for audit and security monitoring.
2. **Authentication of Previously Locked Accounts**:
   - Accounts already marked as locked in the PostgreSQL database can authenticate normally when valid credentials are supplied.
   - Upon successful authentication with valid credentials, the residual `locked` flag and `locked_at` timestamp are automatically cleared, and `failed_login_attempts` is reset to zero.
   - No unauthenticated or public unlock endpoints are exposed.
3. **Active Security Protections**:
   - **Spring Security** filter chain remains active.
   - **BCrypt** password hashing with salt remains enforced.
   - **Session Security**: Stateful HttpOnly sessions (`JSESSIONID`) with server-side invalidation.
   - **CSRF Defense**: `X-XSRF-TOKEN` cookie/header verification.
   - **Role-Based Authorization**: `ROLE_ADMIN` vs `ROLE_USER` route enforcement.
   - **Audit Trail**: Every login attempt (`LOGIN_SUCCESS`, `LOGIN_FAILED`, `LOGOUT`) continues to be logged to PostgreSQL table `audit_events`.

---

## Production Restoration Checklist

Before deploying PharmaSafe to environments requiring formal regulatory controls:

1. **Re-enable Account Lockout Flag in `AuthenticationService.java`**:
   Change:
   ```java
   public static final boolean ACCOUNT_LOCKOUT_ENABLED = false;
   ```
   To:
   ```java
   public static final boolean ACCOUNT_LOCKOUT_ENABLED = true;
   ```

2. **Verify `CustomUserDetailsService.java`**:
   Ensure `accountNonLocked` evaluates to:
   ```java
   boolean accountNonLocked = !user.isLocked();
   ```

3. **Verify Enterprise Test Suite**:
   Run `mvn test` to confirm `AuthControllerTest` and `AdminInitializationServiceTest` pass with lockout enforcement active.
