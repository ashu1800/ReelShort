// Package store 封装 SQLite 存储：连接、迁移、事务与全部 SQL。
//
// 设计要点：
//   - 单文件 SQLite + WAL，读走连接池，写统一走 Write()（BEGIN IMMEDIATE + BUSY 退避重试）。
//   - 所有查询方法挂在 Tx 上；Store.Snapshot() 返回自动提交的读句柄，Store.Write() 返回事务句柄。
//   - 时间列一律 unix 秒。
package store

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"io/fs"
	"os"
	"path/filepath"
	"sort"
	"strconv"
	"strings"
	"time"

	_ "modernc.org/sqlite" // 纯 Go 驱动，CGO_ENABLED=0 可交叉编译

	"card.hjj888.cc/cardservice/migrations"
)

// Store 持有数据库连接池。
type Store struct {
	db *sql.DB
}

// Open 打开（必要时创建）SQLite 数据库并验证连通性。
func Open(path string) (*Store, error) {
	if dir := filepath.Dir(path); dir != "" && dir != "." {
		if err := os.MkdirAll(dir, 0o750); err != nil {
			return nil, fmt.Errorf("创建数据库目录失败: %w", err)
		}
	}
	dsn := fmt.Sprintf(
		"file:%s?_pragma=journal_mode(WAL)&_pragma=busy_timeout(5000)&_pragma=foreign_keys(1)&_pragma=synchronous(NORMAL)&_txlock=immediate",
		path,
	)
	db, err := sql.Open("sqlite", dsn)
	if err != nil {
		return nil, fmt.Errorf("打开数据库失败: %w", err)
	}
	db.SetMaxOpenConns(4)
	db.SetMaxIdleConns(4)
	db.SetConnMaxLifetime(0)

	ctx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()
	if err := db.PingContext(ctx); err != nil {
		_ = db.Close()
		return nil, fmt.Errorf("数据库连通性检查失败: %w", err)
	}
	return &Store{db: db}, nil
}

// Close 关闭连接池。
func (s *Store) Close() error { return s.db.Close() }

// Ping 检查数据库可用性（健康检查用）。
func (s *Store) Ping(ctx context.Context) error { return s.db.PingContext(ctx) }

// Backup 用 VACUUM INTO 生成一致性备份文件。
func (s *Store) Backup(ctx context.Context, dest string) error {
	if err := os.MkdirAll(filepath.Dir(dest), 0o750); err != nil {
		return err
	}
	if _, err := os.Stat(dest); err == nil {
		return fmt.Errorf("备份目标已存在: %s", dest)
	}
	_, err := s.db.ExecContext(ctx, `VACUUM INTO ?`, dest)
	return err
}

// Migrate 按文件名顺序应用未执行的迁移。
func (s *Store) Migrate(ctx context.Context) error {
	if _, err := s.db.ExecContext(ctx,
		`CREATE TABLE IF NOT EXISTS schema_migrations (version INTEGER PRIMARY KEY, applied_at INTEGER NOT NULL)`); err != nil {
		return fmt.Errorf("创建 schema_migrations 失败: %w", err)
	}
	applied := map[int]bool{}
	rows, err := s.db.QueryContext(ctx, `SELECT version FROM schema_migrations`)
	if err != nil {
		return err
	}
	for rows.Next() {
		var v int
		if err := rows.Scan(&v); err != nil {
			_ = rows.Close()
			return err
		}
		applied[v] = true
	}
	if err := rows.Err(); err != nil {
		_ = rows.Close()
		return err
	}
	_ = rows.Close()

	entries, err := fs.ReadDir(migrations.FS, ".")
	if err != nil {
		return err
	}
	names := make([]string, 0, len(entries))
	for _, e := range entries {
		if !e.IsDir() && strings.HasSuffix(e.Name(), ".sql") {
			names = append(names, e.Name())
		}
	}
	sort.Strings(names)

	for _, name := range names {
		version, err := migrationVersion(name)
		if err != nil {
			return err
		}
		if applied[version] {
			continue
		}
		body, err := fs.ReadFile(migrations.FS, name)
		if err != nil {
			return err
		}
		err = s.Write(ctx, func(tx *Tx) error {
			if _, err := tx.q.ExecContext(ctx, string(body)); err != nil {
				return fmt.Errorf("执行迁移 %s 失败: %w", name, err)
			}
			_, err = tx.q.ExecContext(ctx, `INSERT INTO schema_migrations (version, applied_at) VALUES (?, ?)`, version, time.Now().Unix())
			return err
		})
		if err != nil {
			return err
		}
	}
	return nil
}

func migrationVersion(name string) (int, error) {
	idx := strings.Index(name, "_")
	if idx <= 0 {
		return 0, fmt.Errorf("迁移文件名不合法: %s", name)
	}
	v, err := strconv.Atoi(name[:idx])
	if err != nil {
		return 0, fmt.Errorf("迁移版本号不合法: %s", name)
	}
	return v, nil
}

// querier 抽象 *sql.DB 与 *sql.Tx，避免读写两套 SQL。
type querier interface {
	ExecContext(ctx context.Context, query string, args ...any) (sql.Result, error)
	QueryContext(ctx context.Context, query string, args ...any) (*sql.Rows, error)
	QueryRowContext(ctx context.Context, query string, args ...any) *sql.Row
}

// Tx 是查询句柄：既可能是自动提交的连接，也可能在事务中。
type Tx struct {
	q querier
}

// Snapshot 返回只读句柄（自动提交）。
func (s *Store) Snapshot() *Tx { return &Tx{q: s.db} }

// Write 在写事务中执行 fn；遇到 SQLITE_BUSY 自动退避重试。
func (s *Store) Write(ctx context.Context, fn func(tx *Tx) error) error {
	backoff := []time.Duration{0, 50 * time.Millisecond, 150 * time.Millisecond, 400 * time.Millisecond, time.Second}
	var lastErr error
	for attempt := range backoff {
		if attempt > 0 {
			select {
			case <-ctx.Done():
				return ctx.Err()
			case <-time.After(backoff[attempt]):
			}
		}
		lastErr = s.attemptWrite(ctx, fn)
		if lastErr == nil {
			return nil
		}
		if !isBusyError(lastErr) {
			return lastErr
		}
	}
	return lastErr
}

func (s *Store) attemptWrite(ctx context.Context, fn func(tx *Tx) error) error {
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	if err := fn(&Tx{q: tx}); err != nil {
		_ = tx.Rollback()
		return err
	}
	if err := tx.Commit(); err != nil {
		_ = tx.Rollback()
		return err
	}
	return nil
}

func isBusyError(err error) bool {
	if err == nil {
		return false
	}
	msg := strings.ToLower(err.Error())
	return strings.Contains(msg, "sqlite_busy") ||
		strings.Contains(msg, "database is locked") ||
		strings.Contains(msg, "database table is locked")
}

// ErrNotFound 表示记录不存在。
var ErrNotFound = errors.New("record not found")

// rowScanner 兼容 *sql.Row 与 *sql.Rows。
type rowScanner interface {
	Scan(dest ...any) error
}
