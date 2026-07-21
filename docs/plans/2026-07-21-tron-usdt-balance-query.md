# TRON USDT Balance Query Fix Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Make payout preflight read the authoritative TRC20 USDT contract balance and never convert an incomplete node response into a zero balance.

**Architecture:** Replace `TronClient.getUsdtBalance` account metadata parsing with a `triggerconstantcontract` call to `balanceOf(address)`. Reuse the existing Base58Check-to-ABI address helper, decode the returned hexadecimal uint256, and fail closed on node or response-shape errors.

**Tech Stack:** Java 17, Spring Boot, Jackson, Java HTTP Client, JUnit 5, AssertJ, Flyway-managed production deployment.

---

### Task 1: Query TRC20 USDT through `balanceOf`

**Files:**
- Modify: `backend/src/test/java/com/reelshort/backend/withdrawal/TronClientTests.java`
- Modify: `backend/src/main/java/com/reelshort/backend/withdrawal/TronClient.java`

**Step 1: Write the failing HTTP request/response test**

Add a test server endpoint for `/wallet/triggerconstantcontract`. Return:

```json
{"result":{"result":true},"constant_result":["0000000000000000000000000000000000000000000000000000000001312d00"]}
```

Call `getUsdtBalance(DESTINATION)` and assert it returns exactly `20.000000`. Parse the captured request and assert:

```java
assertThat(body.path("function_selector").asText()).isEqualTo("balanceOf(address)");
assertThat(body.path("parameter").asText())
        .isEqualTo(bytesToHex(abiAddress(DESTINATION)));
```

**Step 2: Write the failing incomplete-response test**

Return `{"result":{"result":true}}` without `constant_result` and assert `getUsdtBalance` throws `WithdrawalException` with `failed to query USDT balance`, rather than returning zero.

**Step 3: Verify red**

Run:

```powershell
backend\gradlew.bat test --tests "com.reelshort.backend.withdrawal.TronClientTests"
```

Expected: FAIL because the current implementation calls `/wallet/getaccount` and treats missing `trc20` as zero.

**Step 4: Implement the minimal contract query**

Replace `getUsdtBalance` with a `triggerconstantcontract` request:

```java
JsonNode response = postJson(properties.getNodeUrl() + "/wallet/triggerconstantcontract", Map.of(
        "owner_address", address,
        "contract_address", properties.getUsdtContract(),
        "function_selector", "balanceOf(address)",
        "parameter", bytesToHex(abiAddress(address)),
        "visible", true));
```

Require a successful node result and a nonblank first `constant_result`, parse it as unsigned hexadecimal `BigInteger`, then divide by `USDT_DECIMALS` with scale 6. Throw a 503 `WithdrawalException` for missing, malformed, or failed results.

**Step 5: Verify green**

Run the focused `TronClientTests` command again. Expected: PASS.

**Step 6: Commit**

```powershell
git add backend/src/main/java/com/reelshort/backend/withdrawal/TronClient.java backend/src/test/java/com/reelshort/backend/withdrawal/TronClientTests.java
git commit -m "fix(withdrawal): query TRON USDT contract balance"
```

### Task 2: Document, verify, integrate, and deploy

**Files:**
- Modify: `docs/api/withdrawals.md`

**Step 1: Update the withdrawal documentation**

Document that TRC20 payout preflight reads `balanceOf(address)` directly and treats incomplete node responses as query failures. No `AGENTS.md` update is required because this is a pure bug fix without an API, schema, dependency, or module-boundary change.

**Step 2: Run full verification**

Run:

```powershell
backend\gradlew.bat test --no-daemon
backend\gradlew.bat build
powershell -ExecutionPolicy Bypass -File scripts\verify-release-baseline.ps1
```

Expected: all commands exit 0. Existing frontend build-size and dependency annotation warnings are allowed.

**Step 3: Commit documentation and synchronize Git**

Fetch the feature branch, rebase only if behind, commit documentation, push the feature branch, fast-forward `master`, and push `master`.

**Step 4: Deploy production**

Create a fresh PostgreSQL/source/config backup, upload the merged Git archive without overwriting `infra/.env`, build the backend image, retain the current image/source rollback boundary, and recreate only the backend container.

**Step 5: Verify the production symptom**

Confirm:

- backend is healthy and `DEPLOYED_COMMIT` matches the merged commit;
- the production `balanceOf(address)` returns 20 USDT for the derived hot-wallet address;
- the backend image contains the `balanceOf(address)` query implementation;
- the next preflight no longer reports `TRC20 USDT current balance 0` (the separate TRX fee-limit check may still reject the payout until the wallet has sufficient TRX);
- recent backend logs contain no startup errors.
