# ERC20 Manual Withdrawals Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Disable automated multi-chain payouts and deliver an ERC20-only external-wallet manual confirmation workflow with payout statistics.

**Architecture:** Keep historical withdrawal/payout entities read-only, but remove their operational entry points. Add a small transactional manual settlement path that locks the withdrawal and point account, approves the withdrawal directly, and relies on the existing withdrawal idempotency key. Add an ERC20-only application guard and a Flyway migration that rejects old non-ERC20 pending requests and releases their frozen points.

**Tech Stack:** Spring Boot 3, JPA/Flyway/PostgreSQL, Vue 3/Element Plus/TypeScript, Kotlin Compose app, JUnit 5/MockMvc.

---

### Task 1: Lock the ERC20-only domain boundary

**Files:** `WithdrawalService.java`, `WalletService.java`, Android account UI/state tests, withdrawal contract tests.

1. Write failing backend tests showing non-ERC20 wallet binding and withdrawal creation are rejected while ERC20 succeeds.
2. Write failing Android tests showing the wallet network selector exposes only `ERC20` and keeps ERC20 wallet data in the request.
3. Implement the guards and update user-facing copy; preserve existing historical records in responses.
4. Run focused backend and app-core tests.

### Task 2: Add the migration for legacy pending requests

**Files:** `backend/src/main/resources/db/migration/V27__manual_erc20_withdrawals.sql`, migration tests.

1. Add a failing migration test for rejecting non-ERC20 `PENDING` withdrawals and releasing their frozen points; assert the migration rolls back when frozen points are insufficient.
2. Implement the Flyway SQL without changing or deleting historical payout attempts.
4. Run migration and entity tests and verify the migration is idempotent in the test database.

### Task 3: Implement idempotent manual confirmation

**Files:** `ManualWithdrawalConfirmRequest.java`, `WithdrawalService.java`, `WithdrawalPayoutTransactionService.java`, `AdminWithdrawalController.java`, related repositories/tests.

1. Add failing tests for TOTP/permission enforcement, pending ERC20 confirmation, frozen-point deduction, repeat confirmation, existing automatic/manual-review attempts, and unsupported networks.
2. Add the manual-confirm service method with the existing withdrawal -> point-account lock order and `withdrawal:<id>` idempotency key; do not create a payout attempt or require a txHash.
3. Add the controller endpoint requiring `WITHDRAWAL_WRITE`; accept only the TOTP code and audit success/failure without secrets.
4. Disable/remove automatic approve, batch-sign, broadcast, and scheduled confirmation entry points; retain historical read-only status fields.
5. Run focused controller/service/integration tests.

### Task 4: Add payout statistics

**Files:** `WithdrawalRequestRepository.java`, new stats DTO/service methods, `AdminWithdrawalController.java`, backend tests.

1. Add failing repository/service tests for `APPROVED + ERC20` amount/count aggregation across today, yesterday, this week, this month, and last month, including empty ranges and boundary timestamps.
2. Implement server-timezone Asia/Shanghai half-open range calculation and an aggregate query over `reviewed_at`.
3. Add `GET /api/admin/withdrawals/stats` with strict preset validation and a stable response shape.
4. Run MockMvc and repository tests.

### Task 5: Replace the Admin Web payout workflow

**Files:** `admin-web/src/services/adminApi.ts`, `admin-web/src/views/WithdrawalsView.vue`, related frontend tests/types.

1. Add failing type/UI tests for the manual-confirm button, TOTP prompt, disabled automatic controls, and five-range statistics selector.
2. Remove private-key inputs, batch preview/signing calls, and automatic payout result states.
3. Add per-row manual confirmation with a 2FA prompt, refresh/error handling, and a statistics panel defaulting to today.
4. Run `npm run build` and the relevant frontend checks.

### Task 6: Update documentation, verification, and deployment

**Files:** `docs/api/withdrawals.md`, `AGENTS.md`, `.env.example`, `infra/docker-compose.yml` only if obsolete payout variables need removal.

1. Document ERC20-only manual settlement, migration behavior, 2FA, idempotency, statistics semantics, and historical read-only compatibility.
2. Run backend full tests/build, Android app-core tests/build, admin-web build, content-provider tests, and the release baseline.
3. Perform independent code review and fix all Critical/Important findings.
4. Commit, fetch/rebase if needed, push the feature branch, fast-forward `master`, rerun merged tests, and push `master`.
5. Back up production database/source/config/backend image, deploy only the backend/admin-web changes, verify Flyway migration, service health, ERC20-only behavior, manual confirmation idempotency, and statistics. Do not broadcast any transaction.
