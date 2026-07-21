# Withdrawal API

提现使用用户绑定的 TRC20、ERC20 或 BEP20 钱包。管理员逐次从后台提交私钥，后端仅在内存中签名；私钥不持久化，已签名交易与确定性交易哈希持久化后才广播。金额按人民币积分比例和后台维护的 CNY/USD 汇率计算。

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
- TRON `transfer(address,uint256)` 请求参数只使用 Base58Check payload 去掉 `0x41` 网络前缀后的 20 字节地址，不包含 4 字节 checksum。节点返回的 `raw_data_hex` 使用 Trident generated protobuf 解析。签名前严格核对唯一 `TriggerSmartContract`、owner、USDT 合约、零 `callValue`、零 `callTokenValue`、零 `tokenId`、selector、收款人、金额、`feeLimit`、timestamp 和 expiration；节点返回的 JSON 字段不作为信任依据。首次字段不匹配时只向节点重建一次未签名交易，第二次仍不匹配则返回字段级安全原因码并终止；HTTP、节点业务错误、缺失 `raw_data_hex` 和 `txID` 不匹配不重试。日志只记录原因码，不记录私钥、签名或完整 `raw_data_hex`。
- TRC20 余额预检直接调用 USDT 合约的 `balanceOf(address)`，将返回的 uint256 按 6 位小数换算；节点业务失败、缺少或非法 `constant_result` 均按查询失败返回，不会把不完整响应当成余额 0。TRX 余额仍通过账户接口查询，用于手续费上限预检。
- ERC20 nonce 的数据库行可在独立事务中初始化，但锁定、递增与保存 `SIGNING` intent 位于同一事务；intent 保存失败不会消耗 nonce。
- 所有 attempt mutation 固定按 withdrawal -> payout attempt 顺序加悲观锁，避免状态确认与广播更新之间形成反向锁顺序。
- TRON 交易过期后，即使配置节点单次返回 `NOT_FOUND`，也进入 `MANUAL_REVIEW` 并保留 active slot，禁止自动释放后重新签名。

## 后台提现管理

- `GET /api/admin/withdrawals` 返回提现单及最新 payout attempt 的 `payoutStatus`、`payoutTxHash`、`confirmationCount`、`failureReason`、`manualReview`、`actualFeeAmount` 和 `actualFeeAsset`。实际手续费仅在 attempt 为 `CONFIRMED` 时返回；V26 迁移前的历史记录或链节点未返回费用时保持 `null`。响应不会包含私钥、签名原文或 signing owner。
- `POST /api/admin/withdrawals/batch-preview` 同时要求 `WITHDRAWAL_READ` 和 `WITHDRAWAL_WRITE`，请求体仅包含最多 10 个 `withdrawalIds`。响应展示配置的热钱包公开地址、待打款总额、网络、目标地址、提现状态及 `feeEstimates`；预览不接收私钥，也不查询由私钥派生的钱包余额。
- `POST /api/admin/withdrawals/{id}/approve` 请求按网络提交 `tronPrivateKey` 或 `ethPrivateKey`，并提交 6 位数字 `totpCode`。私钥必须为空或 64 位 hex，可带 `0x`/`0X` 前缀；coordinator 会统一剥离前缀，非法格式统一返回 400。私钥只传入 payout coordinator，不持久化、不审计、不记录日志，也不进入响应。
- `POST /api/admin/withdrawals/batch-approve` 使用相同的私钥与 TOTP 边界，最多逐笔执行 10 笔，并返回 `succeeded`、`pending`、`failed` 和每笔 attempt 状态。只有 `BROADCASTED`、`CONFIRMED` 计为已提交，`PREPARED` 单独计为待广播；`FAILED_RETRYABLE` 和 `MANUAL_REVIEW` 均为非成功结果，后者要求人工核对且禁止重复生成交易。单笔审计写入失败只记录 WARN，不改变该笔结果或阻断后续提现。
- 单笔和批量执行在创建 payout attempt、签名或广播前执行余额预检：按网络聚合本次 USDT 总额，并检查派生热钱包的 USDT 余额以及 TRX、ETH 或 BNB 手续费余额。批量任一网络余额不足会整体拒绝，不创建任何 attempt。预检到创建 attempt 由进程内执行锁串行化，降低并发打款同时通过余额检查的风险；链上余额变化仍以节点在签名/广播时的最终结果为准。
- 余额不足响应使用中文明确返回资产、本次需要、当前余额和差额。后台在提现安全确认弹窗内持久显示该错误，不再仅依赖短暂顶部消息；请求结束后仍立即清空私钥和 2FA，重新提交、返回预览或关闭弹窗时清除旧错误。
- 后台列表只允许申请状态为 `PENDING` 且没有活动 attempt，或最新 attempt 为 `SIGNING`、`FAILED_RETRYABLE` 的记录进入批量勾选和执行按钮；`PREPARED`、`BROADCASTED`、`MANUAL_REVIEW`、`CONFIRMED` 会被禁用，避免重复生成交易。
- 执行、执行失败和拒绝操作均写管理员审计。失败审计使用独立事务，摘要只包含提现 ID、网络、金额、状态和可用的交易哈希，不记录异常请求体或签名材料。

## 手续费展示

- 预计手续费按预览中同一网络的笔数聚合，只使用链原生币展示，不换算为 USDT 或人民币。TRC20 使用配置的 `feeLimit × 笔数`，`estimateType=MAXIMUM`，后台标为“预计上限”；ERC20/BEP20 使用预览时的 `gasPrice × gasLimit × 笔数`，`estimateType=ESTIMATE`。
- TRON 实际手续费读取 transaction info 顶层 `fee`，从 sun 换算为 TRX。ERC20/BEP20 使用 receipt 的 `gasUsed × effectiveGasPrice` 换算为 ETH/BNB；receipt 缺少 `effectiveGasPrice` 时只允许使用该 attempt 已持久化的 gas price，不查询当前 gas price 代替历史值。
- V26 在 `withdrawal_payout_attempts` 新增可空的 `actual_fee_amount numeric(36,18)` 与 `actual_fee_asset varchar(8)`，数据库约束要求两个字段同时为空或金额非负且资产存在。重复观察同一费用幂等，冲突值拒绝覆盖。
- `POST /api/admin/withdrawals/batch-approve` 的 item 同样包含可空 `actualFeeAmount` 和 `actualFeeAsset`。后台列表和结果表仅在字段存在时显示 `金额 + TRX/ETH/BNB`，否则显示 `-`。

## 钱包写操作

`PUT /api/app/wallet` 请求必须包含 `network`、`walletAddress` 和当前 `password`；`POST /api/app/wallet/unbind` 请求必须包含当前 `password`。不增加换绑后的提现冷静期。银行卡占位接口保持现状，不改变现有表单契约。
