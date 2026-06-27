# Order Settlement Foundation Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add an internal, idempotent order settlement boundary that credits points when a recharge order is paid.

**Architecture:** `RechargeOrderService` owns order status transitions and calls `PointsService` for point accounting. `PointsService` owns account balance and point transaction writes. No public payment API is introduced in this slice.

**Tech Stack:** Java 17, Spring Boot, Spring Data JPA, JUnit, AssertJ.

---

### Task 1: Point Recharge Credit

**Files:**
- Modify: `backend/src/main/java/com/reelshort/backend/points/PointTransaction.java`
- Modify: `backend/src/main/java/com/reelshort/backend/points/PointAwardTransaction.java`
- Modify: `backend/src/main/java/com/reelshort/backend/points/PointsService.java`
- Test: `backend/src/test/java/com/reelshort/backend/points/PointsServiceTests.java`

**Step 1:** Write a failing test proving `PointsService.creditRechargeOrder(userId, orderNo, points)` creates a `RECHARGE_ORDER` transaction and increases balance.

**Step 2:** Implement `PointTransaction.rechargeOrder(...)`.

**Step 3:** Implement `PointAwardTransaction.creditRechargeOrder(...)`.

**Step 4:** Add public `PointsService.creditRechargeOrder(...)` wrapper using existing user lock.

**Step 5:** Run `.\gradlew.bat test --tests "*PointsServiceTests"`.

### Task 2: Order Paid Transition

**Files:**
- Modify: `backend/src/main/java/com/reelshort/backend/order/RechargeOrder.java`
- Modify: `backend/src/main/java/com/reelshort/backend/order/RechargeOrderRepository.java`
- Modify: `backend/src/main/java/com/reelshort/backend/order/RechargeOrderService.java`
- Test: `backend/src/test/java/com/reelshort/backend/order/RechargeOrderServiceTests.java`

**Step 1:** Write failing tests for settling a created order and repeated settlement idempotency.

**Step 2:** Add `RechargeOrder.markPaid(paymentChannel)` and optional test helper transition for cancelled state if needed.

**Step 3:** Add repository lookup by order number.

**Step 4:** Inject `PointsService` into `RechargeOrderService`.

**Step 5:** Implement `settlePaid(orderNo, paymentChannel)`: created orders mark paid and credit points; paid orders return unchanged; other statuses reject.

**Step 6:** Run `.\gradlew.bat test --tests "*RechargeOrderServiceTests"`.

### Task 3: Docs and Review

**Files:**
- Modify: `docs/api/orders.md`
- Modify: `docs/api/points.md`
- Modify: `AGENTS.md`

**Step 1:** Document internal settlement scope and `RECHARGE_ORDER` point transaction source.

**Step 2:** Update `AGENTS.md` module description and change history.

**Step 3:** Run `git diff --check`.

**Step 4:** Run `.\gradlew.bat test`.

**Step 5:** Review for idempotency, point ledger consistency, accidental public payment exposure, and status transition correctness.

**Step 6:** Fix findings and repeat review.

**Step 7:** Commit, merge into `master`, verify on `master`, then remove worktree and branch.
