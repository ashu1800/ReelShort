# Auth Security Foundation Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Protect App business APIs with persisted bearer-token authentication and expose a current-user backend context.

**Architecture:** Store only token hashes in JPA, validate bearer tokens in a Spring Security filter, and expose authenticated users through a controller argument resolver. Keep auth endpoints public and protect App business endpoints.

**Tech Stack:** Java 17, Spring Boot 3.4, Spring Security 6.5 style `SecurityFilterChain`, Spring Data JPA, H2 tests, PostgreSQL runtime.

---

### Task 1: Security Dependency and Contract Tests

**Files:**
- Modify: `backend/build.gradle`
- Test: `backend/src/test/java/com/reelshort/backend/auth/AppSecurityContractTests.java`

**Steps:**
1. Write failing tests for unauthenticated content access `401`, invalid token `401`, disabled user token `403`, and authenticated content access success.
2. Run targeted tests and verify they fail because security is not implemented.
3. Add `spring-boot-starter-security`.

### Task 2: Access Token Persistence

**Files:**
- Create: `backend/src/main/java/com/reelshort/backend/auth/AccessToken.java`
- Create: `backend/src/main/java/com/reelshort/backend/auth/AccessTokenRepository.java`
- Modify: `backend/src/main/java/com/reelshort/backend/auth/OpaqueTokenService.java`
- Test: `backend/src/test/java/com/reelshort/backend/auth/AccessTokenRepositoryTests.java`

**Steps:**
1. Write failing test that issued raw tokens are not stored directly and can be found by hash.
2. Implement `AccessToken`, repository, and token hashing.
3. Run repository/token tests and verify they pass.

### Task 3: Security Filter and Current User Context

**Files:**
- Create: `backend/src/main/java/com/reelshort/backend/auth/CurrentUser.java`
- Create: `backend/src/main/java/com/reelshort/backend/auth/AppUserPrincipal.java`
- Create: `backend/src/main/java/com/reelshort/backend/auth/BearerTokenAuthenticationFilter.java`
- Create: `backend/src/main/java/com/reelshort/backend/auth/SecurityConfig.java`
- Create: `backend/src/main/java/com/reelshort/backend/auth/CurrentUserArgumentResolver.java`
- Create: `backend/src/main/java/com/reelshort/backend/system/web/WebMvcConfig.java`

**Steps:**
1. Implement bearer-token validation and current-user resolver.
2. Configure public and protected routes.
3. Run security contract tests and verify they pass.

### Task 4: Content API Uses Current User Boundary

**Files:**
- Modify: `backend/src/main/java/com/reelshort/backend/content/ContentController.java`
- Modify: `backend/src/test/java/com/reelshort/backend/content/ContentControllerTests.java`

**Steps:**
1. Update content controller signatures to accept `CurrentUser`.
2. Adjust controller tests to authenticate requests.
3. Run content and security tests.

### Task 5: Docs, Review, and Merge

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/api/auth-user.md`
- Create: `docs/api/auth-security.md`

**Steps:**
1. Update docs for Authorization header and protected routes.
2. Run full backend tests.
3. Run `git diff --check master`.
4. Review the full diff for auth bypasses, status-code regressions, token leakage, and docs drift.
5. Fix review findings with tests first when behavior changes.
6. Commit, merge to `master`, verify again, then remove the worktree.
