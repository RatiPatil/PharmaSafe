# PharmaSafe — Cloud Deployment Guide (Render & Supabase Free Tier)

This document provides complete instructions for deploying the **PharmaSafe** pharmacovigilance case management system to free-tier cloud hosting using **Render** and **Supabase PostgreSQL**.

---

## 1. Architecture Overview

PharmaSafe uses a decoupled architecture tailored to free-tier cloud constraints:

```
+------------------------------------+          +--------------------------------------+
|  Render Static Site                |          |  Render Web Service                  |
|  (pharmasafe-frontend)             |  HTTPS   |  (pharmasafe-backend)                |
|  - React 19, TypeScript, Vite      | -------> |  - Spring Boot 3.3.4, Java 17        |
|  - Static HTML/CSS/JS (dist)       |  CORS    |  - Embedded Tomcat (Port 8080)       |
|  - Client-side routing rewrite     |          |  - Actuator /api/health check        |
+------------------------------------+          +--------------------------------------+
                                                                   |
                                                                   | JDBC (SSL, IPv4)
                                                                   | Session Pooler (Port 5432)
                                                                   v
                                                +--------------------------------------+
                                                |  Supabase Free PostgreSQL            |
                                                |  - Version: 15+                      |
                                                |  - Host: aws-0-<region>.pooler...    |
                                                |  - Flyway Migrations V1, V2, V3      |
                                                +--------------------------------------+
```

### Critical Architectural Decisions:
1. **Supabase IPv4 Connection Pooler (Port 5432 - Session Mode):**
   - Render free-tier web services do not have outbound IPv6 routing. Direct Supabase domains (`db.<project-ref>.supabase.co`) resolve only to IPv6 and will fail.
   - Applications running on Render **must connect through the Supabase Connection Pooler** (`aws-0-<region>.pooler.supabase.com`) on port `5432`.
   - **Session Mode (port 5432)** is required rather than Transaction Mode (port 6543) because Flyway migrations and Hibernate prepared statements require session-level connection features.
2. **Decoupled Cross-Origin Authentication & CSRF:**
   - Because the frontend is hosted at `https://pharmasafe-frontend.onrender.com` and the backend at `https://pharmasafe-backend.onrender.com`, requests are cross-origin.
   - Session cookies (`JSESSIONID`) and CSRF tokens must be configured with `SameSite=None; Secure`.
   - Frontend calls `GET /api/auth/csrf` on initialization and caches the token in memory, attaching `X-XSRF-TOKEN` to all state-changing requests (`POST`, `PUT`, `DELETE`).
3. **Memory Optimization for Render Free Tier (512 MB RAM):**
   - Java memory limits are capped using `JAVA_OPTS="-Xms256m -Xmx384m"` to prevent cgroup Out-Of-Memory (OOM) kills.
   - HikariCP max pool size is constrained to `5` to prevent exhausting Supabase connection pool limits.

---

## 2. Supabase PostgreSQL Configuration

