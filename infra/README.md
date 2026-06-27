# Infra

单机部署基础设施目录，当前使用 Docker Compose 编排以下服务：

- `nginx`：唯一外部 HTTP 入口，托管后台 Web 静态文件并转发 `/api/` 到后端。
- `backend`：Spring Boot 核心业务服务。
- `content-provider`：Flask ReelShort 内容源服务，仅供后端内部访问。
- `postgres`：核心业务数据库。
- `redis`：缓存和短期状态预留。

## 本地启动

```powershell
Copy-Item .env.example .env
docker compose --env-file .env up -d --build
```

默认访问：

- 后台 Web：`http://localhost`
- 后端健康检查：`http://localhost/actuator/health`

## 生产部署注意

- 生产必须替换 `POSTGRES_PASSWORD`、`REELSHORT_ADMIN_PASSWORD_HASH` 和 `REELSHORT_PAYMENT_CALLBACK_SECRET`。
- 本地开发可以暂时留空 `REELSHORT_ADMIN_PASSWORD_HASH`，后端会使用默认管理员密码哈希。
- 默认 Compose 只把 Nginx 暴露到外部；PostgreSQL 和 Redis 的端口映射仅用于单机开发和维护，可按生产网络策略移除。
- Flask 内容源通过 Compose 内部网络访问，不应直接暴露给公网。
