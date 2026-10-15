package store

import "context"

// AllSettings 读取全部设置项。
func (t *Tx) AllSettings(ctx context.Context) (map[string]string, error) {
	rows, err := t.q.QueryContext(ctx, `SELECT key, value FROM settings`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	out := map[string]string{}
	for rows.Next() {
		var k, v string
		if err := rows.Scan(&k, &v); err != nil {
			return nil, err
		}
		out[k] = v
	}
	return out, rows.Err()
}

// UpsertSettings 写入设置项（存在则覆盖）。
func (t *Tx) UpsertSettings(ctx context.Context, values map[string]string, now int64) error {
	for k, v := range values {
		if _, err := t.q.ExecContext(ctx,
			`INSERT INTO settings (key, value, updated_at) VALUES (?,?,?)
			 ON CONFLICT(key) DO UPDATE SET value = excluded.value, updated_at = excluded.updated_at`,
			k, v, now); err != nil {
			return err
		}
	}
	return nil
}

// EnsureSettings 补齐缺失的默认设置项（不覆盖已有值）。
func (t *Tx) EnsureSettings(ctx context.Context, defaults map[string]string, now int64) error {
	for k, v := range defaults {
		if _, err := t.q.ExecContext(ctx,
			`INSERT OR IGNORE INTO settings (key, value, updated_at) VALUES (?,?,?)`, k, v, now); err != nil {
			return err
		}
	}
	return nil
}
