# 卡密服务接口契约（冻结版 v1）

本文件是 `card-service` 的**接口契约**，先于实现冻结。任何字段名、类型、状态取值都不得随意变更；新增字段必须是可选且向后兼容的。

- 服务地址：`https://card.hjj888.cc`
- 编码：请求与响应均为 **UTF-8**
- 时间格式：`YYYY-MM-DD HH:MM:SS`（Asia/Shanghai），另有 unix 秒的 `*_ts` 字段
- 命名：全服务统一 `snake_case`

---

## 1. 两类接口的差异（有意设计，不是不一致）

| | 公共卡密接口 `/api/*` | 管理后台接口 `/api/admin/*` |
|---|---|---|
| 消费方 | 易语言客户端 `类_后端服务`（**已固化，不可改**） | 本项目自带 Vue 管理后台 |
| 失败时的 HTTP 状态 | **恒为 200**，靠包络里的 `code` 判断 | 标准语义码 400/401/403/404/409/422/429 |
| 响应形态 | 固定包络 `{code,status,message,...}` | 成功直接返回资源；失败 `{"error":{...}}` |

公共接口之所以业务失败也返回 200，是因为易语言客户端用 `网页_访问_对象` + `Json.解析(Response)` 处理结果，非 200 会走进"服务访问超时"分支，丢失真实错误信息。

---

## 2. 公共卡密接口

全部为 **POST**，请求头 `Content-Type: application/json`，请求体为 JSON 对象。

### 2.1 统一响应包络

成功：

```json
{
  "code": "0",
  "status": "valid",
  "message": "",
  "type": "月卡",
  "expires_at": "2026-11-12 20:00:00",
  "expires_at_ts": 1794494400,
  "permanent": false,
  "remaining_seconds": 2592000,
  "server_time": "2026-10-13 20:00:00"
}
```

**类型约定（易语言读取方式决定，不可改）**

| 字段 | JSON 类型 | 易语言读取 | 说明 |
|---|---|---|---|
| `code` | **字符串** | `Json.取文本("code")` | 必须等于 `"0"` 才算业务成功 |
| `status` | 字符串 | `Json.取文本("status")` | `verify` 成功为 `valid`；`login`/`heartbeat`/`logout` 成功为 `ok` |
| `message` | 字符串 | `Json.取文本("message")` | 面向用户的提示，失败时非空 |
| `type` | 字符串 | `Json.取文本("type")` | 卡密类型名称（如 `月卡`） |
| `expires_at` | 字符串 | `Json.取文本("['expires_at']")` | 正常卡为 `YYYY-MM-DD HH:MM:SS`；永久卡为 `永久有效` |
| `expires_at_ts` | 数字 | — | unix 秒，永久卡为 `0` |
| `permanent` | 布尔 | — | 是否永久卡 |
| `remaining_seconds` | 数字 | — | 剩余秒数，永久卡为 `0` |
| `server_time` | 字符串 | — | 服务器当前时间（上海时区） |

缺失的可选字段一律返回空字符串或 `0`，**不返回 `null`**。

### 2.2 错误码表

| 场景 | `code` | `status` | `message` |
|---|---|---|---|
| 参数缺失/格式错误 | `400` | `invalid` | `参数错误：缺少 code` 等具体原因 |
| 会话无效 | `401` | `invalid_session` | `会话无效，请重新登录` |
| 会话超时 | `401` | `expired` | `会话已超时，请重新登录` |
| 机器码不匹配 | `403` | `bound_other` | `卡密已绑定其他机器` |
| 卡密不存在 | `404` | `not_found` | `卡密不存在` |
| 卡密已被顶号 | `409` | `kicked` | `该卡密已在其他机器登录，本机会话已下线` |
| 换绑次数用尽 | `409` | `rebind_limit` | `换绑次数已用完，请联系客服` |
| 卡密已在线、重复登录被拒 | `409` | `online_other` | `卡密已在线，禁止重复登录（可在后台设置中允许顶号）` |
| 卡密已过期 | `410` | `expired` | `卡密已过期` |
| 卡密已禁用 | `423` | `disabled` | `卡密已被禁用` |
| 请求过于频繁 | `429` | `rate_limited` | `请求过于频繁，请稍后再试` |
| 服务异常 | `500` | `error` | `服务异常，请稍后再试` |

