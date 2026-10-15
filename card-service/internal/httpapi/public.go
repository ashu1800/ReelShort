package httpapi

import (
	"net/http"
	"strconv"
	"time"

	"card.hjj888.cc/cardservice/internal/httpx"
	"card.hjj888.cc/cardservice/internal/jstime"
	"card.hjj888.cc/cardservice/internal/service"
)

// envelope 是公共卡密接口的固定响应包络。
//
// 关键约定（易语言客户端决定，不可更改）：
//   - code / status / message / type / expires_at 一律为 JSON 字符串（客户端用 取文本 读取）；
//   - success 必须是 JSON 布尔（客户端用 取逻辑值 读取）；
//   - 未使用的字段返回空字符串或 0，绝不返回 null。
type envelope struct {
	Code    string `json:"code"`
	Status  string `json:"status"`
	Message string `json:"message"`

	Type        string `json:"type"`
	ExpiresAt   string `json:"expires_at"`
	ExpiresAtTs int64  `json:"expires_at_ts"`
	Permanent   bool   `json:"permanent"`

	RemainingSeconds int64  `json:"remaining_seconds"`
	ActivatedAt      string `json:"activated_at"`
	RebindRemaining  int    `json:"rebind_remaining"`
	Online           bool   `json:"online"`

	SessionToken      string `json:"session_token"`
	SessionExpiresAt  string `json:"session_expires_at"`
	HeartbeatInterval int    `json:"heartbeat_interval"`

	Success     bool   `json:"success"`
	MachineCode string `json:"machine_code"`

	ServerTime string `json:"server_time"`
}

// publicRequest 是 6 个公共接口的请求体并集（每个接口只读取自己需要的字段）。
type publicRequest struct {
	Code           string `json:"code"`
	MachineCode    string `json:"machine_code"`
	NewMachineCode string `json:"new_machine_code"`
	SessionToken   string `json:"session_token"`
}

func (s *Server) clientInfo(r *http.Request) service.ClientInfo {
	return service.ClientInfo{
		IP:        httpx.ClientIP(r.Context()),
		UserAgent: r.UserAgent(),
	}
}

func (s *Server) handleVerify(w http.ResponseWriter, r *http.Request) {
	if !requirePost(w, r) {
		return
	}
	req, ok := decodePublic(w, r)
	if !ok {
		return
	}
	writeEnvelope(w, s.svc.Verify(r.Context(), req.Code, req.MachineCode, s.clientInfo(r)))
}

func (s *Server) handleStatus(w http.ResponseWriter, r *http.Request) {
	if !requirePost(w, r) {
		return
	}
	req, ok := decodePublic(w, r)
	if !ok {
		return
	}
	writeEnvelope(w, s.svc.Status(r.Context(), req.Code, req.MachineCode, s.clientInfo(r)))
}

func (s *Server) handleRebind(w http.ResponseWriter, r *http.Request) {
	if !requirePost(w, r) {
		return
	}
	req, ok := decodePublic(w, r)
	if !ok {
		return
	}
	writeEnvelope(w, s.svc.Rebind(r.Context(), req.Code, req.NewMachineCode, s.clientInfo(r)))
}

func (s *Server) handleLogin(w http.ResponseWriter, r *http.Request) {
	if !requirePost(w, r) {
		return
	}
	req, ok := decodePublic(w, r)
	if !ok {
		return
	}
	writeEnvelope(w, s.svc.Login(r.Context(), req.Code, req.MachineCode, s.clientInfo(r)))
}

func (s *Server) handleHeartbeat(w http.ResponseWriter, r *http.Request) {
	if !requirePost(w, r) {
		return
	}
	req, ok := decodePublic(w, r)
	if !ok {
		return
	}
	writeEnvelope(w, s.svc.Heartbeat(r.Context(), req.SessionToken, s.clientInfo(r)))
}

func (s *Server) handleLogout(w http.ResponseWriter, r *http.Request) {
	if !requirePost(w, r) {
		return
	}
	req, ok := decodePublic(w, r)
	if !ok {
		return
	}
	writeEnvelope(w, s.svc.Logout(r.Context(), req.SessionToken, s.clientInfo(r)))
}

// requirePost 校验方法；不合规时返回 405 + 同一包络（客户端永远只发 POST）。
func requirePost(w http.ResponseWriter, r *http.Request) bool {
	if r.Method == http.MethodPost {
		return true
	}
	w.Header().Set("Allow", "POST")
	httpx.WriteJSON(w, http.StatusMethodNotAllowed, envelope{
		Code:       service.CodeMethodNotAllowed,
		Status:     service.StatusMethodNotAllowed,
		Message:    "该接口仅支持 POST",
		ServerTime: jstime.Format(time.Now().Unix()),
	})
	return false
}

// decodePublic 解析请求体；解析失败按业务失败返回（HTTP 200 + code 400）。
func decodePublic(w http.ResponseWriter, r *http.Request) (publicRequest, bool) {
	var req publicRequest
	if err := httpx.DecodeJSON(w, r, &req, 16<<10); err != nil {
		httpx.WriteJSON(w, http.StatusOK, envelope{
			Code:       service.CodeParam,
			Status:     service.StatusInvalid,
			Message:    "请求体必须是合法 JSON 对象",
			ServerTime: jstime.Format(time.Now().Unix()),
		})
		return req, false
	}
	return req, true
}

// writeEnvelope 把业务结果渲染为对外包络。
func writeEnvelope(w http.ResponseWriter, res *service.Result) {
	now := time.Now().Unix()
	env := envelope{
		Code:       service.CodeServerError,
		Status:     service.StatusError,
		Message:    "服务异常，请稍后再试",
		ServerTime: jstime.Format(now),
	}
	if res != nil {
		env.Code = res.Code
		env.Status = res.Status
		env.Message = res.Message
		env.Success = res.Success
		env.MachineCode = res.MachineCode
		if res.Card != nil {
			env.Type = res.Card.TypeName
			switch {
			case res.Card.Permanent:
				env.Permanent = true
				env.ExpiresAt = "永久有效"
			case res.Card.ExpiresAt > 0:
				env.ExpiresAt = jstime.Format(res.Card.ExpiresAt)
				env.ExpiresAtTs = res.Card.ExpiresAt
			}
			env.ActivatedAt = jstime.Format(res.Card.ActivatedAt)
			env.RebindRemaining = res.Card.RebindRemaining
			env.Online = res.Card.Online
			env.RemainingSeconds = res.Card.RemainingSeconds
		}
		if res.SessionToken != "" {
			env.SessionToken = res.SessionToken
		}
		if res.SessionExpiresAt > 0 {
			env.SessionExpiresAt = jstime.Format(res.SessionExpiresAt)
		}
		if res.HeartbeatInterval > 0 {
			env.HeartbeatInterval = res.HeartbeatInterval
		}
		if res.RetryAfter > 0 {
			w.Header().Set("Retry-After", strconv.Itoa(res.RetryAfter))
		}
	}
	httpx.WriteJSON(w, http.StatusOK, env)
}
