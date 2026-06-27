# Auth Session Lifecycle Design

## Goal

补齐 App 侧 Token 生命周期：登录/注册签发的 Bearer Token 有明确过期时间，用户可以登出撤销当前 Token，后端定期清理过期或已撤销 Token。该模块把 `docs/api/auth-security.md` 中“后续补齐”的会话管理能力落到现有 `auth` 边界内。

## Context

当前 `AccessToken` 只保存 `tokenHash`、用户和 `issuedAt`。`BearerTokenAuthenticationFilter` 只验证 Token 是否存在和用户是否禁用。这样 Token 永不过期，用户无法主动撤销，长期运行后 `access_tokens` 表也会持续增长。

## Options

推荐方案：在现有不透明 Token 模型上增加 `expiresAt` 和 `revokedAt`。保持 `AuthToken` 响应结构不变，避免影响 Android App 和后台 Web；默认有效期用配置控制，先设为 7 天。新增 `POST /api/app/auth/logout`，从当前 Authorization header 定位 Token 并撤销。清理任务删除过期或已撤销 Token。

备选方案 1：改成 JWT 并只校验签名和过期时间。客户端无状态，但用户登出撤销需要黑名单或版本号，当前单机架构反而更复杂。

备选方案 2：引入 refresh token 和多设备会话管理。能力更完整，但第一步范围过大，会牵涉 App 会话 UI 和安全策略。

## Design

- `AccessToken` 新增：
  - `expiresAt`：签发时按配置有效期计算。
  - `revokedAt`：登出时写入，非空即无效。
- `AuthSessionProperties` 提供：
  - `reelshort.auth.session.access-token-ttl`，默认 `7d`。
  - `reelshort.auth.session.cleanup-retention`，默认 `1d`，避免刚撤销的记录马上消失。
  - `reelshort.auth.session.cleanup-initial-delay` 和 `reelshort.auth.session.cleanup-interval` 控制定时清理节奏，默认均为 `1h`。
- `TokenService` 扩展：
  - `issue(UserAccount user)`：按 TTL 签发。
  - `revoke(String rawToken)`：撤销当前 Token。
- `BearerTokenAuthenticationFilter`：
  - Token 不存在：`invalid token`。
  - Token 已过期：`token expired`。
  - Token 已撤销：`token revoked`。
  - 用户禁用仍返回 `user disabled`。
- `AuthController` 新增 `POST /api/app/auth/logout`，需要 Bearer Token；撤销成功返回统一成功响应。
- `AuthSessionCleanupService` 使用 `@Scheduled` 调用 `AccessTokenRepository.deleteExpiredOrRevokedBefore(...)`，定期删除过期或撤销并超过保留时间的 Token。

## Test Plan

- Repository 测试覆盖 `expiresAt` 持久化、按 hash 查询和清理删除。
- Service 测试覆盖签发过期时间、撤销 Token、撤销不存在 Token 不泄露信息。
- Filter/Controller 测试覆盖已过期、已撤销、禁用用户和 logout 后访问失败。
- 配置测试覆盖默认 TTL 和 cleanup retention。
- 全量后端测试验证现有注册、登录和受保护接口行为不回归。

## Out of Scope

- 不实现 refresh token。
- 不实现多设备会话列表。
- 不切换到 JWT。
- 不引入 Redis Token 黑名单；Token 真相仍在 PostgreSQL。
