# 管理员 VIP 与密码策略 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 将 App 用户注册和改密的最低密码长度统一为 8 位，并允许有 `USER_WRITE` 权限的管理员设置未来 VIP 到期时间或取消当前 VIP。

**Architecture:** 复用 `users.vip_until` 作为唯一权益来源，不创建虚构 VIP 订单。后台新增用户权益操作接口，直接更新到期时间并写审计日志；管理端用户详情以日期时间选择和确认框调用接口。

**Tech Stack:** Spring Boot 3 / JPA / Flyway、Vue 3 + Element Plus、Kotlin + Jetpack Compose、JUnit 5、Vitest 契约测试。

---

### Task 1: 密码规则测试与实现

**Files:**
- Modify: `backend/src/main/java/com/reelshort/backend/auth/RegisterRequest.java`
- Modify: `backend/src/main/java/com/reelshort/backend/auth/PasswordChangeRequest.java`
- Modify: `backend/src/main/java/com/reelshort/backend/auth/AuthService.java`
- Modify: `backend/src/test/java/com/reelshort/backend/auth/AuthControllerTests.java`
- Modify: Android 注册/改密 UI 与契约测试

1. 先写 7 位注册密码和 7 位新密码返回 `400` 的后端测试，以及 Android 8 位启用条件测试。
2. 运行目标测试，确认因当前 6 位规则而失败。
3. 将 DTO、服务端校验和 Android 表单统一改为 8 位，并同步用户可见错误文案。
4. 重跑认证、Android 单元测试。

### Task 2: 管理员 VIP 权益接口

**Files:**
- Modify: `backend/src/main/java/com/reelshort/backend/admin/AdminUserController.java`
- Modify: `backend/src/main/java/com/reelshort/backend/admin/AdminUserService.java`
- Modify: 管理员用户响应 DTO 与用户实体
- Create: VIP 设置请求 DTO
- Modify: `backend/src/test/java/com/reelshort/backend/admin/AdminUserControllerTests.java`

1. 写 `USER_WRITE` 管理员可设置未来 `vipUntil`、可取消权益、无权限被拒绝、过去时间被拒绝、审计记录正确的失败测试。
2. 实现设置和取消接口；设置写入管理员提交的未来 ISO 时间，取消清空 `vipUntil`，不修改 VIP 订单。
3. 在用户列表和详情响应增加 `vip`、`vipUntil`，并验证现有 VIP 门禁读取更新后的权益。
4. 重跑后台控制器、RBAC 和 VIP 相关测试。

### Task 3: 后台与 Android 展示

**Files:**
- Modify: `admin-web/src/services/adminApi.ts`
- Modify: `admin-web/src/views/UsersView.vue`
- Modify: `admin-web/tests/*`
- Modify: Android 认证/改密界面和测试

1. 写后台契约测试，确认 VIP 操作发送日期时间或取消请求，并保留最终确认。
2. 在用户表和详情显示 VIP 状态/到期时间；用上海时区日期时间选择器设置到期时间，未来时间才可提交，取消有二次确认。
3. 复核 Android 不再允许 6-7 位注册或新密码提交。
4. 构建 admin-web，运行 Android 单元测试与 Debug APK 构建。

### Task 4: 文档、集成与验证

**Files:**
- Modify: `docs/api/auth-user.md`
- Modify: 管理员用户接口文档
- Modify: `AGENTS.md`

1. 记录密码最小长度和管理员 VIP 设置/取消接口、权限、审计及“不修改订单”规则。
2. 更新 `AGENTS.md` 模块描述与变更历史。
3. 运行 `git diff --check`、后端测试、admin-web 构建、Android `app-core`/Debug 单测及 APK 构建。
4. 提交、推送并在获准后部署；本次不执行任何链上付款操作。
