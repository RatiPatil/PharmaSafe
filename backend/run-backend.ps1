# PharmaSafe - Start Spring Boot Backend with .env Variables
# Loads local .env configuration into the process environment and boots Spring Boot.

$ErrorActionPreference = "Stop"

$envFile = Join-Path (Split-Path $PSScriptRoot -Parent) ".env"
if (Test-Path $envFile) {
    Write-Host "Loading environment variables from $envFile..." -ForegroundColor Cyan
    Get-Content $envFile | Where-Object { $_ -match '^([^#=]+)=(.*)$' } | ForEach-Object {
        $name = $matches[1].Trim()
        $val = $matches[2].Trim()
        [System.Environment]::SetEnvironmentVariable($name, $val, "Process")
    }
} else {
    Write-Warning ".env file not found. Running with default/existing environment settings."
}

Write-Host "Starting PharmaSafe Spring Boot backend on port 8080..." -ForegroundColor Green
$jarFile = Join-Path $PSScriptRoot "target\pharmasafe-backend-0.0.1-SNAPSHOT.jar"
if (Test-Path $jarFile) {
    & java -jar $jarFile
} else {
    mvn spring-boot:run
}
