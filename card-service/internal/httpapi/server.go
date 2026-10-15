// Package httpapi 组装对外 HTTP 服务：公共卡密接口、后台接口、健康检查与管理后台静态资源。
package httpapi

import (
	"context"
	"io/fs"
	"log/slog"
	"net/http"
	"strings"
	"time"

	"card.hjj888.cc/cardservice"
	"card.hjj888.cc/cardservice/internal/adminapi"
	"card.hjj888.cc/cardservice/internal/config"
	"card.hjj888.cc/cardservice/internal/httpx"
	"card.hjj888.cc/cardservice/internal/jstime"
	"card.hjj888.cc/cardservice/internal/service"
	"card.hjj888.cc/cardservice/internal/version"
)

// Server 汇总全部 HTTP 处理器。
type Server struct {
	svc       *service.Service
	cfg       *config.Config
	logger    *slog.Logger
	admin     *adminapi.Server
	webFS     fs.FS
	startedAt time.Time
}

// NewServer 创建 HTTP 服务。
func NewServer(svc *service.Service, cfg *config.Config, logger *slog.Logger) *Server {
	if logger == nil {
		logger = slog.Default()
	}
	s := &Server{
		svc:       svc,
		cfg:       cfg,
		logger:    logger,
		webFS:     cardservice.WebFS(),
		startedAt: time.Now(),
	}
	s.admin = adminapi.NewServer(svc, cfg, logger)
	return s
}

// Handler 返回带中间件的完整路由。
func (s *Server) Handler() http.Handler {
	mux := http.NewServeMux()

	// 公共卡密接口（易语言客户端契约，全部 POST）。
	mux.HandleFunc("/api/verify", s.handleVerify)
	mux.HandleFunc("/api/card/status", s.handleStatus)
	mux.HandleFunc("/api/card/rebind", s.handleRebind)
	mux.HandleFunc("/api/card/login", s.handleLogin)
	mux.HandleFunc("/api/card/heartbeat", s.handleHeartbeat)
	mux.HandleFunc("/api/card/logout", s.handleLogout)

	// 运维探活。
	mux.HandleFunc("/healthz", s.handleHealth)

	// 管理后台接口。
	s.admin.Register(mux)

	// 管理后台 SPA。
	mux.HandleFunc("/", s.handleSPA)

	return s.withMiddleware(mux)
}

type statusRecorder struct {
	http.ResponseWriter
	status      int
	bytes       int
	wroteHeader bool
}

func (r *statusRecorder) WriteHeader(code int) {
	if !r.wroteHeader {
		r.status = code
		r.wroteHeader = true
	}
	r.ResponseWriter.WriteHeader(code)
}

func (r *statusRecorder) Write(b []byte) (int, error) {
	if !r.wroteHeader {
		r.wroteHeader = true
	}
	n, err := r.ResponseWriter.Write(b)
	r.bytes += n
	return n, err
}

func (s *Server) withMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		start := time.Now()
		requestID := httpx.NewRequestID()
		ctx := httpx.WithRequestID(r.Context(), requestID)
		ip := httpx.RemoteIP(r, s.cfg.IsTrustedProxy)
		ctx = httpx.WithClientIP(ctx, ip)
		r = r.WithContext(ctx)
		w.Header().Set("X-Request-Id", requestID)

		rec := &statusRecorder{ResponseWriter: w, status: http.StatusOK}

		defer func() {
			if p := recover(); p != nil {
				s.logger.Error("请求处理发生 panic",
					"panic", p, "method", r.Method, "path", r.URL.Path, "request_id", requestID)
				if !rec.wroteHeader {
					httpx.WriteJSON(w, http.StatusInternalServerError, map[string]any{
						"code": service.CodeServerError, "status": service.StatusError,
						"message": "服务异常，请稍后再试",
					})
				}
			}
			if !s.cfg.AccessLog || r.URL.Path == "/healthz" {
				return
			}
			s.logger.Info("http",
				"method", r.Method,
				"path", r.URL.Path,
				"status", rec.status,
				"bytes", rec.bytes,
				"ip", ip,
				"duration_ms", time.Since(start).Milliseconds(),
				"request_id", requestID,
			)
		}()

		next.ServeHTTP(rec, r)
	})
}

// handleHealth 是 nginx/systemd 探活接口。
func (s *Server) handleHealth(w http.ResponseWriter, r *http.Request) {
	ctx, cancel := context.WithTimeout(r.Context(), 3*time.Second)
	defer cancel()
	now := time.Now()
	dbErr := s.svc.Store().Ping(ctx)
	online := 0
	if dbErr == nil {
		if n, err := s.svc.Store().Snapshot().CountOpenSessions(ctx, now.Unix()); err == nil {
			online = n
		}
	}
	status := "ok"
	db := "ok"
	code := http.StatusOK
	if dbErr != nil {
		status = "degraded"
		db = "error"
		code = http.StatusServiceUnavailable
	}
	httpx.WriteJSON(w, code, map[string]any{
		"status":          status,
		"version":         version.Version,
		"db":              db,
		"uptime_seconds":  int(now.Sub(s.startedAt).Seconds()),
		"server_time":     jstime.FormatTime(now),
		"online_sessions": online,
	})
}

func (s *Server) handleSPA(w http.ResponseWriter, r *http.Request) {
	if s.webFS == nil {
		w.Header().Set("Content-Type", "text/plain; charset=utf-8")
		w.WriteHeader(http.StatusServiceUnavailable)
		_, _ = w.Write([]byte("前端尚未构建：请在 web/ 目录执行 npm install && npm run build 后重新编译服务。\n"))
		return
	}
	if strings.HasPrefix(r.URL.Path, "/api/") {
		httpx.WriteJSON(w, http.StatusNotFound, map[string]any{
			"code": service.CodeNotFound, "status": service.StatusNotFound, "message": "接口不存在",
		})
		return
	}
	path := strings.TrimPrefix(r.URL.Path, "/")
	if path == "" {
		path = "index.html"
	}
	setSecurityHeaders(w)
	if _, err := fs.Stat(s.webFS, path); err != nil {
		s.serveIndex(w, r)
		return
	}
	if strings.HasPrefix(path, "assets/") {
		w.Header().Set("Cache-Control", "public, max-age=31536000, immutable")
	} else {
		w.Header().Set("Cache-Control", "no-store")
	}
	http.FileServer(http.FS(s.webFS)).ServeHTTP(w, r)
}

func (s *Server) serveIndex(w http.ResponseWriter, r *http.Request) {
	body, err := fs.ReadFile(s.webFS, "index.html")
	if err != nil {
		w.Header().Set("Content-Type", "text/plain; charset=utf-8")
		w.WriteHeader(http.StatusServiceUnavailable)
		_, _ = w.Write([]byte("前端资源不完整：缺少 index.html。\n"))
		return
	}
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	w.Header().Set("Cache-Control", "no-store")
	w.WriteHeader(http.StatusOK)
	_, _ = w.Write(body)
}

func setSecurityHeaders(w http.ResponseWriter) {
	w.Header().Set("X-Content-Type-Options", "nosniff")
	w.Header().Set("X-Frame-Options", "DENY")
	w.Header().Set("Referrer-Policy", "strict-origin-when-cross-origin")
	w.Header().Set("Content-Security-Policy",
		"default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'")
}
