# Content Video Cache TTL Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Limit playback URL cache fallback to a short configurable TTL so backend never serves long-expired upstream video URLs.

**Architecture:** Keep playback URL lookup real-time on the normal path. Persist the last successful `ContentVideo` only as a temporary 5xx fallback, and reject cached fallback when `content_video_cache.refreshed_at` is older than the configured TTL. No Android or provider API changes are required.

**Tech Stack:** Spring Boot, JPA, JUnit, AssertJ, PostgreSQL Flyway-managed schema.

---

### Task 1: Backend TTL Contract

**Files:**
- Modify: `backend/src/test/java/com/reelshort/backend/content/ContentCacheServiceTests.java`
- Modify: `backend/src/main/java/com/reelshort/backend/content/ContentCacheService.java`
- Create: `backend/src/main/java/com/reelshort/backend/content/ContentCacheProperties.java`
- Modify: `backend/src/main/resources/application.properties`

**Step 1:** Write a failing test: after a successful playback URL cache write, set `refreshedAt` to older than the TTL and make the provider return `503`; `getVideoUrl()` must rethrow the provider error instead of returning stale cache.

Run:

```powershell
cd backend
.\gradlew.bat test --tests com.reelshort.backend.content.ContentCacheServiceTests --no-daemon
```

Expected: fails because stale video cache is currently returned.

**Step 2:** Add `ContentCacheProperties` with prefix `reelshort.content.cache` and `videoFallbackTtl`, default `10m`.

**Step 3:** Inject `ContentCacheProperties` into `ContentCacheService` and update `cachedVideoOrThrow()` to return cached video only when `Duration.between(cache.refreshedAt(), now) <= videoFallbackTtl`.

**Step 4:** Add `reelshort.content.cache.video-fallback-ttl=${REELSHORT_CONTENT_VIDEO_FALLBACK_TTL:10m}` to application properties.

**Step 5:** Rerun the content cache service tests.

### Task 2: Config and Docs

**Files:**
- Modify: `docs/api/content-cache.md`
- Modify: `docs/deploy/release-checklist.md`
- Modify: `docs/deploy/README.md`
- Modify: `infra/.env.example`
- Modify: `infra/docker-compose.yml`
- Modify: `AGENTS.md`

**Step 1:** Document that playback URL cache is a short-lived 5xx fallback only, controlled by `REELSHORT_CONTENT_VIDEO_FALLBACK_TTL`, default `10m`.

**Step 2:** Update AGENTS module description and change history.

**Step 3:** Run:

```powershell
backend\.\gradlew.bat test --no-daemon
git diff --check
```

### Task 3: Review, Fix, Merge

**Step 1:** Review the diff for stale cache edge cases, property binding, test coverage, and docs consistency.

**Step 2:** Fix any findings and rerun affected tests.

**Step 3:** Commit, push the branch, merge to `master`, verify on merged master, push master, and clean the worktree.
