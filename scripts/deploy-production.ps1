<#
.SYNOPSIS
  ReelShort 生产环境极速自动化打包部署工具（目标：Server ID 297 / 177.4.8.115）。
.DESCRIPTION
  解决逐文件高 RTT 跨国传输和目标机器漂移问题：
  1. 支持一键构建 admin-web 与 backend；
  2. 使用 tar -czf 将资产打包为单一轻量 tarball（前端 ~414 KB，后端 ~47 KB）；
  3. 输出标准部署包至 build/deploy-artifacts/，支持 MCP 或 SSH 原子解压与验证。
#>
[CmdletBinding()]
param(
  [string]$ServerId = "297",
  [string]$ServerHost = "177.4.8.115",
  [switch]$SkipBuild,
  [switch]$FrontendOnly,
  [switch]$BackendOnly
)

$ErrorActionPreference = "Stop"
$WorkspaceRoot = (Resolve-Path "$PSScriptRoot\..").Path
$ArtifactDir = Join-Path $WorkspaceRoot "build\deploy-artifacts"

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host " ReelShort 极速生产部署打包器" -ForegroundColor Cyan
Write-Host " 目标服务器: Server #$ServerId ($ServerHost)" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan

if (-not (Test-Path $ArtifactDir)) {
  New-Item -ItemType Directory -Path $ArtifactDir -Force | Out-Null
}

# 1. 前端构建与打包
if (-not $BackendOnly) {
  Write-Host "[1/3] 处理前端 admin-web 产物..." -ForegroundColor Yellow
  $AdminWebDir = Join-Path $WorkspaceRoot "admin-web"
  if (-not $SkipBuild) {
    Write-Host "  -> 执行 npm run build..." -ForegroundColor Gray
    Push-Location $AdminWebDir
    try {
      npm run build
      if ($LASTEXITCODE -ne 0) { throw "admin-web 构建失败" }
    } finally {
      Pop-Location
    }
  }

  $DistDir = Join-Path $AdminWebDir "dist"
  if (-not (Test-Path (Join-Path $DistDir "index.html"))) {
    throw "未找到 admin-web/dist/index.html，请先执行构建"
  }

  $FrontendTar = Join-Path $ArtifactDir "admin-web-dist.tar.gz"
  if (Test-Path $FrontendTar) { Remove-Item $FrontendTar -Force }
  Write-Host "  -> 打包 admin-web/dist 到 $FrontendTar..." -ForegroundColor Gray
  tar -czf $FrontendTar -C $DistDir .
  $FeSize = (Get-Item $FrontendTar).Length / 1KB
  Write-Host "  -> 前端包生成完成: $([Math]::Round($FeSize, 1)) KB" -ForegroundColor Green
}

# 2. 后端类打包
if (-not $FrontendOnly) {
  Write-Host "[2/3] 处理后端 backend 增量类..." -ForegroundColor Yellow
  $BackendDir = Join-Path $WorkspaceRoot "backend"
  if (-not $SkipBuild) {
    Write-Host "  -> 执行 ./gradlew compileJava..." -ForegroundColor Gray
    Push-Location $BackendDir
    try {
      ./gradlew compileJava -x test
      if ($LASTEXITCODE -ne 0) { throw "backend compileJava 失败" }
    } finally {
      Pop-Location
    }
  }

  $ClassesDir = Join-Path $BackendDir "build\classes\java\main"
  if (-not (Test-Path $ClassesDir)) {
    throw "未找到 backend classes 目录，请先编译"
  }

  $BackendTar = Join-Path $ArtifactDir "backend-classes.tar.gz"
  if (Test-Path $BackendTar) { Remove-Item $BackendTar -Force }
  Write-Host "  -> 打包 backend/build/classes/java/main 到 $BackendTar..." -ForegroundColor Gray
  tar -czf $BackendTar -C $ClassesDir com/reelshort/backend/content
  $BeSize = (Get-Item $BackendTar).Length / 1KB
  Write-Host "  -> 后端增量类包生成完成: $([Math]::Round($BeSize, 1)) KB" -ForegroundColor Green
}

# 3. 输出部署指引与远端单行解压命令
Write-Host "[3/3] 产物已准备完毕，远端解压执行步骤:" -ForegroundColor Yellow
Write-Host "--------------------------------------------------" -ForegroundColor DarkGray
if (-not $BackendOnly) {
  Write-Host "前端上传目标: /srv/reelshort/admin-web/dist/" -ForegroundColor White
  Write-Host "远端解压命令: tar -xzf admin-web-dist.tar.gz -C /srv/reelshort/admin-web/dist/ && chown -R reelshort:reelshort /srv/reelshort/admin-web/dist/" -ForegroundColor DarkCyan
}
if (-not $FrontendOnly) {
  Write-Host "后端上传目标: /srv/reelshort/backend/app/BOOT-INF/classes/" -ForegroundColor White
  Write-Host "远端解压命令: tar -xzf backend-classes.tar.gz -C /srv/reelshort/backend/app/BOOT-INF/classes/ && chown -R reelshort:reelshort /srv/reelshort/backend/app/BOOT-INF/classes/ && systemctl restart reelshort-backend" -ForegroundColor DarkCyan
}
Write-Host "--------------------------------------------------" -ForegroundColor DarkGray
Write-Host "健康检查命令: curl -s http://127.0.0.1:8080/actuator/health" -ForegroundColor Green
Write-Host "部署打包完成！产物路径: $ArtifactDir" -ForegroundColor Green
