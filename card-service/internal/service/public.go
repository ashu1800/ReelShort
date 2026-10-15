package service

import (
	"context"
	"database/sql"
	"errors"

	"card.hjj888.cc/cardservice/internal/security"
	"card.hjj888.cc/cardservice/internal/store"
)

// 业务响应码（字符串！易语言客户端用 Json.取文本("code") 读取）。
const (
	CodeOK               = "0"
	CodeParam            = "400"
	CodeUnauthorized     = "401"
	CodeForbidden        = "403"
	CodeNotFound         = "404"
	CodeMethodNotAllowed = "405"
	CodeConflict         = "409"
	CodeGone             = "410"
	CodeLocked           = "423"
	CodeRateLimited      = "429"
	CodeServerError      = "500"
)

// 业务状态（字符串！）。
const (
	StatusValid            = "valid"
	StatusOK               = "ok"
	StatusInvalid          = "invalid"
	StatusNotFound         = "not_found"
	StatusUnbound          = "unbound"
	StatusBoundOther       = "bound_other"
	StatusExpired          = "expired"
	StatusDisabled         = "disabled"
	StatusInvalidSession   = "invalid_session"
	StatusKicked           = "kicked"
	StatusOnlineOther      = "online_other"
	StatusRebindLimit      = "rebind_limit"
	StatusRateLimited      = "rate_limited"
	StatusError            = "error"
	StatusMethodNotAllowed = "method_not_allowed"
)

// Result 是一次公共接口调用的结果，由 httpapi 渲染为响应包络。
type Result struct {
	Code    string
	Status  string
	Message string
	Success bool

	Card *CardSnapshot

	SessionToken      string
	SessionExpiresAt  int64
	HeartbeatInterval int
	RebindRemaining   int
	MachineCode       string

	// RetryAfter 仅在限流时给出（秒），HTTP 层写入 Retry-After 响应头。
	RetryAfter int
}

// CardSnapshot 是返回给客户端的卡密信息快照。
type CardSnapshot struct {
	TypeName         string
	ExpiresAt        int64 // 0 表示永久或未激活
	Permanent        bool
	ActivatedAt      int64
	RemainingSeconds int64
	RebindRemaining  int
	Online           bool
}

// ClientInfo 是调用方信息（用于限流与日志）。
type ClientInfo struct {
	IP        string
	UserAgent string
}

// Verify 实现 POST /api/verify。
func (s *Service) Verify(ctx context.Context, code, machine string, ci ClientInfo) *Result {
	code = security.NormalizeCardCode(code)
	machine = security.NormalizeMachine(machine)
	if code == "" {
		return paramError("参数错误：缺少 code")
	}
	if machine == "" {
		return paramError("参数错误：缺少 machine_code")
	}
	set, err := s.Settings(ctx)
	if err != nil {
		s.logger.Error("读取设置失败", "error", err)
		return serverError()
	}
	if r := s.allow("verify", ci.IP, code, set); r != nil {
		return r
	}

	now := s.now().Unix()
	var result *Result
	err = s.store.Write(ctx, func(tx *store.Tx) error {
		card, err := tx.CardByCode(ctx, code)
		if errors.Is(err, store.ErrNotFound) {
			result = notFound()
			return s.writeLog(ctx, tx, nil, code, "verify", "fail", result.Message, machine, ci)
		}
		if err != nil {
			return err
		}
		res, err := s.evaluateCard(ctx, tx, card, machine, now, set, "verify")
		if err != nil {
			return err
		}
		result = res
		return s.logResult(ctx, tx, card, code, "verify", res, machine, ci)
	})
	return s.finish(err, result)
}

