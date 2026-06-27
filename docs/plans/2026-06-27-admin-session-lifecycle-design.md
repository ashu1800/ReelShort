# Admin Session Lifecycle Design

## Goal

补齐后台管理员 Token 生命周期：后台 Token 支持服务端登出撤销、撤销后拒绝访问、过期/撤销记录定期清理，并让后台 Web 的退出操作调用后端登出接口。

## Context

当前后台登录会签发 `admin_tokens` 记录，包含 `tokenHash`、`adminUserId`、`username`、`issuedAt` 和 `expiresAt`。`AdminBearerTokenAuthenticationFilter` 已校验 Token 是否过期、管理员是否存在和管理员状态，但 Token 没有 `revokedAt`，后台退出只清空浏览器本地会话，服务端 Token 在过期前仍可继续使用。

App Token 已完成过期、登出撤销和清理。后台 Token 应具备同等级生命周期，保持普通用户和管理员认证边界一致。

## Options

推荐方案：延续现有不透明后台 Token 模型，在 `AdminToken` 上增加 `revokedAt`，新增 `POST /api/admin/auth/logout` 撤销当前后台 Token，并新增 `AdminSessionCleanupService` 定时删除过期或撤销且超过保留期的后台 Token。后台 Web 退出按钮先调用 logout，失败也清理本地会话。

备选方案 1：只修改后台 Web 清理本地 Token。不改后端，开发最少，但服务端 Token 仍可复用，不能解决实际安全问题。

备选方案 2：将后台 Token 改为 JWT 并引入 blacklist。能力可扩展，但与当前 PostgreSQL 单机真相模型冲突，且为登出增加 Redis 依赖，不符合当前模块化单体的简单边界。

## Design

- `AdminToken` 新增：
  - `revokedAt`：后台登出时写入；非空即无效。
  - `revoke(OffsetDateTime)`、`isRevoked()`、`isExpired(OffsetDateTime)`。
- `AdminTokenRepository` 新增：
  - `deleteExpiredOrRevokedBefore(OffsetDateTime cutoff)`。
- `AdminAuthService` 新增：
  - `logout(String rawToken)`：按 hash 查找后台 Token，存在且未撤销时写入 `revokedAt`；不存在不抛错、不创建记录。
- `AdminBearerTokenAuthenticationFilter`：
  - Token 不存在：`invalid token`。
  - Token 已撤销：`token revoked`。
  - Token 已过期：`token expired`。
  - 后台账号禁用：`admin disabled`。
- `AdminAuthController` 新增：
  - `POST /api/admin/auth/logout`，需要后台 Bearer Token；撤销成功返回统一成功响应。
- `SecurityConfig`：
  - `POST /api/admin/auth/login` 继续公开。
  - `POST /api/admin/auth/logout` 不加入公开列表，继续受 `/api/admin/**` 鉴权保护。
  - 后台撤销/过期 Token 返回 `401` 和明确 message。
- `AdminSessionProperties`：
  - `reelshort.admin.session.cleanup-retention`，默认 `1d`。
  - `reelshort.admin.session.cleanup-initial-delay`，默认 `1h`。
  - `reelshort.admin.session.cleanup-interval`，默认 `1h`。
- `AdminSessionCleanupService`：
  - 使用 `@Scheduled` 定期删除过期或撤销并超过保留时间的后台 Token。
- `admin-web`：
  - 新增 `logout` API 调用。
  - 顶部退出按钮调用后端 logout；无论成功或失败，都清理本地会话并返回登录页。

## Test Plan

- Repository 测试覆盖 `revokedAt` 持久化和按 cutoff 清理过期/撤销 Token。
- Service 测试覆盖后台 Token 撤销、撤销不存在 Token 不泄露信息。
- MockMvc 测试覆盖后台 logout、logout 后访问失败、撤销 Token 返回 `token revoked`、过期 Token 返回 `token expired`。
- 清理服务测试覆盖只删除超过 retention 的过期或撤销 Token。
- Admin Web 构建验证 TypeScript/API 调用无错误。
- 全量后端测试验证现有后台登录、RBAC、用户/订单/支付/配置接口不回归。

## Out of Scope

- 不实现后台 refresh token。
- 不实现后台多设备会话列表。
- 不切换到 JWT。
- 不引入 Redis Token blacklist。
