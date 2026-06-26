# Admin RBAC Design

## Goal

补齐后台管理的持久化账号、角色和权限基础，让 Admin API 从“配置文件单管理员 + 已登录即可访问”升级为“数据库管理员账号 + 权限校验”。本阶段仍保持默认 `admin / Admin123` 可登录，避免破坏本地开发和已有测试。

## Current State

当前后台登录由 `AdminProperties` 提供单个用户名和密码哈希。登录成功后 `AdminToken` 只保存用户名，`AdminPrincipal` 只携带用户名，`/api/admin/**` 由 Spring Security 校验是否已认证，但没有接口级权限。架构文档已经要求 PostgreSQL 持久化 `admin_users`、`roles`、`permissions`、`admin_user_roles`，并要求后台权限和普通 App 用户权限隔离。

## Options

### Option A: 仅增加注解权限，不改账号模型

改动最小，但权限仍无法落到数据库账号和角色，后续做后台账号管理时会推翻认证模型。不采用。

### Option B: 一次性实现完整角色/账号管理 CRUD

功能完整，但会引入更多 API、表单、权限分配和审计流程，超过当前基础框架切片。暂不采用。

### Option C: 持久化 RBAC 基础

新增管理员、角色、权限和关系表；启动时引导默认超级管理员；登录从数据库读取管理员账号；Token 和 Principal 携带管理员 ID 与权限集合；后台接口通过注解做权限校验。暂不做后台账号/角色管理 CRUD。

本切片采用 Option C。

## Data Model

- `admin_users`：后台账号，字段包含 `id`、`username`、`passwordHash`、`status`、`createdAt`。
- `roles`：后台角色，字段包含 `id`、`code`、`name`、`createdAt`。
- `permissions`：后台权限，字段包含 `id`、`code`、`description`。
- `admin_user_roles`：后台账号与角色关系。
- `role_permissions`：角色与权限关系，用于从角色聚合权限。
- `admin_tokens`：增加 `adminUserId`，保留 `username` 用于兼容审计和响应。

默认引导：

- 创建 `SUPER_ADMIN` 角色。
- 创建后台接口所需权限。
- 将所有权限授予 `SUPER_ADMIN`。
- 使用 `AdminProperties` 中的默认用户名和密码哈希创建或补齐默认管理员账号，并绑定 `SUPER_ADMIN`。

## Permission Model

权限采用字符串代码，先覆盖现有后台接口：

- `USER_READ`
- `USER_WRITE`
- `POINTS_ADJUST`
- `AUDIT_READ`
- `CONTENT_CACHE_READ`
- `CONTENT_CACHE_WRITE`
- `SYSTEM_CONFIG_READ`
- `SYSTEM_CONFIG_WRITE`

接口通过 `@RequireAdminPermission` 标注所需权限。未登录仍返回 `401`；已登录但缺权限返回 `403 forbidden`。App Token 不能访问 Admin API，Admin Token 不能访问 App API 的隔离规则保持不变。

## Compatibility

- 默认 `admin / Admin123` 登录继续可用。
- 现有后台接口路径和响应结构不变。
- 审计日志继续记录 `adminUsername`，当前阶段不改变审计表结构。
- 旧的 `AdminProperties` 不再直接作为唯一账号来源，只作为默认管理员引导配置。

## Testing

- Repository/service 测试验证默认管理员、角色和权限引导。
- Service 测试验证登录使用持久化管理员账号。
- MVC 测试验证默认管理员仍能访问现有后台接口。
- MVC 测试验证缺少权限的管理员访问对应接口返回 `403`。
- 现有后台、认证、内容、观看、积分测试必须继续通过。

