# ERC20 Manual Withdrawal Design

## Goal

将提现切换为仅 ERC20 的外部钱包人工打款流程：用户提交提现后冻结积分，管理员从外部钱包完成 ERC20 转账，再在后台输入 2FA 点击确认，系统幂等扣减冻结积分并将提现标记为已通过。

## Decisions

- 新提现只接受 ERC20；钱包绑定和提现创建拒绝 TRC20/BEP20。
- 旧的非 ERC20 历史记录保留只读。迁移时将非 ERC20 的 `PENDING` 提现标记为 `REJECTED` 并释放冻结积分，用户需改绑 ERC20 后重新申请。
- 移除后台私钥、签名、广播和定时自动确认的可用入口。旧 payout attempt 仅用于历史展示，不再自动推进状态。
- 后台确认接口只接收 6 位 TOTP，不接收 `txHash`、私钥或链上参数。确认直接在提现行锁、用户积分行锁和提现幂等流水约束下执行，不伪造无 hash 的链上 attempt。
- 手动确认使用现有 `WithdrawalRequest.reviewedAt` 作为打款确认时间；统计只计算 `APPROVED` 且 `network=ERC20` 的 `usdtAmount` 和笔数。
- 统计预设为今天、昨天、本周、本月、上月，时间边界按服务端 Asia/Shanghai 日历计算，查询使用半开区间 `[start, end)`。
- 同一提现只能确认一次；重复点击返回当前已处理结果或幂等成功，不重复扣积分。

## API and UI

- 新增 `POST /api/admin/withdrawals/{id}/manual-confirm`，请求体只有 `totpCode`，要求 `WITHDRAWAL_WRITE` 和已启用的 2FA。
- 新增 `GET /api/admin/withdrawals/stats?range=TODAY|YESTERDAY|THIS_WEEK|THIS_MONTH|LAST_MONTH`，返回范围、开始/结束时间、ERC20 USDT 总额和已确认笔数。
- 后台提现列表移除自动打款、预览、私钥输入和批量签名流程；ERC20 `PENDING` 与需人工处理的记录显示“确认已外部打款”按钮。
- 统计区域使用分段预设选择，默认今天，显示打款笔数与 USDT 总额。

## Data and Compatibility

- 新 Flyway 迁移只处理非 ERC20 的 `PENDING` 提现，并在释放冻结积分前要求冻结余额足够，失败则整体回滚；历史 payout attempt 不删除、不改写。
- 保留历史链枚举、旧响应字段和只读记录，避免旧客户端/历史数据反序列化失败；新业务路径不再创建非 ERC20 提现。

## Verification

- 后端单元/集成测试覆盖 ERC20-only 校验、旧单迁移解冻、2FA/权限、重复确认幂等、冻结积分扣减、统计五个预设和自动入口禁用。
- Admin Web 构建验证按钮、错误状态和统计切换。
- Android app-core/App UI 测试确认钱包网络选择只剩 ERC20，提现请求携带 ERC20 快照。
