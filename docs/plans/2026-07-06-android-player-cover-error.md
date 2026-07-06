# Android Player Cover And Error UX Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Show a cover-backed loading state before the first playable frame and provide clear recovery actions when video playback fails.

**Architecture:** Keep `PlayerScreen` as the full-screen owner and extend `MediaPlayerSurface` with cover URL, retry, next episode, and back callbacks. Add small format/contract helpers for overlay mode and error actions so behavior is testable without Compose instrumentation.

**Tech Stack:** Kotlin, Jetpack Compose, Material3, Media3/ExoPlayer, Coil Compose, JVM contract tests.

---

### Task 1: Player Overlay Contract

**Files:**
- Modify: `android-app/app/src/main/java/com/reelshort/app/ui/format/UiFormats.kt`
- Modify: `android-app/app/src/test/kotlin/com/reelshort/app/EpisodeSelectorTextContractTest.kt`

**Step 1:** Add failing tests for:
- First ready not reached => overlay mode `COVER_LOADING`.
- Ready after first frame and `STATE_READY` => overlay mode `NONE`.
- Buffering after first ready => overlay mode `BUFFERING`.
- Error => overlay mode `ERROR`.
- Current episode has next episode => next action visible; last episode => next action hidden.

Run:

```powershell
cd android-app
.\gradlew.bat :app:testDebugUnitTest --tests com.reelshort.app.EpisodeSelectorTextContractTest --no-daemon
```

Expected: fails because helpers do not exist yet.

**Step 2:** Add `PlayerOverlayMode` and helpers in `UiFormats.kt`:
- `playerOverlayMode(playableUrl, playbackState, hasFirstReady, hasError)`
- `playerErrorNextEpisode(currentEpisode, episodes)`

**Step 3:** Keep `playerLoadingOverlayVisible()` as compatibility wrapper around the new helper.

### Task 2: Player UI Implementation

**Files:**
- Modify: `android-app/app/src/main/java/com/reelshort/app/ui/screens/player/PlayerScreen.kt`
- Modify: `android-app/app/src/main/java/com/reelshort/app/ui/format/AppStrings.kt`

**Step 1:** Extend `PlayerScreen` to pass `selectedBook?.coverUrl`, current episode, episodes, `onBack`, and `onOpenPlayer` into `MediaPlayerSurface`.

**Step 2:** Add Coil `AsyncImage` cover background for `COVER_LOADING`, with dark scrim and existing loading label.

**Step 3:** For `BUFFERING`, show a compact translucent loading capsule instead of a full cover overlay.

**Step 4:** For `ERROR`, show an error panel with:
- retry current episode,
- next episode when available,
- back.

**Step 5:** Add localized strings for retry, next episode, and back labels if existing copy is insufficient.

### Task 3: Verification, Review, Merge

**Step 1:** Run:

```powershell
android-app\.\gradlew.bat :app-core:test :app:testDebugUnitTest :app:assembleDebug --no-daemon
git diff --check
```

**Step 2:** Install latest APK:

```powershell
adb install -r android-app\app\build\outputs\apk\debug\app-debug.apk
```

Use the available emulator adb if standard `adb` is not on PATH.

**Step 3:** Manual emulator checks:
- Open a drama from Home and confirm cover-backed loading appears before video frame.
- Switch episodes and confirm loading/selection still works.
- Confirm existing back, reward badge, like/favorite/comment, and episode sheet remain usable.
- If playback error can be induced, confirm retry/next/back actions are visible.

**Step 4:** Review the full diff for UI overlap, state regressions, localization, and test coverage; fix findings and rerun verification.

**Step 5:** Commit, push branch, merge to `master`, verify on merged `master`, push `master`, and clean worktree.
