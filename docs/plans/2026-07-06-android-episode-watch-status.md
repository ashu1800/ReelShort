# Android Episode Watch Status Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add per-episode watch status to the Android player episode selector using existing watch history.

**Architecture:** Keep the state derivation in `UiFormats.kt` and render it in `PlayerScreen.kt`. The UI reads existing `AppUiState.watchHistory`, so no service or backend contract changes are required.

**Tech Stack:** Kotlin, Jetpack Compose, Material3, existing Android app-core models.

---

## Task 1: Add Watch Status Contract Tests

**Files:**
- Modify: `android-app/app/src/test/kotlin/com/reelshort/app/EpisodeSelectorTextContractTest.kt`

**Steps:**

1. Add tests for current, watched, in-progress, and empty states.
2. Add tests for English and Traditional Chinese labels.
3. Run the focused test and verify it fails because helpers are missing.

Command:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.reelshort.app.EpisodeSelectorTextContractTest --no-daemon
```

## Task 2: Implement Helper Contract

**Files:**
- Modify: `android-app/app/src/main/java/com/reelshort/app/ui/format/UiFormats.kt`
- Modify: `android-app/app/src/main/java/com/reelshort/app/ui/format/AppStrings.kt`

**Steps:**

1. Add `EpisodeWatchStatusType`.
2. Add `EpisodeWatchStatus`.
3. Add `episodeWatchStatus(...)`.
4. Add `episodeWatchStatusLabel(...)`.
5. Run the focused test and verify it passes.

## Task 3: Render Status in Episode Sheet

**Files:**
- Modify: `android-app/app/src/main/java/com/reelshort/app/ui/screens/player/PlayerScreen.kt`

**Steps:**

1. Pass `bookId` and `watchHistory` into `EpisodeSelectorBottomSheet`.
2. Compute a status for each episode.
3. Update `EpisodeSelectorItem` to show number, status label, and progress accent.
4. Preserve current item click behavior and bottom sheet layout.

## Task 4: Verification

Run:

```powershell
.\gradlew.bat :app-core:test :app:testDebugUnitTest :app:assembleDebug --no-daemon
git diff --check
adb install -r android-app\app\build\outputs\apk\debug\app-debug.apk
```

Manual emulator checks:

- Open a drama from Home into player.
- Open episode selector.
- Current episode is labeled.
- Watched or partially watched records show status if history exists.
- Tap another episode and verify playback still switches.

## Task 5: Review and Merge

1. Review changed files for UI overlap, touch targets, status priority, and localization.
2. Fix review findings.
3. Rerun verification.
4. Commit, push, merge to `master`, rerun merged verification, install APK again.

