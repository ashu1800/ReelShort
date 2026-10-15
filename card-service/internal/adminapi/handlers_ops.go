package adminapi

import (
	"net/http"
	"strconv"
	"strings"
	"time"

	"card.hjj888.cc/cardservice/internal/httpx"
	"card.hjj888.cc/cardservice/internal/jstime"
	"card.hjj888.cc/cardservice/internal/service"
	"card.hjj888.cc/cardservice/internal/store"
)

func (s *Server) handleListCardTypes(w http.ResponseWriter, r *http.Request, _ *service.AdminIdentity) {
	types, err := s.svc.AdminCardTypes(r.Context())
	if err != nil {
		httpx.WriteError(w, err)
		return
	}
	items := make([]cardTypeDTO, 0, len(types))
	for _, ct := range types {
		items = append(items, toCardTypeDTO(ct))
	}
	httpx.WriteJSON(w, http.StatusOK, map[string]any{"data": items})
}

func (s *Server) handleCreateCardType(w http.ResponseWriter, r *http.Request, admin *service.AdminIdentity) {
	var body struct {
		Name            string `json:"name"`
		DurationMinutes int64  `json:"duration_minutes"`
		MaxRebinds      *int   `json:"max_rebinds"`
		Enabled         *bool  `json:"enabled"`
		SortOrder       int    `json:"sort_order"`
	}
	if err := httpx.DecodeJSON(w, r, &body, 8<<10); err != nil {
		httpx.WriteError(w, service.ValidationError("请求体格式错误"))
		return
	}
	in := service.CardTypeInput{
		Name:            body.Name,
		DurationMinutes: body.DurationMinutes,
		MaxRebinds:      3,
		Enabled:         true,
		SortOrder:       body.SortOrder,
	}
	if body.MaxRebinds != nil {
		in.MaxRebinds = *body.MaxRebinds
	}
	if body.Enabled != nil {
		in.Enabled = *body.Enabled
	}
	ct, err := s.svc.AdminCreateCardType(r.Context(), in, admin, httpx.ClientIP(r.Context()))
	if err != nil {
		httpx.WriteError(w, err)
		return
	}
	httpx.WriteJSON(w, http.StatusOK, map[string]any{"type": toCardTypeDTO(ct)})
}

func (s *Server) handleUpdateCardType(w http.ResponseWriter, r *http.Request, admin *service.AdminIdentity) {
	id, err := pathID(r)
	if err != nil {
		httpx.WriteError(w, err)
		return
	}
	var body struct {
		Name            *string `json:"name"`
		DurationMinutes *int64  `json:"duration_minutes"`
		MaxRebinds      *int    `json:"max_rebinds"`
		Enabled         *bool   `json:"enabled"`
		SortOrder       *int    `json:"sort_order"`
	}
	if err := httpx.DecodeJSON(w, r, &body, 8<<10); err != nil {
		httpx.WriteError(w, service.ValidationError("请求体格式错误"))
		return
	}
	types, err := s.svc.AdminCardTypes(r.Context())
	if err != nil {
		httpx.WriteError(w, err)
		return
	}
	var current *store.CardType
	for _, ct := range types {
		if ct.ID == id {
			current = ct
			break
		}
	}
	if current == nil {
		httpx.WriteError(w, service.NotFoundError("卡密类型不存在"))
		return
	}
	in := service.CardTypeInput{
		Name:            current.Name,
		DurationMinutes: current.DurationMinutes,
		MaxRebinds:      current.MaxRebinds,
		Enabled:         current.Enabled,
		SortOrder:       current.SortOrder,
	}
	if body.Name != nil {
		in.Name = *body.Name
	}
	if body.DurationMinutes != nil {
		in.DurationMinutes = *body.DurationMinutes
	}
	if body.MaxRebinds != nil {
		in.MaxRebinds = *body.MaxRebinds
	}
	if body.Enabled != nil {
		in.Enabled = *body.Enabled
	}
	if body.SortOrder != nil {
		in.SortOrder = *body.SortOrder
	}
	ct, err := s.svc.AdminUpdateCardType(r.Context(), id, in, admin, httpx.ClientIP(r.Context()))
	if err != nil {
		httpx.WriteError(w, err)
		return
	}
	httpx.WriteJSON(w, http.StatusOK, map[string]any{"type": toCardTypeDTO(ct)})
}

