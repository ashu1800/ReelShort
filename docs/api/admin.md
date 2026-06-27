# Admin API

当前文档记录阶段 1 后台管理基础接口。后台接口使用独立管理员 Bearer Token，不能复用 App 用户 Token。

## 管理员账号与 RBAC

后台管理员账号、角色和权限持久化在 PostgreSQL 中。启动时后端会根据配置引导默认管理员：

- `reelshort.admin.username`：默认 `admin`。
- `reelshort.admin.password-hash`：默认对应测试密码 `Admin123`，生产部署必须通过环境变量覆盖。
- `reelshort.admin.token-ttl`：默认 `8h`。
- `reelshort.admin.session.cleanup-retention`：后台过期/撤销 Token 清理保留期，默认 `1d`。
- `reelshort.admin.session.cleanup-initial-delay`：后台 Token 清理任务首次延迟，默认 `1h`。
- `reelshort.admin.session.cleanup-interval`：后台 Token 清理任务间隔，默认 `1h`。

默认管理员会绑定 `SUPER_ADMIN` 角色，并获得当前所有后台权限。以上配置只用于默认管理员引导和 Token 有效期，不再表示后台系统只能存在一个管理员账号。

当前权限代码：

- `DASHBOARD_READ`：读取后台控制台摘要。
- `USER_READ`：读取用户、用户详情、观看记录和积分流水。
- `USER_WRITE`：变更用户状态。
- `POINTS_ADJUST`：后台调整用户积分。
- `AUDIT_READ`：读取后台审计日志。
- `CONTENT_CACHE_READ`：读取内容缓存状态。
- `CONTENT_CACHE_WRITE`：刷新内容缓存。
- `SYSTEM_CONFIG_READ`：读取系统配置。
- `SYSTEM_CONFIG_WRITE`：更新系统配置。
- `SYSTEM_RUNTIME_READ`：读取后台运行诊断。
- `SYSTEM_LOG_READ`：读取后端应用日志。
- `ORDER_READ`：读取充值订单。
- `PAYMENT_EVENT_READ`：读取支付回调事件。

## `POST /api/admin/auth/login`

管理员登录。

请求：

```json
{
  "username": "admin",
  "password": "Admin123"
}
```

