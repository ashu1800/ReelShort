# ReelShort 单机部署说明

## 目标形态

ReelShort 长期按单机部署设计，不引入 K8s 或微服务治理。单机内运行：

- Nginx
- Spring Boot backend
- Vue Admin 静态站点
- Flask content-provider
- PostgreSQL
- Redis

外部用户只访问 Nginx。后台 Web 的 `/api/` 请求由 Nginx 转发到 Spring Boot。Spring Boot 再访问 PostgreSQL、Redis 和 Flask 内容源。

## Docker Compose 启动

进入 `infra` 目录：

```powershell
Copy-Item .env.example .env
notepad .env
docker compose --env-file .env up -d --build
```

检查服务：

```powershell
docker compose --env-file .env ps
docker compose --env-file .env logs -f backend
```

健康检查：

```powershell
docker compose --env-file .env ps
docker compose --env-file .env exec backend curl -fsS http://127.0.0.1:8080/actuator/health
```

`admin-web/nginx.conf` 只代理 `/api/`，其余路径走 SPA 兜底，因此宿主机访问 `http://localhost/actuator/health` 会返回后台 `index.html`（HTTP 200），**不能**据此判断后端存活。nginx 容器的 healthcheck 直连后端容器 `http://backend:8080/actuator/health`（镜像自带 busybox `wget`，非 2xx 返回非 0 退出码，后端 DOWN 时 Spring Boot 返回 503），因此 `docker compose ps` 中的 healthy 才代表后端可用。

默认 Compose 不向宿主机发布 PostgreSQL 和 Redis。仅在本机维护或调试需要直接连接数据层时，使用 loopback-only override：

```powershell
docker compose --env-file .env -f docker-compose.yml -f docker-compose.local-debug.yml up -d --build
```

## 环境变量

生产必须替换：

- `POSTGRES_PASSWORD`
- `REELSHORT_ADMIN_PASSWORD_HASH`
- `REELSHORT_PAYMENT_CALLBACK_SECRET`
- `REELSHORT_TRUSTED_PROXIES`

Compose 对 `POSTGRES_PASSWORD`、`REELSHORT_PAYMENT_CALLBACK_SECRET` 和 `REELSHORT_TRUSTED_PROXIES` 使用 required 插值，未设置时拒绝解析部署配置；不得在生产配置中恢复开发默认密码。

`REELSHORT_TRUSTED_PROXIES` 是后端限流采纳 `X-Forwarded-For` 的可信代理白名单（逗号分隔，支持精确 IP 与 CIDR）。容器内 `remoteAddr` 是 Docker 网关地址而非 loopback，未配置时后端会整条忽略 XFF，所有匿名客户端共用同一个限流键，单个客户端即可打满全平台登录配额。生产填 Nginx 所在的 Docker 子网：

```bash
docker network inspect infra_default --format '{{(index .IPAM.Config 0).Subnet}}'
```

生产运行时后端会校验管理员 BCrypt 哈希和支付回调密钥；缺失、使用已知开发值或不满足强度要求都会拒绝启动。开发默认值只位于显式 `app-dev` 和测试 profile，生产 Compose 不提供回退值。

可按部署环境调整：

- `NGINX_PORT`
- `REELSHORT_RATE_LIMIT_STORE`
- `CONTENT_PROVIDER_INTERNAL_TOKEN`
- `REELSHORT_WITHDRAWAL_PAYOUT_ENABLED`
- `REELSHORT_SITE_URL`
- `REELSHORT_SITE_ID`
- `REELSHORT_REQUEST_TIMEOUT_SECONDS`
- `REELSHORT_MAX_RESPONSE_BYTES`
- `REELSHORT_MAX_REDIRECTS`
- `REELSHORT_CATALOG_SEARCH_KEYWORDS`
- `REELSHORT_CATALOG_MAX_PAGES_PER_KEYWORD`
- `REELSHORT_CATALOG_MAX_BOOKS`
- `REELSHORT_CATALOG_REQUEST_WORKERS`
- `REELSHORT_CONTENT_REFRESH_ENABLED`
- `REELSHORT_CONTENT_REFRESH_INITIAL_DELAY`
- `REELSHORT_CONTENT_REFRESH_INTERVAL`
- `REELSHORT_CONTENT_REFRESH_SHELVES`
- `REELSHORT_CONTENT_REFRESH_LOCALES`
- `REELSHORT_CONTENT_VIDEO_FALLBACK_TTL`
- `REELSHORT_JPA_DDL_AUTO`

`CONTENT_PROVIDER_INTERNAL_TOKEN` 是后端与 content-provider 之间的内部共享密钥（请求头 `X-Content-Provider-Token`），两个服务必须配置为同一取值；留空表示不启用该校验，公网不得访问 content-provider。

