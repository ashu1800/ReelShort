-- V31: review remediation (2026-10-12 全面代码审查的修复迁移)
--
-- 包含四组纯新增/宽松化变更，无任何数据删除：
--   1) 提现打款 attempt 新增 VOIDED 终态，并记录作废审计字段（解除卡死的 ACTIVE attempt）
--   2) 凭证失败计数表（钱包改绑 / 改密 的爆破锁定）
--   3) 图形验证码答案哈希化（不再明文存储）
--   4) 提现提交幂等键与热点查询索引
--
-- 兼容性：新增列均可空、约束对既有状态集合仍然成立，因此旧应用代码可在本迁移之后继续运行。

-- ---------------------------------------------------------------------------
-- 1) withdrawal_payout_attempts: VOIDED 终态
-- ---------------------------------------------------------------------------
ALTER TABLE withdrawal_payout_attempts DROP CONSTRAINT IF EXISTS ck_withdrawal_payout_attempt_active_slot;
ALTER TABLE withdrawal_payout_attempts ADD CONSTRAINT ck_withdrawal_payout_attempt_active_slot check (
    (
        status IN ('SIGNING', 'PREPARED', 'BROADCASTED', 'MANUAL_REVIEW')
        AND active_slot IS NOT NULL
        AND active_slot = 'ACTIVE'
    )
    OR
    (status IN ('CONFIRMED', 'FAILED_RETRYABLE', 'VOIDED') AND active_slot IS NULL)
);

ALTER TABLE withdrawal_payout_attempts DROP CONSTRAINT IF EXISTS ck_withdrawal_payout_attempt_signing;
ALTER TABLE withdrawal_payout_attempts ADD CONSTRAINT ck_withdrawal_payout_attempt_signing check (
    (
        status = 'SIGNING'
        AND signed_raw_transaction IS NULL
        AND tx_hash IS NULL
        AND signing_owner IS NOT NULL
        AND signing_lease_until IS NOT NULL
    )
    OR
    (
        status IN ('PREPARED', 'BROADCASTED', 'CONFIRMED')
        AND signed_raw_transaction IS NOT NULL
        AND tx_hash IS NOT NULL
        AND signing_owner IS NULL
        AND signing_lease_until IS NULL
    )
    OR
    (
        status IN ('MANUAL_REVIEW', 'FAILED_RETRYABLE', 'VOIDED')
        AND signing_owner IS NULL
        AND signing_lease_until IS NULL
        AND (
            (signed_raw_transaction IS NULL AND tx_hash IS NULL)
            OR (signed_raw_transaction IS NOT NULL AND tx_hash IS NOT NULL)
        )
    )
);

ALTER TABLE withdrawal_payout_attempts ADD COLUMN IF NOT EXISTS voided_at timestamp with time zone;
ALTER TABLE withdrawal_payout_attempts ADD COLUMN IF NOT EXISTS voided_by varchar(64);
ALTER TABLE withdrawal_payout_attempts ADD COLUMN IF NOT EXISTS void_reason varchar(255);

-- ---------------------------------------------------------------------------
-- 2) credential_attempts: 钱包改绑 / 改密的失败计数与锁定
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS credential_attempts (
    user_id uuid NOT NULL,
    failed_count integer NOT NULL,
    locked_until timestamp with time zone,
    updated_at timestamp with time zone NOT NULL,
    primary key (user_id),
    constraint fk_credential_attempts_user foreign key (user_id) references users (id),
    constraint ck_credential_attempts_failed_count check (failed_count >= 0)
);

-- ---------------------------------------------------------------------------
-- 3) captcha_challenges: 答案哈希化
-- ---------------------------------------------------------------------------
ALTER TABLE captcha_challenges ADD COLUMN IF NOT EXISTS answer_hash varchar(64);
ALTER TABLE captcha_challenges ALTER COLUMN answer DROP NOT NULL;

-- ---------------------------------------------------------------------------
-- 4) withdrawal_requests: 提交幂等键
-- ---------------------------------------------------------------------------
ALTER TABLE withdrawal_requests ADD COLUMN IF NOT EXISTS idempotency_key varchar(128);
CREATE UNIQUE INDEX IF NOT EXISTS uk_withdrawal_requests_idempotency_key
    ON withdrawal_requests (idempotency_key);

-- ---------------------------------------------------------------------------
-- 5) 热点查询索引
-- ---------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_point_transactions_user_created
    ON point_transactions (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_withdrawal_requests_status_network_reviewed
    ON withdrawal_requests (status, network, reviewed_at);
