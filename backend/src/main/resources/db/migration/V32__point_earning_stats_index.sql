-- V32: 控制台积分获取统计（近 30 天观看奖励合计）的热点查询索引。
-- 现有 idx_point_transactions_user_created 以 user_id 打头，无法支撑全站 created_at 范围扫描。
CREATE INDEX IF NOT EXISTS idx_point_transactions_created_at
    ON point_transactions (created_at);
