# PharmaSafe — Educational Pharmacovigilance Case Management System

PharmaSafe is an educational pharmacovigilance case-management application inspired by the workflow, architecture, and visual aesthetics of **Oracle Argus Safety 7.0.3**.

---

## 1. Architecture & Technology Stack

### Frontend
- **Framework**: React 19 + TypeScript
- **Bundler / Dev Server**: Vite 8
- **Styling**: Tailwind CSS v4 with custom Oracle Argus design tokens (Argus Navy `#0E3D6B`, Slate `#B8CCE4`, high-density tabular layout)
- **Routing**: React Router 7
- **Forms & Validation**: React Hook Form + Zod
- **Testing**: Vitest + React Testing Library + Jest DOM matchers

### Backend
- **Language / Runtime**: Java 17 (LTS)
- **Framework**: Spring Boot 3.3.4
- **Build Tool**: Maven 3.9+
- **Security**: Spring Security 6 (Stateless REST, BCrypt password hashing, failed attempt lock mechanism)
- **Persistence**: Spring Data JPA + Hibernate ORM
- **Database Driver**: PostgreSQL JDBC Driver
- **Schema Management**: Flyway database migrations
- **Health & Metrics**: Spring Boot Actuator (`/actuator/health`, `/api/health`)
- **Testing**: JUnit 5 + Spring Boot Test + MockMvc

### Database & Inspection
- **Database Engine**: PostgreSQL 18 x64 (Port 5432)
- **Target Project Database**: `pharmasafe_db` (dedicated, isolated)
- **Application Role**: `pharmasafe_user` (least privilege, non-superuser)
- **Inspection UI**: pgAdmin 4 (located in `C:\Program Files\PostgreSQL\18\pgAdmin 4`)

---

## 2. Directory Structure

```text
D:\PharmaSafeFresh\
├── backend/
│   ├── pom.xml                               # Maven project definition & dependencies
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/pharmasafe/
│   │   │   │   ├── PharmaSafeApplication.java# Application entry point
│   │   │   │   ├── config/
│   │   │   │   │   └── SecurityConfig.java   # Spring Security, CORS, BCrypt bean
│   │   │   │   ├── controller/
│   │   │   │   │   └── HealthController.java # /api/health REST endpoint
│   │   │   │   ├── dto/
│   │   │   │   │   └── HealthResponseDto.java# Health status DTO
│   │   │   │   └── service/
│   │   │   │       └── HealthService.java    # System and database probe logic
│   │   │   └── resources/
│   │   │       ├── application.yml           # Environment-driven application config
│   │   │       ├── application.properties.example # Sample configuration template
│   │   │       └── db/migration/
│   │   │           └── V1__init_schema.sql   # Stage 1 baseline Flyway migration
│   │   └── test/
│   │       ├── java/com/pharmasafe/          # JUnit 5 integration test suites
│   │       └── resources/
│   │           └── application-test.yml      # Isolated test configuration
├── frontend/
│   ├── index.html
│   ├── package.json                          # Vite, React, Tailwind, Vitest deps
│   ├── vite.config.ts                        # Tailwind v4 plugin, API proxy & Vitest
│   ├── tsconfig.json & tsconfig.app.json
│   └── src/
│       ├── App.tsx                           # Stage 1 status & Argus design preview
│       ├── App.test.tsx                      # Vitest UI tests
│       ├── index.css                         # Tailwind v4 + Argus design tokens
│       └── main.tsx
├── scripts/
│   └── init-db.sql                           # Dedicated DB and role creation script
├── .env.example                              # Root environment template
├── .gitignore                                # Excludes node_modules, target, secrets
└── README.md                                 # Master documentation
```

---

## 3. Database Setup & pgAdmin 4 Inspection

In accordance with project safety guidelines, existing databases are preserved. To initialize the dedicated database:

### Option A: Automated Provisioning Script (Recommended)
Run the interactive PowerShell provisioning script:
```powershell
& "D:\PharmaSafeFresh\scripts\provision-db.ps1"
```
This script:
1. Prompts securely for the PostgreSQL administrator (`postgres`) password.
2. Prompts for the `pharmasafe_user` application password (or defaults to secure preset).
3. Safely verifies whether `pharmasafe_user` or `pharmasafe_db` already exist.
4. Executes `scripts/init-db.sql` in the correct sequence.
5. Generates the local `.env` configuration file (which is git-ignored).
6. Connects using `pharmasafe_user`, runs `SELECT current_database(), current_user;`, tests creating a temporary test table in `public`, and cleans it up.

