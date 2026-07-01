param(
    [string]$PowerShell = "powershell"
)

$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$verifyScript = Join-Path $repoRoot "scripts\verify-release-baseline.ps1"
$tmpRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("reelshort-release-test-" + [System.Guid]::NewGuid().ToString("N"))
$fakeBin = Join-Path $tmpRoot "bin"

function New-FailingCommand {
    param([string]$Name)

    $path = Join-Path $fakeBin "$Name.cmd"
    Set-Content -LiteralPath $path -Encoding ASCII -Value @"
@echo off
echo intentional $Name failure 1>&2
exit /b 23
"@
}

try {
    New-Item -ItemType Directory -Force -Path $fakeBin | Out-Null
    New-FailingCommand -Name "git"

    $previousPath = $env:PATH
    $env:PATH = "$fakeBin;$previousPath"

    & $PowerShell -ExecutionPolicy Bypass -File $verifyScript -SkipContentProvider -SkipBackend -SkipAdminWeb -SkipAndroid
    $exitCode = $LASTEXITCODE

    if ($exitCode -eq 0) {
        throw "Expected verify-release-baseline.ps1 to fail when git exits non-zero, but it returned 0."
    }

    Write-Host "verify-release-baseline detects native command failures."
} finally {
    $env:PATH = $previousPath
    Remove-Item -LiteralPath $tmpRoot -Recurse -Force -ErrorAction SilentlyContinue
}
