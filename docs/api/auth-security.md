# Auth Security API

当前文档记录 App 普通用户访问令牌鉴权约定。

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

以下接口不需要 Token（`permitAll`）：

- `POST /api/app/auth/register`
- `POST /api/app/auth/login`
- `GET /api/app/auth/captcha`
- `POST /api/admin/auth/login`
- `POST /api/internal/payments/recharge/callback`
- `GET /api/system/health`
- `GET /actuator/health`

`POST /api/app/auth/logout` 属于受保护接口，必须携带当前 Bearer Token。

## 游客可访问的内容接口

以下接口允许不带 Token 访问，但**如果请求带了无效/过期 Token，仍会返回 401**（鉴权失败不会降级为游客）：

- `GET /api/app/home/recommend`
- `GET /api/app/content/search`
- `GET /api/app/content/shelves/**`
- `GET /api/app/content/books/{bookId}`
- `GET /api/app/content/books/{bookId}/episodes`
- `GET /api/app/social/books/{bookId}/comments`
- `GET /api/app/release/latest`

## 受保护接口

其余 `/api/app/**` 都需要普通用户 Token，包括：

- `GET /api/app/content/books/{bookId}/episodes/{episodeNum}/play`
- `/api/app/watch/**`、`/api/app/points/**`、`/api/app/wallet/**`、`/api/app/withdrawals/**`、`/api/app/orders/**`、`/api/app/vip/**`
- `/api/app/social/**` 中除游客可访问评论列表外的写操作与个人列表

`/api/admin/**` 使用独立的后台 Token，App Token 不能访问。

## 资金敏感接口的密码校验

`PUT /api/app/wallet`（绑定/更换）与 `POST /api/app/wallet/unbind`（解绑）必须提交当前 App 密码；服务端只校验密码，不保存请求明文。

密码错误返回 `403 invalid current password`（**不是 401**，避免客户端把"密码输错"误判为会话过期并弹出登录面板）。

连续失败触发凭证锁定：

- 默认连续失败 5 次锁定 15 分钟，可用 `REELSHORT_CREDENTIAL_GUARD_MAX_FAILURES`、`REELSHORT_CREDENTIAL_GUARD_LOCK_DURATION` 调整。
- 锁定判断优先于密码比对，锁定期间即使密码正确也返回 `429 too many attempts, try again later`；响应不泄露剩余次数或解锁时间。
- 失败计数用独立事务落库，业务事务回滚不会丢失计数；一次成功校验即清空计数。
- `POST /api/app/auth/password/change` 共用同一份计数。

相关接口同时受限流保护（默认 `10 / 1m`），详见 `docs/api/rate-limit.md`。

## 错误响应

未携带 Token 或 Token 无效：

```json
{
  "code": 401,
  "message": "unauthorized",
  "path": "/api/app/points/account",
  "requestId": "uuid",
  "timestamp": "2026-06-26T17:00:00+08:00"
}
```

Token 有效但用户已禁用：

```json
{
  "code": 403,
  "message": "user disabled",
  "path": "/api/app/points/account",
  "requestId": "uuid",
  "timestamp": "2026-06-26T17:00:00+08:00"
}
```

Token 已过期：

```json
{
  "code": 401,
  "message": "token expired",
  "path": "/api/app/points/account",
  "requestId": "uuid",
  "timestamp": "2026-06-27T14:00:00+08:00"
}
```

Token 已撤销：

```json
{
  "code": 401,
  "message": "token revoked",
  "path": "/api/app/points/account",
  "requestId": "uuid",
  "timestamp": "2026-06-27T14:00:00+08:00"
}
```

凭证锁定：

```json
{
  "code": 429,
  "message": "too many attempts, try again later",
  "path": "/api/app/wallet",
  "requestId": "uuid",
  "timestamp": "2026-10-12T10:00:00+08:00"
}
```

## 请求标识

`X-Request-Id` 只接受 `^[A-Za-z0-9_-]{8,64}$`；缺失或不合规时后端重新生成 UUID，避免客户端伪造日志与审计内容。

## 当前约束

- 数据库只保存 Token 的 SHA-256 哈希，不保存原始 Token。
- App Token 具备过期时间和主动登出撤销能力；`revokedAt` 非空即视为无效。
- 过期或撤销 Token 会由 `AuthSessionCleanupService` 定期清理，默认保留 1 天，可通过 `reelshort.auth.session.cleanup-retention` 调整；清理初始延迟和间隔分别由 `reelshort.auth.session.cleanup-initial-delay`、`reelshort.auth.session.cleanup-interval` 控制。
- 当前 App Token 不提供 refresh token、多设备会话列表或 Redis blacklist；后续可在 `auth/session` 边界内扩展。
- 管理员后台使用独立后台 Token 鉴权，不复用 App Token；管理员仅使用账号密码登录，不存在 TOTP/2FA。
- `SecurityConfig` 中针对已删除路径 `/api/app/update/latest` 的 `permitAll` 匹配项属于历史残留（该路径已不再由后端提供，宿主机 Nginx 也不再转发），后续清理时一并删除。