### Option B: Manual Provisioning via `psql` (PowerShell)
Execute the corrected script using the PostgreSQL superuser account:
```powershell
& "C:\Program Files\PostgreSQL\18\bin\psql.exe" -U postgres -h localhost -f D:\PharmaSafeFresh\scripts\init-db.sql
```

### Option C: Using pgAdmin 4
1. Launch **pgAdmin 4** (from Start Menu or `C:\Program Files\PostgreSQL\18\pgAdmin 4\runtime\pgAdmin4.exe`).
2. Connect to your local PostgreSQL 18 server.
3. Open the **Query Tool** on `postgres` database and execute Step 1 from `D:\PharmaSafeFresh\scripts\init-db.sql`.
4. In Object Explorer, right-click **Databases** &rarr; **Create** &rarr; **Database...**:
   - **Database**: `pharmasafe_db`
   - **Owner**: `pharmasafe_user`
   - **Encoding**: `UTF-8`
5. Click **Save**.
6. In Object Explorer, right-click `pharmasafe_db` &rarr; **Query Tool**, and run Step 4 from `D:\PharmaSafeFresh\scripts\init-db.sql` to apply schema and sequence grants.

---

## 4. Running the Applications

### Backend
To run unit and integration tests:
```powershell
cd D:\PharmaSafeFresh\backend
mvn test
```

To run the Spring Boot server with `.env` settings:
```powershell
cd D:\PharmaSafeFresh\backend
.\run-backend.ps1
```
Or directly via Maven:
```powershell
cd D:\PharmaSafeFresh\backend
mvn spring-boot:run
```
The backend starts on `http://localhost:8080`.
- Health Endpoint: `http://localhost:8080/api/health`
- Actuator Health: `http://localhost:8080/actuator/health`

### Frontend
To run frontend tests:
```powershell
cd D:\PharmaSafeFresh\frontend
npm test
```

To run the development server:
```powershell
cd D:\PharmaSafeFresh\frontend
npm run dev
```
Open `http://localhost:5173` in your browser. The Vite development server automatically proxies `/api` and `/actuator` requests to the Spring Boot backend on port 8080.

---

## 5. Stage 2: Secure Authentication & Audit System

PharmaSafe Stage 2 replaces temporary development authentication with a robust, enterprise-grade, PostgreSQL-backed authentication and auditing architecture:

### Security Features
- **PostgreSQL 18 Credential Store**: Normalized username, BCrypt-hashed password (cost factor 10), roles (`ROLE_ADMIN`, `ROLE_USER`), active/inactive status.
- **3-Attempt Lockout Defense**: Accounts are automatically locked upon reaching 3 consecutive failed login attempts. An audit event is recorded (`ACCOUNT_LOCKED`) with the lock timestamp.
- **Race Condition Prevention**: Employs JPA row-level pessimistic locking (`PESSIMISTIC_WRITE`) via `SELECT ... FOR UPDATE` during authentication evaluation to ensure concurrent login attempts cannot bypass the 3-attempt lockout threshold.
- **Safe Error Reporting**: Returns generic, uniform error messages ("Invalid username or password") to prevent username enumeration, while explicitly informing users when their account is locked ("Account has been locked due to 3 consecutive failed login attempts").
- **Session-Based Authentication with HttpOnly Cookies**: Uses standard Spring Security sessions with `JSESSIONID` cookies marked `HttpOnly` and `SameSite=Lax`. No passwords, session IDs, or security tokens are ever stored in browser `localStorage` or `sessionStorage`.
- **CSRF Protection**: Configured with `CookieCsrfTokenRepository.withHttpOnlyFalse()` providing `XSRF-TOKEN` cookie paired with the `X-XSRF-TOKEN` header for state-changing operations.
- **Immutable Audit Trail**: Tracks `LOGIN_SUCCESS`, `LOGIN_FAILED`, `ACCOUNT_LOCKED`, `LOGOUT`, and `ADMIN_INITIALIZED` with client IP address, user agent, and timestamp in the `audit_events` table via `REQUIRES_NEW` transactions. Never logs passwords, hashes, or session tokens.

