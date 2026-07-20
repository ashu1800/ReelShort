# Payout Balance Preflight Design

## 问题

提现打款目前只校验私钥派生地址和白名单，不在签名/广播前检查 token 与原生手续费余额。热钱包 USDT 为 0 时仍会生成 TRON 交易，链上 `REVERT` 并消耗 TRX。批量请求还可能并发通过同一份过期余额快照。

## 方案

新增 `PayoutBalancePreflightService`，统一为 TRC20、ERC20、BEP20 计算每个网络的 token 总需求和手续费预算。TRON 原生币预算按每笔 `feeLimit` 上限计算；EVM 按 `gasPrice * gasLimit * transactionCount` 计算。余额不足在创建 `SIGNING` attempt 之前抛出明确 409，整批不执行任何一笔。

`WithdrawalService` 对单笔和批量打款使用同一个进程内执行锁，防止并发管理员请求同时通过预检。批量接口在余额预检前重新读取申请和最新 payout attempt，拒绝已进入 `PREPARED`、`BROADCASTED`、`MANUAL_REVIEW` 或 `CONFIRMED` 的申请；`SIGNING` 与 `FAILED_RETRYABLE` 仅用于恢复/重试。

前端勾选逻辑与服务端资格保持一致，已签名、已广播和人工核对状态不可作为首次批量打款选择项。链上余额仍可能在预检后被外部消耗，因此原有 attempt 锁、签名校验和链上失败处理继续保留。

## 测试与部署

使用失败优先测试验证 token/native 余额不足时不会调用 payout coordinator，批量余额按网络聚合，已在途申请被拒绝，BSC 余额查询可用；通过提现、后端全量和发布基线后，仅部署 backend。
