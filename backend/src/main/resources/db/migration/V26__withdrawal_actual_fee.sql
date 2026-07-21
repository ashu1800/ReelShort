ALTER TABLE withdrawal_payout_attempts
    ADD COLUMN actual_fee_amount numeric(36,18);

ALTER TABLE withdrawal_payout_attempts
    ADD COLUMN actual_fee_asset varchar(8);

ALTER TABLE withdrawal_payout_attempts
    ADD CONSTRAINT ck_withdrawal_actual_fee_pair
    CHECK (
        (actual_fee_amount IS NULL AND actual_fee_asset IS NULL)
        OR (actual_fee_amount IS NOT NULL AND actual_fee_amount >= 0 AND actual_fee_asset IS NOT NULL)
    );
