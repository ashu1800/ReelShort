# Content Cache Operations Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a first operational loop for content cache refresh health and recent refresh history.

**Architecture:** Persist refresh runs in PostgreSQL, record both admin and scheduled refresh attempts through one service, expose locale-aware cache health through the existing admin content cache API, and render it in the existing admin Web content cache page. Keep App APIs and Android unchanged.

**Tech Stack:** Spring Boot, JPA, Flyway, MockMvc/JUnit, Vue 3, Element Plus, TypeScript, PowerShell verification script.

---

### Task 1: Persist Content Refresh Runs

**Files:**
- Create: `backend/src/main/resources/db/migration/V7__content_refresh_runs.sql`
- Create: `backend/src/main/java/com/reelshort/backend/content/ContentRefreshTriggerSource.java`
- Create: `backend/src/main/java/com/reelshort/backend/content/ContentRefreshRunStatus.java`
- Create: `backend/src/main/java/com/reelshort/backend/content/ContentRefreshRun.java`
- Create: `backend/src/main/java/com/reelshort/backend/content/ContentRefreshRunRepository.java`
- Test: `backend/src/test/java/com/reelshort/backend/system/DatabaseMigrationTests.java`

**Step 1:** Add failing migration test asserting `content_refresh_runs` exists.

Run:

```powershell
backend\.\gradlew.bat test --tests com.reelshort.backend.system.DatabaseMigrationTests --no-daemon
```

Expected: fails because table is missing.

**Step 2:** Add Flyway migration and JPA entity/repository.

**Step 3:** Run the migration test again.

Expected: pass.

### Task 2: Record Refresh Runs in Backend

**Files:**
- Modify: `backend/src/main/java/com/reelshort/backend/content/ContentCacheService.java`
- Modify: `backend/src/main/java/com/reelshort/backend/content/ContentMetadataRefreshService.java`
- Modify: `backend/src/main/java/com/reelshort/backend/content/AdminContentCacheController.java`
- Test: `backend/src/test/java/com/reelshort/backend/content/ContentCacheServiceTests.java`
- Test: `backend/src/test/java/com/reelshort/backend/content/ContentMetadataRefreshServiceTests.java`

**Step 1:** Write failing tests for successful admin refresh and provider failure creating refresh run records.

**Step 2:** Implement a record-aware `ContentCacheService.refreshShelf(...)` overload wrapping the existing refresh behavior.

**Step 3:** Wire admin and scheduled refresh through the new service.

**Step 4:** Run content tests.

Expected: pass.

### Task 3: Expand Admin Cache Status Contract

**Files:**
- Modify: `backend/src/main/java/com/reelshort/backend/content/ContentCacheStatusResponse.java`
- Modify: `backend/src/main/java/com/reelshort/backend/content/ContentCacheService.java`
- Test: `backend/src/test/java/com/reelshort/backend/content/ContentCacheServiceTests.java`
- Test: `backend/src/test/java/com/reelshort/backend/content/AdminContentCacheControllerTests.java`
- Modify: `docs/api/content-cache.md`

**Step 1:** Write failing tests expecting shelf `locale`, `videoCacheCount`, and `recentRefreshRuns`.

**Step 2:** Implement response fields and query recent refresh runs.

**Step 3:** Update API docs.

**Step 4:** Run backend content tests.

Expected: pass.

### Task 4: Update Admin Web Content Cache Page

**Files:**
- Modify: `admin-web/src/services/adminApi.ts`
- Modify: `admin-web/src/views/ContentCacheView.vue`

**Step 1:** Update TypeScript contract for locale-aware shelf status and refresh run rows.

**Step 2:** Add locale selector and send locale on manual refresh.

**Step 3:** Render shelf locale, video cache count, and recent refresh run table.

**Step 4:** Run admin Web build.

Expected: pass.

### Task 5: Final Verification and Docs

**Files:**
- Modify: `AGENTS.md`
- Optional Modify: `docs/deploy/release-checklist.md`

**Step 1:** Update `AGENTS.md` change history.

**Step 2:** Run:

```powershell
python -m pytest content-provider
backend\.\gradlew.bat test --no-daemon
cd admin-web; npm run build
powershell -ExecutionPolicy Bypass -File scripts\verify-release-baseline.ps1 -SkipAndroid
git diff --check
```

**Step 3:** Commit and merge to `master`.