// Status 实现 POST /api/card/status。
func (s *Service) Status(ctx context.Context, code, machine string, ci ClientInfo) *Result {
	code = security.NormalizeCardCode(code)
	machine = security.NormalizeMachine(machine)
	if code == "" {
		return paramError("参数错误：缺少 code")
	}
	set, err := s.Settings(ctx)
	if err != nil {
		s.logger.Error("读取设置失败", "error", err)
		return serverError()
	}
	if r := s.allow("status", ci.IP, code, set); r != nil {
		return r
	}

	now := s.now().Unix()
	var result *Result
	err = s.store.Write(ctx, func(tx *store.Tx) error {
		card, err := tx.CardByCode(ctx, code)
		if errors.Is(err, store.ErrNotFound) {
			result = notFound()
			return s.writeLog(ctx, tx, nil, code, "status", "fail", result.Message, machine, ci)
		}
		if err != nil {
			return err
		}
		status := StatusValid
		switch {
		case card.State == "disabled":
			status = StatusDisabled
		case card.Expired(now):
			status = StatusExpired
		case card.BoundMachine == "":
			status = StatusUnbound
		case machine != "" && card.BoundMachine != machine:
			status = StatusBoundOther
		}
		result = &Result{Code: CodeOK, Status: status, Card: s.snapshot(card, now)}
		return s.logResult(ctx, tx, card, code, "status", result, machine, ci)
	})
	return s.finish(err, result)
}

// Rebind 实现 POST /api/card/rebind（换绑机器码）。
func (s *Service) Rebind(ctx context.Context, code, newMachine string, ci ClientInfo) *Result {
	code = security.NormalizeCardCode(code)
	newMachine = security.NormalizeMachine(newMachine)
	if code == "" {
		return paramError("参数错误：缺少 code")
	}
	if newMachine == "" {
		return paramError("参数错误：缺少 new_machine_code")
	}
	set, err := s.Settings(ctx)
	if err != nil {
		s.logger.Error("读取设置失败", "error", err)
		return serverError()
	}
	if r := s.allow("rebind", ci.IP, code, set); r != nil {
		return r
	}

	now := s.now().Unix()
	var result *Result
	err = s.store.Write(ctx, func(tx *store.Tx) error {
		card, err := tx.CardByCode(ctx, code)
		if errors.Is(err, store.ErrNotFound) {
			result = notFound()
			result.Success = false
			return s.writeLog(ctx, tx, nil, code, "rebind", "fail", result.Message, newMachine, ci)
		}
		if err != nil {
			return err
		}
		// 先用统一的卡密校验拦截禁用/过期。
		res, err := s.evaluateCard(ctx, tx, card, newMachine, now, set, "rebind")
		if err != nil {
			return err
		}
		switch {
		case res.Code != CodeOK && res.Status == StatusDisabled:
			result = res
			result.Success = false
			return s.logResult(ctx, tx, card, code, "rebind", result, newMachine, ci)
		case res.Code != CodeOK && res.Status == StatusExpired:
			result = res
			result.Success = false
			return s.logResult(ctx, tx, card, code, "rebind", result, newMachine, ci)
		case res.Code != CodeOK && res.Status == StatusBoundOther:
			// 换绑场景下"绑定到别处"是常态，不视为错误，继续走换绑流程。
			card, err = tx.CardByCode(ctx, code)
			if err != nil {
				return err
			}
		}

		if card.BoundMachine == newMachine {
			result = &Result{
				Code: CodeOK, Status: StatusOK, Success: true,
				Message: "机器码未变化，无需换绑", MachineCode: newMachine,
				RebindRemaining: card.RebindRemaining(), Card: s.snapshot(card, now),
			}
			return s.logResult(ctx, tx, card, code, "rebind", result, newMachine, ci)
		}
		if card.BoundMachine == "" {
			// 尚未绑定：直接绑定，不消耗换绑次数。
			card.BoundMachine = newMachine
			card.BoundAt = now
			card.UpdatedAt = now
			if err := tx.UpdateCard(ctx, card); err != nil {
				return err
			}
			result = &Result{
				Code: CodeOK, Status: StatusOK, Success: true, Message: "绑定成功",
				MachineCode: newMachine, RebindRemaining: card.RebindRemaining(), Card: s.snapshot(card, now),
			}
			return s.logResult(ctx, tx, card, code, "rebind", result, newMachine, ci)
		}
		if card.RebindRemaining() <= 0 {
			result = &Result{
				Code: CodeConflict, Status: StatusRebindLimit, Success: false,
				Message: "换绑次数已用完，请联系客服", RebindRemaining: 0,
			}
			return s.logResult(ctx, tx, card, code, "rebind", result, newMachine, ci)
		}

		card.BoundMachine = newMachine
		card.BoundAt = now
		card.RebindCount++
		card.UpdatedAt = now
		if err := tx.UpdateCard(ctx, card); err != nil {
			return err
		}
		// 换绑后原机器必须下线。
		if _, err := tx.CloseOpenSessionsByCard(ctx, card.ID, now, "rebind"); err != nil {
			return err
		}
		if err := tx.ClearCardOnline(ctx, card.ID, now); err != nil {
			return err
		}
		card.OnlineSessionID = sql.NullInt64{}
		card.OnlineMachine = ""
		card.OnlineAt = 0

		result = &Result{
			Code: CodeOK, Status: StatusOK, Success: true, Message: "换绑成功",
			MachineCode: newMachine, RebindRemaining: card.RebindRemaining(), Card: s.snapshot(card, now),
		}
		return s.logResult(ctx, tx, card, code, "rebind", result, newMachine, ci)
	})
	return s.finish(err, result)
}

