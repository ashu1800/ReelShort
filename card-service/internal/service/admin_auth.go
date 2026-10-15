package service

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"strings"
	"sync"
	"time"

	"golang.org/x/crypto/bcrypt"

	"card.hjj888.cc/cardservice/internal/security"
	"card.hjj888.cc/cardservice/internal/store"
)

// AdminIdentity 是当前登录管理员。
type AdminIdentity struct {
	ID                 int64
	Username           string
	DisplayName        string
	MustChangePassword bool
	SessionID          int64
	SessionExpiresAt   int64
}

var dummyHashOnce sync.Once
var dummyHash []byte

// dummyBcrypt 在账号不存在时也做一次 bcrypt 比对，避免账号枚举。
func dummyBcrypt(password string) {
	dummyHashOnce.Do(func() {
		h, err := bcrypt.GenerateFromPassword([]byte("cardservice-dummy-password"), BcryptCost)
		if err == nil {
			dummyHash = h
		}
	})
	if dummyHash != nil {
		_ = bcrypt.CompareHashAndPassword(dummyHash, []byte(password))
	}
}

// AdminLogin 校验管理员账号口令并建立后台会话。
func (s *Service) AdminLogin(ctx context.Context, username, password string, ci ClientInfo) (*AdminIdentity, string, int64, error) {
	username = strings.TrimSpace(username)
	if username == "" || password == "" {
		return nil, "", 0, NewError(ErrCodeInvalidCredentials, "账号或密码错误")
	}
	lockKey := username + "|" + ci.IP
	if locked, remain := s.guard.Locked(lockKey); locked {
		return nil, "", 0, &Error{
			Code:       ErrCodeRateLimited,
			Message:    fmt.Sprintf("登录失败次数过多，请 %d 秒后再试", remain),
			HTTPStatus: 429,
		}
	}

	set, err := s.Settings(ctx)
	if err != nil {
		return nil, "", 0, err
	}
	now := s.now().Unix()
	sessionExpires := now + int64(set.AdminSessionHours)*3600

	var identity *AdminIdentity
	var rawToken string
	err = s.store.Write(ctx, func(tx *store.Tx) error {
		admin, err := tx.AdminByUsername(ctx, username)
		if errors.Is(err, store.ErrNotFound) {
			dummyBcrypt(password)
			s.guard.RecordFailure(lockKey)
			return NewError(ErrCodeInvalidCredentials, "账号或密码错误")
		}
		if err != nil {
			return err
		}
		if bcrypt.CompareHashAndPassword([]byte(admin.PasswordHash), []byte(password)) != nil {
			s.guard.RecordFailure(lockKey)
			return NewError(ErrCodeInvalidCredentials, "账号或密码错误")
		}
		token, hash, err := security.NewSessionToken()
		if err != nil {
			return err
		}
		session := &store.AdminSession{
			TokenHash:  hash,
			AdminID:    admin.ID,
			IP:         ci.IP,
			UserAgent:  truncate(ci.UserAgent, 200),
			CreatedAt:  now,
			ExpiresAt:  sessionExpires,
			LastSeenAt: now,
		}
		if err := tx.InsertAdminSession(ctx, session); err != nil {
			return err
		}
		if err := tx.UpdateAdminLastLogin(ctx, admin.ID, now); err != nil {
			return err
		}
		if err := tx.InsertAuditLog(ctx, &store.AuditLog{
			AdminID: admin.ID, Username: admin.Username, Action: "admin_login",
			TargetType: "admin", TargetID: admin.Username, Detail: "", IP: ci.IP, CreatedAt: now,
		}); err != nil {
			return err
		}
		rawToken = token
		identity = &AdminIdentity{
			ID:                 admin.ID,
			Username:           admin.Username,
			DisplayName:        admin.DisplayName,
			MustChangePassword: admin.MustChangePassword,
			SessionID:          session.ID,
			SessionExpiresAt:   sessionExpires,
		}
		return nil
	})
	if err != nil {
		return nil, "", 0, err
	}
	s.guard.Reset(lockKey)
	return identity, rawToken, sessionExpires, nil
}

// AdminAuthenticate 校验后台会话令牌并滑动续期。
func (s *Service) AdminAuthenticate(ctx context.Context, token string) (*AdminIdentity, error) {
	if token == "" {
		return nil, NewError(ErrCodeUnauthorized, "未登录或登录已过期")
	}
	hash := security.HashToken(token)
	now := s.now().Unix()
	var identity *AdminIdentity
	err := s.store.Write(ctx, func(tx *store.Tx) error {
		sess, err := tx.AdminSessionByTokenHash(ctx, hash)
		if errors.Is(err, store.ErrNotFound) {
			return NewError(ErrCodeUnauthorized, "未登录或登录已过期")
		}
		if err != nil {
			return err
		}
		if sess.ExpiresAt <= now {
			if err := tx.DeleteAdminSession(ctx, sess.ID); err != nil {
				return err
			}
			return NewError(ErrCodeUnauthorized, "未登录或登录已过期")
		}
		admin, err := tx.AdminByID(ctx, sess.AdminID)
		if errors.Is(err, store.ErrNotFound) {
			_ = tx.DeleteAdminSession(ctx, sess.ID)
			return NewError(ErrCodeUnauthorized, "未登录或登录已过期")
		}
		if err != nil {
			return err
		}
		set, err := s.Settings(ctx)
		if err != nil {
			return err
		}
		newExpiry := now + int64(set.AdminSessionHours)*3600
		if err := tx.TouchAdminSession(ctx, sess.ID, newExpiry, now); err != nil {
			return err
		}
		identity = &AdminIdentity{
			ID:                 admin.ID,
			Username:           admin.Username,
			DisplayName:        admin.DisplayName,
			MustChangePassword: admin.MustChangePassword,
			SessionID:          sess.ID,
			SessionExpiresAt:   newExpiry,
		}
		return nil
	})
	if err != nil {
		return nil, err
	}
	return identity, nil
}

