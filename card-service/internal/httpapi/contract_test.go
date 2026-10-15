package httpapi

import (
	"bytes"
	"context"
	"database/sql"
	"encoding/json"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"path/filepath"
	"regexp"
	"strings"
	"sync"
	"testing"
	"time"

	"card.hjj888.cc/cardservice/internal/config"
	"card.hjj888.cc/cardservice/internal/security"
	"card.hjj888.cc/cardservice/internal/service"
	"card.hjj888.cc/cardservice/internal/store"
)

type testEnv struct {
	svc *service.Service
	st  *store.Store
	srv *httptest.Server
	cfg *config.Config
}

func newTestEnv(t *testing.T) *testEnv {
	t.Helper()
	dir := t.TempDir()
	st, err := store.Open(filepath.Join(dir, "test.db"))
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
		AccessLog:              false,
		BootstrapAdminUsername: "admin",
		BootstrapAdminPassword: "admin-password-123",
	}
	if err := svc.Bootstrap(context.Background(), cfg); err != nil {
		t.Fatalf("初始化失败: %v", err)
	}
	srv := httptest.NewServer(NewServer(svc, cfg, logger).Handler())
	t.Cleanup(srv.Close)
	return &testEnv{svc: svc, st: st, srv: srv, cfg: cfg}
}

// post 以易语言客户端完全相同的形态发起请求：POST + application/json + JSON 对象。
func (e *testEnv) post(t *testing.T, path string, body map[string]any) (int, map[string]any) {
	t.Helper()
	raw, err := json.Marshal(body)
	if err != nil {
		t.Fatalf("序列化请求体失败: %v", err)
	}
	req, err := http.NewRequest(http.MethodPost, e.srv.URL+path, bytes.NewReader(raw))
	if err != nil {
		t.Fatalf("构造请求失败: %v", err)
	}
	req.Header.Set("Content-Type", "application/json")
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatalf("请求 %s 失败: %v", path, err)
	}
	defer resp.Body.Close()
	data, _ := io.ReadAll(resp.Body)
	out := map[string]any{}
	if err := json.Unmarshal(data, &out); err != nil {
		t.Fatalf("%s 返回不是 JSON: %s (%v)", path, string(data), err)
	}
	return resp.StatusCode, out
}

var timeLayoutRe = regexp.MustCompile(`^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$`)

// assertString 断言字段存在、是 JSON 字符串且取值符合预期。
func assertString(t *testing.T, m map[string]any, key, want string) {
	t.Helper()
	v, ok := m[key]
	if !ok {
		t.Fatalf("响应缺少字段 %q: %v", key, m)
	}
	s, ok := v.(string)
	if !ok {
		t.Fatalf("字段 %q 必须是 JSON 字符串（易语言用取文本读取），实际 %T: %v", key, v, v)
	}
	if want != "" && s != want {
		t.Fatalf("字段 %q 期望 %q，实际 %q", key, want, s)
	}
}

func assertBoolField(t *testing.T, m map[string]any, key string, want bool) {
	t.Helper()
	v, ok := m[key]
	if !ok {
		t.Fatalf("响应缺少字段 %q: %v", key, m)
	}
	b, ok := v.(bool)
	if !ok {
		t.Fatalf("字段 %q 必须是 JSON 布尔（易语言用取逻辑值读取），实际 %T: %v", key, v, v)
	}
	if b != want {
		t.Fatalf("字段 %q 期望 %v，实际 %v", key, want, b)
	}
}

func assertNoNull(t *testing.T, m map[string]any) {
	t.Helper()
	for k, v := range m {
		if v == nil {
			t.Fatalf("字段 %q 返回了 null，易语言取文本会拿到空值", k)
		}
	}
}

func (e *testEnv) insertCard(t *testing.T, mutate func(c *store.Card)) *store.Card {
	t.Helper()
	code, err := security.GenerateCardCode("", 16)
	if err != nil {
		t.Fatalf("生成卡密失败: %v", err)
	}
	now := time.Now().Unix()
	card := &store.Card{
		Code:            code,
		TypeName:        "月卡",
		DurationMinutes: 43200,
		State:           "unused",
		MaxRebinds:      3,
		CreatedAt:       now,
		UpdatedAt:       now,
	}
	if mutate != nil {
		mutate(card)
	}
	ctx := context.Background()
	if err := e.st.Write(ctx, func(tx *store.Tx) error { return tx.InsertCard(ctx, card) }); err != nil {
		t.Fatalf("插入卡密失败: %v", err)
	}
	return card
}

