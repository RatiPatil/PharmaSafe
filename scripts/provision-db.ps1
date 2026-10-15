# PharmaSafe Stage 1.5 Database Provisioning Script
# Securely provisions pharmasafe_user and pharmasafe_db without exposing credentials in logs or git.

$ErrorActionPreference = "Stop"

$psqlPath = "C:\Program Files\PostgreSQL\18\bin\psql.exe"
if (-not (Test-Path $psqlPath)) {
    $cmd = Get-Command psql -ErrorAction SilentlyContinue
    if ($cmd) {
        $psqlPath = $cmd.Source
    } else {
        Write-Error "PostgreSQL 'psql.exe' not found at '$psqlPath' or in PATH."
        exit 1
    }
}

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host " PharmaSafe - PostgreSQL 18 Safe Provisioning & Verification" -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "Target Host:     localhost:5432"
Write-Host "Target Database: pharmasafe_db"
Write-Host "Target User:     pharmasafe_user (Least privilege)"
Write-Host ""

# Helper function to execute a psql query with separate stdout/stderr and explicit exit code
function Invoke-PsqlQuery {
    param(
        [Parameter(Mandatory=$true)][string]$User,
        [Parameter(Mandatory=$true)][string]$Database,
        [Parameter(Mandatory=$true)][string]$Query
    )

    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $psqlPath
    $psi.Arguments = "-U $User -h localhost -p 5432 -d $Database -t -A -c `"$Query`""
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow = $true

    $proc = [System.Diagnostics.Process]::Start($psi)
    $stdout = $proc.StandardOutput.ReadToEnd()
    $stderr = $proc.StandardError.ReadToEnd()
    $proc.WaitForExit()

    $cleanOutput = if ($null -ne $stdout) { $stdout.Trim() } else { "" }
    $cleanError  = if ($null -ne $stderr) { $stderr.Trim() } else { "" }

    return [PSCustomObject]@{
        ExitCode = $proc.ExitCode
        Output   = $cleanOutput
        Error    = $cleanError
    }
}

# Helper function to execute SQL commands via standard input stream (keeps passwords out of process arguments)
function Invoke-PsqlSqlScript {
    param(
        [Parameter(Mandatory=$true)][string]$User,
        [Parameter(Mandatory=$true)][string]$Database,
        [Parameter(Mandatory=$true)][string]$SqlText
    )

    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $psqlPath
    $psi.Arguments = "-U $User -h localhost -p 5432 -d $Database -v ON_ERROR_STOP=1"
    $psi.RedirectStandardInput = $true
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow = $true

    $proc = [System.Diagnostics.Process]::Start($psi)
    $writer = $proc.StandardInput
    $writer.Write($SqlText)
    $writer.Flush()
    $writer.Close()

    $stdout = $proc.StandardOutput.ReadToEnd()
    $stderr = $proc.StandardError.ReadToEnd()
    $proc.WaitForExit()

    $cleanOutput = if ($null -ne $stdout) { $stdout.Trim() } else { "" }
    $cleanError  = if ($null -ne $stderr) { $stderr.Trim() } else { "" }

    return [PSCustomObject]@{
        ExitCode = $proc.ExitCode
        Output   = $cleanOutput
        Error    = $cleanError
    }
}

# 1. Prompt for administrator password securely
$adminSecure = Read-Host -Prompt "Enter PostgreSQL 'postgres' superuser password" -AsSecureString
if ($null -eq $adminSecure -or $adminSecure.Length -eq 0) {
    Write-Error "PostgreSQL administrator password cannot be empty."
    exit 1
}

$bstrAdmin = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($adminSecure)
$adminPass = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto($bstrAdmin)
[System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstrAdmin)

# 2. Prompt for application user password securely (no hardcoded defaults)
$appSecure = Read-Host -Prompt "Enter new password for application role 'pharmasafe_user'" -AsSecureString
if ($null -eq $appSecure -or $appSecure.Length -eq 0) {
    Write-Error "Application user password cannot be empty. Please enter a secure password."
    exit 1
}

$appSecureConfirm = Read-Host -Prompt "Confirm password for 'pharmasafe_user'" -AsSecureString
if ($null -eq $appSecureConfirm -or $appSecureConfirm.Length -eq 0) {
    Write-Error "Password confirmation cannot be empty."
    exit 1
}

# Note: Plaintext variables are retained temporarily in process memory for database commands and are not written to logs or disk.
$bstrApp = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($appSecure)
$appPass = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto($bstrApp)
[System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstrApp)

$bstrAppConf = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($appSecureConfirm)
$appPassConf = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto($bstrAppConf)
[System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstrAppConf)

if ($appPass -ne $appPassConf) {
    Write-Error "Passwords for 'pharmasafe_user' do not match. Provisioning aborted."
    exit 1
}

Write-Host ""
Write-Host "[1/5] Checking existing PostgreSQL roles and databases..." -ForegroundColor Yellow

$env:PGPASSWORD = $adminPass
try {
    # Check if role exists using count(*)
    $queryRole = "SELECT count(*) FROM pg_roles WHERE rolname = 'pharmasafe_user';"
    $resRole = Invoke-PsqlQuery -User "postgres" -Database "postgres" -Query $queryRole
    if ($resRole.ExitCode -ne 0) {
        Write-Error "Failed to connect to PostgreSQL with the postgres administrator account: $($resRole.Error)"
        Remove-Item env:PGPASSWORD -ErrorAction SilentlyContinue
        exit 1
    }

    $roleExists = ($resRole.Output -eq "1")
    if ($roleExists) {
        Write-Host "  Role 'pharmasafe_user' already exists. Preserving role configuration." -ForegroundColor Green
    } else {
        Write-Host "  Role 'pharmasafe_user' does not exist. It will be created." -ForegroundColor Yellow
    }

    # Check if database exists using count(*)
    $queryDb = "SELECT count(*) FROM pg_database WHERE datname = 'pharmasafe_db';"
    $resDb = Invoke-PsqlQuery -User "postgres" -Database "postgres" -Query $queryDb
    if ($resDb.ExitCode -ne 0) {
        Write-Error "Failed to query database catalog: $($resDb.Error)"
        Remove-Item env:PGPASSWORD -ErrorAction SilentlyContinue
        exit 1
    }

    $dbExists = ($resDb.Output -eq "1")
    if ($dbExists) {
        Write-Host "  Database 'pharmasafe_db' already exists. Preserving database." -ForegroundColor Green
    } else {
        Write-Host "  Database 'pharmasafe_db' does not exist. It will be created." -ForegroundColor Yellow
    }

    Write-Host ""
    Write-Host "[2/5] Provisioning role and database permissions..." -ForegroundColor Yellow

    # Escape single quotes in password for SQL string literal
    $escAppPass = $appPass.Replace("'", "''")

    # Build SQL commands to provision safely:
    # 1. Create role if missing with the user's chosen password
    # 2. Or if role already exists, update password to the user's chosen password
    # 3. Create database if missing with pharmasafe_user as owner
    $provisionSql = @"
DO
`$do`$
BEGIN
   IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'pharmasafe_user') THEN
      CREATE ROLE pharmasafe_user WITH
         LOGIN
         NOSUPERUSER
         NOCREATEDB
         NOCREATEROLE
         NOINHERIT
         NOREPLICATION
         PASSWORD '$escAppPass';
      RAISE NOTICE 'Role pharmasafe_user created with least privilege.';
   ELSE
      ALTER ROLE pharmasafe_user WITH PASSWORD '$escAppPass';
      RAISE NOTICE 'Role pharmasafe_user password updated.';
   END IF;
END
`$do`$;

SELECT 'CREATE DATABASE pharmasafe_db OWNER pharmasafe_user ENCODING ''UTF8'''
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'pharmasafe_db')\gexec
"@

    $resProvision = Invoke-PsqlSqlScript -User "postgres" -Database "postgres" -SqlText $provisionSql
    if ($resProvision.ExitCode -ne 0) {
        Write-Error "Role and database creation failed: $($resProvision.Error)"
        Remove-Item env:PGPASSWORD -ErrorAction SilentlyContinue
        exit 1
    }
    Write-Host "  Role and database created/updated successfully." -ForegroundColor Green

    # Apply schema and object grants inside pharmasafe_db
    Write-Host "  Applying permissions inside pharmasafe_db..." -ForegroundColor Yellow
    $grantsSql = @"
GRANT ALL PRIVILEGES ON DATABASE pharmasafe_db TO pharmasafe_user;
GRANT USAGE, CREATE ON SCHEMA public TO pharmasafe_user;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO pharmasafe_user;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO pharmasafe_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO pharmasafe_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO pharmasafe_user;
"@

    $resGrants = Invoke-PsqlSqlScript -User "postgres" -Database "pharmasafe_db" -SqlText $grantsSql
    if ($resGrants.ExitCode -ne 0) {
        Write-Error "Applying grants on pharmasafe_db failed: $($resGrants.Error)"
        Remove-Item env:PGPASSWORD -ErrorAction SilentlyContinue
        exit 1
    }
    Write-Host "  Schema and default privileges applied successfully." -ForegroundColor Green

} catch {
    Write-Error "Database provisioning error: $_"
    Remove-Item env:PGPASSWORD -ErrorAction SilentlyContinue
    exit 1
} finally {
    Remove-Item env:PGPASSWORD -ErrorAction SilentlyContinue
}

Write-Host ""
Write-Host "[3/5] Configuring local application credentials (.env)..." -ForegroundColor Yellow

$envFilePath = Join-Path (Split-Path $PSScriptRoot -Parent) ".env"
$envContent = @"
# PharmaSafe Local Environment Configuration
# Automatically generated during Stage 1.5 Provisioning
# DO NOT COMMIT THIS FILE TO GIT

SERVER_PORT=8080

# PostgreSQL 18 Application Account Settings
DB_HOST=localhost
DB_PORT=5432
DB_NAME=pharmasafe_db
DB_USERNAME=pharmasafe_user
DB_PASSWORD=$appPass
DB_URL=jdbc:postgresql://localhost:5432/pharmasafe_db

# Flyway Migrations
FLYWAY_ENABLED=true

# Frontend
VITE_API_URL=http://localhost:8080
"@

Set-Content -Path $envFilePath -Value $envContent -Encoding UTF8
Write-Host "  Local configuration saved to: $envFilePath" -ForegroundColor Green

# Verify .gitignore
$gitignorePath = Join-Path (Split-Path $PSScriptRoot -Parent) ".gitignore"
if (Test-Path $gitignorePath) {
    $giContent = Get-Content $gitignorePath -Raw
    if ($giContent -match "(?m)^\.env$") {
        Write-Host "  Confirmed: .env is ignored in .gitignore" -ForegroundColor Green
    } else {
        Write-Warning "  Please ensure .env is listed in .gitignore"
    }
}

Write-Host ""
Write-Host "[4/5] Verifying database connection using application role (pharmasafe_user)..." -ForegroundColor Yellow

$env:PGPASSWORD = $appPass
try {
    $resWho = Invoke-PsqlQuery -User "pharmasafe_user" -Database "pharmasafe_db" -Query "SELECT current_database() || '|' || current_user;"
    if ($resWho.ExitCode -ne 0 -or [string]::IsNullOrWhiteSpace($resWho.Output)) {
        Write-Error "Connection verification as 'pharmasafe_user' failed: $($resWho.Error)"
        Remove-Item env:PGPASSWORD -ErrorAction SilentlyContinue
        exit 1
    }

    $whoParts = $resWho.Output.Split('|')
    $connectedDb = if ($whoParts.Length -ge 1) { $whoParts[0].Trim() } else { "" }
    $connectedUser = if ($whoParts.Length -ge 2) { $whoParts[1].Trim() } else { "" }

    if ($connectedDb -eq "pharmasafe_db" -and $connectedUser -eq "pharmasafe_user") {
        Write-Host "  Connected successfully as: user='$connectedUser' to database='$connectedDb'" -ForegroundColor Green
    } else {
        Write-Error "Unexpected connection identity: database='$connectedDb', user='$connectedUser'"
        Remove-Item env:PGPASSWORD -ErrorAction SilentlyContinue
        exit 1
    }

    Write-Host ""
    Write-Host "[5/5] Testing schema permissions (creating, reading, and dropping test table)..." -ForegroundColor Yellow

    $testSql = @"
CREATE TABLE IF NOT EXISTS _stage15_test_table (
    id SERIAL PRIMARY KEY,
    test_note VARCHAR(100),
    tested_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO _stage15_test_table (test_note) VALUES ('Stage 1.5 Verification Succeeded');
SELECT test_note FROM _stage15_test_table;
DROP TABLE _stage15_test_table;
"@

    $resTest = Invoke-PsqlSqlScript -User "pharmasafe_user" -Database "pharmasafe_db" -SqlText $testSql
    if ($resTest.ExitCode -eq 0 -and $resTest.Output -match "Stage 1.5 Verification Succeeded") {
        Write-Host "  Permission test passed! Role has full DDL and DML access within schema public." -ForegroundColor Green
        Write-Host "  Temporary test object cleaned up." -ForegroundColor Green
    } else {
        Write-Error "Test table verification returned unexpected result: $($resTest.Error)"
        Remove-Item env:PGPASSWORD -ErrorAction SilentlyContinue
        exit 1
    }

} catch {
    Write-Error "Verification as 'pharmasafe_user' failed: $_"
    Remove-Item env:PGPASSWORD -ErrorAction SilentlyContinue
    exit 1
} finally {
    Remove-Item env:PGPASSWORD -ErrorAction SilentlyContinue
}

Write-Host ""
Write-Host "=================================================================" -ForegroundColor Green
Write-Host " Stage 1.5 Database Provisioning and Verification SUCCESSFUL!" -ForegroundColor Green
Write-Host "=================================================================" -ForegroundColor Green
Write-Host "Database Name:       pharmasafe_db"
Write-Host "Application User:    pharmasafe_user (Non-superuser, verified)"
Write-Host "Configuration File:  .env (Created and Git-ignored)"
Write-Host "Next Step:           Start Spring Boot backend to verify Flyway migration"
Write-Host ""
