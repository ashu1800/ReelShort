-- Durable withdrawal signing/broadcast state. The private key is never stored;
-- only the signed raw transaction is persisted so the exact transaction can be retried safely.
CREATE TABLE withdrawal_payout_attempts (
    id uuid NOT NULL,
    withdrawal_request_id uuid NOT NULL,
    attempt_number integer NOT NULL,
    network varchar(16) NOT NULL,
    hot_wallet_address varchar(128) NOT NULL,
    destination_address varchar(128) NOT NULL,
    token_contract_address varchar(128) NOT NULL,
    token_amount numeric(36, 18) NOT NULL,
    chain_id bigint NOT NULL,
    nonce numeric(38, 0) NOT NULL,
    signed_raw_transaction text NOT NULL,
    tx_hash varchar(128),
    status varchar(24) NOT NULL,
    active_slot varchar(24),
    confirmation_count integer NOT NULL DEFAULT 0,
    failure_code varchar(64),
    failure_reason varchar(512),
    created_by varchar(64) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    prepared_at timestamp with time zone,
    broadcast_at timestamp with time zone,
    confirmed_at timestamp with time zone,
    primary key (id),
    constraint fk_withdrawal_payout_attempt_request
        foreign key (withdrawal_request_id) references withdrawal_requests (id),
    constraint uk_withdrawal_payout_attempt_number
        unique (withdrawal_request_id, attempt_number),
    constraint uk_withdrawal_payout_attempt_active
        unique (withdrawal_request_id, active_slot),
    constraint uk_withdrawal_payout_attempt_tx_hash unique (tx_hash),
    constraint ck_withdrawal_payout_attempt_number check (attempt_number > 0),
    constraint ck_withdrawal_payout_attempt_amount check (token_amount > 0),
    constraint ck_withdrawal_payout_attempt_confirmations check (confirmation_count >= 0),
    constraint ck_withdrawal_payout_attempt_active_slot check (
        (
            status IN ('PREPARED', 'BROADCASTED', 'MANUAL_REVIEW')
            AND active_slot IS NOT NULL
            AND active_slot = 'ACTIVE'
        )
        OR
        (status NOT IN ('PREPARED', 'BROADCASTED', 'MANUAL_REVIEW') AND active_slot IS NULL)
    )
);

CREATE INDEX idx_withdrawal_payout_attempt_status
    ON withdrawal_payout_attempts (status, updated_at);

-- Serial nonce allocation per network/hot-wallet/chain prevents concurrent signers
-- from preparing different transactions with the same nonce.
CREATE TABLE hot_wallet_nonces (
    id uuid NOT NULL,
    network varchar(16) NOT NULL,
    wallet_address varchar(128) NOT NULL,
    chain_id bigint NOT NULL,
    next_nonce numeric(38, 0) NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    primary key (id),
    constraint uk_hot_wallet_nonces_wallet_chain unique (network, wallet_address, chain_id),
    constraint ck_hot_wallet_nonces_next_nonce check (next_nonce >= 0)
);

-- Snapshot every collection parameter on the order so later configuration changes
-- cannot alter what an existing order is expected to pay or where it must arrive.
ALTER TABLE vip_orders ADD COLUMN IF NOT EXISTS receiving_network varchar(16);
ALTER TABLE vip_orders ADD COLUMN IF NOT EXISTS receiving_wallet_address varchar(128);
ALTER TABLE vip_orders ADD COLUMN IF NOT EXISTS token_contract_address varchar(128);
ALTER TABLE vip_orders ADD COLUMN IF NOT EXISTS base_usdt_amount numeric(18, 6);
ALTER TABLE vip_orders ADD COLUMN IF NOT EXISTS payable_usdt_amount numeric(18, 6);
ALTER TABLE vip_orders ADD COLUMN IF NOT EXISTS pending_slot varchar(24);
ALTER TABLE vip_orders ADD COLUMN IF NOT EXISTS payment_observed_at timestamp with time zone;
ALTER TABLE vip_orders ADD COLUMN IF NOT EXISTS confirmation_count integer NOT NULL DEFAULT 0;

-- V13-V19 orders do not contain an immutable chain/address/contract snapshot.
-- They cannot be matched safely, so retain them for audit but remove them from
-- the active payment set before deriving any reporting-only amount fields.
UPDATE vip_orders
SET status = 'EXPIRED', pending_slot = NULL
WHERE status = 'PENDING'
  AND (
      receiving_network IS NULL OR trim(receiving_network) = ''
      OR receiving_wallet_address IS NULL OR trim(receiving_wallet_address) = ''
      OR token_contract_address IS NULL OR trim(token_contract_address) = ''
      OR payable_usdt_amount IS NULL OR payable_usdt_amount <= 0
  );

