package store

import (
	"context"
	"database/sql"
	"errors"
)

const adminColumns = `id, username, password_hash, display_name, must_change_password, created_at, updated_at, last_login_at`

func scanAdmin(row rowScanner) (*Admin, error) {
	var a Admin
	var must int
	if err := row.Scan(&a.ID, &a.Username, &a.PasswordHash, &a.DisplayName, &must,
		&a.CreatedAt, &a.UpdatedAt, &a.LastLoginAt); err != nil {
		return nil, err
	}
	a.MustChangePassword = must != 0
	return &a, nil
}

// AdminByUsername 按用户名查询管理员。
func (t *Tx) AdminByUsername(ctx context.Context, username string) (*Admin, error) {
	row := t.q.QueryRowContext(ctx, `SELECT `+adminColumns+` FROM admin_users WHERE username = ?`, username)
	a, err := scanAdmin(row)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, ErrNotFound
	}
	return a, err
}

// AdminByID 按主键查询管理员。
func (t *Tx) AdminByID(ctx context.Context, id int64) (*Admin, error) {
	row := t.q.QueryRowContext(ctx, `SELECT `+adminColumns+` FROM admin_users WHERE id = ?`, id)
	a, err := scanAdmin(row)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, ErrNotFound
	}
	return a, err
}

// CountAdmins 统计管理员数量（判断是否需要创建初始管理员）。
func (t *Tx) CountAdmins(ctx context.Context) (int, error) {
	var n int
	err := t.q.QueryRowContext(ctx, `SELECT COUNT(*) FROM admin_users`).Scan(&n)
	return n, err
}

// InsertAdmin 新建管理员。
func (t *Tx) InsertAdmin(ctx context.Context, a *Admin) error {
	must := 0
	if a.MustChangePassword {
		must = 1
	}
	res, err := t.q.ExecContext(ctx, `INSERT INTO admin_users
		(username, password_hash, display_name, must_change_password, created_at, updated_at, last_login_at)
		VALUES (?,?,?,?,?,?,?)`,
		a.Username, a.PasswordHash, a.DisplayName, must, a.CreatedAt, a.UpdatedAt, a.LastLoginAt)
	if err != nil {
		return err
	}
	a.ID, err = res.LastInsertId()
	return err
}

// UpdateAdminPassword 更新密码哈希，并按需重新置位强制改密标记。
func (t *Tx) UpdateAdminPassword(ctx context.Context, id int64, hash string, mustChange bool, now int64) error {
	must := 0
	if mustChange {
		must = 1
	}
	_, err := t.q.ExecContext(ctx,
		`UPDATE admin_users SET password_hash = ?, must_change_password = ?, updated_at = ? WHERE id = ?`,
		hash, must, now, id)
	return err
}

// UpdateAdminLastLogin 更新最后登录时间。
func (t *Tx) UpdateAdminLastLogin(ctx context.Context, id, now int64) error {
	_, err := t.q.ExecContext(ctx, `UPDATE admin_users SET last_login_at = ? WHERE id = ?`, now, id)
	return err
}

// InsertAdminSession 新建后台会话。
func (t *Tx) InsertAdminSession(ctx context.Context, s *AdminSession) error {
	res, err := t.q.ExecContext(ctx, `INSERT INTO admin_sessions
		(token_hash, admin_id, ip, user_agent, created_at, expires_at, last_seen_at) VALUES (?,?,?,?,?,?,?)`,
		s.TokenHash, s.AdminID, s.IP, s.UserAgent, s.CreatedAt, s.ExpiresAt, s.LastSeenAt)
	if err != nil {
		return err
	}
	s.ID, err = res.LastInsertId()
	return err
}

// AdminSessionByTokenHash 按哈希查询后台会话。
func (t *Tx) AdminSessionByTokenHash(ctx context.Context, hash string) (*AdminSession, error) {
	var s AdminSession
	err := t.q.QueryRowContext(ctx,
		`SELECT id, token_hash, admin_id, ip, user_agent, created_at, expires_at, last_seen_at
		 FROM admin_sessions WHERE token_hash = ?`, hash).
		Scan(&s.ID, &s.TokenHash, &s.AdminID, &s.IP, &s.UserAgent, &s.CreatedAt, &s.ExpiresAt, &s.LastSeenAt)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, ErrNotFound
	}
	if err != nil {
		return nil, err
	}
	return &s, nil
}

// TouchAdminSession 滑动续期。
func (t *Tx) TouchAdminSession(ctx context.Context, id, expiresAt, seenAt int64) error {
	_, err := t.q.ExecContext(ctx,
		`UPDATE admin_sessions SET expires_at = ?, last_seen_at = ? WHERE id = ?`, expiresAt, seenAt, id)
	return err
}

// DeleteAdminSession 删除单个后台会话（退出登录）。
func (t *Tx) DeleteAdminSession(ctx context.Context, id int64) error {
	_, err := t.q.ExecContext(ctx, `DELETE FROM admin_sessions WHERE id = ?`, id)
	return err
}

// DeleteAdminSessionsByAdmin 吊销某管理员的全部会话（改密后）。
func (t *Tx) DeleteAdminSessionsByAdmin(ctx context.Context, adminID int64) error {
	_, err := t.q.ExecContext(ctx, `DELETE FROM admin_sessions WHERE admin_id = ?`, adminID)
	return err
}

// DeleteExpiredAdminSessions 清理过期后台会话。
func (t *Tx) DeleteExpiredAdminSessions(ctx context.Context, now int64) (int, error) {
	res, err := t.q.ExecContext(ctx, `DELETE FROM admin_sessions WHERE expires_at <= ?`, now)
	if err != nil {
		return 0, err
	}
	n, _ := res.RowsAffected()
	return int(n), nil
}
