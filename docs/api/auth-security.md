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

## 公开接口

以下接口不需要 Token：

- `POST /api/app/auth/register`
- `POST /api/app/auth/login`
- `GET /api/system/health`
- `GET /actuator/health`

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

## 当前约束

- 数据库只保存 Token 的 SHA-256 哈希，不保存原始 Token。
- 当前 Token 暂不设置过期时间，后续会在会话管理子模块增加过期、撤销和清理策略。
- 当前实现仅覆盖普通 App 用户，不覆盖管理员后台鉴权。
- `/api/admin/**` 在管理员鉴权模块实现前默认拒绝访问；未认证请求返回 `401`。
