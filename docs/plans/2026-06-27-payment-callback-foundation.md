# Payment Callback Foundation Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add an internal simulated payment callback boundary that records payment events and settles recharge orders idempotently.

**Architecture:** Introduce a `backend/payment` package for callback adaptation and event persistence. The payment module validates event input and delegates settlement to `RechargeOrderService`; it does not directly mutate points or order internals.

**Tech Stack:** Java 17, Spring Boot, Spring MVC, Spring Data JPA, Validation, JUnit, MockMvc.

---

### Task 1: Payment Event Model and Service

**Files:**
- Create: `backend/src/main/java/com/reelshort/backend/payment/PaymentEventStatus.java`
- Create: `backend/src/main/java/com/reelshort/backend/payment/PaymentEvent.java`
- Create: `backend/src/main/java/com/reelshort/backend/payment/PaymentEventRepository.java`
- Create: `backend/src/main/java/com/reelshort/backend/payment/PaymentCallbackRequest.java`
- Create: `backend/src/main/java/com/reelshort/backend/payment/PaymentCallbackResponse.java`
- Create: `backend/src/main/java/com/reelshort/backend/payment/PaymentCallbackService.java`
- Test: `backend/src/test/java/com/reelshort/backend/payment/PaymentCallbackServiceTests.java`

**Step 1:** Write failing service tests for successful callback, duplicate callback idempotency, and amount mismatch rejection.

**Step 2:** Implement payment event entity, status enum, repository, request/response records, and callback service.

**Step 3:** Run `.\gradlew.bat test --tests "*PaymentCallbackServiceTests"`.

### Task 2: Internal Callback Controller and Security

**Files:**
- Create: `backend/src/main/java/com/reelshort/backend/payment/PaymentProperties.java`
- Create: `backend/src/main/java/com/reelshort/backend/payment/PaymentException.java`
- Create: `backend/src/main/java/com/reelshort/backend/payment/PaymentCallbackController.java`
- Modify: `backend/src/main/java/com/reelshort/backend/auth/SecurityConfig.java`
- Modify: `backend/src/main/java/com/reelshort/backend/system/web/GlobalExceptionHandler.java`
- Test: `backend/src/test/java/com/reelshort/backend/payment/PaymentCallbackControllerTests.java`

**Step 1:** Write failing MockMvc tests for missing secret, wrong secret, successful callback, duplicate callback, and amount mismatch.

**Step 2:** Add `PaymentProperties` with `callback-secret`.

**Step 3:** Add controller that checks `X-Payment-Callback-Secret` and calls service.

**Step 4:** Add payment exception mapping to unified error response.

**Step 5:** Permit `/api/internal/payments/recharge/callback` through Spring Security while relying on callback secret.

**Step 6:** Run `.\gradlew.bat test --tests "*PaymentCallbackControllerTests"`.

### Task 3: Docs, Review, and Merge

**Files:**
- Create: `docs/api/payment-callback.md`
- Modify: `docs/api/README.md`
- Modify: `docs/api/orders.md`
- Modify: `AGENTS.md`

**Step 1:** Document internal callback request, secret header, idempotency, and non-real-payment scope.

**Step 2:** Update API docs index and AGENTS module structure/change history.

**Step 3:** Run `git diff --check`.

**Step 4:** Run `.\gradlew.bat test`.

**Step 5:** Review for security boundary, idempotency, amount validation, accidental public payment exposure, and point ledger consistency.

**Step 6:** Fix findings and repeat review.

**Step 7:** Commit, merge into `master`, verify on `master`, then remove worktree and branch.
