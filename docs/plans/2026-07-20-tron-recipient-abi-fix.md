# TRON Recipient ABI Encoding Fix Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 修正 TRC20 transfer 请求中的收款地址 ABI 编码，使节点构建交易与提现意图一致。

**Architecture:** `TronClient` 复用现有 Base58Check 校验和 ABI 地址编码逻辑，避免在请求编码和响应校验间维护两套实现。测试捕获真实 HTTP 请求体并核对完整 `parameter`，覆盖此前测试未验证的节点输入边界。

**Tech Stack:** Java 17、Spring Boot、JUnit 5、JDK HttpServer、AssertJ、Gradle

---

### Task 1: 添加请求参数回归测试

**Files:**
- Modify: `backend/src/test/java/com/reelshort/backend/withdrawal/TronClientTests.java`

**Step 1: 捕获 trigger 请求体**

在测试 HTTP server 的 `/wallet/triggersmartcontract` handler 中保存请求 JSON，同时继续返回合法未签名交易。

**Step 2: 写失败测试**

调用 `prepareTransfer(PRIVATE_KEY, DESTINATION, AMOUNT)`，断言请求 `parameter` 等于 `abiAddress(DESTINATION) + uint256(AMOUNT)`。

**Step 3: 验证 RED**

Run: `gradlew.bat test --tests "com.reelshort.backend.withdrawal.TronClientTests.encodesRecipientWithoutBase58Checksum"`

Expected: FAIL，实际地址 word 包含 Base58 checksum。

### Task 2: 修正 ABI 编码

**Files:**
- Modify: `backend/src/main/java/com/reelshort/backend/withdrawal/TronClient.java`

**Step 1: 写最小实现**

将 `encodeTransferParams` 的地址编码替换为 `abiAddress(toBase58Address)`，金额编码保持不变。

**Step 2: 验证 GREEN**

Run: `gradlew.bat test --tests "com.reelshort.backend.withdrawal.TronClientTests"`

Expected: PASS。

### Task 3: 同步文档

**Files:**
- Modify: `docs/api/withdrawals.md`
- Modify: `AGENTS.md`

**Step 1: 记录编码边界**

说明 TRC20 请求参数只使用 Base58Check payload 中去掉网络前缀后的 20 字节地址。

**Step 2: 更新变更历史**

在 `AGENTS.md` 顶部追加本次修复记录。纯 Bug 修复本可跳过，但该问题已生产复现，需保留发布审计线索。

### Task 4: 验证、合并和部署

**Step 1: 本地验证**

Run: `gradlew.bat test --tests "com.reelshort.backend.withdrawal.*"`

Run: `gradlew.bat test`

Run: `gradlew.bat build`

Run: `powershell -ExecutionPolicy Bypass -File scripts/verify-release-baseline.ps1`

Expected: 全部成功。

**Step 2: Git 自动化**

按 `AGENTS.md` 暂存、同步、提交、推送功能分支，合并到 `master` 后重新运行后端测试并推送。

**Step 3: 生产部署**

备份 PostgreSQL、当前 backend 源码和当前镜像，只重建 backend；等待容器 healthy，确认公网 API、内部 health、日志和两笔 `SIGNING` attempt 无 `tx_hash`。

**Step 4: 业务重试**

管理员使用原私钥和新 TOTP 重试。成功标准是生成 `tx_hash` 并进入 `BROADCASTED`；任何再次失败都保留具体安全原因码且不得放宽校验。
