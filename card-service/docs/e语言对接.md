# 易语言客户端对接说明（`类_后端服务` 对照）

本服务完全按你给出的 `类_后端服务` 代码实现，**客户端无需任何修改**：把 `集_服务地址` 继续指向 `https://card.hjj888.cc` 即可。

## 1. 逐方法对照

| 易语言方法 | 请求 | 服务端行为 | 客户端读取的关键字段 |
|---|---|---|---|
| `校验卡密(卡密, 机器码, 错误信息)` | `POST /api/verify` `{code, machine_code}` | 卡密存在性/禁用/过期检查；首次校验绑定机器码并按类型开始计时 | `code`（字符串，`"0"` 成功）、`status` 必须为 `valid`、`type`、`['expires_at']` |
| `卡密状态(卡密, 机器码, 卡密状态, 错误信息)` | `POST /api/card/status` `{code, machine_code}` | 只查询不修改 | `code` = `"0"` 时读 `status`：`valid`/`unbound`/`expired`/`disabled`/`bound_other` |
| `卡密换绑(卡密, 机器码, 错误信息)` | `POST /api/card/rebind` `{code, new_machine_code}` | 消耗 1 次换绑（默认每卡 3 次，后台可调/可重置），踢掉在线会话，有效期不变 | `success`（**JSON 布尔**，`Json.取逻辑值`）、`message` |
| `上线(卡密, 机器码, 错误信息)` | `POST /api/card/login` `{code, machine_code}` | 同校验卡密；建立会话并返回令牌；默认允许重复登录顶掉旧会话 | `code`=`"0"` 且 `status`=`ok`、`['session_token']` |
| `心跳(心跳通过, 错误信息)` | `POST /api/card/heartbeat` `{session_token}` | 滑动续期（默认 90 秒超时） | `code`=`"0"` 时 `心跳通过 = (status = "ok")` |
| `下线(错误信息)` | `POST /api/card/logout` `{session_token}` | 关闭会话，幂等 | `code`=`"0"` 且 `status`=`ok` |

## 2. 为什么某些字段必须是字符串/布尔

`类_json` 的读取方式决定了服务端的 JSON 类型，**这是硬约束，不要改服务端类型**：

| 字段 | JSON 类型 | 客户端代码 | 说明 |
|---|---|---|---|
| `code` | **字符串** | `Json.取文本("code") = "0"` | 若返回数字 `0`，部分版本的 `类_json` 取文本会得到空串，判定直接失败 |
| `status` | 字符串 | `Json.取文本("status")` | `verify` 成功必须是 `valid`；`login`/`heartbeat`/`logout` 成功必须是 `ok` |
| `success` | **布尔** | `Json.取逻辑值("success")` | 返回字符串 `"true"` 会取到假 |
| `expires_at` | 字符串 | `Json.取文本("['expires_at']")` | 正常卡 `2026-11-12 20:00:00`（北京时间）；永久卡为 `永久有效`（可直接显示） |
| `type` | 字符串 | `Json.取文本("type")` | 卡密类型名称，如 `月卡` |
| `session_token` | 字符串 | `Json.取文本("['session_token']")` | 43 字符 base64url，服务端只存哈希 |

补充：未使用的字段一律返回空字符串或 `0`，**不会返回 `null`**（`null` 在易语言里会取到空值，容易掩盖问题）。

## 3. 完整调用时序（建议）

```text
启动
 ├─ 校验卡密(卡密, 机器码) → 假 → 提示 错误信息，退出
 ├─ (可选) 卡密状态(卡密, 机器码) → 显示 "剩余 X 天"
 ├─ 上线(卡密, 机器码) → 假 → 提示 错误信息
 └─ 定时器每 30 秒：心跳(心跳通过) → 假 → 判定掉线，重新走"校验卡密 + 上线"

用户点击"换机"：
 └─ 卡密换绑(卡密, 新机器码) → 真 → 重新"上线"
退出程序：
 └─ 下线()
```

## 4. 各接口的失败语义（`错误信息` 会拿到什么）

| 场景 | `code` | `status` | 典型 `message` |
|---|---|---|---|
| 卡密不存在 | `404` | `not_found` | 卡密不存在 |
| 卡密已过期 | `410` | `expired` | 卡密已过期 |
| 卡密被后台禁用 | `423` | `disabled` | 卡密已被禁用 |
| 机器码不匹配 | `403` | `bound_other` | 卡密已绑定其他机器 |
| 换绑次数用尽 | `409` | `rebind_limit` | 换绑次数已用完，请联系客服 |
| 会话被顶号 | `409` | `kicked` | 该卡密已在其他机器登录，本机会话已下线 |
| 会话超时 | `401` | `expired` | 会话已超时，请重新登录 |
| 会话无效/已下线 | `401` | `invalid_session` | 会话无效，请重新登录 |
| 请求过于频繁 | `429` | `rate_limited` | 请求过于频繁，请稍后再试 |
| 服务内部异常 | `500` | `error` | 服务异常，请稍后再试 |

**注意**：以上失败都返回 HTTP 200 + JSON 包络，所以 `网页_访问_对象` 的返回值能被 `Json.解析` 正常处理，`错误信息` 也是真实的业务提示；只有网络层故障（DNS/超时/断网）才会返回空串，此时客户端显示"服务访问超时"。

## 5. 换机规则（重要）

- 卡密**首次校验（或首次上线）时绑定机器码**，之后只有该机器码能通过校验与上线。
- **换机器必须调用 `卡密换绑`**：默认每张卡 3 次（可在后台按卡或按类型调整，也可重置）。
- 换绑会立即把旧机器的会话踢下线，旧机器下一次心跳会拿到 `409 / kicked`，客户端据此回到"重新校验"流程。
- 后台可随时"解绑机器码"（不消耗用户次数），用于客服场景。

## 6. 自测

服务端上线后，用下面命令可以在**不改易语言代码**的前提下验证 6 个接口（请求形态与 `网页_访问_对象` 完全一致）：

```powershell
# 只探活
powershell -ExecutionPolicy Bypass -File scripts/prod-check.ps1

# 用管理员口令自动生成一张测试卡并跑完整流程
powershell -ExecutionPolicy Bypass -File scripts/prod-check.ps1 -AdminUser admin -AdminPassword '<后台口令>'

# 用已有卡密跑完整流程
powershell -ExecutionPolicy Bypass -File scripts/prod-check.ps1 -CardCode 'ABCD-EFGH-IJKL-MNPQ'
```

也可以直接 curl（等价于客户端调用）：

```bash
curl -s https://card.hjj888.cc/api/verify \
  -H "Content-Type: application/json" \
  -d '{"code":"ABCD-EFGH-IJKL-MNPQ","machine_code":"9f2c4a1b8e7d6530"}'
```

## 7. 心跳与超时参数

| 参数 | 默认值 | 位置 |
|---|---|---|
| 心跳间隔 | 30 秒（服务端下发给客户端参考，客户端保持原定时器即可） | 后台「系统设置」 |
| 会话超时 | 180 秒（连续 6 次心跳未到达才判定掉线，避免客户端心跳周期偏长被误踢） | 后台「系统设置」 |
| 限流 | 单 IP 300 次/分钟、单卡密 60 次/分钟、单会话 120 次/分钟 | 后台「系统设置」 |
