-- V13: Add username/password VIP capabilities without destroying the legacy
-- phone/SMS authentication model or any user-owned financial data.

-- 1. Add VIP expiry to users. Legacy phone columns remain available for a
-- controlled application-level transition.
ALTER TABLE users ADD COLUMN IF NOT EXISTS vip_until timestamp with time zone;

-- 2. VIP orders table
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

-- 3. Captcha challenges table
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

-- 4. Bank card attempt tracking table
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

-- 5. VIP system config. Existing operator values win during an upgrade.
INSERT INTO system_configs (config_key, config_value, description, updated_at)
SELECT 'vip.price-usdt', '15', 'VIP monthly subscription price in USDT', now()
WHERE NOT EXISTS (SELECT 1 FROM system_configs WHERE config_key = 'vip.price-usdt');

INSERT INTO system_configs (config_key, config_value, description, updated_at)
SELECT 'vip.free-episodes', '7', 'Number of free episodes viewable without VIP', now()
WHERE NOT EXISTS (SELECT 1 FROM system_configs WHERE config_key = 'vip.free-episodes');
