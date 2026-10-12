# Infra

单机部署基础设施目录，当前使用 Docker Compose 编排以下服务：

- `nginx`：唯一外部 HTTP 入口，托管后台 Web，并转发 `/api/` 到后端。
- `backend`：Spring Boot 核心业务服务。
- `content-provider`：Flask ReelShort 内容源服务，仅供后端内部访问。
- `postgres`：核心业务数据库。
- `redis`：缓存和短期状态，当前用于 Docker Compose 部署下的后端限流计数。

所有服务都配置了 `restart: unless-stopped`，并通过 `logging.options` 把容器日志限制为 `max-size=10m`、`max-file=3`（默认 `json-file` 驱动下每容器最多 30 MiB）。

## 本地启动

```powershell
Copy-Item .env.example .env
docker compose --env-file .env up -d --build
```

检查服务状态和健康检查输出：

```powershell
docker compose --env-file .env ps
docker compose --env-file .env exec backend curl -fsS http://127.0.0.1:8080/actuator/health
```

默认访问：

- 后台 Web：`http://localhost`
- 后端健康检查：只能在容器内直连后端，见上一条命令。

`admin-web/nginx.conf` 只代理 `/api/`，其余路径走 SPA 兜底，因此从宿主机请求 `http://localhost/actuator/health` 会返回后台 `index.html`（HTTP 200），**不能**据此判断后端是否存活。nginx 容器的 healthcheck 直接探测 `http://backend:8080/actuator/health`：镜像为 `nginx:1.27-alpine`，自带 busybox `wget`（无 `curl`）；busybox `wget` 对非 2xx 返回非 0 退出码，后端 DOWN 时 Spring Boot 返回 503，判定有效。

不启动容器也可以校验 Compose 语法（需要本机有 Docker；`--env-file` 必须指向一份填好必填键的 env 文件）：

```powershell
docker compose --env-file .env.example -f docker-compose.yml config --quiet
```

## Android 更新文件

发布文件由本地脚本上传到腾讯云 COS，并由后端写入 `app_releases` 表：

- 版本清单：`/api/app/release/latest`，直接返回 COS 预签名下载 URL。

COS 下载链接短时过期；旧版 `/api/app/update/latest`、`/downloads/android/` 兼容链路和 GitHub Releases 方案均已删除，服务器上不再需要 APK 静态目录或只读挂载。

需要从宿主机直接连接 PostgreSQL 或 Redis 调试时，显式叠加仅绑定 loopback 的 override：

```powershell
docker compose --env-file .env -f docker-compose.yml -f docker-compose.local-debug.yml up -d --build
```

## 备份与恢复

备份 PostgreSQL 和部署配置：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/backup.ps1
```

恢复指定备份前必须停止 backend/nginx 写入：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/restore.ps1 -BackupDir backups/YYYYMMDD-HHMMSS -ConfirmRestore
```

生产数据库转储不得存放在仓库内：备份文件默认写入 `infra/backups/`，该目录与 `infra/restored-config/` 都已被 `.gitignore` 忽略；历史遗留的根目录 `reelshort-prod-db-backup-*` 已迁移进 `infra/backups/`，后续只允许通过 `infra/scripts/backup.ps1` 产生备份。完整流程和恢复演练清单见 `docs/deploy/backup-restore.md`。

当前开发机没有 Docker 时，可在仓库根目录执行静态校验：

```powershell
powershell -ExecutionPolicy Bypass -File infra/scripts/verify-backup-scripts.ps1
```

## 生产部署注意

- Compose 强制要求 `POSTGRES_PASSWORD`、`REELSHORT_PAYMENT_CALLBACK_SECRET` 和 `REELSHORT_TRUSTED_PROXIES`；生产必须替换 `.env.example` 中的占位值，并额外配置 `REELSHORT_ADMIN_PASSWORD_HASH`。
- `REELSHORT_TRUSTED_PROXIES` 是后端限流采纳 `X-Forwarded-For` 的白名单（逗号分隔，支持精确 IP 与 CIDR）。容器内 `remoteAddr` 是 Docker 网关地址而非 loopback，未配置时后端会整条忽略 XFF，所有匿名客户端共用同一个限流键，单个客户端即可打满全平台登录配额。生产填 Nginx 所在的 Docker 子网，用下面两条命令确认实际取值（项目名默认取 `infra` 目录，网络通常是 `infra_default`）：
  ```bash
  docker inspect reelshort-nginx --format '{{range $k, $v := .NetworkSettings.Networks}}{{$k}} {{$v.IPAddress}}{{"\n"}}{{end}}'
  docker network inspect infra_default --format '{{(index .IPAM.Config 0).Subnet}}'
  ```
