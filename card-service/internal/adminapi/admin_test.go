package adminapi

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"net/url"
	"path/filepath"
	"strings"
	"testing"

	"card.hjj888.cc/cardservice/internal/config"
	"card.hjj888.cc/cardservice/internal/service"
	"card.hjj888.cc/cardservice/internal/store"
)

type adminEnv struct {
	svc      *service.Service
	st       *store.Store
	srv      *httptest.Server
	auth     *http.Client
	password string
}

func newAdminEnv(t *testing.T) *adminEnv {
	t.Helper()
	dir := t.TempDir()
	st, err := store.Open(filepath.Join(dir, "admin-test.db"))
	if err != nil {
		t.Fatalf("打开测试库失败: %v", err)
	}
	t.Cleanup(func() { _ = st.Close() })
	if err := st.Migrate(context.Background()); err != nil {
		t.Fatalf("迁移失败: %v", err)
	}
	logger := slog.New(slog.NewTextHandler(io.Discard, &slog.HandlerOptions{Level: slog.LevelError}))
	svc := service.New(st, logger)
	cfg := &config.Config{
		Addr:                   "127.0.0.1:0",
		CookieSecure:           false,
		BootstrapAdminUsername: "admin",
		BootstrapAdminPassword: "admin-password-123",
	}
	if err := svc.Bootstrap(context.Background(), cfg); err != nil {
		t.Fatalf("初始化失败: %v", err)
	}
	// 直接挂载后台路由（与生产一致）。
	mux := http.NewServeMux()
	NewServer(svc, cfg, logger).Register(mux)
	srv := httptest.NewServer(mux)
	t.Cleanup(srv.Close)

	jar := newSimpleJar()
	client := &http.Client{Jar: jar}
	return &adminEnv{svc: svc, st: st, srv: srv, auth: client, password: "admin-password-123"}
}

// simpleJar 是一个最小的 Cookie 容器，避免引入外部依赖。
type simpleJar struct{ cookies map[string]*http.Cookie }

func newSimpleJar() *simpleJar { return &simpleJar{cookies: map[string]*http.Cookie{}} }

func (j *simpleJar) SetCookies(_ *url.URL, cs []*http.Cookie) {
	for _, c := range cs {
		if c.MaxAge < 0 || c.Value == "" {
			delete(j.cookies, c.Name)
			continue
		}
		j.cookies[c.Name] = c
	}
}

func (j *simpleJar) Cookies(_ *url.URL) []*http.Cookie {
	out := make([]*http.Cookie, 0, len(j.cookies))
	for _, c := range j.cookies {
		out = append(out, c)
	}
	return out
}

func (e *adminEnv) do(t *testing.T, method, path string, body any, withCSRF bool) (int, map[string]any, http.Header) {
	t.Helper()
	var reader io.Reader
	if body != nil {
		raw, err := json.Marshal(body)
		if err != nil {
			t.Fatalf("序列化失败: %v", err)
		}
		reader = bytes.NewReader(raw)
	}
	req, err := http.NewRequest(method, e.srv.URL+path, reader)
	if err != nil {
		t.Fatalf("构造请求失败: %v", err)
	}
	if body != nil {
		req.Header.Set("Content-Type", "application/json")
	}
	if withCSRF {
		req.Header.Set(CSRFHeader, CSRFValue)
	}
	resp, err := e.auth.Do(req)
	if err != nil {
		t.Fatalf("请求 %s %s 失败: %v", method, path, err)
	}
	defer resp.Body.Close()
	data, _ := io.ReadAll(resp.Body)
	out := map[string]any{}
	if len(bytes.TrimSpace(data)) > 0 && strings.Contains(resp.Header.Get("Content-Type"), "json") {
		_ = json.Unmarshal(data, &out)
	}
	return resp.StatusCode, out, resp.Header
}

func (e *adminEnv) login(t *testing.T) {
	t.Helper()
	status, body, _ := e.do(t, http.MethodPost, "/api/admin/auth/login",
		map[string]any{"username": "admin", "password": e.password}, true)
	if status != http.StatusOK {
		t.Fatalf("登录失败: %d %v", status, body)
	}
}

func (e *adminEnv) changePassword(t *testing.T, oldPwd, newPwd string) {
	t.Helper()
	status, body, _ := e.do(t, http.MethodPut, "/api/admin/auth/password",
		map[string]any{"old_password": oldPwd, "new_password": newPwd}, true)
	if status != http.StatusOK {
		t.Fatalf("改密失败: %d %v", status, body)
	}
	e.password = newPwd
}

