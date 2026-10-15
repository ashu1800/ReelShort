<#
.SYNOPSIS
  构建卡密服务：先构建前端（Vite），再交叉编译 linux/amd64 单二进制（可选 Windows 调试版）。

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts/build.ps1
  powershell -ExecutionPolicy Bypass -File scripts/build.ps1 -Version 0.1.0 -SkipWindows
#>
[CmdletBinding()]
param(
    [string]$Version = "",
    [switch]$SkipFrontend,
    [switch]$SkipWindows
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
if (-not $Version) { $Version = '0.1.0+' + (Get-Date -Format 'yyyyMMdd-HHmm') }
$distDir = Join-Path $root 'dist'
New-Item -ItemType Directory -Force -Path $distDir | Out-Null

Write-Host "== 构建卡密服务 version=$Version ==" -ForegroundColor Cyan

if (-not $SkipFrontend) {
    $web = Join-Path $root 'web'
    Push-Location $web
    try {
        if (-not (Test-Path (Join-Path $web 'node_modules'))) {
            Write-Host '-- 安装前端依赖 --'
            npm install --registry=https://registry.npmmirror.com
            if ($LASTEXITCODE -ne 0) { throw 'npm install 失败' }
        }
        Write-Host '-- 构建前端（vue-tsc + vite build）--'
        npm run build
        if ($LASTEXITCODE -ne 0) { throw '前端构建失败' }
    }
    finally { Pop-Location }
    if (-not (Test-Path (Join-Path $web 'dist\index.html'))) { throw '前端产物缺少 dist/index.html' }
}

$ldflags = "-s -w -X card.hjj888.cc/cardservice/internal/version.Version=$Version"
Push-Location $root
$saved = @{ CGO_ENABLED = $env:CGO_ENABLED; GOOS = $env:GOOS; GOARCH = $env:GOARCH }
try {
    $env:CGO_ENABLED = '0'
    $env:GOOS = 'linux'
    $env:GOARCH = 'amd64'
    $linuxOut = Join-Path $distDir 'cardservice-linux-amd64'
    Write-Host '-- 编译 linux/amd64 --'
    go build -trimpath -ldflags $ldflags -o $linuxOut ./cmd/cardservice
    if ($LASTEXITCODE -ne 0) { throw 'linux 构建失败' }

    if (-not $SkipWindows) {
        $env:GOOS = 'windows'
        $env:GOARCH = 'amd64'
        Write-Host '-- 编译 windows/amd64（本地联调用）--'
        go build -trimpath -ldflags $ldflags -o (Join-Path $distDir 'cardservice.exe') ./cmd/cardservice
        if ($LASTEXITCODE -ne 0) { throw 'windows 构建失败' }
    }
}
finally {
    Pop-Location
    $env:CGO_ENABLED = $saved.CGO_ENABLED
    $env:GOOS = $saved.GOOS
    $env:GOARCH = $saved.GOARCH
}

Write-Host ''
Write-Host '产物：' -ForegroundColor Green
Get-ChildItem $distDir -File | ForEach-Object {
    $hash = (Get-FileHash $_.FullName -Algorithm SHA256).Hash.Substring(0, 16).ToLower()
    '{0,-28} {1,10:N0} bytes  sha256:{2}...' -f $_.Name, $_.Length, $hash
}
Write-Host ''
Write-Host '下一步：scripts/smoke-test.ps1（本地端到端）或 scripts/deploy.py（部署）'