非 POST 方法访问公共接口返回 `405` + 同一包络（`code="405"`, `status="method_not_allowed"`）。

### 2.3 `POST /api/verify` — 校验卡密

请求：

```json
{ "code": "ABCD-EFGH-IJKL-MNPQ", "machine_code": "9f2c4a1b8e7d6530" }
```

成功（`code="0"`, `status="valid"`）：

```json
{
  "code": "0", "status": "valid", "message": "",
  "type": "月卡", "expires_at": "2026-11-12 20:00:00", "expires_at_ts": 1794494400,
  "permanent": false, "remaining_seconds": 2591940,
  "activated_at": "2026-10-13 20:00:00", "rebind_remaining": 3, "online": false,
  "server_time": "2026-10-13 20:01:00"
}
```

行为：
- 首次通过校验时绑定机器码；`settings.activate_on = verify`（默认）时同时开始计时（`expires_at = now + duration_minutes`，`duration_minutes=0` 表示永久）。
- 卡密已绑定其他机器 → 见错误码表（`code="403"`）。
- 无副作用地重复调用是安全的（不会重置有效期）。

### 2.4 `POST /api/card/status` — 查询卡密状态

请求（`machine_code` 可为空字符串，为空时不判断绑定关系）：

```json
{ "code": "ABCD-EFGH-IJKL-MNPQ", "machine_code": "9f2c4a1b8e7d6530" }
```

成功（`code="0"`），`status` 取值：

| `status` | 含义 |
|---|---|
| `valid` | 正常可用（未禁用、未过期；传了机器码时表示机器码匹配） |
| `unbound` | 未绑定机器码（闲置卡） |
| `expired` | 已过期 |
| `disabled` | 已被后台禁用 |
| `bound_other` | 已绑定到其他机器 |

失败：卡密不存在 → `code="404"`, `status="not_found"`。响应额外包含 `type`、`expires_at`、`expires_at_ts`、`permanent`、`remaining_seconds`、`rebind_remaining`、`online`、`server_time`。

### 2.5 `POST /api/card/rebind` — 换绑机器码

请求：

```json
{ "code": "ABCD-EFGH-IJKL-MNPQ", "new_machine_code": "aabbccddeeff0011" }
```

成功：

```json
{ "code": "0", "status": "ok", "success": true, "message": "换绑成功",
  "machine_code": "AABBCCDDEEFF0011", "rebind_remaining": 2,
  "expires_at": "2026-11-12 20:00:00", "server_time": "2026-10-13 20:02:00" }
```

失败：

```json
{ "code": "409", "status": "rebind_limit", "success": false,
  "message": "换绑次数已用完，请联系客服", "rebind_remaining": 0 }
```

**`success` 必须是 JSON 布尔值**（客户端用 `Json.取逻辑值("success")`）。

行为：
- 默认每卡最多换绑 3 次（`settings.default_max_rebinds` 与每张卡的 `max_rebinds` 可覆盖，后台可重置）。
- 新机器码与当前绑定相同时直接成功，**不消耗次数**（`message="机器码未变化，无需换绑"`）。
- 换绑成功会**踢掉当前在线会话**（旧机器下次心跳返回 `409/kicked`），有效期不变。

### 2.6 `POST /api/card/login` — 上线

请求：

```json
{ "code": "ABCD-EFGH-IJKL-MNPQ", "machine_code": "9f2c4a1b8e7d6530" }
```

成功（`code="0"`, `status="ok"`）：

```json
{ "code": "0", "status": "ok", "message": "",
  "session_token": "K7fQ2m...43 字符 base64url",
  "heartbeat_interval": 30, "session_expires_at": "2026-10-13 20:03:30",
  "type": "月卡", "expires_at": "2026-11-12 20:00:00", "expires_at_ts": 1794494400,
  "permanent": false, "remaining_seconds": 2591880, "server_time": "2026-10-13 20:02:00" }
```