func formatted(code string) string { return security.FormatCardCode(code) }

// TestVerifyLoginHeartbeatLogout 覆盖易语言客户端的完整调用序列。
func TestVerifyLoginHeartbeatLogout(t *testing.T) {
	env := newTestEnv(t)
	card := env.insertCard(t, nil)
	machine := "9f2c4a1b8e7d6530"

	_, verify := env.post(t, "/api/verify", map[string]any{"code": formatted(card.Code), "machine_code": machine})
	assertString(t, verify, "code", "0")
	assertString(t, verify, "status", "valid")
	assertString(t, verify, "type", "月卡")
	assertString(t, verify, "expires_at", "")
	if !timeLayoutRe.MatchString(verify["expires_at"].(string)) {
		t.Fatalf("expires_at 格式错误: %v", verify["expires_at"])
	}
	assertBoolField(t, verify, "permanent", false)
	assertBoolField(t, verify, "success", false)
	assertNoNull(t, verify)

	_, login := env.post(t, "/api/card/login", map[string]any{"code": formatted(card.Code), "machine_code": machine})
	assertString(t, login, "code", "0")
	assertString(t, login, "status", "ok")
	token, _ := login["session_token"].(string)
	if len(token) < 20 {
		t.Fatalf("session_token 异常: %q", token)
	}
	if hb, ok := login["heartbeat_interval"].(float64); !ok || hb <= 0 {
		t.Fatalf("heartbeat_interval 缺失或非法: %v", login["heartbeat_interval"])
	}

	_, hb := env.post(t, "/api/card/heartbeat", map[string]any{"session_token": token})
	assertString(t, hb, "code", "0")
	assertString(t, hb, "status", "ok")
	assertString(t, hb, "expires_at", "")

	_, logout := env.post(t, "/api/card/logout", map[string]any{"session_token": token})
	assertString(t, logout, "code", "0")
	assertString(t, logout, "status", "ok")

	// 下线是幂等的。
	_, logout2 := env.post(t, "/api/card/logout", map[string]any{"session_token": token})
	assertString(t, logout2, "code", "0")
	assertString(t, logout2, "status", "ok")

	// 下线后心跳必须失败。
	_, hb2 := env.post(t, "/api/card/heartbeat", map[string]any{"session_token": token})
	assertString(t, hb2, "code", "401")
	assertString(t, hb2, "status", "invalid_session")
}

func TestVerifyUnknownCardAndValidation(t *testing.T) {
	env := newTestEnv(t)

	_, res := env.post(t, "/api/verify", map[string]any{"code": "AAAA-BBBB-CCCC-DDDD", "machine_code": "machine-1"})
	assertString(t, res, "code", "404")
	assertString(t, res, "status", "not_found")
	assertString(t, res, "message", "卡密不存在")

	_, res = env.post(t, "/api/verify", map[string]any{"code": "", "machine_code": "machine-1"})
	assertString(t, res, "code", "400")
	assertString(t, res, "status", "invalid")

	_, res = env.post(t, "/api/verify", map[string]any{"code": "AAAABBBBCCCCDDDD"})
	assertString(t, res, "code", "400")
	assertString(t, res, "status", "invalid")
}

func TestVerifyBindsMachineAndRejectsOtherMachine(t *testing.T) {
	env := newTestEnv(t)
	card := env.insertCard(t, nil)

	_, res := env.post(t, "/api/verify", map[string]any{"code": formatted(card.Code), "machine_code": "MACHINE-A"})
	assertString(t, res, "code", "0")
	assertString(t, res, "status", "valid")

	_, res = env.post(t, "/api/verify", map[string]any{"code": formatted(card.Code), "machine_code": "MACHINE-B"})
	assertString(t, res, "code", "403")
	assertString(t, res, "status", "bound_other")
	assertString(t, res, "message", "卡密已绑定其他机器")

	// 机器码大小写与空白差异不应导致误判。
	_, res = env.post(t, "/api/verify", map[string]any{"code": formatted(card.Code), "machine_code": " machine-a "})
	assertString(t, res, "code", "0")
}

