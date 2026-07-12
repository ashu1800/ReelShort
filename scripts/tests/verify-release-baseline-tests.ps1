param(
    [string]$PowerShell = "powershell"
)

$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$verifyScript = Join-Path $repoRoot "scripts\verify-release-baseline.ps1"
$tmpRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("reelshort-release-test-" + [System.Guid]::NewGuid().ToString("N"))

function Invoke-Native {
    param([string]$FilePath, [string[]]$Arguments = @())

    & $FilePath @Arguments | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "$FilePath exited with code $LASTEXITCODE"
    }
}

function New-TestRepository {
    param([string]$Name)

    $path = Join-Path $tmpRoot $Name
    New-Item -ItemType Directory -Force -Path (Join-Path $path "scripts") | Out-Null
    Copy-Item -LiteralPath $verifyScript -Destination (Join-Path $path "scripts\verify-release-baseline.ps1")
    Push-Location $path
    try {
        Invoke-Native "git" @("init", "--quiet")
        Invoke-Native "git" @("config", "user.email", "release-test@example.invalid")
        Invoke-Native "git" @("config", "user.name", "Release Test")
        Set-Content -LiteralPath "tracked.txt" -Encoding ASCII -Value "clean"
        Invoke-Native "git" @("add", ".")
        Invoke-Native "git" @("commit", "--quiet", "-m", "initial")
    } finally {
        Pop-Location
    }
    return $path
}

function Invoke-Verification {
    param([string]$Repository, [string[]]$Arguments = @())

    $script = Join-Path $Repository "scripts\verify-release-baseline.ps1"
    $previousErrorAction = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        & $PowerShell -ExecutionPolicy Bypass -File $script `
            -SkipContentProvider -SkipBackend -SkipAdminWeb -SkipAndroid @Arguments *> $null
        return $LASTEXITCODE
    } finally { $ErrorActionPreference = $previousErrorAction }
}

function Invoke-VerificationWithOutput {
    param([string]$Repository, [string[]]$Arguments = @())

    $script = Join-Path $Repository "scripts\verify-release-baseline.ps1"
    $outputPath = Join-Path $tmpRoot "verification-output.txt"
    $previousErrorAction = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        & $PowerShell -ExecutionPolicy Bypass -File $script `
            -SkipContentProvider -SkipBackend -SkipAdminWeb -SkipAndroid @Arguments *> $outputPath
        $exitCode = $LASTEXITCODE
    } finally { $ErrorActionPreference = $previousErrorAction }
    [pscustomobject]@{ ExitCode = $exitCode; Output = Get-Content -Raw -LiteralPath $outputPath }
}

function Assert-ExitCode {
    param([string]$Name, [int]$Expected, [int]$Actual)

    if ($Actual -ne $Expected) {
        throw "$Name expected exit code $Expected, got $Actual."
    }
}

