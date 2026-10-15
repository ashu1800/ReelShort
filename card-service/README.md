# card-service · 卡密服务

面向易语言客户端 `类_后端服务` 的卡密授权服务：**Golang 单二进制后端 + Vue 3 管理后台**，单机部署、SQLite 单文件存储、内嵌前端产物，一个二进制即可上线。

- 服务地址：`https://card.hjj888.cc`
- 接口契约（冻结）：[`docs/API.md`](docs/API.md)
- 易语言对接说明：[`docs/e语言对接.md`](docs/e语言对接.md)
- 部署与运维：[`docs/DEPLOY.md`](docs/DEPLOY.md)
- 测试与验收：[`docs/TESTING.md`](docs/TESTING.md)

## 当前部署状态（2026-10-12）

| 项 | 值 |
|---|---|
| 线上地址 | `https://card.hjj888.cc`（管理后台 `/`，公共接口 `/api/*`） |
| 服务器 | `104.143.38.190`（Ubuntu 24.04，与 ReelShort 现网同机，互不影响） |
| 运行方式 | systemd `cardservice.service`，监听 `127.0.0.1:8090`，开机自启 |
| 版本 | `0.1.1`（`/srv/cardservice/current` → `releases/cardservice-0.1.1`） |
| 数据 | `/var/lib/cardservice/cardservice.db`（SQLite + WAL） |
| 备份 | `/var/backups/cardservice/`，每日 04:10（`cardservice-backup.timer`），保留 14 份 |
| TLS | Let's Encrypt `card.hjj888.cc`（certbot 自动续期），Cloudflare 代理 + Full (strict) |
| 管理员 | `admin`（口令见交付说明，**请登录后立即修改**） |

## 功能一览

**公共卡密接口**（易语言客户端调用，全部 POST + JSON）

| 接口 | 用途 |
|---|---|
| `POST /api/verify` | 校验卡密（首次校验绑定机器码并按类型开始计时） |
| `POST /api/card/status` | 查询卡密状态（正常/未绑定/已过期/已禁用/绑定他机） |
| `POST /api/card/rebind` | 换绑机器码（默认每卡 3 次，可后台调整/重置） |
| `POST /api/card/login` | 上线，返回 `session_token` |
| `POST /api/card/heartbeat` | 心跳续期（默认 90 秒超时） |
| `POST /api/card/logout` | 下线（幂等） |

**管理后台**（`/`，管理员账号密码 + HttpOnly Cookie 会话）

- 概览：卡密总量/未激活/激活中/已过期/已禁用/在线、今日激活与校验、近 7 天激活趋势
- 卡密管理：单个/批量生成（≤10000 张）、关键词/状态/类型/批次/日期筛选、禁用/启用、解绑、延期、重置换绑次数、软删除、批量操作、CSV/TXT 导出
- 卡密详情：会话历史 + 操作日志
- 卡密类型：天/周/月/季/年/永久预置，可增删改（时长、默认换绑上限、排序、启停）
- 在线会话：查看与强制下线
- 操作日志 / 审计日志 / 系统设置（心跳、超时、是否允许顶号、默认换绑次数、激活时机、限流参数）

## 快速开始（本地）

```powershell
# 1. 后端单测 + 契约测试
cd card-service
go test ./...

# 2. 前端依赖与构建（会产出 web/dist，被 //go:embed 打进二进制）
cd web; npm install --registry=https://registry.npmmirror.com; npm run build; cd ..

# 3. 本地端到端冒烟（真实二进制 + 临时数据库，36 项断言）
powershell -ExecutionPolicy Bypass -File scripts/smoke-test.ps1

# 4. 本地联调（默认 http://127.0.0.1:8090，前端 dev 用 npm run dev 代理 /api）
$env:CARDSERVICE_DB_PATH="$PWD\data\cardservice.db"
$env:CARDSERVICE_BOOTSTRAP_ADMIN_USERNAME="admin"
$env:CARDSERVICE_BOOTSTRAP_ADMIN_PASSWORD="local-dev-password"
$env:CARDSERVICE_COOKIE_SECURE="false"
go run ./cmd/cardservice
```

## 构建与部署

