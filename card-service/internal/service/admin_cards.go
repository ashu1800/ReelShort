package service

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"strings"

	"card.hjj888.cc/cardservice/internal/jstime"
	"card.hjj888.cc/cardservice/internal/security"
	"card.hjj888.cc/cardservice/internal/store"
)

// CreateCardsInput 是批量生成卡密的入参。
type CreateCardsInput struct {
	TypeID     int64
	Count      int
	Prefix     string
	Length     int
	MaxRebinds *int
	BatchNo    string
	Remark     string
}

// CreateCardsResult 是生成结果。
type CreateCardsResult struct {
	Codes   []string
	BatchNo string
}

// UpdateCardInput 是卡密局部更新入参（nil 表示不改）。
type UpdateCardInput struct {
	Remark     *string
	MaxRebinds *int
	// ExpiresAt："" 表示改为永久；其余为 "YYYY-MM-DD HH:MM:SS"（上海时区）。
	ExpiresAt *string
}

// AdminCreateCards 批量生成卡密（单次事务，冲突自动重试）。
func (s *Service) AdminCreateCards(ctx context.Context, in CreateCardsInput, admin *AdminIdentity, ip string) (*CreateCardsResult, error) {
	if admin == nil {
		return nil, NewError(ErrCodeUnauthorized, "未登录或登录已过期")
	}
	if in.Count <= 0 {
		in.Count = 1
	}
	if in.Count > 10000 {
		return nil, ValidationError("单次最多生成 10000 张卡密")
	}
	if in.Length == 0 {
		in.Length = 16
	}
	if in.Length < 12 || in.Length > 32 {
		return nil, ValidationError("卡密长度需在 12-32 之间")
	}
	prefix := security.NormalizeCardCode(in.Prefix)
	if len(prefix) >= in.Length {
		return nil, ValidationError("前缀长度必须小于卡密总长度")
	}
	if len([]rune(in.Remark)) > 200 {
		return nil, ValidationError("备注不能超过 200 字")
	}
	batchNo := strings.TrimSpace(in.BatchNo)
	if len(batchNo) > 64 {
		return nil, ValidationError("批次号不能超过 64 字符")
	}
	if batchNo != "" && !isSafeToken(batchNo) {
		return nil, ValidationError("批次号只能包含字母、数字、短横线与下划线")
	}

	now := s.now().Unix()
	out := &CreateCardsResult{}
	err := s.store.Write(ctx, func(tx *store.Tx) error {
		ct, err := tx.CardTypeByID(ctx, in.TypeID)
		if errors.Is(err, store.ErrNotFound) {
			return ValidationError("卡密类型不存在")
		}
		if err != nil {
			return err
		}
		maxRebinds := ct.MaxRebinds
		if in.MaxRebinds != nil {
			maxRebinds = *in.MaxRebinds
		}
		if maxRebinds < 0 {
			maxRebinds = 0
		}
		if maxRebinds > 100 {
			maxRebinds = 100
		}
		if batchNo == "" {
			batchNo, err = tx.NextBatchNo(ctx, jstime.DayKey(now), now)
			if err != nil {
				return err
			}
		}
		codes := make([]string, 0, in.Count)
		for i := 0; i < in.Count; i++ {
			inserted := false
			for attempt := 0; attempt < 6; attempt++ {
				code, err := security.GenerateCardCode(prefix, in.Length)
				if err != nil {
					return err
				}
				card := &store.Card{
					Code:            code,
					TypeID:          sql.NullInt64{Int64: ct.ID, Valid: true},
					TypeName:        ct.Name,
					DurationMinutes: ct.DurationMinutes,
					State:           "unused",
					MaxRebinds:      maxRebinds,
					BatchNo:         batchNo,
					Remark:          in.Remark,
					CreatedBy:       admin.Username,
					CreatedAt:       now,
					UpdatedAt:       now,
				}
				if err := tx.InsertCard(ctx, card); err != nil {
					if isUniqueViolation(err) {
						continue
					}
					return err
				}
				codes = append(codes, code)
				inserted = true
				break
			}
			if !inserted {
				return errors.New("卡密生成失败：随机码连续冲突")
			}
		}
		out.Codes = codes
		out.BatchNo = batchNo
		return tx.InsertAuditLog(ctx, &store.AuditLog{
			AdminID: admin.ID, Username: admin.Username, Action: "card_batch_create",
			TargetType: "card", TargetID: batchNo,
			Detail: fmt.Sprintf(`{"count":%d,"type":%q,"batch_no":%q,"max_rebinds":%d}`,
				len(codes), ct.Name, batchNo, maxRebinds),
			IP: ip, CreatedAt: now,
		})
	})
	if err != nil {
		return nil, err
	}
	return out, nil
}

