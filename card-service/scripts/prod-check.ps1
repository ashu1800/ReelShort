<#
.SYNOPSIS
  线上验收：对已部署的卡密服务（默认 https://card.hjj888.cc）跑完整接口自检。

.DESCRIPTION
  - 探活 /healthz 与管理后台首屏
  - 可选：用管理员账号新建一张测试卡密，然后完整走 verify → login → heartbeat → status → rebind → logout
  - 断言易语言客户端依赖的字段类型与取值

.EXAMPLE
  # 只做探活 + 后台首屏
  powershell -ExecutionPolicy Bypass -File scripts/prod-check.ps1

  # 用管理员口令新建测试卡密并跑完整流程
  powershell -ExecutionPolicy Bypass -File scripts/prod-check.ps1 -AdminUser admin -AdminPassword 'xxx'

  # 用已有卡密跑公共接口流程
  powershell -ExecutionPolicy Bypass -File scripts/prod-check.ps1 -CardCode 'ABCD-EFGH-IJKL-MNPQ'
#>
[CmdletBinding()]
param(
    [string]$BaseUrl = 'https://card.hjj888.cc',
    [string]$AdminUser = '',
    [string]$AdminPassword = '',
    [string]$NewAdminPassword = '',
    [string]$CardCode = '',
    [string]$MachineA = 'PRODCHECK-MACHINE-A',
    [string]$MachineB = 'PRODCHECK-MACHINE-B'
)

$ErrorActionPreference = 'Stop'
$BaseUrl = $BaseUrl.TrimEnd('/')
$passed = 0

function Step([string]$name) { Write-Host "== $name ==" -ForegroundColor Cyan }
function Ok([string]$detail) { $script:passed++; Write-Host "  [PASS] $detail" -ForegroundColor Green }
function Fail([string]$detail) { Write-Host "  [FAIL] $detail" -ForegroundColor Red; throw $detail }
function Assert-Equal($actual, $expected, [string]$what) {
    if ($actual -ne $expected) { Fail "$what 期望 [$expected]，实际 [$actual]" }
    Ok "$what = $expected"
}
function Assert-True($cond, [string]$what) { if (-not $cond) { Fail $what }; Ok $what }

Step "探活 $BaseUrl"
$health = Invoke-RestMethod -Uri "$BaseUrl/healthz" -TimeoutSec 20
Assert-Equal $health.status 'ok' 'healthz.status'
Assert-Equal $health.db 'ok' 'healthz.db'
Write-Host "  version=$($health.version) online_sessions=$($health.online_sessions)"

$indexResp = Invoke-WebRequest -Uri "$BaseUrl/" -TimeoutSec 20
Assert-True ($indexResp.StatusCode -eq 200) '管理后台首页返回 200'
Assert-True ($indexResp.Content -match '<div id="app"') '管理后台首页是 SPA 外壳（不再是 nginx 默认页）'
Ok '经 Cloudflare 的 HTTPS 链路正常（不再是 526）'

if ($AdminUser -and $AdminPassword) {
    Step '管理后台：登录并生成测试卡密'
    $session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
    $csrf = @{ 'X-Requested-With' = 'card-admin' }
    $login = Invoke-RestMethod -Uri "$BaseUrl/api/admin/auth/login" -Method Post `
        -Body (@{ username = $AdminUser; password = $AdminPassword } | ConvertTo-Json -Compress) `
        -ContentType 'application/json' -Headers $csrf -WebSession $session -TimeoutSec 20
    Ok "管理员登录成功（$($login.admin.username)）"

    if ($login.admin.must_change_password) {
        if ($NewAdminPassword) {
            Invoke-RestMethod -Uri "$BaseUrl/api/admin/auth/password" -Method Put `
                -Body (@{ old_password = $AdminPassword; new_password = $NewAdminPassword } | ConvertTo-Json -Compress) `
                -ContentType 'application/json' -Headers $csrf -WebSession $session -TimeoutSec 20 | Out-Null
            Ok '初始口令已按要求改为新口令（旧会话已吊销）'
            $session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
            $login = Invoke-RestMethod -Uri "$BaseUrl/api/admin/auth/login" -Method Post `
                -Body (@{ username = $AdminUser; password = $NewAdminPassword } | ConvertTo-Json -Compress) `
                -ContentType 'application/json' -Headers $csrf -WebSession $session -TimeoutSec 20
            Ok '使用新口令重新登录成功'
        }
        else {
            Write-Host '  [WARN] 该管理员仍是初始口令，受保护接口会被拦截；请传 -NewAdminPassword 或先手工改密' -ForegroundColor Yellow
            Write-Host "`n仅完成探活与登录（$passed 项）。" -ForegroundColor Yellow
            exit 0
        }
    }

    $types = (Invoke-RestMethod -Uri "$BaseUrl/api/admin/card_types" -WebSession $session -TimeoutSec 20).data
    $type = $types | Where-Object { $_.name -eq '天卡' } | Select-Object -First 1
    if (-not $type) { $type = $types | Select-Object -First 1 }
    $gen = Invoke-RestMethod -Uri "$BaseUrl/api/admin/cards" -Method Post `
        -Body (@{ type_id = $type.id; count = 1; remark = 'prod-check' } | ConvertTo-Json -Compress) `
        -ContentType 'application/json' -Headers $csrf -WebSession $session -TimeoutSec 20
    $CardCode = $gen.codes[0]
    Ok "生成测试卡密：$CardCode（批次 $($gen.batch_no)）"
}