func (s *Server) handleDeleteCardType(w http.ResponseWriter, r *http.Request, admin *service.AdminIdentity) {
	id, err := pathID(r)
	if err != nil {
		httpx.WriteError(w, err)
		return
	}
	if err := s.svc.AdminDeleteCardType(r.Context(), id, admin, httpx.ClientIP(r.Context())); err != nil {
		httpx.WriteError(w, err)
		return
	}
	httpx.WriteJSON(w, http.StatusOK, map[string]any{"ok": true})
}

func (s *Server) handleListSessions(w http.ResponseWriter, r *http.Request, _ *service.AdminIdentity) {
	filter := store.SessionFilter{
		Active:   queryBool(r, "active", true),
		CardCode: queryString(r, "card_code", ""),
		Page:     queryInt(r, "page", 1),
		PageSize: queryInt(r, "page_size", 20),
	}
	now := time.Now().Unix()
	items, total, err := s.svc.AdminListSessions(r.Context(), filter)
	if err != nil {
		httpx.WriteError(w, err)
		return
	}
	out := make([]sessionDTO, 0, len(items))
	for i := range items {
		out = append(out, toSessionDTO(&items[i], now))
	}
	httpx.WriteJSON(w, http.StatusOK, map[string]any{
		"data":       out,
		"pagination": newPagination(filter.Page, filter.PageSize, total),
	})
}

func (s *Server) handleKickSession(w http.ResponseWriter, r *http.Request, admin *service.AdminIdentity) {
	id, err := pathID(r)
	if err != nil {
		httpx.WriteError(w, err)
		return
	}
	if err := s.svc.AdminKickSession(r.Context(), id, admin, httpx.ClientIP(r.Context())); err != nil {
		httpx.WriteError(w, err)
		return
	}
	httpx.WriteJSON(w, http.StatusOK, map[string]any{"ok": true})
}

func (s *Server) handleListLogs(w http.ResponseWriter, r *http.Request, _ *service.AdminIdentity) {
	from, to, err := parseDayRange(r, "from", "to")
	if err != nil {
		httpx.WriteError(w, err)
		return
	}
	filter := store.LogFilter{
		CardCode: queryString(r, "card_code", ""),
		Action:   queryString(r, "action", ""),
		Result:   queryString(r, "result", ""),
		From:     from,
		To:       to,
		Page:     queryInt(r, "page", 1),
		PageSize: queryInt(r, "page_size", 20),
	}
	logs, total, err := s.svc.AdminListLogs(r.Context(), filter)
	if err != nil {
		httpx.WriteError(w, err)
		return
	}
	items := make([]logDTO, 0, len(logs))
	for _, l := range logs {
		items = append(items, toLogDTO(l))
	}
	httpx.WriteJSON(w, http.StatusOK, map[string]any{
		"data":       items,
		"pagination": newPagination(filter.Page, filter.PageSize, total),
	})
}

func (s *Server) handleListAuditLogs(w http.ResponseWriter, r *http.Request, _ *service.AdminIdentity) {
	from, to, err := parseDayRange(r, "from", "to")
	if err != nil {
		httpx.WriteError(w, err)
		return
	}
	filter := store.AuditFilter{
		Admin:    queryString(r, "admin", ""),
		Action:   queryString(r, "action", ""),
		From:     from,
		To:       to,
		Page:     queryInt(r, "page", 1),
		PageSize: queryInt(r, "page_size", 20),
	}
	logs, total, err := s.svc.AdminListAuditLogs(r.Context(), filter)
	if err != nil {
		httpx.WriteError(w, err)
		return
	}
	items := make([]auditDTO, 0, len(logs))
	for _, l := range logs {
		items = append(items, toAuditDTO(l))
	}
	httpx.WriteJSON(w, http.StatusOK, map[string]any{
		"data":       items,
		"pagination": newPagination(filter.Page, filter.PageSize, total),
	})
}

