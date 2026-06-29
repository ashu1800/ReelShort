# Android API Diagnostics Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a lightweight Android-side API connection diagnostics panel for local Spring Boot and LDPlayer testing.

**Architecture:** Keep diagnostics in `app-core` so networking, repository, state, and UI stay separated. The App still only talks to Spring Boot; diagnostics uses the existing public `/api/system/health` endpoint and never contacts Flask directly.

**Tech Stack:** Kotlin, kotlinx.serialization, OkHttp, Jetpack Compose, Gradle unit tests.

---

### Task 1: API Config Health URL

**Files:**
- Modify: `android-app/app-core/src/main/kotlin/com/reelshort/app/config/ApiConfig.kt`
- Test: `android-app/app-core/src/test/kotlin/com/reelshort/app/config/ApiConfigTest.kt`

**Step 1: Write failing tests**

Add tests for:
- default App API base URL derives `http://10.0.2.2:8080/api/system/health`
- trailing slash is trimmed
- non-standard base URL falls back to appending `/system/health`

**Step 2: Run red test**

Run:

```powershell
cd android-app
.\gradlew.bat :app-core:test --tests com.reelshort.app.config.ApiConfigTest --no-daemon
```

Expected: fails because `systemHealthUrl` does not exist.

**Step 3: Implement**

Add `val systemHealthUrl: String` to `ApiConfig`.

**Step 4: Verify**

Run the same test and expect success.

### Task 2: Network Health Check

**Files:**
- Modify: `android-app/app-core/src/main/kotlin/com/reelshort/app/data/AppModels.kt`
- Modify: `android-app/app-core/src/main/kotlin/com/reelshort/app/network/ReelShortApiClient.kt`
- Modify: `android-app/app-core/src/main/kotlin/com/reelshort/app/network/FakeReelShortApiClient.kt`
- Modify: `android-app/app-core/src/main/kotlin/com/reelshort/app/network/OkHttpReelShortApiClient.kt`
- Test: `android-app/app-core/src/test/kotlin/com/reelshort/app/network/OkHttpReelShortApiClientTest.kt`

**Step 1: Write failing tests**

Add MockWebServer tests verifying:
- `checkSystemHealth()` returns status `UP`
- request path is `/api/system/health`
- request has no `Authorization` header

**Step 2: Run red test**

Run:

```powershell
cd android-app
.\gradlew.bat :app-core:test --tests com.reelshort.app.network.OkHttpReelShortApiClientTest --no-daemon
```

Expected: fails because client method does not exist.

**Step 3: Implement**

Add `ApiHealthStatus` model and implement client parsing.

**Step 4: Verify**

Run the same test and expect success.

### Task 3: Repository and State

**Files:**
- Modify: `android-app/app-core/src/main/kotlin/com/reelshort/app/data/AppDataSource.kt`
- Modify: `android-app/app-core/src/main/kotlin/com/reelshort/app/data/AppRepository.kt`
- Modify: `android-app/app-core/src/main/kotlin/com/reelshort/app/state/AppUiState.kt`
- Modify: `android-app/app-core/src/main/kotlin/com/reelshort/app/state/AppStateController.kt`
- Modify: `android-app/app-core/src/main/kotlin/com/reelshort/app/state/AppUiActions.kt`
- Test: `android-app/app-core/src/test/kotlin/com/reelshort/app/state/AppStateControllerTest.kt`

**Step 1: Write failing tests**

Add a state controller test verifying `checkApiHealth()` writes `apiHealthStatus` into state and exposes the configured API base URL.

**Step 2: Run red test**

Run:

```powershell
cd android-app
.\gradlew.bat :app-core:test --tests com.reelshort.app.state.AppStateControllerTest --no-daemon
```

Expected: fails because state/action methods do not exist.

**Step 3: Implement**

Thread health check through data source, repository, controller, and actions.

**Step 4: Verify**

Run the same test and expect success.

### Task 4: Compose Account Panel

**Files:**
- Modify: `android-app/app/src/main/java/com/reelshort/app/MainActivity.kt`
- Test: `android-app/app/src/test/kotlin/com/reelshort/app/ApiDiagnosticsTextTest.kt`
- Modify: `AGENTS.md`
- Modify: `android-app/README.md`

**Step 1: Write failing text helper test**

Create a small helper for status label text and test unknown/up/down states.

**Step 2: Run red test**

Run:

```powershell
cd android-app
.\gradlew.bat :app:testDebugUnitTest --tests com.reelshort.app.ApiDiagnosticsTextTest --no-daemon
```

Expected: fails because helper does not exist.

**Step 3: Implement UI**

Add an account page `SurfacePanel` showing API base URL, health status, health message, and a refresh button.

**Step 4: Verify**

Run:

```powershell
cd android-app
.\gradlew.bat :app-core:test --no-daemon
.\gradlew.bat :app:testDebugUnitTest --no-daemon
.\gradlew.bat :app:assembleDebug --no-daemon
```

Install and launch on LDPlayer, then check logcat for no critical crash.

### Task 5: Review, Commit, Merge

**Files:**
- All changed files.

**Step 1: Review**

Check for:
- App does not call Flask directly.
- Health check is unauthenticated.
- UI text fits account page.
- Docs and AGENTS are synchronized.

**Step 2: Verify**

Run:

```powershell
git diff --check
git status --short --branch
```

**Step 3: Commit**

```powershell
git add .
git commit -m "feat(android): add api diagnostics"
```

**Step 4: Merge to master**

```powershell
cd C:\Users\Ashu\Desktop\ReelShort
git merge --no-ff feature/android-api-diagnostics -m "merge: android api diagnostics"
```

**Step 5: Re-run master verification and clean worktree**

Run the Android verification commands on `master`, install to LDPlayer, then remove the worktree and branch.
