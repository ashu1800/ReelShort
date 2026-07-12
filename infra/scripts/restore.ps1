param(
    [Parameter(Mandatory = $true)]
    [string] $BackupDir,
    [string] $EnvFile = "infra/.env",
    [switch] $ConfirmRestore,
    [switch] $RestoreEncryptedConfig,
    [string] $ConfigRestorePath
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
$encryptedConfigPath = Join-Path $backupPath "config/environment.dpapi"
$isWindowsHost = $env:OS -eq "Windows_NT"
$configRestoreRoot = [IO.Path]::GetFullPath((Join-Path $repoRoot "infra/restored-config"))
$plainConfigBytes = $null
$configTargetPath = $null

function New-OwnerOnlySecurity {
    param([bool] $Directory)
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent().User
    $acl = if ($Directory) { New-Object Security.AccessControl.DirectorySecurity } else { New-Object Security.AccessControl.FileSecurity }
    $acl.SetOwner($identity)
    $acl.SetAccessRuleProtection($true, $false)
    $inheritance = if ($Directory) { [Security.AccessControl.InheritanceFlags]::ContainerInherit -bor [Security.AccessControl.InheritanceFlags]::ObjectInherit } else { [Security.AccessControl.InheritanceFlags]::None }
    $rule = New-Object Security.AccessControl.FileSystemAccessRule($identity, [Security.AccessControl.FileSystemRights]::FullControl, $inheritance, [Security.AccessControl.PropagationFlags]::None, [Security.AccessControl.AccessControlType]::Allow)
    $acl.AddAccessRule($rule)
    return $acl
}

function Initialize-SecureDirectoryChain {
    param([string] $Root, [string] $TargetParent)
    $paths = @($Root)
    if ($TargetParent.Length -gt $Root.Length) {
        $current = $Root
        foreach ($segment in ($TargetParent.Substring($Root.Length).TrimStart('\', '/') -split '[\\/]+')) {
            $current = Join-Path $current $segment
            $paths += $current
        }
    }
    foreach ($path in $paths) {
        if (Test-Path -LiteralPath $path) {
            $item = Get-Item -LiteralPath $path -Force
            if (-not $item.PSIsContainer -or ($item.Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw "Configuration restore directory chain is unsafe." }
        } else {
            [IO.Directory]::CreateDirectory($path) | Out-Null
        }
        Set-Acl -LiteralPath $path -AclObject (New-OwnerOnlySecurity -Directory $true)
        $item = Get-Item -LiteralPath $path -Force
        if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw "Configuration restore directory chain is unsafe." }
    }
}

function Write-NewSecureConfigFile {
    param([string] $Path, [byte[]] $Bytes)
    $parent = Split-Path -Parent $Path
    Initialize-SecureDirectoryChain -Root $configRestoreRoot -TargetParent $parent
    Assert-NoReparsePointInExistingParents -Root $configRestoreRoot -Target $Path
    $stream = $null
    $ownedCreated = $false
    try {
        $stream = [IO.FileStream]::new($Path, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
        $ownedCreated = $true
        Assert-NoReparsePointInExistingParents -Root $configRestoreRoot -Target $Path
        $currentSid = [Security.Principal.WindowsIdentity]::GetCurrent().User.Value
        Set-Acl -LiteralPath $Path -AclObject (New-OwnerOnlySecurity -Directory $false)
        $fileAcl = Get-Acl -LiteralPath $Path
        if (-not $fileAcl.AreAccessRulesProtected) { throw "Configuration file ACL inheritance must be disabled." }
        $accessSids = @($fileAcl.Access | ForEach-Object { $_.IdentityReference.Translate([Security.Principal.SecurityIdentifier]).Value } | Select-Object -Unique)
        if ($accessSids.Count -ne 1 -or $accessSids[0] -ne $currentSid) { throw "Configuration file ACL must grant access only to the current user SID." }
        $stream.Write($Bytes, 0, $Bytes.Length)
        $stream.Flush($true)
        $stream.Dispose()
        $stream = $null
        $item = Get-Item -LiteralPath $Path -Force
        $hash = (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash
        return [pscustomobject]@{ Path = $Path; Length = $item.Length; LastWriteTimeUtc = $item.LastWriteTimeUtc; Hash = $hash; Owned = $true }
    }
    catch {
        if ($stream) { $stream.Dispose() }
        if ($ownedCreated -and (Test-Path -LiteralPath $Path -PathType Leaf)) { Remove-Item -LiteralPath $Path -Force }
        throw
    }
}

function Remove-OwnedConfigFile {
    param($OwnedFile)
    if (-not $OwnedFile -or -not $OwnedFile.Owned) { return }
    if (-not (Test-Path -LiteralPath $OwnedFile.Path -PathType Leaf)) { return }
    $item = Get-Item -LiteralPath $OwnedFile.Path -Force
    if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -or $item.Length -ne $OwnedFile.Length -or $item.LastWriteTimeUtc -ne $OwnedFile.LastWriteTimeUtc) {
        throw "New configuration could not be cleaned up because its file identity changed."
    }
    if ((Get-FileHash -LiteralPath $OwnedFile.Path -Algorithm SHA256).Hash -ne $OwnedFile.Hash) {
        throw "New configuration could not be cleaned up because its content identity changed."
    }
    Remove-Item -LiteralPath $OwnedFile.Path -Force
}

function Assert-NoReparsePointInExistingParents {
    param([string] $Root, [string] $Target)

    $current = [IO.DirectoryInfo]::new($Root)
    if ($current.Exists -and ($current.Attributes -band [IO.FileAttributes]::ReparsePoint)) {
        throw "Configuration restore root must not be a reparse point."
    }
    $targetParent = Split-Path -Parent $Target
    if ($targetParent.Length -le $Root.Length) { return }
    $relativeParent = $targetParent.Substring($Root.Length).TrimStart('\', '/')
    foreach ($segment in ($relativeParent -split '[\\/]+')) {
        $current = [IO.DirectoryInfo]::new((Join-Path $current.FullName $segment))
        if ($current.Exists -and ($current.Attributes -band [IO.FileAttributes]::ReparsePoint)) {
            throw "Configuration restore parent directories must not contain reparse points."
        }
    }
}

if (-not (Test-Path -LiteralPath $databaseDumpPath)) {
    throw "Missing database.dump in $backupPath"
}
if (-not (Test-Path -LiteralPath $manifestPath)) {
    throw "Missing manifest.json in $backupPath"
}
if (-not $RestoreEncryptedConfig -and -not [string]::IsNullOrWhiteSpace($ConfigRestorePath)) {
    throw "-ConfigRestorePath requires -RestoreEncryptedConfig."
}
if ($RestoreEncryptedConfig) {
    if (-not $isWindowsHost) {
        throw "Encrypted configuration restore requires Windows DPAPI CurrentUser."
    }
    if ([string]::IsNullOrWhiteSpace($ConfigRestorePath)) {
        throw "-ConfigRestorePath is required with -RestoreEncryptedConfig."
    }
    if ([IO.Path]::IsPathRooted($ConfigRestorePath)) {
        throw "-ConfigRestorePath must be relative to infra/restored-config."
    }
    foreach ($segment in ($ConfigRestorePath -split '[\\/]')) {
        if ($segment -eq "..") { throw "-ConfigRestorePath must not contain '..' path segments." }
    }
    if (-not (Test-Path -LiteralPath $encryptedConfigPath)) {
        throw "Missing config/environment.dpapi in $backupPath"
    }
    Add-Type -AssemblyName System.Security
    $configTargetPath = [IO.Path]::GetFullPath((Join-Path $configRestoreRoot $ConfigRestorePath))
    $rootPrefix = $configRestoreRoot.TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
    if (-not $configTargetPath.StartsWith($rootPrefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw "-ConfigRestorePath must remain within infra/restored-config."
    }
    Assert-NoReparsePointInExistingParents -Root $configRestoreRoot -Target $configTargetPath
    if (Test-Path -LiteralPath $configTargetPath) {
        throw "Configuration restore target already exists: $configTargetPath"
    }
    $encryptedBytes = [IO.File]::ReadAllBytes($encryptedConfigPath)
    $plainConfigBytes = [Security.Cryptography.ProtectedData]::Unprotect(
        $encryptedBytes,
        $null,
        [Security.Cryptography.DataProtectionScope]::CurrentUser
    )
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
$preparedConfigFile = $null

if ($RestoreEncryptedConfig) {
    try {
        $preparedConfigFile = Write-NewSecureConfigFile -Path $configTargetPath -Bytes $plainConfigBytes
    }
    catch {
        throw
    }
    finally {
        if ($plainConfigBytes) { [Array]::Clear($plainConfigBytes, 0, $plainConfigBytes.Length) }
    }
}

Write-Host "Restoring PostgreSQL database '$postgresDb' from $databaseDumpPath"
Write-Host "Make sure backend and nginx are stopped before continuing."

Push-Location $repoRoot
try {
    docker compose --env-file $envPath -f $composePath cp $databaseDumpPath "postgres:$containerDumpPath"
    if ($LASTEXITCODE -ne 0) { throw "docker compose cp failed with exit code $LASTEXITCODE" }
    docker compose --env-file $envPath -f $composePath exec -T postgres pg_restore --clean --if-exists --no-owner -U $postgresUser -d $postgresDb $containerDumpPath
    if ($LASTEXITCODE -ne 0) { throw "pg_restore failed with exit code $LASTEXITCODE" }
    if ($RestoreEncryptedConfig) {
        Write-Host "Encrypted configuration restored under infra/restored-config. Rotate all restored secrets before returning services to production."
    }
    Write-Host "Restore completed from $backupPath"
}
catch {
    Remove-OwnedConfigFile -OwnedFile $preparedConfigFile
    throw "Restore failed; any newly restored configuration was removed. Database tools may have applied partial changes: $($_.Exception.Message)"
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
