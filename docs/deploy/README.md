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

## 环境变量

生产必须替换：

- `POSTGRES_PASSWORD`
- `REELSHORT_ADMIN_PASSWORD_HASH`
- `REELSHORT_PAYMENT_CALLBACK_SECRET`

本地开发可以暂时留空 `REELSHORT_ADMIN_PASSWORD_HASH`，后端会使用默认管理员密码哈希。

可按部署环境调整：

- `NGINX_PORT`
- `POSTGRES_PORT`
- `REDIS_PORT`
- `REELSHORT_RATE_LIMIT_STORE`
- `REELSHORT_SITE_URL`
- `REELSHORT_SITE_ID`
- `REELSHORT_REQUEST_TIMEOUT_SECONDS`
- `REELSHORT_JPA_DDL_AUTO`

## 网络边界

- Nginx 是唯一公网入口。
- `backend` 只在 Compose 内部暴露 `8080`。
- `content-provider` 只在 Compose 内部暴露 `5000`。
- PostgreSQL 和 Redis 的宿主机端口映射主要用于单机维护，生产可移除。
- Compose 默认使用 Redis 存储后端限流计数；本地直接运行 Spring Boot 时默认使用内存计数。

## 当前限制

本机当前未安装 Docker，因此本阶段只做 Dockerfile、Compose、Nginx 配置和文档级验证，不执行 `docker compose up`。