- `REELSHORT_WITHDRAWAL_PAYOUT_ENABLED` 默认 `false`，自动打款执行能力保持关闭；重新启用签名/广播前必须显式改为 `true`，并同步确认热钱包地址白名单与权限审计。
- `CONTENT_PROVIDER_INTERNAL_TOKEN` 是后端与 content-provider 之间的内部共享密钥，两个服务必须配置为同一取值；留空表示不启用该校验，公网不得访问 content-provider。
- 固定开发管理员和支付密钥只允许用于显式 `app-dev` 或测试 profile；通过生产 Compose 启动时，管理员哈希或支付密钥缺失、使用已知开发值或强度不足都会拒绝启动。
- 默认 Compose 只把 Nginx 暴露到外部；PostgreSQL 和 Redis 仅在 Compose 内部网络可达。宿主机调试必须显式叠加 `docker-compose.local-debug.yml`，且端口只绑定 `127.0.0.1`。
- Nginx 将 `/api/internal` 和 `/api/internal/` 下的路径透传到 backend；这些接口必须依赖 `X-Internal-Super-Token`、支付回调密钥等后端接口级鉴权，公网调用方不得记录或泄露内部密钥。
- `backend/Dockerfile` 以非 root 账号 `reelshort` 运行，并预先创建 `/app/logs`（`REELSHORT_LOG_FILE` 默认 `logs/backend.log`）。`admin-web/Dockerfile` 仍以 nginx 镜像默认用户运行，建议后续同样切换到非 root（需同时调整 `/var/cache/nginx`、`/var/run` 等路径权限），该改动不在本次范围内。
- Flask 内容源通过 Compose 内部网络访问，不应直接暴露给公网。
- Compose 默认设置 `REELSHORT_RATE_LIMIT_STORE=redis`，后端通过内部服务名 `redis` 访问 Redis；本地直接运行后端时默认仍使用内存限流。
- 内容源与内容缓存当前只支持英文：`REELSHORT_CONTENT_REFRESH_LOCALES` 默认 `en`；写入多个 locale 只会让同一货架按同一语言被重复刷新，不会产生第二套缓存。
- 旧的 AccountManager 短信回调配置（`REELSHORT_..._CALLBACK_*` 一组五个键）与繁中片库关键词扩展配置（`REELSHORT_CATALOG_SEARCH_KEYWORDS_...` 的繁中变体）已删除：后端与内容源都没有对应实现，Compose 不再透传，`.env` 里的残留同名键会被忽略，可直接清理。

## Compose 透传边界

只有 `docker-compose.yml` 中显式列出的 `${...}` 键会被注入容器。以下后端变量当前**未透传**，在 `.env` 里设置不会生效；需要时先补 Compose 透传再改 `.env`：

- 数据库连接池与日志：`REELSHORT_DB_POOL_MAXIMUM_SIZE`、`REELSHORT_LOG_FILE`、`REELSHORT_SYSTEM_LOG_ROOT`、`REELSHORT_SYSTEM_LOG_MAX_LINES`、`REELSHORT_SYSTEM_LOG_MAX_BYTES`。
- 会话、凭证锁定与限流细项：`REELSHORT_AUTH_SESSION_*`、`REELSHORT_ADMIN_SESSION_*`、`REELSHORT_CREDENTIAL_GUARD_MAX_FAILURES`、`REELSHORT_CREDENTIAL_GUARD_LOCK_DURATION`、`REELSHORT_RATE_LIMIT_ENABLED` 及全部 `REELSHORT_RATE_LIMIT_*_LIMIT`/`_WINDOW`（含新增的验证码、钱包、提现、社交规则）。
- 打款任务节奏：`REELSHORT_WITHDRAWAL_PAYOUT_CONFIRMATION_INITIAL_DELAY`（默认 `24h`）。
- 链上节点与合约：`REELSHORT_TRON_NODE_URL`、`REELSHORT_TRON_USDT_CONTRACT`、`REELSHORT_TRON_REQUIRED_CONFIRMATIONS`、`REELSHORT_ETH_NODE_URL`、`REELSHORT_ETH_API_KEY`、`REELSHORT_ETH_USDT_CONTRACT`、`REELSHORT_ETH_CHAIN_ID`、`REELSHORT_BSC_NODE_URL`、`REELSHORT_BSC_API_KEY`、`REELSHORT_BSC_USDT_CONTRACT`、`REELSHORT_BSC_CHAIN_ID`。
