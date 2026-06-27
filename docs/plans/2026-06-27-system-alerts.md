# System Alerts Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 增加后台异常告警模块，保存运行诊断异常并提供后台查看/确认能力。

**Architecture:** 后端新增 `backend/system/alerts` 子模块，基于运行诊断结果生成和恢复持久化告警；Admin API 通过 `SYSTEM_ALERT_READ`/`SYSTEM_ALERT_WRITE` 权限保护。后台 Web 新增异常告警页面，只访问 Spring Boot Admin API。

**Tech Stack:** Spring Boot、JPA、Flyway、JUnit/MockMvc、Vue 3、TypeScript、Element Plus。

---

### Task 1: 告警服务 TDD

**Files:**
- Create: `backend/src/test/java/com/reelshort/backend/system/SystemAlertServiceTests.java`

**Steps:**

1. 写失败测试：DOWN 依赖创建 OPEN 告警。
2. 写失败测试：重复 DOWN 更新 `lastSeenAt`，不重复插入。
3. 写失败测试：依赖恢复后告警转为 RESOLVED。
4. 运行 `.\gradlew.bat test --tests "*SystemAlertServiceTests" --no-daemon`，确认因类缺失失败。

### Task 2: 告警模型与迁移

**Files:**
- Create: `backend/src/main/java/com/reelshort/backend/system/alerts/SystemAlert.java`
- Create: `backend/src/main/java/com/reelshort/backend/system/alerts/SystemAlertStatus.java`
- Create: `backend/src/main/java/com/reelshort/backend/system/alerts/SystemAlertSeverity.java`
- Create: `backend/src/main/java/com/reelshort/backend/system/alerts/SystemAlertRepository.java`
- Create: `backend/src/main/java/com/reelshort/backend/system/alerts/SystemAlertResponse.java`
- Create: `backend/src/main/java/com/reelshort/backend/system/alerts/SystemAlertService.java`
- Create: `backend/src/main/resources/db/migration/V2__system_alerts.sql`

**Steps:**

1. 实体和 Flyway 表字段保持一致。
2. 实现基于 `SystemRuntimeResponse` 的评估逻辑。
3. 实现确认告警和列表查询。
4. 运行服务测试通过。

### Task 3: Admin API 与权限

**Files:**
- Create: `backend/src/main/java/com/reelshort/backend/system/alerts/SystemAlertController.java`
- Create: `backend/src/test/java/com/reelshort/backend/system/SystemAlertControllerTests.java`
- Modify: `backend/src/main/java/com/reelshort/backend/admin/AdminPermissions.java`
- Modify: `backend/src/main/java/com/reelshort/backend/admin/AdminRbacBootstrapService.java`

**Steps:**

1. 写失败测试：未登录查询告警返回 401。
2. 写失败测试：默认管理员可查询告警。
3. 写失败测试：缺少读权限返回 403。
4. 写失败测试：管理员可确认告警，并写入审计。
5. 实现 `GET /api/admin/system/alerts`、`POST /api/admin/system/alerts/evaluate`、`POST /api/admin/system/alerts/{alertId}/acknowledge`。

### Task 4: 后台 Web 页面

**Files:**
- Modify: `admin-web/src/services/adminApi.ts`
- Modify: `admin-web/src/router/index.ts`
- Modify: `admin-web/src/App.vue`
- Create: `admin-web/src/views/SystemAlertsView.vue`
- Modify: `admin-web/README.md`

**Steps:**

1. 增加告警类型和 API client 方法。
2. 增加 `/system-alerts` 路由和侧栏菜单。
3. 新增页面：状态筛选、刷新评估、确认告警。
4. 运行 `npm run build`。

### Task 5: 文档、审查、合并

**Files:**
- Modify: `docs/api/admin.md`
- Modify: `AGENTS.md`

**Steps:**

1. 补充 Admin API 文档和权限说明。
2. 更新 AGENTS 模块结构和变更历史。
3. 运行后端、前端、Android app-core、content-provider 回归验证。
4. 审查 diff，修复问题，再次审查。
5. 提交 `feat(system): add system alerts`。
6. 合并回 `master` 并清理 worktree。
