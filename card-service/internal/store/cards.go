package store

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"strings"
	"time"
)

const cardColumns = `id, code, type_id, type_name, duration_minutes, state, max_rebinds, rebind_count,
	bound_machine, bound_at, activated_at, expires_at, online_session_id, online_machine, online_at,
	batch_no, remark, created_by, created_at, updated_at, deleted_at`

func scanCard(row rowScanner) (*Card, error) {
	var c Card
	err := row.Scan(
		&c.ID, &c.Code, &c.TypeID, &c.TypeName, &c.DurationMinutes, &c.State, &c.MaxRebinds, &c.RebindCount,
		&c.BoundMachine, &c.BoundAt, &c.ActivatedAt, &c.ExpiresAt, &c.OnlineSessionID, &c.OnlineMachine,
		&c.OnlineAt, &c.BatchNo, &c.Remark, &c.CreatedBy, &c.CreatedAt, &c.UpdatedAt, &c.DeletedAt,
	)
	if err != nil {
		return nil, err
	}
	return &c, nil
}

// CardByCode 按规范形卡密查询（不含已删除）。
func (t *Tx) CardByCode(ctx context.Context, code string) (*Card, error) {
	row := t.q.QueryRowContext(ctx, `SELECT `+cardColumns+` FROM cards WHERE code = ? AND deleted_at IS NULL`, code)
	c, err := scanCard(row)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, ErrNotFound
	}
	return c, err
}

// CardByID 按主键查询（不含已删除）。
func (t *Tx) CardByID(ctx context.Context, id int64) (*Card, error) {
	row := t.q.QueryRowContext(ctx, `SELECT `+cardColumns+` FROM cards WHERE id = ? AND deleted_at IS NULL`, id)
	c, err := scanCard(row)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, ErrNotFound
	}
	return c, err
}

// InsertCard 插入一张卡密并回填 ID。
func (t *Tx) InsertCard(ctx context.Context, c *Card) error {
	res, err := t.q.ExecContext(ctx, `INSERT INTO cards (
		code, type_id, type_name, duration_minutes, state, max_rebinds, rebind_count,
		bound_machine, bound_at, activated_at, expires_at, online_session_id, online_machine, online_at,
		batch_no, remark, created_by, created_at, updated_at, deleted_at
	) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)`,
		c.Code, c.TypeID, c.TypeName, c.DurationMinutes, c.State, c.MaxRebinds, c.RebindCount,
		c.BoundMachine, c.BoundAt, c.ActivatedAt, c.ExpiresAt, c.OnlineSessionID, c.OnlineMachine, c.OnlineAt,
		c.BatchNo, c.Remark, c.CreatedBy, c.CreatedAt, c.UpdatedAt, c.DeletedAt,
	)
	if err != nil {
		return err
	}
	c.ID, err = res.LastInsertId()
	return err
}

// UpdateCard 整行更新可变字段（调用方通常先读出来改再写回）。
func (t *Tx) UpdateCard(ctx context.Context, c *Card) error {
	_, err := t.q.ExecContext(ctx, `UPDATE cards SET
		type_id = ?, type_name = ?, duration_minutes = ?, state = ?, max_rebinds = ?, rebind_count = ?,
		bound_machine = ?, bound_at = ?, activated_at = ?, expires_at = ?, online_session_id = ?,
		online_machine = ?, online_at = ?, batch_no = ?, remark = ?, updated_at = ?
		WHERE id = ?`,
		c.TypeID, c.TypeName, c.DurationMinutes, c.State, c.MaxRebinds, c.RebindCount,
		c.BoundMachine, c.BoundAt, c.ActivatedAt, c.ExpiresAt, c.OnlineSessionID,
		c.OnlineMachine, c.OnlineAt, c.BatchNo, c.Remark, c.UpdatedAt, c.ID,
	)
	return err
}

// SoftDeleteCard 软删除卡密并清空在线态。
func (t *Tx) SoftDeleteCard(ctx context.Context, id, now int64) error {
	_, err := t.q.ExecContext(ctx,
		`UPDATE cards SET deleted_at = ?, updated_at = ?, online_session_id = NULL, online_machine = '', online_at = 0 WHERE id = ? AND deleted_at IS NULL`,
		now, now, id)
	return err
}

// CardFilter 是卡密列表/导出筛选条件。
type CardFilter struct {
	Keyword     string
	State       string // unused | active | expired | disabled | online
	TypeID      int64
	BatchNo     string
	CreatedFrom int64 // unix 秒，0 表示不限
	CreatedTo   int64 // unix 秒，0 表示不限
	Sort        string
	Page        int
	PageSize    int
	Limit       int // 导出上限，>0 时覆盖分页
}

