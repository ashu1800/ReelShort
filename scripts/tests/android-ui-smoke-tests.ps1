param(
    [string]$PowerShell = "powershell"
)

$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$smokeScript = Join-Path $repoRoot "scripts\android-ui-smoke.ps1"

& $PowerShell -ExecutionPolicy Bypass -File $smokeScript -SelfTest
if ($LASTEXITCODE -ne 0) {
    throw "android-ui-smoke.ps1 self-test failed with code $LASTEXITCODE."
}

Write-Host "android-ui-smoke.ps1 self-test passed."
