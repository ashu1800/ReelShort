package security

import (
	"strings"
	"testing"
	"time"
)

func TestNormalizeAndFormatCardCode(t *testing.T) {
	cases := map[string]string{
		"abcd-efgh-ijkl-mnpq":   "ABCDEFGHIJKLMNPQ",
		" ABCD EFGH IJKL MNPQ ": "ABCDEFGHIJKLMNPQ",
		"ABCDEFGHIJKLMNPQ":      "ABCDEFGHIJKLMNPQ",
		"":                      "",
		"----":                  "",
	}
	for in, want := range cases {
		if got := NormalizeCardCode(in); got != want {
			t.Fatalf("NormalizeCardCode(%q) = %q，期望 %q", in, got, want)
		}
	}
	if got := FormatCardCode("ABCDEFGHIJKLMNPQ"); got != "ABCD-EFGH-IJKL-MNPQ" {
		t.Fatalf("FormatCardCode 结果错误: %q", got)
	}
}

func TestGenerateCardCode(t *testing.T) {
	seen := map[string]bool{}
	for i := 0; i < 500; i++ {
		code, err := GenerateCardCode("", 16)
		if err != nil {
			t.Fatalf("生成失败: %v", err)
		}
		if len(code) != 16 {
			t.Fatalf("长度错误: %q", code)
		}
		for _, r := range code {
			if !strings.ContainsRune(cardAlphabet, r) {
				t.Fatalf("出现字母表外字符 %q: %q", r, code)
			}
		}
		if seen[code] {
			t.Fatalf("500 次生成出现重复: %q", code)
		}
		seen[code] = true
	}
	if _, err := GenerateCardCode("PREFIX", 6); err == nil {
		t.Fatal("前缀长于总长度时应报错")
	}
	code, err := GenerateCardCode("ab", 12)
	if err != nil {
		t.Fatalf("带前缀生成失败: %v", err)
	}
	if !strings.HasPrefix(code, "AB") || len(code) != 12 {
		t.Fatalf("前缀处理错误: %q", code)
	}
}

func TestMasking(t *testing.T) {
	if got := MaskCardCode("ABCDEFGHIJKLMNPQ"); got != "ABCD-****-****-MNPQ" {
		t.Fatalf("卡密脱敏错误: %q", got)
	}
	if got := MaskMachine("9F2C4A1B8E7D6530"); got != "9F2C4A**6530" {
		t.Fatalf("机器码脱敏错误: %q", got)
	}
	if got := MaskMachine("ABC"); got != "***" {
		t.Fatalf("短机器码脱敏错误: %q", got)
	}
}

func TestNormalizeMachine(t *testing.T) {
	if got := NormalizeMachine(" 9f2c 4a1b\n"); got != "9F2C4A1B" {
		t.Fatalf("机器码归一化错误: %q", got)
	}
	long := strings.Repeat("A", 200)
	if got := NormalizeMachine(long); len(got) != 128 {
		t.Fatalf("超长机器码应截断到 128，实际 %d", len(got))
	}
}

func TestSessionTokenHash(t *testing.T) {
	raw, hash, err := NewSessionToken()
	if err != nil {
		t.Fatalf("生成令牌失败: %v", err)
	}
	if len(raw) != 43 {
		t.Fatalf("令牌长度应为 43（32 字节 base64url），实际 %d", len(raw))
	}
	if HashToken(raw) != hash {
		t.Fatal("令牌哈希不一致")
	}
	raw2, _, _ := NewSessionToken()
	if raw == raw2 {
		t.Fatal("两次生成的令牌不应相同")
	}
}

func TestLimiterWindow(t *testing.T) {
	lim := NewLimiter(time.Minute)
	now := time.Now()
	lim.now = func() time.Time { return now }

	for i := 0; i < 5; i++ {
		if !lim.Allow("k", 5) {
			t.Fatalf("第 %d 次请求不应被限流", i+1)
		}
	}
	if lim.Allow("k", 5) {
		t.Fatal("超出阈值应被限流")
	}
	// 不同 key 互不影响。
	if !lim.Allow("other", 5) {
		t.Fatal("不同 key 不应共享窗口")
	}
	// 窗口滑动后恢复。
	now = now.Add(61 * time.Second)
	if !lim.Allow("k", 5) {
		t.Fatal("窗口过期后应恢复")
	}
	// limit <= 0 表示不限流。
	for i := 0; i < 100; i++ {
		if !lim.Allow("unlimited", 0) {
			t.Fatal("limit<=0 应不限流")
		}
	}
}

func TestFailureGuardLock(t *testing.T) {
	guard := NewFailureGuard(3, time.Minute)
	now := time.Now()
	guard.now = func() time.Time { return now }

	for i := 0; i < 3; i++ {
		if locked, _ := guard.Locked("u"); locked {
			t.Fatalf("第 %d 次失败前不应锁定", i)
		}
		guard.RecordFailure("u")
	}
	locked, remain := guard.Locked("u")
	if !locked || remain <= 0 {
		t.Fatalf("连续 3 次失败后应锁定，locked=%v remain=%d", locked, remain)
	}
	now = now.Add(61 * time.Second)
	if locked, _ := guard.Locked("u"); locked {
		t.Fatal("锁定期过后应解锁")
	}
	guard.RecordFailure("u")
	guard.Reset("u")
	if locked, _ := guard.Locked("u"); locked {
		t.Fatal("Reset 后不应锁定")
	}
}
