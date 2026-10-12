# Withdrawal API

提现仅支持用户绑定的 ERC20 钱包。用户提交申请时冻结积分；管理员在外部钱包完成 ERC20 USDT 转账后，通过后台**人工确认**扣除冻结积分并写入积分流水。系统不接收、不保存热钱包私钥，不签名、不广播；只有在管理员显式发起"作废卡死 attempt"时才会做一次只读链上核对。

> 自动打款执行能力默认关闭（`reelshort.withdrawal.payout.enabled` / `REELSHORT_WITHDRAWAL_PAYOUT_ENABLED`，默认 `false`）。关闭时 `WithdrawalPayoutCoordinator`、`WithdrawalPayoutTransactionService`、`WithdrawalPayoutConfirmationService` 这三个 Bean 不装配，但 `TronClient`/`EthereumClient`/`BscClient` 的签名与广播代码仍在类路径中，属于本轮未清理的剩余项。

## 换算规则

默认配置：

- `withdraw.usdt-per-50-points=0.14`，即 50 积分 = 0.14 USDT。
- `withdraw.minimum-usdt=10`。
- `withdraw.fee-percent=10`，手续费积分从用户提交的提现积分中扣除。

手续费积分为 `ceil(pointAmount * feePercent / 100)`，实际可提现积分为 `pointAmount - feePoints`。到账金额为 `可提现积分 * usdtPer50Points / 50`，始终向下截断到 2 位小数，不四舍五入。实际到账必须至少达到 `withdraw.minimum-usdt`；默认配置下最低到账为 `10 USDT`，最低提现积分为 3969 积分。

## App 接口

### `GET /api/app/withdrawals/summary`

返回余额、冻结积分、可用积分、动态最低提现积分、`usdtPer50Points`、`usdtPerPoint`、`minimumUsdt=10`、手续费百分比和钱包地址，不返回人民币或美元换算字段。

### `GET /api/app/withdrawals`

返回当前用户的提现申请，按创建时间倒序排列。

### `POST /api/app/withdrawals`

请求只提交 `pointAmount`：

```json
{ "pointAmount": 4000 }
```

可选请求头：

```http
Idempotency-Key: <客户端生成的唯一字符串，最长 128>
```

规则：

- 后端按当前配置计算手续费和 2 位 USDT 金额，保存 `usdt_per_50_points` 快照并冻结用户提交的全部积分。
- 只有当前绑定 ERC20 钱包的用户可以创建申请；历史非 ERC20 钱包必须重新绑定 ERC20 后再次申请。
- **同一用户同时只允许一笔 `PENDING`**：已存在待处理申请时返回 `409 withdrawal already pending`。
- 带 `Idempotency-Key` 时，同一用户重复提交同一键直接返回首次创建的申请，不会二次冻结；该键已被其他用户使用时返回 `409 idempotency key is already used`；键超过 128 字符返回 `400`。
- 当前 Android 客户端未发送 `Idempotency-Key`，重复提交保护由"单笔 `PENDING`"约束承担。

### 钱包写操作

`PUT /api/app/wallet` 请求必须包含 `network=ERC20`、`walletAddress` 和当前 `password`；`walletAddress` 必须是 `0x` + 40 位十六进制且不能是全零地址。`POST /api/app/wallet/unbind` 请求必须包含当前 `password`。密码错误返回 `403 invalid current password`，凭证锁定期间返回 `429 too many attempts, try again later`。

`POST /api/app/wallet/bank-card` **已下线**：固定返回 `410 bank card withdrawal is not supported`，不再接收卡号、有效期或 CVV，服务端也不再校验 Luhn/CVV 或落库后四位。

## 后台提现管理

### `GET /api/admin/withdrawals`

要求 `WITHDRAWAL_READ`。返回提现申请与历史 payout attempt 状态；历史 attempt 仅用于查看，不会继续自动处理。已由外部转账确认的申请返回 `payoutStatus=MANUAL_CONFIRMED`。

### `POST /api/admin/withdrawals/{id}/manual-confirm`

要求 `WITHDRAWAL_WRITE`。该接口没有请求体；管理员须已完成后台登录，不要求再次输入验证码。

仅可确认 `PENDING` 的 ERC20 申请；重复确认幂等。成功后在**同一事务**内：

1. 扣除该申请的全部冻结积分；
2. 写入一条 `WITHDRAWAL` 积分流水（金额为负数、带扣减后余额快照、幂等键 `withdrawal:<申请 ID>`，重复确认不会写入第二条）；
3. 将申请标记为 `APPROVED`；
4. 写入 `WITHDRAWAL_MANUAL_CONFIRMED` 审计记录。

