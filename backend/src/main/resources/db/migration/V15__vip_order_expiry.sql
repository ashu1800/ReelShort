-- V15: Add order expiry to vip_orders + configurable timeout

ALTER TABLE vip_orders ADD COLUMN expires_at timestamp with time zone;

INSERT INTO system_configs (config_key, config_value, description, updated_at)
VALUES ('vip.order-timeout-minutes', '20', 'VIP order payment timeout in minutes', now());
