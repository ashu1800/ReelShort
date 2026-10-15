package adminapi

import (
	"bytes"
	"encoding/csv"
	"encoding/json"
	"fmt"
	"net/http"
	"strings"
	"time"

	"card.hjj888.cc/cardservice/internal/httpx"
	"card.hjj888.cc/cardservice/internal/jstime"
	"card.hjj888.cc/cardservice/internal/security"
	"card.hjj888.cc/cardservice/internal/service"
	"card.hjj888.cc/cardservice/internal/store"
)

var cardStates = map[string]bool{"": true, "unused": true, "active": true, "expired": true, "disabled": true, "online": true}

func buildCardFilter(r *http.Request) (store.CardFilter, error) {
	state := strings.TrimSpace(r.URL.Query().Get("state"))
	if !cardStates[state] {
		return store.CardFilter{}, service.ValidationError("状态筛选值不合法")
	}
	from, to, err := parseDayRange(r, "created_from", "created_to")
	if err != nil {
		return store.CardFilter{}, err
	}
	return store.CardFilter{
		Keyword:     queryString(r, "keyword", ""),
		State:       state,
		TypeID:      queryInt64(r, "type_id"),
		BatchNo:     queryString(r, "batch_no", ""),
		CreatedFrom: from,
		CreatedTo:   to,
		Sort:        queryString(r, "sort", "created_at_desc"),
		Page:        queryInt(r, "page", 1),
		PageSize:    queryInt(r, "page_size", 20),
	}, nil
}

func (s *Server) handleListCards(w http.ResponseWriter, r *http.Request, _ *service.AdminIdentity) {
	filter, err := buildCardFilter(r)
	if err != nil {
		httpx.WriteError(w, err)
		return
	}
	now := time.Now().Unix()
	cards, total, err := s.svc.AdminListCards(r.Context(), filter)
	if err != nil {
		httpx.WriteError(w, err)
		return
	}
	items := make([]cardDTO, 0, len(cards))
	for _, c := range cards {
		items = append(items, toCardDTO(c, now))
	}
	httpx.WriteJSON(w, http.StatusOK, map[string]any{
		"data":       items,
		"pagination": newPagination(filter.Page, filter.PageSize, total),
	})
}

func (s *Server) handleCreateCards(w http.ResponseWriter, r *http.Request, admin *service.AdminIdentity) {
	var body struct {
		TypeID     int64  `json:"type_id"`
		Count      int    `json:"count"`
		Prefix     string `json:"prefix"`
		Length     int    `json:"length"`
		MaxRebinds *int   `json:"max_rebinds"`
		BatchNo    string `json:"batch_no"`
		Remark     string `json:"remark"`
	}
	if err := httpx.DecodeJSON(w, r, &body, 64<<10); err != nil {
		httpx.WriteError(w, service.ValidationError("请求体格式错误"))
		return
	}
	if body.TypeID <= 0 {
		httpx.WriteError(w, service.ValidationError("请选择卡密类型"))
		return
	}
	res, err := s.svc.AdminCreateCards(r.Context(), service.CreateCardsInput{
		TypeID:     body.TypeID,
		Count:      body.Count,
		Prefix:     body.Prefix,
		Length:     body.Length,
		MaxRebinds: body.MaxRebinds,
		BatchNo:    body.BatchNo,
		Remark:     strings.TrimSpace(body.Remark),
	}, admin, httpx.ClientIP(r.Context()))
	if err != nil {
		httpx.WriteError(w, err)
		return
	}
	codes := make([]string, 0, len(res.Codes))
	for _, c := range res.Codes {
		codes = append(codes, security.FormatCardCode(c))
	}
	httpx.WriteJSON(w, http.StatusOK, map[string]any{
		"codes":    codes,
		"batch_no": res.BatchNo,
		"count":    len(codes),
	})
}

