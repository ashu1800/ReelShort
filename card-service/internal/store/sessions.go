package store

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"strings"
)

const sessionColumns = `id, token_hash, card_id, machine_code, ip, user_agent, created_at, last_heartbeat_at, expires_at, closed_at, close_reason`

func scanSession(row rowScanner) (*Session, error) {
	var s Session
	err := row.Scan(&s.ID, &s.TokenHash, &s.CardID, &s.MachineCode, &s.IP, &s.UserAgent,
		&s.CreatedAt, &s.LastHeartbeatAt, &s.ExpiresAt, &s.ClosedAt, &s.CloseReason)
	if err != nil {
		return nil, err
	}
	return &s, nil
}

// InsertSession 新建会话并回填 ID。
func (t *Tx) InsertSession(ctx context.Context, s *Session) error {
	res, err := t.q.ExecContext(ctx, `INSERT INTO card_sessions (
		token_hash, card_id, machine_code, ip, user_agent, created_at, last_heartbeat_at, expires_at, closed_at, close_reason
	) VALUES (?,?,?,?,?,?,?,?,NULL,'')`,
		s.TokenHash, s.CardID, s.MachineCode, s.IP, s.UserAgent, s.CreatedAt, s.LastHeartbeatAt, s.ExpiresAt)
	if err != nil {
		return err
	}
	s.ID, err = res.LastInsertId()
	return err
}

// SessionByTokenHash 按令牌哈希查询（含已关闭会话，便于区分"被顶号"与"不存在"）。
func (t *Tx) SessionByTokenHash(ctx context.Context, hash string) (*Session, error) {
	row := t.q.QueryRowContext(ctx, `SELECT `+sessionColumns+` FROM card_sessions WHERE token_hash = ?`, hash)
	s, err := scanSession(row)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, ErrNotFound
	}
	return s, err
}

// SessionByID 按主键查询。
func (t *Tx) SessionByID(ctx context.Context, id int64) (*Session, error) {
	row := t.q.QueryRowContext(ctx, `SELECT `+sessionColumns+` FROM card_sessions WHERE id = ?`, id)
	s, err := scanSession(row)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, ErrNotFound
	}
	return s, err
}

// UpdateSessionHeartbeat 滑动续期。
func (t *Tx) UpdateSessionHeartbeat(ctx context.Context, id, at, expiresAt int64) error {
	_, err := t.q.ExecContext(ctx,
		`UPDATE card_sessions SET last_heartbeat_at = ?, expires_at = ? WHERE id = ? AND closed_at IS NULL`,
		at, expiresAt, id)
	return err
}

// ActiveSessionByCard 查询该卡当前有效会话（未关闭且未超时）。
func (t *Tx) ActiveSessionByCard(ctx context.Context, cardID, now int64) (*Session, error) {
	row := t.q.QueryRowContext(ctx,
		`SELECT `+sessionColumns+` FROM card_sessions WHERE card_id = ? AND closed_at IS NULL AND expires_at > ?
		 ORDER BY id DESC LIMIT 1`, cardID, now)
	s, err := scanSession(row)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, ErrNotFound
	}
	return s, err
}

// CloseSession 关闭单个会话。
func (t *Tx) CloseSession(ctx context.Context, id, now int64, reason string) error {
	_, err := t.q.ExecContext(ctx,
		`UPDATE card_sessions SET closed_at = ?, close_reason = ? WHERE id = ? AND closed_at IS NULL`,
		now, reason, id)
	return err
}

// CloseOpenSessionsByCard 关闭该卡全部未关闭会话，返回关闭数量。
func (t *Tx) CloseOpenSessionsByCard(ctx context.Context, cardID, now int64, reason string) (int, error) {
	res, err := t.q.ExecContext(ctx,
		`UPDATE card_sessions SET closed_at = ?, close_reason = ? WHERE card_id = ? AND closed_at IS NULL`,
		now, reason, cardID)
	if err != nil {
		return 0, err
	}
	n, _ := res.RowsAffected()
	return int(n), nil
}

// SetCardOnline 把卡标记为在线。
func (t *Tx) SetCardOnline(ctx context.Context, cardID, sessionID, now int64, machine string) error {
	_, err := t.q.ExecContext(ctx,
		`UPDATE cards SET online_session_id = ?, online_machine = ?, online_at = ?, updated_at = ? WHERE id = ?`,
		sessionID, machine, now, now, cardID)
	return err
}

