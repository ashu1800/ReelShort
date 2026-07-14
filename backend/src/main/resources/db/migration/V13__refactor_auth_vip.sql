-- V13: Auth model refactor (username+password, remove phone/SMS) + VIP feature
-- Portable across PostgreSQL and H2 (uses DELETE FROM ordered by FK dependencies
-- instead of PostgreSQL-only TRUNCATE TABLE ... CASCADE).

-- 1. Clear all user-dependent data (auth model fundamentally changes).
--    Order matters: child tables first, then the parent (users) last so no FK
--    constraint is violated. Every table below has a FK to users except users.
DELETE FROM point_transfers;
DELETE FROM withdrawal_requests;
DELETE FROM watch_episode_reward_claims;
DELETE FROM watch_records;
DELETE FROM point_transactions;
DELETE FROM point_accounts;
DELETE FROM recharge_orders;
DELETE FROM payment_events;
DELETE FROM user_wallets;
DELETE FROM sms_verification_codes;
DELETE FROM access_tokens;
DELETE FROM likes;
DELETE FROM favorites;
DELETE FROM comments;
DELETE FROM users;

-- 2. Remove phone columns from users
ALTER TABLE users DROP COLUMN IF EXISTS phone_country_code;
ALTER TABLE users DROP COLUMN IF EXISTS phone_number;
ALTER TABLE users DROP COLUMN IF EXISTS phone_e164;

-- 3. Add VIP expiry to users
ALTER TABLE users ADD COLUMN IF NOT EXISTS vip_until timestamp with time zone;

-- 4. Drop SMS verification codes table (no longer used)
DROP TABLE IF EXISTS sms_verification_codes;

-- 5. Drop point_transfers table (SMS-dependent transfer feature removed)
DROP TABLE IF EXISTS point_transfers;

-- 6. VIP orders table
CREATE TABLE IF NOT EXISTS vip_orders (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    order_no varchar(64) NOT NULL,
    usdt_amount numeric(18, 6) NOT NULL,
    tx_hash varchar(128),
    status varchar(24) NOT NULL,
    payment_method varchar(32) NOT NULL,
    confirmed_by varchar(64),
    created_at timestamp with time zone NOT NULL,
    confirmed_at timestamp with time zone,
    primary key (id),
    constraint uk_vip_orders_order_no unique (order_no),
    constraint fk_vip_orders_user foreign key (user_id) references users (id)
);

CREATE INDEX IF NOT EXISTS idx_vip_orders_user_created
    ON vip_orders (user_id, created_at);

CREATE INDEX IF NOT EXISTS idx_vip_orders_status_created
    ON vip_orders (status, created_at);

-- 7. Captcha challenges table
CREATE TABLE IF NOT EXISTS captcha_challenges (
    id uuid NOT NULL,
    answer varchar(8) NOT NULL,
    image_base64 text NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    used_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    primary key (id)
);

CREATE INDEX IF NOT EXISTS idx_captcha_expires
    ON captcha_challenges (expires_at);

-- 8. Bank card attempt tracking table
CREATE TABLE IF NOT EXISTS bank_card_attempts (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    card_number_last4 varchar(4) NOT NULL,
    attempt_count integer NOT NULL DEFAULT 0,
    locked_until timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    primary key (id),
    constraint fk_bank_card_attempts_user foreign key (user_id) references users (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_bank_card_attempts_user
    ON bank_card_attempts (user_id);

-- 9. VIP price system config.
--    Avoid PostgreSQL-only "ON CONFLICT": the users table (and everything seeded
--    from it) was just cleared, so these keys cannot pre-exist. A plain INSERT
--    works on both PostgreSQL and H2.
INSERT INTO system_configs (config_key, config_value, description, updated_at)
VALUES ('vip.price-usdt', '15', 'VIP monthly subscription price in USDT', now());

INSERT INTO system_configs (config_key, config_value, description, updated_at)
VALUES ('vip.free-episodes', '7', 'Number of free episodes viewable without VIP', now());
