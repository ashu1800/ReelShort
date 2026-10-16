param(
    [string]$Adb = "adb",
    [string]$ApkPath = "",
    [string]$OutputDir = "",
    [switch]$SkipInstall,
    [switch]$AllowDowngrade,
    [ValidateSet("light", "dark")][string]$Mode = "light",
    [string]$FontScale = "1.0",
    [switch]$SelfTest
)

$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$defaultApkPath = Join-Path $repoRoot "android-flutter\build\app\outputs\flutter-apk\app-debug.apk"
$packageName = "com.reelshort.app"
$activityName = "com.reelshort.app/.MainActivity"

if ([string]::IsNullOrWhiteSpace($ApkPath)) {
    $ApkPath = $defaultApkPath
}

if ([string]::IsNullOrWhiteSpace($OutputDir)) {
    $stamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $OutputDir = Join-Path $repoRoot "artifacts\ui-review\$stamp-$Mode-$FontScale"
}

# Guest-reachable surfaces of the current build, in navigation order.
# Ratios are viewport fractions so the same scenario works on any device size.
$scenarios = @(
    @{ Name = "01-home"; Wait = 4.0 },
    @{ Name = "02-search-discovery"; Tab = 2; Wait = 2.5 },
    @{ Name = "03-search-results"; Tap = @("0.26,0.33"); Wait = 3.0 },
    @{ Name = "04-account"; Tab = 4; Wait = 2.5 },
    @{ Name = "05-account-scrolled"; Swipe = @("0.5,0.82,0.5,0.35"); Wait = 1.5 },
    @{ Name = "06-auth-sheet"; Tap = @("0.5,0.32"); Wait = 2.0 },
    @{ Name = "07-funds"; Tab = 3; Wait = 2.5 },
    @{ Name = "08-home-return"; Tab = 1; Wait = 2.0 },
    @{ Name = "09-home-poster-tap"; Tap = @("0.25,0.30"); Wait = 6.0 }
)

function ConvertTo-SafeFileName {
    param([string]$Name)

    return ($Name.Trim().ToLowerInvariant() -replace "[^a-z0-9]+", "-").Trim("-")
}

function Get-AdbScreenSize {
    param([string]$WmSizeOutput)

    if ($WmSizeOutput -notmatch "(\d+)x(\d+)") {
        throw "Unable to parse adb wm size output: $WmSizeOutput"
    }

    [pscustomobject]@{
        Width = [int]$Matches[1]
        Height = [int]$Matches[2]
    }
}

function Get-TapPoint {
    param(
        [int]$Width,
        [int]$Height,
        [string]$Ratio
    )

    if ($Ratio -notmatch "^\s*([0-9.]+)\s*,\s*([0-9.]+)\s*$") {
        throw "Tap ratio '$Ratio' must look like '0.5,0.9'."
    }

    [pscustomobject]@{
        X = [int][math]::Round($Width * [double]$Matches[1])
        Y = [int][math]::Round($Height * [double]$Matches[2])
    }
}

# The four primary tabs occupy equal quarters of the bottom navigation bar.
function Get-TabTapRatio {
    param([int]$Tab)

    if ($Tab -lt 1 -or $Tab -gt 4) {
        throw "Tab index must be between 1 and 4, got $Tab."
    }

    $x = (($Tab - 0.5) / 4).ToString("0.###")
    return "$x,0.955"
}

# Extracts "package/activity" from a `dumpsys window` dump, or an empty string
# when the dump carries no focus line or the focused window is not an activity
# (a popup, the keyguard, the status bar, ...).
function Get-FocusedWindowName {
    param([string]$Dump)

    $match = [regex]::Match($Dump, "mCurrentFocus=Window\{[^}]*\s(?<name>[^\s}]+)\}")
    if (-not $match.Success) {
        return ""
    }

    $name = $match.Groups["name"].Value
    if ($name -notmatch "^[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+$") {
        return ""
    }
    return $name
}

