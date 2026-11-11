-- 观看来源标记：区分 App 自然观看（APP）与内部运营模拟观看任务（OPERATIONS）。
-- 历史行产生时还没有来源概念，统一落在默认值 APP 上（V37 之前的运营刷积分行无法区分）。
ALTER TABLE watch_records ADD COLUMN source varchar(16) NOT NULL DEFAULT 'APP';

CREATE INDEX idx_watch_records_source_updated_at ON watch_records (source, updated_at DESC);