管理员必须在调用前完成外部钱包转账；该接口不接收或校验交易哈希，不签名、不广播、不核验链上交易。

存在 `SIGNING`/`PREPARED`/`BROADCASTED` 的 `ACTIVE` attempt 时返回 `409 automatic payout attempt is still active`，需要先作废该 attempt。

### `POST /api/admin/withdrawals/{id}/reject`

要求 `WITHDRAWAL_WRITE`。拒绝待处理申请并释放冻结积分；存在 `ACTIVE` attempt 时返回 `409 withdrawal payout is active`。

### `POST /api/admin/withdrawals/{id}/payout-attempt/void`

要求 `WITHDRAWAL_WRITE`。作废卡死的 `ACTIVE` 打款尝试，解除"既不能确认也不能拒绝"的死结。请求体：

```json
{ "reason": "遗留自动打款 attempt，链上查无交易" }
```

`reason` 必填，最长 255 字符。行为：

- `SIGNING` / `PREPARED`：尚未广播，直接标记 `VOIDED` 并释放 `active_slot`。
- `BROADCASTED`：先做**只读**链上核对（不签名、不广播）。确认数达到该网络要求（`REELSHORT_TRON_REQUIRED_CONFIRMATIONS` / `REELSHORT_ETH_REQUIRED_CONFIRMATIONS` / `REELSHORT_BSC_REQUIRED_CONFIRMATIONS`）时转为 `MANUAL_REVIEW`，由管理员走人工确认；链上查无或交易失败才作废；链上状态未定（`PENDING`/`UNKNOWN`）返回 `409 payout chain state is not final yet; retry later`。
- `MANUAL_REVIEW`：返回 `409 payout attempt is already in manual review; use manual confirmation`。
- 该接口在打款执行能力关闭时仍可用；全部路径写入 `WITHDRAWAL_PAYOUT_ATTEMPT_VOIDED` 或 `WITHDRAWAL_PAYOUT_ATTEMPT_MANUAL_REVIEW` 审计。
- 作废只释放 attempt 槽位并记录原因/操作人，**不会自动释放冻结积分**；积分释放仍需管理员显式调用 `reject`。

### `GET /api/admin/withdrawals/stats?range=TODAY`

要求 `WITHDRAWAL_READ`。仅统计 `ERC20` 且 `APPROVED` 的提现，时间字段使用 `reviewedAt`，按 `Asia/Shanghai` 使用 `[from, to)` 边界。

合法 `range`：`TODAY`、`YESTERDAY`、`THIS_WEEK`（周一开始）、`THIS_MONTH`、`LAST_MONTH`、`CUSTOM`。响应包含统计区间、`payoutCount` 和 `totalUsdt`。

自定义区间需要同时传 `fromDate` 与 `toDate`（`yyyy-MM-dd`）：

```http
GET /api/admin/withdrawals/stats?range=CUSTOM&fromDate=2026-09-01&toDate=2026-09-30
```

日期缺失返回 `400 custom withdrawal stats dates are required`，格式错误返回 `400 custom withdrawal stats dates must use yyyy-MM-dd`，结束日期早于开始日期返回 `400 custom withdrawal stats end date must not be before start date`。

## 旧自动打款接口

`POST /api/admin/withdrawals/{id}/approve`、`POST /api/admin/withdrawals/batch-preview` 和 `POST /api/admin/withdrawals/batch-approve` 已停用并固定返回 `410 automatic payout is disabled`。不会再执行自动签名、广播、余额预检或链上确认任务。

## 数据迁移

V27 将历史 `PENDING` 的非 ERC20 提现标记为 `REJECTED` 并释放对应冻结积分；历史已处理记录保持只读，不删除。

V28 新增可空 `usdt_per_50_points` 快照列，只供新申请写入；历史提现的 `usdt_amount`、旧比例和旧人民币快照不回填、不重算。

V31 为提现状态机补 `VOIDED` 终态与作废审计字段（`voided_at`、`voided_by`、`void_reason`），放宽 `active_slot` 与 `SIGNING` 两个 CHECK 约束以容纳新终态，新增可空 `idempotency_key` 与唯一索引 `uk_withdrawal_requests_idempotency_key`，并新增统计索引 `idx_withdrawal_requests_status_network_reviewed`。全部为新增/宽松化变更，无数据删除，**旧应用代码可在该迁移之后继续运行**。

历史提现缺 `WITHDRAWAL` 流水的问题不回填，V31 只保证本轮之后"余额 = 流水累计"成立。
