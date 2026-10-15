package store

import (
	"context"
	"database/sql"
	"path/filepath"
	"testing"
	"time"
)

func nullInt(v int64) sql.NullInt64 {
	return sql.NullInt64{Int64: v, Valid: true}
}

func newTestStore(t *testing.T) *Store {
	t.Helper()
	st, err := Open(filepath.Join(t.TempDir(), "store-test.db"))
	if err != nil {
		t.Fatalf("打开测试库失败: %v", err)
	}
	t.Cleanup(func() { _ = st.Close() })
	if err := st.Migrate(context.Background()); err != nil {
		t.Fatalf("迁移失败: %v", err)
	}
	return st
}

func TestMigrateIsIdempotent(t *testing.T) {
	st := newTestStore(t)
	ctx := context.Background()
	if err := st.Migrate(ctx); err != nil {
		t.Fatalf("重复迁移应成功（幂等）: %v", err)
	}
	var n int
	if err := st.Snapshot().q.QueryRowContext(ctx, `SELECT COUNT(*) FROM schema_migrations`).Scan(&n); err != nil {
		t.Fatalf("查询迁移记录失败: %v", err)
	}
	if n != 1 {
		t.Fatalf("迁移记录应为 1 条，实际 %d", n)
	}
}

// 回归：批次号解析曾把 "-0001" 当成 -1，导致新批次号变成 -0000。
func TestNextBatchNo(t *testing.T) {
	st := newTestStore(t)
	ctx := context.Background()

	var first string
	if err := st.Write(ctx, func(tx *Tx) error {
		no, err := tx.NextBatchNo(ctx, "2026-10-12", 0)
		first = no
		return err
	}); err != nil {
		t.Fatalf("生成首批次号失败: %v", err)
	}
	if first != "B20261012-0001" {
		t.Fatalf("首个批次号应为 B20261012-0001，实际 %s", first)
	}

	insert := func(code, batch string) {
		t.Helper()
		if err := st.Write(ctx, func(tx *Tx) error {
			return tx.InsertCard(ctx, &Card{
				Code: code, TypeName: "月卡", DurationMinutes: 43200, State: "unused",
				MaxRebinds: 3, BatchNo: batch, CreatedAt: 1, UpdatedAt: 1,
			})
		}); err != nil {
			t.Fatalf("插入卡密失败: %v", err)
		}
	}
	insert("AAAABBBBCCCC0001", first)
	insert("AAAABBBBCCCC0002", "B20261012-0002")

	var next string
	if err := st.Write(ctx, func(tx *Tx) error {
		no, err := tx.NextBatchNo(ctx, "2026-10-12", 0)
		next = no
		return err
	}); err != nil {
		t.Fatalf("生成下一批次号失败: %v", err)
	}
	if next != "B20261012-0003" {
		t.Fatalf("下一批次号应为 B20261012-0003，实际 %s", next)
	}
}

func TestCardStatsAndTrend(t *testing.T) {
	st := newTestStore(t)
	ctx := context.Background()
	// 趋势统计以"服务器当天"为锚点，这里必须使用真实当前时间。
	now := time.Now().Unix()

	if err := st.Write(ctx, func(tx *Tx) error {
		cards := []*Card{
			{Code: "AAAABBBBCCCC0001", TypeName: "月卡", DurationMinutes: 43200, State: "unused", MaxRebinds: 3, CreatedAt: now, UpdatedAt: now},
			{Code: "AAAABBBBCCCC0002", TypeName: "月卡", DurationMinutes: 43200, State: "active", MaxRebinds: 3, ActivatedAt: now, ExpiresAt: nullInt(now + 3600), CreatedAt: now, UpdatedAt: now},
			{Code: "AAAABBBBCCCC0003", TypeName: "月卡", DurationMinutes: 43200, State: "active", MaxRebinds: 3, ActivatedAt: now, ExpiresAt: nullInt(now - 10), CreatedAt: now, UpdatedAt: now},
			{Code: "AAAABBBBCCCC0004", TypeName: "月卡", DurationMinutes: 43200, State: "disabled", MaxRebinds: 3, CreatedAt: now, UpdatedAt: now},
		}
		for _, c := range cards {
			if err := tx.InsertCard(ctx, c); err != nil {
				return err
			}
		}
		return nil
	}); err != nil {
		t.Fatalf("准备数据失败: %v", err)
	}

	stats, err := st.Snapshot().CardStats(ctx, now)
	if err != nil {
		t.Fatalf("统计失败: %v", err)
	}
	if stats.Total != 4 || stats.Unused != 1 || stats.Active != 1 || stats.Expired != 1 || stats.Disabled != 1 {
		t.Fatalf("统计结果不符：%+v", stats)
	}

	activated, err := st.Snapshot().CountActivatedBetween(ctx, now-60, now+60)
	if err != nil {
		t.Fatalf("激活统计失败: %v", err)
	}
	if activated != 2 {
		t.Fatalf("激活数量应为 2，实际 %d", activated)
	}

	trend, err := st.Snapshot().ActivationTrend(ctx, 3)
	if err != nil {
		t.Fatalf("趋势统计失败: %v", err)
	}
	if len(trend) != 3 {
		t.Fatalf("趋势应返回 3 天，实际 %d", len(trend))
	}
	total := 0
	for _, point := range trend {
		total += point.Activated
	}
	if total != 2 {
		t.Fatalf("趋势合计应为 2，实际 %d（%+v）", total, trend)
	}
}

func TestCloseTimedOutSessionsClearsCardOnline(t *testing.T) {
	st := newTestStore(t)
	ctx := context.Background()
	now := int64(1_800_000_000)
	cardID := int64(0)

	if err := st.Write(ctx, func(tx *Tx) error {
		card := &Card{Code: "AAAABBBBCCCC9999", TypeName: "月卡", DurationMinutes: 43200,
			State: "active", MaxRebinds: 3, CreatedAt: now, UpdatedAt: now}
		if err := tx.InsertCard(ctx, card); err != nil {
			return err
		}
		cardID = card.ID
		session := &Session{TokenHash: "hash-1", CardID: card.ID, MachineCode: "M1",
			CreatedAt: now, LastHeartbeatAt: now, ExpiresAt: now - 1}
		if err := tx.InsertSession(ctx, session); err != nil {
			return err
		}
		return tx.SetCardOnline(ctx, card.ID, session.ID, now, "M1")
	}); err != nil {
		t.Fatalf("准备数据失败: %v", err)
	}

	if err := st.Write(ctx, func(tx *Tx) error {
		n, err := tx.CloseTimedOutSessions(ctx, now)
		if err != nil {
			return err
		}
		if n != 1 {
			t.Fatalf("应关闭 1 个超时会话，实际 %d", n)
		}
		return nil
	}); err != nil {
		t.Fatalf("关闭超时会话失败: %v", err)
	}

	card, err := st.Snapshot().CardByID(ctx, cardID)
	if err != nil {
		t.Fatalf("读取卡密失败: %v", err)
	}
	if card.ActiveOnline() {
		t.Fatal("超时会话关闭后卡密的在线标记必须被清除")
	}
}