行为：
- 先做与 `/api/verify` 完全相同的卡密校验（含首次绑定与激活）。
- **换机必须走 `/api/card/rebind`**：机器码已绑定到其他机器时，登录直接返回 `403/bound_other`，不能用"顶号"绕过——这是反共享的核心约束，与每卡换绑次数配额配套。
- 同一张卡已有在线会话时（客户端重启等重复登录场景）：`settings.allow_takeover = true`（默认）→ 新会话建立、旧会话关闭（同机记为 `close_reason=relogin`，异常跨机记为 `kicked`）；为 `false` → `code="409"`, `status="online_other"`。

### 2.7 `POST /api/card/heartbeat` — 心跳

请求：

```json
{ "session_token": "K7fQ2m...43 字符 base64url" }
```

成功：

```json
{ "code": "0", "status": "ok", "message": "",
  "session_expires_at": "2026-10-13 20:04:00", "heartbeat_interval": 30,
  "expires_at": "2026-11-12 20:00:00", "expires_at_ts": 1794494400,
  "permanent": false, "remaining_seconds": 2591850, "server_time": "2026-10-13 20:03:00" }
```

失败：见错误码表（`401/expired`、`401/invalid_session`、`409/kicked`、`410/expired`、`423/disabled`）。卡密过期或被禁用时服务器会同时关闭该会话。

会话超时时间 = `settings.session_timeout_seconds`（默认 180 秒，即 6 次心跳未到；客户端心跳周期未知时留足容错）。

### 2.8 `POST /api/card/logout` — 下线

请求：

```json
{ "session_token": "K7fQ2m...43 字符 base64url" }
```

成功（幂等，已下线的 token 重复调用仍返回成功）：

```json
{ "code": "0", "status": "ok", "message": "", "server_time": "2026-10-13 20:05:00" }
```

未知 token → `code="401"`, `status="invalid_session"`。

### 2.9 限流

| 维度 | 默认阈值 |
|---|---|
| 单 IP（verify/status/rebind/login 合计） | 30 次/分钟 |
| 单卡密（verify/status/rebind/login 合计） | 60 次/分钟 |
| 单会话（heartbeat） | 120 次/分钟 |
| 单 IP（全部公共接口） | 300 次/分钟 |

超限返回 `code="429"`, `status="rate_limited"`（HTTP 仍为 200，并附带 `Retry-After` 响应头）。

---

## 3. 管理后台接口

### 3.1 鉴权

- 登录成功后下发 Cookie：`card_admin_session`（`HttpOnly`、`Secure`、`SameSite=Strict`、`Path=/`），有效期 `settings.admin_session_hours`（默认 12 小时，滑动续期）。
- 除 `/api/admin/auth/login` 外，所有管理接口都要求有效 Cookie。
- 所有**写操作**（POST/PUT/PATCH/DELETE）必须带请求头 `X-Requested-With: card-admin`，否则 `403 forbidden`（CSRF 防护）。
- 首次管理员（由启动环境变量创建）`must_change_password = true` 时，除 `/auth/me`、`/auth/password`、`/auth/logout` 外的接口返回 `403 password_change_required`。

错误响应：

```json
{ "error": { "code": "invalid_credentials", "message": "账号或密码错误" } }
```

错误码：`invalid_credentials`、`unauthorized`、`forbidden`、`password_change_required`、`not_found`、`conflict`、`validation_error`、`rate_limited`、`internal_error`。

| 接口 | 请求 | 成功响应 |
|---|---|---|
| `POST /api/admin/auth/login` | `{username, password}` | `{"admin": Admin, "expires_at": "..."}` |
| `POST /api/admin/auth/logout` | — | `{"ok": true}` |
| `GET /api/admin/auth/me` | — | `{"admin": Admin}` |
| `PUT /api/admin/auth/password` | `{old_password, new_password}` | `{"ok": true, "message": "密码已修改，请重新登录"}`（吊销全部管理会话） |

`Admin` 对象：`{"id":1,"username":"admin","display_name":"超级管理员","must_change_password":false,"last_login_at":"...","created_at":"..."}`

登录失败 5 次锁定 15 分钟（按用户名+IP），返回 `429 rate_limited`。

### 3.2 数据对象

**Card**

