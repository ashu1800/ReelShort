# Android UI Controller Wiring Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Wire the Android Compose UI to the existing `AppStateController` instead of local sample state.

**Architecture:** Keep business state and network orchestration in `app-core`. Add a small testable UI action facade in `app-core`, then make `app` collect `AppUiState` and call facade/controller actions from Compose.

**Tech Stack:** Kotlin, Jetpack Compose, Kotlin coroutines, StateFlow, existing OkHttp client and app-core Repository.

---

### Task 1: Add Testable UI Action Facade

**Files:**
- Create: `android-app/app-core/src/main/kotlin/com/reelshort/app/state/AppUiActions.kt`
- Create: `android-app/app-core/src/test/kotlin/com/reelshort/app/state/AppUiActionsTest.kt`

**Step 1: Write failing tests**

Cover:

- `login(username, password)` delegates to controller and moves state to home.
- `search(query)` delegates to controller and records query.
- `openBook(book)` delegates to controller and moves state to detail.
- `logout()` delegates to controller and resets state to login.

Use fake `AppDataSource` similar to existing `AppStateControllerTest`.

**Step 2: Run failing test**

```powershell
cd android-app
.\gradlew.bat :app-core:test --tests "*AppUiActionsTest" --no-daemon
```

Expected: fail because `AppUiActions` does not exist.

**Step 3: Implement facade**

`AppUiActions` wraps `AppStateController` and exposes suspend methods with UI-friendly names:

- `restoreSession`
- `login`
- `register`
- `refreshHome`
- `showSearch`
- `search`
- `openBook`
- `openPlayer`
- `reportProgress`
- `loadAccount`
- `logout`
- `clearError`

**Step 4: Run tests**

```powershell
cd android-app
.\gradlew.bat :app-core:test --tests "*AppUiActionsTest" --no-daemon
```

Expected: pass.

### Task 2: Wire Compose UI to Controller State

**Files:**
- Modify: `android-app/app/src/main/java/com/reelshort/app/MainActivity.kt`

**Step 1: Add app factory**

Create a small app-level factory function in `MainActivity.kt` that builds:

- `ApiConfig`
- `OkHttpReelShortApiClient`
- `InMemorySessionStore`
- `AppRepository`
- `AppStateController`
- `AppUiActions`

**Step 2: Replace local sample state**

Replace `AppState.sample()` state with:

- `val uiState by controller.state.collectAsState()`
- `LaunchedEffect(Unit) { actions.restoreSession() }`
- button handlers launching coroutines to call `actions`

**Step 3: Render from `AppUiState`**

Use `AppUiState.homeShelf`, `searchResults`, `selectedBook`, `episodes`, `currentVideoUrl`, `watchHistory`, `pointAccount`, and `orders`.

**Step 4: Remove local sample model**

Remove local `AppState` and local `AppScreen` from `MainActivity.kt`; use `com.reelshort.app.state.AppScreen`.

### Task 3: Sync Android Docs and AGENTS

**Files:**
- Modify: `android-app/README.md`
- Modify: `AGENTS.md`

**Step 1: Update docs**

Document that Compose UI now consumes `AppStateController` and `AppUiState`; Android platform persistence is still in-memory until DataStore is introduced.

**Step 2: Update AGENTS**

Add top history:

```text
[2026-06-27] android-app/ui - 将 Compose UI 接入 `AppStateController`/`AppUiState`，移除页面本地 sample 状态源。
```

### Task 4: Review, Verify, Commit, Merge

**Step 1: Review**

Run:

```powershell
git diff --check
git diff --stat
rg -n "AppState.sample|private data class AppState|private enum class AppScreen" android-app/app/src/main/java/com/reelshort/app/MainActivity.kt
```

Expected: no sample state matches remain.

**Step 2: Verification**

Run:

```powershell
cd android-app
.\gradlew.bat :app-core:test --no-daemon
cd ..\backend
.\gradlew.bat test --no-daemon
cd ..\content-provider
pytest
cd ..\admin-web
npm ci
npm run build
```

Do not claim Android UI module compile because current machine has no Android SDK.

**Step 3: Commit and merge**

Commit on `feature/android-ui-controller-wiring`, merge into `master`, clean worktree and branch.
