$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$backupScript = Join-Path $repoRoot "infra\scripts\backup.ps1"
$restoreScript = Join-Path $repoRoot "infra\scripts\restore.ps1"

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

Push-Location $repoRoot
try {
    git check-ignore -q "infra/backups/"
    Assert-Condition ($LASTEXITCODE -eq 0) "infra/backups/ must be ignored by Git"
}
finally {
    Pop-Location
}

$backupContent = Get-Content -LiteralPath $backupScript -Raw
$restoreContent = Get-Content -LiteralPath $restoreScript -Raw

foreach ($required in @("pg_dump", "-Fc", "docker compose", "-f `$composePath", "docker compose --env-file `$envPath -f `$composePath cp", "manifest.json")) {
    Assert-Condition ($backupContent.Contains($required)) "backup.ps1 must contain '$required'"
}

foreach ($required in @("pg_restore", "--clean", "--if-exists", "-f `$composePath", "docker compose --env-file `$envPath -f `$composePath cp", "ConfirmRestore")) {
    Assert-Condition ($restoreContent.Contains($required)) "restore.ps1 must contain '$required'"
}

Write-Host "Backup script verification passed."
