param(
    [string] $EnvFile = "infra/.env",
    [string] $OutputRoot = "infra/backups",
    [int] $RetentionDays = 14,
    [switch] $IncludeEncryptedConfig
)

$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$envPath = (Resolve-Path -LiteralPath (Join-Path $repoRoot $EnvFile)).Path
$composePath = (Resolve-Path -LiteralPath (Join-Path $repoRoot "infra/docker-compose.yml")).Path
$outputRootPath = Join-Path $repoRoot $OutputRoot
$timestamp = "{0}-{1}" -f (Get-Date -Format "yyyyMMdd-HHmmss-fff"), ([Guid]::NewGuid().ToString("N").Substring(0, 8))
$backupDir = Join-Path $outputRootPath $timestamp
$databaseDumpPath = Join-Path $backupDir "database.dump"
$configDir = Join-Path $backupDir "config"
$containerDumpPath = "/tmp/reelshort-$timestamp.dump"
$isWindowsHost = $env:OS -eq "Windows_NT"

function Set-OwnerOnlyAcl {
    param([string] $Path)

    if (-not $isWindowsHost) {
        return
    }
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent().Name
    $isDirectory = Test-Path -LiteralPath $Path -PathType Container
    $acl = New-Object Security.AccessControl.DirectorySecurity
    if (-not $isDirectory) {
        $acl = New-Object Security.AccessControl.FileSecurity
    }
    $acl.SetAccessRuleProtection($true, $false)
    if ($isDirectory) {
        $rule = New-Object Security.AccessControl.FileSystemAccessRule(
            $identity,
            [Security.AccessControl.FileSystemRights]::FullControl,
            ([Security.AccessControl.InheritanceFlags]::ContainerInherit -bor [Security.AccessControl.InheritanceFlags]::ObjectInherit),
            [Security.AccessControl.PropagationFlags]::None,
            [Security.AccessControl.AccessControlType]::Allow
        )
    }
    else {
        $rule = New-Object Security.AccessControl.FileSystemAccessRule(
            $identity,
            [Security.AccessControl.FileSystemRights]::FullControl,
            [Security.AccessControl.AccessControlType]::Allow
        )
    }
    $acl.AddAccessRule($rule)
    Set-Acl -LiteralPath $Path -AclObject $acl
}

if ($IncludeEncryptedConfig -and -not $isWindowsHost) {
    throw "Encrypted configuration backup requires Windows DPAPI CurrentUser. Database backup remains available without -IncludeEncryptedConfig."
}
if ($IncludeEncryptedConfig) {
    Add-Type -AssemblyName System.Security
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

New-Item -ItemType Directory -Force -Path $backupDir, $configDir | Out-Null
Set-OwnerOnlyAcl -Path $backupDir

Push-Location $repoRoot
try {
    Write-Host "Creating PostgreSQL backup at $databaseDumpPath"
    docker compose --env-file $envPath -f $composePath exec -T postgres pg_dump -U $postgresUser -d $postgresDb -Fc -f $containerDumpPath
    docker compose --env-file $envPath -f $composePath cp "postgres:$containerDumpPath" $databaseDumpPath

    if ($IncludeEncryptedConfig) {
        $plainBytes = [IO.File]::ReadAllBytes($envPath)
        try {
            $encryptedBytes = [Security.Cryptography.ProtectedData]::Protect(
                $plainBytes,
                $null,
                [Security.Cryptography.DataProtectionScope]::CurrentUser
            )
            $encryptedConfigPath = Join-Path $configDir "environment.dpapi"
            [IO.File]::WriteAllBytes($encryptedConfigPath, $encryptedBytes)
            Set-OwnerOnlyAcl -Path $encryptedConfigPath
        }
        finally {
            if ($plainBytes) { [Array]::Clear($plainBytes, 0, $plainBytes.Length) }
        }
    }
    Copy-Item -LiteralPath (Join-Path $repoRoot "infra/docker-compose.yml") -Destination $configDir -Force
    Copy-Item -LiteralPath (Join-Path $repoRoot "docs/deploy/README.md") -Destination (Join-Path $configDir "deploy-README.md") -Force
    Copy-Item -LiteralPath (Join-Path $repoRoot "docs/deploy/backup-restore.md") -Destination (Join-Path $configDir "backup-restore.md") -Force

    $migrationSnapshot = Join-Path $configDir "db/migration"
    New-Item -ItemType Directory -Force -Path $migrationSnapshot | Out-Null
    Copy-Item -LiteralPath (Join-Path $repoRoot "backend/src/main/resources/db/migration") -Destination (Join-Path $configDir "db") -Recurse -Force
    $manifest = [ordered]@{
        createdAt = (Get-Date).ToString("o")
        postgresDatabase = $postgresDb
        postgresUser = $postgresUser
        databaseDump = "database.dump"
        composeEnvFile = $EnvFile
        retentionDays = $RetentionDays
        encryptedEnvironmentIncluded = [bool] $IncludeEncryptedConfig
        files = @()
        restoreHint = "Stop backend/nginx writes, then run infra/scripts/restore.ps1 -BackupDir $backupDir -ConfirmRestore"
    }
    $manifest | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $backupDir "manifest.json") -Encoding UTF8

    $files = Get-ChildItem -LiteralPath $backupDir -Recurse -File |
        ForEach-Object { $_.FullName.Substring($backupDir.Length + 1).Replace("\", "/") }
    $manifest.files = $files
    $manifest | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $backupDir "manifest.json") -Encoding UTF8

    if ($RetentionDays -gt 0 -and (Test-Path -LiteralPath $outputRootPath)) {
        $cutoff = (Get-Date).AddDays(-$RetentionDays)
        Get-ChildItem -LiteralPath $outputRootPath -Directory |
            Where-Object { $_.LastWriteTime -lt $cutoff } |
            Remove-Item -Recurse -Force
    }

    Write-Host "Backup completed: $backupDir"
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
