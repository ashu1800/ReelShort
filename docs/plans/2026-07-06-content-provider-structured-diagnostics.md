# Content Provider Structured Diagnostics Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Expose content-provider diagnostic events as structured backend runtime data and render them clearly in the admin runtime diagnostics page.

**Architecture:** Keep the existing dependency `detail` string for compatibility with current alerts, but add structured `contentProviderDiagnostics` to `SystemRuntimeResponse`. The content-provider checker owns fetching and normalizing `/diagnostics`; `SystemRuntimeService` attaches that data when the checker is present. Admin Web renders a focused operations panel without changing App APIs.

**Tech Stack:** Spring Boot, RestClient, JUnit/MockRestServiceServer, Vue 3, Element Plus, TypeScript.

---

### Task 1: Add Backend Structured Diagnostics Contract

**Files:**
- Modify: `backend/src/main/java/com/reelshort/backend/system/runtime/SystemRuntimeResponse.java`
- Modify: `backend/src/main/java/com/reelshort/backend/system/runtime/ContentProviderRuntimeDependencyChecker.java`
- Modify: `backend/src/main/java/com/reelshort/backend/system/runtime/SystemRuntimeService.java`
- Test: `backend/src/test/java/com/reelshort/backend/system/ContentProviderRuntimeDependencyCheckerTests.java`

**Step 1:** Write a failing test asserting the content-provider checker exposes total events, counters, and recent events from `/diagnostics`.

Run:

```powershell
cd backend
.\gradlew.bat test --tests com.reelshort.backend.system.ContentProviderRuntimeDependencyCheckerTests --no-daemon
```

Expected: fails because no structured diagnostics accessor exists.

**Step 2:** Add `SystemRuntimeResponse.ContentProviderDiagnostics` and `ContentProviderDiagnosticEvent` records.

**Step 3:** Make `ContentProviderRuntimeDependencyChecker` cache the latest diagnostics from `/diagnostics` during `check()`, while preserving the existing detail summary.

**Step 4:** Add diagnostics to `SystemRuntimeService.snapshot()`.

**Step 5:** Rerun the test and confirm it passes.

### Task 2: Cover Runtime API Serialization

**Files:**
- Modify: `backend/src/test/java/com/reelshort/backend/system/SystemRuntimeControllerTests.java`

**Step 1:** Add a controller assertion that `/api/admin/system/runtime` includes nullable `contentProviderDiagnostics`.

**Step 2:** Run:

```powershell
cd backend
.\gradlew.bat test --tests com.reelshort.backend.system.SystemRuntimeControllerTests --no-daemon
```

Expected: pass after Task 1.

### Task 3: Render Admin Diagnostics Panel

**Files:**
- Modify: `admin-web/src/services/adminApi.ts`
- Modify: `admin-web/src/views/SystemRuntimeView.vue`

**Step 1:** Extend TypeScript runtime types with `contentProviderDiagnostics`.

**Step 2:** Add a runtime page panel showing total events, type counters, and recent event rows.

**Step 3:** Keep the dependency table visible below the panel.

**Step 4:** Run:

```powershell
cd admin-web
npm run build
```

Expected: build succeeds.

### Task 4: Documentation and Final Verification

**Files:**
- Modify: `docs/api/admin.md`
- Modify: `docs/api/content-cache.md`
- Modify: `AGENTS.md`

**Step 1:** Document the new runtime response field and admin behavior.

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

**Step 3:** Review the diff, fix findings, then commit, merge to `master`, and push.
