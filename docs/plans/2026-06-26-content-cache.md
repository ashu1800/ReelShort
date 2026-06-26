# Content Cache Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add content shelf APIs, PostgreSQL-backed content cache, and admin cache status/refresh endpoints.

**Architecture:** Keep Spring Boot as the only business entry. Add a content service between controllers and `ContentProvider`; persist shelf results and book indexes in PostgreSQL so App requests can fall back to the last usable cache when Flask is unavailable.

**Tech Stack:** Java 17, Spring Boot 3.4, Spring MVC, Spring Data JPA, H2 tests, PostgreSQL production target.

---

### Task 1: Content Shelf Provider Contract

**Files:**
- Modify: `backend/src/main/java/com/reelshort/backend/content/ContentProvider.java`
- Modify: `backend/src/main/java/com/reelshort/backend/content/FlaskContentProvider.java`
- Create: `backend/src/main/java/com/reelshort/backend/content/ContentShelfType.java`
- Test: `backend/src/test/java/com/reelshort/backend/content/FlaskContentProviderTests.java`

**Steps:**
1. Add failing provider tests for `RECOMMEND`, `NEW_RELEASE`, and `DRAMA_DUB`.
2. Add `ContentShelfType` enum with API values `recommend`, `new-release`, `drama-dub`.
3. Add `getShelf(ContentShelfType shelfType)` to `ContentProvider`.
4. Map enum values to Flask endpoints `/api/v1/reelshort/recommend`, `/newrelease`, `/dramadub`.
5. Run `.\gradlew.bat --no-daemon --console=plain test --tests com.reelshort.backend.content.FlaskContentProviderTests`.

### Task 2: PostgreSQL Content Cache Service

**Files:**
- Create: `backend/src/main/java/com/reelshort/backend/content/ContentShelfCache.java`
- Create: `backend/src/main/java/com/reelshort/backend/content/ContentShelfCacheRepository.java`
- Create: `backend/src/main/java/com/reelshort/backend/content/ContentBookCache.java`
- Create: `backend/src/main/java/com/reelshort/backend/content/ContentBookCacheRepository.java`
- Create: `backend/src/main/java/com/reelshort/backend/content/ContentCacheService.java`
- Create: `backend/src/main/java/com/reelshort/backend/content/ContentCacheStatusResponse.java`
- Test: `backend/src/test/java/com/reelshort/backend/content/ContentCacheServiceTests.java`

**Steps:**
1. Write failing service tests for successful shelf refresh and cache fallback.
2. Persist shelf JSON with Jackson `ObjectMapper`.
3. Upsert book cache rows for returned shelf/search books.
4. Return cached shelf only when provider fails and cached JSON exists.
5. Run `.\gradlew.bat --no-daemon --console=plain test --tests com.reelshort.backend.content.ContentCacheServiceTests`.

### Task 3: App Content Controllers

**Files:**
- Modify: `backend/src/main/java/com/reelshort/backend/content/ContentController.java`
- Create: `backend/src/main/java/com/reelshort/backend/content/HomeController.java`
- Test: `backend/src/test/java/com/reelshort/backend/content/ContentControllerTests.java`

**Steps:**
1. Write failing controller tests for `/api/app/home/recommend` and `/api/app/content/shelves/{shelfType}`.
2. Inject `ContentCacheService` instead of direct `ContentProvider` where cache should be used.
3. Keep existing search, episodes, play response contracts unchanged.
4. Run `.\gradlew.bat --no-daemon --console=plain test --tests com.reelshort.backend.content.ContentControllerTests`.

### Task 4: Admin Cache Operations

**Files:**
- Create: `backend/src/main/java/com/reelshort/backend/content/AdminContentCacheController.java`
- Test: `backend/src/test/java/com/reelshort/backend/content/AdminContentCacheControllerTests.java`
- Modify: `docs/api/admin.md`
- Create: `docs/api/content-cache.md`
- Modify: `AGENTS.md`

**Steps:**
1. Write failing admin MVC tests for cache status, refresh, and App Token denial.
2. Add `GET /api/admin/content/cache`.
3. Add `POST /api/admin/content/cache/shelves/{shelfType}/refresh`.
4. Record audit action `CONTENT_CACHE_REFRESHED` on refresh success.
5. Update API docs and AGENTS change history.
6. Run admin content cache tests.

### Task 5: Review, Full Verification, Commit, Merge

**Files:**
- All touched files.

**Steps:**
1. Stage all changes and run `git diff --cached --check`.
2. Review staged diff for security, stale cache behavior, transaction boundaries, and docs consistency.
3. Run `.\gradlew.bat --no-daemon --console=plain test --rerun-tasks`.
4. Commit with `feat(content): add shelf cache operations`.
5. Merge to `master` with `--no-ff`.
6. Run full tests on `master`.
7. Remove worktree and delete merged branch.