// AdminListCards 分页查询卡密。
func (s *Service) AdminListCards(ctx context.Context, f store.CardFilter) ([]*store.Card, int, error) {
	f.Keyword = smartKeyword(f.Keyword)
	return s.store.Snapshot().ListCards(ctx, f, s.now().Unix())
}

// AdminExportCards 导出卡密（不分页）。
func (s *Service) AdminExportCards(ctx context.Context, f store.CardFilter) ([]*store.Card, error) {
	f.Keyword = smartKeyword(f.Keyword)
	if f.Limit <= 0 {
		f.Limit = 100000
	}
	return s.store.Snapshot().ExportCards(ctx, f, s.now().Unix())
}

// AdminCardDetail 返回卡密详情、会话历史与操作日志。
func (s *Service) AdminCardDetail(ctx context.Context, id int64) (*store.Card, []*store.Session, []*store.CardLog, error) {
	tx := s.store.Snapshot()
	card, err := tx.CardByID(ctx, id)
	if errors.Is(err, store.ErrNotFound) {
		return nil, nil, nil, NotFoundError("卡密不存在")
	}
	if err != nil {
		return nil, nil, nil, err
	}
	sessions, err := tx.ListSessionsByCard(ctx, id, 50)
	if err != nil {
		return nil, nil, nil, err
	}
	logs, err := tx.ListLogsByCard(ctx, id, 50)
	if err != nil {
		return nil, nil, nil, err
	}
	return card, sessions, logs, nil
}

// AdminUpdateCard 局部更新卡密（备注/换绑上限/有效期）。
func (s *Service) AdminUpdateCard(ctx context.Context, id int64, in UpdateCardInput, admin *AdminIdentity, ip string) (*store.Card, error) {
	if admin == nil {
		return nil, NewError(ErrCodeUnauthorized, "未登录或登录已过期")
	}
	now := s.now().Unix()
	var updated *store.Card
	err := s.store.Write(ctx, func(tx *store.Tx) error {
		card, err := tx.CardByID(ctx, id)
		if errors.Is(err, store.ErrNotFound) {
			return NotFoundError("卡密不存在")
		}
		if err != nil {
			return err
		}
		changes := []string{}
		if in.Remark != nil {
			if len([]rune(*in.Remark)) > 200 {
				return ValidationError("备注不能超过 200 字")
			}
			if card.Remark != *in.Remark {
				changes = append(changes, fmt.Sprintf("备注:%q->%q", card.Remark, *in.Remark))
				card.Remark = *in.Remark
			}
		}
		if in.MaxRebinds != nil {
			v := *in.MaxRebinds
			if v < 0 || v > 100 {
				return ValidationError("换绑次数上限需在 0-100 之间")
			}
			if card.MaxRebinds != v {
				changes = append(changes, fmt.Sprintf("换绑上限:%d->%d", card.MaxRebinds, v))
				card.MaxRebinds = v
			}
		}
		if in.ExpiresAt != nil {
			raw := strings.TrimSpace(*in.ExpiresAt)
			if raw == "" {
				if card.ExpiresAt.Valid {
					changes = append(changes, fmt.Sprintf("到期:%s->永久", jstime.Format(card.ExpiresAt.Int64)))
				}
				card.ExpiresAt = sql.NullInt64{}
				card.DurationMinutes = 0
			} else {
				t, err := jstime.Parse(raw)
				if err != nil {
					return ValidationError("到期时间格式应为 YYYY-MM-DD HH:MM:SS")
				}
				if t.Unix() <= now {
					return ValidationError("到期时间必须晚于当前时间")
				}
				changes = append(changes, fmt.Sprintf("到期:%s->%s", jstime.Format(card.ExpiresAt.Int64), raw))
				card.ExpiresAt = sql.NullInt64{Int64: t.Unix(), Valid: true}
			}
		}
		if len(changes) == 0 {
			updated = card
			return nil
		}
		card.UpdatedAt = now
		if err := tx.UpdateCard(ctx, card); err != nil {
			return err
		}
		if err := tx.InsertAuditLog(ctx, &store.AuditLog{
			AdminID: admin.ID, Username: admin.Username, Action: "card_update",
			TargetType: "card", TargetID: fmt.Sprintf("%d", card.ID),
			Detail: strings.Join(changes, "; "), IP: ip, CreatedAt: now,
		}); err != nil {
			return err
		}
		if err := tx.InsertCardLog(ctx, &store.CardLog{
			CardID: sql.NullInt64{Int64: card.ID, Valid: true}, CodeMasked: security.MaskCardCode(card.Code),
			Action: "admin_edit", Result: "ok", Message: truncate(strings.Join(changes, "; "), 200),
			IP: ip, CreatedAt: now,
		}); err != nil {
			return err
		}
		updated = card
		return nil
	})
	if err != nil {
		return nil, err
	}
	return updated, nil
}

