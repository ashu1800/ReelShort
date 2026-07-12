# Security Hardening Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 修复全面代码审查确认的七项安全与发布可靠性问题，同时保持显式开发 profile 的本地便利性。

**Architecture:** 生产配置采用 fail-closed 校验，开发默认值只存在于 `app-dev`/测试 profile；部署网络、Android 会话、内容源出站请求、备份和发布脚本分别收紧边界。每个行为变更先添加能复现旧问题的失败测试，再写最小实现。

**Tech Stack:** Spring Boot 3/Java 21、Kotlin/AndroidX Security、Python/Flask/requests、Docker Compose、PowerShell、pytest、Gradle。

---

### Task 1: 后端生产密钥 fail-closed

**Files:**
- Modify: `backend/src/main/java/com/reelshort/backend/admin/AdminProperties.java`
- Modify: `backend/src/main/java/com/reelshort/backend/payment/PaymentProperties.java`
- Create: `backend/src/main/java/com/reelshort/backend/system/ProductionSecurityConfigurationValidator.java`
- Test: `backend/src/test/java/com/reelshort/backend/system/ProductionSecurityConfigurationValidatorTests.java`
- Modify: `backend/src/main/resources/application-app-dev.properties`
- Modify: `backend/src/test/resources/application.properties`

**Steps:**
1. 添加生产缺失/弱管理员哈希和支付密钥会失败、dev/test profile 放行的测试。
2. 运行目标测试并确认因缺少校验而失败。
3. 删除属性类固定默认值并实现 profile 感知的启动校验。
4. 运行目标测试和后端全量测试。

### Task 2: 部署网络和 internal API 边界

**Files:**
- Modify: `infra/docker-compose.yml`
- Create: `infra/docker-compose.local-debug.yml`
- Modify: `infra/.env.example`
- Modify: `admin-web/nginx.conf`
- Modify: `infra/README.md`
- Modify: `docs/deploy/README.md`

**Steps:**
1. 扩展部署静态验证，断言生产 Compose 不发布数据库端口、Nginx 拒绝 `/api/internal/`。
2. 运行静态测试并确认失败。
3. 删除生产端口映射，增加 loopback-only 调试 override，并增加 Nginx internal 拒绝规则。
4. 运行部署静态验证。

### Task 3: Android 会话存储 fail-closed

**Files:**
- Modify: `android-app/app/src/main/kotlin/com/reelshort/app/AndroidSessionStore.kt`
- Modify: `android-app/app/src/main/kotlin/com/reelshort/app/ReelShortViewModel.kt`
- Test: `android-app/app/src/test/kotlin/com/reelshort/app/AndroidSessionStoreContractTest.kt`

**Steps:**
1. 添加加密初始化失败时不使用明文 fallback、旧明文文件被删除的测试边界。
2. 运行目标测试并确认旧实现失败。
3. 实现内存 fail-closed store，移除生产组合根的 `FileSessionStore` fallback。
4. 运行 Android 单测。

### Task 4: 内容源安全 HTTP 客户端

**Files:**
- Modify: `content-provider/app.py`
- Test: `content-provider/tests/test_app.py`
- Modify: `content-provider/requirements.txt`（仅在确有必要时）

**Steps:**
1. 添加私网重定向、响应体超限、重定向上限和正常响应测试。
2. 逐个运行并确认旧实现失败。
3. 实现 URL/IP 校验、手动重定向、流式限制读取和总 deadline。
4. 运行内容源全部 pytest。

### Task 5: 加密配置备份

**Files:**
- Modify: `infra/scripts/backup.ps1`
- Modify: `infra/scripts/restore.ps1`
- Modify: `infra/scripts/validate-backup-scripts.ps1`
- Modify: `docs/deploy/backup-restore.md`

**Steps:**
1. 添加默认不复制 `.env`、显式配置备份产生加密文件的静态/脚本测试。
2. 运行测试并确认旧行为失败。
3. 使用 DPAPI 加密配置，收紧 ACL，并实现恢复解密。
4. 运行备份脚本验证测试。

### Task 6: 发布基线工作树完整性

**Files:**
- Modify: `scripts/verify-release-baseline.ps1`
- Modify: `scripts/tests/verify-release-baseline-tests.ps1`

**Steps:**
1. 添加 staged whitespace 与 untracked 文件导致失败的回归测试。
2. 运行脚本测试并确认失败。
3. 增加 `git diff --cached --check` 和未跟踪文件检查，并提供显式跳过开关。
4. 运行脚本测试。

### Task 7: 文档同步与完整验证

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/deploy/README.md`
- Modify: `docs/deploy/backup-restore.md`

**Steps:**
1. 更新模块描述和变更历史。
2. 运行 `scripts/verify-release-baseline.ps1 -SkipDiffCheck`。
3. 运行 `git diff --check`、`git diff --cached --check` 与工作树检查。
4. 审查最终 diff，按项目 Git 自动化流程暂存、fetch/rebase、提交和推送。

