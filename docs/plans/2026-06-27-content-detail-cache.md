# Content Detail Cache Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add App content detail API and PostgreSQL episode-list cache fallback.

**Architecture:** Keep Spring Boot as the only business entry. `ContentCacheService` owns book-detail reads from `ContentBookCache` and episode-cache persistence. The Flask `ContentProvider` remains unchanged; cache fallback lives inside the content service.

**Tech Stack:** Java 17, Spring Boot 3.4, Spring MVC, Spring Data JPA, Jackson JSON, H2/PostgreSQL-compatible JPA.

---

### Task 1: Content Detail API

**Files:**
- Modify: `backend/src/main/java/com/reelshort/backend/content/ContentCacheService.java`
- Modify: `backend/src/main/java/com/reelshort/backend/content/ContentController.java`
- Test: `backend/src/test/java/com/reelshort/backend/content/ContentControllerTests.java`
- Test: `backend/src/test/java/com/reelshort/backend/content/ContentCacheServiceTests.java`

**Step 1:** Write failing controller/service tests for `GET /api/app/content/books/{bookId}` returning cached `ContentBook`.

**Step 2:** Verify focused tests fail because the route/service method does not exist.

**Step 3:** Implement `ContentCacheService.getBook(bookId)` using `ContentBookCacheRepository`.

**Step 4:** Add controller route returning `ApiResponse<ContentBook>`.

**Step 5:** Run focused tests and confirm pass.

### Task 2: Episode Cache Entity and Repository

**Files:**
- Create: `backend/src/main/java/com/reelshort/backend/content/ContentEpisodeCache.java`
- Create: `backend/src/main/java/com/reelshort/backend/content/ContentEpisodeCacheRepository.java`
- Test: `backend/src/test/java/com/reelshort/backend/content/ContentEpisodeCacheRepositoryTests.java`

**Step 1:** Write failing repository tests for unique `bookId + filteredTitle` and lookup.

**Step 2:** Verify tests fail because entity/repository do not exist.

**Step 3:** Implement JPA entity and repository.

**Step 4:** Run repository tests and confirm pass.

### Task 3: Episode Cache Fallback

**Files:**
- Modify: `backend/src/main/java/com/reelshort/backend/content/ContentCacheService.java`
- Test: `backend/src/test/java/com/reelshort/backend/content/ContentCacheServiceTests.java`

**Step 1:** Write failing service test proving successful `getEpisodes` stores cache.

**Step 2:** Write failing service test proving provider failure returns cached episodes when available.

**Step 3:** Implement JSON serialization/deserialization and fallback behavior.

**Step 4:** Run content service tests and confirm pass.

### Task 4: Status and Documentation

**Files:**
- Modify: `backend/src/main/java/com/reelshort/backend/content/ContentCacheStatusResponse.java`
- Modify: `backend/src/main/java/com/reelshort/backend/content/ContentCacheService.java`
- Modify: `docs/api/backend-foundation.md`
- Modify: `docs/api/content-cache.md`
- Modify: `AGENTS.md`

**Step 1:** Extend cache status response with episode cache count.

**Step 2:** Update API docs and AGENTS module history.

**Step 3:** Run focused content/admin cache tests.

### Task 5: Review, Verification, Merge

**Files:**
- All touched files.

**Step 1:** Run `git diff --check`.

**Step 2:** Run `.\gradlew.bat --no-daemon --console=plain test`.

**Step 3:** Review diff for scope, stale abstractions, and fallback correctness.

**Step 4:** Commit feature branch, merge into `master`, rerun full backend tests on `master`.

**Step 5:** Remove worktree and delete feature branch after merge.
