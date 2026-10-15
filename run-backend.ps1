# PharmaSafe - Root Backend Launcher Script
# Automatically loads .env configuration and launches Spring Boot backend on port 8080

$ErrorActionPreference = "Stop"

$projectRoot = $PSScriptRoot
$envFile = Join-Path $projectRoot ".env"

if (Test-Path $envFile) {
    Write-Host "Loading environment variables from $envFile..." -ForegroundColor Cyan
    Get-Content $envFile | Where-Object { $_ -match '^([^#=]+)=(.*)$' } | ForEach-Object {
        $name = $matches[1].Trim()
        $val = $matches[2].Trim()
        [System.Environment]::SetEnvironmentVariable($name, $val, "Process")
    }
} else {
    Write-Warning "Local .env file not found. Starting with system environment settings."
}

$backendDir = Join-Path $projectRoot "backend"
Push-Location $backendDir
try {
    Write-Host "Starting PharmaSafe Spring Boot backend on port 8080..." -ForegroundColor Green
    mvn spring-boot:run
} finally {
    Pop-Location
}
