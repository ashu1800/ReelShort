// Package security 提供卡密生成、令牌、脱敏、限流等安全原语。
package security

import (
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"errors"
	"strings"
	"unicode"
)

// cardAlphabet 去掉了 I/O/0/1 等易混字符，便于人工抄写与客服核对。
const cardAlphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

// ErrEmptyCode 表示归一化后卡密为空。
var ErrEmptyCode = errors.New("empty card code")

// NormalizeCardCode 把用户输入归一化为存储用的规范形：去掉所有非字母数字字符并转大写。
func NormalizeCardCode(raw string) string {
	var b strings.Builder
	b.Grow(len(raw))
	for _, r := range raw {
		switch {
		case r >= '0' && r <= '9':
			b.WriteRune(r)
		case r >= 'a' && r <= 'z':
			b.WriteRune(r - 'a' + 'A')
		case r >= 'A' && r <= 'Z':
			b.WriteRune(r)
		}
	}
	return b.String()
}

// GenerateCardCode 生成规范形卡密（prefix + 随机体）。
// length 为总长度（含前缀），调用方需保证 12 <= length <= 32。
func GenerateCardCode(prefix string, length int) (string, error) {
	prefix = NormalizeCardCode(prefix)
	if length <= len(prefix) {
		return "", errors.New("card code length must exceed prefix length")
	}
	body := make([]byte, length-len(prefix))
	// 拒绝采样：256 % 32 == 0，因此每个字节可直接映射，无模偏置。
	for i := range body {
		var buf [1]byte
		if _, err := rand.Read(buf[:]); err != nil {
			return "", err
		}
		body[i] = cardAlphabet[int(buf[0])%len(cardAlphabet)]
	}
	return prefix + string(body), nil
}

// FormatCardCode 把规范形卡密按每 4 位一组加分隔符，便于展示与抄写。
func FormatCardCode(canonical string) string {
	if canonical == "" {
		return ""
	}
	var b strings.Builder
	for i, r := range canonical {
		if i > 0 && i%4 == 0 {
			b.WriteByte('-')
		}
		b.WriteRune(r)
	}
	return b.String()
}

// MaskCardCode 用于日志与列表默认展示，始终保留首 4 与末 4。
func MaskCardCode(canonical string) string {
	n := len(canonical)
	if n == 0 {
		return ""
	}
	if n <= 8 {
		return strings.Repeat("*", n)
	}
	return canonical[:4] + "-****-****-" + canonical[n-4:]
}

// NormalizeMachine 归一化机器码：去首尾与内部空白、转大写、去掉控制字符。
// 返回空字符串代表机器码非法（调用方必须拒绝）。
func NormalizeMachine(raw string) string {
	var b strings.Builder
	b.Grow(len(raw))
	for _, r := range raw {
		if unicode.IsSpace(r) || unicode.IsControl(r) {
			continue
		}
		b.WriteRune(unicode.ToUpper(r))
	}
	s := b.String()
	if len(s) > 128 {
		s = s[:128]
	}
	return s
}

// MaskMachine 机器码脱敏：保留前 6 后 4。
func MaskMachine(machine string) string {
	n := len(machine)
	if n == 0 {
		return ""
	}
	if n <= 10 {
		if n <= 4 {
			return strings.Repeat("*", n)
		}
		return machine[:2] + strings.Repeat("*", n-4) + machine[n-2:]
	}
	return machine[:6] + "**" + machine[n-4:]
}

// NewSessionToken 生成 32 字节随机的会话令牌，返回明文（下发客户端）与 SHA-256 十六进制（落库）。
func NewSessionToken() (raw string, hash string, err error) {
	buf := make([]byte, 32)
	if _, err := rand.Read(buf); err != nil {
		return "", "", err
	}
	raw = base64.RawURLEncoding.EncodeToString(buf)
	return raw, HashToken(raw), nil
}

// HashToken 计算令牌哈希，落库与查询都用它，明文令牌不落库。
func HashToken(raw string) string {
	sum := sha256.Sum256([]byte(raw))
	return hex.EncodeToString(sum[:])
}

// NewRandomPassword 生成给管理员的随机初始密码（用于部署脚本与紧急重置）。
func NewRandomPassword(length int) (string, error) {
	const alphabet = "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789!@#%^*-_=+"
	buf := make([]byte, length)
	for i := range buf {
		var one [1]byte
		if _, err := rand.Read(one[:]); err != nil {
			return "", err
		}
		buf[i] = alphabet[int(one[0])%len(alphabet)]
	}
	return string(buf), nil
}
