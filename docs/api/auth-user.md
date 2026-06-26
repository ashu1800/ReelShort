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

## 当前约束

- 密码使用 BCrypt 哈希保存，不保存明文密码。
- 用户状态当前包含 `ACTIVE` 和 `DISABLED`。
- 当前 Token 为第一阶段不透明访问令牌结构，后续可替换为 JWT；客户端只依赖 `token` 和 `tokenType` 字段。
- 本阶段尚未实现全局鉴权过滤器，后续 App 业务接口保护会在 `auth/security` 子模块补齐。
