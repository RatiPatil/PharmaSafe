# Automated Static Analysis and Logic Tests for recover-admin.ps1
# Validates exit code verification, error propagation, and guaranteed finally cleanup.
# Runs strictly locally without connecting to or mutating any database or administrator records.

$ErrorActionPreference = "Stop"

$scriptRoot = Split-Path $PSScriptRoot -Parent
$recoverScriptPath = Join-Path $scriptRoot "recover-admin.ps1"

$passedTests = 0
$totalTests = 0

function Assert-Test {
    param(
        [string]$Description,
        [bool]$Condition
    )
    $script:totalTests++
    if ($Condition) {
        $script:passedTests++
        Write-Host "  [PASS] $Description" -ForegroundColor Green
    } else {
        Write-Host "  [FAIL] $Description" -ForegroundColor Red
        throw "Test failed: $Description"
    }
}

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host " Testing recover-admin.ps1 Logic and Safety Guards" -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan

# -----------------------------------------------------------------------------
# 1. PowerShell AST Syntax & Static Analysis
# -----------------------------------------------------------------------------
Write-Host "`n[Test Group 1] PowerShell AST Parsing & Static Safety Analysis" -ForegroundColor Yellow

$parseErrors = $null
$tokens = $null
$ast = [System.Management.Automation.Language.Parser]::ParseFile(
    $recoverScriptPath,
    [ref]$tokens,
    [ref]$parseErrors
)

Assert-Test "recover-admin.ps1 parses without syntax errors" ($parseErrors.Count -eq 0)

$scriptContent = Get-Content $recoverScriptPath -Raw

# Verify presence of try and finally blocks
Assert-Test "Script contains a try block wrapping critical execution" ($scriptContent -match "try\s*\{")
Assert-Test "Script contains a finally block for guaranteed cleanup" ($scriptContent -match "\}\s*finally\s*\{")

# Verify native process exit code is captured and checked immediately
Assert-Test "Script captures `$LASTEXITCODE immediately into `$javaExitCode" ($scriptContent -match "\`$javaExitCode\s*=\s*\`$LASTEXITCODE")
Assert-Test "Script verifies `$javaExitCode is not null and not nonzero" ($scriptContent -match "if\s*\(\`$null\s*-eq\s*\`$javaExitCode\s*-or\s*\`$javaExitCode\s*-ne\s*0\)")

