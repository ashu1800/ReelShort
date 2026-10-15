package service

import (
	"errors"
	"fmt"
)

// Error 是管理后台接口的业务错误，Code 由 HTTP 层映射为状态码。
type Error struct {
	Code    string
	Message string
	// HTTPStatus 允许覆盖默认映射。
	HTTPStatus int
}

func (e *Error) Error() string { return e.Message }

// 管理接口错误码。
const (
	ErrCodeInvalidCredentials = "invalid_credentials"
	ErrCodeUnauthorized       = "unauthorized"
	ErrCodeForbidden          = "forbidden"
	ErrCodePasswordChange     = "password_change_required"
	ErrCodeNotFound           = "not_found"
	ErrCodeConflict           = "conflict"
	ErrCodeValidation         = "validation_error"
	ErrCodeRateLimited        = "rate_limited"
	ErrCodeInternal           = "internal_error"
)

// NewError 构造业务错误。
func NewError(code, message string) *Error {
	return &Error{Code: code, Message: message}
}

// ValidationError 构造校验错误。
func ValidationError(format string, args ...any) *Error {
	return &Error{Code: ErrCodeValidation, Message: fmt.Sprintf(format, args...)}
}

// NotFoundError 构造"不存在"错误。
func NotFoundError(message string) *Error {
	return &Error{Code: ErrCodeNotFound, Message: message}
}

// ConflictError 构造冲突错误。
func ConflictError(message string) *Error {
	return &Error{Code: ErrCodeConflict, Message: message}
}

// IsErrorCode 判断错误是否属于指定业务码。
func IsErrorCode(err error, code string) bool {
	var e *Error
	if errors.As(err, &e) {
		return e.Code == code
	}
	return false
}
