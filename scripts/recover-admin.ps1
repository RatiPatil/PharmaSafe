# PharmaSafe Administrator Account Recovery Tool
# Securely unlocks a locked administrator account and resets the password.
# Enforces enterprise password complexity (min 12 chars) with masked input.
# Guarantees no plaintext passwords or hashes in logs, history, or console.

[CmdletBinding()]
param(
    [Parameter(Mandatory=$false)][string]$Username,
    [Parameter(Mandatory=$false)][System.Security.SecureString]$Password,
    [Parameter(Mandatory=$false)][System.Security.SecureString]$PasswordConfirmation
)

$ErrorActionPreference = "Stop"

$projectRoot = Split-Path $PSScriptRoot -Parent
$envFile = Join-Path $projectRoot ".env"

if (-not (Test-Path $envFile)) {
    Write-Error "Local .env file not found. Please ensure database provisioning was completed first."
    exit 1
}

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host " PharmaSafe - Administrator Account Recovery & Unlock" -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "This procedure unlocks a locked administrator account and resets" -ForegroundColor Gray
Write-Host "the password using BCrypt. Execution is strictly local." -ForegroundColor Gray
Write-Host ""

# Helper to flush lingering console input buffer keystrokes (e.g. residual Enter/newline)
function Clear-ConsoleInputBuffer {
    try {
        if ($host.UI.RawUI -and $host.UI.RawUI.KeyAvailable) {
            while ($host.UI.RawUI.KeyAvailable) {
                $null = $host.UI.RawUI.ReadKey("NoEcho,IncludeKeyUp")
            }
        }
    } catch {
        # Host does not support RawUI KeyAvailable (e.g., redirected or background stream)
    }
}

# Helper to safely read secure/masked input across both interactive console and redirected streams
function Read-SecureInputSafe {
    param(
        [string]$Prompt
    )

    Clear-ConsoleInputBuffer

    # If standard input is redirected, read line from Console
    if ([Console]::IsInputRedirected) {
        $line = [Console]::ReadLine()
        if ($null -eq $line) {
            return $null
        }
        return (ConvertTo-SecureString $line -AsPlainText -Force)
    }

    # Interactive console with masked input
    return (Read-Host -Prompt $Prompt -AsSecureString)
}

# 1. Determine administrator username (parameter or prompt, default: admin)
$adminUser = $Username
if ([string]::IsNullOrWhiteSpace($adminUser)) {
    if ([Console]::IsInputRedirected) {
        $adminUser = [Console]::ReadLine()
    } else {
        $adminUser = Read-Host -Prompt "Enter administrator username to recover [admin]"
    }
}
if ([string]::IsNullOrWhiteSpace($adminUser)) {
    $adminUser = "admin"
}
$adminUser = $adminUser.Trim().ToLower()

# Initialize references for cleanup tracking
$bstrPass = [System.IntPtr]::Zero
$bstrConf = [System.IntPtr]::Zero
$plainPass = $null
$plainConf = $null

