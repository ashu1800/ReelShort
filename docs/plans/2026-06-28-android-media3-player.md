# Android Media3 Player Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace the static Android player placeholder with a Media3 ExoPlayer-backed player container while preserving the existing Spring Boot playback URL and progress-reporting boundaries.

**Architecture:** Keep the platform player in the Android `app` module. `app-core` stays pure Kotlin and unchanged. Add a tiny URL helper for unit-testable guardrails, then embed Media3 `PlayerView` through Compose `AndroidView`.

**Tech Stack:** Kotlin, Jetpack Compose, AndroidX Media3 ExoPlayer, AndroidView, existing JVM unit tests.

---

### Task 1: URL Contract

**Files:**
- Create: `android-app/app/src/test/kotlin/com/reelshort/app/PlayableMediaUrlContractTest.kt`
- Modify: `android-app/app/src/main/java/com/reelshort/app/MainActivity.kt`

**Step 1: Write failing tests**

Cover:
- null/blank URL returns null
- leading/trailing whitespace is trimmed
- `http://` and `https://` URLs are accepted
- non-HTTP URLs are rejected

**Step 2: Verify red**

Run:

```powershell
cd android-app
.\gradlew.bat :app:testDebugUnitTest --no-daemon
```

Expected: fails because `playableMediaUrlOrNull` does not exist.

**Step 3: Implement helper**

Add:

```kotlin
internal fun String?.playableMediaUrlOrNull(): String?
```

**Step 4: Verify green**

Run the same test command. Expected: pass.

### Task 2: Media3 Player Surface

**Files:**
- Modify: `android-app/app/build.gradle.kts`
- Modify: `android-app/app/src/main/java/com/reelshort/app/MainActivity.kt`
- Modify: `android-app/README.md`
- Modify: `AGENTS.md`

**Step 1: Add dependencies**

```kotlin
implementation("androidx.media3:media3-exoplayer:1.5.1")
implementation("androidx.media3:media3-ui:1.5.1")
```

**Step 2: Add imports**

Use `AndroidView`, `MediaItem`, `ExoPlayer`, and `PlayerView`.

**Step 3: Create `MediaPlayerSurface`**

Use `remember(normalizedUrl)` to create/recreate player, set `MediaItem`, call `prepare()`, set `playWhenReady = false`, and release in `DisposableEffect`.

**Step 4: Wire `PlayerScreen`**

Replace the static HLS placeholder box with `MediaPlayerSurface(videoUrl, episode?.number)`.

**Step 5: Update docs**

Update README and AGENTS to reflect Media3 base player.

### Task 3: Verification and Review

Run sequentially:

```powershell
cd android-app
.\gradlew.bat :app-core:test --no-daemon
.\gradlew.bat :app:testDebugUnitTest --no-daemon
.\gradlew.bat :app:assembleDebug --no-daemon
cd ..
git diff --check
```

Install and launch:

```powershell
C:\leidian\LDPlayer14\adb.exe -s emulator-5554 install -r android-app\app\build\outputs\apk\debug\app-debug.apk
C:\leidian\LDPlayer14\adb.exe -s emulator-5554 shell am start -n com.reelshort.app/.MainActivity
C:\leidian\LDPlayer14\adb.exe -s emulator-5554 logcat -d -t 200 | Select-String -Pattern 'FATAL EXCEPTION|AndroidRuntime'
```

Review for:
- no changes to `app-core` playback contracts
- no direct Flask/content-provider URL access
- player release lifecycle exists
- invalid URL fallback remains visible

Commit:

```powershell
git add .
git commit -m "feat(android): add media3 player surface"
```