func TestCardStatusStates(t *testing.T) {
	env := newTestEnv(t)
	now := time.Now().Unix()

	unbound := env.insertCard(t, nil)
	_, res := env.post(t, "/api/card/status", map[string]any{"code": formatted(unbound.Code), "machine_code": "M1"})
	assertString(t, res, "code", "0")
	assertString(t, res, "status", "unbound")

	bound := env.insertCard(t, func(c *store.Card) {
		c.State = "active"
		c.BoundMachine = "M1"
		c.BoundAt = now
		c.ActivatedAt = now
		c.ExpiresAt = nullInt(now + 3600)
	})
	_, res = env.post(t, "/api/card/status", map[string]any{"code": formatted(bound.Code), "machine_code": "M1"})
	assertString(t, res, "code", "0")
	assertString(t, res, "status", "valid")
	_, res = env.post(t, "/api/card/status", map[string]any{"code": formatted(bound.Code), "machine_code": "OTHER"})
	assertString(t, res, "status", "bound_other")
	_, res = env.post(t, "/api/card/status", map[string]any{"code": formatted(bound.Code)})
	assertString(t, res, "status", "valid")

	expired := env.insertCard(t, func(c *store.Card) {
		c.State = "active"
		c.ActivatedAt = now - 7200
		c.ExpiresAt = nullInt(now - 60)
	})
	_, res = env.post(t, "/api/card/status", map[string]any{"code": formatted(expired.Code), "machine_code": "M1"})
	assertString(t, res, "status", "expired")

	disabled := env.insertCard(t, func(c *store.Card) { c.State = "disabled" })
	_, res = env.post(t, "/api/card/status", map[string]any{"code": formatted(disabled.Code), "machine_code": "M1"})
	assertString(t, res, "status", "disabled")

	_, res = env.post(t, "/api/card/status", map[string]any{"code": "ZZZZZZZZZZZZZZZZ", "machine_code": "M1"})
	assertString(t, res, "code", "404")
	assertString(t, res, "status", "not_found")
}

func TestRebindFlowAndLimit(t *testing.T) {
	env := newTestEnv(t)
	card := env.insertCard(t, func(c *store.Card) { c.MaxRebinds = 1; c.RebindCount = 0 })

	// 首次绑定到 A。
	_, res := env.post(t, "/api/verify", map[string]any{"code": formatted(card.Code), "machine_code": "MACHINE-A"})
	assertString(t, res, "code", "0")

	// 换绑到 B：success 必须是布尔 true。
	_, res = env.post(t, "/api/card/rebind", map[string]any{"code": formatted(card.Code), "new_machine_code": "MACHINE-B"})
	assertString(t, res, "code", "0")
	assertString(t, res, "status", "ok")
	assertBoolField(t, res, "success", true)
	if left, ok := res["rebind_remaining"].(float64); !ok || int(left) != 0 {
		t.Fatalf("rebind_remaining 期望 0，实际 %v", res["rebind_remaining"])
	}

	// 旧机器被拒，新机器可用。
	_, res = env.post(t, "/api/verify", map[string]any{"code": formatted(card.Code), "machine_code": "MACHINE-A"})
	assertString(t, res, "code", "403")
	_, res = env.post(t, "/api/verify", map[string]any{"code": formatted(card.Code), "machine_code": "MACHINE-B"})
	assertString(t, res, "code", "0")

	// 次数用尽。
	_, res = env.post(t, "/api/card/rebind", map[string]any{"code": formatted(card.Code), "new_machine_code": "MACHINE-C"})
	assertString(t, res, "code", "409")
	assertString(t, res, "status", "rebind_limit")
	assertBoolField(t, res, "success", false)

	// 相同机器码不消耗次数。
	_, res = env.post(t, "/api/card/rebind", map[string]any{"code": formatted(card.Code), "new_machine_code": "MACHINE-B"})
	assertString(t, res, "code", "0")
	assertBoolField(t, res, "success", true)
}

