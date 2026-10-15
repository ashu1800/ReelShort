// Package httpx 提供 HTTP 层共享工具：JSON 读写、错误映射、真实 IP 解析、请求 ID。
package httpx

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"errors"
	"net"
	"net/http"
	"strings"
	"time"

	"card.hjj888.cc/cardservice/internal/service"
)

type ctxKey int

const (
	ctxKeyClientIP ctxKey = iota
	ctxKeyRequestID
)

// WithClientIP 把解析后的真实客户端 IP 放入上下文。
func WithClientIP(ctx context.Context, ip string) context.Context {
	return context.WithValue(ctx, ctxKeyClientIP, ip)
}

// ClientIP 读取上下文中的真实客户端 IP。
func ClientIP(ctx context.Context) string {
	if v, ok := ctx.Value(ctxKeyClientIP).(string); ok {
		return v
	}
	return ""
}

// WithRequestID 把请求 ID 放入上下文。
func WithRequestID(ctx context.Context, id string) context.Context {
	return context.WithValue(ctx, ctxKeyRequestID, id)
}

// RequestID 读取请求 ID。
func RequestID(ctx context.Context) string {
	if v, ok := ctx.Value(ctxKeyRequestID).(string); ok {
		return v
	}
	return ""
}

// NewRequestID 生成 16 位十六进制请求 ID。
func NewRequestID() string {
	buf := make([]byte, 8)
	if _, err := rand.Read(buf); err != nil {
		return hex.EncodeToString([]byte(time.Now().Format("150405.000000")))
	}
	return hex.EncodeToString(buf)
}

// WriteJSON 写 JSON 响应（不转义 HTML，便于中文与前端展示）。
func WriteJSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.Header().Set("Cache-Control", "no-store")
	w.Header().Set("X-Content-Type-Options", "nosniff")
	w.WriteHeader(status)
	enc := json.NewEncoder(w)
	enc.SetEscapeHTML(false)
	_ = enc.Encode(v)
}

// ErrorBody 是管理接口的统一错误体。
type ErrorBody struct {
	Error ErrorDetail `json:"error"`
}

// ErrorDetail 描述错误码与提示。
type ErrorDetail struct {
	Code    string `json:"code"`
	Message string `json:"message"`
}

// WriteError 把业务错误映射为 HTTP 状态码 + 统一错误体。
func WriteError(w http.ResponseWriter, err error) {
	status := http.StatusInternalServerError
	code := service.ErrCodeInternal
	message := "服务异常，请稍后再试"

	var se *service.Error
	if errors.As(err, &se) {
		code = se.Code
		message = se.Message
		switch se.Code {
		case service.ErrCodeInvalidCredentials, service.ErrCodeUnauthorized:
			status = http.StatusUnauthorized
		case service.ErrCodeForbidden, service.ErrCodePasswordChange:
			status = http.StatusForbidden
		case service.ErrCodeNotFound:
			status = http.StatusNotFound
		case service.ErrCodeConflict:
			status = http.StatusConflict
		case service.ErrCodeValidation:
			status = http.StatusUnprocessableEntity
		case service.ErrCodeRateLimited:
			status = http.StatusTooManyRequests
		default:
			status = http.StatusInternalServerError
		}
		if se.HTTPStatus != 0 {
			status = se.HTTPStatus
		}
	}
	WriteJSON(w, status, ErrorBody{Error: ErrorDetail{Code: code, Message: message}})
}

// WriteErrorCode 直接写业务错误码。
func WriteErrorCode(w http.ResponseWriter, status int, code, message string) {
	WriteJSON(w, status, ErrorBody{Error: ErrorDetail{Code: code, Message: message}})
}

// DecodeJSON 限制体积并解析 JSON 请求体。
func DecodeJSON(w http.ResponseWriter, r *http.Request, dst any, maxBytes int64) error {
	if maxBytes <= 0 {
		maxBytes = 1 << 20
	}
	r.Body = http.MaxBytesReader(w, r.Body, maxBytes)
	dec := json.NewDecoder(r.Body)
	if err := dec.Decode(dst); err != nil {
		return errors.New("请求体必须是合法 JSON")
	}
	return nil
}

// RemoteIP 解析真实客户端 IP：
// 只有当直接对端属于可信代理（例如本机 nginx）时才采信 X-Forwarded-For，取最右侧的非可信地址。
func RemoteIP(r *http.Request, trusted func(net.IP) bool) string {
	host, _, err := net.SplitHostPort(r.RemoteAddr)
	if err != nil {
		host = r.RemoteAddr
	}
	remote := net.ParseIP(strings.TrimSpace(host))
	if remote == nil {
		return ""
	}
	if trusted == nil || !trusted(remote) {
		return remote.String()
	}
	xff := r.Header.Get("X-Forwarded-For")
	if xff == "" {
		if real := strings.TrimSpace(r.Header.Get("X-Real-IP")); real != "" {
			if ip := net.ParseIP(real); ip != nil {
				return ip.String()
			}
		}
		return remote.String()
	}
	parts := strings.Split(xff, ",")
	for i := len(parts) - 1; i >= 0; i-- {
		ip := net.ParseIP(strings.TrimSpace(parts[i]))
		if ip == nil {
			continue
		}
		if trusted(ip) {
			continue
		}
		return ip.String()
	}
	return remote.String()
}
