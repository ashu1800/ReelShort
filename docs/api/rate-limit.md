# Rate Limit API

当前文档记录后端限流基础能力。限流在 Spring Boot 内执行，客户端仍只接收统一 API 错误结构。

## 默认保护范围

| Rule | Method | Path | 默认窗口 |
| --- | --- | --- | --- |
| `app-auth` | `POST` | `/api/app/auth/**` | `20 / 1m` |
| `admin-auth` | `POST` | `/api/admin/auth/login` | `10 / 1m` |
| `app-content-read` | `GET` | `/api/app/content/**` | `120 / 1m` |
| `app-home-read` | `GET` | `/api/app/home/**` | `120 / 1m` |
| `app-watch-progress` | `POST` | `/api/app/watch/progress` | `240 / 1m` |
| `app-points-read` | `GET` | `/api/app/points/**` | `120 / 1m` |

## Key 策略

- 已认证 App 请求按 `APP:<userId>` 限流。
- 已认证 Admin 请求按 `ADMIN:<username>` 限流。
- 未认证请求按客户端 IP 限流。
- 请求来自本机、内网或链路本地代理地址时，若存在 `X-Forwarded-For`，从右向左选择首个非代理 IP；否则使用 `remoteAddr`。

## 超限响应

```json
{
  "code": 429,
  "message": "too many requests",
  "path": "/api/app/auth/login",
  "requestId": "uuid",
  "timestamp": "2026-06-26T19:00:00+08:00"
}
```

响应头包含：

```http
Retry-After: 60
```

## 配置

限流总开关：

```properties
reelshort.rate-limit.enabled=true
```

限流计数存储：

```properties
reelshort.rate-limit.store=memory
```

可选值：

- `memory`：默认值，使用单机内存计数，适合本地开发和测试。
- `redis`：使用 Redis 计数，适合 Docker Compose 单机部署。Redis 连接使用 Spring Boot 标准配置，例如 `spring.data.redis.host` 和 `spring.data.redis.port`。

默认规则可通过环境变量覆盖限制值和窗口，例如：

```properties
REELSHORT_RATE_LIMIT_APP_AUTH_LIMIT=20
REELSHORT_RATE_LIMIT_APP_AUTH_WINDOW=1m
```

Docker Compose 默认将 `REELSHORT_RATE_LIMIT_STORE` 设为 `redis`，并把后端连接到 Compose 内部的 `redis` 服务。即使 Redis 缓存丢失，限流窗口也只会重置，不影响 PostgreSQL 中的核心业务数据。