### Authentication & Audit REST API
| Method | Endpoint | Access | Description |
|---|---|---|---|
| `POST` | `/api/auth/login` | Anonymous | Authenticates credentials, creates session, resets failed count |
| `POST` | `/api/auth/logout` | Authenticated | Invalidates server session and clears `JSESSIONID` |
| `GET` | `/api/auth/me` | Authenticated | Returns current authenticated user profile (no secrets) |
| `GET` | `/api/auth/csrf` | Anonymous | Fetches active CSRF token details |
| `GET` | `/api/admin/audit-logs` | `ROLE_ADMIN` | Retrieves enterprise security audit log history |
| `GET` | `/api/health` | Anonymous | System and database readiness probe (preserved from Stage 1) |

---

## 6. Secure Administrator Initialization

PharmaSafe strictly forbids hardcoded default passwords and public self-registration endpoints. The initial system administrator must be initialized explicitly using the automated script:

```powershell
& "D:\PharmaSafeFresh\scripts\init-admin.ps1"
```

### Script Execution Workflow:
1. Prompts interactively for the administrator username (defaults to `sysadmin`).
2. Prompts securely for the password using `Read-Host -AsSecureString` (characters are masked; password is never written to disk, console output, or PowerShell command history).
3. Invokes the backend via Spring Boot CLI runner or database utility with BCrypt hashing.
4. **Takeover Protection**: Verifies whether an administrator already exists. If an existing `ROLE_ADMIN` user is detected, initialization is immediately aborted with an error message to prevent administrator account takeover.
5. Records an `ADMIN_INITIALIZED` audit event with the creation timestamp and actor.

---

## 7. Running the Applications

### Backend (PowerShell Terminal 1)
```powershell
cd D:\PharmaSafeFresh\backend
.\run-backend.ps1
```
The Spring Boot backend will start on `http://localhost:8080`.
- Health Endpoint: `http://localhost:8080/api/health`
- Login Endpoint: `http://localhost:8080/api/auth/login`

### Frontend (PowerShell Terminal 2)
```powershell
cd D:\PharmaSafeFresh\frontend
npm run dev
```
The Vite development server will start on `http://localhost:5173`.
Navigate to `http://localhost:5173` to access the PharmaSafe Oracle Argus Safety-inspired login interface.

---

## 8. Stage 3: Compact Argus Safety Dashboard & Quick Launch

Inspired by Oracle Argus Safety 7.0.3, Stage 3 provides an enterprise dashboard with high-density workspace controls:
- **Argus-Inspired Navigation Header**: Blue header bar with user profile, role badge, session status, and quick logout.
- **Quick Launch Toolbar**: Icon toolbar supporting `New Case`, `Open Case`, `Save Case`, `Close Case`, `Print`, and search shortcuts.
- **Three-Tier Workspace Layout**:
  - **Section A**: Case Quick Launch & search by Case Number, Product, or Criteria with live query filtering.
  - **Section B**: Cases Assigned work queue table displaying Country/Case Number, Report Type, Primary Product, Workflow State, and Primary Event.
  - **Section C & D**: Action Item Entries and Contact Log Entries queues.
- **High-Density Typography & Design**: Argus navy `#0E3D6B`, slate borders `#B8CCE4`, monospace reference columns, and responsive flex layout without horizontal clipping.

---

## 9. Stage 4: Safety Case Management (ICH E2B(R3))

Stage 4 introduces end-to-end safety case processing with a normalized relational schema, atomic number sequence, and a 9-tab Case Entry interface:

### 1. Normalized Relational Database Schema (`V3__case_management.sql`)
- **`case_number_seq`**: Atomic PostgreSQL sequence generating standardized identifiers in `PS-YYYY-CCC-NNNNN` format.
- **`cases`**: Primary safety case table with check constraints on receipt dates, priorities (`Routine`, `Priority`, `Expedited`), and workflow states (`Draft`, `Data Entry`, `Quality Review`, `Medical Review`, `Closed`).
- **`case_patients`**: Patient demographics (ICH E2B Section B.1) with PII minimization and age unit validation.
- **`case_reporters`**: Primary and secondary healthcare professional / consumer reporters (ICH E2B Section A.2).
- **`case_products`**: Suspect and concomitant medicinal products with dosage, formulation, lot, and dechallenge/rechallenge outcomes (ICH E2B Section B.4).
- **`case_events`**: Adverse reaction descriptions, MedDRA terms (LLT, PT, SOC), onset/stop dates, outcomes, and seriousness criteria flags (ICH E2B Section B.2).
- **`case_assessments`**: Causality ratings (Reporter/Company) and expectedness determinations.
- **`case_medical_history`**: Relevant medical conditions, dates, and ongoing status.
- **`case_references`**: Literature citations and source reference records.
- **`case_attachments`**: Secure file attachment metadata with SHA-256 verification and file classifications.