func (s *Server) handleStats(w http.ResponseWriter, r *http.Request, _ *service.AdminIdentity) {
	stats, err := s.svc.AdminStats(r.Context())
	if err != nil {
		httpx.WriteError(w, err)
		return
	}
	trend := make([]map[string]any, 0, len(stats.Trend))
	for _, p := range stats.Trend {
		trend = append(trend, map[string]any{"date": p.Date, "activated": p.Activated})
	}
	httpx.WriteJSON(w, http.StatusOK, map[string]any{
		"cards": map[string]any{
			"total":    stats.Cards.Total,
			"unused":   stats.Cards.Unused,
			"active":   stats.Cards.Active,
			"expired":  stats.Cards.Expired,
			"disabled": stats.Cards.Disabled,
			"online":   stats.Cards.Online,
		},
		"today": map[string]any{
			"activated": stats.TodayActivated,
			"verified":  stats.TodayVerified,
			"logged_in": stats.TodayLoggedIn,
			"rebinds":   stats.TodayRebinds,
		},
		"trend":       trend,
		"server_time": jstime.Format(stats.ServerTime),
	})
}

func (s *Server) handleGetSettings(w http.ResponseWriter, r *http.Request, _ *service.AdminIdentity) {
	raw, err := s.svc.AdminSettings(r.Context())
	if err != nil {
		httpx.WriteError(w, err)
		return
	}
	httpx.WriteJSON(w, http.StatusOK, map[string]any{"settings": settingsToJSON(raw)})
}

func (s *Server) handleUpdateSettings(w http.ResponseWriter, r *http.Request, admin *service.AdminIdentity) {
	var body struct {
		HeartbeatInterval   *int    `json:"heartbeat_interval_seconds"`
		SessionTimeout      *int    `json:"session_timeout_seconds"`
		AllowTakeover       *bool   `json:"allow_takeover"`
		DefaultMaxRebinds   *int    `json:"default_max_rebinds"`
		ActivateOn          *string `json:"activate_on"`
		LogHeartbeatSuccess *bool   `json:"log_heartbeat_success"`
		RateLimitIP         *int    `json:"rate_limit_ip_per_minute"`
		RateLimitCard       *int    `json:"rate_limit_card_per_minute"`
		RateLimitSession    *int    `json:"rate_limit_session_per_minute"`
		AdminSessionHours   *int    `json:"admin_session_hours"`
	}
	if err := httpx.DecodeJSON(w, r, &body, 8<<10); err != nil {
		httpx.WriteError(w, service.ValidationError("请求体格式错误"))
		return
	}
	raw, err := s.svc.AdminUpdateSettings(r.Context(), service.SettingsInput{
		HeartbeatInterval:   body.HeartbeatInterval,
		SessionTimeout:      body.SessionTimeout,
		AllowTakeover:       body.AllowTakeover,
		DefaultMaxRebinds:   body.DefaultMaxRebinds,
		ActivateOn:          body.ActivateOn,
		LogHeartbeatSuccess: body.LogHeartbeatSuccess,
		RateLimitIP:         body.RateLimitIP,
		RateLimitCard:       body.RateLimitCard,
		RateLimitSession:    body.RateLimitSession,
		AdminSessionHours:   body.AdminSessionHours,
	}, admin, httpx.ClientIP(r.Context()))
	if err != nil {
		httpx.WriteError(w, err)
		return
	}
	httpx.WriteJSON(w, http.StatusOK, map[string]any{"settings": settingsToJSON(raw)})
}

var boolSettingKeys = map[string]bool{"allow_takeover": true, "log_heartbeat_success": true}

func settingsToJSON(raw map[string]string) map[string]any {
	out := make(map[string]any, len(service.SettingsKeys))
	for _, k := range service.SettingsKeys {
		v := strings.TrimSpace(raw[k])
		switch {
		case boolSettingKeys[k]:
			out[k] = strings.EqualFold(v, "true")
		case k == "activate_on":
			out[k] = v
		default:
			n, err := strconv.Atoi(v)
			if err != nil {
				n = 0
			}
			out[k] = n
		}
	}
	return out
}