func TestRebindKicksOnlineSession(t *testing.T) {
	env := newTestEnv(t)
	card := env.insertCard(t, nil)

	_, login := env.post(t, "/api/card/login", map[string]any{"code": formatted(card.Code), "machine_code": "MACHINE-A"})
	token := login["session_token"].(string)
	assertString(t, login, "code", "0")

	_, res := env.post(t, "/api/card/rebind", map[string]any{"code": formatted(card.Code), "new_machine_code": "MACHINE-B"})
	assertBoolField(t, res, "success", true)

	_, hb := env.post(t, "/api/card/heartbeat", map[string]any{"session_token": token})
	assertString(t, hb, "code", "401")
	assertString(t, hb, "status", "invalid_session")
}

func TestLoginTakeoverSameMachine(t *testing.T) {
	env := newTestEnv(t)
	card := env.insertCard(t, nil)

	_, first := env.post(t, "/api/card/login", map[string]any{"code": formatted(card.Code), "machine_code": "MACHINE-A"})
	assertString(t, first, "code", "0")
	firstToken := first["session_token"].(string)

	// 同一台机器重复上线（例如客户端重启）：默认允许顶号。
	_, second := env.post(t, "/api/card/login", map[string]any{"code": formatted(card.Code), "machine_code": "MACHINE-A"})
	assertString(t, second, "code", "0")
	assertString(t, second, "status", "ok")
	secondToken := second["session_token"].(string)

	_, hb := env.post(t, "/api/card/heartbeat", map[string]any{"session_token": firstToken})
	assertString(t, hb, "code", "409")
	assertString(t, hb, "status", "kicked")

	_, hb = env.post(t, "/api/card/heartbeat", map[string]any{"session_token": secondToken})
	assertString(t, hb, "code", "0")
	assertString(t, hb, "status", "ok")

	_, stats := env.post(t, "/api/card/status", map[string]any{"code": formatted(card.Code), "machine_code": "MACHINE-A"})
	assertString(t, stats, "status", "valid")
	if online, ok := stats["online"].(bool); !ok || !online {
		t.Fatalf("上线后 online 应为 true，实际 %v", stats["online"])
	}
}

// 换机必须走换绑：机器码绑定是反共享的核心机制，不能被"顶号"绕过。
func TestLoginFromOtherMachineRequiresRebind(t *testing.T) {
	env := newTestEnv(t)
	card := env.insertCard(t, nil)

	_, first := env.post(t, "/api/card/login", map[string]any{"code": formatted(card.Code), "machine_code": "MACHINE-A"})
	assertString(t, first, "code", "0")

	_, denied := env.post(t, "/api/card/login", map[string]any{"code": formatted(card.Code), "machine_code": "MACHINE-B"})
	assertString(t, denied, "code", "403")
	assertString(t, denied, "status", "bound_other")

	_, rebind := env.post(t, "/api/card/rebind", map[string]any{"code": formatted(card.Code), "new_machine_code": "MACHINE-B"})
	assertBoolField(t, rebind, "success", true)

	_, second := env.post(t, "/api/card/login", map[string]any{"code": formatted(card.Code), "machine_code": "MACHINE-B"})
	assertString(t, second, "code", "0")
	assertString(t, second, "status", "ok")
}

// allow_takeover=false 时必须拒绝重复登录。
func TestLoginWithoutTakeover(t *testing.T) {
	env := newTestEnv(t)
	card := env.insertCard(t, nil)
	ctx := context.Background()
	admin, _, _, err := env.svc.AdminLogin(ctx, "admin", "admin-password-123", service.ClientInfo{IP: "1.1.1.1"})
	if err != nil {
		t.Fatalf("管理员登录失败: %v", err)
	}
	disabled := false
	if _, err := env.svc.AdminUpdateSettings(ctx, service.SettingsInput{AllowTakeover: &disabled}, admin, "1.1.1.1"); err != nil {
		t.Fatalf("关闭顶号失败: %v", err)
	}

	_, first := env.post(t, "/api/card/login", map[string]any{"code": formatted(card.Code), "machine_code": "MACHINE-A"})
	assertString(t, first, "code", "0")

	_, second := env.post(t, "/api/card/login", map[string]any{"code": formatted(card.Code), "machine_code": "MACHINE-A"})
	assertString(t, second, "code", "409")
	assertString(t, second, "status", "online_other")
}