// ClearCardOnline 清除卡的在线标记。
func (t *Tx) ClearCardOnline(ctx context.Context, cardID, now int64) error {
	_, err := t.q.ExecContext(ctx,
		`UPDATE cards SET online_session_id = NULL, online_machine = '', online_at = 0, updated_at = ? WHERE id = ?`,
		now, cardID)
	return err
}

// CloseTimedOutSessions 关闭超时会话，并清理指向已关闭会话的卡在线标记。返回关闭数量。
func (t *Tx) CloseTimedOutSessions(ctx context.Context, now int64) (int, error) {
	res, err := t.q.ExecContext(ctx,
		`UPDATE card_sessions SET closed_at = ?, close_reason = 'timeout'
		 WHERE closed_at IS NULL AND expires_at <= ?`, now, now)
	if err != nil {
		return 0, err
	}
	n, _ := res.RowsAffected()
	if _, err := t.q.ExecContext(ctx,
		`UPDATE cards SET online_session_id = NULL, online_machine = '', online_at = 0, updated_at = ?
		 WHERE online_session_id IS NOT NULL
		   AND online_session_id IN (SELECT id FROM card_sessions WHERE closed_at IS NOT NULL)`, now); err != nil {
		return int(n), err
	}
	return int(n), nil
}

// CountOpenSessions 统计当前有效会话数。
func (t *Tx) CountOpenSessions(ctx context.Context, now int64) (int, error) {
	var n int
	err := t.q.QueryRowContext(ctx,
		`SELECT COUNT(*) FROM card_sessions WHERE closed_at IS NULL AND expires_at > ?`, now).Scan(&n)
	return n, err
}

// SessionFilter 是会话列表筛选。
type SessionFilter struct {
	Active   bool
	CardCode string
	Page     int
	PageSize int
}

// SessionView 是带卡密展示信息的会话行。
type SessionView struct {
	Session
	CardCode string
}

// ListSessions 分页查询会话。
func (t *Tx) ListSessions(ctx context.Context, f SessionFilter, now int64) ([]SessionView, int, error) {
	clauses := []string{"1 = 1"}
	args := []any{}
	if f.Active {
		clauses = append(clauses, "s.closed_at IS NULL AND s.expires_at > ?")
		args = append(args, now)
	}
	if code := strings.TrimSpace(f.CardCode); code != "" {
		clauses = append(clauses, "c.code = ?")
		args = append(args, code)
	}
	where := strings.Join(clauses, " AND ")
	var total int
	if err := t.q.QueryRowContext(ctx,
		`SELECT COUNT(*) FROM card_sessions s JOIN cards c ON c.id = s.card_id WHERE `+where, args...).Scan(&total); err != nil {
		return nil, 0, err
	}
	page, size := f.Page, f.PageSize
	if page < 1 {
		page = 1
	}
	if size < 1 || size > 200 {
		size = 20
	}
	query := fmt.Sprintf(`SELECT s.id, s.token_hash, s.card_id, s.machine_code, s.ip, s.user_agent,
		s.created_at, s.last_heartbeat_at, s.expires_at, s.closed_at, s.close_reason, c.code
		FROM card_sessions s JOIN cards c ON c.id = s.card_id
		WHERE %s ORDER BY s.id DESC LIMIT ? OFFSET ?`, where)
	rows, err := t.q.QueryContext(ctx, query, append(append([]any{}, args...), size, (page-1)*size)...)
	if err != nil {
		return nil, 0, err
	}
	defer rows.Close()
	var out []SessionView
	for rows.Next() {
		var sv SessionView
		if err := rows.Scan(&sv.ID, &sv.TokenHash, &sv.CardID, &sv.MachineCode, &sv.IP, &sv.UserAgent,
			&sv.CreatedAt, &sv.LastHeartbeatAt, &sv.ExpiresAt, &sv.ClosedAt, &sv.CloseReason, &sv.CardCode); err != nil {
			return nil, 0, err
		}
		out = append(out, sv)
	}
	return out, total, rows.Err()
}

// ListSessionsByCard 查询某卡最近 N 条会话（详情页用）。
func (t *Tx) ListSessionsByCard(ctx context.Context, cardID int64, limit int) ([]*Session, error) {
	if limit <= 0 {
		limit = 50
	}
	rows, err := t.q.QueryContext(ctx,
		`SELECT `+sessionColumns+` FROM card_sessions WHERE card_id = ? ORDER BY id DESC LIMIT ?`, cardID, limit)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []*Session
	for rows.Next() {
		s, err := scanSession(rows)
		if err != nil {
			return nil, err
		}
		out = append(out, s)
	}
	return out, rows.Err()
}
