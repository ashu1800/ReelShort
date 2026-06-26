# Auth Security Foundation Design

## 目标

把阶段 1 已返回的 App 登录 Token 接入后端鉴权链路，建立普通用户请求上下文，并保护 App 业务接口。后续观看记录、积分奖励和用户禁用访问控制都依赖这个身份边界。

## 范围

本阶段实现：

- 登录和注册时签发可持久化校验的访问令牌。
- `Authorization: Bearer <token>` 鉴权。
- 当前用户上下文 `CurrentUser`。
- 禁用用户不能继续访问 App 业务接口。
- `/api/app/auth/register`、`/api/app/auth/login`、`/api/system/health`、`/actuator/health` 公开访问。
- `/api/app/content/**` 等 App 业务接口需要普通用户 Token。

本阶段不实现刷新令牌、Token 过期清理任务、多设备会话管理、管理员鉴权和细粒度权限。这些能力后续分别在 `auth/session` 和 `admin/security` 中扩展。

## 架构

`auth` 模块新增 `AccessToken` 持久化模型和仓储。`TokenService` 签发原始 Token 时只保存哈希值，避免数据库泄漏后直接使用 Token。请求进入 `BearerTokenAuthenticationFilter` 后，从请求头提取 Token，哈希后查找有效 Token 和用户状态，再设置 Spring Security `Authentication`。

控制器需要当前用户时通过 `CurrentUserResolver` 获取认证主体。本阶段先在 `ContentController` 注入 `CurrentUser` 参数，以验证 App 内容 API 已经经过业务层身份上下文。

## 错误语义

- 未携带 Token：`401`
- Token 无效：`401`
- Token 对应用户已禁用：`403`
- 已认证请求参数错误：继续返回已有 `400`

所有错误仍使用统一 `ApiErrorResponse`。

## 测试

- 未登录访问内容搜索返回 `401`。
- 使用注册返回的 Token 访问内容搜索成功。
- 使用无效 Token 返回 `401`。
- 禁用用户 Token 访问内容搜索返回 `403`。
- 注册/登录和健康检查保持公开访问。
- Token 数据库只保存哈希，不保存原始 Token。
