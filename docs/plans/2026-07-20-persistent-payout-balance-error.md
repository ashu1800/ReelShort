# Persistent Payout Balance Error Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Keep payout balance-preflight failures visible inside the withdrawal dialog and show the required, available, and missing amounts in Chinese.

**Architecture:** Preserve the existing execute endpoint and security boundary. The backend formats actionable Chinese balance errors, while the Vue view stores the returned message in component state and renders a non-expiring `el-alert`; private keys and TOTP continue to be cleared in `finally`.

**Tech Stack:** Java 17, Spring Boot, JUnit 5, Vue 3, TypeScript, Element Plus, Node test runner.

---

### Task 1: Format actionable backend balance errors

**Files:**
- Modify: `backend/src/test/java/com/reelshort/backend/withdrawal/PayoutBalancePreflightServiceTests.java`
- Modify: `backend/src/main/java/com/reelshort/backend/withdrawal/PayoutBalancePreflightService.java`

**Step 1: Write the failing test**

Change the existing insufficient-USDT assertion to require the exact Chinese fields and add an assertion for the missing amount:

```java
assertThatThrownBy(() -> service.requireSufficient(
        List.of(first, second), "tron-key", null, null))
        .isInstanceOf(WithdrawalException.class)
        .hasMessage("TRC20 USDT 余额不足：本次需要 13，当前余额 12，还差 1");
```

Add the same required/current/missing contract for the native-fee balance test.

**Step 2: Run the test to verify it fails**

Run: `backend\gradlew.bat test --tests "com.reelshort.backend.withdrawal.PayoutBalancePreflightServiceTests"`

Expected: FAIL because the current message is English and has no shortfall.

**Step 3: Implement the minimal message change**

Update `checkAtLeast` to calculate `required.subtract(available)` and throw:

```java
BigDecimal missing = required.subtract(available);
throw new WithdrawalException(409, asset + " 余额不足：本次需要 "
        + decimal(required) + "，当前余额 " + decimal(available)
        + "，还差 " + decimal(missing));
```

Keep the `available == null` branch explicit so node-query failures do not claim a numeric balance.

**Step 4: Run the focused test**

Run: `backend\gradlew.bat test --tests "com.reelshort.backend.withdrawal.PayoutBalancePreflightServiceTests"`

Expected: PASS.

### Task 2: Keep payout failures visible in the dialog

**Files:**
- Modify: `admin-web/tests/withdrawal-security.test.mjs`
- Modify: `admin-web/src/views/WithdrawalsView.vue`

**Step 1: Write the failing contract test**

Add a test that requires:

```js
assert.match(source, /const payoutError = ref\(''\)/)
assert.match(source, /payoutError\.value = backendErrorMessage/)
assert.match(source, /v-if="payoutError"/)
assert.match(source, /title="打款未执行"/)
assert.match(source, /:description="payoutError"/)
```

Also require clearing before a new execute attempt and when the dialog closes.

**Step 2: Run the test to verify it fails**

Run: `npm run test:contracts`

Expected: FAIL because no persistent payout error state exists.

**Step 3: Implement persistent error state**

Add `const payoutError = ref('')`. Clear it when opening/closing the dialog, returning to preview, and immediately before a new payout request. In the non-timeout catch branch assign:

```ts
payoutError.value = backendErrorMessage(error, '打款提交失败')
```

Render an unclosable error alert in the credentials step:

```vue
<el-alert
  v-if="payoutError"
  title="打款未执行"
  :description="payoutError"
  type="error"
  show-icon
  :closable="false"
/>
```

Do not remove `clearSecrets()` from `finally`.

**Step 4: Run frontend tests and build**

Run: `npm run test:contracts`

Expected: all contract tests pass.

Run: `npm run build`

Expected: production build succeeds.

### Task 3: Document, verify, integrate, and deploy

**Files:**
- Modify: `docs/api/withdrawals.md`
- Modify: `AGENTS.md`

**Step 1: Document the behavior**

Record that balance failures remain visible in the credentials dialog, show required/current/missing values, and still clear secrets.

**Step 2: Run repository verification**

Run:

```powershell
backend\gradlew.bat test --tests "com.reelshort.backend.withdrawal.*"
backend\gradlew.bat build
cd admin-web; npm run test:contracts; npm run build
powershell -ExecutionPolicy Bypass -File scripts/verify-release-baseline.ps1
```

Expected: all commands exit 0.

**Step 3: Commit and integrate**

```powershell
git add .
git commit -m "fix(withdrawal): persist balance preflight errors"
git push origin codex/persistent-payout-balance-error
```

Fast-forward `master`, push it, and confirm local/remote commit hashes match.

**Step 4: Deploy production**

Back up PostgreSQL, current source, and current backend/nginx images. Build backend and nginx from the merged commit in an isolated release directory, switch services, wait for healthy status, verify public APIs and the new admin asset, and retain rollback artifacts.