try {
    New-Item -ItemType Directory -Force -Path $tmpRoot | Out-Null

    $cleanRepo = New-TestRepository "clean"
    Assert-ExitCode "clean repository" 0 (Invoke-Verification $cleanRepo)

    $stagedRepo = New-TestRepository "staged-whitespace"
    Set-Content -LiteralPath (Join-Path $stagedRepo "staged.txt") -NoNewline -Encoding ASCII -Value "trailing whitespace   `n"
    Push-Location $stagedRepo
    try { Invoke-Native "git" @("add", "staged.txt") } finally { Pop-Location }
    Assert-ExitCode "staged whitespace" 1 (Invoke-Verification $stagedRepo)

    $unstagedRepo = New-TestRepository "unstaged-whitespace"
    Set-Content -LiteralPath (Join-Path $unstagedRepo "unstaged.txt") -Encoding ASCII -Value "clean"
    Push-Location $unstagedRepo
    try {
        Invoke-Native "git" @("add", "unstaged.txt")
        Invoke-Native "git" @("commit", "--quiet", "-m", "track unstaged fixture")
    } finally { Pop-Location }
    Set-Content -LiteralPath (Join-Path $unstagedRepo "unstaged.txt") -NoNewline -Encoding ASCII -Value "trailing whitespace   `n"
    Assert-ExitCode "unstaged whitespace" 1 (Invoke-Verification $unstagedRepo)

    $multiFailureRepo = New-TestRepository "multiple-failures"
    Set-Content -LiteralPath (Join-Path $multiFailureRepo "bad.txt") -Encoding ASCII -Value "clean"
    Push-Location $multiFailureRepo
    try {
        Invoke-Native "git" @("add", "bad.txt")
        Invoke-Native "git" @("commit", "--quiet", "-m", "track whitespace fixture")
    } finally { Pop-Location }
    Set-Content -LiteralPath (Join-Path $multiFailureRepo "bad.txt") -NoNewline -Encoding ASCII -Value "trailing whitespace   `n"
    Set-Content -LiteralPath (Join-Path $multiFailureRepo "generated.txt") -Encoding ASCII -Value "generated"
    $multiFailure = Invoke-VerificationWithOutput $multiFailureRepo
    Assert-ExitCode "multiple failures" 1 $multiFailure.ExitCode
    foreach ($stepName in @("FAILED: git working tree diff --check", "FAILED: git untracked files", "- git working tree diff --check:", "- git untracked files:")) {
        if ($multiFailure.Output -notmatch [regex]::Escape($stepName)) {
            throw "Multiple failure output did not contain '$stepName'."
        }
    }

    $untrackedRepo = New-TestRepository "untracked"
    Set-Content -LiteralPath (Join-Path $untrackedRepo "generated.txt") -Encoding ASCII -Value "generated"
    Assert-ExitCode "untracked file" 1 (Invoke-Verification $untrackedRepo)
    Assert-ExitCode "explicit untracked allowance" 0 (Invoke-Verification $untrackedRepo @("-AllowUntrackedFiles"))

    $ignoredRepo = New-TestRepository "ignored"
    Set-Content -LiteralPath (Join-Path $ignoredRepo ".gitignore") -Encoding ASCII -Value "generated.txt"
    Push-Location $ignoredRepo
    try {
        Invoke-Native "git" @("add", ".gitignore")
        Invoke-Native "git" @("commit", "--quiet", "-m", "ignore generated output")
    } finally { Pop-Location }
    Set-Content -LiteralPath (Join-Path $ignoredRepo "generated.txt") -Encoding ASCII -Value "generated"
    Assert-ExitCode "ignored file" 0 (Invoke-Verification $ignoredRepo)

    Assert-ExitCode "SkipDiffCheck bypasses git integrity checks" 0 (Invoke-Verification $untrackedRepo @("-SkipDiffCheck"))

    $fakeRepo = New-TestRepository "fake-git"
    $fakeBin = Join-Path $tmpRoot "fake-bin"
    New-Item -ItemType Directory -Force -Path $fakeBin | Out-Null
    $realGit = (Get-Command git.exe).Source
    Set-Content -LiteralPath (Join-Path $fakeBin "git.cmd") -Encoding ASCII -Value @"
@echo off
echo %* | findstr /c:"ls-files" >nul
if not errorlevel 1 exit /b 23
"$realGit" %*
exit /b %ERRORLEVEL%
"@
    $oldPath = $env:PATH
    try {
        $env:PATH = "$fakeBin;$oldPath"
        $lsFilesFailure = Invoke-VerificationWithOutput $fakeRepo
    } finally { $env:PATH = $oldPath }
    Assert-ExitCode "git ls-files failure" 1 $lsFilesFailure.ExitCode
    if ($lsFilesFailure.Output -notmatch "git untracked files") {
        throw "git ls-files failure was not reported by the untracked-files step."
    }

    $failingBin = Join-Path $tmpRoot "failing-bin"
    New-Item -ItemType Directory -Force -Path $failingBin | Out-Null
    Set-Content -LiteralPath (Join-Path $failingBin "git.cmd") -Encoding ASCII -Value @"
@echo off
echo intentional git failure 1>&2
exit /b 23
"@
    $oldPath = $env:PATH
    try {
        $env:PATH = "$failingBin;$oldPath"
        $nativeFailure = Invoke-VerificationWithOutput $fakeRepo
    } finally { $env:PATH = $oldPath }
    Assert-ExitCode "native git command failure propagation" 1 $nativeFailure.ExitCode

    Write-Host "verify-release-baseline git integrity checks passed."
} finally {
    Remove-Item -LiteralPath $tmpRoot -Recurse -Force -ErrorAction SilentlyContinue
}
