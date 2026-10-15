// Package service 实现卡密领域逻辑：校验、绑定、激活、换绑、上下线与后台运营操作。
package service

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"os"
	"path/filepath"
	"sort"
	"strconv"
	"strings"
	"sync"
	"time"

	"golang.org/x/crypto/bcrypt"

	"card.hjj888.cc/cardservice/internal/config"
	"card.hjj888.cc/cardservice/internal/security"
	"card.hjj888.cc/cardservice/internal/store"
)

// BcryptCost 管理员口令哈希强度。
const BcryptCost = 12

// Settings 是运行期可调参数（来自 settings 表，10 秒缓存）。
type Settings struct {
	HeartbeatInterval   int
	SessionTimeout      int
	AllowTakeover       bool
	DefaultMaxRebinds   int
	ActivateOn          string
	LogHeartbeatSuccess bool
	RateLimitIP         int
	RateLimitCard       int
	RateLimitSession    int
	AdminSessionHours   int
}

// DefaultSettings 是首次启动写入数据库的默认值。
func DefaultSettings() map[string]string {
	return map[string]string{
		"heartbeat_interval_seconds":    "30",
		"session_timeout_seconds":       "180",
		"allow_takeover":                "true",
		"default_max_rebinds":           "3",
		"activate_on":                   "verify",
		"log_heartbeat_success":         "false",
		"rate_limit_ip_per_minute":      "300",
		"rate_limit_card_per_minute":    "60",
		"rate_limit_session_per_minute": "120",
		"admin_session_hours":           "12",
	}
}

// SettingsKeys 是允许后台读写的设置键（顺序即前端展示顺序）。
var SettingsKeys = []string{
	"heartbeat_interval_seconds",
	"session_timeout_seconds",
	"allow_takeover",
	"default_max_rebinds",
	"activate_on",
	"log_heartbeat_success",
	"rate_limit_ip_per_minute",
	"rate_limit_card_per_minute",
	"rate_limit_session_per_minute",
	"admin_session_hours",
}

// Service 是业务入口。
type Service struct {
	store   *store.Store
	limiter *security.Limiter
	guard   *security.FailureGuard
	logger  *slog.Logger
	now     func() time.Time

	settingsMu   sync.RWMutex
	settingsVal  Settings
	settingsLoad time.Time
}

// New 创建服务。
func New(st *store.Store, logger *slog.Logger) *Service {
	if logger == nil {
		logger = slog.Default()
	}
	return &Service{
		store:   st,
		limiter: security.NewLimiter(time.Minute),
		guard:   security.NewFailureGuard(5, 15*time.Minute),
		logger:  logger,
		now:     time.Now,
	}
}

// Store 暴露底层存储（健康检查、备份等基础设施使用）。
func (s *Service) Store() *store.Store { return s.store }

// Now 返回服务当前时间。
func (s *Service) Now() time.Time { return s.now() }

// Bootstrap 补齐默认设置、预置卡密类型，并按需创建初始管理员。
func (s *Service) Bootstrap(ctx context.Context, cfg *config.Config) error {
	now := s.now().Unix()
	return s.store.Write(ctx, func(tx *store.Tx) error {
		if err := tx.EnsureSettings(ctx, DefaultSettings(), now); err != nil {
			return err
		}
		if err := tx.EnsureCardTypes(ctx, defaultCardTypes(now)); err != nil {
			return err
		}
		n, err := tx.CountAdmins(ctx)
		if err != nil {
			return err
		}
		if n > 0 {
			return nil
		}
		if cfg.BootstrapAdminUsername == "" || cfg.BootstrapAdminPassword == "" {
			return errors.New("数据库中没有管理员，且未配置 CARDSERVICE_BOOTSTRAP_ADMIN_USERNAME / CARDSERVICE_BOOTSTRAP_ADMIN_PASSWORD")
		}
		hash, err := bcrypt.GenerateFromPassword([]byte(cfg.BootstrapAdminPassword), BcryptCost)
		if err != nil {
			return err
		}
		admin := &store.Admin{
			Username:           cfg.BootstrapAdminUsername,
			PasswordHash:       string(hash),
			DisplayName:        cfg.BootstrapAdminDisplay,
			MustChangePassword: true,
			CreatedAt:          now,
			UpdatedAt:          now,
		}
		if err := tx.InsertAdmin(ctx, admin); err != nil {
			// 并发启动时另一个进程已创建，忽略唯一键冲突。
			if strings.Contains(strings.ToLower(err.Error()), "unique") {
				return nil
			}
			return err
		}
		s.logger.Info("已创建初始管理员", "username", admin.Username, "must_change_password", true)
		return nil
	})
}

func defaultCardTypes(now int64) []*store.CardType {
	defs := []struct {
		name    string
		minutes int64
		rebinds int
		sortOrd int
	}{
		{"天卡", 1440, 3, 10},
		{"周卡", 10080, 3, 20},
		{"月卡", 43200, 3, 30},
		{"季卡", 129600, 3, 40},
		{"年卡", 525600, 3, 50},
		{"永久", 0, 3, 60},
	}
	out := make([]*store.CardType, 0, len(defs))
	for _, d := range defs {
		out = append(out, &store.CardType{
			Name:            d.name,
			DurationMinutes: d.minutes,
			MaxRebinds:      d.rebinds,
			Enabled:         true,
			SortOrder:       d.sortOrd,
			CreatedAt:       now,
			UpdatedAt:       now,
		})
	}
	return out
}

