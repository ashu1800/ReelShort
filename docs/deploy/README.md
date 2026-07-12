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

- `http://localhost/actuator/health`
- `http://localhost`

默认 Compose 不向宿主机发布 PostgreSQL 和 Redis。仅在本机维护或调试需要直接连接数据层时，使用 loopback-only override：

```powershell
docker compose --env-file .env -f docker-compose.yml -f docker-compose.local-debug.yml up -d --build
```

## 环境变量

生产必须替换：

- `POSTGRES_PASSWORD`
- `REELSHORT_ADMIN_PASSWORD_HASH`
- `REELSHORT_PAYMENT_CALLBACK_SECRET`

Compose 对 `POSTGRES_PASSWORD` 使用 required 插值，未设置时拒绝解析部署配置；不得在生产配置中恢复开发默认密码。

本地开发可以暂时留空 `REELSHORT_ADMIN_PASSWORD_HASH`，后端会使用默认管理员密码哈希。

可按部署环境调整：

- `NGINX_PORT`
- `REELSHORT_RATE_LIMIT_STORE`
- `REELSHORT_SITE_URL`
- `REELSHORT_SITE_ID`
- `REELSHORT_REQUEST_TIMEOUT_SECONDS`
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

`POSTGRES_PORT` 和 `REDIS_PORT` 只供 `docker-compose.local-debug.yml` 使用；调试端口固定绑定 `127.0.0.1`，不得改为公网地址。

`REELSHORT_CONTENT_VIDEO_FALLBACK_TTL` 控制播放地址缓存兜底窗口，默认 `10m`。播放地址正常路径始终按需向上游获取，缓存只在内容源 5xx 且未超过该 TTL 时兜底；设置为 `0` 可禁用兜底。

## 数据库迁移

后端数据库 schema 由 Flyway 管理，迁移脚本位于 `backend/src/main/resources/db/migration`。后端启动时会自动执行未应用的迁移，然后由 JPA 校验实体和数据库结构是否一致。

默认配置为：

- `spring.flyway.enabled=true`
- `spring.jpa.hibernate.ddl-auto=validate`

`REELSHORT_JPA_DDL_AUTO` 仅作为开发/应急覆盖项保留，长期部署不要改回 `update`，后续表结构变化应新增 `V2__*.sql`、`V3__*.sql` 等迁移脚本。

## 备份与恢复

长期单机部署必须定期备份 PostgreSQL 业务数据和部署配置。备份脚本位于 `infra/scripts/backup.ps1`，恢复脚本位于 `infra/scripts/restore.ps1`，详细步骤见 `docs/deploy/backup-restore.md`。

恢复数据库前必须先停止 backend 和 nginx，避免恢复过程中继续写入。Redis 只保存缓存、限流计数和短期状态，不作为核心持久化数据恢复对象。

## 网络边界

- Nginx 是唯一公网入口。
- `backend` 只在 Compose 内部暴露 `8080`。
- `content-provider` 只在 Compose 内部暴露 `5000`。
- PostgreSQL 和 Redis 不发布宿主机端口，只通过 Compose 内部网络访问。
- `/api/internal` 及 `/api/internal/` 下的所有路径在公网 Nginx 层直接返回 404；内部运营、开户注册和支付回调必须通过可信内网路径访问 backend。
- Compose 默认使用 Redis 存储后端限流计数；本地直接运行 Spring Boot 时默认使用内存计数。

## 当前限制

本机当前未安装 Docker，因此本阶段只做 Dockerfile、Compose、Nginx 配置和文档级验证，不执行 `docker compose up`。