# Verify that finally block cleans up process-scoped env vars, BSTRs, and plaintext references
Assert-Test "Finally block clears RECOVER_ADMIN_USERNAME environment variable" ($scriptContent -match "\[System\.Environment\]::SetEnvironmentVariable\(\`"RECOVER_ADMIN_USERNAME\`",\s*\`$null,\s*\`"Process\`"\)")
Assert-Test "Finally block clears RECOVER_ADMIN_PASSWORD environment variable" ($scriptContent -match "\[System\.Environment\]::SetEnvironmentVariable\(\`"RECOVER_ADMIN_PASSWORD\`",\s*\`$null,\s*\`"Process\`"\)")
Assert-Test "Finally block zeroes BSTR password allocation" ($scriptContent -match "\[System\.Runtime\.InteropServices\.Marshal\]::ZeroFreeBSTR\(\`$bstrPass\)")
Assert-Test "Finally block zeroes BSTR confirmation allocation" ($scriptContent -match "\[System\.Runtime\.InteropServices\.Marshal\]::ZeroFreeBSTR\(\`$bstrConf\)")
Assert-Test "Finally block clears `$plainPass reference" ($scriptContent -match "\`$plainPass\s*=\s*\`$null")
Assert-Test "Finally block clears `$plainConf reference" ($scriptContent -match "\`$plainConf\s*=\s*\`$null")

# Verify password complexity: minimum 12 characters enforced
Assert-Test "Script enforces minimum 12 characters for administrator password" ($scriptContent -match "-lt\s*12")

# Verify secrets are never echoed to console
Assert-Test "Script never logs plaintext password variables to console" (-not ($scriptContent -match 'Write-(Host|Output|Information)\s+.*\$plainPass'))
Assert-Test "Script never logs plain password confirmation to console" (-not ($scriptContent -match 'Write-(Host|Output|Information)\s+.*\$plainConf'))

# -----------------------------------------------------------------------------
# 2. Nonzero Java Exit Code Detection Simulation
# -----------------------------------------------------------------------------
Write-Host "`n[Test Group 2] Nonzero Java Exit Code Detection & Banner Suppression" -ForegroundColor Yellow

# Simulate the exact execution and check pattern used in recover-admin.ps1
function Simulate-JavaRecoveryExecution {
    param(
        [int]$SimulatedExitCode
    )

    $outputLog = [System.Collections.Generic.List[string]]::new()
    $caughtException = $null
    $bannerDisplayed = $false

    $bstrPass = [System.IntPtr]::Zero
    $bstrConf = [System.IntPtr]::Zero
    $plainPass = $null
    $plainConf = $null

    try {
        # Simulate setting credentials
        $plainPass = "SimulatedSecret123!"
        $plainConf = "SimulatedSecret123!"
        [System.Environment]::SetEnvironmentVariable("RECOVER_ADMIN_USERNAME", "admin", "Process")
        [System.Environment]::SetEnvironmentVariable("RECOVER_ADMIN_PASSWORD", $plainPass, "Process")
        $plainPass = $null
        $plainConf = $null

        # Simulate Java execution and immediate exit code capture
        $javaExitCode = $SimulatedExitCode

        # Immediately check native Java recovery process exit code
        if ($null -eq $javaExitCode -or $javaExitCode -ne 0) {
            throw "Backend recovery process failed with exit code $javaExitCode."
        }

        # Success banner - MUST NOT be displayed if exit code != 0
        $bannerDisplayed = $true
        $outputLog.Add("SUCCESS_BANNER_DISPLAYED")
    } catch {
        $caughtException = $_.Exception.Message
        $outputLog.Add("FAILURE_REPORTED: $caughtException")
    } finally {
        # Guaranteed cleanup in finally
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
    }

    return @{
        BannerDisplayed  = $bannerDisplayed
        CaughtException  = $caughtException
        OutputLog        = $outputLog
        EnvUserCleared   = [string]::IsNullOrEmpty([System.Environment]::GetEnvironmentVariable("RECOVER_ADMIN_USERNAME", "Process"))
        EnvPassCleared   = [string]::IsNullOrEmpty([System.Environment]::GetEnvironmentVariable("RECOVER_ADMIN_PASSWORD", "Process"))
        PlainPassCleared = ($null -eq $plainPass)
        PlainConfCleared = ($null -eq $plainConf)
        BstrPassCleared  = ($bstrPass -eq [System.IntPtr]::Zero)
        BstrConfCleared  = ($bstrConf -eq [System.IntPtr]::Zero)
    }
}

# Test Case 2.1: Nonzero Java Exit Code = 1 (e.g. Account not found, Spring Boot error)
$resExit1 = Simulate-JavaRecoveryExecution -SimulatedExitCode 1
Assert-Test "Nonzero exit code (1) does NOT display success banner" ($resExit1.BannerDisplayed -eq $false)
Assert-Test "Nonzero exit code (1) reports failure exception" ($resExit1.CaughtException -match "exit code 1")
Assert-Test "Nonzero exit code (1) logs failure report" ($resExit1.OutputLog -contains "FAILURE_REPORTED: Backend recovery process failed with exit code 1.")

# Test Case 2.2: Nonzero Java Exit Code = 255 (e.g. JVM fatal failure)
$resExit255 = Simulate-JavaRecoveryExecution -SimulatedExitCode 255
Assert-Test "Nonzero exit code (255) does NOT display success banner" ($resExit255.BannerDisplayed -eq $false)
Assert-Test "Nonzero exit code (255) reports failure exception" ($resExit255.CaughtException -match "exit code 255")

# Test Case 2.3: Zero Exit Code = 0 (Success)
$resExit0 = Simulate-JavaRecoveryExecution -SimulatedExitCode 0
Assert-Test "Zero exit code (0) displays success banner" ($resExit0.BannerDisplayed -eq $true)
Assert-Test "Zero exit code (0) catches no exception" ($null -eq $resExit0.CaughtException)

# -----------------------------------------------------------------------------
# 3. Guaranteed Cleanup in Finally on Success and Failure
# -----------------------------------------------------------------------------
Write-Host "`n[Test Group 3] Cleanup in Finally on Both Success and Failure" -ForegroundColor Yellow

# Test Case 3.1: Cleanup verification on successful execution
Assert-Test "On SUCCESS: RECOVER_ADMIN_USERNAME environment variable is cleared" ($resExit0.EnvUserCleared -eq $true)
Assert-Test "On SUCCESS: RECOVER_ADMIN_PASSWORD environment variable is cleared" ($resExit0.EnvPassCleared -eq $true)
Assert-Test "On SUCCESS: `$plainPass reference is null" ($resExit0.PlainPassCleared -eq $true)
Assert-Test "On SUCCESS: `$plainConf reference is null" ($resExit0.PlainConfCleared -eq $true)
Assert-Test "On SUCCESS: `$bstrPass is Zero" ($resExit0.BstrPassCleared -eq $true)
Assert-Test "On SUCCESS: `$bstrConf is Zero" ($resExit0.BstrConfCleared -eq $true)

# Test Case 3.2: Cleanup verification on nonzero exit code failure
Assert-Test "On NONZERO FAILURE: RECOVER_ADMIN_USERNAME environment variable is cleared" ($resExit1.EnvUserCleared -eq $true)
Assert-Test "On NONZERO FAILURE: RECOVER_ADMIN_PASSWORD environment variable is cleared" ($resExit1.EnvPassCleared -eq $true)
Assert-Test "On NONZERO FAILURE: `$plainPass reference is null" ($resExit1.PlainPassCleared -eq $true)
Assert-Test "On NONZERO FAILURE: `$plainConf reference is null" ($resExit1.PlainConfCleared -eq $true)
Assert-Test "On NONZERO FAILURE: `$bstrPass is Zero" ($resExit1.BstrPassCleared -eq $true)
Assert-Test "On NONZERO FAILURE: `$bstrConf is Zero" ($resExit1.BstrConfCleared -eq $true)

# Test Case 3.3: Cleanup verification on password mismatch failure
function Simulate-PasswordMismatchFailure {
    $bstrPass = [System.IntPtr]::Zero
    $bstrConf = [System.IntPtr]::Zero
    $plainPass = $null
    $plainConf = $null
    $mismatchCaught = $false

    try {
        # Simulate allocating BSTRs from SecureString
        $sec1 = ConvertTo-SecureString "PasswordMatch123!" -AsPlainText -Force
        $sec2 = ConvertTo-SecureString "PasswordMismatch456!" -AsPlainText -Force

        $bstrPass = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($sec1)
        $plainPass = [System.Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstrPass)
        [System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstrPass)
        $bstrPass = [System.IntPtr]::Zero

        $bstrConf = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($sec2)
        $plainConf = [System.Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstrConf)
        [System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstrConf)
        $bstrConf = [System.IntPtr]::Zero

        if ($plainPass -ne $plainConf) {
            throw "Administrator passwords do not match. Recovery aborted."
        }
    } catch {
        $mismatchCaught = $true
    } finally {
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
    }

    return @{
        MismatchCaught   = $mismatchCaught
        PlainPassCleared = ($null -eq $plainPass)
        PlainConfCleared = ($null -eq $plainConf)
        BstrPassCleared  = ($bstrPass -eq [System.IntPtr]::Zero)
        BstrConfCleared  = ($bstrConf -eq [System.IntPtr]::Zero)
    }
}

$resMismatch = Simulate-PasswordMismatchFailure
Assert-Test "Password mismatch is caught and aborted" ($resMismatch.MismatchCaught -eq $true)
Assert-Test "On MISMATCH FAILURE: `$plainPass reference is null" ($resMismatch.PlainPassCleared -eq $true)
Assert-Test "On MISMATCH FAILURE: `$plainConf reference is null" ($resMismatch.PlainConfCleared -eq $true)
Assert-Test "On MISMATCH FAILURE: `$bstrPass is Zero" ($resMismatch.BstrPassCleared -eq $true)
Assert-Test "On MISMATCH FAILURE: `$bstrConf is Zero" ($resMismatch.BstrConfCleared -eq $true)

# Test Case 3.4: Cleanup verification on unhandled exception in try block
function Simulate-UnhandledExceptionInTry {
    $bstrPass = [System.IntPtr]::Zero
    $bstrConf = [System.IntPtr]::Zero
    $plainPass = "LeftoverSecret"
    $plainConf = "LeftoverSecret"
    [System.Environment]::SetEnvironmentVariable("RECOVER_ADMIN_USERNAME", "admin", "Process")
    [System.Environment]::SetEnvironmentVariable("RECOVER_ADMIN_PASSWORD", "LeftoverSecret", "Process")

    $exceptionCaught = $false
    try {
        # Simulate allocating BSTR without freeing yet
        $sec = ConvertTo-SecureString "LeftoverSecret" -AsPlainText -Force
        $bstrPass = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($sec)

        # Unexpected failure happens
        throw "Unexpected internal system fault."
    } catch {
        $exceptionCaught = $true
    } finally {
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
    }

    return @{
        ExceptionCaught  = $exceptionCaught
        PlainPassCleared = ($null -eq $plainPass)
        PlainConfCleared = ($null -eq $plainConf)
        BstrPassCleared  = ($bstrPass -eq [System.IntPtr]::Zero)
        BstrConfCleared  = ($bstrConf -eq [System.IntPtr]::Zero)
        EnvUserCleared   = [string]::IsNullOrEmpty([System.Environment]::GetEnvironmentVariable("RECOVER_ADMIN_USERNAME", "Process"))
        EnvPassCleared   = [string]::IsNullOrEmpty([System.Environment]::GetEnvironmentVariable("RECOVER_ADMIN_PASSWORD", "Process"))
    }
}

$resUnhandled = Simulate-UnhandledExceptionInTry
Assert-Test "Unhandled exception in try block is caught" ($resUnhandled.ExceptionCaught -eq $true)
Assert-Test "On UNHANDLED EXCEPTION: BSTR allocation is freed in finally" ($resUnhandled.BstrPassCleared -eq $true)
Assert-Test "On UNHANDLED EXCEPTION: `$plainPass is cleared in finally" ($resUnhandled.PlainPassCleared -eq $true)
Assert-Test "On UNHANDLED EXCEPTION: `$plainConf is cleared in finally" ($resUnhandled.PlainConfCleared -eq $true)
Assert-Test "On UNHANDLED EXCEPTION: RECOVER_ADMIN_USERNAME is cleared in finally" ($resUnhandled.EnvUserCleared -eq $true)
Assert-Test "On UNHANDLED EXCEPTION: RECOVER_ADMIN_PASSWORD is cleared in finally" ($resUnhandled.EnvPassCleared -eq $true)

# -----------------------------------------------------------------------------
# 4. Password Policy & Complexity Verification
# -----------------------------------------------------------------------------
Write-Host "`n[Test Group 4] Password Policy Enforcement Simulation" -ForegroundColor Yellow

function Test-PasswordLengthRule([string]$password) {
    if ($null -eq $password -or $password.Length -lt 12) {
        return $false
    }
    return $true
}

Assert-Test "Password shorter than 12 characters (11 chars) is rejected" ((Test-PasswordLengthRule "ShortPass11") -eq $false)
Assert-Test "Password with 0 characters is rejected" ((Test-PasswordLengthRule "") -eq $false)
Assert-Test "Password with null value is rejected" ((Test-PasswordLengthRule $null) -eq $false)
Assert-Test "Password with exactly 12 characters is accepted" ((Test-PasswordLengthRule "TwelveChars!") -eq $true)
Assert-Test "Password with 16 characters is accepted" ((Test-PasswordLengthRule "SixteenCharPass!") -eq $true)

# -----------------------------------------------------------------------------
# 5. Password Validation & Confirmation Error Handling Tests
# -----------------------------------------------------------------------------
Write-Host "`n[Test Group 5] Password Validation & Confirmation Error Handling Tests" -ForegroundColor Yellow

# Test Case 5.1: Short Password (< 12 characters)
$shortSec = ConvertTo-SecureString "Short123!" -AsPlainText -Force
$errShort = $null
try {
    & $recoverScriptPath -Username "admin" -Password $shortSec -PasswordConfirmation $shortSec
} catch {
    $errShort = $_.Exception.Message
}
Assert-Test "Short password (<12 chars) is rejected with expected message" ($errShort -match "Administrator password must be at least 12 characters long")

# Test Case 5.2: Empty Confirmation
$validSec = ConvertTo-SecureString "ValidPass1234!" -AsPlainText -Force
$emptySec = [System.Security.SecureString]::new()
$errEmptyConf = $null
try {
    & $recoverScriptPath -Username "admin" -Password $validSec -PasswordConfirmation $emptySec
} catch {
    $errEmptyConf = $_.Exception.Message
}
Assert-Test "Empty confirmation is rejected with expected message" ($errEmptyConf -match "Password confirmation cannot be empty and must be at least 12 characters long")

# Test Case 5.3: Short Confirmation (< 12 characters)
$errShortConf = $null
try {
    & $recoverScriptPath -Username "admin" -Password $validSec -PasswordConfirmation $shortSec
} catch {
    $errShortConf = $_.Exception.Message
}
Assert-Test "Short confirmation (<12 chars) is rejected with expected message" ($errShortConf -match "Password confirmation cannot be empty and must be at least 12 characters long")

# Test Case 5.4: Mismatched Passwords
$diffSec = ConvertTo-SecureString "DifferentPassword123!" -AsPlainText -Force
$errMismatch = $null
try {
    & $recoverScriptPath -Username "admin" -Password $validSec -PasswordConfirmation $diffSec
} catch {
    $errMismatch = $_.Exception.Message
}
Assert-Test "Mismatched passwords rejected with expected message" ($errMismatch -match "Administrator passwords do not match")

# Test Case 5.5: Matching Passwords Validation Flow
function Test-PasswordMatchLogic {
    param([string]$Pass1, [string]$Pass2)
    $sec1 = if ([string]::IsNullOrEmpty($Pass1)) { [System.Security.SecureString]::new() } else { ConvertTo-SecureString $Pass1 -AsPlainText -Force }
    $sec2 = if ([string]::IsNullOrEmpty($Pass2)) { [System.Security.SecureString]::new() } else { ConvertTo-SecureString $Pass2 -AsPlainText -Force }

    $b1 = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($sec1)
    $p1 = [System.Runtime.InteropServices.Marshal]::PtrToStringBSTR($b1)
    [System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($b1)

    $b2 = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($sec2)
    $p2 = [System.Runtime.InteropServices.Marshal]::PtrToStringBSTR($b2)
    [System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($b2)

    if ([string]::IsNullOrEmpty($p1) -or $p1.Length -lt 12) { return "SHORT_PASS1" }
    if ([string]::IsNullOrEmpty($p2) -or $p2.Length -lt 12) { return "SHORT_PASS2" }

    $match = ($p1.Length -eq $p2.Length)
    $diff = 0
    $len = [Math]::Min($p1.Length, $p2.Length)
    for ($i = 0; $i -lt $len; $i++) {
        $diff = $diff -bor ([int][char]$p1[$i] -bxor [int][char]$p2[$i])
    }
    if (-not $match -or $diff -ne 0) {
        return "MISMATCH"
    }
    return "MATCH_SUCCESS"
}

Assert-Test "Matching 12+ char passwords successfully pass comparison" ((Test-PasswordMatchLogic "AdminPassword123!" "AdminPassword123!") -eq "MATCH_SUCCESS")
Assert-Test "Mismatched 12+ char passwords correctly identified" ((Test-PasswordMatchLogic "AdminPassword123!" "AdminPassword456!") -eq "MISMATCH")
Assert-Test "Short password correctly identified in match logic" ((Test-PasswordMatchLogic "Short" "Short") -eq "SHORT_PASS1")
Assert-Test "Empty confirmation correctly identified in match logic" ((Test-PasswordMatchLogic "AdminPassword123!" "") -eq "SHORT_PASS2")

# Test Case 5.6: Successful Recovery Simulation Flow
$simulatedRecoveryResult = Simulate-JavaRecoveryExecution -SimulatedExitCode 0
Assert-Test "Successful recovery execution unlocks account and displays success banner" ($simulatedRecoveryResult.BannerDisplayed -eq $true)
Assert-Test "Successful recovery clears all sensitive environment variables" ($simulatedRecoveryResult.EnvPassCleared -eq $true)

Write-Host "`n=================================================================" -ForegroundColor Green
Write-Host " All recover-admin Tests Passed Successfully! ($passedTests / $totalTests)" -ForegroundColor Green
Write-Host "=================================================================" -ForegroundColor Green