UPDATE vip_orders
SET base_usdt_amount = usdt_amount
WHERE base_usdt_amount IS NULL;

UPDATE vip_orders
SET payable_usdt_amount = usdt_amount + (unique_suffix / 100.0)
WHERE payable_usdt_amount IS NULL;

-- Preserve the oldest active order per user, then the oldest active order per
-- payable amount. Older schema versions did not prevent either collision.
UPDATE vip_orders
SET status = 'EXPIRED'
WHERE id IN (
    SELECT id FROM (
        SELECT id, row_number() OVER (PARTITION BY user_id ORDER BY created_at, id) AS position
        FROM vip_orders
        WHERE status = 'PENDING'
    ) ranked_user_orders
    WHERE position > 1
);

UPDATE vip_orders
SET status = 'EXPIRED'
WHERE id IN (
    SELECT id FROM (
        SELECT id, row_number() OVER (PARTITION BY payable_usdt_amount ORDER BY created_at, id) AS position
        FROM vip_orders
        WHERE status = 'PENDING'
    ) ranked_amount_orders
    WHERE position > 1
);

UPDATE vip_orders
SET pending_slot = CASE WHEN status = 'PENDING' THEN 'PENDING' ELSE NULL END;

ALTER TABLE vip_orders ADD CONSTRAINT uk_vip_orders_user_pending_slot
    unique (user_id, pending_slot);
ALTER TABLE vip_orders ADD CONSTRAINT uk_vip_orders_amount_pending_slot
    unique (payable_usdt_amount, pending_slot);
ALTER TABLE vip_orders ADD CONSTRAINT uk_vip_orders_tx_hash unique (tx_hash);
ALTER TABLE vip_orders ADD CONSTRAINT ck_vip_orders_pending_slot
    check (
        (
            status = 'PENDING'
            AND pending_slot = 'PENDING'
            AND receiving_network IS NOT NULL AND trim(receiving_network) <> ''
            AND receiving_wallet_address IS NOT NULL AND trim(receiving_wallet_address) <> ''
            AND token_contract_address IS NOT NULL AND trim(token_contract_address) <> ''
            AND base_usdt_amount IS NOT NULL AND base_usdt_amount > 0
            AND payable_usdt_amount IS NOT NULL AND payable_usdt_amount >= base_usdt_amount
        )
        OR
        (status <> 'PENDING' AND pending_slot IS NULL)
    );
ALTER TABLE vip_orders ADD CONSTRAINT ck_vip_orders_confirmation_count
    check (confirmation_count >= 0);
ALTER TABLE vip_orders ADD CONSTRAINT ck_vip_orders_amounts
    check (
        (base_usdt_amount IS NULL AND payable_usdt_amount IS NULL)
        OR
        (base_usdt_amount > 0 AND payable_usdt_amount >= base_usdt_amount)
    );

ALTER TABLE withdrawal_requests ADD CONSTRAINT uk_withdrawal_requests_tx_hash unique (tx_hash);

-- A caller-supplied idempotency key makes an administrator adjustment exactly-once.
ALTER TABLE point_transactions ADD COLUMN IF NOT EXISTS idempotency_key varchar(128);
ALTER TABLE point_transactions ADD CONSTRAINT uk_point_transactions_idempotency_key unique (idempotency_key);

ALTER TABLE point_accounts ADD CONSTRAINT ck_point_accounts_balance check (balance >= 0);
ALTER TABLE point_accounts ADD CONSTRAINT ck_point_accounts_frozen_non_negative check (frozen_points >= 0);
ALTER TABLE point_accounts ADD CONSTRAINT ck_point_accounts_frozen_within_balance check (frozen_points <= balance);
ALTER TABLE point_accounts ADD CONSTRAINT ck_point_accounts_fractional_part check (fractional_part between 0 and 9);

-- ORDER_WRITE is intentionally separate from ORDER_READ. Existing super-admin
-- roles receive it during migration; other roles require an explicit grant.
INSERT INTO permissions (id, code, description)
SELECT CAST('00000000-0000-0000-0000-000000000020' AS uuid), 'ORDER_WRITE', 'Confirm or reject VIP orders'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'ORDER_WRITE');

INSERT INTO role_permissions (role_id, permission_id)
SELECT roles.id, permissions.id
FROM roles
JOIN permissions ON permissions.code = 'ORDER_WRITE'
WHERE roles.code = 'SUPER_ADMIN'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions
      WHERE role_permissions.role_id = roles.id
        AND role_permissions.permission_id = permissions.id
  );
