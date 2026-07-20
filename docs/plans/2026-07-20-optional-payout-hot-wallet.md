# Optional Payout Hot-Wallet Address Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 允许未配置预期热钱包公开地址的生产环境使用管理员临时提交的私钥执行提现，同时保留已配置地址时的严格匹配校验。

**Architecture:** 保留三条链现有的私钥派生地址与统一校验调用，只调整统一校验函数为空配置时直接返回。测试直接覆盖 Coordinator 的 reserve 边界，证明空配置继续执行、错误配置仍在签名前拒绝。

**Tech Stack:** Java 17、Spring Boot、JUnit 5、Mockito、Gradle、Docker Compose

---

### Task 1: 用失败测试定义可选配置行为

**Files:**
- Modify: `backend/src/test/java/com/reelshort/backend/withdrawal/WithdrawalPayoutCoordinatorTests.java`

**Step 1: 写失败测试**

将 ERC20 与 BEP20 空配置测试改为期望调用对应 `reserve` 方法，并新增 TRC20 空配置测试。为每条链准备最小 request、派生地址、reserve 结果及后续签名/广播桩。

**Step 2: 验证测试按预期失败**

Run: `gradlew.bat test --tests "com.reelshort.backend.withdrawal.WithdrawalPayoutCoordinatorTests"`

Expected: FAIL，错误为 `expected hot wallet address is not configured`。

### Task 2: 实现配置存在时才校验

**Files:**
- Modify: `backend/src/main/java/com/reelshort/backend/withdrawal/WithdrawalPayoutCoordinator.java`

**Step 1: 写最小实现**

在统一地址校验方法中，当配置为 `null` 或空白时直接返回；非空时保持现有大小写规则与不匹配异常。

**Step 2: 验证目标测试通过**

Run: `gradlew.bat test --tests "com.reelshort.backend.withdrawal.WithdrawalPayoutCoordinatorTests"`

Expected: PASS。

### Task 3: 同步配置文档

**Files:**
- Modify: `.env.example`
- Modify: `AGENTS.md`

**Step 1: 更新说明**

标明三条链的预期热钱包公开地址均为可选；补齐 BSC 示例配置，并说明配置后才执行匹配校验。

**Step 2: 更新项目变更历史**

在 `AGENTS.md` 变更历史顶部记录提现公开地址由必填改为可选校验，不改变私钥临时提交及签名流程。

### Task 4: 完整验证、提交和部署

**Files:**
- Verify: `backend`
- Deploy: `infra/docker-compose.yml`

**Step 1: 运行完整验证**

Run: `gradlew.bat test`

Run: `gradlew.bat build`

Expected: 两条命令均成功。

**Step 2: 执行 Git 自动化流程**

依次执行 `git add .`、`git fetch origin master`、必要时 `git pull --rebase`、规范提交、`git push origin master`。

**Step 3: 备份并部署**

先备份生产 PostgreSQL，再构建并切换 backend；保留上一版本回滚目录。

**Step 4: 线上验证**

确认 backend 与依赖容器 healthy、`/api/system/health` 返回成功，并确认运行容器在热钱包地址为空时已加载新版本。
