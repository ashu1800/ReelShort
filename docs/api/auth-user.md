# Auth/User API

当前 App 普通账号使用 `username + password`。注册需要先获取一张图形验证码，验证码为 4 位大写字母数字（已排除易混淆的 `I`、`O`、`0`、`1`），有效期 5 分钟、只能使用一次，且**答错立即作废**。

> 历史说明：手机号 + 短信验证码注册、改密短信验证码、AccountManager 供应商短信回调、`account_not_found` 假成功策略，以及内部开户注册接口 `/api/internal/users/register-phone(/batch)`，均已在 2026-07-14 的认证模型重构中删除，代码与配置中都不再存在。`infra/.env.example` 与 `docker-compose.yml` 也不再注入 `REELSHORT_SMS_CALLBACK_*`。

## `GET /api/app/auth/captcha`

生成一张图形验证码，公开接口。响应中的 `imageBase64` 是可直接渲染的 `data:image/png;base64,...`；`captchaId` 是注册时必须回传的挑战标识。

响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "captchaId": "00000000-0000-0000-0000-000000000000",
    "imageBase64": "data:image/png;base64,<png-bytes>"
  },
  "requestId": "uuid",
  "timestamp": "2026-10-12T10:00:00+08:00"
}
```

约束：

- 答案只以 SHA-256 哈希落库（`captcha_challenges.answer_hash`），明文不落库、不回显。
- 校验时对输入做去空白 + 统一大写后同样哈希比较，大小写不敏感。
- 过期挑战由定时任务清理（默认每 `1h` 执行一次，保留期为过期后 1 小时）。
- 该接口受限流规则 `app-auth-captcha` 保护，默认 `10 / 1m`。

## `POST /api/app/auth/register`

公开注册接口。注册成功后直接返回可登录 Token。

请求：

```json
{
  "username": "shortlink_user",
  "password": "Password123",
  "captchaId": "00000000-0000-0000-0000-000000000000",
  "captchaAnswer": "A7K2"
}
```

响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "userId": "00000000-0000-0000-0000-000000000000",
    "username": "shortlink_user",
    "token": "opaque-token",
    "tokenType": "Bearer"
  },
  "requestId": "uuid",
  "timestamp": "2026-10-12T10:00:00+08:00"
}
```

错误：

- `400`：用户名格式不合法、密码少于 8 位、验证码参数缺失，或验证码错误/已过期/已使用（`captcha verification failed`、`captcha expired`）。
- `409`：`username already exists`。

## `POST /api/app/auth/login`

用户名密码登录。

请求：

```json
{
  "username": "shortlink_user",
  "password": "Password123"
}
```

错误：

- `400`：参数缺失或空白。
- `401`：`invalid username or password`。
- `403`：`user disabled`。

服务端先执行一次 BCrypt 比对，再判定账号状态，避免仅凭状态码枚举"该用户名存在且被禁用"。

## `POST /api/app/auth/password/change`

登录用户修改密码。必须携带 `Authorization: Bearer <token>`。新密码至少 8 位；成功后撤销该用户全部未撤销 App Token，客户端需要重新登录。

请求：

```json
{
  "oldPassword": "Password123",
  "newPassword": "NewPassword123"
}
```

错误：

- `401`：`invalid old password`。
- `403`：`user disabled`。
- `429`：凭证锁定期间返回 `too many attempts, try again later`。

## `POST /api/app/auth/logout`

撤销当前 App Bearer Token。该接口必须携带 `Authorization: Bearer <token>` 请求头，返回 `logged out`。

## 当前约束

- 用户名规则：`^[a-zA-Z][a-zA-Z0-9_]{3,19}$`，即 4–20 位、以字母开头、只允许字母/数字/下划线。
- 密码使用 BCrypt 哈希保存，不保存明文密码；注册与改密均要求 8–128 位。
- 用户状态包含 `ACTIVE`、`DISABLED`、`BLACKLISTED`。
- 当前 Token 为不透明访问令牌；数据库只保存 Token 哈希，不保存原始 Token。
- 改密与钱包改绑共用同一份凭证失败计数：默认连续失败 5 次锁定 15 分钟，可通过 `REELSHORT_CREDENTIAL_GUARD_MAX_FAILURES`、`REELSHORT_CREDENTIAL_GUARD_LOCK_DURATION` 调整；锁定期间即使密码正确也返回 `429`。