```powershell
# 构建（前端 + linux/amd64 静态二进制，产物在 dist/）
powershell -ExecutionPolicy Bypass -File scripts/build.ps1 -Version 0.1.0

# 部署（幂等：上传二进制 → 切符号链接 → systemd → nginx → certbot → 健康检查）
$env:CARDSERVICE_DEPLOY_PASSWORD='<SSH 密码>'
python scripts/deploy.py --host 104.143.38.190 --user root --version 0.1.0

# 线上验收（经 Cloudflare 打真实域名）
powershell -ExecutionPolicy Bypass -File scripts/prod-check.ps1 -AdminUser admin -AdminPassword '<后台口令>'
```

## 目录结构

```
card-service/
├─ cmd/cardservice/        服务入口（-version / -backup / -reset-admin-password）
├─ internal/
│  ├─ config/              环境变量配置与校验
│  ├─ store/               SQLite 连接、迁移、事务与全部 SQL
│  ├─ service/             卡密领域逻辑（校验/绑定/激活/换绑/会话/后台操作）
│  ├─ httpapi/             公共卡密接口 + 路由 + 内嵌后台静态资源
│  ├─ adminapi/            管理后台接口（Cookie 会话 + CSRF）
│  ├─ httpx/               JSON 读写、错误映射、真实 IP 解析
│  ├─ security/            卡密生成、令牌哈希、脱敏、限流
│  └─ jstime/              Asia/Shanghai 时间格式化
├─ migrations/             SQLite 迁移（内嵌，启动自动执行）
├─ web/                    Vue 3 + Vite + TS + Element Plus 管理后台
├─ deploy/                 systemd unit、Nginx vhost、安全头片段、环境变量样例
├─ scripts/                build / smoke-test / prod-check / deploy
└─ docs/                   API、易语言对接、部署、测试
```

## 关键设计

- **单二进制**：前端产物通过 `//go:embed` 打进可执行文件，服务器上不需要 Go/Node/Nginx 之外的任何运行时。
- **SQLite + WAL**：写操作统一走 `BEGIN IMMEDIATE` 事务并带 BUSY 退避重试；`VACUUM INTO` 生成一致性备份。
- **会话令牌只存哈希**：客户端拿到的 `session_token` 明文不落库，日志里卡密与机器码均脱敏。
- **真实 IP**：只采信来自可信代理（本机 nginx）的 `X-Forwarded-For`，nginx 侧用 Cloudflare 网段 + `CF-Connecting-IP` 还原客户端 IP，防止伪造头部绕过限流。
- **限流**：IP / 卡密 / 会话三个维度，超限返回 `code="429"`（HTTP 仍为 200，保证易语言客户端能解析）。

## 环境变量

| 变量 | 默认 | 说明 |
|---|---|---|
| `CARDSERVICE_ADDR` | `127.0.0.1:8090` | 监听地址（生产只监听本机，公网入口交给 nginx） |
| `CARDSERVICE_DB_PATH` | `./data/cardservice.db` | SQLite 文件路径 |
| `CARDSERVICE_BOOTSTRAP_ADMIN_USERNAME` | 空 | 首次启动创建的管理员账号（库中已有管理员时忽略） |
| `CARDSERVICE_BOOTSTRAP_ADMIN_PASSWORD` | 空 | 初始口令（≥10 位，首次登录强制修改） |
| `CARDSERVICE_COOKIE_SECURE` | `true` | 后台 Cookie 是否只在 HTTPS 下发送 |
| `CARDSERVICE_TRUSTED_PROXIES` | `127.0.0.1/32,::1/128` | 可信代理 CIDR，列表 |
| `CARDSERVICE_LOG_LEVEL` | `info` | `debug` / `info` / `warn` / `error` |
| `CARDSERVICE_ACCESS_LOG` | `true` | 是否输出访问日志 |
| `CARDSERVICE_RATE_LIMIT_GLOBAL_PER_MINUTE` | `600` | 预留的全局限流阈值 |

## 运维命令

```bash
systemctl status cardservice            # 服务状态
journalctl -u cardservice -n 100        # 或 /var/log/cardservice/*.log
/srv/cardservice/current/cardservice -backup /var/backups/cardservice   # 手动备份
/srv/cardservice/current/cardservice -reset-admin-password admin        # 重置后台口令
curl -s http://127.0.0.1:8090/healthz   # 本机探活
```

## 许可

内部项目，未附开源许可。