if (-not $CardCode) {
    Write-Host ''
    Write-Host "仅完成探活（$passed 项）。若要做完整接口自检，请传 -CardCode 或 -AdminUser/-AdminPassword。" -ForegroundColor Yellow
    exit 0
}

Step '公共卡密接口全流程（与易语言客户端同形态）'
$json = 'application/json'
$verify = Invoke-RestMethod -Uri "$BaseUrl/api/verify" -Method Post -Body (@{ code = $CardCode; machine_code = $MachineA } | ConvertTo-Json -Compress) -ContentType $json -TimeoutSec 20
Assert-Equal $verify.code '0' 'verify.code'
Assert-Equal $verify.status 'valid' 'verify.status'
Assert-True ($verify.expires_at -is [string]) 'verify.expires_at 是字符串'
Assert-True ($verify.type -is [string]) 'verify.type 是字符串'
Ok "卡密类型=$($verify.type) 到期=$($verify.expires_at)"

$other = Invoke-RestMethod -Uri "$BaseUrl/api/verify" -Method Post -Body (@{ code = $CardCode; machine_code = $MachineB } | ConvertTo-Json -Compress) -ContentType $json -TimeoutSec 20
Assert-Equal $other.code '403' '其他机器 verify.code'

$login = Invoke-RestMethod -Uri "$BaseUrl/api/card/login" -Method Post -Body (@{ code = $CardCode; machine_code = $MachineA } | ConvertTo-Json -Compress) -ContentType $json -TimeoutSec 20
Assert-Equal $login.code '0' 'login.code'
Assert-Equal $login.status 'ok' 'login.status'
$token = $login.session_token
Assert-True (-not [string]::IsNullOrWhiteSpace($token)) 'login 返回 session_token'

$hb = Invoke-RestMethod -Uri "$BaseUrl/api/card/heartbeat" -Method Post -Body (@{ session_token = $token } | ConvertTo-Json -Compress) -ContentType $json -TimeoutSec 20
Assert-Equal $hb.code '0' 'heartbeat.code'
Assert-Equal $hb.status 'ok' 'heartbeat.status'

$st = Invoke-RestMethod -Uri "$BaseUrl/api/card/status" -Method Post -Body (@{ code = $CardCode; machine_code = $MachineA } | ConvertTo-Json -Compress) -ContentType $json -TimeoutSec 20
Assert-Equal $st.code '0' 'status.code'
Assert-Equal $st.status 'valid' 'status.status'

$rebind = Invoke-RestMethod -Uri "$BaseUrl/api/card/rebind" -Method Post -Body (@{ code = $CardCode; new_machine_code = $MachineB } | ConvertTo-Json -Compress) -ContentType $json -TimeoutSec 20
Assert-Equal $rebind.code '0' 'rebind.code'
Assert-True ($rebind.success -is [bool] -and $rebind.success) 'rebind.success 为布尔 true'

$kicked = Invoke-RestMethod -Uri "$BaseUrl/api/card/heartbeat" -Method Post -Body (@{ session_token = $token } | ConvertTo-Json -Compress) -ContentType $json -TimeoutSec 20
Assert-True ($kicked.code -ne '0') '换绑后旧会话心跳被拒'

$loginB = Invoke-RestMethod -Uri "$BaseUrl/api/card/login" -Method Post -Body (@{ code = $CardCode; machine_code = $MachineB } | ConvertTo-Json -Compress) -ContentType $json -TimeoutSec 20
Assert-Equal $loginB.code '0' '换绑后新机器 login.code'
$logout = Invoke-RestMethod -Uri "$BaseUrl/api/card/logout" -Method Post -Body (@{ session_token = $loginB.session_token } | ConvertTo-Json -Compress) -ContentType $json -TimeoutSec 20
Assert-Equal $logout.code '0' 'logout.code'
Assert-Equal $logout.status 'ok' 'logout.status'

Write-Host ''
Write-Host "线上验收全部通过：$passed 项断言" -ForegroundColor Green
