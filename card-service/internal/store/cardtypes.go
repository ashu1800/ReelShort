package store

import (
	"context"
	"database/sql"
	"errors"
)

const cardTypeColumns = `id, name, duration_minutes, max_rebinds, enabled, sort_order, created_at, updated_at`

func scanCardType(row rowScanner) (*CardType, error) {
	var t CardType
	var enabled int
	if err := row.Scan(&t.ID, &t.Name, &t.DurationMinutes, &t.MaxRebinds, &enabled, &t.SortOrder,
		&t.CreatedAt, &t.UpdatedAt); err != nil {
		return nil, err
	}
	t.Enabled = enabled != 0
	return &t, nil
}

// ListCardTypes 列出全部卡密类型（含在用卡密数量）。
func (t *Tx) ListCardTypes(ctx context.Context) ([]*CardType, error) {
	rows, err := t.q.QueryContext(ctx, `SELECT `+cardTypeColumns+`,
		(SELECT COUNT(*) FROM cards c WHERE c.type_id = card_types.id AND c.deleted_at IS NULL) AS card_count
		FROM card_types ORDER BY sort_order ASC, id ASC`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []*CardType
	for rows.Next() {
		var ct CardType
		var enabled int
		if err := rows.Scan(&ct.ID, &ct.Name, &ct.DurationMinutes, &ct.MaxRebinds, &enabled, &ct.SortOrder,
			&ct.CreatedAt, &ct.UpdatedAt, &ct.CardCount); err != nil {
			return nil, err
		}
		ct.Enabled = enabled != 0
		out = append(out, &ct)
	}
	return out, rows.Err()
}

// CardTypeByID 按主键查询类型。
func (t *Tx) CardTypeByID(ctx context.Context, id int64) (*CardType, error) {
	row := t.q.QueryRowContext(ctx, `SELECT `+cardTypeColumns+` FROM card_types WHERE id = ?`, id)
	ct, err := scanCardType(row)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, ErrNotFound
	}
	return ct, err
}

// InsertCardType 新建类型。
func (t *Tx) InsertCardType(ctx context.Context, ct *CardType) error {
	enabled := 0
	if ct.Enabled {
		enabled = 1
	}
	res, err := t.q.ExecContext(ctx, `INSERT INTO card_types
		(name, duration_minutes, max_rebinds, enabled, sort_order, created_at, updated_at) VALUES (?,?,?,?,?,?,?)`,
		ct.Name, ct.DurationMinutes, ct.MaxRebinds, enabled, ct.SortOrder, ct.CreatedAt, ct.UpdatedAt)
	if err != nil {
		return err
	}
	ct.ID, err = res.LastInsertId()
	return err
}

// UpdateCardType 更新类型。
func (t *Tx) UpdateCardType(ctx context.Context, ct *CardType) error {
	enabled := 0
	if ct.Enabled {
		enabled = 1
	}
	_, err := t.q.ExecContext(ctx, `UPDATE card_types SET
		name = ?, duration_minutes = ?, max_rebinds = ?, enabled = ?, sort_order = ?, updated_at = ? WHERE id = ?`,
		ct.Name, ct.DurationMinutes, ct.MaxRebinds, enabled, ct.SortOrder, ct.UpdatedAt, ct.ID)
	return err
}

// DeleteCardType 删除类型。
func (t *Tx) DeleteCardType(ctx context.Context, id int64) error {
	_, err := t.q.ExecContext(ctx, `DELETE FROM card_types WHERE id = ?`, id)
	return err
}

// CountCardsByType 统计某类型下的在用卡密数量（删除类型前的引用检查）。
func (t *Tx) CountCardsByType(ctx context.Context, typeID int64) (int, error) {
	var n int
	err := t.q.QueryRowContext(ctx,
		`SELECT COUNT(*) FROM cards WHERE type_id = ? AND deleted_at IS NULL`, typeID).Scan(&n)
	return n, err
}

// EnsureCardTypes 预置类型（幂等，按名称唯一）。
func (t *Tx) EnsureCardTypes(ctx context.Context, types []*CardType) error {
	for _, ct := range types {
		enabled := 0
		if ct.Enabled {
			enabled = 1
		}
		if _, err := t.q.ExecContext(ctx, `INSERT OR IGNORE INTO card_types
			(name, duration_minutes, max_rebinds, enabled, sort_order, created_at, updated_at) VALUES (?,?,?,?,?,?,?)`,
			ct.Name, ct.DurationMinutes, ct.MaxRebinds, enabled, ct.SortOrder, ct.CreatedAt, ct.UpdatedAt); err != nil {
			return err
		}
	}
	return nil
}

var _ = sql.ErrNoRows