func TestExpiredAndDisabledCards(t *testing.T) {
	env := newTestEnv(t)
	now := time.Now().Unix()

	expired := env.insertCard(t, func(c *store.Card) {
		c.State = "active"
		c.ActivatedAt = now - 7200
		c.ExpiresAt = nullInt(now - 1)
	})
	_, res := env.post(t, "/api/verify", map[string]any{"code": formatted(expired.Code), "machine_code": "M1"})
	assertString(t, res, "code", "410")
	assertString(t, res, "status", "expired")
	assertString(t, res, "message", "卡密已过期")

	disabled := env.insertCard(t, func(c *store.Card) { c.State = "disabled" })
	_, res = env.post(t, "/api/verify", map[string]any{"code": formatted(disabled.Code), "machine_code": "M1"})
	assertString(t, res, "code", "423")
	assertString(t, res, "status", "disabled")

	_, res = env.post(t, "/api/card/login", map[string]any{"code": formatted(disabled.Code), "machine_code": "M1"})
	assertString(t, res, "code", "423")
}

func TestExpiryClosesOnlineSession(t *testing.T) {
	env := newTestEnv(t)
	now := time.Now().Unix()
	card := env.insertCard(t, func(c *store.Card) {
		c.State = "active"
		c.BoundMachine = "M1"
		c.BoundAt = now
		c.ActivatedAt = now - 10
		c.ExpiresAt = nullInt(now + 60)
	})

	_, login := env.post(t, "/api/card/login", map[string]any{"code": formatted(card.Code), "machine_code": "M1"})
	token := login["session_token"].(string)
	assertString(t, login, "code", "0")

	// 把卡密改成已过期，心跳必须失败并关闭会话。
	ctx := context.Background()
	if err := env.st.Write(ctx, func(tx *store.Tx) error {
		c, err := tx.CardByID(ctx, card.ID)
		if err != nil {
			return err
		}
		c.ExpiresAt = nullInt(now - 1)
		c.UpdatedAt = now
		return tx.UpdateCard(ctx, c)
	}); err != nil {
		t.Fatalf("更新卡密失败: %v", err)
	}

	_, hb := env.post(t, "/api/card/heartbeat", map[string]any{"session_token": token})
	assertString(t, hb, "code", "410")
	assertString(t, hb, "status", "expired")
}

func TestPermanentCard(t *testing.T) {
	env := newTestEnv(t)
	card := env.insertCard(t, func(c *store.Card) {
		c.TypeName = "永久"
		c.DurationMinutes = 0
	})

	_, res := env.post(t, "/api/verify", map[string]any{"code": formatted(card.Code), "machine_code": "M1"})
	assertString(t, res, "code", "0")
	assertString(t, res, "expires_at", "永久有效")
	assertBoolField(t, res, "permanent", true)
	if ts, ok := res["expires_at_ts"].(float64); !ok || ts != 0 {
		t.Fatalf("永久卡 expires_at_ts 应为 0，实际 %v", res["expires_at_ts"])
	}
}

func TestHeartbeatInvalidToken(t *testing.T) {
	env := newTestEnv(t)
	_, res := env.post(t, "/api/card/heartbeat", map[string]any{"session_token": "not-a-real-token"})
	assertString(t, res, "code", "401")
	assertString(t, res, "status", "invalid_session")

	_, res = env.post(t, "/api/card/heartbeat", map[string]any{})
	assertString(t, res, "code", "400")
}

