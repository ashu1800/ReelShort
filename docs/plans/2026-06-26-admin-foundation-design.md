# Admin Foundation Design

## 目标

建立阶段 1 的后台管理基础闭环：管理员登录、后台 Token 鉴权、用户列表/详情、用户禁用/启用、用户观看记录查询和用户积分流水查询。后台能力只通过 `/api/admin/**` 暴露，与 App 用户接口隔离。

## 范围

本阶段实现：

- `POST /api/admin/auth/login`
- `GET /api/admin/users`
- `GET /api/admin/users/{userId}`
- `POST /api/admin/users/{userId}/status`
- `GET /api/admin/users/{userId}/watch-records`
- `GET /api/admin/users/{userId}/point-records`

本阶段不实现后台积分调整、运营配置、审计日志、内容缓存刷新和管理员账号管理界面。默认管理员账号从配置读取，适合当前单机早期部署；后续可迁移到数据库管理员表和审计模块。

## 架构

`admin` 模块拥有后台登录、后台当前用户上下文和后台查询编排。普通用户数据仍由 `user`、`watch`、`points` 模块持有，后台只通过 repository/service 查询，不改变 App API 的边界。

后台认证使用独立 Bearer Token，避免和普通 App Token 混用：

- 默认账号：`reelshort.admin.username`
- 默认密码哈希：`reelshort.admin.password-hash`
- Token 有效期：`reelshort.admin.token-ttl`

本设计实施当时由测试环境提供固定管理员密码 `Admin123`；当前该固定值仅允许显式 `app-dev` 和测试 profile 使用。生产环境必须通过环境变量提供独立 BCrypt 密码哈希，缺失或使用已知开发值时后端拒绝启动。

## 权限与错误语义

- `/api/admin/auth/login` 允许匿名访问。
- `/api/admin/**` 需要后台 Bearer Token。
- 普通 App Token 不能访问后台接口。
- 后台 Token 不能访问 `/api/app/**`。
- 用户不存在返回 `404`。
- 用户状态只允许 `ACTIVE` 或 `DISABLED`。

## 数据与接口

后台列表当前先返回全量用户，后续再补分页和筛选。列表项包含用户 ID、用户名、状态、创建时间、积分余额。用户详情增加观看记录数量和积分流水数量。

用户状态变更直接更新 `UserAccount.status`。禁用后，既有 App Token 在后续访问中会被安全过滤器拒绝。

## 测试

- 管理员登录成功返回后台 Token。
- 错误密码登录失败。
- 未登录访问后台用户列表返回 `401`。
- App Token 访问后台接口返回 `401` 或 `403`。
- 后台 Token 可查看用户列表和详情。
- 后台可禁用用户，禁用后该用户 App 访问失败。
- 后台可查询指定用户观看记录和积分流水，且不会泄露其他用户数据。
