$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$backupScript = Join-Path $repoRoot "infra\scripts\backup.ps1"
$restoreScript = Join-Path $repoRoot "infra\scripts\restore.ps1"
$productionCompose = Join-Path $repoRoot "infra\docker-compose.yml"
$localDebugCompose = Join-Path $repoRoot "infra\docker-compose.local-debug.yml"
$nginxConfig = Join-Path $repoRoot "admin-web\nginx.conf"

function Assert-Condition {
    param(
        [bool] $Condition,
        [string] $Message
    )

    if (-not $Condition) {
        throw $Message
    }
}

Assert-Condition (Test-Path -LiteralPath $backupScript) "Missing infra/scripts/backup.ps1"
Assert-Condition (Test-Path -LiteralPath $restoreScript) "Missing infra/scripts/restore.ps1"
Assert-Condition (Test-Path -LiteralPath $productionCompose) "Missing infra/docker-compose.yml"
Assert-Condition (Test-Path -LiteralPath $localDebugCompose) "Missing infra/docker-compose.local-debug.yml"
Assert-Condition (Test-Path -LiteralPath $nginxConfig) "Missing admin-web/nginx.conf"

Push-Location $repoRoot
try {
    git check-ignore -q "infra/backups/"
    Assert-Condition ($LASTEXITCODE -eq 0) "infra/backups/ must be ignored by Git"
    git check-ignore -q "infra/restored-config/example.env"
    Assert-Condition ($LASTEXITCODE -eq 0) "infra/restored-config/ must be ignored by Git"
}
finally {
    Pop-Location
}

$backupContent = Get-Content -LiteralPath $backupScript -Raw
$restoreContent = Get-Content -LiteralPath $restoreScript -Raw
$productionComposeContent = Get-Content -LiteralPath $productionCompose -Raw
$localDebugComposeContent = Get-Content -LiteralPath $localDebugCompose -Raw
$nginxContent = Get-Content -LiteralPath $nginxConfig -Raw

foreach ($required in @("pg_dump", "-Fc", "docker compose", "-f `$composePath", "docker compose --env-file `$envPath -f `$composePath cp", "manifest.json")) {
    Assert-Condition ($backupContent.Contains($required)) "backup.ps1 must contain '$required'"
}

foreach ($required in @("pg_restore", "--clean", "--if-exists", "-f `$composePath", "docker compose --env-file `$envPath -f `$composePath cp", "ConfirmRestore")) {
    Assert-Condition ($restoreContent.Contains($required)) "restore.ps1 must contain '$required'"
}

foreach ($required in @("IncludeEncryptedConfig", "environment.dpapi", "ProtectedData", "CurrentUser")) {
    Assert-Condition ($backupContent.Contains($required)) "backup.ps1 must contain '$required'"
}

Assert-Condition (-not ($backupContent -match 'Copy-Item[^\r\n]+\$envPath')) "backup.ps1 must never copy the environment file as plaintext"

foreach ($required in @("RestoreEncryptedConfig", "ConfigRestorePath", "environment.dpapi", "ProtectedData", "CurrentUser", "infra/restored-config", "IsPathRooted", "ReparsePoint", "CreateNew")) {
    Assert-Condition ($restoreContent.Contains($required)) "restore.ps1 must contain '$required'"
}

foreach ($service in @("postgres", "redis")) {
    $servicePattern = "(?ms)^  ${service}:\r?\n(?<body>.*?)(?=^  [a-zA-Z0-9-]+:\r?\n|^volumes:\r?\n)"
    $serviceMatch = [regex]::Match($productionComposeContent, $servicePattern)
    Assert-Condition $serviceMatch.Success "Production Compose must define the $service service"
    Assert-Condition (-not ($serviceMatch.Groups["body"].Value -match "(?m)^    ports:\s*$")) "Production Compose must not publish the $service port"
}

Assert-Condition ($localDebugComposeContent -match '(?m)^\s*-\s*"127\.0\.0\.1:\$\{POSTGRES_PORT:-5432\}:5432"\s*$') "Local debug Compose must bind PostgreSQL to 127.0.0.1"
Assert-Condition ($localDebugComposeContent -match '(?m)^\s*-\s*"127\.0\.0\.1:\$\{REDIS_PORT:-6379\}:6379"\s*$') "Local debug Compose must bind Redis to 127.0.0.1"
Assert-Condition ($productionComposeContent -match 'POSTGRES_PASSWORD:\s*\$\{POSTGRES_PASSWORD:\?[^}]+\}') "Production Compose must require POSTGRES_PASSWORD"
Assert-Condition ($productionComposeContent -match 'REELSHORT_DB_PASSWORD:\s*\$\{POSTGRES_PASSWORD:\?[^}]+\}') "Backend must require POSTGRES_PASSWORD"
Assert-Condition (-not $productionComposeContent.Contains('reelshort_dev')) "Production Compose must not contain the development database password"
Assert-Condition (-not ($nginxContent -match '(?ms)location\s+=\s+/api/internal\s*\{\s*return\s+404;\s*\}')) "Nginx must not reject the exact /api/internal path"
Assert-Condition (-not ($nginxContent -match '(?ms)location\s+\^~\s+/api/internal/\s*\{\s*return\s+404;\s*\}')) "Nginx must not reject /api/internal/ before the general /api/ proxy"
Assert-Condition ($nginxContent -match '(?ms)location\s+/api/\s*\{.*proxy_pass\s+http://backend:8080/api/;') "Nginx must proxy /api/ paths, including /api/internal, to backend"

& (Join-Path $repoRoot "infra\scripts\tests\backup-security-tests.ps1")

Write-Host "Infrastructure static verification passed."
