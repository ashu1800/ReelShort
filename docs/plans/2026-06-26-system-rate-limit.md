# System Rate Limit Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add backend rate limiting for sensitive App/Admin endpoints with unified `429` responses and a storage abstraction that can later move from memory to Redis.

**Architecture:** Use a Spring MVC interceptor in `system/ratelimit`. Rules are configured through properties, matched before controller execution, and counted through a `RateLimitStore` interface. The first store is in-memory for single-node deployment and tests.

**Tech Stack:** Java 17, Spring Boot 3.4, Spring MVC, Spring Boot configuration properties, JUnit, MockMvc.

---

### Task 1: Rate Limit Store

**Files:**
- Create: `backend/src/main/java/com/reelshort/backend/system/ratelimit/RateLimitStore.java`
- Create: `backend/src/main/java/com/reelshort/backend/system/ratelimit/InMemoryRateLimitStore.java`
- Create: `backend/src/main/java/com/reelshort/backend/system/ratelimit/RateLimitResult.java`
- Test: `backend/src/test/java/com/reelshort/backend/system/ratelimit/InMemoryRateLimitStoreTests.java`

**Steps:**
1. Write failing tests for allow, reject after limit, and allow after window expiry.
2. Implement a synchronized in-memory fixed-window counter.
3. Return result with `allowed`, `limit`, `remaining`, and `retryAfterSeconds`.
4. Run store tests.

### Task 2: Rule Matching and Key Resolution

**Files:**
- Create: `backend/src/main/java/com/reelshort/backend/system/ratelimit/RateLimitRule.java`
- Create: `backend/src/main/java/com/reelshort/backend/system/ratelimit/RateLimitProperties.java`
- Create: `backend/src/main/java/com/reelshort/backend/system/ratelimit/RateLimitKeyResolver.java`
- Test: `backend/src/test/java/com/reelshort/backend/system/ratelimit/RateLimitRuleTests.java`

**Steps:**
1. Write failing tests for path/method matching and IP extraction.
2. Implement exact path and prefix wildcard matching ending in `/**`.
3. Resolve authenticated principals before IP fallback.
4. Run rule tests.

### Task 3: MVC Interceptor

**Files:**
- Create: `backend/src/main/java/com/reelshort/backend/system/ratelimit/RateLimitInterceptor.java`
- Modify: `backend/src/main/java/com/reelshort/backend/system/web/WebMvcConfig.java`
- Test: `backend/src/test/java/com/reelshort/backend/system/RateLimitInterceptorTests.java`

**Steps:**
1. Write failing MockMvc tests for login rate limit, separate IP counters, and health endpoint bypass.
2. Implement interceptor with unified JSON error response and `Retry-After` header.
3. Register interceptor in `WebMvcConfig`.
4. Run interceptor tests.

### Task 4: Configuration and Documentation

**Files:**
- Modify: `backend/src/main/resources/application.properties`
- Modify: `backend/src/test/resources/application.properties`
- Create: `docs/api/rate-limit.md`
- Modify: `docs/api/backend-foundation.md`
- Modify: `AGENTS.md`

**Steps:**
1. Add default enabled flag and default limits.
2. Add aggressive test-only login rule values for deterministic tests if needed.
3. Document protected endpoints and `429` response.
4. Update AGENTS module structure and change history.

### Task 5: Review, Verify, Commit, Merge

**Files:**
- All touched files.

**Steps:**
1. Stage all changes and run `git diff --cached --check`.
2. Review staged diff for authentication ordering, path coverage, memory cleanup, and docs consistency.
3. Run `.\gradlew.bat --no-daemon --console=plain test --rerun-tasks`.
4. Commit with `feat(system): add rate limit foundation`.
5. Merge to `master` with `--no-ff`.
6. Run full tests on `master`.
7. Remove worktree and delete merged branch.