```json
{
  "id": 1,
  "code": "ABCD-EFGH-IJKL-MNPQ",
  "type_id": 3, "type_name": "月卡", "duration_minutes": 43200,
  "state": "active", "display_state": "active",
  "max_rebinds": 3, "rebind_count": 1, "rebind_remaining": 2,
  "bound_machine": "9F2C...", "bound_at": "2026-10-13 20:00:00",
  "activated_at": "2026-10-13 20:00:00",
  "expires_at": "2026-11-12 20:00:00", "permanent": false, "remaining_seconds": 2591940,
  "online": false, "online_machine": "", "online_at": "",
  "batch_no": "B20261013-0001", "remark": "",
  "created_by": "admin", "created_at": "2026-10-13 19:00:00", "updated_at": "2026-10-13 20:00:00"
}
```

- `state`：存储态，`unused` | `active` | `disabled`
- `display_state`：展示态，`unused` | `active` | `expired` | `disabled`
- 时间字段为空时返回 `""`

**Session**：`{"id":1,"card_id":1,"card_code":"ABCD-...","machine_code":"9F2C...","ip":"1.2.3.4","user_agent":"...","created_at":"...","last_heartbeat_at":"...","expires_at":"...","closed_at":"","close_reason":"","active":true}`

`close_reason` ∈ `logout` | `kicked` | `relogin` | `timeout` | `expired` | `disabled` | `admin_kick` | `rebind`

**Log**：`{"id":1,"card_id":1,"code_masked":"ABCD-****-****-MNPQ","action":"verify","result":"ok","message":"","machine_code":"9F2C**6530","ip":"1.2.3.4","created_at":"..."}`

`action` ∈ `verify` | `status` | `rebind` | `login` | `heartbeat` | `logout` | `admin_create` | `admin_disable` | `admin_enable` | `admin_unbind` | `admin_extend` | `admin_reset_rebinds` | `admin_delete` | `admin_kick` | `admin_edit`；`result` ∈ `ok` | `fail`

**分页**：`{"data":[...], "pagination":{"page":1,"page_size":20,"total_items":142,"total_pages":8}}`

### 3.3 卡密

| 接口 | 请求 | 成功响应 |
|---|---|---|
| `GET /api/admin/cards` | query：`keyword`（卡密/机器码/批次/备注模糊）、`state`（`unused`/`active`/`expired`/`disabled`/`online`）、`type_id`、`batch_no`、`created_from`、`created_to`（`YYYY-MM-DD`）、`page`、`page_size`（≤200）、`sort`（`created_at_desc`/`created_at_asc`/`expires_at_asc`） | 分页 `Card` 列表 |
| `POST /api/admin/cards` | `{type_id, count?=1, prefix?="", length?=16, max_rebinds?:null, batch_no?="", remark?=""}` | `{"codes":["ABCD-..."],"batch_no":"B...","count":1}` |
| `POST /api/admin/cards/batch` | 同上，`count` ≤ 10000 | 同上（返回全部卡密，供导出） |
| `GET /api/admin/cards/{id}` | — | `{"card":Card,"sessions":[Session],"logs":[Log]}` |
| `PATCH /api/admin/cards/{id}` | `{remark?, max_rebinds?, expires_at?}`（`expires_at:""` = 永久） | `{"card":Card}` |
| `POST /api/admin/cards/{id}/disable` | — | `{"card":Card}`（同时踢下线） |
| `POST /api/admin/cards/{id}/enable` | — | `{"card":Card}` |
| `POST /api/admin/cards/{id}/unbind` | — | `{"card":Card}`（清空绑定机器码，不重置换绑次数，踢下线） |
| `POST /api/admin/cards/{id}/reset_rebinds` | — | `{"card":Card}` |
| `POST /api/admin/cards/{id}/extend` | `{days?}` 或 `{minutes?}` | `{"card":Card}`（从 `max(now, 现有到期)` 顺延；永久卡不变） |
| `POST /api/admin/cards/batch_action` | `{ids:[1,2], action:"disable"\|"enable"\|"delete"\|"reset_rebinds"\|"extend", days?}` | `{"affected":2}` |
| `DELETE /api/admin/cards/{id}` | — | `{"ok":true}`（软删） |
| `GET /api/admin/cards/export` | query 同列表 + `format=csv\|txt`（默认 csv） | 文件下载；CSV 带 UTF-8 BOM，列为 `卡密,类型,状态,绑定机器码,激活时间,到期时间,换绑次数,批次,备注,创建时间`；txt 每行一个卡密；上限 10 万行 |

