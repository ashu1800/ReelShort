param(
    [switch]$SkipContentProvider,
    [switch]$SkipBackend,
    [switch]$SkipAdminWeb,
    [switch]$SkipAndroid,
    [switch]$SkipDiffCheck,
    [switch]$AllowUntrackedFiles,
    [switch]$InstallApk,
    [string]$Adb = "adb"
)

$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$androidDir = Join-Path $repoRoot "android-app"
$backendDir = Join-Path $repoRoot "backend"
$adminWebDir = Join-Path $repoRoot "admin-web"
$apkPath = Join-Path $androidDir "app\build\outputs\apk\debug\app-debug.apk"
$failures = New-Object System.Collections.Generic.List[string]

function Invoke-Step {
    param(
        [string]$Name,
        [scriptblock]$Command
    )

    Write-Host ""
    Write-Host "==> $Name" -ForegroundColor Cyan
    try {
        & $Command
        Write-Host "OK: $Name" -ForegroundColor Green
    } catch {
        $failures.Add("${Name}: $($_.Exception.Message)")
        Write-Host "FAILED: $Name" -ForegroundColor Red
        Write-Host $_.Exception.Message -ForegroundColor Red
    }
}

function Invoke-Native {
    param(
        [string]$FilePath,
        [string[]]$Arguments = @()
    )

    & $FilePath @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$FilePath exited with code $LASTEXITCODE"
    }
}

Invoke-Step "Show git baseline" {
    Push-Location $repoRoot
    try {
        Invoke-Native "git" @("status", "--short", "--branch")
        Invoke-Native "git" @("log", "--oneline", "-5")
    } finally {
        Pop-Location
    }
}

if (-not $SkipContentProvider) {
    Invoke-Step "content-provider pytest" {
        Push-Location $repoRoot
        try {
            Invoke-Native "python" @("-m", "pytest", "content-provider")
        } finally {
            Pop-Location
        }
    }
}

if (-not $SkipBackend) {
    Invoke-Step "backend tests" {
        Push-Location $backendDir
        try {
            Invoke-Native ".\gradlew.bat" @("test", "--no-daemon")
        } finally {
            Pop-Location
        }
    }
}

if (-not $SkipAdminWeb) {
    Invoke-Step "admin-web build" {
        Push-Location $adminWebDir
        try {
            if (-not (Test-Path "node_modules")) {
                Invoke-Native "npm" @("ci")
            }
            Invoke-Native "npm" @("run", "build")
        } finally {
            Pop-Location
        }
    }
}

if (-not $SkipAndroid) {
    Invoke-Step "Android unit tests and debug APK" {
        Push-Location $androidDir
        try {
            Invoke-Native ".\gradlew.bat" @(":app-core:test", ":app:testDebugUnitTest", ":app:assembleDebug", "--no-daemon")
        } finally {
            Pop-Location
        }
    }

    if ($InstallApk) {
        Invoke-Step "Install debug APK to emulator" {
            if (-not (Test-Path $apkPath)) {
                throw "APK not found: $apkPath"
            }
            Invoke-Native $Adb @("install", "-r", $apkPath)
        }
    }
}

if (-not $SkipDiffCheck) {
    Invoke-Step "git working tree diff --check" {
        Push-Location $repoRoot
        try {
            Invoke-Native "git" @("diff", "--check")
        } finally {
            Pop-Location
        }
    }

    Invoke-Step "git staged diff --check" {
        Push-Location $repoRoot
        try {
            Invoke-Native "git" @("diff", "--cached", "--check")
        } finally {
            Pop-Location
        }
    }

    if (-not $AllowUntrackedFiles) {
        Invoke-Step "git untracked files" {
            Push-Location $repoRoot
            try {
                $untrackedFiles = @(& git ls-files --others --exclude-standard)
                if ($LASTEXITCODE -ne 0) {
                    throw "git exited with code $LASTEXITCODE"
                }
                if ($untrackedFiles.Count -gt 0) {
                    throw "Untracked files found: $($untrackedFiles -join ', '). Use -AllowUntrackedFiles only for intentional generated output."
                }
            } finally {
                Pop-Location
            }
        }
    }
}

Write-Host ""
if ($failures.Count -gt 0) {
    Write-Host "Release baseline verification failed:" -ForegroundColor Red
    foreach ($failure in $failures) {
        Write-Host "- $failure" -ForegroundColor Red
    }
    exit 1
}

Write-Host "Release baseline verification completed successfully." -ForegroundColor Green
