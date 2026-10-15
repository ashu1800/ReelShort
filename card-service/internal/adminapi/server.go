// Package adminapi 实现管理后台接口（Cookie 会话 + CSRF 校验 + 标准 HTTP 语义）。
package adminapi

import (
	"io"
	"log/slog"
	"net/http"
	"strconv"
	"strings"
	"time"

	"card.hjj888.cc/cardservice/internal/config"
	"card.hjj888.cc/cardservice/internal/httpx"
	"card.hjj888.cc/cardservice/internal/jstime"
	"card.hjj888.cc/cardservice/internal/service"
	"card.hjj888.cc/cardservice/internal/store"
)

// CookieName 是后台会话 Cookie 名称。
const CookieName = "card_admin_session"

// CSRFHeader/CSRFValue 是写操作必须携带的请求头（配合 SameSite=Strict Cookie）。
const (
	CSRFHeader = "X-Requested-With"
	CSRFValue  = "card-admin"
)

// Server 汇总后台处理器。
type Server struct {
	svc    *service.Service
	cfg    *config.Config
	logger *slog.Logger
}

// NewServer 创建后台服务。
func NewServer(svc *service.Service, cfg *config.Config, logger *slog.Logger) *Server {
	if logger == nil {
		logger = slog.Default()
	}
	return &Server{svc: svc, cfg: cfg, logger: logger}
}

// Register 注册全部后台路由。
func (s *Server) Register(mux *http.ServeMux) {
	mux.HandleFunc("POST /api/admin/auth/login", s.handleLogin)
	mux.HandleFunc("POST /api/admin/auth/logout", s.auth(s.handleLogout, authOptions{allowPasswordChange: true}))
	mux.HandleFunc("GET /api/admin/auth/me", s.auth(s.handleMe, authOptions{allowPasswordChange: true}))
	mux.HandleFunc("PUT /api/admin/auth/password", s.auth(s.handleChangePassword, authOptions{allowPasswordChange: true}))

	mux.HandleFunc("GET /api/admin/cards", s.auth(s.handleListCards, authOptions{}))
	mux.HandleFunc("POST /api/admin/cards", s.auth(s.handleCreateCards, authOptions{}))
	mux.HandleFunc("POST /api/admin/cards/batch", s.auth(s.handleCreateCards, authOptions{}))
	mux.HandleFunc("GET /api/admin/cards/export", s.auth(s.handleExportCards, authOptions{}))
	mux.HandleFunc("POST /api/admin/cards/batch_action", s.auth(s.handleBatchAction, authOptions{}))
	mux.HandleFunc("GET /api/admin/cards/{id}", s.auth(s.handleCardDetail, authOptions{}))
	mux.HandleFunc("PATCH /api/admin/cards/{id}", s.auth(s.handleUpdateCard, authOptions{}))
	mux.HandleFunc("DELETE /api/admin/cards/{id}", s.auth(s.handleDeleteCard, authOptions{}))
	mux.HandleFunc("POST /api/admin/cards/{id}/disable", s.cardAction("disable"))
	mux.HandleFunc("POST /api/admin/cards/{id}/enable", s.cardAction("enable"))
	mux.HandleFunc("POST /api/admin/cards/{id}/unbind", s.cardAction("unbind"))
	mux.HandleFunc("POST /api/admin/cards/{id}/reset_rebinds", s.cardAction("reset_rebinds"))
	mux.HandleFunc("POST /api/admin/cards/{id}/extend", s.cardAction("extend"))

	mux.HandleFunc("GET /api/admin/card_types", s.auth(s.handleListCardTypes, authOptions{}))
	mux.HandleFunc("POST /api/admin/card_types", s.auth(s.handleCreateCardType, authOptions{}))
	mux.HandleFunc("PATCH /api/admin/card_types/{id}", s.auth(s.handleUpdateCardType, authOptions{}))
	mux.HandleFunc("DELETE /api/admin/card_types/{id}", s.auth(s.handleDeleteCardType, authOptions{}))

	mux.HandleFunc("GET /api/admin/sessions", s.auth(s.handleListSessions, authOptions{}))
	mux.HandleFunc("POST /api/admin/sessions/{id}/kick", s.auth(s.handleKickSession, authOptions{}))
	mux.HandleFunc("GET /api/admin/logs", s.auth(s.handleListLogs, authOptions{}))
	mux.HandleFunc("GET /api/admin/audit_logs", s.auth(s.handleListAuditLogs, authOptions{}))
	mux.HandleFunc("GET /api/admin/stats", s.auth(s.handleStats, authOptions{}))
	mux.HandleFunc("GET /api/admin/settings", s.auth(s.handleGetSettings, authOptions{}))
	mux.HandleFunc("PUT /api/admin/settings", s.auth(s.handleUpdateSettings, authOptions{}))
}