// AdminCardAction 执行单卡操作：disable / enable / unbind / reset_rebinds / extend。
// minutes 仅 extend 使用（正数表示顺延的分钟数）。
func (s *Service) AdminCardAction(ctx context.Context, id int64, action string, minutes int64, admin *AdminIdentity, ip string) (*store.Card, error) {
	if admin == nil {
		return nil, NewError(ErrCodeUnauthorized, "未登录或登录已过期")
	}
	if action == "extend" && minutes <= 0 {
		return nil, ValidationError("请提供延期时长")
	}
	var updated *store.Card
	now := s.now().Unix()
	err := s.store.Write(ctx, func(tx *store.Tx) error {
		card, err := tx.CardByID(ctx, id)
		if errors.Is(err, store.ErrNotFound) {
			return NotFoundError("卡密不存在")
		}
		if err != nil {
			return err
		}
		detail, err := s.applyCardAction(ctx, tx, card, action, minutes, now)
		if err != nil {
			return err
		}
		card.UpdatedAt = now
		if err := tx.UpdateCard(ctx, card); err != nil {
			return err
		}
		if err := tx.InsertAuditLog(ctx, &store.AuditLog{
			AdminID: admin.ID, Username: admin.Username, Action: "card_" + action,
			TargetType: "card", TargetID: fmt.Sprintf("%d", card.ID),
			Detail: detail, IP: ip, CreatedAt: now,
		}); err != nil {
			return err
		}
		if err := tx.InsertCardLog(ctx, &store.CardLog{
			CardID: sql.NullInt64{Int64: card.ID, Valid: true}, CodeMasked: security.MaskCardCode(card.Code),
			Action: "admin_" + action, Result: "ok", Message: detail, IP: ip, CreatedAt: now,
		}); err != nil {
			return err
		}
		updated = card
		return nil
	})
	if err != nil {
		return nil, err
	}
	return updated, nil
}

// applyCardAction 在事务内修改卡密实体（不含落库与审计）。
func (s *Service) applyCardAction(ctx context.Context, tx *store.Tx, card *store.Card, action string, minutes int64, now int64) (string, error) {
	switch action {
	case "disable":
		if card.State != "disabled" {
			card.State = "disabled"
			if _, err := tx.CloseOpenSessionsByCard(ctx, card.ID, now, "disabled"); err != nil {
				return "", err
			}
			if err := tx.ClearCardOnline(ctx, card.ID, now); err != nil {
				return "", err
			}
		}
		return "已禁用并踢下线", nil
	case "enable":
		if card.State == "disabled" {
			if card.ActivatedAt > 0 {
				card.State = "active"
			} else {
				card.State = "unused"
			}
		}
		return "已启用", nil
	case "unbind":
		card.BoundMachine = ""
		card.BoundAt = 0
		if _, err := tx.CloseOpenSessionsByCard(ctx, card.ID, now, "admin_kick"); err != nil {
			return "", err
		}
		if err := tx.ClearCardOnline(ctx, card.ID, now); err != nil {
			return "", err
		}
		return "已解绑机器码（换绑次数未重置）", nil
	case "reset_rebinds":
		card.RebindCount = 0
		return "已重置换绑次数", nil
	case "extend":
		if minutes <= 0 {
			return "", ValidationError("请提供延期时长")
		}
		if !card.Activated() {
			if card.DurationMinutes == 0 {
				return "", ValidationError("永久卡无需延期")
			}
			card.DurationMinutes += minutes
			return "未激活卡时长 +" + humanMinutes(minutes), nil
		}
		if card.IsPermanent() {
			return "", ValidationError("永久卡无需延期")
		}
		base := now
		if card.ExpiresAt.Valid && card.ExpiresAt.Int64 > now {
			base = card.ExpiresAt.Int64
		}
		card.ExpiresAt = sql.NullInt64{Int64: base + minutes*60, Valid: true}
		return "到期时间顺延 " + humanMinutes(minutes), nil
	default:
		return "", ValidationError("不支持的操作：%s", action)
	}
}

