package store

import "database/sql"

// Card 是一张卡密。时间字段为 unix 秒；ExpiresAt 为 NULL 表示未激活或永久。
type Card struct {
	ID              int64
	Code            string
	TypeID          sql.NullInt64
	TypeName        string
	DurationMinutes int64
	State           string // unused | active | disabled
	MaxRebinds      int
	RebindCount     int
	BoundMachine    string
	BoundAt         int64
	ActivatedAt     int64
	ExpiresAt       sql.NullInt64
	OnlineSessionID sql.NullInt64
	OnlineMachine   string
	OnlineAt        int64
	BatchNo         string
	Remark          string
	CreatedBy       string
	CreatedAt       int64
	UpdatedAt       int64
	DeletedAt       sql.NullInt64
}

// IsPermanent 表示不限期卡密。
//
// 判定条件：时长配置为 0（永久类型）且没有显式到期时间。
// 管理员给永久卡显式设置到期时间后，它就不再是永久卡。
func (c *Card) IsPermanent() bool {
	return c.DurationMinutes == 0 && !(c.ExpiresAt.Valid && c.ExpiresAt.Int64 > 0)
}

// Expired 判断卡密是否已过期。
func (c *Card) Expired(now int64) bool {
	return c.ExpiresAt.Valid && c.ExpiresAt.Int64 > 0 && c.ExpiresAt.Int64 <= now
}

// Activated 表示已经开始计时。
func (c *Card) Activated() bool { return c.ActivatedAt > 0 }

// ActiveOnline 表示 card 表中缓存的在线态（由登录/关闭会话与后台守护协程维护）。
func (c *Card) ActiveOnline() bool { return c.OnlineSessionID.Valid && c.OnlineSessionID.Int64 > 0 }

// RebindRemaining 剩余换绑次数。
func (c *Card) RebindRemaining() int {
	if c.MaxRebinds < 0 {
		return 0
	}
	left := c.MaxRebinds - c.RebindCount
	if left < 0 {
		return 0
	}
	return left
}

// DisplayState 返回展示态：disabled > expired > active > unused。
func (c *Card) DisplayState(now int64) string {
	switch {
	case c.State == "disabled":
		return "disabled"
	case c.Expired(now):
		return "expired"
	case c.State == "active":
		return "active"
	default:
		return "unused"
	}
}

// Session 是一次上线会话。
type Session struct {
	ID              int64
	TokenHash       string
	CardID          int64
	MachineCode     string
	IP              string
	UserAgent       string
	CreatedAt       int64
	LastHeartbeatAt int64
	ExpiresAt       int64
	ClosedAt        sql.NullInt64
	CloseReason     string
}

// Open 表示会话仍在有效期内且未被关闭。
func (s *Session) Open(now int64) bool {
	return !s.ClosedAt.Valid && s.ExpiresAt > now
}

// CardLog 是一条卡密操作日志。
type CardLog struct {
	ID          int64
	CardID      sql.NullInt64
	CodeMasked  string
	Action      string
	Result      string
	Message     string
	MachineCode string
	IP          string
	UserAgent   string
	CreatedAt   int64
}

// Admin 是后台管理员。
type Admin struct {
	ID                 int64
	Username           string
	PasswordHash       string
	DisplayName        string
	MustChangePassword bool
	CreatedAt          int64
	UpdatedAt          int64
	LastLoginAt        int64
}

// AdminSession 是后台管理会话。
type AdminSession struct {
	ID         int64
	TokenHash  string
	AdminID    int64
	IP         string
	UserAgent  string
	CreatedAt  int64
	ExpiresAt  int64
	LastSeenAt int64
}

// AuditLog 是管理员操作审计。
type AuditLog struct {
	ID         int64
	AdminID    int64
	Username   string
	Action     string
	TargetType string
	TargetID   string
	Detail     string
	IP         string
	CreatedAt  int64
}

// CardType 是卡密类型（时长模板）。
type CardType struct {
	ID              int64
	Name            string
	DurationMinutes int64
	MaxRebinds      int
	Enabled         bool
	SortOrder       int
	CreatedAt       int64
	UpdatedAt       int64
	CardCount       int
}

// Stats 是概览页统计。
type Stats struct {
	Total    int
	Unused   int
	Active   int
	Expired  int
	Disabled int
	Online   int
}

// TrendPoint 是按天聚合的一个点。
type TrendPoint struct {
	Date      string
	Activated int
}
