-- V33: 每日积分平滑速率
-- 记录每个账号当天累计观看秒数，用于按播放时长递减奖励速率。
ALTER TABLE point_daily_earning_quotas
    ADD COLUMN IF NOT EXISTS watched_seconds integer NOT NULL DEFAULT 0;

-- 旧额度行按当天已领取的观看奖励分集时长回填累计观看秒数。
-- 现有额度日期由 UTC 自然日生成，因此这里按 UTC 日界回填；新额度统一使用 Asia/Shanghai 日界。
CREATE INDEX IF NOT EXISTS idx_watch_episode_reward_claims_user_created
    ON watch_episode_reward_claims (user_id, created_at);

UPDATE point_daily_earning_quotas q
SET watched_seconds = LEAST(COALESCE((
    SELECT SUM(c.duration_seconds)
    FROM watch_episode_reward_claims c
    WHERE c.user_id = q.user_id
      AND c.created_at >= (q.earning_date::timestamp AT TIME ZONE 'UTC')
      AND c.created_at < ((q.earning_date + 1)::timestamp AT TIME ZONE 'UTC')
), 0), 86400)::integer
WHERE q.watched_seconds = 0;
