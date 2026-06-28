param(
    [string]$AndroidSdk = "$env:LOCALAPPDATA\Android\Sdk",
    [string]$LdPlayerAdb = "C:\leidian\LDPlayer14\adb.exe",
    [int]$BackendPort = 8080,
    [int]$ContentProviderPort = 5000,
    [switch]$SkipInstall
)

$ErrorActionPreference = "Stop"

function Get-PortOwnerIds {
    param([int[]]$Ports)

    Get-NetTCPConnection -LocalPort $Ports -State Listen -ErrorAction SilentlyContinue |
        Select-Object -ExpandProperty OwningProcess -Unique
}

function Test-PortAvailable {
    param(
        [int]$Port,
        [string]$ServiceName
    )

    $ownerIds = Get-PortOwnerIds -Ports @($Port)
    if ($ownerIds) {
        $processes = $ownerIds |
            ForEach-Object {
                Get-CimInstance Win32_Process -Filter "ProcessId=$_" -ErrorAction SilentlyContinue
            } |
            Where-Object { $_ } |
            ForEach-Object { "$($_.ProcessId) $($_.Name) $($_.CommandLine)" }
        throw "$ServiceName port $Port is already in use. Stop the process first: $($processes -join '; ')"
    }
}

function Wait-Health {
    param(
        [string]$ServiceName,
        [string]$Url
    )

    for ($i = 0; $i -lt 60; $i++) {
        try {
            $response = Invoke-RestMethod -Uri $Url -TimeoutSec 2
            if ($response.status -eq "UP") {
                return
            }
        } catch {
            Start-Sleep -Seconds 2
        }
    }
    throw "$ServiceName did not become healthy at $Url"
}

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$contentProviderDir = Join-Path $repoRoot "content-provider"
$backendDir = Join-Path $repoRoot "backend"
$androidDir = Join-Path $repoRoot "android-app"
$apkPath = Join-Path $androidDir "app\build\outputs\apk\debug\app-debug.apk"
$logDir = Join-Path $repoRoot "backend\build\app-local-dev"
$contentProviderStdout = Join-Path $logDir "content-provider.out.log"
$contentProviderStderr = Join-Path $logDir "content-provider.err.log"
$backendStdout = Join-Path $logDir "backend.out.log"
$backendStderr = Join-Path $logDir "backend.err.log"

if (-not (Test-Path (Join-Path $AndroidSdk "platforms\android-35\android.jar"))) {
    throw "Android SDK android-35 not found at $AndroidSdk"
}
if (-not (Test-Path $LdPlayerAdb)) {
    throw "LDPlayer adb not found at $LdPlayerAdb"
}
Test-PortAvailable -Port $ContentProviderPort -ServiceName "content-provider"
Test-PortAvailable -Port $BackendPort -ServiceName "backend"
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
Remove-Item -LiteralPath $contentProviderStdout,$contentProviderStderr,$backendStdout,$backendStderr -ErrorAction SilentlyContinue

$env:ANDROID_HOME = $AndroidSdk
$env:ANDROID_SDK_ROOT = $AndroidSdk
Set-Content -LiteralPath (Join-Path $androidDir "local.properties") -Value ("sdk.dir=" + ($AndroidSdk -replace "\\", "/")) -Encoding ASCII

$contentProvider = $null
$backend = $null
$previousContentProviderPort = $env:CONTENT_PROVIDER_PORT

try {
    $env:CONTENT_PROVIDER_PORT = "$ContentProviderPort"
    $contentProvider = Start-Process -FilePath "python" -ArgumentList "app.py" -WorkingDirectory $contentProviderDir -RedirectStandardOutput $contentProviderStdout -RedirectStandardError $contentProviderStderr -PassThru -WindowStyle Hidden
    $backendArgs = "--spring.profiles.active=app-dev --server.port=$BackendPort --reelshort.content-provider.base-url=http://127.0.0.1:$ContentProviderPort"
    $backend = Start-Process -FilePath (Join-Path $backendDir "gradlew.bat") -ArgumentList "bootRun --args=`"$backendArgs`"" -WorkingDirectory $backendDir -RedirectStandardOutput $backendStdout -RedirectStandardError $backendStderr -PassThru -WindowStyle Hidden

    Write-Host "content-provider PID: $($contentProvider.Id)"
    Write-Host "backend PID: $($backend.Id)"
    Write-Host "Waiting for content-provider health..."

    $contentProviderHealthUrl = "http://127.0.0.1:$ContentProviderPort/health"
    $healthUrl = "http://127.0.0.1:$BackendPort/actuator/health"
    Wait-Health -ServiceName "content-provider" -Url $contentProviderHealthUrl
    Write-Host "Waiting for backend health..."
    Wait-Health -ServiceName "backend" -Url $healthUrl

    Push-Location $androidDir
    try {
        .\gradlew.bat :app:assembleDebug --no-daemon "-PreelshortApiBaseUrl=http://10.0.2.2:$BackendPort/api/app"
    } finally {
        Pop-Location
    }

    if (-not $SkipInstall) {
        & $LdPlayerAdb install -r $apkPath
        & $LdPlayerAdb shell am start -n com.reelshort.app/.MainActivity
    }

    Write-Host "App local dev is running."
    Write-Host "Backend App API: http://127.0.0.1:$BackendPort/api/app"
    Write-Host "Android emulator API base URL: http://10.0.2.2:$BackendPort/api/app"
    $ownerIds = Get-PortOwnerIds -Ports @($ContentProviderPort, $BackendPort)
    Write-Host "Stop command: Stop-Process -Id $($ownerIds -join ',')"
} catch {
    Write-Host "content-provider stdout: $contentProviderStdout"
    if (Test-Path $contentProviderStdout) {
        Get-Content -LiteralPath $contentProviderStdout -Tail 40
    }
    Write-Host "content-provider stderr: $contentProviderStderr"
    if (Test-Path $contentProviderStderr) {
        Get-Content -LiteralPath $contentProviderStderr -Tail 40
    }
    Write-Host "backend stdout: $backendStdout"
    if (Test-Path $backendStdout) {
        Get-Content -LiteralPath $backendStdout -Tail 80
    }
    Write-Host "backend stderr: $backendStderr"
    if (Test-Path $backendStderr) {
        Get-Content -LiteralPath $backendStderr -Tail 80
    }
    $ownerIds = Get-PortOwnerIds -Ports @($ContentProviderPort, $BackendPort)
    foreach ($ownerId in $ownerIds) {
        Stop-Process -Id $ownerId -Force -ErrorAction SilentlyContinue
    }
    if ($contentProvider -and -not $contentProvider.HasExited) {
        Stop-Process -Id $contentProvider.Id -Force -ErrorAction SilentlyContinue
    }
    if ($backend -and -not $backend.HasExited) {
        Stop-Process -Id $backend.Id -Force -ErrorAction SilentlyContinue
    }
    throw
} finally {
    $env:CONTENT_PROVIDER_PORT = $previousContentProviderPort
}
