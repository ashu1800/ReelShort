param(
    [Parameter(Mandatory = $true)]
    [string] $BackupDir,
    [string] $EnvFile = "infra/.env",
    [switch] $ConfirmRestore
)

$ErrorActionPreference = "Stop"

if (-not $ConfirmRestore) {
    throw "Restore is destructive. Re-run with -ConfirmRestore after stopping backend/nginx writes."
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$backupPath = (Resolve-Path -LiteralPath $BackupDir).Path
$envPath = (Resolve-Path -LiteralPath (Join-Path $repoRoot $EnvFile)).Path
$composePath = (Resolve-Path -LiteralPath (Join-Path $repoRoot "infra/docker-compose.yml")).Path
$databaseDumpPath = Join-Path $backupPath "database.dump"
$manifestPath = Join-Path $backupPath "manifest.json"
$containerDumpPath = "/tmp/reelshort-restore-$(Get-Date -Format 'yyyyMMdd-HHmmss').dump"

if (-not (Test-Path -LiteralPath $databaseDumpPath)) {
    throw "Missing database.dump in $backupPath"
}
if (-not (Test-Path -LiteralPath $manifestPath)) {
    throw "Missing manifest.json in $backupPath"
}

function Read-EnvValue {
    param(
        [string] $Path,
        [string] $Name,
        [string] $DefaultValue
    )

    $line = Get-Content -LiteralPath $Path |
        Where-Object { $_ -match "^\s*$([regex]::Escape($Name))=" } |
        Select-Object -First 1
    if (-not $line) {
        return $DefaultValue
    }
    return ($line -split "=", 2)[1].Trim()
}

$postgresDb = Read-EnvValue -Path $envPath -Name "POSTGRES_DB" -DefaultValue "reelshort"
$postgresUser = Read-EnvValue -Path $envPath -Name "POSTGRES_USER" -DefaultValue "reelshort"

Write-Host "Restoring PostgreSQL database '$postgresDb' from $databaseDumpPath"
Write-Host "Make sure backend and nginx are stopped before continuing."

Push-Location $repoRoot
try {
    docker compose --env-file $envPath -f $composePath cp $databaseDumpPath "postgres:$containerDumpPath"
    docker compose --env-file $envPath -f $composePath exec -T postgres pg_restore --clean --if-exists --no-owner -U $postgresUser -d $postgresDb $containerDumpPath
    Write-Host "Restore completed from $backupPath"
}
finally {
    try {
        docker compose --env-file $envPath -f $composePath exec -T postgres rm -f $containerDumpPath
    }
    catch {
        Write-Warning "Failed to remove temporary container dump '$containerDumpPath': $($_.Exception.Message)"
    }
    Pop-Location
}