type authedHandler func(w http.ResponseWriter, r *http.Request, admin *service.AdminIdentity)

type authOptions struct {
	// allowPasswordChange 为 true 时允许"必须改密"状态访问（改密、退出、查看自身）。
	allowPasswordChange bool
}

func (s *Server) auth(h authedHandler, opts authOptions) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		admin, err := s.authenticate(r)
		if err != nil {
			httpx.WriteError(w, err)
			return
		}
		if !opts.allowPasswordChange && admin.MustChangePassword {
			httpx.WriteErrorCode(w, http.StatusForbidden, service.ErrCodePasswordChange, "首次登录请先修改初始密码")
			return
		}
		if isMutating(r.Method) {
			if err := checkCSRF(r); err != nil {
				httpx.WriteError(w, err)
				return
			}
		}
		h(w, r, admin)
	}
}

func (s *Server) authenticate(r *http.Request) (*service.AdminIdentity, error) {
	cookie, err := r.Cookie(CookieName)
	if err != nil || cookie.Value == "" {
		return nil, service.NewError(service.ErrCodeUnauthorized, "未登录或登录已过期")
	}
	return s.svc.AdminAuthenticate(r.Context(), cookie.Value)
}

func isMutating(method string) bool {
	switch method {
	case http.MethodPost, http.MethodPut, http.MethodPatch, http.MethodDelete:
		return true
	default:
		return false
	}
}

func checkCSRF(r *http.Request) error {
	if !strings.EqualFold(strings.TrimSpace(r.Header.Get(CSRFHeader)), CSRFValue) {
		return service.NewError(service.ErrCodeForbidden, "缺少 "+CSRFHeader+" 请求头")
	}
	origin := strings.TrimSpace(r.Header.Get("Origin"))
	if origin == "" {
		return nil
	}
	if idx := strings.Index(origin, "://"); idx >= 0 {
		host := origin[idx+3:]
		if host != r.Host {
			return service.NewError(service.ErrCodeForbidden, "来源校验失败")
		}
	}
	return nil
}

func (s *Server) setSessionCookie(w http.ResponseWriter, token string, expiresAt int64) {
	maxAge := int(expiresAt - time.Now().Unix())
	if maxAge < 1 {
		maxAge = 1
	}
	http.SetCookie(w, &http.Cookie{
		Name:     CookieName,
		Value:    token,
		Path:     "/",
		HttpOnly: true,
		Secure:   s.cfg.CookieSecure,
		SameSite: http.SameSiteStrictMode,
		Expires:  time.Unix(expiresAt, 0),
		MaxAge:   maxAge,
	})
}

func (s *Server) clearSessionCookie(w http.ResponseWriter) {
	http.SetCookie(w, &http.Cookie{
		Name:     CookieName,
		Value:    "",
		Path:     "/",
		HttpOnly: true,
		Secure:   s.cfg.CookieSecure,
		SameSite: http.SameSiteStrictMode,
		MaxAge:   -1,
		Expires:  time.Unix(0, 0),
	})
}