`REELSHORT_WITHDRAWAL_PAYOUT_ENABLED` 默认 `false`，自动打款执行能力保持关闭；重新启用签名/广播前必须显式改为 `true`，并同步确认热钱包地址白名单与权限审计。

`REELSHORT_CONTENT_REFRESH_LOCALES` 默认 `en`。内容源当前只支持英文单语，写入多个 locale 只会让同一货架按同一语言被重复刷新，不会产生第二套缓存。

`POSTGRES_PORT` 和 `REDIS_PORT` 只供 `docker-compose.local-debug.yml` 使用；调试端口固定绑定 `127.0.0.1`，不得改为公网地址。

`REELSHORT_REQUEST_TIMEOUT_SECONDS` 是单次上游调用的总时间边界；`REELSHORT_MAX_RESPONSE_BYTES` 默认限制响应体为 `5242880` 字节，`REELSHORT_MAX_REDIRECTS` 默认最多允许 `3` 次重定向。内容源会逐跳校验公网目标，禁止重定向到私网、回环或链路本地地址。

`REELSHORT_CONTENT_VIDEO_FALLBACK_TTL` 控制播放地址缓存兜底窗口，默认 `10m`。播放地址正常路径始终按需向上游获取，缓存只在内容源 5xx 且未超过该 TTL 时兜底；设置为 `0` 可禁用兜底。

## 数据库迁移

后端数据库 schema 由 Flyway 管理，迁移脚本位于 `backend/src/main/resources/db/migration`。后端启动时会自动执行未应用的迁移，然后由 JPA 校验实体和数据库结构是否一致。

默认配置为：

- `spring.flyway.enabled=true`
- `spring.jpa.hibernate.ddl-auto=validate`

`REELSHORT_JPA_DDL_AUTO` 仅作为开发/应急覆盖项保留，长期部署不要改回 `update`，后续表结构变化应新增 `V2__*.sql`、`V3__*.sql` 等迁移脚本。当前最新迁移为 `V31__review_remediation.sql`（纯新增列/索引与约束放宽，无数据删除）。

## 备份与恢复

长期单机部署必须定期备份 PostgreSQL 业务数据和部署配置。备份脚本位于 `infra/scripts/backup.ps1`，恢复脚本位于 `infra/scripts/restore.ps1`，详细步骤见 `docs/deploy/backup-restore.md`。

生产数据库转储**不得存放在仓库根目录**：备份默认写入 `infra/backups/`，该目录与 `infra/restored-config/` 都已被 `.gitignore` 忽略，且备份目录在 Windows 上会移除继承 ACL、仅授予当前用户完全控制。`-OutputRoot` 必须是仓库内的相对路径，传入绝对路径或 `..` 逃逸会被直接拒绝（保留期清理使用递归强删，必须先约束根目录）。

脚本默认只备份数据库和非秘密部署快照，不复制 `.env`。配置备份必须在 Windows 上显式启用 DPAPI `CurrentUser` 加密，且只能由同一用户上下文恢复；非 Windows 主机应使用外部密钥管理或加密备份系统。任何配置恢复后都必须轮换全部恢复出的密钥。

恢复数据库前必须先停止 backend 和 nginx，避免恢复过程中继续写入。Redis 只保存缓存、限流计数和短期状态，不作为核心持久化数据恢复对象。

## 服务运行基线

- 所有服务均配置 `restart: unless-stopped`，并通过 `logging.options` 把容器日志限制为 `max-size=10m`、`max-file=3`（默认 `json-file` 驱动下每容器最多 30 MiB）。
- `backend` 容器以非 root 账号 `reelshort` 运行，日志目录 `/app/logs`（`REELSHORT_LOG_FILE` 默认 `logs/backend.log`，相对 `WORKDIR /app`）。
- `content-provider` 容器以 `nobody` 运行，由 gunicorn 承载（`-w 2 --threads 8 -t 30`），不再使用 Werkzeug 开发服务器。
- `admin-web` 容器仍以 nginx 镜像默认用户运行，尚未非 root 化。

## 网络边界

- Nginx 是唯一公网入口。
- `backend` 只在 Compose 内部暴露 `8080`。
- `content-provider` 只在 Compose 内部暴露 `5000`。
- PostgreSQL 和 Redis 不发布宿主机端口，只通过 Compose 内部网络访问。
- `/api/internal` 及 `/api/internal/` 下的所有路径由公网 Nginx 透传到 backend；内部运营与支付回调必须依赖 `X-Internal-Super-Token`、支付回调密钥等后端接口级鉴权。内部运营接口不提供开户注册能力（认证模型已收敛为用户名 + 密码 + 图形验证码）。
- Compose 默认使用 Redis 存储后端限流计数；本地直接运行 Spring Boot 时默认使用内存计数。

## 当前限制

本机当前未安装 Docker，因此本阶段只做 Dockerfile、Compose、Nginx 配置和文档级验证，不执行 `docker compose up`。