// AdminBatchAction 批量操作，返回受影响数量。minutes 仅 extend 使用。
func (s *Service) AdminBatchAction(ctx context.Context, ids []int64, action string, minutes int64, admin *AdminIdentity, ip string) (int, error) {
	if admin == nil {
		return 0, NewError(ErrCodeUnauthorized, "未登录或登录已过期")
	}
	if len(ids) == 0 {
		return 0, ValidationError("请选择要操作的卡密")
	}
	if len(ids) > 5000 {
		return 0, ValidationError("单次最多操作 5000 张卡密")
	}
	allowed := map[string]bool{"disable": true, "enable": true, "delete": true, "reset_rebinds": true, "extend": true}
	if !allowed[action] {
		return 0, ValidationError("不支持的批量操作：%s", action)
	}
	if action == "extend" && minutes <= 0 {
		return 0, ValidationError("请提供延期时长")
	}
	now := s.now().Unix()
	affected := 0
	err := s.store.Write(ctx, func(tx *store.Tx) error {
		for _, id := range ids {
			card, err := tx.CardByID(ctx, id)
			if errors.Is(err, store.ErrNotFound) {
				continue
			}
			if err != nil {
				return err
			}
			if action == "delete" {
				if _, err := tx.CloseOpenSessionsByCard(ctx, card.ID, now, "admin_kick"); err != nil {
					return err
				}
				if err := tx.SoftDeleteCard(ctx, card.ID, now); err != nil {
					return err
				}
			} else {
				detail, err := s.applyCardAction(ctx, tx, card, action, minutes, now)
				if err != nil {
					return err
				}
				card.UpdatedAt = now
				if err := tx.UpdateCard(ctx, card); err != nil {
					return err
				}
				if err := tx.InsertCardLog(ctx, &store.CardLog{
					CardID: sql.NullInt64{Int64: card.ID, Valid: true}, CodeMasked: security.MaskCardCode(card.Code),
					Action: "admin_" + action, Result: "ok", Message: detail, IP: ip, CreatedAt: now,
				}); err != nil {
					return err
				}
			}
			affected++
		}
		return tx.InsertAuditLog(ctx, &store.AuditLog{
			AdminID: admin.ID, Username: admin.Username, Action: "card_batch_" + action,
			TargetType: "card", TargetID: fmt.Sprintf("%d", len(ids)),
			Detail: fmt.Sprintf(`{"requested":%d,"affected":%d,"minutes":%d}`, len(ids), affected, minutes),
			IP:     ip, CreatedAt: now,
		})
	})
	if err != nil {
		return 0, err
	}
	return affected, nil
}

// AdminDeleteCard 软删除单张卡密。
func (s *Service) AdminDeleteCard(ctx context.Context, id int64, admin *AdminIdentity, ip string) error {
	if admin == nil {
		return NewError(ErrCodeUnauthorized, "未登录或登录已过期")
	}
	now := s.now().Unix()
	return s.store.Write(ctx, func(tx *store.Tx) error {
		card, err := tx.CardByID(ctx, id)
		if errors.Is(err, store.ErrNotFound) {
			return NotFoundError("卡密不存在")
		}
		if err != nil {
			return err
		}
		if _, err := tx.CloseOpenSessionsByCard(ctx, card.ID, now, "admin_kick"); err != nil {
			return err
		}
		if err := tx.SoftDeleteCard(ctx, card.ID, now); err != nil {
			return err
		}
		return tx.InsertAuditLog(ctx, &store.AuditLog{
			AdminID: admin.ID, Username: admin.Username, Action: "card_delete",
			TargetType: "card", TargetID: fmt.Sprintf("%d", card.ID),
			Detail: security.MaskCardCode(card.Code), IP: ip, CreatedAt: now,
		})
	})
}

// smartKeyword 让用户输入的带分隔符卡密也能命中（卡密列存的是规范形）。
func smartKeyword(raw string) string {
	kw := strings.TrimSpace(raw)
	if kw == "" {
		return ""
	}
	if normalized := security.NormalizeCardCode(kw); len(normalized) >= 8 {
		return normalized
	}
	return kw
}

func isSafeToken(s string) bool {
	for _, r := range s {
		switch {
		case r >= '0' && r <= '9':
		case r >= 'a' && r <= 'z':
		case r >= 'A' && r <= 'Z':
		case r == '-' || r == '_':
		default:
			return false
		}
	}
	return true
}

func isUniqueViolation(err error) bool {
	if err == nil {
		return false
	}
	msg := strings.ToLower(err.Error())
	return strings.Contains(msg, "unique constraint") || strings.Contains(msg, "constraint failed")
}

// humanMinutes 把分钟数转成中文可读时长（日志与审计用）。
func humanMinutes(minutes int64) string {
	switch {
	case minutes <= 0:
		return "0 分钟"
	case minutes%1440 == 0:
		return fmt.Sprintf("%d 天", minutes/1440)
	case minutes%60 == 0:
		return fmt.Sprintf("%d 小时", minutes/60)
	default:
		return fmt.Sprintf("%d 分钟", minutes)
	}
}
