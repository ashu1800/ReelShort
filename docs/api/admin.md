# Admin API

当前文档记录阶段 1 后台管理基础接口。后台接口使用独立管理员 Bearer Token，不能复用 App 用户 Token。

管理员账号配置：

- `reelshort.admin.username`：默认 `admin`。
- `reelshort.admin.password-hash`：默认对应测试密码 `Admin123`，生产部署必须通过环境变量覆盖。
- `reelshort.admin.token-ttl`：默认 `8h`。

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

## 鉴权

除登录接口外，所有后台接口都需要：

```http
Authorization: Bearer <admin-token>
```

普通 App Token 不能访问 `/api/admin/**`。后台 Token 只用于后台接口。

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

错误：

- `400`：请求字段缺失、UUID 或状态格式不合法。
- `401`：未提供有效后台 Token，或使用了 App Token。
- `404`：用户不存在。
