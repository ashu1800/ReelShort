# Withdrawal API

提现继续使用用户绑定的 TRC20 钱包并人工转账 USDT。金额按人民币积分比例和后台维护的 CNY/USD 汇率计算。

## 换算规则

默认配置：

- `withdraw.cny-per-point=0.02`，即 50 积分 = 1 CNY。
- `withdraw.cny-per-usd=7.2`，即 1 USD = 7.2 CNY。
- `withdraw.minimum-usd=10`。

最低提现积分为 `ceil(minimumUsd * cnyPerUsd / cnyPerPoint)`，默认是 `3600` 积分。到账 USDT 为 `pointAmount * cnyPerPoint / cnyPerUsd`，按 6 位小数保存。

## `GET /api/app/withdrawals/summary`

返回余额、冻结积分、可用积分、动态最低提现积分、USDT 比例、人民币比例、汇率、最低美元金额和钱包地址。

## `POST /api/app/withdrawals`

请求仍只提交 `pointAmount`。后端按当前配置计算金额并在提现单保存 `cnyPerPoint`、`cnyPerUsd`、`minimumUsd` 和 `usdtPerPoint` 快照；之后调整后台配置不会影响已提交申请。

历史提现记录可能没有新增的人民币和美元快照字段，API 会返回 `null`，最终 `usdtAmount` 和 `usdtPerPoint` 仍可正常展示。