function Invoke-SelfTest {
    if ((ConvertTo-SafeFileName "03 Search / Results") -ne "03-search-results") {
        throw "Safe file name conversion failed."
    }

    $size = Get-AdbScreenSize "Physical size: 1080x1920"
    if ($size.Width -ne 1080 -or $size.Height -ne 1920) {
        throw "Screen size parsing failed: $($size.Width)x$($size.Height)"
    }

    $point = Get-TapPoint -Width 1080 -Height 1920 -Ratio "0.955,0.953"
    if ($point.X -ne 1031 -or $point.Y -ne 1830) {
        throw "Tap point calculation failed: $($point.X),$($point.Y)"
    }

    if ((Get-TabTapRatio 1) -ne "0.125,0.955" -or (Get-TabTapRatio 4) -ne "0.875,0.955") {
        throw "Tab tap ratio calculation failed."
    }

    $dump = @'
  mCurrentFocus=Window{a2ccd3d u0 com.reelshort.app/com.reelshort.app.MainActivity}
  mFocusedApp=AppWindowToken{bd5bc56 token=Token{4cf4939 ActivityRecord{2a7dd00 u0 com.reelshort.app/.MainActivity t7365}}}
'@
    if ((Get-FocusedWindowName -Dump $dump) -ne "com.reelshort.app/com.reelshort.app.MainActivity") {
        throw "Focus parsing failed: '$(Get-FocusedWindowName -Dump $dump)'"
    }

    if ((Get-FocusedWindowName -Dump "mCurrentFocus=Window{c63950d u0 PopupWindow:da40d6}") -ne "") {
        throw "Focus parsing must ignore non-activity windows."
    }

    Write-Host "android-ui-review internal self-test passed."
}

if ($SelfTest) {
    Invoke-SelfTest
    exit 0
}

function Invoke-Adb {
    param([string[]]$Arguments)

    & $Adb @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$Adb $($Arguments -join ' ') exited with code $LASTEXITCODE"
    }
}

function Invoke-AdbCapture {
    param([string[]]$Arguments)

    $output = & $Adb @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$Adb $($Arguments -join ' ') exited with code $LASTEXITCODE"
    }
    return ($output -join "`n")
}

function Wait-ForUi {
    param([double]$Seconds)

    Start-Sleep -Milliseconds ([int]($Seconds * 1000))
}

function Save-Screenshot {
    param([string]$Name, [int]$Width, [int]$Height)

    $safeName = ConvertTo-SafeFileName $Name
    $devicePath = "/sdcard/reelshort-ui-review-$safeName.png"
    $localPath = Join-Path $OutputDir "$safeName.png"
    Invoke-Adb @("shell", "screencap", "-p", $devicePath)
    Invoke-Adb @("pull", $devicePath, $localPath)
    Invoke-Adb @("shell", "rm", $devicePath)
    Write-Host "Screenshot: $localPath"
    return $localPath
}

function Tap-Ratio {
    param([string]$Ratio, [int]$Width, [int]$Height)

    $point = Get-TapPoint -Width $Width -Height $Height -Ratio $Ratio
    Invoke-Adb @("shell", "input", "tap", "$($point.X)", "$($point.Y)")
}

function Swipe-Ratio {
    param([string]$Ratio, [int]$Width, [int]$Height)

    $parts = $Ratio -split ","
    if ($parts.Count -ne 4) {
        throw "Swipe ratio '$Ratio' must look like 'x1,y1,x2,y2'."
    }
    $start = Get-TapPoint -Width $Width -Height $Height -Ratio "$($parts[0]),$($parts[1])"
    $end = Get-TapPoint -Width $Width -Height $Height -Ratio "$($parts[2]),$($parts[3])"
    Invoke-Adb @("shell", "input", "swipe", "$($start.X)", "$($start.Y)", "$($end.X)", "$($end.Y)", "420")
}

# --- device interaction guards -------------------------------------------
# A sleeping or locked device captures black frames, and taps then land on
# whatever app happens to be on screen. Every run therefore wakes the device,
# dismisses the keyguard, and refuses to tap unless ShortLink owns the focus.

function Get-AdbFocusedWindow {
    return Get-FocusedWindowName -Dump (Invoke-AdbCapture @("shell", "dumpsys", "window"))
}

function Enable-DeviceInteraction {
    Invoke-Adb @("shell", "input", "keyevent", "KEYCODE_WAKEUP")
    Start-Sleep -Milliseconds 600
    Invoke-Adb @("shell", "wm", "dismiss-keyguard")
    Start-Sleep -Milliseconds 800
}

