# System Config Design

## 目标

建立后台运营配置基础能力：系统配置以持久化键值形式保存，后台可查询和更新允许运营调整的配置。第一步接入积分奖励规则，让观看阶段奖励积分不再写死在代码里。

## 范围

本阶段实现：

- `GET /api/admin/system/configs`
- `POST /api/admin/system/configs/{configKey}`
- 系统配置表 `system_configs`
- 配置更新审计日志 `SYSTEM_CONFIG_UPDATED`
- 积分观看阶段奖励配置 `points.watch.stage-points`
- 推荐配置占位 `content.recommendation.strategy`

本阶段不实现复杂配置类型系统、不实现前端配置页面、不实现配置版本回滚、不实现 Redis 配置缓存。配置读取直接走数据库；当前单机部署和配置规模下足够简单。

## 配置模型

`SystemConfig` 字段：

- `key`：配置键，主键。
- `value`：字符串值。
- `description`：配置说明。
- `updatedAt`：更新时间。

配置键白名单由代码定义，避免后台写入任意未知配置：

- `points.watch.stage-points`：整数，范围 `0..1000`，默认 `1`。
- `content.recommendation.strategy`：字符串枚举，允许 `LATEST` / `POPULAR`，默认 `LATEST`。

## 积分规则接入

`PointsService` 发放观看阶段奖励时读取 `points.watch.stage-points`。如果配置不存在，自动使用默认值并可在配置查询中返回默认值。

阶段奖励为 `0` 时，后端仍记录阶段领取记录，但不增加余额、不生成金额为 0 的积分流水。这样可以临时关闭观看奖励，同时避免关闭期间重复上报在恢复奖励后补发旧阶段。

## 审计

后台更新配置时写入 `AdminAuditLog`：

- `action = SYSTEM_CONFIG_UPDATED`
- `targetType = SYSTEM_CONFIG`
- `targetId` 使用稳定 UUID，由配置键派生。
- `summary` 记录配置键和新值。

## 测试

- 管理员可查询默认配置。
- 管理员可更新 `points.watch.stage-points`，后续观看奖励按新值发放。
- 非法配置键返回 `404`。
- 非法配置值返回 `400`。
- 配置更新写入审计日志。
- App Token 不能访问后台配置接口。