生成规则：卡密字母表 `ABCDEFGHJKLMNPQRSTUVWXYZ23456789`（去掉 `I O 0 1` 易混字符），`length` 为总长度（12–32，默认 16），`prefix` 仅允许字母数字并会被大写；展示时每 4 位一组用 `-` 分隔，存储为无分隔规范形。

### 3.4 卡密类型

| 接口 | 请求 | 成功响应 |
|---|---|---|
| `GET /api/admin/card_types` | — | `{"data":[CardType]}` |
| `POST /api/admin/card_types` | `{name, duration_minutes, max_rebinds?, enabled?, sort_order?}` | `{"type":CardType}` |
| `PATCH /api/admin/card_types/{id}` | 同上字段子集 | `{"type":CardType}` |
| `DELETE /api/admin/card_types/{id}` | — | `{"ok":true}`；仍被卡密引用时 `409 conflict` |

`CardType`：`{"id":1,"name":"月卡","duration_minutes":43200,"duration_text":"30 天","max_rebinds":3,"enabled":true,"sort_order":30,"card_count":12,"created_at":"..."}`

`duration_minutes=0` 表示永久。预置：天卡 1440、周卡 10080、月卡 43200、季卡 129600、年卡 525600、永久 0。

### 3.5 会话、日志、统计、设置

| 接口 | 请求 | 成功响应 |
|---|---|---|
| `GET /api/admin/sessions` | `active?`（默认 true）、`card_code?`、`page`、`page_size` | 分页 `Session` 列表 |
| `POST /api/admin/sessions/{id}/kick` | — | `{"ok":true}`（`close_reason=admin_kick`） |
| `GET /api/admin/logs` | `card_code?`、`action?`、`result?`、`from?`、`to?`、`page`、`page_size` | 分页 `Log` 列表 |
| `GET /api/admin/audit_logs` | `admin?`、`action?`、`from?`、`to?`、`page`、`page_size` | 分页 `{"id","username","action","target_type","target_id","detail","ip","created_at"}` |
| `GET /api/admin/stats` | — | 见下 |
| `GET /api/admin/settings` | — | `{"settings":{...}}` |
| `PUT /api/admin/settings` | 设置子集 | `{"settings":{...}}`（写审计日志） |

`GET /api/admin/stats`：

```json
{
  "cards": { "total": 1200, "unused": 900, "active": 250, "expired": 30, "disabled": 20, "online": 12 },
  "today": { "activated": 12, "verified": 345, "logged_in": 20, "rebinds": 2 },
  "trend": [ { "date": "2026-10-07", "activated": 3 } ],
  "server_time": "2026-10-13 20:00:00"
}
```

`settings` 键（类型固定）：

| 键 | 类型 | 默认 | 约束 |
|---|---|---|---|
| `heartbeat_interval_seconds` | 数字 | 30 | 10–300 |
| `session_timeout_seconds` | 数字 | 180 | ≥ 2×心跳间隔，≤ 3600 |
| `allow_takeover` | 布尔 | `true` | — |
| `default_max_rebinds` | 数字 | 3 | 0–100 |
| `activate_on` | 字符串 | `verify` | `verify` \| `login` |
| `log_heartbeat_success` | 布尔 | `false` | — |
| `rate_limit_ip_per_minute` | 数字 | 300 | 10–100000 |
| `rate_limit_card_per_minute` | 数字 | 60 | 5–100000 |
| `rate_limit_session_per_minute` | 数字 | 120 | 5–100000 |
| `admin_session_hours` | 数字 | 12 | 1–168 |

### 3.6 健康检查

`GET /healthz`（无鉴权，供 nginx/systemd 探活）：

```json
{ "status": "ok", "version": "0.1.0", "db": "ok", "uptime_seconds": 1234,
  "server_time": "2026-10-13 20:00:00", "online_sessions": 12 }
```

数据库不可用时返回 `503`，`status="degraded"`, `db="error"`。