// Settings 读取运行参数（10 秒缓存，写入后立即失效）。
func (s *Service) Settings(ctx context.Context) (Settings, error) {
	s.settingsMu.RLock()
	if time.Since(s.settingsLoad) < 10*time.Second {
		v := s.settingsVal
		s.settingsMu.RUnlock()
		return v, nil
	}
	s.settingsMu.RUnlock()

	raw, err := s.store.Snapshot().AllSettings(ctx)
	if err != nil {
		return Settings{}, err
	}
	merged := DefaultSettings()
	for k, v := range raw {
		merged[k] = v
	}
	val := parseSettings(merged)

	s.settingsMu.Lock()
	s.settingsVal = val
	s.settingsLoad = time.Now()
	s.settingsMu.Unlock()
	return val, nil
}

// InvalidateSettings 让设置缓存立即失效。
func (s *Service) InvalidateSettings() {
	s.settingsMu.Lock()
	s.settingsLoad = time.Time{}
	s.settingsMu.Unlock()
}

func parseSettings(raw map[string]string) Settings {
	atoi := func(key string, def int) int {
		if v, ok := raw[key]; ok {
			if n, err := strconv.Atoi(strings.TrimSpace(v)); err == nil {
				return n
			}
		}
		return def
	}
	atob := func(key string, def bool) bool {
		if v, ok := raw[key]; ok {
			switch strings.ToLower(strings.TrimSpace(v)) {
			case "true", "1", "yes", "on":
				return true
			case "false", "0", "no", "off":
				return false
			}
		}
		return def
	}
	st := Settings{
		HeartbeatInterval:   atoi("heartbeat_interval_seconds", 30),
		SessionTimeout:      atoi("session_timeout_seconds", 180),
		AllowTakeover:       atob("allow_takeover", true),
		DefaultMaxRebinds:   atoi("default_max_rebinds", 3),
		ActivateOn:          strings.TrimSpace(raw["activate_on"]),
		LogHeartbeatSuccess: atob("log_heartbeat_success", false),
		RateLimitIP:         atoi("rate_limit_ip_per_minute", 300),
		RateLimitCard:       atoi("rate_limit_card_per_minute", 60),
		RateLimitSession:    atoi("rate_limit_session_per_minute", 120),
		AdminSessionHours:   atoi("admin_session_hours", 12),
	}
	if st.ActivateOn != "login" {
		st.ActivateOn = "verify"
	}
	if st.HeartbeatInterval < 10 {
		st.HeartbeatInterval = 10
	}
	if st.HeartbeatInterval > 300 {
		st.HeartbeatInterval = 300
	}
	if st.SessionTimeout < 2*st.HeartbeatInterval {
		st.SessionTimeout = 2 * st.HeartbeatInterval
	}
	if st.SessionTimeout > 3600 {
		st.SessionTimeout = 3600
	}
	if st.AdminSessionHours < 1 || st.AdminSessionHours > 168 {
		st.AdminSessionHours = 12
	}
	return st
}

// StartJanitor 启动后台维护协程：关闭超时会话、清理限流窗口与过期后台会话。
func (s *Service) StartJanitor(ctx context.Context) {
	go func() {
		ticker := time.NewTicker(15 * time.Second)
		defer ticker.Stop()
		for {
			select {
			case <-ctx.Done():
				return
			case <-ticker.C:
				s.janitorTick(ctx)
			}
		}
	}()
}

func (s *Service) janitorTick(ctx context.Context) {
	now := s.now().Unix()
	err := s.store.Write(ctx, func(tx *store.Tx) error {
		if _, err := tx.CloseTimedOutSessions(ctx, now); err != nil {
			return err
		}
		_, err := tx.DeleteExpiredAdminSessions(ctx, now)
		return err
	})
	if err != nil {
		s.logger.Warn("后台维护任务失败", "error", err)
	}
	s.limiter.GC()
}

// RunBackup 执行一致性备份，并清理超出保留份数的旧备份。
func (s *Service) RunBackup(ctx context.Context, dir string, keep int) (string, error) {
	name := fmt.Sprintf("cardservice-%s.db", s.now().Format("20060102-150405"))
	dest := strings.TrimRight(dir, "/\\") + string(os.PathSeparator) + name
	if err := s.store.Backup(ctx, dest); err != nil {
		return "", err
	}
	if keep > 0 {
		s.pruneBackups(dir, keep)
	}
	return dest, nil
}

func (s *Service) pruneBackups(dir string, keep int) {
	entries, err := os.ReadDir(dir)
	if err != nil {
		return
	}
	names := make([]string, 0, len(entries))
	for _, e := range entries {
		if !e.IsDir() && strings.HasPrefix(e.Name(), "cardservice-") && strings.HasSuffix(e.Name(), ".db") {
			names = append(names, e.Name())
		}
	}
	if len(names) <= keep {
		return
	}
	sort.Strings(names)
	for _, name := range names[:len(names)-keep] {
		if err := os.Remove(filepath.Join(dir, name)); err != nil {
			s.logger.Warn("清理旧备份失败", "file", name, "error", err)
		}
	}
}

func truncate(s string, max int) string {
	if len(s) <= max {
		return s
	}
	return s[:max]
}
