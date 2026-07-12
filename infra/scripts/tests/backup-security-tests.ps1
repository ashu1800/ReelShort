$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path
$testId = [Guid]::NewGuid().ToString("N")
$testRoot = Join-Path $repoRoot "infra\backups-test-$testId"
$backupOutputRoot = Join-Path $testRoot "backups"
$fakeBin = Join-Path $testRoot "bin"
$envFile = Join-Path $testRoot "source.env"
$restoreEnvFile = Join-Path $testRoot "restored.env"
$relativeBackupOutputRoot = $backupOutputRoot.Substring($repoRoot.Length + 1)
$relativeEnvFile = $envFile.Substring($repoRoot.Length + 1)
$relativeRestoreEnvFile = $restoreEnvFile.Substring($repoRoot.Length + 1)
$safeRestoreRoot = Join-Path $repoRoot "infra\restored-config"
$safeRestoreName = "test-$testId.env"
$safeRestorePath = Join-Path $safeRestoreRoot $safeRestoreName
$capturedFailureOutput = ""
$secret = "test-secret-$testId"

function Assert-Condition {
    param([bool] $Condition, [string] $Message)
    if (-not $Condition) { throw $Message }
}

function Assert-Throws {
    param([scriptblock] $Action, [string] $Message)
    try {
        $output = & $Action *>&1
        $script:capturedFailureOutput += ($output | Out-String)
    }
    catch {
        $script:capturedFailureOutput += ($_ | Out-String)
        return
    }
    throw $Message
}