function Start-ReviewApp {
    param([int]$Attempts = 3)

    for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
        Invoke-Adb @("shell", "am", "force-stop", $packageName)
        Start-Sleep -Milliseconds 500
        Invoke-Adb @("shell", "am", "start", "-n", $activityName)
        Wait-ForUi 4.0
        $focus = Get-AdbFocusedWindow
        if ($focus -like "$packageName/*") {
            return
        }
        Write-Warning "Launch attempt ${attempt}: focus is '$focus', expected $packageName."
        Enable-DeviceInteraction
    }

    throw "ShortLink never reached the foreground; refusing to capture or tap. Last focus: '$(Get-AdbFocusedWindow)'"
}

function Assert-ReviewAppForeground {
    param([string]$Step)

    $focus = Get-AdbFocusedWindow
    if ($focus -notlike "$packageName/*") {
        throw "Refusing to interact at '$Step': $packageName is not in the foreground (focus: '$focus')."
    }
    return $focus
}

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

$devices = Invoke-AdbCapture @("devices")
if (-not ($devices -match "\bdevice\b")) {
    throw "No adb device is connected. Output: $devices"
}

if (-not $SkipInstall) {
    if (-not (Test-Path $ApkPath)) {
        throw "APK not found: $ApkPath"
    }
    Write-Host "Installing $ApkPath"
    $installArgs = @("install", "-r")
    if ($AllowDowngrade) {
        # The device may carry a higher versionCode release build; -d permits the
        # debug build to replace it without wiping app data.
        $installArgs += "-d"
    }
    $installArgs += $ApkPath
    Invoke-Adb $installArgs
}

# Display configuration is applied before the app starts so Flutter reads it on cold start.
Invoke-Adb @("shell", "cmd", "uimode", "night", $(if ($Mode -eq "dark") { "yes" } else { "no" }))
Invoke-Adb @("shell", "settings", "put", "system", "font_scale", $FontScale)

$screen = Get-AdbScreenSize (Invoke-AdbCapture @("shell", "wm", "size"))

Enable-DeviceInteraction
Start-ReviewApp

$shots = New-Object System.Collections.Generic.List[object]

foreach ($scenario in $scenarios) {
    if ($scenario.ContainsKey("Tab")) {
        Assert-ReviewAppForeground "$($scenario.Name) (tab)" | Out-Null
        Tap-Ratio (Get-TabTapRatio $scenario.Tab) $screen.Width $screen.Height
        Wait-ForUi 1.5
    }

    if ($scenario.ContainsKey("Tap")) {
        Assert-ReviewAppForeground "$($scenario.Name) (tap)" | Out-Null
        foreach ($ratio in $scenario.Tap) {
            Tap-Ratio $ratio $screen.Width $screen.Height
            Wait-ForUi 1.0
        }
    }

    if ($scenario.ContainsKey("Swipe")) {
        Assert-ReviewAppForeground "$($scenario.Name) (swipe)" | Out-Null
        Swipe-Ratio $scenario.Swipe $screen.Width $screen.Height
    }

    Wait-ForUi $scenario.Wait
    $focus = Assert-ReviewAppForeground "$($scenario.Name) (capture)"
    $shot = Save-Screenshot $scenario.Name $screen.Width $screen.Height
    $shots.Add([pscustomobject]@{
            name       = $scenario.Name
            file       = (Split-Path $shot -Leaf)
            focus      = $focus
            capturedAt = (Get-Date).ToString("o")
        })
}

$manifest = [pscustomobject]@{
    capturedAt = (Get-Date).ToString("o")
    mode       = $Mode
    fontScale  = $FontScale
    screen     = "$($screen.Width)x$($screen.Height)"
    apk        = $ApkPath
    shots      = $shots
}
$manifest | ConvertTo-Json -Depth 4 | Set-Content -Encoding UTF8 -Path (Join-Path $OutputDir "manifest.json")

Write-Host ""
Write-Host "Android UI review capture completed. Artifacts: $OutputDir" -ForegroundColor Green
Write-Host "Note: display mode/font scale were changed on the device and are left as-is."
