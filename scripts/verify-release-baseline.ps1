param(
    [switch]$SkipContentProvider,
    [switch]$SkipBackend,
    [switch]$SkipAdminWeb,
    [switch]$SkipAdminWebContracts,
    [switch]$SkipAndroid,
    [switch]$SkipAndroidPackage,
    [switch]$SkipInfraChecks,
    [switch]$SkipScriptTests,
    [switch]$SkipDiffCheck,
    [switch]$AllowUntrackedFiles,
    [switch]$AllowDirtyTree,
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

function Resolve-PowerShellHost {
    foreach ($candidate in @("pwsh", "powershell")) {
        $command = Get-Command $candidate -ErrorAction SilentlyContinue
        if ($null -ne $command) {
            return $command.Source
        }
    }
    throw "Neither pwsh nor powershell is available to run nested verification scripts."
}

# Runs a nested script in a child shell: some verification scripts call `exit`, which would otherwise
# terminate this baseline process before the failure summary is printed.
function Invoke-ScriptFile {
    param(
        [string]$Path,
        [string[]]$Arguments = @()
    )

    $shell = Resolve-PowerShellHost
    $shellArguments = if ($env:OS -eq "Windows_NT") {
        @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $Path) + $Arguments
    } else {
        @("-NoProfile", "-File", $Path) + $Arguments
    }
    Invoke-Native $shell $shellArguments
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

if (-not $SkipAdminWeb -and -not $SkipAdminWebContracts) {
    $adminWebContracts = Join-Path $adminWebDir "tests"
    if (Test-Path -LiteralPath $adminWebContracts) {
        Invoke-Step "admin-web contract tests" {
            Push-Location $adminWebDir
            try {
                Invoke-Native "npm" @("run", "test:contracts")
            } finally {
                Pop-Location
            }
        }
    }
}

if (-not $SkipAndroid) {
    Invoke-Step "Android unit tests" {
        Push-Location $androidDir
        try {
            Invoke-Native ".\gradlew.bat" @(":app-core:test", ":app:testDebugUnitTest", "--no-daemon")
        } finally {
            Pop-Location
        }
    }

    if (-not $SkipAndroidPackage) {
        Invoke-Step "Android debug APK packaging (requires ANDROID_SIGNING_*)" {
            Push-Location $androidDir
            try {
                Invoke-Native ".\gradlew.bat" @(":app:assembleDebug", "--no-daemon")
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
}

if (-not $SkipInfraChecks) {
    $infraVerification = Join-Path $repoRoot "infra/scripts/verify-backup-scripts.ps1"
    if (Test-Path -LiteralPath $infraVerification) {
        Invoke-Step "infra static verification" {
            Invoke-ScriptFile $infraVerification
        }
    }
}

if (-not $SkipScriptTests) {
    $scriptTests = Join-Path $repoRoot "scripts/tests"
    if (Test-Path -LiteralPath $scriptTests) {
        Invoke-Step "publish script regression tests" {
            Push-Location $repoRoot
            try {
                Invoke-Native "python" @("-m", "pytest", "scripts/tests/test_publish_android.py")
            } finally {
                Pop-Location
            }
        }

        Invoke-Step "release baseline regression tests" {
            Invoke-ScriptFile (Join-Path $scriptTests "verify-release-baseline-tests.ps1")
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

    if (-not $AllowDirtyTree) {
        Invoke-Step "git modified tracked files" {
            Push-Location $repoRoot
            try {
                $modifiedFiles = @(& git status --porcelain --untracked-files=no)
                if ($LASTEXITCODE -ne 0) {
                    throw "git exited with code $LASTEXITCODE"
                }
                if ($modifiedFiles.Count -gt 0) {
                    throw "Uncommitted tracked changes found: $($modifiedFiles -join '; '). Commit or stash them, or pass -AllowDirtyTree for an intentional local build."
                }
            } finally {
                Pop-Location
            }
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
