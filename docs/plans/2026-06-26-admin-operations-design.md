# Admin Operations Design

## 目标

补齐后台运营基础能力：管理员可以调整用户积分，系统记录对应积分流水，并生成后台操作审计日志。这个切片把 `points` 模型从“仅观看奖励”扩展为“通用账户流水”，为后续充值、权益、风控和运营配置保留边界。

## 范围

本阶段实现：

- `POST /api/admin/users/{userId}/points/adjust`
- `GET /api/admin/audit-logs`
- 后台积分调整流水来源 `ADMIN_ADJUSTMENT`
- 后台操作日志记录积分调整和用户状态变更
- `point_transactions` 支持非观看奖励流水引用字段为空

本阶段不实现支付订单、不实现运营配置 UI、不实现复杂筛选分页、不实现多管理员账号和角色体系。后台日志先按创建时间倒序返回全量，后续再补分页和筛选。

## 架构

`points` 模块继续拥有积分账户和流水变更。新增 `PointsService.adjustByAdmin`，由 `admin` 模块调用，保证余额和流水仍在积分模块内维护。

`admin` 模块新增审计日志实体 `AdminAuditLog`。后台接口触发重要操作时写入审计日志，记录：

- 管理员用户名
- 操作类型
- 目标类型和目标 ID
- 摘要
- 创建时间

`AdminPrincipal` 由后台 Token 过滤器写入 Spring Security 上下文。控制器通过新的 `CurrentAdmin` 参数获取当前管理员用户名。

## 积分调整规则

- `amount` 不能为 `0`。
- 调整后余额不能小于 `0`。
- `reason` 必填，最长 255。
- 成功调整会生成一条 `point_transactions`：
  - `source = ADMIN_ADJUSTMENT`
  - `amount` 可正可负
  - `balanceAfter` 为调整后余额
  - `bookId / episodeNum / stage` 为空
  - `reason` 保存后台填写原因

## 审计日志规则

- 用户状态变更记录 `USER_STATUS_CHANGED`。
- 积分调整记录 `POINTS_ADJUSTED`。
- 审计日志失败不应被吞掉；如果日志无法写入，当前操作应失败，避免后台操作不可追溯。

## 测试

- 后台积分增加会更新余额、生成 `ADMIN_ADJUSTMENT` 流水并记录审计日志。
- 后台积分扣减不能让余额为负。
- `amount = 0` 或空白 `reason` 返回 `400`。
- 用户状态变更会记录审计日志。
- 普通 App Token 不能访问审计日志。
