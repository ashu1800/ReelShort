ALTER TABLE withdrawal_payout_attempts ALTER COLUMN signed_raw_transaction DROP NOT NULL;
ALTER TABLE withdrawal_payout_attempts ADD COLUMN gas_price numeric(38, 0);
ALTER TABLE withdrawal_payout_attempts ADD COLUMN signing_owner varchar(64);
ALTER TABLE withdrawal_payout_attempts ADD COLUMN signing_lease_until timestamp with time zone;
ALTER TABLE withdrawal_payout_attempts ADD COLUMN unknown_count integer NOT NULL DEFAULT 0;
ALTER TABLE withdrawal_payout_attempts ADD COLUMN unknown_first_seen timestamp with time zone;

ALTER TABLE withdrawal_payout_attempts DROP CONSTRAINT ck_withdrawal_payout_attempt_active_slot;
ALTER TABLE withdrawal_payout_attempts ADD CONSTRAINT ck_withdrawal_payout_attempt_active_slot check (
    (
        status IN ('SIGNING', 'PREPARED', 'BROADCASTED', 'MANUAL_REVIEW')
		AND active_slot IS NOT NULL
        AND active_slot = 'ACTIVE'
    )
    OR
    (status IN ('CONFIRMED', 'FAILED_RETRYABLE') AND active_slot IS NULL)
);

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
		status IN ('MANUAL_REVIEW', 'FAILED_RETRYABLE')
		AND signing_owner IS NULL
		AND signing_lease_until IS NULL
		AND (
			(signed_raw_transaction IS NULL AND tx_hash IS NULL)
			OR (signed_raw_transaction IS NOT NULL AND tx_hash IS NOT NULL)
		)
	)
);

ALTER TABLE withdrawal_payout_attempts ADD CONSTRAINT ck_withdrawal_payout_attempt_unknown_count
    check (unknown_count >= 0);
