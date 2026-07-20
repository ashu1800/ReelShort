# TRON 原始交易校验诊断与单次重建设计

## 背景

TRC20 提现通过 TRON 节点的 `triggersmartcontract` 构建未签名交易。后端在签名前解析 `raw_data_hex`，严格校验 owner、USDT 合约、收款人、金额、调用类型、附加价值、`feeLimit` 和时间窗口。当前任一字段不匹配都统一返回 `TRON raw transaction does not match payout intent`，无法区分上游瞬时异常与固定兼容问题。

生产中的两笔提现仍为 `PENDING`，payout attempt 停在 `SIGNING`，没有签名、交易哈希或广播记录。使用相同生产节点、attempt 中的公开 owner、实际收款地址和金额重新构建时，当前响应符合全部意图字段，说明需要同时覆盖瞬时异常恢复和稳定复现诊断。

## 方案

1. 将每个原始交易校验条件拆成稳定的安全原因码，例如 `fee_limit`、`owner_address`、`recipient`、`amount`、`expiration_too_far`。
2. 使用专用的内部异常类型标识“原始交易与意图不匹配”，避免按错误消息字符串判断。
3. `prepareTransfer` 第一次捕获该专用异常时，丢弃未签名交易并重新调用 `triggersmartcontract` 一次。
4. 第二次仍不匹配时返回 `TRON raw transaction does not match payout intent: <reason>`。错误只包含原因码，不包含私钥、签名或完整原始交易。
5. 节点业务错误、响应缺少 `raw_data_hex`、HTTP 错误和 txID 不一致不走该重试路径，保持现有失败语义。

## 安全边界

- 两次响应都必须通过全部校验后才签名。
- owner、USDT 合约、收款地址、金额和调用 selector 不放宽。
- 自动重建发生在签名前，不会产生链上交易或重复广播。
- 现有 `SIGNING` intent 继续复用，不需要 Flyway 迁移或人工删除 attempt。

## 测试

- 第一次 recipient 不匹配、第二次响应正确时，构建两次并成功签名第二笔交易。
- 连续两次 amount 不匹配时，返回带 `amount` 原因码的 502 错误。
- 为 owner、合约、附加 TRX/TRC10、selector、feeLimit 和时间窗口保留或补齐原因码断言。
- 运行 `TronClientTests`、withdrawal 测试、后端完整测试和发布质量基线。
- 部署后确认 backend healthy，再由管理员重试原有两笔 `SIGNING` attempt。
