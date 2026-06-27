# Orders Foundation Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add the backend recharge order foundation without integrating real payment or changing point balances.

**Architecture:** Create a new `backend/order` package. App APIs use `CurrentUser`, Admin APIs use existing admin token and permission infrastructure, and order persistence is isolated from points.

**Tech Stack:** Java 17, Spring Boot, Spring MVC, Spring Data JPA, JUnit, MockMvc.

---

### Task 1: Domain and Repository

**Files:**
- Create: `backend/src/main/java/com/reelshort/backend/order/RechargeOrderStatus.java`
- Create: `backend/src/main/java/com/reelshort/backend/order/RechargeOrder.java`
- Create: `backend/src/main/java/com/reelshort/backend/order/RechargeOrderRepository.java`
- Test: `backend/src/test/java/com/reelshort/backend/order/RechargeOrderRepositoryTests.java`

**Step 1:** Write failing repository test for saving an order and finding orders by user ID ordered by `createdAt desc`.

**Step 2:** Implement entity, enum, and repository.

**Step 3:** Run `.\gradlew.bat test --tests "*RechargeOrderRepositoryTests"`.

### Task 2: Service and DTOs

**Files:**
- Create: `backend/src/main/java/com/reelshort/backend/order/CreateRechargeOrderRequest.java`
- Create: `backend/src/main/java/com/reelshort/backend/order/RechargeOrderResponse.java`
- Create: `backend/src/main/java/com/reelshort/backend/order/RechargeOrderService.java`
- Test: `backend/src/test/java/com/reelshort/backend/order/RechargeOrderServiceTests.java`

**Step 1:** Write failing service tests for creating `CREATED` orders, rejecting invalid values, and not touching points.

**Step 2:** Implement request, response, service, order number generation, and validation.

**Step 3:** Run `.\gradlew.bat test --tests "*RechargeOrderServiceTests"`.

### Task 3: App API

**Files:**
- Create: `backend/src/main/java/com/reelshort/backend/order/OrderController.java`
- Test: `backend/src/test/java/com/reelshort/backend/order/OrderControllerTests.java`
- Modify: `docs/api/README.md`

**Step 1:** Write failing MockMvc tests for authenticated order creation and user-isolated order listing.

**Step 2:** Implement App order controller.

**Step 3:** Run `.\gradlew.bat test --tests "*OrderControllerTests"`.

### Task 4: Admin API and Permission

**Files:**
- Create: `backend/src/main/java/com/reelshort/backend/order/AdminOrderController.java`
- Modify: `backend/src/main/java/com/reelshort/backend/admin/AdminPermissions.java`
- Modify: `backend/src/main/java/com/reelshort/backend/admin/AdminRbacBootstrapService.java`
- Test: `backend/src/test/java/com/reelshort/backend/order/AdminOrderControllerTests.java`
- Modify: `docs/api/admin.md`

**Step 1:** Write failing admin tests for `GET /api/admin/orders` requiring `ORDER_READ`.

**Step 2:** Add permission constant, default permission bootstrap, and admin controller.

**Step 3:** Run `.\gradlew.bat test --tests "*AdminOrderControllerTests"`.

### Task 5: Docs, Review, and Merge

**Files:**
- Modify: `AGENTS.md`
- Create: `docs/api/orders.md`

**Step 1:** Document App and Admin order APIs and non-payment scope.

**Step 2:** Update `AGENTS.md` module structure and change history.

**Step 3:** Run `git diff --check`.

**Step 4:** Run `.\gradlew.bat test`.

**Step 5:** Review for module isolation, order status rules, permission coverage, and point-balance side effects.

**Step 6:** Fix findings, repeat review, commit, merge to `master`, verify again, clean worktree.