// Login 实现 POST /api/card/login（上线）。
func (s *Service) Login(ctx context.Context, code, machine string, ci ClientInfo) *Result {
	code = security.NormalizeCardCode(code)
	machine = security.NormalizeMachine(machine)
	if code == "" {
		return paramError("参数错误：缺少 code")
	}
	if machine == "" {
		return paramError("参数错误：缺少 machine_code")
	}
	set, err := s.Settings(ctx)
	if err != nil {
		s.logger.Error("读取设置失败", "error", err)
		return serverError()
	}
	if r := s.allow("login", ci.IP, code, set); r != nil {
		return r
	}
	rawToken, tokenHash, err := security.NewSessionToken()
	if err != nil {
		s.logger.Error("生成会话令牌失败", "error", err)
		return serverError()
	}

	now := s.now().Unix()
	sessionExpires := now + int64(set.SessionTimeout)
	var result *Result
	err = s.store.Write(ctx, func(tx *store.Tx) error {
		card, err := tx.CardByCode(ctx, code)
		if errors.Is(err, store.ErrNotFound) {
			result = notFound()
			return s.writeLog(ctx, tx, nil, code, "login", "fail", result.Message, machine, ci)
		}
		if err != nil {
			return err
		}
		res, err := s.evaluateCard(ctx, tx, card, machine, now, set, "login")
		if err != nil {
			return err
		}
		if res.Code != CodeOK {
			result = res
			return s.logResult(ctx, tx, card, code, "login", res, machine, ci)
		}

		logMessage := ""
		if active, err := tx.ActiveSessionByCard(ctx, card.ID, now); err == nil {
			// 注意：机器码绑定校验在 evaluateCard 里已经通过，因此能走到这里的只可能是
			// "同一台机器重复登录"（换绑会踢掉旧会话，旧会话不可能来自别的机器）。
			// allow_takeover=true（默认）时顶掉旧会话；false 时直接拒绝重复登录。
			if !set.AllowTakeover {
				result = &Result{
					Code: CodeConflict, Status: StatusOnlineOther,
					Message: "卡密已在线，禁止重复登录（可在后台设置中允许顶号）",
				}
				return s.logResult(ctx, tx, card, code, "login", result, machine, ci)
			}
			reason := "kicked"
			if active.MachineCode == machine {
				reason = "relogin"
				logMessage = "同机重复登录：旧会话已被替换"
			} else {
				logMessage = "顶号：已将其他机器会话下线"
			}
			if err := tx.CloseSession(ctx, active.ID, now, reason); err != nil {
				return err
			}
		} else if err != nil && !errors.Is(err, store.ErrNotFound) {
			return err
		}

		session := &store.Session{
			TokenHash:       tokenHash,
			CardID:          card.ID,
			MachineCode:     machine,
			IP:              ci.IP,
			UserAgent:       truncate(ci.UserAgent, 200),
			CreatedAt:       now,
			LastHeartbeatAt: now,
			ExpiresAt:       sessionExpires,
		}
		if err := tx.InsertSession(ctx, session); err != nil {
			return err
		}
		if err := tx.SetCardOnline(ctx, card.ID, session.ID, now, machine); err != nil {
			return err
		}
		card.OnlineSessionID = sql.NullInt64{Int64: session.ID, Valid: true}
		card.OnlineMachine = machine
		card.OnlineAt = now

		result = &Result{
			Code: CodeOK, Status: StatusOK, Success: true,
			Card:              s.snapshot(card, now),
			SessionToken:      rawToken,
			SessionExpiresAt:  sessionExpires,
			HeartbeatInterval: set.HeartbeatInterval,
			RebindRemaining:   card.RebindRemaining(),
		}
		if logMessage != "" {
			return s.writeLog(ctx, tx, &card.ID, code, "login", "ok", logMessage, machine, ci)
		}
		return s.writeLog(ctx, tx, &card.ID, code, "login", "ok", "", machine, ci)
	})
	return s.finish(err, result)
}