### 2. Argus-Inspired Case Entry UI (`CaseEntryForm.tsx`)
- **9 Sub-Tabs**:
  1. *General Info*: Incident country, report type, receipt dates, priority, workflow state.
  2. *Patient*: Identifier, age, gender, weight, height, pregnancy status.
  3. *Reporters*: Multi-row dynamic table for primary and secondary reporters.
  4. *Products*: Multi-row suspect / concomitant drugs with formulation and lot numbers.
  5. *Adverse Events*: Multi-row reactions with MedDRA PT/LLT/SOC and seriousness criteria.
  6. *Medical History*: Concurrent conditions and disease history.
  7. *Assessments*: Causality and expectedness evaluation.
  8. *Narrative & References*: Case narrative, company comments, and citations.
  9. *Attachments*: Document metadata and classifications.
- **Dynamic Seriousness Badge**: Automatically computes case seriousness from event criteria.
- **Draft vs. Final Submission**: Supports partial draft saves (`Save Draft`) and regulatory validation enforcement (`Save Case`).

### 3. Case REST API
| Method | Endpoint | Access | Description |
|---|---|---|---|
| `POST` | `/api/cases` | Authenticated | Creates a new safety case with atomic case number generation |
| `PUT` | `/api/cases/{id}` | Authenticated | Updates case, replaces child collections, increments `version_number` |
| `GET` | `/api/cases/{id}` | Authenticated | Retrieves full case details with all 8 child collections |
| `GET` | `/api/cases/by-number/{caseNumber}` | Authenticated | Retrieves full case details by case number |
| `GET` | `/api/cases` | Authenticated | Searches cases by keyword, country, state, or report type |
| `GET` | `/api/cases/assigned` | Authenticated | Returns cases assigned to the authenticated user |

---

## 10. Automated Testing & Verification

### Backend Tests (46 tests, 100% pass)
```powershell
cd D:\PharmaSafeFresh\backend
mvn test
```
Test suites executed:
- `CaseControllerTest` (9 tests): Creation, validation, authorization, search, retrieval, updates.
- `CaseServiceTest` (5 tests): Transactional case creation, version increments, search filters.
- `AuthenticationControllerTest` & `AuthenticationServiceTest` (13 tests): Auth lifecycle and security.
- `SecurityConfigTest` (6 tests): CSRF, CORS, session policies.
- `AdminInitializationServiceTest` (9 tests): Admin initialization and recovery.
- `HealthControllerTest` (2 tests) & `PharmaSafeApplicationTests` (1 test): Context readiness.
- `TemporaryAccountLockoutDisabledTest` (1 test): Lockout configuration verification.

### Frontend Tests (15 tests, 100% pass)
```powershell
cd D:\PharmaSafeFresh\frontend
npm test -- --run
```
Test suites executed:
- `App.test.tsx` (7 tests): Authentication views, error states, session restoration.
- `Stage3Dashboard.test.tsx` (8 tests): Dashboard layout, Quick Launch toolbar, search query execution, Cases Assigned table, row selection, Case Entry form modal.

### Production Build
```powershell
cd D:\PharmaSafeFresh\frontend
npm run build
```

---

## 11. Stage Progress

- [x] **Stage 1: Environment, Scaffold & Database Readiness** (COMPLETED)
- [x] **Stage 2: Secure Authentication & Audit System** (COMPLETED)
- [x] **Stage 3: Dashboard & Quick Launch Worklist** (COMPLETED)
- [x] **Stage 4: Case Intake, Duplicate Screening & Initial Entry** (COMPLETED)
- [ ] **Stage 5: Complete Case Form & Regulatory Reporting**

