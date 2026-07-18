# Auth Security API

当前文档记录阶段 1 App 普通用户访问令牌鉴权约定。

## 请求头

App 业务接口使用 Bearer Token：

```http
Authorization: Bearer <token>
```

Token 来源于：

- `POST /api/app/auth/register`
- `POST /api/app/auth/login`

Token 默认为 7 天有效期，可通过 `reelshort.auth.session.access-token-ttl` 配置调整。

## 公开接口

以下接口不需要 Token：

- `POST /api/app/auth/register`
- `POST /api/app/auth/login`
- `GET /api/system/health`
- `GET /actuator/health`

`POST /api/app/auth/logout` 属于受保护接口，必须携带当前 Bearer Token。

## 受保护接口

以下接口开始要求普通用户 Token：

- `GET /api/app/content/search`
- `GET /api/app/content/books/{bookId}/episodes`
- `GET /api/app/content/books/{bookId}/episodes/{episodeNum}/play`

后续 `watch`、`points` 等 App 业务接口默认也应纳入 `/api/app/**` 鉴权边界。

## 错误响应

未携带 Token 或 Token 无效：

```json
{
  "code": 401,
  "message": "unauthorized",
  "path": "/api/app/content/search",
  "requestId": "uuid",
  "timestamp": "2026-06-26T17:00:00+08:00"
}
```

Token 有效但用户已禁用：

```json
{
  "code": 403,
  "message": "user disabled",
  "path": "/api/app/content/search",
  "requestId": "uuid",
  "timestamp": "2026-06-26T17:00:00+08:00"
}
```

Token 已过期：

```json
{
  "code": 401,
  "message": "token expired",
  "path": "/api/app/content/search",
  "requestId": "uuid",
  "timestamp": "2026-06-27T14:00:00+08:00"
}
```

Token 已撤销：

```json
{
  "code": 401,
  "message": "token revoked",
  "path": "/api/app/content/search",
  "requestId": "uuid",
  "timestamp": "2026-06-27T14:00:00+08:00"
}
```

## 当前约束

- 数据库只保存 Token 的 SHA-256 哈希，不保存原始 Token。
- App Token 具备过期时间和主动登出撤销能力；`revokedAt` 非空即视为无效。
- 过期或撤销 Token 会由 `AuthSessionCleanupService` 定期清理，默认保留 1 天，可通过 `reelshort.auth.session.cleanup-retention` 调整；清理初始延迟和间隔分别由 `reelshort.auth.session.cleanup-initial-delay`、`reelshort.auth.session.cleanup-interval` 控制。
- 当前 App Token 不提供 refresh token、多设备会话列表或 Redis blacklist；后续可在 `auth/session` 边界内扩展。
- 管理员后台使用独立后台 Token 鉴权，不复用 App Token。
- 已启用 TOTP 的管理员登录必须提交有效 6 位动态码；`/api/admin/2fa/enable` 只能首次启用，换绑必须验证旧码。
- 钱包绑定、更换和解绑属于资金敏感操作，必须提交当前 App 密码；服务端只校验密码，不保存请求明文。
