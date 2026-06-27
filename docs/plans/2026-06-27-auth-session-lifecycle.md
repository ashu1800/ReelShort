# Auth Session Lifecycle Implementation Plan

**Goal:** Add App access token expiration, logout revocation, and cleanup while preserving existing Bearer token response shape.

**Architecture:** Keep PostgreSQL `access_tokens` as the source of truth. Extend the existing opaque token service with TTL and revocation fields, and keep authentication enforcement inside the existing App bearer filter.

**Tech Stack:** Spring Boot 3.4, Spring Security filter chain, Spring Data JPA, JUnit 5, MockMvc.

---

### Task 1: Token Domain and Repository

**Files:**
- Modify: `backend/src/main/java/com/reelshort/backend/auth/AccessToken.java`
- Modify: `backend/src/main/java/com/reelshort/backend/auth/AccessTokenRepository.java`
- Test: `backend/src/test/java/com/reelshort/backend/auth/AccessTokenRepositoryTests.java`

**Step 1:** Write failing repository tests for `expiresAt`, `revokedAt`, and cleanup delete.

**Step 2:** Run `.\gradlew.bat test --tests "*AccessTokenRepositoryTests"` and verify failure.

**Step 3:** Add fields and repository delete method.

**Step 4:** Run repository tests until green.

### Task 2: Token Service Lifecycle

**Files:**
- Create: `backend/src/main/java/com/reelshort/backend/auth/AuthSessionProperties.java`
- Modify: `backend/src/main/java/com/reelshort/backend/auth/TokenService.java`
- Modify: `backend/src/main/java/com/reelshort/backend/auth/OpaqueTokenService.java`
- Test: `backend/src/test/java/com/reelshort/backend/auth/AuthSessionServiceTests.java`

**Step 1:** Write failing service tests for configured TTL, revoke current token, and non-leaking missing token revoke.

**Step 2:** Run targeted tests and verify failure.

**Step 3:** Implement properties and service methods.

**Step 4:** Run service tests until green.

### Task 3: Filter, Logout API, and Cleanup Task

**Files:**
- Modify: `backend/src/main/java/com/reelshort/backend/auth/BearerTokenAuthenticationFilter.java`
- Modify: `backend/src/main/java/com/reelshort/backend/auth/AuthController.java`
- Create: `backend/src/main/java/com/reelshort/backend/auth/AuthSessionCleanupService.java`
- Modify: `backend/src/main/java/com/reelshort/backend/BackendApplication.java`
- Test: `backend/src/test/java/com/reelshort/backend/auth/AppSecurityContractTests.java`
- Test: `backend/src/test/java/com/reelshort/backend/auth/AuthControllerTests.java`

**Step 1:** Write failing MockMvc tests for expired token rejection, revoked token rejection, logout revocation, and cleanup deletion.

**Step 2:** Run targeted tests and verify failure.

**Step 3:** Implement filter checks, logout endpoint, and scheduled cleanup service.

**Step 4:** Run targeted tests until green.

### Task 4: Docs, Review, and Merge

**Files:**
- Modify: `backend/src/main/resources/application.properties`
- Modify: `docs/api/auth-security.md`
- Modify: `docs/api/auth-user.md`
- Modify: `AGENTS.md`

**Step 1:** Document session TTL, logout endpoint, and cleanup behavior.

**Step 2:** Run `git diff --check`.

**Step 3:** Run backend full tests, admin-web build, content-provider pytest.

**Step 4:** Review for auth bypass, disabled-user precedence, stale docs, and scheduler side effects.

**Step 5:** Fix findings, repeat verification, commit, merge to `master`, then clean up worktree and branch.
