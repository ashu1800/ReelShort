-- V18: 公平模式从"全局 ×10 缩放"重构为"定点小数字段累积"
--
-- 背景：V16 引入 fair mode 时采用全局 ×10 缩放策略（balance 存 ×10 内部值，展示 ÷10）。
-- 该方案有多处展示遗漏、开关切换无迁移、int 上限缩水等风险。
-- 新方案在 point_accounts 新增 fractional_part 字段（十分位 0-9），balance 永远存真实整数。
--
-- 历史积分无法仅凭现有字段可靠区分真实整数和旧版十分位内部值，因此迁移不得
-- 猜测并重写余额、流水、奖励或提现记录。已有数据保持原值，后续奖励从新增字段累积。

-- 1. 新增小数字段（用 INTEGER 与 Java int 映射一致，生产 ddl-auto=validate 校验通过）
ALTER TABLE point_accounts ADD COLUMN IF NOT EXISTS fractional_part INTEGER NOT NULL DEFAULT 0;
ALTER TABLE point_daily_earning_quotas ADD COLUMN IF NOT EXISTS fractional_earned INTEGER NOT NULL DEFAULT 0;

-- 2. 更新配置描述（语义已变更：开关切换不再批量换算历史数据）
UPDATE system_configs SET description = 'Fair mode: watch rewards accumulate fractional tenths for precise per-second calculation (1=on, 0=off). Balance is always real integer; toggling is risk-free.'
WHERE config_key = 'points.fair-mode.enabled';
