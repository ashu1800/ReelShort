# Auth/User API

当前文档记录阶段 1 普通用户账号基础接口。

## `POST /api/app/auth/register`

注册普通 App 用户。

请求：

```json
{
  "username": "alice",
  "password": "Password123"
}
```

响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "userId": "uuid",
    "username": "alice",
    "token": "opaque-access-token",
    "tokenType": "Bearer"
  },
  "requestId": "uuid-or-client-request-id",
  "timestamp": "2026-06-26T16:00:00+08:00"
}
```

错误：

- `400`：用户名或密码为空，或长度不符合要求。
- `409`：用户名已存在。

## `POST /api/app/auth/login`

普通 App 用户登录。

请求：

```json
{
  "username": "alice",
  "password": "Password123"
}
```

响应结构与注册接口一致。

错误：

- `400`：用户名或密码为空。
- `401`：用户名不存在或密码错误。
- `403`：用户已禁用。

## `POST /api/app/auth/logout`

撤销当前 App Bearer Token。该接口必须携带 `Authorization: Bearer <token>` 请求头。

请求体：无。

响应：

```json
{
  "code": 0,
  "message": "success",
  "data": "logged out",
  "requestId": "uuid-or-client-request-id",
  "timestamp": "2026-06-27T14:00:00+08:00"
}
```

错误：

- `401`：未携带 Token、Token 无效、Token 已过期或 Token 已撤销。
- `403`：用户已禁用。

## 当前约束

- 密码使用 BCrypt 哈希保存，不保存明文密码。
- 用户状态当前包含 `ACTIVE` 和 `DISABLED`。
- 当前 Token 为不透明访问令牌结构，客户端通过 `Authorization: Bearer <token>` 访问 App 业务接口。
- 数据库只保存 Token 哈希，不保存原始 Token。
- Token 默认 7 天过期，支持登出撤销；暂不提供 refresh token。
