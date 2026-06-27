# Redis Rate Limit Store Implementation Plan

**Goal:** Add an optional Redis-backed `RateLimitStore` while keeping the current memory store as the default.

**Architecture:** Extend the existing `system/ratelimit` abstraction instead of changing controllers. Register the store and interceptor through explicit configuration so Web MVC slice tests do not accidentally load partial infrastructure. Use Spring Boot Redis auto-configuration and `StringRedisTemplate`; choose the store with `reelshort.rate-limit.store`.

**Tech Stack:** Spring Boot 3.4, Spring Data Redis, Gradle, JUnit 5, Mockito.

---

### Task 1: Configuration Boundary

**Files:**
- Modify: `backend/build.gradle`
- Modify: `backend/src/main/java/com/reelshort/backend/system/ratelimit/RateLimitProperties.java`
- Modify: `backend/src/main/java/com/reelshort/backend/system/ratelimit/InMemoryRateLimitStore.java`
- Test: `backend/src/test/java/com/reelshort/backend/system/ratelimit/RateLimitStoreConfigurationTests.java`

**Step 1:** Write context tests proving default store is memory and redis mode selects Redis.

**Step 2:** Run the new tests and verify they fail because Redis dependencies/store conditions do not exist.

**Step 3:** Add Redis starter dependency and `store` property.

**Step 4:** Add conditional bean registration for memory and Redis stores.

### Task 2: Redis Store Behavior

**Files:**
- Create: `backend/src/main/java/com/reelshort/backend/system/ratelimit/RedisRateLimitStore.java`
- Test: `backend/src/test/java/com/reelshort/backend/system/ratelimit/RedisRateLimitStoreTests.java`

**Step 1:** Write unit tests for allowed increments, rejected limit, retry-after TTL, and missing TTL repair.

**Step 2:** Run the new tests and verify they fail because `RedisRateLimitStore` is missing.

**Step 3:** Implement `RedisRateLimitStore` with `StringRedisTemplate`.

**Step 4:** Run Redis store tests and fix until green.

### Task 3: Docs and Deployment Wiring

**Files:**
- Modify: `backend/src/main/resources/application.properties`
- Modify: `infra/.env.example`
- Modify: `infra/docker-compose.yml`
- Modify: `docs/api/rate-limit.md`
- Modify: `AGENTS.md`

**Step 1:** Document `reelshort.rate-limit.store=memory|redis`.

**Step 2:** Configure Compose backend to use Redis by default and pass `spring.data.redis.host=redis`.

**Step 3:** Update AGENTS module description/change history.

**Step 4:** Run full backend tests, file checks, review, commit, merge to `master`, and clean up worktree.
