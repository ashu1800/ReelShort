# TRON Dynamic Fee Preflight Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace TRC20 execution preflight's `feeLimit * count` balance requirement with a live resource-aware estimate while retaining the transaction fee limit as a hard cap.

**Architecture:** Add read-only fee-estimation methods to `TronClient` that simulate exact transfers, query chain pricing and account resources, and return an integer-sun estimate with a 20% margin. Update `PayoutBalancePreflightService` final execution validation to use that quote; keep no-private-key batch preview unchanged as a clearly labelled maximum.

**Tech Stack:** Java 17, Spring Boot, Jackson, Java HTTP Client, JUnit 5, AssertJ, Mockito, Gradle.

---

### Task 1: Add Resource-Aware TRON Fee Quote

**Files:**
- Modify: `backend/src/test/java/com/reelshort/backend/withdrawal/TronClientTests.java`
- Modify: `backend/src/main/java/com/reelshort/backend/withdrawal/TronClient.java`
- Create: `backend/src/main/java/com/reelshort/backend/withdrawal/TronFeeQuote.java`

**Step 1: Write failing HTTP tests**

Add tests that serve and capture:

- `/wallet/triggerconstantcontract` with `energy_used=130285` for each exact transfer;
- `/wallet/getchainparameters` with `getEnergyFee=100` and `getTransactionFee=1000`;
- `/wallet/getaccountresource` with explicit Energy and bandwidth limits/usage.

Assert the simulator sends `transfer(address,uint256)` with the exact ABI recipient and raw six-decimal amount. Assert two transfers with no available Energy cost `31.269000 TRX` after the 20% margin, plus any configured chargeable bandwidth. Add a case where existing Energy reduces the required TRX.

Add fail-closed tests for missing `energy_used`, unsuccessful simulation, missing chain prices and invalid account resource values. Add a test rejecting a per-item simulated charge above the configured `feeLimit`.

**Step 2: Verify red**

Run:

```powershell
.\gradlew.bat test --tests "com.reelshort.backend.withdrawal.TronClientTests"
```

Expected: compilation failure because `estimateTransferFees`/`TronFeeQuote` do not exist.

**Step 3: Implement minimal quote logic**

Create an immutable quote record containing required TRX, total energy, available Energy and the margin percentage. Add a public `estimateTransferFees(ownerAddress, withdrawals)` method to `TronClient`.

Reuse the existing transfer ABI encoder for each withdrawal. Require successful constant simulation and a non-negative `energy_used`. Query chain parameters and account resources, calculate chargeable Energy and bandwidth with `BigInteger`, apply a 120/100 margin with integer ceiling, and convert sun to a six-decimal `BigDecimal`.

Keep all RPC failures wrapped as `WithdrawalException(503, "failed to estimate TRON payout fee: ...")`. Reject a single simulated energy charge above `properties.getFeeLimit()`.

**Step 4: Verify green**

Run the focused `TronClientTests` command and confirm all tests pass.

**Step 5: Commit**

```powershell
git add backend/src/main/java/com/reelshort/backend/withdrawal backend/src/test/java/com/reelshort/backend/withdrawal/TronClientTests.java
git commit -m "feat(withdrawal): estimate TRON payout resource fees"
```

### Task 2: Use Dynamic Quote During Final Preflight

**Files:**
- Modify: `backend/src/test/java/com/reelshort/backend/withdrawal/PayoutBalancePreflightServiceTests.java`
- Modify: `backend/src/main/java/com/reelshort/backend/withdrawal/PayoutBalancePreflightService.java`

**Step 1: Write failing service tests**

For two TRC20 withdrawals, mock `tronClient.estimateTransferFees` to return `31.269000 TRX` and mock a `19.969158 TRX` balance. Assert final preflight fails with:

```text
TRX 预计手续费余额不足：本次预计需要 31.269，当前余额 19.969158，还差 11.299842
```

Add the inverse case showing a wallet with 32 TRX passes even though `feeLimit * count` is 200 TRX. Verify the exact withdrawal list is supplied to the quote method.

Keep the existing preview test asserting two TRC20 items report `200 TRX`, `estimateType=MAXIMUM` when the configured fee limit is 100 TRX.

**Step 2: Verify red**

Run:

```powershell
.\gradlew.bat test --tests "com.reelshort.backend.withdrawal.PayoutBalancePreflightServiceTests"
```

Expected: FAIL because final preflight still compares against `feeLimit * count`.

**Step 3: Implement minimal service change**

For the TRC20 final execution branch, call the new quote using the derived owner and exact withdrawals in that network. Compare the quote amount with `getTrxBalance`. Allow the balance error helper to use the label `TRX 预计手续费`.

Do not alter `estimateFees`; preview remains maximum-only.

**Step 4: Verify green**

Run the two focused withdrawal test classes and confirm all pass.

**Step 5: Commit**

```powershell
git add backend/src/main/java/com/reelshort/backend/withdrawal/PayoutBalancePreflightService.java backend/src/test/java/com/reelshort/backend/withdrawal/PayoutBalancePreflightServiceTests.java
git commit -m "fix(withdrawal): use dynamic TRON fee preflight"
```

### Task 3: Document, Verify, Integrate And Deploy

**Files:**
- Modify: `docs/api/withdrawals.md`
- Modify: `AGENTS.md`

**Step 1: Update documentation**

Document that preview remains an upper bound, final execution uses exact transfer simulation, current chain prices/resources and a 20% margin, and actual fee remains receipt-derived. Add a dated `backend/withdrawal` change-history entry because the payout behavior and internal client contract changed.

**Step 2: Run full verification**

```powershell
.\gradlew.bat test --no-daemon
.\gradlew.bat build
powershell -ExecutionPolicy Bypass -File scripts\verify-release-baseline.ps1
```

Run backend commands in `backend`; run the baseline from the repository root. All commands must exit 0.

**Step 3: Request independent review**

Review the complete branch against the design, including arithmetic overflow, response validation, private-key boundaries, preview semantics and regression coverage. Resolve all Critical and Important findings, then rerun affected tests.

**Step 4: Synchronize Git**

Fetch the feature branch, rebase only if behind, commit docs, push the feature branch, fast-forward `master`, rerun the backend test suite on merged `master`, and push `master`.

**Step 5: Deploy production**

Create fresh PostgreSQL/source/config and backend-image rollback backups. Upload the merged Git archive without overwriting `infra/.env`, build and recreate only backend, then wait for healthy.

**Step 6: Verify production symptom**

Confirm:

- deployed commit and image match;
- all five services are healthy;
- production read-only simulation of the two current pending TRC20 withdrawals yields about 26.057 TRX before margin and about 31.269 TRX after margin;
- a wallet balance around 19.969158 TRX now reports the dynamic estimated shortage rather than requiring 200 TRX;
- recent backend logs contain no startup errors.
