package service

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"strconv"
	"strings"

	"card.hjj888.cc/cardservice/internal/jstime"
	"card.hjj888.cc/cardservice/internal/security"
	"card.hjj888.cc/cardservice/internal/store"
)

// CardTypeInput 是卡密类型新增/修改入参。
type CardTypeInput struct {
	Name            string
	DurationMinutes int64
	MaxRebinds      int
	Enabled         bool
	SortOrder       int
}

// SettingsInput 是设置修改入参（nil 表示不改）。
type SettingsInput struct {
	HeartbeatInterval   *int
	SessionTimeout      *int
	AllowTakeover       *bool
	DefaultMaxRebinds   *int
	ActivateOn          *string
	LogHeartbeatSuccess *bool
	RateLimitIP         *int
	RateLimitCard       *int
	RateLimitSession    *int
	AdminSessionHours   *int
}

// StatsView 是概览统计。
type StatsView struct {
	Cards          store.Stats
	TodayActivated int
	TodayVerified  int
	TodayLoggedIn  int
	TodayRebinds   int
	Trend          []store.TrendPoint
	ServerTime     int64
}

// AdminCardTypes 列出卡密类型。
func (s *Service) AdminCardTypes(ctx context.Context) ([]*store.CardType, error) {
	return s.store.Snapshot().ListCardTypes(ctx)
}

// AdminCreateCardType 新增卡密类型。
func (s *Service) AdminCreateCardType(ctx context.Context, in CardTypeInput, admin *AdminIdentity, ip string) (*store.CardType, error) {
	if admin == nil {
		return nil, NewError(ErrCodeUnauthorized, "未登录或登录已过期")
	}
	if err := validateCardType(in); err != nil {
		return nil, err
	}
	now := s.now().Unix()
	ct := &store.CardType{
		Name:            strings.TrimSpace(in.Name),
		DurationMinutes: in.DurationMinutes,
		MaxRebinds:      in.MaxRebinds,
		Enabled:         in.Enabled,
		SortOrder:       in.SortOrder,
		CreatedAt:       now,
		UpdatedAt:       now,
	}
	err := s.store.Write(ctx, func(tx *store.Tx) error {
		if err := tx.InsertCardType(ctx, ct); err != nil {
			if isUniqueViolation(err) {
				return ConflictError("同名卡密类型已存在")
			}
			return err
		}
		return tx.InsertAuditLog(ctx, &store.AuditLog{
			AdminID: admin.ID, Username: admin.Username, Action: "card_type_create",
			TargetType: "card_type", TargetID: fmt.Sprintf("%d", ct.ID),
			Detail: fmt.Sprintf(`{"name":%q,"duration_minutes":%d,"max_rebinds":%d}`, ct.Name, ct.DurationMinutes, ct.MaxRebinds),
			IP:     ip, CreatedAt: now,
		})
	})
	if err != nil {
		return nil, err
	}
	return ct, nil
}