// Heartbeat 实现 POST /api/card/heartbeat。
func (s *Service) Heartbeat(ctx context.Context, token string, ci ClientInfo) *Result {
	if token == "" {
		return paramError("参数错误：缺少 session_token")
	}
	set, err := s.Settings(ctx)
	if err != nil {
		s.logger.Error("读取设置失败", "error", err)
		return serverError()
	}
	hash := security.HashToken(token)
	if !s.limiter.Allow("session:"+hash, set.RateLimitSession) {
		return rateLimited(s.limiter.RetryAfter("session:" + hash))
	}

	now := s.now().Unix()
	var result *Result
	err = s.store.Write(ctx, func(tx *store.Tx) error {
		sess, err := tx.SessionByTokenHash(ctx, hash)
		if errors.Is(err, store.ErrNotFound) {
			result = invalidSession()
			return s.writeLog(ctx, tx, nil, "", "heartbeat", "fail", result.Message, "", ci)
		}
		if err != nil {
			return err
		}
		card, err := tx.CardByID(ctx, sess.CardID)
		if errors.Is(err, store.ErrNotFound) {
			result = invalidSession()
			return s.writeLog(ctx, tx, nil, "", "heartbeat", "fail", result.Message, sess.MachineCode, ci)
		}
		if err != nil {
			return err
		}
		code := card.Code

		if sess.ClosedAt.Valid {
			switch sess.CloseReason {
			case "kicked":
				result = &Result{Code: CodeConflict, Status: StatusKicked, Message: "该卡密已在其他机器登录，本机会话已下线"}
			case "relogin":
				result = &Result{Code: CodeConflict, Status: StatusKicked, Message: "本机会话已被新的登录顶替，请重新登录"}
			case "timeout":
				result = &Result{Code: CodeUnauthorized, Status: StatusExpired, Message: "会话已超时，请重新登录"}
			case "expired":
				result = &Result{Code: CodeGone, Status: StatusExpired, Message: "卡密已过期"}
			case "disabled":
				result = &Result{Code: CodeLocked, Status: StatusDisabled, Message: "卡密已被禁用"}
			case "admin_kick":
				result = &Result{Code: CodeUnauthorized, Status: StatusKicked, Message: "会话已被管理员下线"}
			default:
				result = invalidSession()
			}
			return s.logResult(ctx, tx, card, code, "heartbeat", result, sess.MachineCode, ci)
		}
		if sess.ExpiresAt <= now {
			if err := tx.CloseSession(ctx, sess.ID, now, "timeout"); err != nil {
				return err
			}
			if err := tx.ClearCardOnline(ctx, card.ID, now); err != nil {
				return err
			}
			result = &Result{Code: CodeUnauthorized, Status: StatusExpired, Message: "会话已超时，请重新登录"}
			return s.logResult(ctx, tx, card, code, "heartbeat", result, sess.MachineCode, ci)
		}
		if card.State == "disabled" {
			if err := tx.CloseSession(ctx, sess.ID, now, "disabled"); err != nil {
				return err
			}
			if err := tx.ClearCardOnline(ctx, card.ID, now); err != nil {
				return err
			}
			result = &Result{Code: CodeLocked, Status: StatusDisabled, Message: "卡密已被禁用"}
			return s.logResult(ctx, tx, card, code, "heartbeat", result, sess.MachineCode, ci)
		}
		if card.Expired(now) {
			if err := tx.CloseSession(ctx, sess.ID, now, "expired"); err != nil {
				return err
			}
			if err := tx.ClearCardOnline(ctx, card.ID, now); err != nil {
				return err
			}
			result = &Result{Code: CodeGone, Status: StatusExpired, Message: "卡密已过期"}
			return s.logResult(ctx, tx, card, code, "heartbeat", result, sess.MachineCode, ci)
		}

		newExpiry := now + int64(set.SessionTimeout)
		if err := tx.UpdateSessionHeartbeat(ctx, sess.ID, now, newExpiry); err != nil {
			return err
		}
		if err := tx.SetCardOnline(ctx, card.ID, sess.ID, now, sess.MachineCode); err != nil {
			return err
		}
		result = &Result{
			Code: CodeOK, Status: StatusOK,
			Card:              s.snapshot(card, now),
			SessionExpiresAt:  newExpiry,
			HeartbeatInterval: set.HeartbeatInterval,
		}
		if set.LogHeartbeatSuccess {
			return s.writeLog(ctx, tx, &card.ID, code, "heartbeat", "ok", "", sess.MachineCode, ci)
		}
		return nil
	})
	return s.finish(err, result)
}

