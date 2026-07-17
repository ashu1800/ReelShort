-- V18: 公平模式从"全局 ×10 缩放"重构为"定点小数字段累积"
--
-- 背景：V16 引入 fair mode 时采用全局 ×10 缩放策略（balance 存 ×10 内部值，展示 ÷10）。
-- 该方案有多处展示遗漏、开关切换无迁移、int 上限缩水等风险。
-- 新方案在 point_accounts 新增 fractional_part 字段（十分位 0-9），balance 永远存真实整数。
--
-- 前提：V13 已清空所有用户数据（DELETE FROM point_accounts/point_transactions/...），
-- V16 后产生的所有积分数据均在 fair mode ×10 模式下，可安全 ÷10 还原。
--
-- 兼容性：用 WHERE EXISTS 子句替代 PostgreSQL 专有的 DO $$ 块，兼容 H2 和 PostgreSQL。
-- 当 fair mode 当前关闭时（测试环境默认），÷10 分支不执行，仅添加新字段。

-- 1. 新增小数字段（用 INTEGER 与 Java int 映射一致，生产 ddl-auto=validate 校验通过）
ALTER TABLE point_accounts ADD COLUMN IF NOT EXISTS fractional_part INTEGER NOT NULL DEFAULT 0;
ALTER TABLE point_daily_earning_quotas ADD COLUMN IF NOT EXISTS fractional_earned INTEGER NOT NULL DEFAULT 0;

-- 2. 仅当 fair mode 当前开启时执行 ÷10 还原（WHERE EXISTS 保证条件分支跨数据库兼容）
--    当前余额：余数进 fractional_part（例如 balance=13 → balance=1, fractional_part=3）
UPDATE point_accounts SET
    fractional_part = balance % 10,
    balance = balance / 10
WHERE EXISTS (SELECT 1 FROM system_configs WHERE config_key = 'points.fair-mode.enabled' AND config_value = '1');

--    每日配额：earned_points 余数进 fractional_earned；effective_maximum 直接 ÷10
UPDATE point_daily_earning_quotas SET
    fractional_earned = earned_points % 10,
    earned_points = earned_points / 10,
    effective_maximum = effective_maximum / 10
WHERE EXISTS (SELECT 1 FROM system_configs WHERE config_key = 'points.fair-mode.enabled' AND config_value = '1');

--    历史流水（V13 后全在 fair mode 下产生，统一 ÷10）
UPDATE point_transactions SET
    amount = amount / 10,
    balance_after = balance_after / 10
WHERE EXISTS (SELECT 1 FROM system_configs WHERE config_key = 'points.fair-mode.enabled' AND config_value = '1');

--    观看奖励领取记录
UPDATE watch_episode_reward_claims SET
    calculated_points = calculated_points / 10,
    awarded_points = awarded_points / 10
WHERE EXISTS (SELECT 1 FROM system_configs WHERE config_key = 'points.fair-mode.enabled' AND config_value = '1');

--    提现申请（point_amount、fee_amount 都是 ×10 内部值）
UPDATE withdrawal_requests SET
    point_amount = point_amount / 10,
    fee_amount = fee_amount / 10
WHERE EXISTS (SELECT 1 FROM system_configs WHERE config_key = 'points.fair-mode.enabled' AND config_value = '1');

-- 3. 更新配置描述（语义已变更：开关切换零风险）
UPDATE system_configs SET description = 'Fair mode: watch rewards accumulate fractional tenths for precise per-second calculation (1=on, 0=off). Balance is always real integer; toggling is risk-free.'
WHERE config_key = 'points.fair-mode.enabled';
