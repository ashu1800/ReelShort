param(
    [string]$PowerShell = "powershell"
)

$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$reviewScript = Join-Path $repoRoot "scripts\android-ui-review.ps1"

& $PowerShell -ExecutionPolicy Bypass -File $reviewScript -SelfTest
if ($LASTEXITCODE -ne 0) {
    throw "android-ui-review.ps1 self-test failed with code $LASTEXITCODE."
}

Write-Host "android-ui-review.ps1 self-test passed."
