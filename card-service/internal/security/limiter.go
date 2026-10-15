package security

import (
	"sync"
	"time"
)

// Limiter 是一个按 key 计数的滑动窗口限流器（内存实现）。
//
// 卡密服务是单机部署、请求量可控，内存限流足够；进程重启后窗口清零是可接受的行为。
type Limiter struct {
	mu      sync.Mutex
	entries map[string]*windowEntry
	window  time.Duration
	// now 便于测试注入。
	now func() time.Time
}

type windowEntry struct {
	count       int
	windowStart time.Time
}

// NewLimiter 创建限流器，window 为统计窗口（通常 1 分钟）。
func NewLimiter(window time.Duration) *Limiter {
	l := &Limiter{
		entries: make(map[string]*windowEntry),
		window:  window,
		now:     time.Now,
	}
	return l
}

// Allow 在 key 上消耗一次配额；limit <= 0 表示不限流。
func (l *Limiter) Allow(key string, limit int) bool {
	if limit <= 0 {
		return true
	}
	now := l.now()
	l.mu.Lock()
	defer l.mu.Unlock()
	e, ok := l.entries[key]
	if !ok || now.Sub(e.windowStart) >= l.window {
		l.entries[key] = &windowEntry{count: 1, windowStart: now}
		return true
	}
	if e.count >= limit {
		return false
	}
	e.count++
	return true
}

// GC 清理过期窗口，避免 key 无限增长；由后台协程定期调用。
func (l *Limiter) GC() {
	now := l.now()
	l.mu.Lock()
	defer l.mu.Unlock()
	for k, e := range l.entries {
		if now.Sub(e.windowStart) >= 2*l.window {
			delete(l.entries, k)
		}
	}
}

// RetryAfter 返回距离当前窗口重置的秒数（至少 1 秒）。
func (l *Limiter) RetryAfter(key string) int {
	now := l.now()
	l.mu.Lock()
	defer l.mu.Unlock()
	e, ok := l.entries[key]
	if !ok {
		return 1
	}
	remain := l.window - now.Sub(e.windowStart)
	if remain <= 0 {
		return 1
	}
	return int(remain.Seconds()) + 1
}

// FailureGuard 是账号维度的失败计数锁（管理员登录用）：连续失败 N 次锁定一段时间。
type FailureGuard struct {
	mu      sync.Mutex
	entries map[string]*failureEntry
	maxFail int
	lockFor time.Duration
	now     func() time.Time
}

type failureEntry struct {
	failures    int
	lockedUntil time.Time
}

// NewFailureGuard 创建失败锁：maxFail 次失败后锁定 lockFor。
func NewFailureGuard(maxFail int, lockFor time.Duration) *FailureGuard {
	return &FailureGuard{
		entries: make(map[string]*failureEntry),
		maxFail: maxFail,
		lockFor: lockFor,
		now:     time.Now,
	}
}

// Locked 返回该 key 是否处于锁定期，以及剩余锁定秒数。
func (g *FailureGuard) Locked(key string) (bool, int) {
	now := g.now()
	g.mu.Lock()
	defer g.mu.Unlock()
	e, ok := g.entries[key]
	if !ok || e.lockedUntil.IsZero() {
		return false, 0
	}
	if now.After(e.lockedUntil) {
		delete(g.entries, key)
		return false, 0
	}
	return true, int(e.lockedUntil.Sub(now).Seconds()) + 1
}

// RecordFailure 记录一次失败，达到阈值即锁定。
func (g *FailureGuard) RecordFailure(key string) {
	now := g.now()
	g.mu.Lock()
	defer g.mu.Unlock()
	e, ok := g.entries[key]
	if !ok {
		e = &failureEntry{}
		g.entries[key] = e
	}
	if !e.lockedUntil.IsZero() && now.After(e.lockedUntil) {
		e.failures = 0
		e.lockedUntil = time.Time{}
	}
	e.failures++
	if e.failures >= g.maxFail {
		e.lockedUntil = now.Add(g.lockFor)
		e.failures = 0
	}
}

// Reset 成功后清除失败计数。
func (g *FailureGuard) Reset(key string) {
	g.mu.Lock()
	defer g.mu.Unlock()
	delete(g.entries, key)
}
