# Auth/User API

当前 App 普通账号使用 `countryCode + phoneNumber + password`。公开注册只模拟短信流程，不创建可登录账号；真正可登录账号由内部接口创建。

## `POST /api/app/auth/sms/send`

公开模拟短信发送接口。仅允许 `PUBLIC_REGISTER` 用途。

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

公开假注册完成接口。验证码固定为 `000000`，通过后只返回模拟完成状态，不创建用户、不返回 Token。

请求：

```json
{
  "countryCode": "+1",
  "phoneNumber": "4155550101",
  "password": "Password123",
  "verificationCode": "000000"
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

内部开户注册接口。必须携带 `X-Internal-Super-Token`。

请求：

```json
{
  "countryCode": "+1",
  "phoneNumber": "4155550101",
  "password": "Password123"
}
```

成功后创建 `ACTIVE` 用户并返回可登录 Token。

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

登录用户发送改密验证码。必须携带 `Authorization: Bearer <token>`，请求体为空。后端只会给当前登录用户手机号发送 `PASSWORD_CHANGE` 模拟验证码。

## `POST /api/app/auth/password/change`

登录用户修改密码。成功后撤销该用户所有未撤销 App Token，客户端需要重新登录。

请求：

```json
{
  "oldPassword": "Password123",
  "newPassword": "NewPassword123",
  "verificationCode": "000000"
}
```

## `POST /api/app/auth/logout`

撤销当前 App Bearer Token。该接口必须携带 `Authorization: Bearer <token>` 请求头。

## 当前约束

- 只支持非中国大陆手机号，拒绝 `+86`。
- 模拟短信验证码固定为 `000000`，有效期 120 秒。
- 同手机号同用途重复发送会失效旧验证码；验证码只能消费一次。
- 密码使用 BCrypt 哈希保存，不保存明文密码。
- 用户状态包含 `ACTIVE`、`DISABLED`、`BLACKLISTED`。
- 当前 Token 为不透明访问令牌；数据库只保存 Token 哈希，不保存原始 Token。
