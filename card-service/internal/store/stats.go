package store

import (
	"context"
	"time"

	"card.hjj888.cc/cardservice/internal/jstime"
)

// CardStats 统计卡密各状态数量。
func (t *Tx) CardStats(ctx context.Context, now int64) (*Stats, error) {
	var s Stats
	err := t.q.QueryRowContext(ctx, `SELECT
		COUNT(*),
		COALESCE(SUM(CASE WHEN state = 'unused' THEN 1 ELSE 0 END), 0),
		COALESCE(SUM(CASE WHEN state = 'active' AND (expires_at IS NULL OR expires_at > ?) THEN 1 ELSE 0 END), 0),
		COALESCE(SUM(CASE WHEN state <> 'disabled' AND expires_at IS NOT NULL AND expires_at <= ? THEN 1 ELSE 0 END), 0),
		COALESCE(SUM(CASE WHEN state = 'disabled' THEN 1 ELSE 0 END), 0),
		COALESCE(SUM(CASE WHEN online_session_id IS NOT NULL THEN 1 ELSE 0 END), 0)
		FROM cards WHERE deleted_at IS NULL`, now, now).
		Scan(&s.Total, &s.Unused, &s.Active, &s.Expired, &s.Disabled, &s.Online)
	return &s, err
}

// CountActivatedBetween 统计时间窗内激活的卡密数量。
func (t *Tx) CountActivatedBetween(ctx context.Context, from, to int64) (int, error) {
	var n int
	err := t.q.QueryRowContext(ctx,
		`SELECT COUNT(*) FROM cards WHERE deleted_at IS NULL AND activated_at >= ? AND activated_at < ?`,
		from, to).Scan(&n)
	return n, err
}

// ActivationTrend 返回最近 days 天（含今天）的每日激活数，缺失日期补 0。
func (t *Tx) ActivationTrend(ctx context.Context, days int) ([]TrendPoint, error) {
	if days <= 0 {
		days = 7
	}
	today := time.Now().In(jstime.Location())
	startDay := time.Date(today.Year(), today.Month(), today.Day(), 0, 0, 0, 0, jstime.Location()).AddDate(0, 0, -(days - 1))
	counts := map[string]int{}
	// SQLite 里 unix 秒 + 28800 秒即为东八区墙上时间，strftime 再按 UTC 格式化即得北京日期。
	rows, err := t.q.QueryContext(ctx, `SELECT strftime('%Y-%m-%d', activated_at + 28800, 'unixepoch') AS day, COUNT(*)
		FROM cards WHERE deleted_at IS NULL AND activated_at >= ? GROUP BY day`, startDay.Unix())
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	for rows.Next() {
		var day string
		var n int
		if err := rows.Scan(&day, &n); err != nil {
			return nil, err
		}
		counts[day] = n
	}
	if err := rows.Err(); err != nil {
		return nil, err
	}
	out := make([]TrendPoint, 0, days)
	for i := 0; i < days; i++ {
		d := startDay.AddDate(0, 0, i)
		key := d.Format(jstime.DateLayout)
		out = append(out, TrendPoint{Date: key, Activated: counts[key]})
	}
	return out, nil
}

// ExpiringSoon 统计 N 天内到期的卡密数量（运维用，可选展示）。
func (t *Tx) ExpiringSoon(ctx context.Context, now, until int64) (int, error) {
	var n int
	err := t.q.QueryRowContext(ctx,
		`SELECT COUNT(*) FROM cards WHERE deleted_at IS NULL AND state = 'active' AND expires_at IS NOT NULL AND expires_at > ? AND expires_at <= ?`,
		now, until).Scan(&n)
	return n, err
}