// setupReady 登录并完成强制改密，返回可用的会话。
func (e *adminEnv) setupReady(t *testing.T) {
	t.Helper()
	e.login(t)
	e.changePassword(t, e.password, "new-admin-password-456")
	e.login(t)
}

// 首次登录必须改密：其他接口应被拦截。
func TestMustChangePasswordBlocksOtherEndpoints(t *testing.T) {
	env := newAdminEnv(t)
	env.login(t)

	status, body, _ := env.do(t, http.MethodGet, "/api/admin/cards", nil, false)
	if status != http.StatusForbidden {
		t.Fatalf("初始管理员未改密时应返回 403，实际 %d %v", status, body)
	}
	if errObj, ok := body["error"].(map[string]any); !ok || errObj["code"] != service.ErrCodePasswordChange {
		t.Fatalf("错误码应为 password_change_required，实际 %v", body)
	}

	env.changePassword(t, "admin-password-123", "new-admin-password-456")
	// 改密吊销全部会话。
	status, _, _ = env.do(t, http.MethodGet, "/api/admin/cards", nil, false)
	if status != http.StatusUnauthorized {
		t.Fatalf("改密后旧会话应失效，实际 %d", status)
	}
	env.login(t)
	status, body, _ = env.do(t, http.MethodGet, "/api/admin/cards", nil, false)
	if status != http.StatusOK {
		t.Fatalf("改密后重新登录应可访问，实际 %d %v", status, body)
	}
}

func TestLoginFailuresAndCSRF(t *testing.T) {
	env := newAdminEnv(t)
	status, body, _ := env.do(t, http.MethodPost, "/api/admin/auth/login",
		map[string]any{"username": "admin", "password": "wrong-password"}, true)
	if status != http.StatusUnauthorized {
		t.Fatalf("错误口令应 401，实际 %d %v", status, body)
	}
	if errObj, ok := body["error"].(map[string]any); !ok || errObj["code"] != service.ErrCodeInvalidCredentials {
		t.Fatalf("错误码应为 invalid_credentials，实际 %v", body)
	}

	// 连续失败触发锁定。
	for i := 0; i < 5; i++ {
		_, _, _ = env.do(t, http.MethodPost, "/api/admin/auth/login",
			map[string]any{"username": "admin", "password": "wrong-password"}, true)
	}
	status, body, _ = env.do(t, http.MethodPost, "/api/admin/auth/login",
		map[string]any{"username": "admin", "password": "admin-password-123"}, true)
	if status != http.StatusTooManyRequests {
		t.Fatalf("锁定后应 429，实际 %d %v", status, body)
	}

	// 未登录访问受保护接口。
	status, _, _ = env.do(t, http.MethodGet, "/api/admin/stats", nil, false)
	if status != http.StatusUnauthorized {
		t.Fatalf("未登录应 401，实际 %d", status)
	}
}

func TestCSRFHeaderRequired(t *testing.T) {
	env := newAdminEnv(t)
	env.setupReady(t)

	status, _, _ := env.do(t, http.MethodPost, "/api/admin/cards",
		map[string]any{"type_id": 3, "count": 1}, false)
	if status != http.StatusForbidden {
		t.Fatalf("缺少 X-Requested-With 应 403，实际 %d", status)
	}
}