响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "username": "admin",
    "token": "opaque-token",
    "tokenType": "Bearer"
  },
  "requestId": "uuid",
  "timestamp": "2026-06-26T18:00:00+08:00"
}
```

## `POST /api/admin/auth/logout`

撤销当前后台 Bearer Token。该接口必须携带 `Authorization: Bearer <admin-token>` 请求头。

请求体：无。

响应：

```json
{
  "code": 0,
  "message": "success",
  "data": "logged out",
  "requestId": "uuid",
  "timestamp": "2026-06-27T14:30:00+08:00"
}
```

登出后同一后台 Token 继续访问 `/api/admin/**` 会返回：

```json
{
  "code": 401,
  "message": "token revoked",
  "path": "/api/admin/users",
  "requestId": "uuid",
  "timestamp": "2026-06-27T14:30:00+08:00"
}
```

## 鉴权

除登录接口外，所有后台接口都需要：

```http
Authorization: Bearer <admin-token>
```

普通 App Token 不能访问 `/api/admin/**`。后台 Token 只用于后台接口。

后台接口会校验接口所需权限。管理员已登录但缺少权限时返回 `403 forbidden`。后台 Token 已过期时返回 `401 token expired`，已登出撤销时返回 `401 token revoked`。

## `GET /api/admin/dashboard/summary`

返回后台控制台聚合摘要，需要 `DASHBOARD_READ` 权限。

响应数据：

```json
{
  "users": {
    "total": 10,
    "disabled": 1
  },
  "orders": {
    "total": 5,
    "created": 2,
    "paid": 3,
    "totalAmountCents": 9900
  },
  "payments": {
    "total": 4,
    "processed": 3,
    "rejected": 1
  },
  "content": {
    "bookCount": 120,
    "episodeCacheCount": 80,
    "shelfCount": 3
  },
  "auditLogs": {
    "latest": []
  }
}
```

## `GET /api/admin/users`

返回用户列表，按创建时间倒序排列。

列表项字段：

- `id`
- `username`
- `status`
- `pointBalance`
- `createdAt`

## `GET /api/admin/users/{userId}`

返回用户详情。

详情字段：

- `id`
- `username`
- `status`
- `pointBalance`
- `watchRecordCount`
- `pointRecordCount`
- `createdAt`

## `POST /api/admin/users/{userId}/status`

变更用户状态。

请求：

```json
{
  "status": "DISABLED"
}
```

允许状态：

- `ACTIVE`
- `DISABLED`

用户禁用后，既有 App Token 后续访问 `/api/app/**` 会返回 `403`。

该操作会写入后台审计日志，动作为 `USER_STATUS_CHANGED`。

## `POST /api/admin/users/{userId}/points/adjust`

后台调整用户积分。

请求：

```json
{
  "amount": 10,
  "reason": "manual campaign grant"
}
```

规则：

- `amount` 不能为 `0`，可为正数或负数。
- 调整后余额不能小于 `0`。
- `reason` 必填，最长 255。
- 成功后生成 `ADMIN_ADJUSTMENT` 积分流水，并写入后台审计日志 `POINTS_ADJUSTED`。

## `GET /api/admin/users/{userId}/watch-records`

返回指定用户观看记录，按更新时间倒序排列。

## `GET /api/admin/users/{userId}/point-records`

返回指定用户积分流水，按创建时间倒序排列。

## `GET /api/admin/audit-logs`

返回后台操作审计日志，按创建时间倒序排列。

日志字段：

- `id`
- `adminUsername`
- `action`
- `targetType`
- `targetId`
- `summary`
- `createdAt`

## `GET /api/admin/orders`

返回所有用户充值订单，按创建时间倒序排列。详见 `docs/api/orders.md`。

## `GET /api/admin/system/configs`

返回后台支持的系统配置。详见 `docs/api/system-config.md`。

## `POST /api/admin/system/configs/{configKey}`

更新后台系统配置。成功后写入审计日志 `SYSTEM_CONFIG_UPDATED`。

## `GET /api/admin/system/runtime`

返回后端运行诊断摘要，需要 `SYSTEM_RUNTIME_READ` 权限。该接口只返回脱敏后的运行状态，不返回数据库 URL、账号密码、Redis 地址、环境变量或异常堆栈。

响应数据：

```json
{
  "status": "DEGRADED",
  "checkedAt": "2026-06-27T09:30:00Z",
  "application": {
    "service": "reelshort-backend",
    "version": "0.0.1-SNAPSHOT",
    "javaVersion": "17.0.12",
    "uptimeSeconds": 3600
  },
  "memory": {
    "usedBytes": 12345678,
    "maxBytes": 536870912
  },
  "dependencies": [
    {
      "name": "database",
      "status": "UP",
      "detail": "validated"
    },
    {
      "name": "redis",
      "status": "DOWN",
      "detail": "unavailable"
    },
    {
      "name": "content-provider",
      "status": "UP",
      "detail": "reachable"
    }
  ]
}
```

总体状态规则：

- `UP`：所有依赖检查均为 `UP`。
- `DEGRADED`：至少一个依赖检查为 `DOWN`。

依赖异常不会导致接口整体失败；接口仍返回 `200` 和统一成功响应，具体异常由 `dependencies[].status` 和脱敏 `detail` 表达。

## `GET /api/admin/system/logs`

返回后端应用日志最近内容，需要 `SYSTEM_LOG_READ` 权限。该接口只读取 `reelshort.system.logs.root` 配置目录下的普通 `.log` 文件，不接受绝对路径、子目录路径、`..` 路径或非 `.log` 文件。

查询参数：

- `file`：可选，日志文件名，例如 `backend.log`；未传时读取可用文件列表中的第一个文件。
- `lines`：可选，读取最近多少行；默认 `200`，最大受 `reelshort.system.logs.max-lines` 限制，默认 `500`。

后端只读取日志文件尾部内容，单次读取字节数受 `reelshort.system.logs.max-bytes` 限制，默认 `1048576`。

响应数据：

```json
{
  "files": ["backend.log"],
  "selectedFile": "backend.log",
  "requestedLines": 200,
  "lineCount": 2,
  "truncated": false,
  "updatedAt": "2026-06-27T09:30:00Z",
  "lines": [
    "2026-06-27 INFO started",
    "2026-06-27 INFO ready"
  ]
}
```

当日志目录不存在或没有可读 `.log` 文件时，接口返回空 `files` 和空 `lines`。非法文件名返回 `400 bad request`，不会泄漏服务器实际路径。

## `GET /api/admin/content/cache`

返回内容缓存状态。详见 `docs/api/content-cache.md`。

## `POST /api/admin/content/cache/shelves/{shelfType}/refresh`

刷新指定内容货架缓存。成功后写入审计日志 `CONTENT_CACHE_REFRESHED`。

错误：

- `400`：请求字段缺失、UUID 或状态格式不合法。
- `401`：未提供有效后台 Token，或使用了 App Token。
- `403`：后台账号已登录但缺少接口所需权限。
- `404`：用户不存在。
