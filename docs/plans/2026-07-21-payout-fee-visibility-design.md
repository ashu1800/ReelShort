# 提现手续费可见性设计

## 目标

管理员在执行单笔或批量提现前看到每条网络的预计手续费，链上确认成功后在提现列表和打款结果中看到真实消耗的原生币手续费。

手续费只以网络原生币展示：TRC20 使用 TRX，ERC20 使用 ETH，BEP20 使用 BNB；不换算为 USDT 或人民币。

## 预计手续费

沿用现有 `POST /api/admin/withdrawals/batch-preview`，不新增接受私钥的预览接口。响应增加按网络聚合的 `feeEstimates`：

- `network`：TRC20、ERC20 或 BEP20。
- `asset`：TRX、ETH 或 BNB。
- `transactionCount`：该网络本次提现笔数。
- `estimatedAmount`：该网络本次预计手续费总额。
- `estimateType`：`MAXIMUM` 或 `ESTIMATE`。

TRC20 使用配置的每笔 `feeLimit` 乘以笔数，属于最大手续费预算，标记为 `MAXIMUM`；TRON 实际消耗会受带宽和能量资源影响。ERC20/BEP20 使用当前 gas price × 配置 gas limit × 笔数，标记为 `ESTIMATE`。gas price 查询沿用客户端现有受控 fallback。

后台预览按网络展示，例如：`TRC20 · 2 笔 · 预计上限 200 TRX`。预计值不查询热钱包余额、不需要私钥，也不承诺等于最终链上扣费。

## 实际手续费

Flyway 新增 `withdrawal_payout_attempts.actual_fee_amount` 与 `actual_fee_asset` 两个可空字段。历史 attempt 保持 `null`，无需回填猜测。

链上查询成功时计算真实费用：

- TRON：读取 `gettransactioninfobyid` 返回的顶层 `fee`（sun），按 6 位精度换算为 TRX。该字段为节点报告的实际 TRX 消耗，使用质押资源时可以为 0。
- Ethereum/BSC：读取成功 receipt 的 `gasUsed` 与 `effectiveGasPrice`，计算 `gasUsed × effectiveGasPrice / 10^18`。若 receipt 缺少 `effectiveGasPrice`，使用已持久化 attempt gas price 作为兼容回退，不重新猜测当前 gas price。

`PayoutChainStatus` 携带可选的实际费用数据。确认扫描在记录确认数或最终结算时，将费用与 attempt 状态在同一锁顺序和事务边界中保存。重复扫描只允许写入同值，不允许已记录费用被不同值覆盖。

API 在 `WithdrawalResponse` 和批量结果 item 中增加可空的 `actualFeeAmount`、`actualFeeAsset`。后台仅当 payout 状态为 `CONFIRMED` 且费用字段存在时展示“实际手续费”；失败、待广播、广播中和人工核对状态显示 `-`，避免把预算当成实际费用。

## 界面

- 打款预览：在提现总额与笔数下增加“预计手续费”区域，按网络逐行展示预计总额和笔数。
- 提现列表：增加“实际手续费”列，确认成功后显示数值与原生币。
- 打款结果：增加“实际手续费”列；立即广播后的结果通常为空，后续刷新列表在确认完成后显示真实值。
- 所有金额使用后端规范化十进制字符串，前端不使用浮点数重新计算。

## 错误与安全边界

- 预览仍只接收提现 ID，不接收私钥，不查询私钥派生地址余额。
- 预计费用查询失败时使用现有受控 gas price fallback；网络或配置完全不可用时预览失败，不显示伪造的 0 手续费。
- receipt 暂不可用时实际费用保持为空，确认扫描后续重试。
- 私钥、签名原文、原始交易和 receipt 原文不进入 API、审计日志或手续费字段。
- 余额预检继续按相同预计预算检查 TRX/ETH/BNB，不改变现有防超支边界。

## 测试与部署

- 单元测试覆盖三条链预计费用聚合和单位换算。
- 客户端测试覆盖 TRON `fee`、EVM `gasUsed × effectiveGasPrice` 解析以及 EVM 回退。
- 集成测试覆盖实际费用持久化、重复扫描幂等、历史空值和响应映射。
- 前端契约测试覆盖预览、列表和结果中的手续费展示。
- 新增 Flyway 迁移并在生产部署前备份 PostgreSQL；部署后验证迁移版本、容器健康、公开 API 和后台新静态资源。
