# Withdrawal API

提现仅支持用户绑定的 ERC20 钱包。用户提交申请时冻结积分；管理员在外部钱包完成 ERC20 USDT 转账后，通过后台确认扣除冻结积分。系统不保存或接收热钱包私钥，不签名、不广播、不查询链上交易，也不需要交易哈希。

## 换算规则

默认配置：

- `withdraw.cny-per-point=0.02`，即 50 积分 = 1 CNY。
- `withdraw.cny-per-usd=7.2`，即 1 USD = 7.2 CNY。
- `withdraw.minimum-usd=10`。

最低提现积分为 `ceil(minimumUsd * cnyPerUsd / cnyPerPoint)`，默认是 `3600` 积分。到账 USDT 为 `pointAmount * cnyPerPoint / cnyPerUsd`，按 6 位小数保存。

## App 接口

### `GET /api/app/withdrawals/summary`

返回余额、冻结积分、可用积分、动态最低提现积分、USDT 比例、人民币比例、汇率、最低美元金额和钱包地址。

### `POST /api/app/withdrawals`

请求只提交 `pointAmount`。后端按当前配置保存换算快照并冻结积分。只有当前绑定 ERC20 钱包的用户可以创建申请；历史非 ERC20 钱包必须重新绑定 ERC20 后再次申请。

### 钱包写操作

`PUT /api/app/wallet` 请求必须包含 `network=ERC20`、`walletAddress` 和当前 `password`。`POST /api/app/wallet/unbind` 请求必须包含当前 `password`。银行卡占位接口保持不变。

## 后台提现管理

### `GET /api/admin/withdrawals`

要求 `WITHDRAWAL_READ`。返回提现申请与历史 payout attempt 状态；历史 attempt 仅用于查看，不会继续自动处理。已由外部转账确认的申请返回 `payoutStatus=MANUAL_CONFIRMED`。

### `POST /api/admin/withdrawals/{id}/manual-confirm`

要求 `WITHDRAWAL_WRITE`。该接口没有请求体；管理员须已完成后台登录，但不要求再次输入 TOTP。

仅可确认 `PENDING` 的 ERC20 申请；重复确认幂等。成功后系统扣除该申请全部冻结积分，将申请标记为 `APPROVED`，并写入 `WITHDRAWAL_MANUAL_CONFIRMED` 审计记录。管理员必须在调用前完成外部钱包转账；该接口不接收或校验交易哈希。

### `POST /api/admin/withdrawals/{id}/reject`

要求 `WITHDRAWAL_WRITE`。拒绝待处理申请并释放冻结积分。

### `GET /api/admin/withdrawals/stats?range=TODAY`

要求 `WITHDRAWAL_READ`。仅统计 `ERC20` 且 `APPROVED` 的提现，时间字段使用 `reviewedAt`，按 `Asia/Shanghai` 使用 `[from, to)` 边界。

合法 `range`：`TODAY`、`YESTERDAY`、`THIS_WEEK`（周一开始）、`THIS_MONTH`、`LAST_MONTH`。响应包含统计区间、`payoutCount` 和 `totalUsdt`。

## 旧自动打款接口

`POST /api/admin/withdrawals/{id}/approve`、`POST /api/admin/withdrawals/batch-preview` 和 `POST /api/admin/withdrawals/batch-approve` 已停用并返回 `410`。不会再执行自动签名、广播、余额预检或链上确认任务。

## 数据迁移

V27 将历史 `PENDING` 的非 ERC20 提现标记为 `REJECTED` 并释放对应冻结积分；历史已处理记录保持只读，不删除。