### Step 2.1: Create Project
1. Log in to [Supabase](https://supabase.com) and click **New project**.
2. Name: `pharmasafe-db`.
3. Set a strong database password (store securely).
4. Choose an AWS region close to your Render deployment (e.g., `US East (N. Virginia)` / `us-east-1` for Render Oregon or Ohio).

### Step 2.2: Retrieve Connection Pooler Details
1. In the Supabase Dashboard, navigate to **Project Settings** -> **Database**.
2. Scroll to the **Connection string** section and select the **Connection Pooler** tab.
3. Select **Mode: Session** and **Port: 5432**.
4. Note the connection parameters:
   - **Host:** `aws-0-<region>.pooler.supabase.com`
   - **Port:** `5432`
   - **Database name:** `postgres`
   - **User:** `postgres.<your-project-ref>`
   - **Password:** The database password chosen during setup.
   - **SSL:** Required (`?sslmode=require`).

---

## 3. Render Blueprint Deployment

A declarative Blueprint (`render.yaml`) is included in the root of the repository.

### Step 3.1: Connect GitHub Repository
1. Log in to [Render](https://render.com).
2. Click **New** -> **Blueprint**.
3. Connect your GitHub repository: `https://github.com/RatiPatil/PharmaSafe.git`.
4. Select the `main` branch.

### Step 3.2: Render Blueprint Variables Configuration
Render will inspect `render.yaml` and prompt for the `sync: false` variables:

| Service | Environment Variable | Recommended Value | Notes |
| :--- | :--- | :--- | :--- |
| `pharmasafe-backend` | `DB_HOST` | `aws-0-<region>.pooler.supabase.com` | Supabase Pooler IPv4 host |
| `pharmasafe-backend` | `DB_PORT` | `5432` | Session Mode port |
| `pharmasafe-backend` | `DB_NAME` | `postgres` | Default Supabase database |
| `pharmasafe-backend` | `DB_USERNAME` | `postgres.<project-ref>` | Supabase pooler username format |
| `pharmasafe-backend` | `DB_PASSWORD` | `<your-database-password>` | Mark as Secret |
| `pharmasafe-backend` | `CORS_ALLOWED_ORIGINS` | `https://pharmasafe-frontend.onrender.com` | Update to match actual frontend URL |
| `pharmasafe-backend` | `COOKIE_SAME_SITE` | `None` | Required for cross-domain cookies |
| `pharmasafe-backend` | `COOKIE_SECURE` | `true` | Required for HTTPS |
| `pharmasafe-frontend` | `VITE_API_URL` | `https://pharmasafe-backend.onrender.com` | Update to match actual backend URL |

### Step 3.3: Automatic Deployment Protection
Notice that both services in `render.yaml` specify:
```yaml
autoDeploy: false
```
This ensures that pushes to `main` do not automatically trigger live deployments until manually triggered via the Render dashboard or authorized via GitHub Actions.

---

## 4. GitHub Actions CI/CD Workflow

The repository includes `.github/workflows/ci.yml`.

### Workflow Structure:
1. **Frontend CI:**
   - Installs Node 24 dependencies (`npm ci`).
   - Runs Vitest unit tests (`npm test -- --run`).
   - Builds production bundle (`npm run build`).
2. **Backend CI:**
   - Installs Java 17 (Temurin).
   - Executes all Maven tests (`mvn test`).
   - Packages the executable jar (`mvn package -DskipTests`).
3. **Deployment Gate:**
   - Requires both `frontend-ci` and `backend-ci` to pass.
   - For regular pushes and pull requests, automatic deployment is **disabled**.
   - To trigger deployment through GitHub Actions:
     - Navigate to **Actions** -> **PharmaSafe CI/CD Pipeline**.
     - Click **Run workflow**.
     - Select `trigger_deploy: yes`.
     - (Requires repository secrets `RENDER_BACKEND_DEPLOY_HOOK` and `RENDER_FRONTEND_DEPLOY_HOOK` configured under GitHub Settings -> Secrets).

---

## 5. Cold Starts & Monitoring

### Free Tier Sleep Behavior
- Render free-tier web services spin down after 15 minutes of inactivity.
- The initial wake-up request (cold start) typically takes 30-50 seconds as the Java 17 JVM initializes and Flyway verifies migrations.
- Frontend includes non-blocking health polling (`/api/health`) to display live connectivity state.

### Health Check Endpoint
- Path: `GET /api/health`
- Response: HTTP 200 OK
```json
{
  "status": "UP",
  "database": "PostgreSQL 15+",
  "details": {
    "systemName": "PharmaSafe Pharmacovigilance Case Management",
    "version": "1.0.0"
  }
}
```

---

## 6. Security & Credential Hygiene Checklist

- [x] **No Local Secrets Committed:** Local `.env` and development keys are strictly ignored by `.gitignore`.
- [x] **SSL Enforced:** JDBC connection requires `sslmode=require`.
- [x] **Session Cookies:** `HttpOnly=true`, `SameSite=None`, `Secure=true` for HTTPS cross-site delivery.
- [x] **Dynamic CORS:** Strict origin whitelisting via `CORS_ALLOWED_ORIGINS` (no wildcard `*` with credentials).
- [x] **CSRF Protection:** Double-submit cookie with customizer + JSON body fallback support.
- [x] **User Account Lockout:** 5 failed attempts locks the account with 15-minute cooldown.

---

## 7. Rollback Plan

If an issue occurs during or after deployment:

1. **Rollback Backend on Render:**
   - Open Render Dashboard -> `pharmasafe-backend` -> **Deploys**.
   - Locate the previous successful build.
   - Click the three dots `...` and select **Rollback to this deploy**.
2. **Rollback Frontend on Render:**
   - Open Render Dashboard -> `pharmasafe-frontend` -> **Deploys**.
   - Locate the previous successful build and select **Rollback to this deploy**.
3. **Database Migration Safety:**
   - Flyway migrations are strictly forward-compatible and additive (`CREATE TABLE IF NOT EXISTS`, `CREATE INDEX IF NOT EXISTS`).
   - No destructive DDL (`DROP TABLE`, `ALTER TABLE ... DROP COLUMN`) is present in `V1`, `V2`, or `V3`.
   - In case of a database issue, inspect `flyway_schema_history` table on Supabase.
