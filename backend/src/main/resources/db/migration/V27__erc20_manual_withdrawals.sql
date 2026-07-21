-- Disable legacy non-ERC20 pending withdrawals without touching historical settled rows.
-- The point-account non-negative constraint makes an inconsistent frozen balance fail the migration.
UPDATE point_accounts account
SET frozen_points = account.frozen_points - (
    SELECT COALESCE(SUM(request.point_amount), 0)
    FROM withdrawal_requests request
    WHERE request.user_id = account.user_id
      AND request.status = 'PENDING'
      AND request.network <> 'ERC20'
)
WHERE EXISTS (
    SELECT 1
    FROM withdrawal_requests request
    WHERE request.user_id = account.user_id
      AND request.status = 'PENDING'
      AND request.network <> 'ERC20'
);

UPDATE withdrawal_requests
SET status = 'REJECTED',
    admin_note = 'network disabled; please bind an ERC20 wallet and submit again',
    reviewed_by = 'SYSTEM_NETWORK_MIGRATION',
    reviewed_at = CURRENT_TIMESTAMP
WHERE status = 'PENDING'
  AND network <> 'ERC20';
