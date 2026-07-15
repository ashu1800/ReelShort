-- V14: Add unique_suffix to vip_orders for amount-based payment matching.
-- Each order gets a unique micro-amount suffix so incoming USDT transfers can be
-- auto-matched to orders without manual txHash entry.

ALTER TABLE vip_orders ADD COLUMN unique_suffix integer NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_vip_orders_unique_suffix_pending
    ON vip_orders (unique_suffix, status);