func clientInfo(r *http.Request) service.ClientInfo {
	return service.ClientInfo{IP: httpx.ClientIP(r.Context()), UserAgent: r.UserAgent()}
}

// ---------- 查询参数解析 ----------

func queryString(r *http.Request, key, def string) string {
	if v := strings.TrimSpace(r.URL.Query().Get(key)); v != "" {
		return v
	}
	return def
}

func queryInt(r *http.Request, key string, def int) int {
	raw := strings.TrimSpace(r.URL.Query().Get(key))
	if raw == "" {
		return def
	}
	n, err := strconv.Atoi(raw)
	if err != nil {
		return def
	}
	return n
}

func queryInt64(r *http.Request, key string) int64 {
	raw := strings.TrimSpace(r.URL.Query().Get(key))
	if raw == "" {
		return 0
	}
	n, err := strconv.ParseInt(raw, 10, 64)
	if err != nil {
		return 0
	}
	return n
}

func queryBool(r *http.Request, key string, def bool) bool {
	raw := strings.ToLower(strings.TrimSpace(r.URL.Query().Get(key)))
	switch raw {
	case "true", "1", "yes":
		return true
	case "false", "0", "no":
		return false
	default:
		return def
	}
}

// parseDayRange 把日期区间参数（`YYYY-MM-DD` 或 `YYYY-MM-DD HH:MM:SS`）转换为 unix 秒区间（左闭右开）。
// 纯日期上界按"当天 24:00"处理，带时间的上界按秒级包含处理（前端日志页发送 `23:59:59`）。
func parseDayRange(r *http.Request, fromKey, toKey string) (int64, int64, error) {
	var from, to int64
	if raw := strings.TrimSpace(r.URL.Query().Get(fromKey)); raw != "" {
		t, _, err := jstime.ParseFlexible(raw)
		if err != nil {
			return 0, 0, service.ValidationError("%s 时间格式应为 YYYY-MM-DD 或 YYYY-MM-DD HH:MM:SS", fromKey)
		}
		from = t.Unix()
	}
	if raw := strings.TrimSpace(r.URL.Query().Get(toKey)); raw != "" {
		t, isDate, err := jstime.ParseFlexible(raw)
		if err != nil {
			return 0, 0, service.ValidationError("%s 时间格式应为 YYYY-MM-DD 或 YYYY-MM-DD HH:MM:SS", toKey)
		}
		if isDate {
			to = t.AddDate(0, 0, 1).Unix()
		} else {
			to = t.Unix() + 1
		}
	}
	return from, to, nil
}

func pathID(r *http.Request) (int64, error) {
	raw := r.PathValue("id")
	id, err := strconv.ParseInt(raw, 10, 64)
	if err != nil || id <= 0 {
		return 0, service.ValidationError("路径 ID 不合法")
	}
	return id, nil
}

// readBody 读取请求体（带体积上限）。
func readBody(r *http.Request, maxBytes int64) ([]byte, error) {
	if maxBytes <= 0 {
		maxBytes = 1 << 20
	}
	return io.ReadAll(io.LimitReader(r.Body, maxBytes))
}

// ---------- DTO ----------

type adminDTO struct {
	ID                 int64  `json:"id"`
	Username           string `json:"username"`
	DisplayName        string `json:"display_name"`
	MustChangePassword bool   `json:"must_change_password"`
	LastLoginAt        string `json:"last_login_at"`
	CreatedAt          string `json:"created_at"`
}

func toAdminDTO(a *store.Admin) adminDTO {
	return adminDTO{
		ID:                 a.ID,
		Username:           a.Username,
		DisplayName:        a.DisplayName,
		MustChangePassword: a.MustChangePassword,
		LastLoginAt:        jstime.Format(a.LastLoginAt),
		CreatedAt:          jstime.Format(a.CreatedAt),
	}
}

