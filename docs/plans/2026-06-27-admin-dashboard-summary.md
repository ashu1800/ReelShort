# Admin Dashboard Summary Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a single backend admin dashboard summary API and switch admin-web Dashboard to consume it.

**Architecture:** Keep dashboard aggregation inside the `admin` backend boundary while reusing existing repositories from user, order, payment, content, and audit modules. Use one protected Admin API endpoint with `DASHBOARD_READ` permission and let the Vue dashboard render the aggregated response.

**Tech Stack:** Spring Boot 3.4, Spring Data JPA, Spring Security/RBAC, JUnit 5, MockMvc, Vue 3, TypeScript, Axios, Element Plus.

---

### Task 1: Backend Dashboard Service and Response

**Files:**
- Create: `backend/src/main/java/com/reelshort/backend/admin/AdminDashboardSummaryResponse.java`
- Create: `backend/src/main/java/com/reelshort/backend/admin/AdminDashboardService.java`
- Modify: `backend/src/main/java/com/reelshort/backend/admin/AdminAuditLogRepository.java`
- Test: `backend/src/test/java/com/reelshort/backend/admin/AdminDashboardServiceTests.java`

**Step 1:** Write failing service tests for user, order, payment, content, and latest audit log aggregation.

**Step 2:** Run `.\gradlew.bat test --tests "*AdminDashboardServiceTests"` and verify failure.

**Step 3:** Implement response records, service aggregation, and audit repository top-5 query.

**Step 4:** Run targeted service tests until green.

### Task 2: Admin Dashboard API and Permission

**Files:**
- Create: `backend/src/main/java/com/reelshort/backend/admin/AdminDashboardController.java`
- Modify: `backend/src/main/java/com/reelshort/backend/admin/AdminPermissions.java`
- Modify: `backend/src/main/java/com/reelshort/backend/admin/AdminRbacBootstrapService.java`
- Test: `backend/src/test/java/com/reelshort/backend/admin/AdminDashboardControllerTests.java`

**Step 1:** Write failing MockMvc tests for unauthenticated access, App Token rejection, and admin success response.

**Step 2:** Run targeted tests and verify failure.

**Step 3:** Implement controller and add `DASHBOARD_READ` to RBAC bootstrap descriptions/default role.

**Step 4:** Run targeted tests until green.

### Task 3: Admin Web Dashboard Refactor

**Files:**
- Modify: `admin-web/src/services/adminApi.ts`
- Modify: `admin-web/src/views/DashboardView.vue`

**Step 1:** Add `AdminDashboardSummary` type and `fetchDashboardSummary()` API method.

**Step 2:** Replace dashboard multi-request loading with the single summary API.

**Step 3:** Render user/order/payment/content metrics and latest audit logs from the summary.

**Step 4:** Run `npm run build`.

### Task 4: Docs, Review, Verification, and Merge

**Files:**
- Modify: `docs/api/admin.md`
- Modify: `AGENTS.md`

**Step 1:** Document `GET /api/admin/dashboard/summary` and `DASHBOARD_READ`.

**Step 2:** Run `git diff --check`.

**Step 3:** Run backend full tests, admin-web build, and content-provider pytest.

**Step 4:** Review for permission gaps, inefficient aggregation, stale docs, frontend error handling, and accidental list API regressions.

**Step 5:** Fix findings, repeat review and verification, commit, merge to `master`, then clean up worktree and branch.
