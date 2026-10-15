# PharmaSafe Safe Administrator Authentication Diagnostic Tool
# Tests administrator credentials against the database without altering state or locking accounts.
# Never prints or logs the password or hash.

$ErrorActionPreference = "Stop"

$projectRoot = Split-Path $PSScriptRoot -Parent
$envFile = Join-Path $projectRoot ".env"

if (-not (Test-Path $envFile)) {
    Write-Error "Local .env file not found. Please ensure database provisioning (Stage 1.5) was completed first."
    exit 1
}

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host " PharmaSafe - Safe Administrator Authentication Diagnostic" -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "This diagnostic checks if credentials match the PostgreSQL database" -ForegroundColor Gray
Write-Host "WITHOUT modifying failed attempt counters or locking the account." -ForegroundColor Gray
Write-Host ""

# 1. Prompt for username
$adminUser = Read-Host -Prompt "Enter username to diagnose (e.g. admin)"
if ([string]::IsNullOrWhiteSpace($adminUser)) {
    Write-Error "Username cannot be empty."
    exit 1
}
$adminUser = $adminUser.Trim().ToLower()

# 2. Prompt for password securely
$adminPassSecure = Read-Host -Prompt "Enter password to test for '$adminUser'" -AsSecureString
if ($null -eq $adminPassSecure) {
    Write-Error "Password cannot be empty."
    exit 1
}

$bstrPass = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($adminPassSecure)
$plainPass = [System.Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstrPass)
[System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstrPass)

Write-Host ""
Write-Host "Running safe authentication diagnosis..." -ForegroundColor Yellow

# Load .env into process environment for database connection
Get-Content $envFile | Where-Object { $_ -match '^([^#=]+)=(.*)$' } | ForEach-Object {
    $name = $matches[1].Trim()
    $val = $matches[2].Trim()
    [System.Environment]::SetEnvironmentVariable($name, $val, "Process")
}

# Set ephemeral diagnostic credentials
[System.Environment]::SetEnvironmentVariable("DIAGNOSE_USERNAME", $adminUser, "Process")
[System.Environment]::SetEnvironmentVariable("DIAGNOSE_PASSWORD", $plainPass, "Process")

$backendDir = Join-Path $projectRoot "backend"
$jarFile = Join-Path $backendDir "target\pharmasafe-backend-0.0.1-SNAPSHOT.jar"

try {
    if (Test-Path $jarFile) {
        & java -jar $jarFile --spring.main.web-application-type=none
    } else {
        Push-Location $backendDir
        mvn spring-boot:run -Dspring-boot.run.arguments="--spring.main.web-application-type=none"
        Pop-Location
    }
} catch {
    Write-Error "Authentication diagnosis encountered an error: $_"
} finally {
    # Scrub sensitive variables from process environment
    [System.Environment]::SetEnvironmentVariable("DIAGNOSE_USERNAME", $null, "Process")
    [System.Environment]::SetEnvironmentVariable("DIAGNOSE_PASSWORD", $null, "Process")
    Remove-Item env:DIAGNOSE_USERNAME -ErrorAction SilentlyContinue
    Remove-Item env:DIAGNOSE_PASSWORD -ErrorAction SilentlyContinue
}