type paginationDTO struct {
	Page       int `json:"page"`
	PageSize   int `json:"page_size"`
	TotalItems int `json:"total_items"`
	TotalPages int `json:"total_pages"`
}

func newPagination(page, size, total int) paginationDTO {
	if page < 1 {
		page = 1
	}
	if size < 1 {
		size = 20
	}
	pages := 0
	if total > 0 {
		pages = (total + size - 1) / size
	}
	return paginationDTO{Page: page, PageSize: size, TotalItems: total, TotalPages: pages}
}

type cardDTO struct {
	ID               int64  `json:"id"`
	Code             string `json:"code"`
	TypeID           int64  `json:"type_id"`
	TypeName         string `json:"type_name"`
	DurationMinutes  int64  `json:"duration_minutes"`
	State            string `json:"state"`
	DisplayState     string `json:"display_state"`
	MaxRebinds       int    `json:"max_rebinds"`
	RebindCount      int    `json:"rebind_count"`
	RebindRemaining  int    `json:"rebind_remaining"`
	BoundMachine     string `json:"bound_machine"`
	BoundAt          string `json:"bound_at"`
	ActivatedAt      string `json:"activated_at"`
	ExpiresAt        string `json:"expires_at"`
	Permanent        bool   `json:"permanent"`
	RemainingSeconds int64  `json:"remaining_seconds"`
	Online           bool   `json:"online"`
	OnlineMachine    string `json:"online_machine"`
	OnlineAt         string `json:"online_at"`
	BatchNo          string `json:"batch_no"`
	Remark           string `json:"remark"`
	CreatedBy        string `json:"created_by"`
	CreatedAt        string `json:"created_at"`
	UpdatedAt        string `json:"updated_at"`
}

func toCardDTO(c *store.Card, now int64) cardDTO {
	dto := cardDTO{
		ID:              c.ID,
		Code:            c.Code,
		TypeName:        c.TypeName,
		DurationMinutes: c.DurationMinutes,
		State:           c.State,
		DisplayState:    c.DisplayState(now),
		MaxRebinds:      c.MaxRebinds,
		RebindCount:     c.RebindCount,
		RebindRemaining: c.RebindRemaining(),
		BoundMachine:    c.BoundMachine,
		BoundAt:         jstime.Format(c.BoundAt),
		ActivatedAt:     jstime.Format(c.ActivatedAt),
		Permanent:       c.IsPermanent(),
		Online:          c.ActiveOnline(),
		OnlineMachine:   c.OnlineMachine,
		OnlineAt:        jstime.Format(c.OnlineAt),
		BatchNo:         c.BatchNo,
		Remark:          c.Remark,
		CreatedBy:       c.CreatedBy,
		CreatedAt:       jstime.Format(c.CreatedAt),
		UpdatedAt:       jstime.Format(c.UpdatedAt),
	}
	if c.TypeID.Valid {
		dto.TypeID = c.TypeID.Int64
	}
	if c.ExpiresAt.Valid && c.ExpiresAt.Int64 > 0 {
		dto.ExpiresAt = jstime.Format(c.ExpiresAt.Int64)
		if d := c.ExpiresAt.Int64 - now; d > 0 {
			dto.RemainingSeconds = d
		}
	}
	return dto
}

type sessionDTO struct {
	ID              int64  `json:"id"`
	CardID          int64  `json:"card_id"`
	CardCode        string `json:"card_code"`
	MachineCode     string `json:"machine_code"`
	IP              string `json:"ip"`
	UserAgent       string `json:"user_agent"`
	CreatedAt       string `json:"created_at"`
	LastHeartbeatAt string `json:"last_heartbeat_at"`
	ExpiresAt       string `json:"expires_at"`
	ClosedAt        string `json:"closed_at"`
	CloseReason     string `json:"close_reason"`
	Active          bool   `json:"active"`
}