func (s *Server) handleExportCards(w http.ResponseWriter, r *http.Request, _ *service.AdminIdentity) {
	filter, err := buildCardFilter(r)
	if err != nil {
		httpx.WriteError(w, err)
		return
	}
	format := strings.ToLower(queryString(r, "format", "csv"))
	if format != "csv" && format != "txt" {
		httpx.WriteError(w, service.ValidationError("format 只能是 csv 或 txt"))
		return
	}
	cards, err := s.svc.AdminExportCards(r.Context(), filter)
	if err != nil {
		httpx.WriteError(w, err)
		return
	}
	now := time.Now().Unix()
	stamp := time.Now().In(jstime.Location()).Format("20060102-150405")

	if format == "txt" {
		var buf bytes.Buffer
		buf.WriteString("\xEF\xBB\xBF")
		for _, c := range cards {
			buf.WriteString(security.FormatCardCode(c.Code))
			buf.WriteString("\r\n")
		}
		w.Header().Set("Content-Type", "text/plain; charset=utf-8")
		w.Header().Set("Content-Disposition", fmt.Sprintf(`attachment; filename="cards-%s.txt"`, stamp))
		w.Header().Set("Cache-Control", "no-store")
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write(buf.Bytes())
		return
	}

	var buf bytes.Buffer
	buf.WriteString("\xEF\xBB\xBF") // UTF-8 BOM，便于 Excel 直接打开
	cw := csv.NewWriter(&buf)
	_ = cw.Write([]string{"卡密", "类型", "状态", "绑定机器码", "激活时间", "到期时间", "换绑次数", "批次", "备注", "创建时间"})
	for _, c := range cards {
		expires := "永久有效"
		if c.ExpiresAt.Valid && c.ExpiresAt.Int64 > 0 {
			expires = jstime.Format(c.ExpiresAt.Int64)
		} else if !c.IsPermanent() {
			expires = "未激活"
		}
		_ = cw.Write([]string{
			security.FormatCardCode(c.Code),
			c.TypeName,
			displayStateText(c.DisplayState(now)),
			c.BoundMachine,
			jstime.Format(c.ActivatedAt),
			expires,
			fmt.Sprintf("%d/%d", c.RebindCount, c.MaxRebinds),
			c.BatchNo,
			c.Remark,
			jstime.Format(c.CreatedAt),
		})
	}
	cw.Flush()
	w.Header().Set("Content-Type", "text/csv; charset=utf-8")
	w.Header().Set("Content-Disposition", fmt.Sprintf(`attachment; filename="cards-%s.csv"`, stamp))
	w.Header().Set("Cache-Control", "no-store")
	w.WriteHeader(http.StatusOK)
	_, _ = w.Write(buf.Bytes())
}

func displayStateText(state string) string {
	switch state {
	case "unused":
		return "未激活"
	case "active":
		return "正常"
	case "expired":
		return "已过期"
	case "disabled":
		return "已禁用"
	default:
		return state
	}
}

func (s *Server) handleCardDetail(w http.ResponseWriter, r *http.Request, _ *service.AdminIdentity) {
	id, err := pathID(r)
	if err != nil {
		httpx.WriteError(w, err)
		return
	}
	card, sessions, logs, err := s.svc.AdminCardDetail(r.Context(), id)
	if err != nil {
		httpx.WriteError(w, err)
		return
	}
	now := time.Now().Unix()
	sessionItems := make([]sessionDTO, 0, len(sessions))
	for _, sess := range sessions {
		sessionItems = append(sessionItems, toSessionOnlyDTO(sess, now))
	}
	logItems := make([]logDTO, 0, len(logs))
	for _, l := range logs {
		logItems = append(logItems, toLogDTO(l))
	}
	httpx.WriteJSON(w, http.StatusOK, map[string]any{
		"card":     toCardDTO(card, now),
		"sessions": sessionItems,
		"logs":     logItems,
	})
}

