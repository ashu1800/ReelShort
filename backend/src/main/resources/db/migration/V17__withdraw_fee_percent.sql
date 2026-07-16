INSERT INTO system_configs (config_key, config_value, description, updated_at)
VALUES ('withdraw.fee-percent', '10', 'Withdrawal fee percentage deducted from points.', now());
ALTER TABLE withdrawal_requests ADD COLUMN fee_amount integer NOT NULL DEFAULT 0;
