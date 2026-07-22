# Auth/User API

当前 App 普通账号使用 `countryCode + phoneNumber + password`。公开注册只完成验证码流程，不创建可登录账号；真正可登录账号由内部接口创建。验证码由 ShortLink 生成随机 6 位数字，并通过 AccountManager 供应商短信回调写入验证码查询系统。

## `POST /api/app/auth/sms/send`

公开短信发送接口。仅允许 `PUBLIC_REGISTER` 用途。发送成功的前提是 AccountManager 回调成功。
如果 AccountManager 返回 `404 account_not_found`，ShortLink 会对 App 返回成功并让客户端进入 120 秒倒计时，但本次本地验证码会立即作废，后续注册提交任意验证码都会返回 `invalid verification code`。
除 `account_not_found` 外的回调失败仍返回业务错误。

请求：

```json
{
  "purpose": "PUBLIC_REGISTER",
  "countryCode": "+1",
  "phoneNumber": "4155550101"
}
```

响应：

```json
{
  "code": 0,
  "message": "success",
  "data": { "expiresInSeconds": 120 }
}
```

错误：

- `400`：非注册用途、`+86`、手机号格式错误或参数缺失。

## `POST /api/app/auth/register`

公开假注册完成接口。验证码为最近一次发送成功的 6 位数字，通过后只返回模拟完成状态，不创建用户、不返回 Token。注册密码至少 8 位。

请求：

```json
{
  "countryCode": "+1",
  "phoneNumber": "4155550101",
  "password": "Password123",
  "verificationCode": "123456"
}
```

响应：

```json
{
  "code": 0,
  "message": "success",
  "data": { "status": "SIMULATED" }
}
```

## `POST /api/internal/users/register-phone`

内部开户注册接口。必须携带 `X-Internal-Super-Token`。该接口兼容单账号和批量账号两种请求体。

请求：

```json
{
  "countryCode": "+1",
  "phoneNumber": "4155550101",
  "password": "Password123"
}
```

成功后创建 `ACTIVE` 用户并返回可登录 Token。

批量请求：

```json
{
  "accounts": [
    { "countryCode": "+1", "phoneNumber": "4155550101", "password": "Password123" },
    { "countryCode": "+44", "phoneNumber": "7400123456", "password": "Password123" }
  ]
}
```

批量响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "total": 2,
    "successCount": 1,
    "failureCount": 1,
    "results": [
      {
        "index": 0,
        "success": true,
        "countryCode": "+1",
        "phoneNumber": "4155550101",
        "userId": "00000000-0000-0000-0000-000000000000",
        "username": "+14155550101",
        "phoneE164": "+14155550101",
        "token": "opaque-token",
        "tokenType": "Bearer",
        "errorCode": null,
        "message": null
      },
      {
        "index": 1,
        "success": false,
        "countryCode": "+44",
        "phoneNumber": "7400123456",
        "userId": null,
        "username": null,
        "phoneE164": null,
        "token": null,
        "tokenType": null,
        "errorCode": "PHONE_ALREADY_EXISTS",
        "message": "phone already exists"
      }
    ]
  }
}
```

批量接口逐条处理账号。某个手机号重复或国家区号不支持时只标记该条失败，不影响同批其他账号创建。请求 JSON 结构错误、账号列表为空或超过 100 个时返回 `400 bad request`。

## `POST /api/internal/users/register-phone/batch`

内部批量开户注册别名接口。请求体和响应体与 `/api/internal/users/register-phone` 的批量形态一致。

示例响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "total": 2,
    "successCount": 1,
    "failureCount": 1,
    "results": [
      {
        "index": 0,
        "success": true,
        "countryCode": "+1",
        "phoneNumber": "4155550101",
        "userId": "00000000-0000-0000-0000-000000000000",
        "username": "+14155550101",
        "phoneE164": "+14155550101",
        "token": "opaque-token",
        "tokenType": "Bearer",
        "errorCode": null,
        "message": null
      },
      {
        "index": 1,
        "success": false,
        "countryCode": "+44",
        "phoneNumber": "7400123456",
        "userId": null,
        "username": null,
        "phoneE164": null,
        "token": null,
        "tokenType": null,
        "errorCode": "PHONE_ALREADY_EXISTS",
        "message": "phone already exists"
      }
    ]
  }
}
```

## `POST /api/app/auth/login`

手机号密码登录。

请求：

```json
{
  "countryCode": "+1",
  "phoneNumber": "4155550101",
  "password": "Password123"
}
```

错误：

- `400`：国家区号、手机号或密码参数错误。
- `401`：手机号不存在或密码错误。
- `403`：用户已禁用或拉黑。

## `POST /api/app/auth/password/verification/send`

登录用户发送改密验证码。必须携带 `Authorization: Bearer <token>`，请求体为空。后端只会给当前登录用户手机号发送 `PASSWORD_CHANGE` 验证码。

## `POST /api/app/auth/password/change`

登录用户修改密码。新密码至少 8 位；成功后撤销该用户所有未撤销 App Token，客户端需要重新登录。

请求：

```json
{
  "oldPassword": "Password123",
  "newPassword": "NewPassword123",
  "verificationCode": "123456"
}
```

## `POST /api/app/auth/logout`

撤销当前 App Bearer Token。该接口必须携带 `Authorization: Bearer <token>` 请求头。

## 当前约束

- 只支持非中国大陆手机号，拒绝 `+86`。
- 验证码为随机 6 位数字，有效期 120 秒；发送依赖 AccountManager 供应商短信回调成功，不做本地可用验证码兜底。
- AccountManager `404 account_not_found` 是未售出手机号兼容策略：发送接口对 App 假成功，但验证码不可用，验证阶段仍失败。
- AccountManager 回调超时由 `REELSHORT_SMS_CALLBACK_TIMEOUT` 控制，默认 `5s`。
- 同手机号同用途重复发送会失效旧验证码；验证码只能消费一次。
- 密码使用 BCrypt 哈希保存，不保存明文密码。
- 注册和修改密码的长度均不得少于 8 位；已有较短密码账号仍可登录，但改密后必须满足该规则。
- 用户状态包含 `ACTIVE`、`DISABLED`、`BLACKLISTED`。
- 当前 Token 为不透明访问令牌；数据库只保存 Token 哈希，不保存原始 Token。
