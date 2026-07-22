# Infra

单机部署基础设施目录，当前使用 Docker Compose 编排以下服务：

- `nginx`：唯一外部 HTTP 入口，托管后台 Web，并转发 `/api/` 与旧版 ShortLink Android 下载路径到后端。
- `backend`：Spring Boot 核心业务服务。
- `content-provider`：Flask ReelShort 内容源服务，仅供后端内部访问。
- `postgres`：核心业务数据库。
- `redis`：缓存和短期状态，当前用于 Docker Compose 部署下的后端限流计数。

## 本地启动

```powershell
Copy-Item .env.example .env
docker compose --env-file .env up -d --build
```

默认访问：

- 后台 Web：`http://localhost`
- 后端健康检查：`http://localhost/actuator/health`

## Android 更新文件

发布文件由本地脚本上传到腾讯云 COS，并由后端写入 `app_releases` 表：

- 新版版本清单：`/api/app/release/latest`，直接返回 COS 预签名下载 URL。
- 旧版兼容清单：`/api/app/update/latest`，返回 `/downloads/android/ShortLink-vX.Y.Z.apk[.sha256]` 稳定 URL。
- 旧版下载路径：`/downloads/android/ShortLink-vX.Y.Z.apk[.sha256]`，由 Nginx 转发到后端，后端 302 到 COS 预签名 URL。

COS 下载链接短时过期；旧版稳定路径只承担兼容跳转，不保存 APK 文件。

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

备份文件默认写入 `infra/backups/`，该目录已被 Git 忽略。完整流程和恢复演练清单见 `docs/deploy/backup-restore.md`。

当前开发机没有 Docker 时，可在仓库根目录执行静态校验：

```powershell
powershell -ExecutionPolicy Bypass -File infra/scripts/verify-backup-scripts.ps1
```

## 生产部署注意

- Compose 强制要求 `POSTGRES_PASSWORD`，且生产必须替换 `.env.example` 中的占位值；同时必须配置 `REELSHORT_ADMIN_PASSWORD_HASH` 和 `REELSHORT_PAYMENT_CALLBACK_SECRET`。
- 固定开发管理员和支付密钥只允许用于显式 `app-dev` 或测试 profile；通过生产 Compose 启动时，管理员哈希或支付密钥缺失、使用已知开发值或强度不足都会拒绝启动。
- 默认 Compose 只把 Nginx 暴露到外部；PostgreSQL 和 Redis 仅在 Compose 内部网络可达。宿主机调试必须显式叠加 `docker-compose.local-debug.yml`，且端口只绑定 `127.0.0.1`。
- Nginx 将 `/api/internal` 和 `/api/internal/` 下的路径透传到 backend；这些接口必须依赖 `X-Internal-Super-Token`、支付回调密钥等后端接口级鉴权，公网调用方不得记录或泄露内部密钥。
- Android 发布目录由无 sudo 的 `shortlink-release` 用户写入，Nginx 仅以只读方式挂载；正式签名密钥不得上传服务器。
- Flask 内容源通过 Compose 内部网络访问，不应直接暴露给公网。
- Compose 默认设置 `REELSHORT_RATE_LIMIT_STORE=redis`，后端通过内部服务名 `redis` 访问 Redis；本地直接运行后端时默认仍使用内存限流。