try {
    # 2. Prompt for new password securely with masked input (or use parameter)
    $newPassSecure = $Password
    if ($null -eq $newPassSecure) {
        $newPassSecure = Read-SecureInputSafe -Prompt "Enter new secure password for '$adminUser' (min 12 chars)"
    }
    if ($null -eq $newPassSecure -or $newPassSecure.Length -lt 12) {
        throw "Administrator password must be at least 12 characters long. Recovery aborted."
    }

    # 3. Prompt for password confirmation (flushing console input buffer before prompt)
    $newPassConfirm = $PasswordConfirmation
    if ($null -eq $newPassConfirm) {
        $newPassConfirm = Read-SecureInputSafe -Prompt "Confirm new password for '$adminUser'"
    }
    if ($null -eq $newPassConfirm -or $newPassConfirm.Length -lt 12) {
        throw "Password confirmation cannot be empty and must be at least 12 characters long. Recovery aborted."
    }

    # Convert SecureString to unmanaged BSTR
    $bstrPass = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($newPassSecure)
    $plainPass = [System.Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstrPass)
    [System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstrPass)
    $bstrPass = [System.IntPtr]::Zero

    $bstrConf = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($newPassConfirm)
    $plainConf = [System.Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstrConf)
    [System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstrConf)
    $bstrConf = [System.IntPtr]::Zero

    if ([string]::IsNullOrEmpty($plainPass) -or $plainPass.Length -lt 12) {
        throw "Administrator password must be at least 12 characters long. Recovery aborted."
    }

    if ([string]::IsNullOrEmpty($plainConf) -or $plainConf.Length -lt 12) {
        throw "Password confirmation cannot be empty and must be at least 12 characters long. Recovery aborted."
    }

    # Constant-time comparison to prevent timing side channels
    $match = ($plainPass.Length -eq $plainConf.Length)
    $diff = 0
    $len = [Math]::Min($plainPass.Length, $plainConf.Length)
    for ($i = 0; $i -lt $len; $i++) {
        $diff = $diff -bor ([int][char]$plainPass[$i] -bxor [int][char]$plainConf[$i])
    }
    if (-not $match -or $diff -ne 0) {
        throw "Administrator passwords do not match. Recovery aborted."
    }

    Write-Host ""
    Write-Host "Executing secure local administrator recovery via Spring Boot..." -ForegroundColor Yellow

    # Load .env into process environment for database connection
    Get-Content $envFile | Where-Object { $_ -match '^([^#=]+)=(.*)$' } | ForEach-Object {
        $name = $matches[1].Trim()
        $val = $matches[2].Trim()
        [System.Environment]::SetEnvironmentVariable($name, $val, "Process")
    }

    # Set ephemeral recovery credentials in process environment
    [System.Environment]::SetEnvironmentVariable("RECOVER_ADMIN_USERNAME", $adminUser, "Process")
    [System.Environment]::SetEnvironmentVariable("RECOVER_ADMIN_PASSWORD", $plainPass, "Process")

    # Clear plaintext references immediately after setting process environment
    $plainPass = $null
    $plainConf = $null

    $backendDir = Join-Path $projectRoot "backend"
    $jarFile = Join-Path $backendDir "target\pharmasafe-backend-0.0.1-SNAPSHOT.jar"

    $javaExitCode = -1
    if (Test-Path $jarFile) {
        & java -jar $jarFile --spring.main.web-application-type=none
        $javaExitCode = $LASTEXITCODE
    } else {
        Push-Location $backendDir
        try {
            mvn spring-boot:run -Dspring-boot.run.arguments="--spring.main.web-application-type=none"
            $javaExitCode = $LASTEXITCODE
        } finally {
            Pop-Location
        }
    }

    # Immediately check native Java recovery process exit code
    if ($null -eq $javaExitCode -or $javaExitCode -ne 0) {
        throw "Backend recovery process failed with exit code $javaExitCode."
    }

    Write-Host ""
    Write-Host "=================================================================" -ForegroundColor Green
    Write-Host " Administrator account recovery completed successfully." -ForegroundColor Green
    Write-Host " Account:        $adminUser" -ForegroundColor Green
    Write-Host " Lock Status:    UNLOCKED (failed_login_attempts reset to 0)" -ForegroundColor Green
    Write-Host " BCrypt Hash:    UPDATED & VERIFIED" -ForegroundColor Green
    Write-Host " Audit Log:      ADMIN_ACCOUNT_RECOVERED event recorded" -ForegroundColor Green
    Write-Host "=================================================================" -ForegroundColor Green
    Write-Host ""
    Write-Host "You may now log in at http://localhost:5173 with your new credentials." -ForegroundColor Cyan
} catch {
    Write-Error "Administrator recovery failed: $($_.Exception.Message)"
    exit 1
} finally {
    # Scrub sensitive credentials, references, and allocations unconditionally
    if ($bstrPass -ne [System.IntPtr]::Zero) {
        [System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstrPass)
        $bstrPass = [System.IntPtr]::Zero
    }
    if ($bstrConf -ne [System.IntPtr]::Zero) {
        [System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstrConf)
        $bstrConf = [System.IntPtr]::Zero
    }
    $plainPass = $null
    $plainConf = $null
    [System.Environment]::SetEnvironmentVariable("RECOVER_ADMIN_USERNAME", $null, "Process")
    [System.Environment]::SetEnvironmentVariable("RECOVER_ADMIN_PASSWORD", $null, "Process")
    Remove-Item env:RECOVER_ADMIN_USERNAME -ErrorAction SilentlyContinue
    Remove-Item env:RECOVER_ADMIN_PASSWORD -ErrorAction SilentlyContinue
    [System.GC]::Collect()
    [System.GC]::WaitForPendingFinalizers()
}