func escapeLike(s string) string {
	s = strings.ReplaceAll(s, `\`, `\\`)
	s = strings.ReplaceAll(s, `%`, `\%`)
	s = strings.ReplaceAll(s, `_`, `\_`)
	return s
}

func (f CardFilter) where(now int64) (string, []any) {
	clauses := []string{"deleted_at IS NULL"}
	args := []any{}
	if kw := strings.TrimSpace(f.Keyword); kw != "" {
		like := "%" + escapeLike(kw) + "%"
		clauses = append(clauses, `(code LIKE ? ESCAPE '\' OR bound_machine LIKE ? ESCAPE '\' OR batch_no LIKE ? ESCAPE '\' OR remark LIKE ? ESCAPE '\')`)
		args = append(args, like, like, like, like)
	}
	switch f.State {
	case "unused":
		clauses = append(clauses, "state = 'unused'")
	case "active":
		clauses = append(clauses, "state = 'active' AND (expires_at IS NULL OR expires_at > ?)")
		args = append(args, now)
	case "expired":
		clauses = append(clauses, "state <> 'disabled' AND expires_at IS NOT NULL AND expires_at <= ?")
		args = append(args, now)
	case "disabled":
		clauses = append(clauses, "state = 'disabled'")
	case "online":
		clauses = append(clauses, "online_session_id IS NOT NULL")
	}
	if f.TypeID > 0 {
		clauses = append(clauses, "type_id = ?")
		args = append(args, f.TypeID)
	}
	if b := strings.TrimSpace(f.BatchNo); b != "" {
		clauses = append(clauses, "batch_no = ?")
		args = append(args, b)
	}
	if f.CreatedFrom > 0 {
		clauses = append(clauses, "created_at >= ?")
		args = append(args, f.CreatedFrom)
	}
	if f.CreatedTo > 0 {
		clauses = append(clauses, "created_at < ?")
		args = append(args, f.CreatedTo)
	}
	return strings.Join(clauses, " AND "), args
}

func (f CardFilter) orderBy() string {
	switch f.Sort {
	case "created_at_asc":
		return "ORDER BY id ASC"
	case "expires_at_asc":
		return "ORDER BY (expires_at IS NULL), expires_at ASC, id ASC"
	default:
		return "ORDER BY id DESC"
	}
}

// ListCards 分页查询卡密，返回总数。
func (t *Tx) ListCards(ctx context.Context, f CardFilter, now int64) ([]*Card, int, error) {
	where, args := f.where(now)
	var total int
	if err := t.q.QueryRowContext(ctx, `SELECT COUNT(*) FROM cards WHERE `+where, args...).Scan(&total); err != nil {
		return nil, 0, err
	}
	page, size := f.Page, f.PageSize
	if page < 1 {
		page = 1
	}
	if size < 1 || size > 200 {
		size = 20
	}
	query := fmt.Sprintf(`SELECT %s FROM cards WHERE %s %s LIMIT ? OFFSET ?`, cardColumns, where, f.orderBy())
	rows, err := t.q.QueryContext(ctx, query, append(append([]any{}, args...), size, (page-1)*size)...)
	if err != nil {
		return nil, 0, err
	}
	defer rows.Close()
	var out []*Card
	for rows.Next() {
		c, err := scanCard(rows)
		if err != nil {
			return nil, 0, err
		}
		out = append(out, c)
	}
	return out, total, rows.Err()
}

// ExportCards 导出（不分页，带上限）。
func (t *Tx) ExportCards(ctx context.Context, f CardFilter, now int64) ([]*Card, error) {
	where, args := f.where(now)
	limit := f.Limit
	if limit <= 0 || limit > 100000 {
		limit = 100000
	}
	query := fmt.Sprintf(`SELECT %s FROM cards WHERE %s %s LIMIT ?`, cardColumns, where, f.orderBy())
	rows, err := t.q.QueryContext(ctx, query, append(append([]any{}, args...), limit)...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []*Card
	for rows.Next() {
		c, err := scanCard(rows)
		if err != nil {
			return nil, err
		}
		out = append(out, c)
	}
	return out, rows.Err()
}

// CountCards 按卡密 ID 批量统计存在数量。
func (t *Tx) CountCardsByIDs(ctx context.Context, ids []int64) (int, error) {
	if len(ids) == 0 {
		return 0, nil
	}
	placeholders := strings.TrimSuffix(strings.Repeat("?,", len(ids)), ",")
	args := make([]any, 0, len(ids))
	for _, id := range ids {
		args = append(args, id)
	}
	var n int
	err := t.q.QueryRowContext(ctx,
		`SELECT COUNT(*) FROM cards WHERE deleted_at IS NULL AND id IN (`+placeholders+`)`, args...).Scan(&n)
	return n, err
}

// NextBatchNo 基于当天已有的最大批次号生成下一个批次号，形如 B20261013-0007。
func (t *Tx) NextBatchNo(ctx context.Context, day string, now int64) (string, error) {
	prefix := "B" + strings.ReplaceAll(day, "-", "")
	var maxNo sql.NullString
	err := t.q.QueryRowContext(ctx,
		`SELECT MAX(batch_no) FROM cards WHERE batch_no LIKE ?`, prefix+"%").Scan(&maxNo)
	if err != nil {
		return "", err
	}
	next := 1
	if maxNo.Valid && len(maxNo.String) > len(prefix) {
		// 形如 "B20261012-0007"：去掉分隔符后再取数字，避免 "-0007" 被 Sscanf 解析成负数。
		rest := strings.TrimPrefix(maxNo.String[len(prefix):], "-")
		var n int
		if _, err := fmt.Sscanf(rest, "%d", &n); err == nil && n >= 0 {
			next = n + 1
		}
	}
	return fmt.Sprintf("%s-%04d", prefix, next), nil
}

// TouchCardUpdated 只更新 updated_at（用于备注等轻量修改）。
func (t *Tx) TouchCardUpdated(ctx context.Context, id, now int64) error {
	_, err := t.q.ExecContext(ctx, `UPDATE cards SET updated_at = ? WHERE id = ?`, now, id)
	return err
}

// NowUnix 便于在 store 内统一取时间。
func NowUnix() int64 { return time.Now().Unix() }
