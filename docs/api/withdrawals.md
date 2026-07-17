# Withdrawal API

提现使用用户绑定的 TRC20 或 ERC20 钱包。管理员逐次从后台提交私钥，后端仅在内存中签名；私钥不持久化，已签名交易与确定性交易哈希持久化后才广播。金额按人民币积分比例和后台维护的 CNY/USD 汇率计算。

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

## 打款安全边界

- `REELSHORT_TRON_HOT_WALLET_ADDRESS` 与 `REELSHORT_ETH_HOT_WALLET_ADDRESS` 分别配置受控热钱包公钥地址。对应网络配置为空或私钥派生地址不匹配时，在创建打款 attempt 前拒绝请求。
- TRON 节点返回的 `raw_data_hex` 使用 Trident generated protobuf 解析。签名前严格核对唯一 `TriggerSmartContract`、owner、USDT 合约、零 `callValue`、零 `callTokenValue`、零 `tokenId`、`transfer(address,uint256)` selector、收款人、金额、`feeLimit`、timestamp 和 expiration；节点返回的 JSON 字段不作为信任依据。
- ERC20 nonce 的数据库行可在独立事务中初始化，但锁定、递增与保存 `SIGNING` intent 位于同一事务；intent 保存失败不会消耗 nonce。
- 所有 attempt mutation 固定按 withdrawal -> payout attempt 顺序加悲观锁，避免状态确认与广播更新之间形成反向锁顺序。
- TRON 交易过期后，即使配置节点单次返回 `NOT_FOUND`，也进入 `MANUAL_REVIEW` 并保留 active slot，禁止自动释放后重新签名。