// AdminUpdateCardType 修改卡密类型。
func (s *Service) AdminUpdateCardType(ctx context.Context, id int64, in CardTypeInput, admin *AdminIdentity, ip string) (*store.CardType, error) {
	if admin == nil {
		return nil, NewError(ErrCodeUnauthorized, "未登录或登录已过期")
	}
	if err := validateCardType(in); err != nil {
		return nil, err
	}
	now := s.now().Unix()
	var out *store.CardType
	err := s.store.Write(ctx, func(tx *store.Tx) error {
		ct, err := tx.CardTypeByID(ctx, id)
		if errors.Is(err, store.ErrNotFound) {
			return NotFoundError("卡密类型不存在")
		}
		if err != nil {
			return err
		}
		detail := fmt.Sprintf(`{"name":%q->%q,"duration_minutes":%d->%d,"max_rebinds":%d->%d}`,
			ct.Name, strings.TrimSpace(in.Name), ct.DurationMinutes, in.DurationMinutes, ct.MaxRebinds, in.MaxRebinds)
		ct.Name = strings.TrimSpace(in.Name)
		ct.DurationMinutes = in.DurationMinutes
		ct.MaxRebinds = in.MaxRebinds
		ct.Enabled = in.Enabled
		ct.SortOrder = in.SortOrder
		ct.UpdatedAt = now
		if err := tx.UpdateCardType(ctx, ct); err != nil {
			if isUniqueViolation(err) {
				return ConflictError("同名卡密类型已存在")
			}
			return err
		}
		if err := tx.InsertAuditLog(ctx, &store.AuditLog{
			AdminID: admin.ID, Username: admin.Username, Action: "card_type_update",
			TargetType: "card_type", TargetID: fmt.Sprintf("%d", ct.ID), Detail: detail, IP: ip, CreatedAt: now,
		}); err != nil {
			return err
		}
		out = ct
		return nil
	})
	if err != nil {
		return nil, err
	}
	return out, nil
}

// AdminDeleteCardType 删除卡密类型（仍被引用时拒绝）。
func (s *Service) AdminDeleteCardType(ctx context.Context, id int64, admin *AdminIdentity, ip string) error {
	if admin == nil {
		return NewError(ErrCodeUnauthorized, "未登录或登录已过期")
	}
	now := s.now().Unix()
	return s.store.Write(ctx, func(tx *store.Tx) error {
		ct, err := tx.CardTypeByID(ctx, id)
		if errors.Is(err, store.ErrNotFound) {
			return NotFoundError("卡密类型不存在")
		}
		if err != nil {
			return err
		}
		n, err := tx.CountCardsByType(ctx, id)
		if err != nil {
			return err
		}
		if n > 0 {
			return ConflictError(fmt.Sprintf("该类型下仍有 %d 张卡密，不能删除", n))
		}
		if err := tx.DeleteCardType(ctx, id); err != nil {
			return err
		}
		return tx.InsertAuditLog(ctx, &store.AuditLog{
			AdminID: admin.ID, Username: admin.Username, Action: "card_type_delete",
			TargetType: "card_type", TargetID: fmt.Sprintf("%d", id), Detail: ct.Name, IP: ip, CreatedAt: now,
		})
	})
}

func validateCardType(in CardTypeInput) error {
	name := strings.TrimSpace(in.Name)
	if name == "" || len([]rune(name)) > 32 {
		return ValidationError("类型名称必填且不超过 32 字")
	}
	if in.DurationMinutes < 0 || in.DurationMinutes > 5256000 {
		return ValidationError("时长（分钟）需在 0-5256000 之间，0 表示永久")
	}
	if in.MaxRebinds < 0 || in.MaxRebinds > 100 {
		return ValidationError("默认换绑上限需在 0-100 之间")
	}
	if in.SortOrder < 0 || in.SortOrder > 100000 {
		return ValidationError("排序值需在 0-100000 之间")
	}
	return nil
}

// AdminListSessions 分页查询会话。
func (s *Service) AdminListSessions(ctx context.Context, f store.SessionFilter) ([]store.SessionView, int, error) {
	if code := strings.TrimSpace(f.CardCode); code != "" {
		f.CardCode = security.NormalizeCardCode(code)
	}
	return s.store.Snapshot().ListSessions(ctx, f, s.now().Unix())
}