func (s *Server) handleUpdateCard(w http.ResponseWriter, r *http.Request, admin *service.AdminIdentity) {
	id, err := pathID(r)
	if err != nil {
		httpx.WriteError(w, err)
		return
	}
	var body struct {
		Remark     *string `json:"remark"`
		MaxRebinds *int    `json:"max_rebinds"`
		ExpiresAt  *string `json:"expires_at"`
	}
	if err := httpx.DecodeJSON(w, r, &body, 8<<10); err != nil {
		httpx.WriteError(w, service.ValidationError("请求体格式错误"))
		return
	}
	card, err := s.svc.AdminUpdateCard(r.Context(), id, service.UpdateCardInput{
		Remark:     body.Remark,
		MaxRebinds: body.MaxRebinds,
		ExpiresAt:  body.ExpiresAt,
	}, admin, httpx.ClientIP(r.Context()))
	if err != nil {
		httpx.WriteError(w, err)
		return
	}
	httpx.WriteJSON(w, http.StatusOK, map[string]any{"card": toCardDTO(card, time.Now().Unix())})
}

func (s *Server) handleDeleteCard(w http.ResponseWriter, r *http.Request, admin *service.AdminIdentity) {
	id, err := pathID(r)
	if err != nil {
		httpx.WriteError(w, err)
		return
	}
	if err := s.svc.AdminDeleteCard(r.Context(), id, admin, httpx.ClientIP(r.Context())); err != nil {
		httpx.WriteError(w, err)
		return
	}
	httpx.WriteJSON(w, http.StatusOK, map[string]any{"ok": true})
}

// parseExtendMinutes 解析 {days} 或 {minutes}。
func parseExtendMinutes(body []byte) (int64, error) {
	var payload struct {
		Days    int64 `json:"days"`
		Minutes int64 `json:"minutes"`
	}
	if len(bytes.TrimSpace(body)) == 0 {
		return 0, service.ValidationError("请提供 days 或 minutes")
	}
	if err := json.Unmarshal(body, &payload); err != nil {
		return 0, service.ValidationError("请求体格式错误")
	}
	switch {
	case payload.Days > 0:
		return payload.Days * 1440, nil
	case payload.Minutes > 0:
		return payload.Minutes, nil
	default:
		return 0, service.ValidationError("延期时长必须为正数")
	}
}

func (s *Server) cardAction(action string) http.HandlerFunc {
	return s.auth(func(w http.ResponseWriter, r *http.Request, admin *service.AdminIdentity) {
		id, err := pathID(r)
		if err != nil {
			httpx.WriteError(w, err)
			return
		}
		var minutes int64
		if action == "extend" {
			raw, err := readBody(r, 8<<10)
			if err != nil {
				httpx.WriteError(w, service.ValidationError("请求体读取失败"))
				return
			}
			minutes, err = parseExtendMinutes(raw)
			if err != nil {
				httpx.WriteError(w, err)
				return
			}
		}
		card, err := s.svc.AdminCardAction(r.Context(), id, action, minutes, admin, httpx.ClientIP(r.Context()))
		if err != nil {
			httpx.WriteError(w, err)
			return
		}
		httpx.WriteJSON(w, http.StatusOK, map[string]any{"card": toCardDTO(card, time.Now().Unix())})
	}, authOptions{})
}

func (s *Server) handleBatchAction(w http.ResponseWriter, r *http.Request, admin *service.AdminIdentity) {
	var body struct {
		IDs     []int64 `json:"ids"`
		Action  string  `json:"action"`
		Days    int64   `json:"days"`
		Minutes int64   `json:"minutes"`
	}
	if err := httpx.DecodeJSON(w, r, &body, 256<<10); err != nil {
		httpx.WriteError(w, service.ValidationError("请求体格式错误"))
		return
	}
	minutes := body.Minutes
	if body.Days > 0 {
		minutes = body.Days * 1440
	}
	affected, err := s.svc.AdminBatchAction(r.Context(), body.IDs, strings.TrimSpace(body.Action), minutes, admin, httpx.ClientIP(r.Context()))
	if err != nil {
		httpx.WriteError(w, err)
		return
	}
	httpx.WriteJSON(w, http.StatusOK, map[string]any{"affected": affected})
}