func TestCardLifecycle(t *testing.T) {
	env := newAdminEnv(t)
	env.setupReady(t)

	// 类型列表（预置 6 种）。
	status, body, _ := env.do(t, http.MethodGet, "/api/admin/card_types", nil, false)
	if status != http.StatusOK {
		t.Fatalf("类型列表失败: %d %v", status, body)
	}
	types, _ := body["data"].([]any)
	if len(types) != 6 {
		t.Fatalf("预置类型应为 6 个，实际 %d", len(types))
	}
	first, _ := types[0].(map[string]any)
	typeID := int64(first["id"].(float64))

	// 批量生成。
	status, body, _ = env.do(t, http.MethodPost, "/api/admin/cards/batch", map[string]any{
		"type_id": typeID, "count": 5, "length": 16, "max_rebinds": 2, "remark": "集成测试",
	}, true)
	if status != http.StatusOK {
		t.Fatalf("批量生成失败: %d %v", status, body)
	}
	codes, _ := body["codes"].([]any)
	if len(codes) != 5 {
		t.Fatalf("应生成 5 张卡密，实际 %d", len(codes))
	}
	firstCode, _ := codes[0].(string)
	if !strings.Contains(firstCode, "-") {
		t.Fatalf("返回的卡密应带分隔符便于展示，实际 %q", firstCode)
	}
	batchNo, _ := body["batch_no"].(string)
	if batchNo == "" {
		t.Fatalf("批次号不应为空")
	}

	// 列表 + 关键词检索（用带分隔符的卡密检索，验证归一化）。
	status, body, _ = env.do(t, http.MethodGet, "/api/admin/cards?keyword="+firstCode, nil, false)
	if status != http.StatusOK {
		t.Fatalf("列表失败: %d %v", status, body)
	}
	items, _ := body["data"].([]any)
	if len(items) != 1 {
		t.Fatalf("按卡密检索应命中 1 条，实际 %d", len(items))
	}
	card := items[0].(map[string]any)
	cardID := int64(card["id"].(float64))
	if card["display_state"] != "unused" {
		t.Fatalf("新卡密展示态应为 unused，实际 %v", card["display_state"])
	}
	if card["rebind_remaining"].(float64) != 2 {
		t.Fatalf("换绑剩余应为 2，实际 %v", card["rebind_remaining"])
	}

	// 详情。
	status, body, _ = env.do(t, http.MethodGet, pathf("/api/admin/cards/%d", cardID), nil, false)
	if status != http.StatusOK || body["card"] == nil {
		t.Fatalf("详情失败: %d %v", status, body)
	}

	// 延期（未激活卡：增加时长）。
	status, body, _ = env.do(t, http.MethodPost, pathf("/api/admin/cards/%d/extend", cardID),
		map[string]any{"days": 30}, true)
	if status != http.StatusOK {
		t.Fatalf("延期失败: %d %v", status, body)
	}

	// 禁用后再启用。
	status, body, _ = env.do(t, http.MethodPost, pathf("/api/admin/cards/%d/disable", cardID), nil, true)
	if status != http.StatusOK {
		t.Fatalf("禁用失败: %d %v", status, body)
	}
	card = body["card"].(map[string]any)
	if card["state"] != "disabled" || card["display_state"] != "disabled" {
		t.Fatalf("禁用后状态错误: %v", card)
	}
	status, body, _ = env.do(t, http.MethodPost, pathf("/api/admin/cards/%d/enable", cardID), nil, true)
	if status != http.StatusOK {
		t.Fatalf("启用失败: %d %v", status, body)
	}

	// 导出 CSV。
	req, _ := http.NewRequest(http.MethodGet, env.srv.URL+"/api/admin/cards/export?format=csv", nil)
	resp, err := env.auth.Do(req)
	if err != nil {
		t.Fatalf("导出失败: %v", err)
	}
	defer resp.Body.Close()
	csvData, _ := io.ReadAll(resp.Body)
	if !strings.Contains(string(csvData), "卡密,类型,状态") || !strings.Contains(string(csvData), firstCode) {
		t.Fatalf("CSV 内容不符合预期: %s", string(csvData[:min(400, len(csvData))]))
	}

	// 统计。
	status, body, _ = env.do(t, http.MethodGet, "/api/admin/stats", nil, false)
	if status != http.StatusOK {
		t.Fatalf("统计失败: %d %v", status, body)
	}
	cards, _ := body["cards"].(map[string]any)
	if cards["total"].(float64) < 5 {
		t.Fatalf("统计总数错误: %v", cards)
	}

	// 批量删除。
	status, body, _ = env.do(t, http.MethodPost, "/api/admin/cards/batch_action", map[string]any{
		"ids": []int64{cardID}, "action": "delete",
	}, true)
	if status != http.StatusOK || body["affected"].(float64) != 1 {
		t.Fatalf("批量删除失败: %d %v", status, body)
	}

	// 审计日志应包含以上操作。
	status, body, _ = env.do(t, http.MethodGet, "/api/admin/audit_logs?page_size=50", nil, false)
	if status != http.StatusOK {
		t.Fatalf("审计日志失败: %d %v", status, body)
	}
	logs, _ := body["data"].([]any)
	if len(logs) == 0 {
		t.Fatalf("审计日志不应为空")
	}
}

