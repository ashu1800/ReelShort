package store

import (
	"context"
	"fmt"
	"strings"
)

// InsertCardLog 写入一条卡密操作日志。
func (t *Tx) InsertCardLog(ctx context.Context, l *CardLog) error {
	_, err := t.q.ExecContext(ctx, `INSERT INTO card_logs (
		card_id, code_masked, action, result, message, machine_code, ip, user_agent, created_at
	) VALUES (?,?,?,?,?,?,?,?,?)`,
		l.CardID, l.CodeMasked, l.Action, l.Result, l.Message, l.MachineCode, l.IP, l.UserAgent, l.CreatedAt)
	return err
}

// LogFilter 是日志筛选。
type LogFilter struct {
	CardCode string
	Action   string
	Result   string
	From     int64
	To       int64
	Page     int
	PageSize int
}

// ListCardLogs 分页查询卡密操作日志。
func (t *Tx) ListCardLogs(ctx context.Context, f LogFilter) ([]*CardLog, int, error) {
	clauses := []string{"1 = 1"}
	args := []any{}
	if code := strings.TrimSpace(f.CardCode); code != "" {
		clauses = append(clauses, "(card_id IN (SELECT id FROM cards WHERE code = ?) OR code_masked = ?)")
		args = append(args, code, code)
	}
	if a := strings.TrimSpace(f.Action); a != "" {
		clauses = append(clauses, "action = ?")
		args = append(args, a)
	}
	if r := strings.TrimSpace(f.Result); r != "" {
		clauses = append(clauses, "result = ?")
		args = append(args, r)
	}
	if f.From > 0 {
		clauses = append(clauses, "created_at >= ?")
		args = append(args, f.From)
	}
	if f.To > 0 {
		clauses = append(clauses, "created_at < ?")
		args = append(args, f.To)
	}
	where := strings.Join(clauses, " AND ")
	var total int
	if err := t.q.QueryRowContext(ctx, `SELECT COUNT(*) FROM card_logs WHERE `+where, args...).Scan(&total); err != nil {
		return nil, 0, err
	}
	page, size := f.Page, f.PageSize
	if page < 1 {
		page = 1
	}
	if size < 1 || size > 200 {
		size = 20
	}
	query := fmt.Sprintf(`SELECT id, card_id, code_masked, action, result, message, machine_code, ip, user_agent, created_at
		FROM card_logs WHERE %s ORDER BY id DESC LIMIT ? OFFSET ?`, where)
	rows, err := t.q.QueryContext(ctx, query, append(append([]any{}, args...), size, (page-1)*size)...)
	if err != nil {
		return nil, 0, err
	}
	defer rows.Close()
	var out []*CardLog
	for rows.Next() {
		var l CardLog
		if err := rows.Scan(&l.ID, &l.CardID, &l.CodeMasked, &l.Action, &l.Result, &l.Message,
			&l.MachineCode, &l.IP, &l.UserAgent, &l.CreatedAt); err != nil {
			return nil, 0, err
		}
		out = append(out, &l)
	}
	return out, total, rows.Err()
}

// ListLogsByCard 查询某卡最近 N 条日志（详情页用）。
func (t *Tx) ListLogsByCard(ctx context.Context, cardID int64, limit int) ([]*CardLog, error) {
	if limit <= 0 {
		limit = 50
	}
	rows, err := t.q.QueryContext(ctx,
		`SELECT id, card_id, code_masked, action, result, message, machine_code, ip, user_agent, created_at
		 FROM card_logs WHERE card_id = ? ORDER BY id DESC LIMIT ?`, cardID, limit)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []*CardLog
	for rows.Next() {
		var l CardLog
		if err := rows.Scan(&l.ID, &l.CardID, &l.CodeMasked, &l.Action, &l.Result, &l.Message,
			&l.MachineCode, &l.IP, &l.UserAgent, &l.CreatedAt); err != nil {
			return nil, err
		}
		out = append(out, &l)
	}
	return out, rows.Err()
}

// CountLogs 统计某动作/结果在时间窗内的条数（首页"今日"指标用）。
func (t *Tx) CountLogs(ctx context.Context, action, result string, from, to int64) (int, error) {
	clauses := []string{"created_at >= ?", "created_at < ?"}
	args := []any{from, to}
	if action != "" {
		clauses = append(clauses, "action = ?")
		args = append(args, action)
	}
	if result != "" {
		clauses = append(clauses, "result = ?")
		args = append(args, result)
	}
	var n int
	err := t.q.QueryRowContext(ctx,
		`SELECT COUNT(*) FROM card_logs WHERE `+strings.Join(clauses, " AND "), args...).Scan(&n)
	return n, err
}

// InsertAuditLog 写入管理员审计日志。
func (t *Tx) InsertAuditLog(ctx context.Context, a *AuditLog) error {
	_, err := t.q.ExecContext(ctx, `INSERT INTO admin_audit_logs (
		admin_id, username, action, target_type, target_id, detail, ip, created_at
	) VALUES (?,?,?,?,?,?,?,?)`,
		a.AdminID, a.Username, a.Action, a.TargetType, a.TargetID, a.Detail, a.IP, a.CreatedAt)
	return err
}

// AuditFilter 是审计日志筛选。
type AuditFilter struct {
	Admin    string
	Action   string
	From     int64
	To       int64
	Page     int
	PageSize int
}

// ListAuditLogs 分页查询审计日志。
func (t *Tx) ListAuditLogs(ctx context.Context, f AuditFilter) ([]*AuditLog, int, error) {
	clauses := []string{"1 = 1"}
	args := []any{}
	if a := strings.TrimSpace(f.Admin); a != "" {
		clauses = append(clauses, "username = ?")
		args = append(args, a)
	}
	if a := strings.TrimSpace(f.Action); a != "" {
		clauses = append(clauses, "action = ?")
		args = append(args, a)
	}
	if f.From > 0 {
		clauses = append(clauses, "created_at >= ?")
		args = append(args, f.From)
	}
	if f.To > 0 {
		clauses = append(clauses, "created_at < ?")
		args = append(args, f.To)
	}
	where := strings.Join(clauses, " AND ")
	var total int
	if err := t.q.QueryRowContext(ctx, `SELECT COUNT(*) FROM admin_audit_logs WHERE `+where, args...).Scan(&total); err != nil {
		return nil, 0, err
	}
	page, size := f.Page, f.PageSize
	if page < 1 {
		page = 1
	}
	if size < 1 || size > 200 {
		size = 20
	}
	query := fmt.Sprintf(`SELECT id, admin_id, username, action, target_type, target_id, detail, ip, created_at
		FROM admin_audit_logs WHERE %s ORDER BY id DESC LIMIT ? OFFSET ?`, where)
	rows, err := t.q.QueryContext(ctx, query, append(append([]any{}, args...), size, (page-1)*size)...)
	if err != nil {
		return nil, 0, err
	}
	defer rows.Close()
	var out []*AuditLog
	for rows.Next() {
		var a AuditLog
		if err := rows.Scan(&a.ID, &a.AdminID, &a.Username, &a.Action, &a.TargetType, &a.TargetID,
			&a.Detail, &a.IP, &a.CreatedAt); err != nil {
			return nil, 0, err
		}
		out = append(out, &a)
	}
	return out, total, rows.Err()
}
