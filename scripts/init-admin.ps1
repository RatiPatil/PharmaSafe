# PharmaSafe Initial Administrator Setup Script
# Prompts securely for administrator credentials and registers the first admin account in PostgreSQL.
# Guarantees no plaintext passwords in command history, source code, or logs.

$ErrorActionPreference = "Stop"

$projectRoot = Split-Path $PSScriptRoot -Parent
$envFile = Join-Path $projectRoot ".env"

if (-not (Test-Path $envFile)) {
    Write-Error "Local .env file not found. Please ensure database provisioning (Stage 1.5) was completed first."
    exit 1
}

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host " PharmaSafe - Initial Administrator Initialization" -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "This utility initializes the primary administrative account."
Write-Host "If an administrator account already exists, initialization will be rejected." -ForegroundColor Yellow
Write-Host ""

# 1. Prompt for username
$adminUser = Read-Host -Prompt "Enter username for initial administrator (e.g. sysadmin)"
if ([string]::IsNullOrWhiteSpace($adminUser) -or $adminUser.Trim().Length -lt 3) {
    Write-Error "Administrator username must be at least 3 characters long."
    exit 1
}
$adminUser = $adminUser.Trim().ToLower()

# 2. Prompt for password securely
$adminPassSecure = Read-Host -Prompt "Enter secure password for '$adminUser' (min 8 chars)" -AsSecureString
if ($null -eq $adminPassSecure -or $adminPassSecure.Length -lt 8) {
    Write-Error "Administrator password must be at least 8 characters long."
    exit 1
}

$adminPassConfirm = Read-Host -Prompt "Confirm password for '$adminUser'" -AsSecureString
if ($null -eq $adminPassConfirm -or $adminPassConfirm.Length -lt 8) {
    Write-Error "Password confirmation cannot be empty."
    exit 1
}

$bstrPass = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($adminPassSecure)
$plainPass = [System.Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstrPass)
[System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstrPass)

$bstrConf = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($adminPassConfirm)
$plainConf = [System.Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstrConf)
[System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstrConf)

if ($plainPass -ne $plainConf) {
    Write-Error "Administrator passwords do not match. Initialization aborted."
    exit 1
}

Write-Host ""
Write-Host "Executing administrator initialization via Spring Boot backend..." -ForegroundColor Yellow

# Load .env into process environment for database connection
Get-Content $envFile | Where-Object { $_ -match '^([^#=]+)=(.*)$' } | ForEach-Object {
    $name = $matches[1].Trim()
    $val = $matches[2].Trim()
    [System.Environment]::SetEnvironmentVariable($name, $val, "Process")
}

# Set ephemeral initialization credentials
[System.Environment]::SetEnvironmentVariable("INIT_ADMIN_USERNAME", $adminUser, "Process")
[System.Environment]::SetEnvironmentVariable("INIT_ADMIN_PASSWORD", $plainPass, "Process")

$backendDir = Join-Path $projectRoot "backend"
$jarFile = Join-Path $backendDir "target\pharmasafe-backend-0.0.1-SNAPSHOT.jar"

try {
    if (Test-Path $jarFile) {
        Write-Host "Running initialization via compiled application jar..." -ForegroundColor Cyan
        & java -jar $jarFile --spring.main.web-application-type=none
    } else {
        Write-Host "Compiled jar not found. Building and running initialization via Maven..." -ForegroundColor Cyan
        Push-Location $backendDir
        mvn spring-boot:run -Dspring-boot.run.arguments="--spring.main.web-application-type=none"
        Pop-Location
    }

    Write-Host ""
    Write-Host "=================================================================" -ForegroundColor Green
    Write-Host " Administrator initialization process completed." -ForegroundColor Green
    Write-Host " Account: $adminUser" -ForegroundColor Green
    Write-Host " Role:    ROLE_ADMIN" -ForegroundColor Green
    Write-Host "=================================================================" -ForegroundColor Green
} catch {
    Write-Error "Administrator initialization encountered an error: $_"
} finally {
    # Scrub sensitive variables from process environment
    [System.Environment]::SetEnvironmentVariable("INIT_ADMIN_USERNAME", $null, "Process")
    [System.Environment]::SetEnvironmentVariable("INIT_ADMIN_PASSWORD", $null, "Process")
    Remove-Item env:INIT_ADMIN_USERNAME -ErrorAction SilentlyContinue
    Remove-Item env:INIT_ADMIN_PASSWORD -ErrorAction SilentlyContinue
}
