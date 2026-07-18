ALTER TABLE point_transactions ADD COLUMN frozen_points_after integer;

ALTER TABLE point_transactions ADD CONSTRAINT ck_point_transactions_frozen_after
    CHECK (
        frozen_points_after IS NULL
        OR (frozen_points_after >= 0 AND frozen_points_after <= balance_after)
    );

ALTER TABLE watch_episode_reward_claims ADD COLUMN calculated_tenths integer;

UPDATE watch_episode_reward_claims
SET calculated_tenths = calculated_points * 10
WHERE calculated_tenths IS NULL;

ALTER TABLE watch_episode_reward_claims ALTER COLUMN calculated_tenths SET NOT NULL;
ALTER TABLE watch_episode_reward_claims ADD CONSTRAINT ck_watch_episode_reward_claims_calculated_tenths
    CHECK (calculated_tenths >= 0);