// AdminKickSession 强制下线指定会话。
func (s *Service) AdminKickSession(ctx context.Context, id int64, admin *AdminIdentity, ip string) error {
	if admin == nil {
		return NewError(ErrCodeUnauthorized, "未登录或登录已过期")
	}
	now := s.now().Unix()
	return s.store.Write(ctx, func(tx *store.Tx) error {
		sess, err := tx.SessionByID(ctx, id)
		if errors.Is(err, store.ErrNotFound) {
			return NotFoundError("会话不存在")
		}
		if err != nil {
			return err
		}
		if err := tx.CloseSession(ctx, sess.ID, now, "admin_kick"); err != nil {
			return err
		}
		if err := tx.ClearCardOnline(ctx, sess.CardID, now); err != nil {
			return err
		}
		if err := tx.InsertCardLog(ctx, &store.CardLog{
			CardID: sql.NullInt64{Int64: sess.CardID, Valid: true},
			Action: "admin_kick", Result: "ok", Message: "管理员强制下线",
			MachineCode: security.MaskMachine(sess.MachineCode), IP: ip, CreatedAt: now,
		}); err != nil {
			return err
		}
		return tx.InsertAuditLog(ctx, &store.AuditLog{
			AdminID: admin.ID, Username: admin.Username, Action: "session_kick",
			TargetType: "session", TargetID: fmt.Sprintf("%d", sess.ID),
			Detail: fmt.Sprintf(`{"card_id":%d}`, sess.CardID), IP: ip, CreatedAt: now,
		})
	})
}

// AdminListLogs 分页查询卡密操作日志。
func (s *Service) AdminListLogs(ctx context.Context, f store.LogFilter) ([]*store.CardLog, int, error) {
	if code := strings.TrimSpace(f.CardCode); code != "" {
		f.CardCode = security.NormalizeCardCode(code)
	}
	return s.store.Snapshot().ListCardLogs(ctx, f)
}

// AdminListAuditLogs 分页查询审计日志。
func (s *Service) AdminListAuditLogs(ctx context.Context, f store.AuditFilter) ([]*store.AuditLog, int, error) {
	return s.store.Snapshot().ListAuditLogs(ctx, f)
}

// AdminStats 概览统计。
func (s *Service) AdminStats(ctx context.Context) (*StatsView, error) {
	st := s.store.Snapshot()
	now := s.now().Unix()
	dayStart, dayEnd := jstime.DayRange(now)
	cards, err := st.CardStats(ctx, now)
	if err != nil {
		return nil, err
	}
	activated, err := st.CountActivatedBetween(ctx, dayStart, dayEnd)
	if err != nil {
		return nil, err
	}
	verified, err := st.CountLogs(ctx, "verify", "ok", dayStart, dayEnd)
	if err != nil {
		return nil, err
	}
	loggedIn, err := st.CountLogs(ctx, "login", "ok", dayStart, dayEnd)
	if err != nil {
		return nil, err
	}
	rebinds, err := st.CountLogs(ctx, "rebind", "ok", dayStart, dayEnd)
	if err != nil {
		return nil, err
	}
	trend, err := st.ActivationTrend(ctx, 7)
	if err != nil {
		return nil, err
	}
	return &StatsView{
		Cards:          *cards,
		TodayActivated: activated,
		TodayVerified:  verified,
		TodayLoggedIn:  loggedIn,
		TodayRebinds:   rebinds,
		Trend:          trend,
		ServerTime:     now,
	}, nil
}

// AdminSettings 返回全部设置项（字符串形式，前端按契约转换）。
func (s *Service) AdminSettings(ctx context.Context) (map[string]string, error) {
	raw, err := s.store.Snapshot().AllSettings(ctx)
	if err != nil {
		return nil, err
	}
	merged := DefaultSettings()
	for k, v := range raw {
		merged[k] = v
	}
	out := map[string]string{}
	for _, k := range SettingsKeys {
		out[k] = merged[k]
	}
	return out, nil
}