func TestMethodAndMalformedBody(t *testing.T) {
	env := newTestEnv(t)

	resp, err := http.Get(env.srv.URL + "/api/verify")
	if err != nil {
		t.Fatalf("GET 请求失败: %v", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusMethodNotAllowed {
		t.Fatalf("非 POST 应返回 405，实际 %d", resp.StatusCode)
	}
	var out map[string]any
	data, _ := io.ReadAll(resp.Body)
	if err := json.Unmarshal(data, &out); err != nil {
		t.Fatalf("405 响应必须是 JSON 包络: %s", string(data))
	}
	assertString(t, out, "code", "405")

	req, _ := http.NewRequest(http.MethodPost, env.srv.URL+"/api/verify", strings.NewReader("{不是 JSON"))
	req.Header.Set("Content-Type", "application/json")
	resp2, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatalf("请求失败: %v", err)
	}
	defer resp2.Body.Close()
	data2, _ := io.ReadAll(resp2.Body)
	var out2 map[string]any
	if err := json.Unmarshal(data2, &out2); err != nil {
		t.Fatalf("非法 JSON 也要返回包络: %s", string(data2))
	}
	if resp2.StatusCode != http.StatusOK {
		t.Fatalf("业务失败应返回 HTTP 200（易语言解析路径依赖），实际 %d", resp2.StatusCode)
	}
	assertString(t, out2, "code", "400")
}

func TestRateLimit(t *testing.T) {
	env := newTestEnv(t)
	card := env.insertCard(t, nil)
	ctx := context.Background()
	limit := 5
	admin, _, _, err := env.svc.AdminLogin(ctx, "admin", "admin-password-123", service.ClientInfo{IP: "1.1.1.1"})
	if err != nil {
		t.Fatalf("管理员登录失败: %v", err)
	}
	if _, err := env.svc.AdminUpdateSettings(ctx, service.SettingsInput{
		RateLimitCard: &limit,
		RateLimitIP:   intPtr(100000),
	}, admin, "1.1.1.1"); err != nil {
		t.Fatalf("更新限流设置失败: %v", err)
	}

	last := map[string]any{}
	for i := 0; i < limit+1; i++ {
		_, last = env.post(t, "/api/verify", map[string]any{"code": formatted(card.Code), "machine_code": "M1"})
	}
	assertString(t, last, "code", "429")
	assertString(t, last, "status", "rate_limited")
}

func TestConcurrentFirstVerifyBindsOnce(t *testing.T) {
	env := newTestEnv(t)
	card := env.insertCard(t, nil)

	var wg sync.WaitGroup
	results := make([]string, 2)
	machines := []string{"CONCURRENT-A", "CONCURRENT-B"}
	for i := range machines {
		wg.Add(1)
		go func(idx int) {
			defer wg.Done()
			_, res := env.post(t, "/api/verify", map[string]any{
				"code": formatted(card.Code), "machine_code": machines[idx],
			})
			if code, ok := res["code"].(string); ok {
				results[idx] = code
			}
		}(i)
	}
	wg.Wait()

	success := 0
	for _, code := range results {
		if code == "0" {
			success++
		}
	}
	if success != 1 {
		t.Fatalf("并发首次校验应当只有一台机器绑定成功，实际成功 %d 次（%v）", success, results)
	}
}

func TestAdminUIServedOrExplained(t *testing.T) {
	env := newTestEnv(t)
	// 前端已构建（web/dist 存在）：根路径返回 SPA 外壳；未构建：返回明确的 503 提示而不是白屏。
	resp, err := http.Get(env.srv.URL + "/")
	if err != nil {
		t.Fatalf("请求失败: %v", err)
	}
	defer resp.Body.Close()
	body, _ := io.ReadAll(resp.Body)
	switch resp.StatusCode {
	case http.StatusOK:
		if !strings.Contains(string(body), `id="app"`) {
			t.Fatalf("根路径应返回管理后台 SPA 外壳，实际: %.200s", string(body))
		}
	case http.StatusServiceUnavailable:
		if !strings.Contains(string(body), "前端尚未构建") {
			t.Fatalf("未构建时应给出明确提示，实际: %.200s", string(body))
		}
	default:
		t.Fatalf("根路径返回了意外状态码 %d", resp.StatusCode)
	}

	// 未匹配的 API 路径必须返回 JSON 包络而不是 SPA。
	apiResp, err := http.Get(env.srv.URL + "/api/not-exist")
	if err != nil {
		t.Fatalf("请求失败: %v", err)
	}
	defer apiResp.Body.Close()
	if apiResp.StatusCode != http.StatusNotFound {
		t.Fatalf("未知 API 路径应 404，实际 %d", apiResp.StatusCode)
	}
}

func intPtr(v int) *int { return &v }

func nullInt(v int64) sql.NullInt64 {
	return sql.NullInt64{Int64: v, Valid: true}
}
