# Auth/User Foundation Design

## 目标

建立阶段 1 的普通用户账号基础闭环：用户注册、用户登录、密码哈希存储、用户状态校验和统一 API 响应。该模块为后续观看记录、积分账户、App 接口鉴权和用户禁用能力提供稳定边界。

## 范围

本阶段实现 App 侧普通用户账号能力：

- `POST /api/app/auth/register`
- `POST /api/app/auth/login`
- 用户唯一用户名约束
- 密码使用 BCrypt 哈希保存
- 用户状态包含 `ACTIVE` 和 `DISABLED`
- 禁用用户登录返回 `403`
- 登录成功返回访问令牌结构

本阶段不实现完整 JWT 验签过滤器、管理员账号、角色权限和刷新令牌。这些能力会在后续 `auth/security` 与 `admin` 子模块中扩展。

## 架构

`auth` 模块负责认证用例、密码校验和 Token 签发边界。`user` 模块负责用户实体、用户状态和用户仓储。控制器只处理请求/响应转换，不直接操作仓储。

本阶段使用 JPA 建立持久化边界，并引入 H2 作为测试运行时数据库。长期部署仍以 PostgreSQL 为准，后续数据库迁移脚本在数据模型稳定后补齐。

## 组件

- `UserAccount`：用户实体，保存用户名、密码哈希、状态和创建时间。
- `UserStatus`：用户状态枚举。
- `UserAccountRepository`：用户仓储，提供用户名查找和唯一性判断。
- `PasswordHasher`：密码哈希接口。
- `BCryptPasswordHasher`：BCrypt 实现。
- `TokenService`：访问令牌签发接口。
- `OpaqueTokenService`：第一阶段临时不透明 Token 实现，后续可替换为 JWT。
- `AuthService`：注册和登录业务用例。
- `AuthController`：App Auth API 入口。

## 数据流

注册请求进入 `AuthController` 后由 `AuthService` 检查用户名唯一性，哈希密码，创建 `ACTIVE` 用户并返回 Token。登录请求根据用户名加载用户，校验状态和密码，成功后返回 Token。

错误分层：

- 用户名已存在：`409`
- 用户名或密码错误：`401`
- 用户禁用：`403`
- 请求参数缺失或空白：`400`

## 测试

优先用集成测试覆盖外部 API 行为，用服务层测试覆盖密码哈希与状态分支：

- 注册成功返回统一响应和 Token。
- 注册重复用户名返回 `409`。
- 登录成功返回 Token。
- 错误密码返回 `401`。
- 禁用用户登录返回 `403`。
- 密码不会明文保存。
