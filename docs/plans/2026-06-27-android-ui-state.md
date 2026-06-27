# Android UI State Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a pure Kotlin Android UI state control layer so Compose can later bind to repository-backed app flows without embedding API orchestration in UI code.

**Architecture:** Add an `AppDataSource` interface implemented by `AppRepository`, then add `AppUiState` and `AppStateController` in `app-core`. The controller exposes `StateFlow<AppUiState>` and coordinates login, home, search, detail, player, watch progress, points, history, and orders.

**Tech Stack:** Kotlin JVM, kotlinx.coroutines `StateFlow`, kotlinx.coroutines test, kotlin.test.

---

### Task 1: Add State Controller Tests

**Files:**
- Create: `android-app/app-core/src/test/kotlin/com/reelshort/app/state/AppStateControllerTest.kt`

**Step 1: Write failing tests**

Add tests for:

- initial state is login screen
- login success stores session and loads home shelf
- login failure records error and keeps login screen
- search updates query and results
- open book loads episodes
- open player loads video URL
- report progress refreshes history and points

**Step 2: Run test to verify it fails**

Run:

```powershell
cd android-app
.\gradlew.bat :app-core:test --no-daemon
```

Expected: compilation fails because `AppStateController`, `AppUiState`, `AppScreen`, and `AppDataSource` do not exist.

### Task 2: Add Data Source Boundary

**Files:**
- Create: `android-app/app-core/src/main/kotlin/com/reelshort/app/data/AppDataSource.kt`
- Modify: `android-app/app-core/src/main/kotlin/com/reelshort/app/data/AppRepository.kt`

**Step 1: Implement interface**

Create `AppDataSource` with the same app-level methods already exposed by `AppRepository`.

**Step 2: Make repository implement it**

Change `class AppRepository(...)` to `class AppRepository(...) : AppDataSource`.

### Task 3: Add UI State Model

**Files:**
- Create: `android-app/app-core/src/main/kotlin/com/reelshort/app/state/AppUiState.kt`

**Step 1: Implement model**

Add `AppScreen` enum and `AppUiState` data class with session, screen, loading, error, content, player, account, and order fields.

### Task 4: Add State Controller

**Files:**
- Create: `android-app/app-core/src/main/kotlin/com/reelshort/app/state/AppStateController.kt`

**Step 1: Implement controller**

Use `MutableStateFlow` internally and expose `StateFlow`. Add methods:

- `login`
- `register`
- `refreshHome`
- `search`
- `openBook`
- `openPlayer`
- `reportProgress`
- `loadAccountSnapshot`
- `clearError`

All methods should set loading, clear previous error, catch exceptions, preserve existing data on failure, and update the state atomically.

**Step 2: Run tests**

Run:

```powershell
cd android-app
.\gradlew.bat :app-core:test --no-daemon
```

Expected: all `app-core` tests pass.

### Task 5: Update Documentation

**Files:**
- Modify: `android-app/README.md`
- Modify: `AGENTS.md`

**Step 1: Document new state layer**

Update Android module description and add AGENTS change history:

```text
[2026-06-27] android-app/state - 增加纯 Kotlin UI 状态控制层、Repository 数据源边界和 JVM 状态流测试。
```

### Task 6: Review, Verify, Commit, Merge

**Step 1: Review diff**

Run:

```powershell
git diff --check
git diff --stat
```

Then inspect changed files for correctness.

**Step 2: Full verification**

Run:

```powershell
cd android-app
.\gradlew.bat :app-core:test --no-daemon
```

Optionally run backend, admin-web, and content-provider regression checks if the diff touches shared files beyond Android docs.

**Step 3: Commit and merge**

Commit on `feature/android-ui-state`, then merge into `master` from the root repository.
