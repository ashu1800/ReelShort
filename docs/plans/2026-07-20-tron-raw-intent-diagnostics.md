# TRON Raw Intent Diagnostics Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 为 TRON 未签名交易意图校验增加一次安全重建和字段级原因码，使瞬时节点异常可自愈、稳定异常可定位。

**Architecture:** `TronClient` 使用内部专用异常承载安全原因码，`prepareTransfer` 只对该异常重新请求一次未签名交易。所有响应仍必须通过现有严格字段校验后才能签名，第二次失败将原因码返回后台。

**Tech Stack:** Java 17、Spring Boot、Trident protobuf、JUnit 5、JDK HttpServer、Gradle

---

### Task 1: 定义重建与原因码契约

**Files:**
- Modify: `backend/src/test/java/com/reelshort/backend/withdrawal/TronClientTests.java`

**Step 1: 写失败测试**

- 新增测试：首次返回 recipient 不匹配交易，第二次返回正确交易，断言请求两次且成功。
- 新增测试：连续两次 amount 不匹配，断言请求两次且错误包含 `amount`。
- 将现有 owner、合约、TRC10 附加值测试补充对应原因码断言。

**Step 2: 验证 RED**

Run: `gradlew.bat test --tests "com.reelshort.backend.withdrawal.TronClientTests"`

Expected: 新增测试因当前不重试且错误无原因码而失败。

### Task 2: 实现专用异常与单次重建

**Files:**
- Modify: `backend/src/main/java/com/reelshort/backend/withdrawal/TronClient.java`

**Step 1: 写最小实现**

- 添加内部 `RawIntentMismatchException`，继承 `WithdrawalException` 并携带原因码。
- 拆分各校验条件，按失败字段抛出专用异常。
- 提取单次未签名交易构建逻辑；首次专用异常只记录原因码并重建一次，第二次直接抛出。

**Step 2: 验证 GREEN**

Run: `gradlew.bat test --tests "com.reelshort.backend.withdrawal.TronClientTests"`

Expected: PASS。

### Task 3: 同步提现文档

**Files:**
- Modify: `docs/api/withdrawals.md`
- Modify: `AGENTS.md`

**Step 1: 更新安全边界**

记录 TRON 原始交易首次不匹配时只重建一次、第二次返回安全原因码，所有字段仍严格校验。

**Step 2: 更新变更历史和模块描述**

在 `AGENTS.md` 变更历史顶部追加本次修复，并同步 `backend/withdrawal` 描述。

### Task 4: 完整验证、版本控制和部署

**Files:**
- Verify: `backend`
- Deploy: `infra`

**Step 1: 运行验证**

Run: `gradlew.bat test --tests "com.reelshort.backend.withdrawal.*"`

Run: `gradlew.bat test`

Run: `gradlew.bat build`

Run: `powershell -ExecutionPolicy Bypass -File scripts/verify-release-baseline.ps1`

Expected: 全部成功。

**Step 2: 提交并合并**

按项目 Git 自动化规范暂存、同步、提交、推送功能分支，合并回 `master` 后再次验证并推送。

**Step 3: 备份并部署 backend**

备份生产 PostgreSQL，构建新 backend，保留上一源码和镜像回滚点，切换后等待 healthy。

**Step 4: 线上验证**

确认部署提交、容器健康、公网健康接口和现有两笔提现仍未广播。由管理员使用原私钥与新 TOTP 重试，成功则确认生成 tx hash；若仍失败，后台应显示具体原因码。
