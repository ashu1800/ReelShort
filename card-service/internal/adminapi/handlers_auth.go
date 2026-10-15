package adminapi

import (
	"net/http"

	"card.hjj888.cc/cardservice/internal/httpx"
	"card.hjj888.cc/cardservice/internal/jstime"
	"card.hjj888.cc/cardservice/internal/service"
)

func (s *Server) handleLogin(w http.ResponseWriter, r *http.Request) {
	var body struct {
		Username string `json:"username"`
		Password string `json:"password"`
	}
	if err := httpx.DecodeJSON(w, r, &body, 8<<10); err != nil {
		httpx.WriteError(w, service.ValidationError("请求体格式错误"))
		return
	}
	if err := checkCSRF(r); err != nil {
		httpx.WriteError(w, err)
		return
	}
	identity, token, expiresAt, err := s.svc.AdminLogin(r.Context(), body.Username, body.Password, clientInfo(r))
	if err != nil {
		httpx.WriteError(w, err)
		return
	}
	profile, err := s.svc.AdminProfile(r.Context(), identity.ID)
	if err != nil {
		httpx.WriteError(w, err)
		return
	}
	s.setSessionCookie(w, token, expiresAt)
	httpx.WriteJSON(w, http.StatusOK, map[string]any{
		"admin":      toAdminDTO(profile),
		"expires_at": jstime.Format(expiresAt),
	})
}

func (s *Server) handleLogout(w http.ResponseWriter, r *http.Request, admin *service.AdminIdentity) {
	cookie, _ := r.Cookie(CookieName)
	token := ""
	if cookie != nil {
		token = cookie.Value
	}
	if err := s.svc.AdminLogout(r.Context(), token, admin, clientInfo(r)); err != nil {
		httpx.WriteError(w, err)
		return
	}
	s.clearSessionCookie(w)
	httpx.WriteJSON(w, http.StatusOK, map[string]any{"ok": true})
}

func (s *Server) handleMe(w http.ResponseWriter, r *http.Request, admin *service.AdminIdentity) {
	profile, err := s.svc.AdminProfile(r.Context(), admin.ID)
	if err != nil {
		httpx.WriteError(w, err)
		return
	}
	httpx.WriteJSON(w, http.StatusOK, map[string]any{"admin": toAdminDTO(profile)})
}

func (s *Server) handleChangePassword(w http.ResponseWriter, r *http.Request, admin *service.AdminIdentity) {
	var body struct {
		OldPassword string `json:"old_password"`
		NewPassword string `json:"new_password"`
	}
	if err := httpx.DecodeJSON(w, r, &body, 8<<10); err != nil {
		httpx.WriteError(w, service.ValidationError("请求体格式错误"))
		return
	}
	if err := s.svc.AdminChangePassword(r.Context(), admin, body.OldPassword, body.NewPassword, clientInfo(r)); err != nil {
		httpx.WriteError(w, err)
		return
	}
	s.clearSessionCookie(w)
	httpx.WriteJSON(w, http.StatusOK, map[string]any{
		"ok":      true,
		"message": "密码已修改，请重新登录",
	})
}
