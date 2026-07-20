# Payout Balance Preflight Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 在提现签名和广播前校验三条链的 token/手续费余额，并阻止已在途申请被批量重复执行。

**Architecture:** 新增服务端余额预检服务，单笔和批量共用；批量先锁定执行权、读取所有申请并聚合余额，任何失败都不创建 attempt。前端和后端都按 payout attempt 状态过滤，但 coordinator 保留已有交易的幂等恢复能力。

**Tech Stack:** Java 17、Spring Boot、JUnit 5、Mockito、web3j、Trident、Vue 3、TypeScript、Gradle、Vite

---

### Task 1: 增加余额预检与 BSC 原生余额能力

**Files:**
- Create: `backend/src/main/java/com/reelshort/backend/withdrawal/PayoutBalancePreflightService.java`
- Modify: `backend/src/main/java/com/reelshort/backend/withdrawal/BscClient.java`
- Modify: `backend/src/main/java/com/reelshort/backend/withdrawal/WithdrawalService.java`

**Step 1: 写失败测试**

在 `WithdrawalAdminServiceTests` 增加 USDT 余额不足、原生币余额不足、批量按 network 聚合以及已在途 attempt 拒绝测试，断言 `payoutCoordinator.prepareAndBroadcast` 一次都不调用。

**Step 2: 验证 RED**

Run: `gradlew.bat test --tests "com.reelshort.backend.withdrawal.WithdrawalAdminServiceTests"`

Expected: 新增测试因当前服务没有余额预检而失败。

**Step 3: 写最小实现**

实现三条链的 token/native 查询、金额聚合、余额不足的 409 错误；在单笔和批量执行前调用；增加 BSC `getBnbBalance`。

**Step 4: 验证 GREEN**

Run: `gradlew.bat test --tests "com.reelshort.backend.withdrawal.WithdrawalAdminServiceTests"`

Expected: PASS。

### Task 2: 加固批量选择和并发执行

**Files:**
- Modify: `admin-web/src/views/WithdrawalsView.vue`
- Modify: `backend/src/main/java/com/reelshort/backend/withdrawal/WithdrawalService.java`
- Modify: `admin-web/tests/withdrawal-security.test.mjs`

**Step 1: 写失败测试**

增加前端源代码契约测试，要求 `PREPARED`、`BROADCASTED`、`MANUAL_REVIEW`、`CONFIRMED` 不可被批量选择；增加后端并发执行锁测试。

**Step 2: 验证 RED**

Run: `npm test`

Run: `gradlew.bat test --tests "com.reelshort.backend.withdrawal.WithdrawalAdminServiceTests"`

Expected: 新增断言失败。

**Step 3: 写最小实现**

前端统一使用 payout eligibility predicate；后端单笔/批量共享 `ReentrantLock`，批量预检前重新读取最新 attempt 状态，拒绝已在途状态。

**Step 4: 验证 GREEN**

Run: `npm test`

Run: `gradlew.bat test --tests "com.reelshort.backend.withdrawal.WithdrawalAdminServiceTests"`

Expected: PASS。

### Task 3: 文档与发布验证

**Files:**
- Modify: `docs/api/withdrawals.md`
- Modify: `AGENTS.md`

**Step 1: 更新安全边界**

记录 token/native 余额预检、批量全有或全无、手续费预算来源和在途状态过滤。

**Step 2: 完整验证**

Run: `gradlew.bat test --tests "com.reelshort.backend.withdrawal.*"`

Run: `gradlew.bat test`

Run: `gradlew.bat build`

Run: `npm test`

Run: `powershell -ExecutionPolicy Bypass -File scripts/verify-release-baseline.ps1`

### Task 4: 合并与部署

按项目 Git 自动化规范提交、推送、合并 `master`，在合并后重新运行后端测试；备份 PostgreSQL、旧 backend 源码和镜像，仅重建 backend，验证容器 health、API 和余额不足错误响应。部署前先为生产热钱包补足 USDT/TRX，部署后再让管理员执行两笔提现。