func toSessionDTO(sv *store.SessionView, now int64) sessionDTO {
	s := sv.Session
	dto := sessionDTO{
		ID:              s.ID,
		CardID:          s.CardID,
		CardCode:        sv.CardCode,
		MachineCode:     s.MachineCode,
		IP:              s.IP,
		UserAgent:       s.UserAgent,
		CreatedAt:       jstime.Format(s.CreatedAt),
		LastHeartbeatAt: jstime.Format(s.LastHeartbeatAt),
		ExpiresAt:       jstime.Format(s.ExpiresAt),
		ClosedAt:        jstime.Format(s.ClosedAt.Int64),
		CloseReason:     s.CloseReason,
		Active:          s.Open(now),
	}
	return dto
}

func toSessionOnlyDTO(s *store.Session, now int64) sessionDTO {
	return toSessionDTO(&store.SessionView{Session: *s}, now)
}

type logDTO struct {
	ID          int64  `json:"id"`
	CardID      int64  `json:"card_id"`
	CodeMasked  string `json:"code_masked"`
	Action      string `json:"action"`
	Result      string `json:"result"`
	Message     string `json:"message"`
	MachineCode string `json:"machine_code"`
	IP          string `json:"ip"`
	CreatedAt   string `json:"created_at"`
}

func toLogDTO(l *store.CardLog) logDTO {
	dto := logDTO{
		ID:          l.ID,
		CodeMasked:  l.CodeMasked,
		Action:      l.Action,
		Result:      l.Result,
		Message:     l.Message,
		MachineCode: l.MachineCode,
		IP:          l.IP,
		CreatedAt:   jstime.Format(l.CreatedAt),
	}
	if l.CardID.Valid {
		dto.CardID = l.CardID.Int64
	}
	return dto
}

type auditDTO struct {
	ID         int64  `json:"id"`
	Username   string `json:"username"`
	Action     string `json:"action"`
	TargetType string `json:"target_type"`
	TargetID   string `json:"target_id"`
	Detail     string `json:"detail"`
	IP         string `json:"ip"`
	CreatedAt  string `json:"created_at"`
}

func toAuditDTO(a *store.AuditLog) auditDTO {
	return auditDTO{
		ID:         a.ID,
		Username:   a.Username,
		Action:     a.Action,
		TargetType: a.TargetType,
		TargetID:   a.TargetID,
		Detail:     a.Detail,
		IP:         a.IP,
		CreatedAt:  jstime.Format(a.CreatedAt),
	}
}

type cardTypeDTO struct {
	ID              int64  `json:"id"`
	Name            string `json:"name"`
	DurationMinutes int64  `json:"duration_minutes"`
	DurationText    string `json:"duration_text"`
	MaxRebinds      int    `json:"max_rebinds"`
	Enabled         bool   `json:"enabled"`
	SortOrder       int    `json:"sort_order"`
	CardCount       int    `json:"card_count"`
	CreatedAt       string `json:"created_at"`
}

func toCardTypeDTO(ct *store.CardType) cardTypeDTO {
	return cardTypeDTO{
		ID:              ct.ID,
		Name:            ct.Name,
		DurationMinutes: ct.DurationMinutes,
		DurationText:    durationText(ct.DurationMinutes),
		MaxRebinds:      ct.MaxRebinds,
		Enabled:         ct.Enabled,
		SortOrder:       ct.SortOrder,
		CardCount:       ct.CardCount,
		CreatedAt:       jstime.Format(ct.CreatedAt),
	}
}

func durationText(minutes int64) string {
	switch {
	case minutes <= 0:
		return "永久"
	case minutes%1440 == 0:
		return strconv.FormatInt(minutes/1440, 10) + " 天"
	case minutes%60 == 0:
		return strconv.FormatInt(minutes/60, 10) + " 小时"
	default:
		return strconv.FormatInt(minutes, 10) + " 分钟"
	}
}