// AdminUpdateSettings 校验并保存设置项。
func (s *Service) AdminUpdateSettings(ctx context.Context, in SettingsInput, admin *AdminIdentity, ip string) (map[string]string, error) {
	if admin == nil {
		return nil, NewError(ErrCodeUnauthorized, "未登录或登录已过期")
	}
	current, err := s.AdminSettings(ctx)
	if err != nil {
		return nil, err
	}
	next := map[string]string{}
	for k, v := range current {
		next[k] = v
	}

	heartbeat := atoiOr(next["heartbeat_interval_seconds"], 30)
	timeout := atoiOr(next["session_timeout_seconds"], 90)

	if in.HeartbeatInterval != nil {
		if *in.HeartbeatInterval < 10 || *in.HeartbeatInterval > 300 {
			return nil, ValidationError("心跳间隔需在 10-300 秒之间")
		}
		heartbeat = *in.HeartbeatInterval
		next["heartbeat_interval_seconds"] = strconv.Itoa(heartbeat)
	}
	if in.SessionTimeout != nil {
		if *in.SessionTimeout > 3600 {
			return nil, ValidationError("会话超时不能超过 3600 秒")
		}
		timeout = *in.SessionTimeout
	}
	if timeout < 2*heartbeat {
		return nil, ValidationError("会话超时至少为心跳间隔的 2 倍（当前心跳 %d 秒）", heartbeat)
	}
	next["session_timeout_seconds"] = strconv.Itoa(timeout)

	if in.AllowTakeover != nil {
		next["allow_takeover"] = strconv.FormatBool(*in.AllowTakeover)
	}
	if in.LogHeartbeatSuccess != nil {
		next["log_heartbeat_success"] = strconv.FormatBool(*in.LogHeartbeatSuccess)
	}
	if in.DefaultMaxRebinds != nil {
		if *in.DefaultMaxRebinds < 0 || *in.DefaultMaxRebinds > 100 {
			return nil, ValidationError("默认换绑次数需在 0-100 之间")
		}
		next["default_max_rebinds"] = strconv.Itoa(*in.DefaultMaxRebinds)
	}
	if in.ActivateOn != nil {
		v := strings.TrimSpace(*in.ActivateOn)
		if v != "verify" && v != "login" {
			return nil, ValidationError("激活时机只能是 verify 或 login")
		}
		next["activate_on"] = v
	}
	rateChecks := []struct {
		value *int
		key   string
		min   int
		max   int
		label string
	}{
		{in.RateLimitIP, "rate_limit_ip_per_minute", 10, 100000, "单 IP 每分钟限流"},
		{in.RateLimitCard, "rate_limit_card_per_minute", 5, 100000, "单卡密每分钟限流"},
		{in.RateLimitSession, "rate_limit_session_per_minute", 5, 100000, "单会话每分钟限流"},
		{in.AdminSessionHours, "admin_session_hours", 1, 168, "后台会话时长（小时）"},
	}
	for _, rc := range rateChecks {
		if rc.value == nil {
			continue
		}
		if *rc.value < rc.min || *rc.value > rc.max {
			return nil, ValidationError("%s 需在 %d-%d 之间", rc.label, rc.min, rc.max)
		}
		next[rc.key] = strconv.Itoa(*rc.value)
	}

	changes := []string{}
	for _, k := range SettingsKeys {
		if next[k] != current[k] {
			changes = append(changes, fmt.Sprintf("%s:%s->%s", k, current[k], next[k]))
		}
	}
	if len(changes) == 0 {
		return current, nil
	}

	now := s.now().Unix()
	err = s.store.Write(ctx, func(tx *store.Tx) error {
		if err := tx.UpsertSettings(ctx, next, now); err != nil {
			return err
		}
		return tx.InsertAuditLog(ctx, &store.AuditLog{
			AdminID: admin.ID, Username: admin.Username, Action: "settings_update",
			TargetType: "settings", TargetID: "runtime",
			Detail: truncate(strings.Join(changes, "; "), 2000), IP: ip, CreatedAt: now,
		})
	})
	if err != nil {
		return nil, err
	}
	s.InvalidateSettings()
	return next, nil
}

func atoiOr(s string, def int) int {
	n, err := strconv.Atoi(strings.TrimSpace(s))
	if err != nil {
		return def
	}
	return n
}
