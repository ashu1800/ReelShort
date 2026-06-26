# Admin Foundation Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build the first backend admin management loop for administrator login, user management, watch records, and point records.

**Architecture:** Keep admin authentication separate from App authentication under `/api/admin/**`. The admin module orchestrates queries against existing user, watch, and points persistence without changing App contracts.

**Tech Stack:** Java 17, Spring Boot 3.4, Spring Security, Spring Data JPA, MockMvc, H2 tests.

---

### Task 1: Admin Security Contract Tests

**Files:**
- Create: `backend/src/test/java/com/reelshort/backend/admin/AdminAuthControllerTests.java`
- Modify later: `backend/src/main/java/com/reelshort/backend/auth/SecurityConfig.java`

**Steps:**
1. Write failing MockMvc tests for `POST /api/admin/auth/login` success and invalid password failure.
2. Write failing tests proving `/api/admin/users` rejects anonymous requests and rejects App Bearer tokens.
3. Run `.\gradlew.bat --no-daemon --console=plain test --tests com.reelshort.backend.admin.AdminAuthControllerTests`.

### Task 2: Admin Authentication Implementation

**Files:**
- Create: `backend/src/main/java/com/reelshort/backend/admin/AdminProperties.java`
- Create: `backend/src/main/java/com/reelshort/backend/admin/AdminToken.java`
- Create: `backend/src/main/java/com/reelshort/backend/admin/AdminTokenRepository.java`
- Create: `backend/src/main/java/com/reelshort/backend/admin/AdminPrincipal.java`
- Create: `backend/src/main/java/com/reelshort/backend/admin/AdminBearerTokenAuthenticationFilter.java`
- Create: `backend/src/main/java/com/reelshort/backend/admin/AdminAuthService.java`
- Create: `backend/src/main/java/com/reelshort/backend/admin/AdminAuthController.java`
- Modify: `backend/src/main/java/com/reelshort/backend/auth/SecurityConfig.java`
- Modify: `backend/src/test/resources/application.properties`

**Steps:**
1. Add admin properties with test defaults.
2. Add `admin_tokens` persistence with token hash and expiry.
3. Add admin login service using existing `PasswordHasher`.
4. Add admin bearer filter for `/api/admin/**`.
5. Configure anonymous access to `/api/admin/auth/login` and authentication for other admin endpoints.
6. Run admin auth tests until green.

### Task 3: Admin User Query Tests

**Files:**
- Create: `backend/src/test/java/com/reelshort/backend/admin/AdminUserControllerTests.java`

**Steps:**
1. Write failing tests for user list and user detail with admin token.
2. Include assertion that list/detail include user status and point balance.
3. Run targeted tests and confirm failure because endpoints do not exist.

### Task 4: Admin User Query Implementation

**Files:**
- Modify: `backend/src/main/java/com/reelshort/backend/user/UserAccount.java`
- Modify: `backend/src/main/java/com/reelshort/backend/user/UserAccountRepository.java`
- Create: `backend/src/main/java/com/reelshort/backend/admin/AdminUserSummaryResponse.java`
- Create: `backend/src/main/java/com/reelshort/backend/admin/AdminUserDetailResponse.java`
- Create: `backend/src/main/java/com/reelshort/backend/admin/AdminUserService.java`
- Create: `backend/src/main/java/com/reelshort/backend/admin/AdminUserController.java`

**Steps:**
1. Add repository lookups needed by admin.
2. Implement list/detail responses.
3. Run admin user query tests until green.

### Task 5: Admin User Status Tests and Implementation

**Files:**
- Modify: `backend/src/test/java/com/reelshort/backend/admin/AdminUserControllerTests.java`
- Create: `backend/src/main/java/com/reelshort/backend/admin/AdminUserStatusRequest.java`
- Modify: `backend/src/main/java/com/reelshort/backend/admin/AdminUserService.java`
- Modify: `backend/src/main/java/com/reelshort/backend/admin/AdminUserController.java`

**Steps:**
1. Write failing test for disabling a user and proving App access fails after disable.
2. Implement status mutation with `ACTIVE` / `DISABLED` validation.
3. Run targeted tests until green.

### Task 6: Admin User Activity Tests and Implementation

**Files:**
- Modify: `backend/src/test/java/com/reelshort/backend/admin/AdminUserControllerTests.java`
- Modify: `backend/src/main/java/com/reelshort/backend/watch/WatchRecordRepository.java`
- Modify: `backend/src/main/java/com/reelshort/backend/points/PointTransactionRepository.java`
- Modify: `backend/src/main/java/com/reelshort/backend/admin/AdminUserService.java`
- Modify: `backend/src/main/java/com/reelshort/backend/admin/AdminUserController.java`

**Steps:**
1. Write failing tests for admin querying a specific user's watch records and point records.
2. Implement user-scoped admin query methods.
3. Run targeted tests until green.

### Task 7: Docs, Review, and Merge

**Files:**
- Modify: `AGENTS.md`
- Create: `docs/api/admin.md`

**Steps:**
1. Document admin APIs and security boundaries.
2. Run `.\gradlew.bat --no-daemon --console=plain test --rerun-tasks`.
3. Run `git diff --check`.
4. Review for token boundary leaks, disabled-user behavior, user data leakage, missing validation, and docs drift.
5. Fix findings with tests first when behavior changes.
6. Commit, merge to `master`, verify again, then remove worktree.
