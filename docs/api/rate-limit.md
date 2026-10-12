# Rate Limit API

当前文档记录后端限流规则。限流在 Spring Boot 内执行，客户端仍只接收统一 API 错误结构。

## 默认保护范围

| Rule | Method | Path | 默认窗口 | 覆盖环境变量 |
| --- | --- | --- | --- | --- |
| `app-auth` | `POST` | `/api/app/auth/**` | `20 / 1m` | `REELSHORT_RATE_LIMIT_APP_AUTH_LIMIT` / `_WINDOW` |
| `app-auth-captcha` | `GET` | `/api/app/auth/captcha` | `10 / 1m` | `REELSHORT_RATE_LIMIT_APP_CAPTCHA_LIMIT` / `_WINDOW` |
| `admin-auth` | `POST` | `/api/admin/auth/login` | `10 / 1m` | `REELSHORT_RATE_LIMIT_ADMIN_AUTH_LIMIT` / `_WINDOW` |
| `app-content-read` | `GET` | `/api/app/content/**` | `120 / 1m` | `REELSHORT_RATE_LIMIT_APP_CONTENT_LIMIT` / `_WINDOW` |
| `app-home-read` | `GET` | `/api/app/home/**` | `120 / 1m` | `REELSHORT_RATE_LIMIT_APP_HOME_LIMIT` / `_WINDOW` |
| `app-watch-progress` | `POST` | `/api/app/watch/progress` | `240 / 1m` | `REELSHORT_RATE_LIMIT_APP_WATCH_LIMIT` / `_WINDOW` |
| `app-points-read` | `GET` | `/api/app/points/**` | `120 / 1m` | `REELSHORT_RATE_LIMIT_APP_POINTS_LIMIT` / `_WINDOW` |
| `app-release-read` | `GET` | `/api/app/release/**` | `60 / 1m` | `REELSHORT_RATE_LIMIT_APP_RELEASE_LIMIT` / `_WINDOW` |
| `app-vip-order-create` | `POST` | `/api/app/vip/orders` | `5 / 1m` | `REELSHORT_RATE_LIMIT_APP_VIP_ORDER_LIMIT` / `_WINDOW` |
| `app-wallet-write` | `POST` | `/api/app/wallet/**` | `10 / 1m` | `REELSHORT_RATE_LIMIT_APP_WALLET_LIMIT` / `_WINDOW` |
| `app-wallet-bind` | `PUT` | `/api/app/wallet` | `10 / 1m` | `REELSHORT_RATE_LIMIT_APP_WALLET_BIND_LIMIT` / `_WINDOW` |
| `app-withdrawal-create` | `POST` | `/api/app/withdrawals` | `5 / 1m` | `REELSHORT_RATE_LIMIT_APP_WITHDRAWAL_LIMIT` / `_WINDOW` |
| `app-social-write` | `POST` | `/api/app/social/**` | `30 / 1m` | `REELSHORT_RATE_LIMIT_APP_SOCIAL_LIMIT` / `_WINDOW` |
| `admin-vip-mutation` | `POST` | `/api/admin/vip/orders/**` | `20 / 1m` | `REELSHORT_RATE_LIMIT_ADMIN_VIP_LIMIT` / `_WINDOW` |

规则与默认值定义在 `backend/src/main/resources/application.properties` 的 `reelshort.rate-limit.rules[n].*`。

## Key 策略

- 已认证 App 请求按 `APP:<userId>` 限流。
- 已认证 Admin 请求按 `ADMIN:<username>` 限流。
- 未认证请求按客户端 IP 限流。
- 当 `remoteAddr` 命中可信代理白名单且存在 `X-Forwarded-For` 时，从右向左选择首个非代理 IP；否则使用 `remoteAddr`。

## 可信代理白名单

```properties
reelshort.rate-limit.trusted-proxies=${REELSHORT_TRUSTED_PROXIES:}
```

- 逗号分隔，支持**精确 IP 与 CIDR**（例如 `172.18.0.0/16`）。
- 默认空，仅信任 loopback。
- 前缀过宽会被拒绝：IPv4 前缀小于 `/8`、IPv6 前缀小于 `/16` 时**启动直接失败**，避免用 `0.0.0.0/0` 让任意客户端伪造 `X-Forwarded-For`。
- 白名单未覆盖实际代理地址时，`X-Forwarded-For` 会被整条忽略，并只打一次 WARN。此时**全部匿名客户端共用一个限流键**，单个客户端即可打满全平台登录配额。

> 生产 Docker Compose 中该键为必填：`REELSHORT_TRUSTED_PROXIES: ${REELSHORT_TRUSTED_PROXIES:?...}`。容器内 `remoteAddr` 是 Docker 网关地址而非 loopback，漏配会让限流塌缩为全站单桶，必须在 `infra/.env` 填 Nginx 所在 Docker 子网（通常形如 `172.18.0.0/16`）。

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

凭证锁定（改密、钱包改绑密码连续失败）返回的是 `429 too many attempts, try again later`，与限流响应文案不同，详见 `docs/api/auth-security.md`。

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

默认规则可通过上表的环境变量覆盖限制值和窗口，例如：

```properties
REELSHORT_RATE_LIMIT_APP_AUTH_LIMIT=20
REELSHORT_RATE_LIMIT_APP_AUTH_WINDOW=1m
```

Docker Compose 默认将 `REELSHORT_RATE_LIMIT_STORE` 设为 `redis`，并把后端连接到 Compose 内部的 `redis` 服务。即使 Redis 缓存丢失，限流窗口也只会重置，不影响 PostgreSQL 中的核心业务数据。