// Logout 实现 POST /api/card/logout（幂等）。
func (s *Service) Logout(ctx context.Context, token string, ci ClientInfo) *Result {
	if token == "" {
		return paramError("参数错误：缺少 session_token")
	}
	hash := security.HashToken(token)
	now := s.now().Unix()
	var result *Result
	err := s.store.Write(ctx, func(tx *store.Tx) error {
		sess, err := tx.SessionByTokenHash(ctx, hash)
		if errors.Is(err, store.ErrNotFound) {
			result = invalidSession()
			return s.writeLog(ctx, tx, nil, "", "logout", "fail", result.Message, "", ci)
		}
		if err != nil {
			return err
		}
		card, cardErr := tx.CardByID(ctx, sess.CardID)
		code := ""
		var cardID *int64
		if cardErr == nil {
			code = card.Code
			cardID = &card.ID
		}
		if sess.ClosedAt.Valid {
			result = &Result{Code: CodeOK, Status: StatusOK, Message: "会话已下线"}
			return s.writeLog(ctx, tx, cardID, code, "logout", "ok", result.Message, sess.MachineCode, ci)
		}
		if err := tx.CloseSession(ctx, sess.ID, now, "logout"); err != nil {
			return err
		}
		if cardErr == nil {
			if err := tx.ClearCardOnline(ctx, card.ID, now); err != nil {
				return err
			}
		}
		result = &Result{Code: CodeOK, Status: StatusOK}
		return s.writeLog(ctx, tx, cardID, code, "logout", "ok", "", sess.MachineCode, ci)
	})
	return s.finish(err, result)
}

// evaluateCard 是 verify/login/rebind 共用的卡密校验：禁用 → 过期 → 绑定 → 激活。
// action 取 verify | login | rebind。
func (s *Service) evaluateCard(ctx context.Context, tx *store.Tx, card *store.Card, machine string, now int64, set Settings, action string) (*Result, error) {
	if card.State == "disabled" {
		if card.ActiveOnline() {
			if _, err := tx.CloseOpenSessionsByCard(ctx, card.ID, now, "disabled"); err != nil {
				return nil, err
			}
			if err := tx.ClearCardOnline(ctx, card.ID, now); err != nil {
				return nil, err
			}
			card.OnlineSessionID = sql.NullInt64{}
			card.OnlineMachine = ""
			card.OnlineAt = 0
		}
		return &Result{Code: CodeLocked, Status: StatusDisabled, Message: "卡密已被禁用"}, nil
	}
	if card.Expired(now) {
		if card.ActiveOnline() {
			if _, err := tx.CloseOpenSessionsByCard(ctx, card.ID, now, "expired"); err != nil {
				return nil, err
			}
			if err := tx.ClearCardOnline(ctx, card.ID, now); err != nil {
				return nil, err
			}
			card.OnlineSessionID = sql.NullInt64{}
			card.OnlineMachine = ""
			card.OnlineAt = 0
		}
		return &Result{Code: CodeGone, Status: StatusExpired, Message: "卡密已过期"}, nil
	}

	changed := false
	if card.BoundMachine == "" {
		card.BoundMachine = machine
		card.BoundAt = now
		changed = true
	} else if card.BoundMachine != machine {
		return &Result{Code: CodeForbidden, Status: StatusBoundOther, Message: "卡密已绑定其他机器"}, nil
	}

	if card.State == "unused" && (action == "login" || set.ActivateOn == "verify") {
		card.State = "active"
		card.ActivatedAt = now
		if card.DurationMinutes > 0 {
			card.ExpiresAt = sql.NullInt64{Int64: now + card.DurationMinutes*60, Valid: true}
		} else {
			card.ExpiresAt = sql.NullInt64{}
		}
		changed = true
	}
	if changed {
		card.UpdatedAt = now
		if err := tx.UpdateCard(ctx, card); err != nil {
			return nil, err
		}
	}
	return &Result{Code: CodeOK, Status: StatusValid, Card: s.snapshot(card, now)}, nil
}

