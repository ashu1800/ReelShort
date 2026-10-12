<#
.SYNOPSIS
    为 systemd 生产部署准备"薄更新"上传物：只打包应用代码（BOOT-INF/classes），不传 133 MB 的 fat jar。

.DESCRIPTION
    生产后端以展开目录 + 版本化 classes 运行（详见 docs/deploy/systemd.md 第 13 节）：

        /srv/reelshort/backend/classes -> releases/classes-<版本>/classes   # 应用代码（约 1.4 MB）
        /srv/reelshort/backend/app/BOOT-INF/lib                              # 第三方依赖（138 MB，仅依赖变更时替换）

    本脚本负责"构建 + 提取 + 打印上传指令"，实际的 SFTP 上传与符号链接切换按打印出来的步骤执行。
    只改前端时加 -SkipBackend；只改后端时加 -SkipAdminWeb（默认两者都构建）。

.PARAMETER Version
    版本标签，用于生成 releases/classes-<Version> 目录名，默认当前时间戳。

.PARAMETER SkipBackend
    跳过后端 jar 构建与 classes 提取。

.PARAMETER SkipAdminWeb
    跳过后台前端构建与 dist 暂存。

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File infra/scripts/stage-backend-update.ps1
    powershell -ExecutionPolicy Bypass -File infra/scripts/stage-backend-update.ps1 -Version 20261013-1 -SkipAdminWeb

.NOTES
    依赖：backend 用 gradlew bootJar（无需测试）；admin-web 用 npm run build。
    脚本不接触生产凭据，也不直接连服务器。
#>
[CmdletBinding()]
param(
    [string]$Version = (Get-Date -Format 'yyyyMMdd-HHmm'),
    [switch]$SkipBackend,
    [switch]$SkipAdminWeb,
    [string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
)

$ErrorActionPreference = 'Stop'

function Write-Step([string]$text) {
    Write-Host ""
    Write-Host "=== $text ===" -ForegroundColor Cyan
}

$stagingRoot = Join-Path $env:TEMP "reelshort-update-$Version"
$result = [ordered]@{}

if (-not $SkipBackend) {
    Write-Step "构建后端 boot jar"
    Push-Location (Join-Path $RepoRoot 'backend')
    try {
        & .\gradlew.bat bootJar -x test --console=plain | Select-String -Pattern 'BUILD|FAILED' | Select-Object -First 3
        $bootJar = Join-Path $RepoRoot 'backend\build\libs\backend-0.0.1-SNAPSHOT.jar'
        if (-not (Test-Path $bootJar)) { throw "未找到可执行 jar：$bootJar" }
        # Spring Boot Gradle 插件同时产出 -plain.jar（瘦包，无 BOOT-INF），必须显式使用可执行包
        $sizeMb = [math]::Round((Get-Item $bootJar).Length / 1MB, 2)
        Write-Host "boot jar: $sizeMb MB"
    }
    finally { Pop-Location }

    Write-Step "只提取 BOOT-INF/classes（应用代码 + 配置 + Flyway 迁移）"
    $classesOut = Join-Path $stagingRoot 'classes'
    if (Test-Path $stagingRoot) { Remove-Item $stagingRoot -Recurse -Force }
    New-Item -ItemType Directory -Path $classesOut -Force | Out-Null
    python -c @"
import zipfile, os, shutil
jar, dst = r'$bootJar', r'$classesOut'
z = zipfile.ZipFile(jar)
files = total = 0
for i in z.infolist():
    if i.filename.startswith('BOOT-INF/classes/') and not i.filename.endswith('/'):
        rel = i.filename[len('BOOT-INF/classes/'):]
        out = os.path.join(dst, rel.replace('/', os.sep))
        os.makedirs(os.path.dirname(out), exist_ok=True)
        with z.open(i) as src, open(out, 'wb') as f:
            shutil.copyfileobj(src, f)
        files += 1
        total += i.file_size
print(f'classes: {files} 个文件, {total/1e6:.2f} MB')
"@
    if ($LASTEXITCODE -ne 0) { throw 'classes 提取失败' }
    $result['classesStaging'] = $stagingRoot
    $result['classesRemote'] = "/srv/reelshort/backend/releases/classes-$Version"
}

if (-not $SkipAdminWeb) {
    Write-Step "构建后台前端"
    Push-Location (Join-Path $RepoRoot 'admin-web')
    try {
        if (-not (Test-Path 'node_modules')) { & npm install }
        & npm run build | Select-String -Pattern 'built in|error|ERROR' | Select-Object -Last 3
    }
    finally { Pop-Location }
    $dist = Join-Path $RepoRoot 'admin-web\dist'
    if (-not (Test-Path $dist)) { throw "未找到前端产物：$dist" }
    $distMb = [math]::Round((Get-ChildItem $dist -Recurse -File | Measure-Object -Property Length -Sum).Sum / 1MB, 2)
    Write-Host "dist: $distMb MB"
    $result['distLocal'] = $dist
}

Write-Step "上传与切换步骤（按需执行）"
Write-Host @"
按实际改动只上传对应部分：

1) 后端代码变更（约 1.4 MB，替代 133 MB fat jar）
   - 上传本地 '$($result['classesStaging'] ?? '<staging>')'
     到远端 '$($result['classesRemote'] ?? "/srv/reelshort/backend/releases/classes-$Version")'
   - 服务器上执行：
       chown -R reelshort:reelshort $($result['classesRemote'] ?? "/srv/reelshort/backend/releases/classes-$Version")
       ln -sfn $($result['classesRemote'] ?? "/srv/reelshort/backend/releases/classes-$Version")/classes /srv/reelshort/backend/classes
       systemctl restart reelshort-backend && curl -sS http://127.0.0.1:8080/actuator/health

2) 前端变更（约 1.4 MB）
   - 上传本地 '$($result['distLocal'] ?? '<dist>')' 到远端 '/srv/reelshort/admin-web/dist'
   - 静态资源无需重启：nginx 直接读取新文件（index.html 不缓存，/assets/ 文件名带 hash）

3) 依赖版本变更时（少见）
   - 同步 /srv/reelshort/backend/app/BOOT-INF/lib（或重传 app.jar 后重新展开）

4) 内容源变更
   - 只上传 content-provider/app.py（约 42 KB）后 systemctl restart reelshort-content-provider

回滚：
   - 应用代码：ln -sfn /srv/reelshort/backend/releases/<上一个版本>/classes /srv/reelshort/backend/classes && systemctl restart reelshort-backend
   - 彻底回滚：ExecStart 改回 -jar /srv/reelshort/backend/app.jar
"@ -ForegroundColor Yellow
