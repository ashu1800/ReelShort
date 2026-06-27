# Admin Session Lifecycle Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add admin access token logout revocation and cleanup while preserving the existing admin Bearer token response shape.

**Architecture:** Keep PostgreSQL `admin_tokens` as the source of truth. Extend the existing opaque admin token model with revocation state, enforce it in the existing admin bearer filter, and let admin-web call the backend logout endpoint before clearing local session state.

**Tech Stack:** Spring Boot 3.4, Spring Security filter chain, Spring Data JPA, JUnit 5, MockMvc, Vue 3, TypeScript, Axios.

---

### Task 1: Admin Token Domain and Repository

**Files:**
- Modify: `backend/src/main/java/com/reelshort/backend/admin/AdminToken.java`
- Modify: `backend/src/main/java/com/reelshort/backend/admin/AdminTokenRepository.java`
- Test: `backend/src/test/java/com/reelshort/backend/admin/AdminAuthServiceTests.java`

**Step 1:** Write failing repository/service tests for `revokedAt` persistence and cleanup delete.

**Step 2:** Run `.\gradlew.bat test --tests "*AdminAuthServiceTests"` and verify failure.

**Step 3:** Add `revokedAt`, lifecycle helpers, and repository delete method.

**Step 4:** Run targeted tests until green.

### Task 2: Admin Auth Service Logout

**Files:**
- Modify: `backend/src/main/java/com/reelshort/backend/admin/AdminAuthService.java`
- Test: `backend/src/test/java/com/reelshort/backend/admin/AdminAuthServiceTests.java`

**Step 1:** Write failing service tests for `logout(rawToken)` revoking an existing Token and ignoring a missing Token.

**Step 2:** Run targeted tests and verify failure.

**Step 3:** Implement `logout(String rawToken)` using `TokenHasher` and `AdminTokenRepository`.

**Step 4:** Run service tests until green.

### Task 3: Filter, Logout API, and Cleanup Task

**Files:**
- Modify: `backend/src/main/java/com/reelshort/backend/admin/AdminBearerTokenAuthenticationFilter.java`
- Modify: `backend/src/main/java/com/reelshort/backend/admin/AdminAuthController.java`
- Create: `backend/src/main/java/com/reelshort/backend/admin/AdminSessionProperties.java`
- Create: `backend/src/main/java/com/reelshort/backend/admin/AdminSessionCleanupService.java`
- Modify: `backend/src/main/java/com/reelshort/backend/auth/SecurityConfig.java`
- Modify: `backend/src/main/resources/application.properties`
- Modify: `backend/src/test/resources/application.properties`
- Test: `backend/src/test/java/com/reelshort/backend/admin/AdminAuthControllerTests.java`
- Test: `backend/src/test/java/com/reelshort/backend/admin/AdminSessionCleanupServiceTests.java`

**Step 1:** Write failing MockMvc tests for logout revocation, revoked token rejection, expired token rejection, and cleanup deletion.

**Step 2:** Run targeted tests and verify failure.

**Step 3:** Implement filter checks, logout endpoint, configuration properties, and scheduled cleanup service.

**Step 4:** Run targeted tests until green.

### Task 4: Admin Web Logout Call

**Files:**
- Modify: `admin-web/src/services/adminApi.ts`
- Modify: `admin-web/src/App.vue`

**Step 1:** Add a typed `logout()` admin API method.

**Step 2:** Update the header logout action to call the backend logout endpoint and always clear local session afterwards.

**Step 3:** Run `npm run build`.

### Task 5: Docs, Review, Verification, and Merge

**Files:**
- Modify: `docs/api/admin.md`
- Modify: `AGENTS.md`

**Step 1:** Document admin logout endpoint, revoked-token behavior, and cleanup configuration.

**Step 2:** Run `git diff --check`.

**Step 3:** Run backend full tests, admin-web build, and content-provider pytest.

**Step 4:** Review for auth bypass, revoked token enforcement, stale docs, scheduler side effects, and frontend logout failure behavior.

**Step 5:** Fix findings, repeat review and verification, commit, merge to `master`, then clean up worktree and branch.
