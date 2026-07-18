-- Transaction hashes are case-insensitive on both Tron and EVM networks.
-- Normalize legacy values before enforcing the canonical lower-case form.
UPDATE vip_orders
SET tx_hash = lower(trim(tx_hash))
WHERE tx_hash IS NOT NULL;

UPDATE withdrawal_requests
SET tx_hash = lower(trim(tx_hash))
WHERE tx_hash IS NOT NULL;

UPDATE withdrawal_payout_attempts
SET tx_hash = lower(trim(tx_hash))
WHERE tx_hash IS NOT NULL;

ALTER TABLE vip_orders
    ADD CONSTRAINT ck_vip_orders_tx_hash_lower
    CHECK (tx_hash IS NULL OR tx_hash = lower(tx_hash));

ALTER TABLE withdrawal_requests
    ADD CONSTRAINT ck_withdrawal_requests_tx_hash_lower
    CHECK (tx_hash IS NULL OR tx_hash = lower(tx_hash));

ALTER TABLE withdrawal_payout_attempts
    ADD CONSTRAINT ck_withdrawal_payout_attempts_tx_hash_lower
    CHECK (tx_hash IS NULL OR tx_hash = lower(tx_hash));

ALTER TABLE vip_transfer_scan_cursors
    ADD CONSTRAINT ck_vip_transfer_scan_cursor_fingerprint_length
    CHECK (fingerprint IS NULL OR length(fingerprint) <= 512);