try {
    New-Item -ItemType Directory -Force -Path $fakeBin | Out-Null
    Set-Content -LiteralPath $envFile -Value @(
        "POSTGRES_DB=reelshort_test"
        "POSTGRES_USER=reelshort_test"
        "REELSHORT_PAYMENT_CALLBACK_SECRET=$secret"
    )

    $fakeDocker = @'
@echo off
setlocal EnableDelayedExpansion
if not "%REELSHORT_TEST_DOCKER_LOG%"=="" echo %*>>"%REELSHORT_TEST_DOCKER_LOG%"
echo %*| findstr /c:"pg_restore" >nul
if not errorlevel 1 if "%REELSHORT_TEST_FAIL_PG_RESTORE%"=="1" exit /b 42
set prev=
for %%A in (%*) do (
  if "!prev!"=="cp" (
    set source=%%~A
    set prev=cp-source
  ) else if "!prev!"=="cp-source" (
    set destination=%%~A
    if not "!destination:~0,9!"=="postgres:" type nul > "!destination!"
    exit /b 0
  ) else if "%%~A"=="cp" (
    set prev=cp
  )
)
exit /b 0
'@
    Set-Content -LiteralPath (Join-Path $fakeBin "docker.cmd") -Value $fakeDocker -Encoding ASCII
    $originalPath = $env:PATH
    $env:PATH = "$fakeBin;$originalPath"
    $dockerLog = Join-Path $testRoot "docker.log"
    $env:REELSHORT_TEST_DOCKER_LOG = $dockerLog

    $defaultOutput = (& (Join-Path $repoRoot "infra\scripts\backup.ps1") -EnvFile $relativeEnvFile -OutputRoot $relativeBackupOutputRoot -RetentionDays 0 *>&1 | Out-String)
    $defaultBackup = Get-ChildItem -LiteralPath $backupOutputRoot -Directory | Where-Object Name -Match '^\d{8}-\d{6}-\d{3}-[0-9a-f]{8}$' | Select-Object -First 1
    Assert-Condition ($null -ne $defaultBackup) "Default backup was not created"
    Assert-Condition (-not (Test-Path (Join-Path $defaultBackup.FullName "config\.env"))) "Default backup must not copy plaintext .env"
    Assert-Condition (-not (Test-Path (Join-Path $defaultBackup.FullName "config\environment.dpapi"))) "Default backup must not include environment configuration"

    & (Join-Path $repoRoot "infra\scripts\backup.ps1") -EnvFile $relativeEnvFile -OutputRoot $relativeBackupOutputRoot -RetentionDays 0
    $rapidBackups = @(Get-ChildItem -LiteralPath $backupOutputRoot -Directory | Where-Object Name -Match '^\d{8}-\d{6}-\d{3}-[0-9a-f]{8}$')
    Assert-Condition ($rapidBackups.Count -ge 2) "Rapid backups must each create a unique directory"
    Assert-Condition (($rapidBackups.Name | Select-Object -Unique).Count -eq $rapidBackups.Count) "Rapid backups must not reuse a timestamp directory"

    $encryptedOutput = (& (Join-Path $repoRoot "infra\scripts\backup.ps1") -EnvFile $relativeEnvFile -OutputRoot $relativeBackupOutputRoot -RetentionDays 0 -IncludeEncryptedConfig *>&1 | Out-String)
    $encryptedBackup = Get-ChildItem -LiteralPath $backupOutputRoot -Directory | Where-Object Name -Match '^\d{8}-\d{6}-\d{3}-[0-9a-f]{8}$' | Sort-Object Name -Descending | Select-Object -First 1
    $encryptedConfig = Join-Path $encryptedBackup.FullName "config\environment.dpapi"
    Assert-Condition (Test-Path $encryptedConfig) "Explicit configuration backup must create environment.dpapi"
    Assert-Condition (-not ([Text.Encoding]::UTF8.GetString([IO.File]::ReadAllBytes($encryptedConfig)).Contains($secret))) "Encrypted configuration must not contain plaintext secrets"
    Assert-Condition (-not (Test-Path (Join-Path $encryptedBackup.FullName "config\.env"))) "Encrypted backup must not leave a plaintext .env"

    Assert-Throws { & (Join-Path $repoRoot "infra\scripts\restore.ps1") -BackupDir $encryptedBackup.FullName -EnvFile $relativeEnvFile -ConfirmRestore -RestoreEncryptedConfig } "Config restore without a path must fail"
    Assert-Throws { & (Join-Path $repoRoot "infra\scripts\restore.ps1") -BackupDir $encryptedBackup.FullName -EnvFile $relativeEnvFile -ConfirmRestore -ConfigRestorePath $safeRestoreName } "Config restore path without the restore switch must fail"
    Assert-Throws { & (Join-Path $repoRoot "infra\scripts\restore.ps1") -BackupDir $encryptedBackup.FullName -EnvFile $relativeEnvFile -ConfirmRestore -RestoreEncryptedConfig -ConfigRestorePath $restoreEnvFile } "Absolute config restore paths must fail"
    Assert-Throws { & (Join-Path $repoRoot "infra\scripts\restore.ps1") -BackupDir $encryptedBackup.FullName -EnvFile $relativeEnvFile -ConfirmRestore -RestoreEncryptedConfig -ConfigRestorePath "..\escaped.env" } "Config restore path traversal must fail"
    Assert-Throws { & (Join-Path $repoRoot "infra\scripts\restore.ps1") -BackupDir $encryptedBackup.FullName -EnvFile $relativeEnvFile -ConfirmRestore -RestoreEncryptedConfig -ConfigRestorePath "nested\..\allowed.env" } "Config restore must reject traversal segments before normalization"
    Assert-Condition (-not ((Get-Content -LiteralPath $dockerLog -Raw).Contains("pg_restore"))) "Configuration validation failure must occur before pg_restore"

    New-Item -ItemType Directory -Force -Path $safeRestoreRoot | Out-Null
    $reparseTarget = Join-Path $testRoot "reparse-target"
    $reparseParent = Join-Path $safeRestoreRoot "linked-$testId"
    New-Item -ItemType Directory -Force -Path $reparseTarget | Out-Null
    New-Item -ItemType Junction -Path $reparseParent -Target $reparseTarget | Out-Null
    Assert-Throws { & (Join-Path $repoRoot "infra\scripts\restore.ps1") -BackupDir $encryptedBackup.FullName -EnvFile $relativeEnvFile -ConfirmRestore -RestoreEncryptedConfig -ConfigRestorePath "linked-$testId\escaped.env" } "Reparse points in the restore parent chain must fail"
    Remove-Item -LiteralPath $reparseParent -Force

    $mockRestoreScript = Join-Path $repoRoot "infra\scripts\restore-acl-failure-test.ps1"
    $restoreSource = Get-Content -LiteralPath (Join-Path $repoRoot "infra\scripts\restore.ps1") -Raw
    $mockSource = $restoreSource.Replace('Set-Acl -LiteralPath $path -AclObject (New-OwnerOnlySecurity -Directory $true)', 'throw "Mock ACL failure"')
    Set-Content -LiteralPath $mockRestoreScript -Value $mockSource
    Clear-Content -LiteralPath $dockerLog
    Assert-Throws { & $mockRestoreScript -BackupDir $encryptedBackup.FullName -EnvFile $relativeEnvFile -ConfirmRestore -RestoreEncryptedConfig -ConfigRestorePath $safeRestoreName } "ACL failure must fail configuration preparation"
    Assert-Condition (-not (Test-Path $safeRestorePath)) "ACL failure must not leave a configuration file"
    $aclFailureLog = [string](Get-Content -LiteralPath $dockerLog -Raw)
    Assert-Condition (-not ($aclFailureLog -match "pg_restore")) "ACL failure must occur before pg_restore"
    Remove-Item -LiteralPath $mockRestoreScript -Force

    $windowsOs = $env:OS
    try {
        $env:OS = "NonWindows"
        Assert-Throws { & (Join-Path $repoRoot "infra\scripts\backup.ps1") -EnvFile $relativeEnvFile -OutputRoot $relativeBackupOutputRoot -RetentionDays 0 -IncludeEncryptedConfig } "Non-Windows encrypted configuration backup must fail explicitly"
        & (Join-Path $repoRoot "infra\scripts\backup.ps1") -EnvFile $relativeEnvFile -OutputRoot $relativeBackupOutputRoot -RetentionDays 0
        $nonWindowsBackup = Get-ChildItem -LiteralPath $backupOutputRoot -Directory | Where-Object Name -Match '^\d{8}-\d{6}-\d{3}-[0-9a-f]{8}$' | Sort-Object Name -Descending | Select-Object -First 1
        Assert-Condition (-not (Test-Path (Join-Path $nonWindowsBackup.FullName "config\environment.dpapi"))) "Non-Windows default backup must not create encrypted configuration"
    }
    finally {
        $env:OS = $windowsOs
    }

    Set-Content -LiteralPath $safeRestorePath -Value "existing-content"
    Assert-Throws { & (Join-Path $repoRoot "infra\scripts\restore.ps1") -BackupDir $encryptedBackup.FullName -EnvFile $relativeEnvFile -ConfirmRestore -RestoreEncryptedConfig -ConfigRestorePath $safeRestoreName } "Existing config restore targets must fail"
    Assert-Condition ((Get-Content -LiteralPath $safeRestorePath -Raw).Trim() -eq "existing-content") "Failed restore must not modify an existing target"
    Remove-Item -LiteralPath $safeRestorePath -Force

    $raceRestoreScript = Join-Path $repoRoot "infra\scripts\restore-create-race-test.ps1"
    $restoreSource = Get-Content -LiteralPath (Join-Path $repoRoot "infra\scripts\restore.ps1") -Raw
    $raceSource = $restoreSource.Replace('$preparedConfigFile = Write-NewSecureConfigFile -Path $configTargetPath -Bytes $plainConfigBytes', 'Set-Content -LiteralPath $configTargetPath -Value "concurrent-content"; $preparedConfigFile = Write-NewSecureConfigFile -Path $configTargetPath -Bytes $plainConfigBytes')
    Set-Content -LiteralPath $raceRestoreScript -Value $raceSource
    Assert-Throws { & $raceRestoreScript -BackupDir $encryptedBackup.FullName -EnvFile $relativeEnvFile -ConfirmRestore -RestoreEncryptedConfig -ConfigRestorePath $safeRestoreName } "Concurrent target creation before CreateNew must fail"
    Assert-Condition ((Get-Content -LiteralPath $safeRestorePath -Raw).Trim() -eq "concurrent-content") "CreateNew race failure must preserve the concurrent owner's file"
    Remove-Item -LiteralPath $safeRestorePath -Force
    Remove-Item -LiteralPath $raceRestoreScript -Force

    $env:REELSHORT_TEST_FAIL_PG_RESTORE = "1"
    Assert-Throws { & (Join-Path $repoRoot "infra\scripts\restore.ps1") -BackupDir $encryptedBackup.FullName -EnvFile $relativeEnvFile -ConfirmRestore -RestoreEncryptedConfig -ConfigRestorePath $safeRestoreName } "Database restore failure must fail the operation"
    $env:REELSHORT_TEST_FAIL_PG_RESTORE = $null
    Assert-Condition (-not (Test-Path $safeRestorePath)) "Database restore failure must remove the newly created configuration"

    $restoreOutput = (& (Join-Path $repoRoot "infra\scripts\restore.ps1") -BackupDir $encryptedBackup.FullName -EnvFile $relativeEnvFile -ConfirmRestore -RestoreEncryptedConfig -ConfigRestorePath $safeRestoreName *>&1 | Out-String)
    Assert-Condition (Test-Path $safeRestorePath) "Explicit configuration restore must create a file under infra/restored-config"
    Assert-Condition ((Get-Content -LiteralPath $safeRestorePath -Raw).Contains($secret)) "Restored environment file must contain the original configuration"
    Remove-Item -LiteralPath $safeRestorePath -Force

    [IO.File]::WriteAllBytes($encryptedConfig, [byte[]](1, 2, 3, 4))
    Assert-Throws { & (Join-Path $repoRoot "infra\scripts\restore.ps1") -BackupDir $encryptedBackup.FullName -EnvFile $relativeEnvFile -ConfirmRestore -RestoreEncryptedConfig -ConfigRestorePath $safeRestoreName } "Invalid DPAPI data must fail"
    Assert-Condition (-not (Test-Path $safeRestorePath)) "Invalid DPAPI data must not leave a restore file"

    $allOutput = $defaultOutput + $encryptedOutput + $restoreOutput + $capturedFailureOutput
    Assert-Condition (-not $allOutput.Contains($secret)) "Backup and restore output must not disclose secrets"
    $backupBytes = @($defaultBackup, $encryptedBackup) | ForEach-Object {
        Get-ChildItem -LiteralPath $_.FullName -Recurse -File | ForEach-Object { [IO.File]::ReadAllBytes($_.FullName) }
    }
    foreach ($bytes in $backupBytes) {
        Assert-Condition (-not ([Text.Encoding]::UTF8.GetString($bytes).Contains($secret))) "Backup files must not contain plaintext secrets"
    }

    Write-Host "Backup security behavior tests passed."
}
finally {
    if ($originalPath) { $env:PATH = $originalPath }
    Get-ChildItem -LiteralPath (Join-Path $repoRoot "infra\scripts") -Filter "restore-acl-failure-test.ps1" -ErrorAction SilentlyContinue | Remove-Item -Force
    Get-ChildItem -LiteralPath (Join-Path $repoRoot "infra\scripts") -Filter "restore-create-race-test.ps1" -ErrorAction SilentlyContinue | Remove-Item -Force
    $env:REELSHORT_TEST_FAIL_PG_RESTORE = $null
    $env:REELSHORT_TEST_DOCKER_LOG = $null
    if (Test-Path -LiteralPath $safeRestorePath) { Remove-Item -LiteralPath $safeRestorePath -Force }
    if (Test-Path -LiteralPath $testRoot) { Remove-Item -LiteralPath $testRoot -Recurse -Force }
}
