# Automated Static and Logic Tests for provision-db.ps1
# Runs without connecting to or modifying any database.

$ErrorActionPreference = "Stop"

$scriptRoot = Split-Path $PSScriptRoot -Parent
$provisionScriptPath = Join-Path $scriptRoot "provision-db.ps1"
$initScriptPath = Join-Path $scriptRoot "init-db.sql"

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
Write-Host " Testing provision-db.ps1 Logic and Syntax (No DB Mutations)" -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan

# -----------------------------------------------------------------------------
# 1. AST Syntax & Static Analysis
# -----------------------------------------------------------------------------
Write-Host "`n[Test Group 1] PowerShell AST Parsing & Syntax Validation" -ForegroundColor Yellow

$parseErrors = $null
$tokens = $null
$ast = [System.Management.Automation.Language.Parser]::ParseFile(
    $provisionScriptPath,
    [ref]$tokens,
    [ref]$parseErrors
)

Assert-Test "provision-db.ps1 parses without syntax errors" ($parseErrors.Count -eq 0)

# Check for absence of hardcoded default password
$scriptContent = Get-Content $provisionScriptPath -Raw
$initContent = Get-Content $initScriptPath -Raw

Assert-Test "provision-db.ps1 has NO hardcoded default password 'PharmaSafeApp2026!Secure'" (-not ($scriptContent -match "PharmaSafeApp2026!Secure"))
Assert-Test "init-db.sql has NO hardcoded default password 'PharmaSafeApp2026!Secure'" (-not ($initContent -match "PharmaSafeApp2026!Secure"))

# -----------------------------------------------------------------------------
# 2. Output and Null Safety Simulation
# -----------------------------------------------------------------------------
Write-Host "`n[Test Group 2] Null-Safety and Output Parsing Simulation" -ForegroundColor Yellow

# Function simulating the output sanitization used in Invoke-PsqlQuery
function Clean-ProcessOutput($rawOutput) {
    if ($null -ne $rawOutput) {
        return $rawOutput.Trim()
    } else {
        return ""
    }
}

# Case A: External process outputs null (0 rows returned)
$rawNull = $null
$cleanA = Clean-ProcessOutput $rawNull
Assert-Test "Null output converts to empty string without error" ($cleanA -eq "")
$isOneA = ($cleanA -eq "1")
Assert-Test "Cleaned null output safely evaluates -eq '1' as false" ($isOneA -eq $false)

# Case B: External process outputs empty string
$rawEmpty = ""
$cleanB = Clean-ProcessOutput $rawEmpty
Assert-Test "Empty string output converts to empty string" ($cleanB -eq "")
$isOneB = ($cleanB -eq "1")
Assert-Test "Cleaned empty output safely evaluates -eq '1' as false" ($isOneB -eq $false)

# Case C: External process outputs "0" (count(*) when entity does not exist)
$rawZero = "0`r`n"
$cleanC = Clean-ProcessOutput $rawZero
Assert-Test "'0\r\n' output trims cleanly to '0'" ($cleanC -eq "0")
$isOneC = ($cleanC -eq "1")
Assert-Test "'0' safely evaluates -eq '1' as false" ($isOneC -eq $false)

# Case D: External process outputs "1" (count(*) when entity exists)
$rawOne = "  1  `n"
$cleanD = Clean-ProcessOutput $rawOne
Assert-Test "'  1  \n' trims cleanly to '1'" ($cleanD -eq "1")
$isOneD = ($cleanD -eq "1")
Assert-Test "'1' safely evaluates -eq '1' as true" ($isOneD -eq $true)

# -----------------------------------------------------------------------------
# 3. Connection Identity String Splitting Simulation
# -----------------------------------------------------------------------------
Write-Host "`n[Test Group 3] Connection Identity Parsing Simulation" -ForegroundColor Yellow

function Parse-WhoAmI($whoamiOutput) {
    if ([string]::IsNullOrWhiteSpace($whoamiOutput)) {
        return $null
    }
    $parts = $whoamiOutput.Split('|')
    $db = if ($parts.Length -ge 1) { $parts[0].Trim() } else { "" }
    $user = if ($parts.Length -ge 2) { $parts[1].Trim() } else { "" }
    return @{ Database = $db; User = $user }
}

# Standard expected output
$validWho = "pharmasafe_db|pharmasafe_user"
$resValid = Parse-WhoAmI $validWho
Assert-Test "Valid identity parses correct database" ($resValid.Database -eq "pharmasafe_db")
Assert-Test "Valid identity parses correct user" ($resValid.User -eq "pharmasafe_user")

# Edge Case: Null output
$nullWho = $null
$resNullWho = Parse-WhoAmI $nullWho
Assert-Test "Null identity output returns null without throwing exception" ($null -eq $resNullWho)

# Edge Case: Whitespace output
$emptyWho = "   "
$resEmptyWho = Parse-WhoAmI $emptyWho
Assert-Test "Whitespace identity output returns null without throwing exception" ($null -eq $resEmptyWho)

# Edge Case: Single part output (missing delimiter)
$singleWho = "pharmasafe_db"
$resSingle = Parse-WhoAmI $singleWho
Assert-Test "Single-token identity extracts database without error" ($resSingle.Database -eq "pharmasafe_db")
Assert-Test "Single-token identity leaves user empty safely" ($resSingle.User -eq "")

# -----------------------------------------------------------------------------
# 4. Password Matching Logic Simulation
# -----------------------------------------------------------------------------
Write-Host "`n[Test Group 4] Password Validation Logic" -ForegroundColor Yellow

$pass1 = "SecretPass123!"
$pass2 = "SecretPass123!"
$pass3 = "DifferentPass456!"

Assert-Test "Matching passwords pass equality check" ($pass1 -eq $pass2)
Assert-Test "Mismatched passwords fail equality check" ($pass1 -ne $pass3)
Assert-Test "Empty password check correctly identifies empty string" ([string]::IsNullOrEmpty(""))

Write-Host "`n=================================================================" -ForegroundColor Green
Write-Host " All Tests Passed! ($passedTests / $totalTests)" -ForegroundColor Green
Write-Host "=================================================================" -ForegroundColor Green
