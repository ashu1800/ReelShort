<#
.SYNOPSIS
  本地端到端冒烟测试：编译 Windows 二进制 → 用临时数据库启动 → 走完后台与 6 个卡密接口全流程。

.DESCRIPTION
  覆盖：管理员登录/强制改密 → 生成卡密 → verify → login → heartbeat → status → rebind → logout，
  并断言易语言客户端依赖的字段类型（code 为字符串、success 为布尔、expires_at 格式）。
  全部通过退出码为 0，任何一步失败即抛错退出。
#>
[CmdletBinding()]
param(
    [int]$Port = 18090,
    [string]$AdminUser = 'admin',
    [string]$AdminPassword = 'smoke-admin-password',
    [string]$NewAdminPassword = 'smoke-admin-password-2'
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$work = Join-Path ([System.IO.Path]::GetTempPath()) ('cardservice-smoke-' + [guid]::NewGuid().ToString('N').Substring(0, 8))
New-Item -ItemType Directory -Force -Path $work | Out-Null
$base = "http://127.0.0.1:$Port"
$passed = 0

function Step([string]$name) { Write-Host "== $name ==" -ForegroundColor Cyan }
function Ok([string]$detail) { $script:passed++; Write-Host "  [PASS] $detail" -ForegroundColor Green }
function Fail([string]$detail) { Write-Host "  [FAIL] $detail" -ForegroundColor Red; throw $detail }

function Assert-Equal($actual, $expected, [string]$what) {
    if ($actual -ne $expected) { Fail "$what 期望 [$expected]，实际 [$actual]" }
    Ok "$what = $expected"
}

function Assert-True($cond, [string]$what) {
    if (-not $cond) { Fail $what }
    Ok $what
}

function Assert-StringField($obj, [string]$key) {
    $v = $obj.$key
    if ($null -eq $v -or $v -isnot [string]) { Fail "字段 $key 必须是 JSON 字符串，实际类型 $($v.GetType().Name)" }
    Ok "字段 $key 是字符串（$v）"
}

# ---------- 构建 + 启动 ----------
Step '构建并启动本地实例'
$exe = Join-Path $work 'cardservice.exe'
Push-Location $root
try {
    $env:CGO_ENABLED = '0'
    go build -o $exe ./cmd/cardservice
    if ($LASTEXITCODE -ne 0) { throw 'go build 失败' }
}
finally { Pop-Location }
Ok "编译完成：$exe"

$env:CARDSERVICE_ADDR = "127.0.0.1:$Port"
$env:CARDSERVICE_DB_PATH = Join-Path $work 'smoke.db'
$env:CARDSERVICE_BOOTSTRAP_ADMIN_USERNAME = $AdminUser
$env:CARDSERVICE_BOOTSTRAP_ADMIN_PASSWORD = $AdminPassword
$env:CARDSERVICE_COOKIE_SECURE = 'false'
$env:CARDSERVICE_LOG_LEVEL = 'warn'
$env:CARDSERVICE_ACCESS_LOG = 'false'

$stdout = Join-Path $work 'stdout.log'
$stderr = Join-Path $work 'stderr.log'
$proc = Start-Process -FilePath $exe -PassThru -NoNewWindow -RedirectStandardOutput $stdout -RedirectStandardError $stderr
try {
    $ready = $false
    for ($i = 0; $i -lt 40; $i++) {
        Start-Sleep -Milliseconds 250
        try {
            $health = Invoke-RestMethod -Uri "$base/healthz" -TimeoutSec 3
            if ($health.status -eq 'ok') { $ready = $true; break }
        }
        catch { }
    }
    if (-not $ready) { Fail "服务未就绪，日志：`n$(Get-Content $stderr -Raw -ErrorAction SilentlyContinue)" }
    Ok "服务已就绪（$base，pid=$($proc.Id)）"

    $json = @{ 'Content-Type' = 'application/json' }
    $csrf = @{ 'X-Requested-With' = 'card-admin' }

    # ---------- 后台：登录 + 强制改密 ----------
    Step '管理后台：登录 / 强制改密 / 生成卡密'
    $session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
    $loginBody = @{ username = $AdminUser; password = $AdminPassword } | ConvertTo-Json -Compress
    $login = Invoke-RestMethod -Uri "$base/api/admin/auth/login" -Method Post -Body $loginBody -ContentType 'application/json' -Headers $csrf -WebSession $session
    Assert-True ($login.admin.must_change_password -eq $true) '初始管理员处于强制改密状态'

    $blocked = $false
    try { Invoke-RestMethod -Uri "$base/api/admin/cards" -WebSession $session | Out-Null }
    catch { $blocked = ($_.Exception.Response.StatusCode.value__ -eq 403) }
    Assert-True $blocked '未改密时受保护接口被拦截（403）'

    $pwdBody = @{ old_password = $AdminPassword; new_password = $NewAdminPassword } | ConvertTo-Json -Compress
    Invoke-RestMethod -Uri "$base/api/admin/auth/password" -Method Put -Body $pwdBody -ContentType 'application/json' -Headers $csrf -WebSession $session | Out-Null
    Ok '改密成功（旧会话已吊销）'

    $session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
    $loginBody = @{ username = $AdminUser; password = $NewAdminPassword } | ConvertTo-Json -Compress
    Invoke-RestMethod -Uri "$base/api/admin/auth/login" -Method Post -Body $loginBody -ContentType 'application/json' -Headers $csrf -WebSession $session | Out-Null
    Ok '使用新口令重新登录成功'

    $types = (Invoke-RestMethod -Uri "$base/api/admin/card_types" -WebSession $session).data
    $monthly = $types | Where-Object { $_.name -eq '月卡' } | Select-Object -First 1
    Assert-True ($null -ne $monthly) '预置卡密类型包含「月卡」'

    $genBody = @{ type_id = $monthly.id; count = 3; length = 16; max_rebinds = 2; remark = 'smoke' } | ConvertTo-Json -Compress
    $gen = Invoke-RestMethod -Uri "$base/api/admin/cards/batch" -Method Post -Body $genBody -ContentType 'application/json' -Headers $csrf -WebSession $session
    Assert-Equal $gen.count 3 '批量生成 3 张卡密'
    $code = $gen.codes[0]
    Assert-True ($code -match '-') "卡密带分隔符展示：$code"
    $batchNo = $gen.batch_no
    Assert-True (-not [string]::IsNullOrWhiteSpace($batchNo)) "批次号：$batchNo"

    $list = Invoke-RestMethod -Uri "$base/api/admin/cards?keyword=$code" -WebSession $session
    Assert-Equal $list.pagination.total_items 1 '按卡密检索命中 1 条'
    Assert-Equal $list.data[0].display_state 'unused' '新卡密展示态为 unused'

    $stats = Invoke-RestMethod -Uri "$base/api/admin/stats" -WebSession $session
    Assert-True ($stats.cards.total -ge 3) "统计总数包含新生成的卡密（total=$($stats.cards.total)）"

    # ---------- 公共接口（易语言客户端序列） ----------
    Step '公共卡密接口：verify → login → heartbeat → status → rebind → logout'
    $machineA = 'SMOKE-MACHINE-A'
    $machineB = 'SMOKE-MACHINE-B'

    $verify = Invoke-RestMethod -Uri "$base/api/verify" -Method Post -Body (@{ code = $code; machine_code = $machineA } | ConvertTo-Json -Compress) -ContentType 'application/json'
    Assert-Equal $verify.code '0' 'verify.code'
    Assert-Equal $verify.status 'valid' 'verify.status'
    Assert-StringField $verify 'type'
    Assert-StringField $verify 'expires_at'
    Assert-True ($verify.expires_at -match '^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$') "expires_at 格式：$($verify.expires_at)"

    $denied = Invoke-RestMethod -Uri "$base/api/verify" -Method Post -Body (@{ code = $code; machine_code = $machineB } | ConvertTo-Json -Compress) -ContentType 'application/json'
    Assert-Equal $denied.code '403' '其他机器 verify.code'
    Assert-Equal $denied.status 'bound_other' '其他机器 verify.status'

    $loginCard = Invoke-RestMethod -Uri "$base/api/card/login" -Method Post -Body (@{ code = $code; machine_code = $machineA } | ConvertTo-Json -Compress) -ContentType 'application/json'
    Assert-Equal $loginCard.code '0' 'login.code'
    Assert-Equal $loginCard.status 'ok' 'login.status'
    $token = $loginCard.session_token
    Assert-True (-not [string]::IsNullOrWhiteSpace($token)) 'login 返回 session_token'

    $hb = Invoke-RestMethod -Uri "$base/api/card/heartbeat" -Method Post -Body (@{ session_token = $token } | ConvertTo-Json -Compress) -ContentType 'application/json'
    Assert-Equal $hb.code '0' 'heartbeat.code'
    Assert-Equal $hb.status 'ok' 'heartbeat.status'

    $statusRes = Invoke-RestMethod -Uri "$base/api/card/status" -Method Post -Body (@{ code = $code; machine_code = $machineA } | ConvertTo-Json -Compress) -ContentType 'application/json'
    Assert-Equal $statusRes.status 'valid' 'status.status'
    Assert-True ($statusRes.online -eq $true) 'status.online 为 true'

    $rebind = Invoke-RestMethod -Uri "$base/api/card/rebind" -Method Post -Body (@{ code = $code; new_machine_code = $machineB } | ConvertTo-Json -Compress) -ContentType 'application/json'
    Assert-Equal $rebind.code '0' 'rebind.code'
    if ($rebind.success -isnot [bool]) { Fail 'rebind.success 必须是 JSON 布尔' }
    Assert-True ($rebind.success -eq $true) 'rebind.success 为布尔 true'

    $hbKicked = Invoke-RestMethod -Uri "$base/api/card/heartbeat" -Method Post -Body (@{ session_token = $token } | ConvertTo-Json -Compress) -ContentType 'application/json'
    Assert-Equal $hbKicked.code '401' '换绑后旧会话心跳被拒'

    $verifyB = Invoke-RestMethod -Uri "$base/api/verify" -Method Post -Body (@{ code = $code; machine_code = $machineB } | ConvertTo-Json -Compress) -ContentType 'application/json'
    Assert-Equal $verifyB.code '0' '换绑后新机器 verify 通过'

    $loginB = Invoke-RestMethod -Uri "$base/api/card/login" -Method Post -Body (@{ code = $code; machine_code = $machineB } | ConvertTo-Json -Compress) -ContentType 'application/json'
    $tokenB = $loginB.session_token
    $logout = Invoke-RestMethod -Uri "$base/api/card/logout" -Method Post -Body (@{ session_token = $tokenB } | ConvertTo-Json -Compress) -ContentType 'application/json'
    Assert-Equal $logout.code '0' 'logout.code'
    Assert-Equal $logout.status 'ok' 'logout.status'

    $heartbeatAfterLogout = Invoke-RestMethod -Uri "$base/api/card/heartbeat" -Method Post -Body (@{ session_token = $tokenB } | ConvertTo-Json -Compress) -ContentType 'application/json'
    Assert-Equal $heartbeatAfterLogout.code '401' '下线后心跳被拒'

    # ---------- 会话与日志 ----------
    Step '后台：在线会话与操作日志'
    $logs = Invoke-RestMethod -Uri "$base/api/admin/logs?card_code=$($code -replace '-','')&page_size=50" -WebSession $session
    Assert-True ($logs.pagination.total_items -ge 5) "操作日志已记录（$($logs.pagination.total_items) 条）"
    $audit = Invoke-RestMethod -Uri "$base/api/admin/audit_logs?page_size=50" -WebSession $session
    Assert-True ($audit.pagination.total_items -ge 2) "审计日志已记录（$($audit.pagination.total_items) 条）"

    Write-Host ''
    Write-Host "全部通过：$passed 项断言" -ForegroundColor Green
    Write-Host "临时目录：$work（数据库与日志保留，便于排查）"
    exit 0
}
finally {
    if ($proc -and -not $proc.HasExited) { Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue }
}
