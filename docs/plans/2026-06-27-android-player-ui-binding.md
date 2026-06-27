# Android Player UI Binding Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Bind the Android Compose player screen to `PlaybackState` and expose refresh/progress/report actions through the existing app action flow.

**Architecture:** Keep playback state ownership in `app-core`. The Android `app` module remains a thin Compose renderer that reads `AppUiState.playback` and calls `AppUiActions` methods.

**Tech Stack:** Kotlin, Jetpack Compose source, app-core JVM tests.

---

### Task 1: Action Flow Test Coverage

**Files:**
- Modify: `android-app/app-core/src/test/kotlin/com/reelshort/app/state/AppUiActionsTest.kt`

**Step 1: Add test**

Add assertions that the action facade can update local playback position, refresh the URL, and report the current playback position through the controller.

**Step 2: Run focused test**

Run:

```powershell
cd android-app
.\gradlew.bat :app-core:test --tests "com.reelshort.app.state.AppUiActionsTest" --no-daemon
```

Expected: pass if existing action facade already exposes needed methods; otherwise fail before implementation.

### Task 2: Compose Player Binding

**Files:**
- Modify: `android-app/app/src/main/java/com/reelshort/app/MainActivity.kt`

**Step 1: Wire actions**

Pass `onUpdatePlaybackPosition` and `onRefreshPlaybackUrl` from `ReelShortApp` into `MainShell`.

**Step 2: Change PlayerScreen contract**

Replace `PlayerScreen(book, episode, videoUrl, onReportProgress)` with `PlayerScreen(state, onUpdatePlaybackPosition, onRefreshPlaybackUrl, onReportProgress)`.

**Step 3: Render PlaybackState**

Show title, episode, URL, duration, current position, progress percent, last reported percent, and action buttons.

### Task 3: Docs And Metadata

**Files:**
- Modify: `android-app/README.md`
- Modify: `AGENTS.md`

**Step 1: Document UI binding**

Update Android README to say playback page is bound to `PlaybackState` but platform HLS player remains pending.

**Step 2: Update AGENTS**

Prepend changelog entry for `android-app/player-ui`.

### Task 4: Verification And Review

**Commands:**

```powershell
cd android-app
.\gradlew.bat :app-core:test --no-daemon
cd ..\content-provider
pytest
cd ..
git diff --check
rg "currentVideoUrl\\?\\.url|上报 75%" android-app/app/src/main/java/com/reelshort/app/MainActivity.kt
```

Expected:

- app-core tests pass.
- content-provider tests pass.
- diff check passes.
- `rg` finds no old player binding or fixed 75% report text.