func TestSettingsValidationAndUpdate(t *testing.T) {
	env := newAdminEnv(t)
	env.setupReady(t)

	status, body, _ := env.do(t, http.MethodGet, "/api/admin/settings", nil, false)
	if status != http.StatusOK {
		t.Fatalf("读取设置失败: %d %v", status, body)
	}
	settings, _ := body["settings"].(map[string]any)
	if settings["activate_on"] != "verify" {
		t.Fatalf("默认激活时机应为 verify，实际 %v", settings["activate_on"])
	}
	if settings["session_timeout_seconds"].(float64) != 180 {
		t.Fatalf("默认会话超时应为 180，实际 %v", settings["session_timeout_seconds"])
	}

	// 会话超时小于 2 倍心跳应被拒。
	status, body, _ = env.do(t, http.MethodPut, "/api/admin/settings",
		map[string]any{"session_timeout_seconds": 30}, true)
	if status != http.StatusUnprocessableEntity {
		t.Fatalf("非法设置应 422，实际 %d %v", status, body)
	}

	status, body, _ = env.do(t, http.MethodPut, "/api/admin/settings", map[string]any{
		"heartbeat_interval_seconds": 20, "session_timeout_seconds": 60, "default_max_rebinds": 5,
	}, true)
	if status != http.StatusOK {
		t.Fatalf("保存设置失败: %d %v", status, body)
	}
	updated, _ := body["settings"].(map[string]any)
	if updated["heartbeat_interval_seconds"].(float64) != 20 || updated["default_max_rebinds"].(float64) != 5 {
		t.Fatalf("设置未生效: %v", updated)
	}
}

func TestCardTypeCRUDConflict(t *testing.T) {
	env := newAdminEnv(t)
	env.setupReady(t)

	status, body, _ := env.do(t, http.MethodPost, "/api/admin/card_types", map[string]any{
		"name": "测试卡", "duration_minutes": 2880, "max_rebinds": 2, "enabled": true, "sort_order": 99,
	}, true)
	if status != http.StatusOK {
		t.Fatalf("新增类型失败: %d %v", status, body)
	}
	created := body["type"].(map[string]any)
	typeID := int64(created["id"].(float64))
	if created["duration_text"] != "2 天" {
		t.Fatalf("时长文案错误: %v", created["duration_text"])
	}

	// 同名冲突。
	status, _, _ = env.do(t, http.MethodPost, "/api/admin/card_types", map[string]any{
		"name": "测试卡", "duration_minutes": 1440,
	}, true)
	if status != http.StatusConflict {
		t.Fatalf("同名类型应 409，实际 %d", status)
	}

	// 生成一张卡密后删除该类型应被拒绝。
	_, _, _ = env.do(t, http.MethodPost, "/api/admin/cards", map[string]any{"type_id": typeID, "count": 1}, true)
	status, body, _ = env.do(t, http.MethodDelete, pathf("/api/admin/card_types/%d", typeID), nil, true)
	if status != http.StatusConflict {
		t.Fatalf("被引用类型删除应 409，实际 %d %v", status, body)
	}
}

func pathf(format string, args ...any) string {
	return fmt.Sprintf(format, args...)
}

// 前端日志页发送的是 `YYYY-MM-DD HH:MM:SS`，卡密页发送的是 `YYYY-MM-DD`，两者都必须被接受。
func TestLogDateRangeFormats(t *testing.T) {
	env := newAdminEnv(t)
	env.setupReady(t)

	for _, path := range []string{
		"/api/admin/logs?from=2026-10-01%2000:00:00&to=2026-10-13%2023:59:59",
		"/api/admin/logs?from=2026-10-01&to=2026-10-13",
		"/api/admin/audit_logs?from=2026-10-01%2000:00:00&to=2026-10-13%2023:59:59",
		"/api/admin/cards?created_from=2026-10-01&created_to=2026-10-13",
	} {
		status, body, _ := env.do(t, http.MethodGet, path, nil, false)
		if status != http.StatusOK {
			t.Fatalf("%s 应返回 200，实际 %d %v", path, status, body)
		}
	}

	status, _, _ := env.do(t, http.MethodGet, "/api/admin/logs?from=2026/10/01", nil, false)
	if status != http.StatusUnprocessableEntity {
		t.Fatalf("非法时间格式应 422，实际 %d", status)
	}
}
