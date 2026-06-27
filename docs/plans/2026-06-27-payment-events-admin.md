# Payment Events Admin Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a read-only admin payment events surface for backend API and Vue admin web.

**Architecture:** Keep payment event ownership in `backend/payment`, expose read-only admin access through Spring Boot Admin API with `PAYMENT_EVENT_READ` permission, and add a Vue admin route that consumes the API via the existing admin client.

**Tech Stack:** Java 17, Spring Boot, Spring Data JPA, Spring MVC, JUnit/MockMvc, Vue 3, TypeScript, Element Plus, Axios.

---

### Task 1: Backend Admin Payment Events API

**Files:**
- Create: `backend/src/main/java/com/reelshort/backend/payment/AdminPaymentEventController.java`
- Create: `backend/src/main/java/com/reelshort/backend/payment/PaymentEventResponse.java`
- Create: `backend/src/main/java/com/reelshort/backend/payment/PaymentEventService.java`
- Modify: `backend/src/main/java/com/reelshort/backend/payment/PaymentEventRepository.java`
- Modify: `backend/src/main/java/com/reelshort/backend/admin/AdminPermissions.java`
- Test: `backend/src/test/java/com/reelshort/backend/payment/AdminPaymentEventControllerTests.java`

**Step 1:** Write failing MockMvc tests for unauthorized access, authorized list, status filter, order filter, channel filter, and newest-first ordering.

**Step 2:** Run `.\gradlew.bat test --tests "*AdminPaymentEventControllerTests"` and confirm it fails because the endpoint does not exist.

**Step 3:** Add `PAYMENT_EVENT_READ` permission constant and rely on existing RBAC bootstrap to register it.

**Step 4:** Add repository query methods or a simple service-side filter for payment events.

**Step 5:** Add `PaymentEventResponse`, `PaymentEventService`, and `AdminPaymentEventController`.

**Step 6:** Run `.\gradlew.bat test --tests "*AdminPaymentEventControllerTests"` and confirm it passes.

### Task 2: Admin Web Payment Events View

**Files:**
- Modify: `admin-web/src/services/adminApi.ts`
- Modify: `admin-web/src/router/index.ts`
- Modify: `admin-web/src/App.vue`
- Create: `admin-web/src/views/PaymentEventsView.vue`

**Step 1:** Add typed `PaymentEvent` model and `listPaymentEvents` API method.

**Step 2:** Add `/payments/events` route.

**Step 3:** Add sidebar navigation entry.

**Step 4:** Create `PaymentEventsView.vue` with filters and table.

**Step 5:** Run `npm run build`.

### Task 3: Docs, AGENTS, Review, and Merge

**Files:**
- Create: `docs/api/payment-events-admin.md`
- Modify: `docs/api/README.md`
- Modify: `AGENTS.md`

**Step 1:** Document the admin payment events API and filters.

**Step 2:** Update API docs index and AGENTS module history.

**Step 3:** Run `git diff --check`.

**Step 4:** Run `.\gradlew.bat test` in `backend`.

**Step 5:** Run `npm run build` in `admin-web`.

**Step 6:** Review security, permission coverage, query semantics, frontend route integration, and docs consistency.

**Step 7:** Fix findings, repeat review, commit, merge to `master`, verify on `master`, then clean up worktree and branch.
