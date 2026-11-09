-- V34: 每用户最多一笔 PENDING 提现，由数据库唯一约束兜底。
--
-- 与 vip_orders 相同的手法（pending_slot 哨兵列 + 复合唯一约束），因为 H2 不支持部分索引，
-- 而迁移测试要在 H2 上跑完全部脚本。

ALTER TABLE withdrawal_requests ADD COLUMN IF NOT EXISTS pending_slot varchar(24);

-- 历史数据里如果有同一用户的多笔 PENDING（旧版本只有应用层单机锁），
-- 保留最早一笔，其余标记 REJECTED；管理员可在后台按 reviewed_by='system' 找到它们。
UPDATE withdrawal_requests
SET status = 'REJECTED',
    admin_note = 'auto rejected: duplicate pending withdrawal',
    reviewed_by = 'system',
    reviewed_at = now()
WHERE id IN (
    SELECT id FROM (
        SELECT id, row_number() OVER (PARTITION BY user_id ORDER BY created_at, id) AS position
        FROM withdrawal_requests
        WHERE status = 'PENDING'
    ) ranked
    WHERE position > 1
);

-- 被上面强制拒绝的那些用户，冻结积分按仍然 PENDING 的申请重算（冻结点只来自提现申请）。
UPDATE point_accounts account
SET frozen_points = COALESCE((
    SELECT SUM(request.point_amount) FROM withdrawal_requests request
    WHERE request.user_id = account.user_id AND request.status = 'PENDING'
), 0)
WHERE account.user_id IN (
    SELECT user_id FROM withdrawal_requests
    WHERE status = 'REJECTED' AND reviewed_by = 'system'
      AND admin_note = 'auto rejected: duplicate pending withdrawal'
);

UPDATE withdrawal_requests
SET pending_slot = CASE WHEN status = 'PENDING' THEN 'PENDING' ELSE NULL END;

ALTER TABLE withdrawal_requests ADD CONSTRAINT uk_withdrawal_requests_user_pending_slot
    unique (user_id, pending_slot);

ALTER TABLE withdrawal_requests ADD CONSTRAINT ck_withdrawal_requests_pending_slot
    check (
        (status = 'PENDING' AND pending_slot = 'PENDING')
        OR
        (status <> 'PENDING' AND pending_slot IS NULL)
    );
