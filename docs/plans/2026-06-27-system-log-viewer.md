# System Log Viewer Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 增加后台系统日志查看 API 和 Vue 后台日志页面。

**Architecture:** 后端在 `backend/system/logs` 下实现只读日志服务，严格限制读取配置日志目录内的 `.log` 文件，并通过 `SYSTEM_LOG_READ` 权限保护。后台 Web 通过 `/api/admin/system/logs` 拉取文件列表和最近日志行，不直接访问文件系统。

**Tech Stack:** Spring Boot、JUnit/MockMvc、Vue 3、TypeScript、Element Plus。

---

### Task 1: 后端日志服务测试

**Files:**
- Create: `backend/src/test/java/com/reelshort/backend/system/SystemLogServiceTests.java`

**Steps:**

1. 写失败测试：缺失日志目录返回空响应。
2. 写失败测试：读取允许 `.log` 文件最后 N 行。
3. 写失败测试：拒绝 `../secret.log`、`nested/app.log`、绝对路径和非 `.log` 文件。
4. 运行 `.\gradlew.bat test --tests "*SystemLogServiceTests" --no-daemon`，确认因类缺失失败。

### Task 2: 后端日志服务实现

**Files:**
- Create: `backend/src/main/java/com/reelshort/backend/system/logs/SystemLogProperties.java`
- Create: `backend/src/main/java/com/reelshort/backend/system/logs/SystemLogResponse.java`
- Create: `backend/src/main/java/com/reelshort/backend/system/logs/SystemLogService.java`
- Modify: `backend/src/main/resources/application.properties`

**Steps:**

1. 实现配置绑定，默认 root 为 `logs`，默认 max-lines 为 `500`。
2. 实现日志文件枚举和尾部行读取。
3. 实现文件名校验、normalize 边界校验和行数限制。
4. 运行 `.\gradlew.bat test --tests "*SystemLogServiceTests" --no-daemon`，确认通过。

### Task 3: 后端 Admin API 与权限

**Files:**
- Create: `backend/src/main/java/com/reelshort/backend/system/logs/SystemLogController.java`
- Create: `backend/src/test/java/com/reelshort/backend/system/SystemLogControllerTests.java`
- Modify: `backend/src/main/java/com/reelshort/backend/admin/AdminPermissions.java`
- Modify: `backend/src/main/java/com/reelshort/backend/admin/AdminRbacBootstrapService.java`
- Modify: `backend/src/test/java/com/reelshort/backend/admin/AdminRbacBootstrapServiceTests.java`

**Steps:**

1. 写失败测试：未登录访问 `/api/admin/system/logs` 返回 401。
2. 写失败测试：默认管理员可读取日志响应。
3. 写失败测试：缺少 `SYSTEM_LOG_READ` 权限返回 403。
4. 写失败测试：`AdminPermissions.ALL` 包含 `SYSTEM_LOG_READ`。
5. 实现 Controller、权限常量和 RBAC 描述。
6. 运行相关后端测试。

### Task 4: 后台 Web 页面

**Files:**
- Modify: `admin-web/src/services/adminApi.ts`
- Modify: `admin-web/src/router/index.ts`
- Modify: `admin-web/src/App.vue`
- Create: `admin-web/src/views/SystemLogsView.vue`
- Modify: `admin-web/README.md`

**Steps:**

1. 增加系统日志 API 类型和 `fetchSystemLogs` 方法。
2. 增加 `/system-logs` 路由。
3. 菜单增加“系统日志”入口。
4. 创建日志页面，支持文件选择、行数输入和刷新。
5. 运行 `npm run build`。

### Task 5: 文档和审查

**Files:**
- Modify: `docs/api/admin.md`
- Modify: `AGENTS.md`

**Steps:**

1. 在 Admin API 文档补充 `SYSTEM_LOG_READ` 和日志接口。
2. 更新 `AGENTS.md` 模块描述和变更历史。
3. 运行 `git diff --check`。
4. 运行后端、前端、content-provider、android app-core 回归验证。
5. 审查 diff，修复发现的问题，再次审查。
6. 提交 `feat(system): add system log viewer`。
7. 合并回 `master` 并清理 worktree。