func (s *Service) snapshot(card *store.Card, now int64) *CardSnapshot {
	snap := &CardSnapshot{
		TypeName:        card.TypeName,
		ActivatedAt:     card.ActivatedAt,
		RebindRemaining: card.RebindRemaining(),
		Online:          card.ActiveOnline(),
	}
	if card.IsPermanent() {
		snap.Permanent = true
	} else if card.ExpiresAt.Valid && card.ExpiresAt.Int64 > 0 {
		snap.ExpiresAt = card.ExpiresAt.Int64
		if d := card.ExpiresAt.Int64 - now; d > 0 {
			snap.RemainingSeconds = d
		}
	}
	return snap
}

// allow 做 IP 与卡密两个维度的限流。
func (s *Service) allow(action, ip, code string, set Settings) *Result {
	if ip != "" && !s.limiter.Allow("ip:"+ip, set.RateLimitIP) {
		return rateLimited(s.limiter.RetryAfter("ip:" + ip))
	}
	if code != "" && !s.limiter.Allow("card:"+code, set.RateLimitCard) {
		return rateLimited(s.limiter.RetryAfter("card:" + code))
	}
	return nil
}

func (s *Service) logResult(ctx context.Context, tx *store.Tx, card *store.Card, code, action string, res *Result, machine string, ci ClientInfo) error {
	result := "ok"
	if res.Code != CodeOK {
		result = "fail"
	}
	message := res.Message
	if action == "rebind" && res.Success && message == "" {
		message = "换绑成功"
	}
	var cardID *int64
	if card != nil {
		cardID = &card.ID
	}
	return s.writeLog(ctx, tx, cardID, code, action, result, message, machine, ci)
}

func (s *Service) writeLog(ctx context.Context, tx *store.Tx, cardID *int64, code, action, result, message, machine string, ci ClientInfo) error {
	var cid sql.NullInt64
	if cardID != nil {
		cid = sql.NullInt64{Int64: *cardID, Valid: true}
	}
	return tx.InsertCardLog(ctx, &store.CardLog{
		CardID:      cid,
		CodeMasked:  security.MaskCardCode(code),
		Action:      action,
		Result:      result,
		Message:     truncate(message, 200),
		MachineCode: security.MaskMachine(machine),
		IP:          ci.IP,
		UserAgent:   truncate(ci.UserAgent, 200),
		CreatedAt:   s.now().Unix(),
	})
}

// finish 统一处理存储层错误：出错时记录日志并返回 500 包络。
func (s *Service) finish(err error, result *Result) *Result {
	if err == nil {
		if result == nil {
			return serverError()
		}
		return result
	}
	s.logger.Error("卡密接口处理失败", "error", err)
	return serverError()
}

func paramError(message string) *Result {
	return &Result{Code: CodeParam, Status: StatusInvalid, Message: message}
}

func notFound() *Result {
	return &Result{Code: CodeNotFound, Status: StatusNotFound, Message: "卡密不存在"}
}

func invalidSession() *Result {
	return &Result{Code: CodeUnauthorized, Status: StatusInvalidSession, Message: "会话无效，请重新登录"}
}

func rateLimited(retryAfter int) *Result {
	if retryAfter < 1 {
		retryAfter = 1
	}
	return &Result{Code: CodeRateLimited, Status: StatusRateLimited, Message: "请求过于频繁，请稍后再试", RetryAfter: retryAfter}
}

func serverError() *Result {
	return &Result{Code: CodeServerError, Status: StatusError, Message: "服务异常，请稍后再试"}
}
