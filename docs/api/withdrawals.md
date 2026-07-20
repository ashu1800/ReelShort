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

- `REELSHORT_TRON_HOT_WALLET_ADDRESS`、`REELSHORT_ETH_HOT_WALLET_ADDRESS` 与 `REELSHORT_BSC_HOT_WALLET_ADDRESS` 可选配置受控热钱包公开地址。未配置时使用本次提交私钥派生出的地址创建打款 attempt；配置后若私钥派生地址不匹配，则在创建 attempt 前拒绝请求。签名阶段始终复核私钥派生地址与已持久化 intent 地址一致。
- TRON 节点返回的 `raw_data_hex` 使用 Trident generated protobuf 解析。签名前严格核对唯一 `TriggerSmartContract`、owner、USDT 合约、零 `callValue`、零 `callTokenValue`、零 `tokenId`、`transfer(address,uint256)` selector、收款人、金额、`feeLimit`、timestamp 和 expiration；节点返回的 JSON 字段不作为信任依据。首次字段不匹配时只向节点重建一次未签名交易，第二次仍不匹配则返回字段级安全原因码并终止；HTTP、节点业务错误、缺失 `raw_data_hex` 和 `txID` 不匹配不重试。日志只记录原因码，不记录私钥、签名或完整 `raw_data_hex`。
- ERC20 nonce 的数据库行可在独立事务中初始化，但锁定、递增与保存 `SIGNING` intent 位于同一事务；intent 保存失败不会消耗 nonce。
- 所有 attempt mutation 固定按 withdrawal -> payout attempt 顺序加悲观锁，避免状态确认与广播更新之间形成反向锁顺序。
- TRON 交易过期后，即使配置节点单次返回 `NOT_FOUND`，也进入 `MANUAL_REVIEW` 并保留 active slot，禁止自动释放后重新签名。

## 后台提现管理

- `GET /api/admin/withdrawals` 返回提现单及最新 payout attempt 的 `payoutStatus`、`payoutTxHash`、`confirmationCount`、`failureReason` 和 `manualReview`。响应不会包含私钥、签名原文或 signing owner。
- `POST /api/admin/withdrawals/batch-preview` 同时要求 `WITHDRAWAL_READ` 和 `WITHDRAWAL_WRITE`，请求体仅包含最多 10 个 `withdrawalIds`。响应展示配置的热钱包公开地址、待打款总额、网络、目标地址和提现状态；预览不接收私钥，也不查询由私钥派生的钱包余额。
- `POST /api/admin/withdrawals/{id}/approve` 请求按网络提交 `tronPrivateKey` 或 `ethPrivateKey`，并提交 6 位数字 `totpCode`。私钥必须为空或 64 位 hex，可带 `0x`/`0X` 前缀；coordinator 会统一剥离前缀，非法格式统一返回 400。私钥只传入 payout coordinator，不持久化、不审计、不记录日志，也不进入响应。
- `POST /api/admin/withdrawals/batch-approve` 使用相同的私钥与 TOTP 边界，最多逐笔执行 10 笔，并返回 `succeeded`、`pending`、`failed` 和每笔 attempt 状态。只有 `BROADCASTED`、`CONFIRMED` 计为已提交，`PREPARED` 单独计为待广播；`FAILED_RETRYABLE` 和 `MANUAL_REVIEW` 均为非成功结果，后者要求人工核对且禁止重复生成交易。单笔审计写入失败只记录 WARN，不改变该笔结果或阻断后续提现。
- 执行、执行失败和拒绝操作均写管理员审计。失败审计使用独立事务，摘要只包含提现 ID、网络、金额、状态和可用的交易哈希，不记录异常请求体或签名材料。

## 钱包写操作

`PUT /api/app/wallet` 请求必须包含 `network`、`walletAddress` 和当前 `password`；`POST /api/app/wallet/unbind` 请求必须包含当前 `password`。不增加换绑后的提现冷静期。银行卡占位接口保持现状，不改变现有表单契约。
