# 测试与验收

## 1. 自动化测试

| 层 | 命令 | 覆盖内容 |
|---|---|---|
| 后端单元/契约 | `go test ./...` | 6 个公共接口全流程与全部错误分支、字段类型契约、并发绑定、限流、卡密/机器码归一化、管理后台鉴权与 CSRF、卡密与类型 CRUD、设置校验 |
| 前端类型与构建 | `cd web; npm run build` | `vue-tsc` 零类型错误 + Vite 生产构建 |
| 前端契约单测 | `cd web; npm run test:contracts` | 错误包络解析、状态标签映射、剩余时长格式化、CSV 导出内容、设置字段转换 |
| 本地端到端 | `powershell -File scripts/smoke-test.ps1` | 用真实二进制 + 临时 SQLite 跑完整业务流程（36 项断言） |
| 线上验收 | `powershell -File scripts/prod-check.ps1` | 经 Cloudflare 打真实域名：探活、后台首屏、6 个接口全流程 |

### 契约测试为什么重要

易语言客户端用 `类_json` 读字段，对 JSON **类型**敏感（见 `docs/e语言对接.md` 第 2 节）。因此测试里显式断言：

- `code`、`status`、`message`、`type`、`expires_at` 必须是 JSON **字符串**；
- `success` 必须是 JSON **布尔**；
- 任何字段都不允许是 `null`；
- 业务失败也必须返回 HTTP 200 + 同一包络；
- `expires_at` 必须匹配 `^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$`（永久卡为 `永久有效`）。

## 2. 关键回归用例（均已自动化）

1. `verify → login → heartbeat → logout` 全流程，且 logout 幂等、下线后心跳被拒。
2. 卡密不存在 / 参数缺失 / 非法 JSON / 非 POST 方法的包络与状态码。
3. 首次校验绑定机器码；其他机器码得到 `403 bound_other`；机器码大小写与空格差异不误判。
4. **并发首次校验只有一台机器绑定成功**（事务串行化验证）。
5. 换绑：成功后旧机器被拒、新机器可用；次数用尽返回 `409 rebind_limit`；相同机器码不消耗次数；换绑踢掉在线会话。
6. 换机登录必须走换绑（`403 bound_other`），不能被"顶号"绕过。
7. 同机重复登录顶号（旧 token `409 kicked`）；`allow_takeover=false` 时重复登录被拒（`409 online_other`）。
8. 卡密过期/被禁用时校验、上线失败，且在线会话被同步关闭（心跳返回 `410/expired`、`423/disabled`）。
9. 永久卡：`expires_at = 永久有效`、`permanent = true`、`expires_at_ts = 0`。
10. 限流触发 `429 rate_limited`（后台改小阈值后验证）。
11. 后台：初始管理员强制改密拦截、改密吊销全部会话、登录失败锁定、缺少 CSRF 头 403、生成/查询/延期/禁用/启用/删除/导出/统计/审计全链路。

## 4. 本次交付的实测结果（2026-10-12）

| 项 | 命令 | 结果 |
|---|---|---|
| Go 单元/契约测试 | `go test ./...` | **35 个用例全部通过**（httpapi 17 / adminapi 7 / security 7 / store 4），0 失败 |
| 前端类型检查 + 构建 | `cd web; npm run build` | `vue-tsc` 零错误，Vite 产物 7 个 entry/asset 分块，构建 928 ms |
| 前端契约单测 | `cd web; npm run test:contracts` | **17 个用例全部通过** |
| 本地端到端 | `scripts/smoke-test.ps1` | **36 项断言全部通过**（后台登录→强制改密→批量生成→6 接口全流程） |
| 线上验收（经 Cloudflare） | `scripts/prod-check.ps1 -AdminUser admin -AdminPassword ...` | **26 项断言全部通过**：`https://card.hjj888.cc/healthz` = ok、后台 SPA 200、verify/login/heartbeat/status/rebind/logout 全部成功 |
| 现网回归 | `https://shortlink.hjj888.cc/` 及其 `/api/app/release/latest` | 均 200，未受本次部署影响 |
| 线上 SSL | `curl -I https://card.hjj888.cc/` | 200（部署前为 Cloudflare 526） |
| 备份演练 | `systemctl start cardservice-backup.service` | 成功产出 `cardservice-20261012-172408.db`（143 KB），定时器已启用（每日 04:13 触发） |

线上实测发现并已修复的两个缺陷（均已补自动化回归）：

1. 批次号 `NextBatchNo` 把 `"-0001"` 解析为 `-1`，导致第二批变成 `B20261012-0000` → 修复为去掉分隔符后再解析，并新增 `TestNextBatchNo`。
2. 前端日志页发送的日期区间带时间（`YYYY-MM-DD HH:MM:SS`），后端只接受 `YYYY-MM-DD` 会返回 422 → 后端改为 `jstime.ParseFlexible` 兼容两种格式，并新增 `TestLogDateRangeFormats`。

另有一次未复现的 Cloudflare 525（CF↔源站瞬时 TLS 握手失败）：同一时刻直连源站与经 CF 的 10 次连续请求均返回 200，重跑自检全部通过，判定为边缘节点瞬态问题；若再次出现，按 `docs/DEPLOY.md` 第 7 节排查。

## 5. 人工验收清单（部署后）

```powershell
# 1. 探活 + 后台首屏
powershell -ExecutionPolicy Bypass -File scripts/prod-check.ps1
# 2. 完整接口流程（会自动生成一张测试卡）
powershell -ExecutionPolicy Bypass -File scripts/prod-check.ps1 -AdminUser admin -AdminPassword '<口令>'
```

- [ ] `https://card.hjj888.cc/` 打开是管理后台登录页（不再是 nginx 默认页，也不再是 526）
- [ ] 用管理员口令登录（本次部署的初始口令已由自检脚本改为强口令，见交付说明）
- [ ] 卡密管理：批量生成 100 张、按卡密/状态/批次筛选、导出 CSV（Excel 打开中文不乱码）
- [ ] 随便挑一张卡密，在易语言客户端里走一遍：校验卡密 → 上线 → 心跳（放 2 分钟看是否掉线）→ 下线
- [ ] 换一台机器（或改机器码）验证：校验返回 `403`、换绑成功后可上线、换绑次数递减
- [ ] 后台「在线会话」能看到真实客户端 IP（不是 127.0.0.1）、可强制下线
- [ ] 后台「操作日志」能看到刚才每一次调用（卡密与机器码均为脱敏显示）
- [ ] `systemctl status cardservice` 为 `active (running)` 且 `systemctl is-enabled cardservice` 为 `enabled`
- [ ] `systemctl list-timers cardservice-backup.timer` 显示下一次备份时间

## 6. 未覆盖项（已知边界）

- **易语言本机实跑**：开发环境没有易语言运行时，客户端行为通过 curl/PowerShell 复刻同一请求形态验证（路径、字段名、`Content-Type: application/json`、JSON 类型完全一致）。最终以你在客户端实跑一次为准。
- **多机并发压测**：未做上万卡密在线的心跳压测；如需可按 `docs/API.md` 的 6 个接口写脚本压测，SQLite 单写者模型下建议先评估是否需要换 PostgreSQL。
- **Cloudflare 侧风控**：CF 的 WAF/Bot Fight 规则变更不在本项目测试范围内，线上验收包含一次经 CF 的真实调用。