// AdminProfile 返回管理员资料（用于登录响应与 /auth/me）。
func (s *Service) AdminProfile(ctx context.Context, id int64) (*store.Admin, error) {
	admin, err := s.store.Snapshot().AdminByID(ctx, id)
	if errors.Is(err, store.ErrNotFound) {
		return nil, NewError(ErrCodeUnauthorized, "未登录或登录已过期")
	}
	if err != nil {
		return nil, err
	}
	return admin, nil
}

// AdminResetPassword 为指定管理员生成新的随机口令（运维应急用，通过命令行触发）。
// 返回明文新口令，并把该管理员重新置为"必须改密"。
func (s *Service) AdminResetPassword(ctx context.Context, username string) (string, error) {
	username = strings.TrimSpace(username)
	if username == "" {
		return "", NewError(ErrCodeValidation, "请提供管理员账号")
	}
	password, err := security.NewRandomPassword(16)
	if err != nil {
		return "", err
	}
	hash, err := bcrypt.GenerateFromPassword([]byte(password), BcryptCost)
	if err != nil {
		return "", err
	}
	now := s.now().Unix()
	err = s.store.Write(ctx, func(tx *store.Tx) error {
		admin, err := tx.AdminByUsername(ctx, username)
		if errors.Is(err, store.ErrNotFound) {
			return NotFoundError("管理员不存在")
		}
		if err != nil {
			return err
		}
		if err := tx.UpdateAdminPassword(ctx, admin.ID, string(hash), true, now); err != nil {
			return err
		}
		if err := tx.DeleteAdminSessionsByAdmin(ctx, admin.ID); err != nil {
			return err
		}
		return tx.InsertAuditLog(ctx, &store.AuditLog{
			AdminID: admin.ID, Username: admin.Username, Action: "admin_reset_password",
			TargetType: "admin", TargetID: admin.Username, Detail: "命令行重置口令", CreatedAt: now,
		})
	})
	if err != nil {
		return "", err
	}
	return password, nil
}

// AdminLogout 删除后台会话（幂等）。
func (s *Service) AdminLogout(ctx context.Context, token string, admin *AdminIdentity, ci ClientInfo) error {
	if token == "" {
		return nil
	}
	hash := security.HashToken(token)
	now := s.now().Unix()
	return s.store.Write(ctx, func(tx *store.Tx) error {
		sess, err := tx.AdminSessionByTokenHash(ctx, hash)
		if errors.Is(err, store.ErrNotFound) {
			return nil
		}
		if err != nil {
			return err
		}
		if err := tx.DeleteAdminSession(ctx, sess.ID); err != nil {
			return err
		}
		if admin != nil {
			return tx.InsertAuditLog(ctx, &store.AuditLog{
				AdminID: admin.ID, Username: admin.Username, Action: "admin_logout",
				TargetType: "admin", TargetID: admin.Username, IP: ci.IP, CreatedAt: now,
			})
		}
		return nil
	})
}

// AdminChangePassword 修改当前管理员口令，并吊销该管理员全部会话。
func (s *Service) AdminChangePassword(ctx context.Context, admin *AdminIdentity, oldPassword, newPassword string, ci ClientInfo) error {
	if admin == nil {
		return NewError(ErrCodeUnauthorized, "未登录或登录已过期")
	}
	if len([]rune(newPassword)) < 10 {
		return ValidationError("新密码至少 10 位")
	}
	if oldPassword == newPassword {
		return ValidationError("新密码不能与旧密码相同")
	}
	hash, err := bcrypt.GenerateFromPassword([]byte(newPassword), BcryptCost)
	if err != nil {
		return err
	}
	now := s.now().Unix()
	return s.store.Write(ctx, func(tx *store.Tx) error {
		current, err := tx.AdminByID(ctx, admin.ID)
		if errors.Is(err, store.ErrNotFound) {
			return NewError(ErrCodeUnauthorized, "未登录或登录已过期")
		}
		if err != nil {
			return err
		}
		if bcrypt.CompareHashAndPassword([]byte(current.PasswordHash), []byte(oldPassword)) != nil {
			return NewError(ErrCodeValidation, "旧密码不正确")
		}
		if err := tx.UpdateAdminPassword(ctx, admin.ID, string(hash), false, now); err != nil {
			return err
		}
		if err := tx.DeleteAdminSessionsByAdmin(ctx, admin.ID); err != nil {
			return err
		}
		return tx.InsertAuditLog(ctx, &store.AuditLog{
			AdminID: admin.ID, Username: admin.Username, Action: "admin_change_password",
			TargetType: "admin", TargetID: admin.Username, IP: ci.IP, CreatedAt: now,
		})
	})
}

var _ = sql.NullInt64{}
var _ = time.Now
