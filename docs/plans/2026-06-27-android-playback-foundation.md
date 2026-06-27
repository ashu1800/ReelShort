# Android Playback Foundation Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build a pure Kotlin playback state foundation for the Android App so the later HLS player can bind to stable state and call existing Spring Boot APIs through `AppStateController`.

**Architecture:** Add `PlaybackState` to `app-core` as part of `AppUiState`, then make `AppStateController` own playback opening, local progress updates, backend progress reporting, and URL refresh. Keep backend and content-provider unchanged.

**Tech Stack:** Kotlin JVM, kotlinx.coroutines, kotlin.test, existing Gradle `app-core` test setup.

---

### Task 1: Playback State Model

**Files:**
- Create: `android-app/app-core/src/main/kotlin/com/reelshort/app/state/PlaybackState.kt`
- Modify: `android-app/app-core/src/main/kotlin/com/reelshort/app/state/AppUiState.kt`
- Test: `android-app/app-core/src/test/kotlin/com/reelshort/app/state/AppStateControllerTest.kt`

**Step 1: Write failing tests**

Add tests asserting that `openPlayer()` initializes `state.playback` with `READY`, the selected book, selected episode, video URL, duration, zero position, and zero progress.

**Step 2: Run test to verify failure**

Run:

```powershell
cd android-app
.\gradlew.bat :app-core:test --tests "com.reelshort.app.state.AppStateControllerTest" --no-daemon
```

Expected: compilation/test failure because `playback` does not exist.

**Step 3: Implement minimal model**

Create `PlaybackStatus` and `PlaybackState`, add `playback: PlaybackState = PlaybackState()` to `AppUiState`, and populate it in `openPlayer()`.

**Step 4: Run test to verify pass**

Run the same `app-core` test command and expect success.

### Task 2: Local Progress Updates

**Files:**
- Modify: `android-app/app-core/src/main/kotlin/com/reelshort/app/state/PlaybackState.kt`
- Modify: `android-app/app-core/src/main/kotlin/com/reelshort/app/state/AppStateController.kt`
- Test: `android-app/app-core/src/test/kotlin/com/reelshort/app/state/AppStateControllerTest.kt`

**Step 1: Write failing tests**

Add tests for `updatePlaybackPosition(positionSeconds, durationSeconds)`, including progress percentage calculation and clamping negative or over-duration positions.

**Step 2: Run test to verify failure**

Expected: compilation failure because `updatePlaybackPosition` does not exist.

**Step 3: Implement minimal behavior**

Add `PlaybackState.withPosition()` and controller `updatePlaybackPosition()`.

**Step 4: Run test to verify pass**

Run focused `app-core` tests.

### Task 3: Progress Reporting And URL Refresh

**Files:**
- Modify: `android-app/app-core/src/main/kotlin/com/reelshort/app/state/AppStateController.kt`
- Test: `android-app/app-core/src/test/kotlin/com/reelshort/app/state/AppStateControllerTest.kt`

**Step 1: Write failing tests**

Add tests asserting that `reportProgress()` updates playback reported fields, and `refreshPlaybackUrl()` reloads the URL while preserving current position.

**Step 2: Run test to verify failure**

Expected: missing method or assertion failure.

**Step 3: Implement minimal behavior**

Update existing `reportProgress()` to sync playback state after backend success. Add `refreshPlaybackUrl()`.

**Step 4: Run test to verify pass**

Run focused `app-core` tests, then full `:app-core:test`.

### Task 4: Docs And Project Metadata

**Files:**
- Modify: `android-app/README.md`
- Modify: `AGENTS.md`

**Step 1: Update docs**

Document the new playback state boundary and prepend the AGENTS change history entry.

**Step 2: Verify**

Run:

```powershell
cd android-app
.\gradlew.bat :app-core:test --no-daemon
cd ..
git diff --check
```

Expected: both commands pass.
