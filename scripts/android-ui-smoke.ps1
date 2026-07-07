param(
    [string]$Adb = "adb",
    [string]$ApkPath = "",
    [string]$OutputDir = "",
    [switch]$SkipInstall,
    [switch]$SkipPlayerTap,
    [switch]$SelfTest
)

$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$defaultApkPath = Join-Path $repoRoot "android-app\app\build\outputs\apk\debug\app-debug.apk"
$packageName = "com.reelshort.app"
$activityName = "com.reelshort.app/.MainActivity"

if ([string]::IsNullOrWhiteSpace($ApkPath)) {
    $ApkPath = $defaultApkPath
}

if ([string]::IsNullOrWhiteSpace($OutputDir)) {
    $stamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $OutputDir = Join-Path $repoRoot "artifacts\android-ui-smoke\$stamp"
}

function Invoke-Native {
    param(
        [string]$FilePath,
        [string[]]$Arguments = @()
    )

    & $FilePath @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$FilePath $($Arguments -join ' ') exited with code $LASTEXITCODE"
    }
}

function Invoke-NativeCapture {
    param(
        [string]$FilePath,
        [string[]]$Arguments = @()
    )

    $output = & $FilePath @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$FilePath $($Arguments -join ' ') exited with code $LASTEXITCODE"
    }
    return ($output -join "`n")
}

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
        [double]$XRatio,
        [double]$YRatio
    )

    [pscustomobject]@{
        X = [int][math]::Round($Width * $XRatio)
        Y = [int][math]::Round($Height * $YRatio)
    }
}

function Test-XmlContainsAny {
    param(
        [string]$Xml,
        [string[]]$Patterns
    )

    foreach ($pattern in $Patterns) {
        if ($Xml.Contains($pattern)) {
            return $true
        }
    }
    return $false
}

function Assert-XmlContainsAny {
    param(
        [string]$Name,
        [string]$Xml,
        [string[]]$Patterns
    )

    if (-not (Test-XmlContainsAny -Xml $Xml -Patterns $Patterns)) {
        throw "$Name did not contain any expected UI text: $($Patterns -join ', ')"
    }
}

function Invoke-SelfTest {
    $safeName = ConvertTo-SafeFileName "Player / Auth Sheet"
    if ($safeName -ne "player-auth-sheet") {
        throw "Safe file name conversion failed: $safeName"
    }

    $size = Get-AdbScreenSize "Physical size: 720x1280"
    if ($size.Width -ne 720 -or $size.Height -ne 1280) {
        throw "Screen size parsing failed: $($size.Width)x$($size.Height)"
    }

    $tap = Get-TapPoint -Width 720 -Height 1280 -XRatio 0.5 -YRatio 0.9
    if ($tap.X -ne 360 -or $tap.Y -ne 1152) {
        throw "Tap point calculation failed: $($tap.X),$($tap.Y)"
    }

    if (-not (Test-XmlContainsAny -Xml "<node text='Continue watching' />" -Patterns @("Me", "Continue watching"))) {
        throw "XML positive match failed."
    }

    if (Test-XmlContainsAny -Xml "<node text='Home' />" -Patterns @("Continue watching", "Reward")) {
        throw "XML negative match failed."
    }

    Write-Host "android-ui-smoke internal self-test passed."
}

if ($SelfTest) {
    Invoke-SelfTest
    exit 0
}

function Invoke-Adb {
    param([string[]]$Arguments)
    Invoke-Native $Adb $Arguments
}

function Invoke-AdbCapture {
    param([string[]]$Arguments)
    Invoke-NativeCapture $Adb $Arguments
}

function Wait-ForUi {
    param([int]$Seconds = 2)
    Start-Sleep -Seconds $Seconds
}

function Save-Screenshot {
    param([string]$Name)

    $safeName = ConvertTo-SafeFileName $Name
    $devicePath = "/sdcard/reelshort-ui-smoke-$safeName.png"
    $localPath = Join-Path $OutputDir "$safeName.png"
    Invoke-Adb @("shell", "screencap", "-p", $devicePath)
    Invoke-Adb @("pull", $devicePath, $localPath)
    Invoke-Adb @("shell", "rm", $devicePath)
    Write-Host "Screenshot: $localPath"
    return $localPath
}

function Save-UiDump {
    param([string]$Name)

    $safeName = ConvertTo-SafeFileName $Name
    $devicePath = "/sdcard/reelshort-ui-smoke-window.xml"
    $localPath = Join-Path $OutputDir "$safeName.xml"
    Invoke-Adb @("shell", "uiautomator", "dump", $devicePath)
    Invoke-Adb @("pull", $devicePath, $localPath)
    Invoke-Adb @("shell", "rm", $devicePath)
    return Get-Content -Raw -LiteralPath $localPath
}

function Tap-Ratio {
    param(
        [double]$XRatio,
        [double]$YRatio
    )

    $point = Get-TapPoint -Width $script:screenSize.Width -Height $script:screenSize.Height -XRatio $XRatio -YRatio $YRatio
    Invoke-Adb @("shell", "input", "tap", "$($point.X)", "$($point.Y)")
}

function Swipe-Ratio {
    param(
        [double]$StartX,
        [double]$StartY,
        [double]$EndX,
        [double]$EndY,
        [int]$DurationMs = 450
    )

    $start = Get-TapPoint -Width $script:screenSize.Width -Height $script:screenSize.Height -XRatio $StartX -YRatio $StartY
    $end = Get-TapPoint -Width $script:screenSize.Width -Height $script:screenSize.Height -XRatio $EndX -YRatio $EndY
    Invoke-Adb @("shell", "input", "swipe", "$($start.X)", "$($start.Y)", "$($end.X)", "$($end.Y)", "$DurationMs")
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
    Invoke-Adb @("install", "-r", $ApkPath)
}

$script:screenSize = Get-AdbScreenSize (Invoke-AdbCapture @("shell", "wm", "size"))

Invoke-Adb @("shell", "am", "force-stop", $packageName)
Invoke-Adb @("shell", "am", "start", "-n", $activityName)
Wait-ForUi 3

$homeXml = Save-UiDump "home"
Assert-XmlContainsAny "Home screen" $homeXml @("Today's picks", "Home", "Discover")
Save-Screenshot "home" | Out-Null

Tap-Ratio 0.84 0.93
Wait-ForUi 3
$accountXml = Save-UiDump "account"
Assert-XmlContainsAny "Account screen" $accountXml @("Me", "Favorites", "Points", "Watch history", "Sign in")
Save-Screenshot "account" | Out-Null

Swipe-Ratio 0.5 0.86 0.5 0.35
Wait-ForUi 1
$accountScrolledXml = Save-UiDump "account-continue"
if (Test-XmlContainsAny -Xml $accountScrolledXml -Patterns @("Continue watching", "Pick up from your last episode")) {
    Save-Screenshot "account-continue" | Out-Null
}

if (-not $SkipPlayerTap) {
    Tap-Ratio 0.16 0.93
    Wait-ForUi 2
    Tap-Ratio 0.25 0.44
    Wait-ForUi 5
    $playerXml = Save-UiDump "player-or-auth"
    Assert-XmlContainsAny "Player or auth screen" $playerXml @("Loading EP", "Episodes", "Favorite", "Sign in")
    Save-Screenshot "player-or-auth" | Out-Null
}

Write-Host ""
Write-Host "Android UI smoke completed. Artifacts: $OutputDir" -ForegroundColor Green
