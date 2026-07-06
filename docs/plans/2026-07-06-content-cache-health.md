# Content Cache Health Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add operator-friendly health signals to content shelf cache status so admins can immediately see missing, empty, stale, failed, and healthy shelf/locale combinations.

**Architecture:** Do not change the database schema. Compute shelf health from existing `content_shelf_cache` fields: `itemCount`, `refreshedAt`, and `lastError`. Extend the admin content cache status response and render the signal in the existing admin Web content cache page.

**Tech Stack:** Spring Boot, JPA, JUnit, Vue 3, Element Plus, TypeScript.

---

### Task 1: Backend Shelf Health Contract

**Files:**
- Create: `backend/src/main/java/com/reelshort/backend/content/ContentShelfCacheHealth.java`
- Modify: `backend/src/main/java/com/reelshort/backend/content/ContentCacheStatusResponse.java`
- Modify: `backend/src/main/java/com/reelshort/backend/content/ContentCacheService.java`
- Test: `backend/src/test/java/com/reelshort/backend/content/ContentCacheServiceTests.java`

**Step 1:** Add failing tests for `MISSING`, `EMPTY`, `ERROR`, `STALE`, and `HEALTHY` shelf health.

Run:

```powershell
cd backend
.\gradlew.bat test --tests com.reelshort.backend.content.ContentCacheServiceTests --no-daemon
```

Expected: fails because the response does not expose health.

**Step 2:** Add `ContentShelfCacheHealth`.

**Step 3:** Add `health` and `healthMessage` to `ContentCacheStatusResponse.ShelfStatus`.

**Step 4:** Compute health in `ContentCacheService.status()` with precedence `ERROR > MISSING > EMPTY > STALE > HEALTHY`, where stale means older than 12 hours.

**Step 5:** Rerun backend content tests.

### Task 2: Admin Web Health Display

**Files:**
- Modify: `admin-web/src/services/adminApi.ts`
- Modify: `admin-web/src/views/ContentCacheView.vue`

**Step 1:** Extend TypeScript `ContentCacheStatus.shelves[]` with `health` and `healthMessage`.

**Step 2:** Add a health tag column before item count.

**Step 3:** Use Element Plus tag types: `success` for `HEALTHY`, `warning` for `EMPTY`/`STALE`/`MISSING`, and `danger` for `ERROR`.

**Step 4:** Run:

```powershell
cd admin-web
npm run build
```

Expected: build succeeds.

### Task 3: Documentation, Review, Merge

**Files:**
- Modify: `docs/api/content-cache.md`
- Modify: `AGENTS.md`

**Step 1:** Document new status fields.

**Step 2:** Run:

```powershell
cd backend
.\gradlew.bat test --no-daemon
cd ..\admin-web
npm run build
cd ..
git diff --check
```

**Step 3:** Review the full diff, fix findings, commit, merge to `master`, and push.
