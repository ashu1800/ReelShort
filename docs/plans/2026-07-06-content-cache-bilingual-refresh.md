# Content Cache Bilingual Refresh Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a single admin operation to refresh a selected content shelf for both supported locales (`en` and `zh-TW`) and return per-locale results.

**Architecture:** Keep existing single-locale refresh unchanged. Add a batch admin endpoint that loops through supported locales, calls the existing record-aware refresh service per locale, catches per-locale failures, and returns a result row for each locale. Admin Web adds a separate button so operators can refresh bilingual metadata without changing App APIs.

**Tech Stack:** Spring Boot, JUnit/MockMvc, Vue 3, Element Plus, TypeScript.

---

### Task 1: Backend Batch Refresh Endpoint

**Files:**
- Create: `backend/src/main/java/com/reelshort/backend/content/ContentShelfRefreshResult.java`
- Modify: `backend/src/main/java/com/reelshort/backend/content/AdminContentCacheController.java`
- Test: `backend/src/test/java/com/reelshort/backend/content/AdminContentCacheControllerTests.java`

**Step 1:** Add a failing MockMvc test for `POST /api/admin/content/cache/shelves/recommend/refresh-locales` returning two rows for `en` and `zh-TW`, with one success and one failure.

Run:

```powershell
cd backend
.\gradlew.bat test --tests com.reelshort.backend.content.AdminContentCacheControllerTests --no-daemon
```

Expected: fails because the endpoint does not exist.

**Step 2:** Add `ContentShelfRefreshResult`.

**Step 3:** Implement controller endpoint using the existing `ContentCacheService.refreshShelf(..., ADMIN)` once per locale.

**Step 4:** Record one audit entry summarizing the batch refresh.

**Step 5:** Rerun controller tests.

### Task 2: Admin Web Button

**Files:**
- Modify: `admin-web/src/services/adminApi.ts`
- Modify: `admin-web/src/views/ContentCacheView.vue`

**Step 1:** Add `refreshContentShelfLocales(shelfType)` API client.

**Step 2:** Add a secondary button `刷新双语货架` beside the existing single-locale refresh button.

**Step 3:** Show success message with `成功 N / 失败 M` and reload status.

**Step 4:** Run:

```powershell
cd admin-web
npm run build
```

Expected: build succeeds.

### Task 3: Docs, Review, Merge

**Files:**
- Modify: `docs/api/content-cache.md`
- Modify: `AGENTS.md`

**Step 1:** Document the new batch endpoint and response fields.

**Step 2:** Run:

```powershell
python -m pytest content-provider
cd backend
.\gradlew.bat test --no-daemon
cd ..\admin-web
npm run build
cd ..
git diff --check
```

**Step 3:** Review the diff, fix findings, commit, merge to `master`, and push.
