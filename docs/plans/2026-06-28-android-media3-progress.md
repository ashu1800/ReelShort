# Android Media3 Progress Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Feed Media3 player position into the existing `PlaybackState` so manual progress reporting uses real player progress instead of only the debug simulation button.

**Architecture:** Keep playback business state in `app-core`. Add pure conversion helpers and a lightweight polling loop in the Android `app` module's `MediaPlayerSurface`.

**Tech Stack:** Kotlin, Jetpack Compose, Media3 ExoPlayer, coroutines, existing JVM unit tests.

---

### Task 1: Progress Conversion Contract

**Files:**
- Create: `android-app/app/src/test/kotlin/com/reelshort/app/MediaProgressContractTest.kt`
- Modify: `android-app/app/src/main/java/com/reelshort/app/MainActivity.kt`

**Step 1: Write failing tests**

Cover:
- negative position milliseconds converts to 0 seconds
- `1500ms` converts to 1 second
- unknown duration milliseconds falls back to backend duration
- known duration milliseconds converts to seconds

**Step 2: Verify red**

Run:

```powershell
cd android-app
.\gradlew.bat :app:testDebugUnitTest --no-daemon
```

Expected: fails because helpers do not exist.

**Step 3: Implement helpers**

Add:

```kotlin
internal fun mediaPositionSeconds(positionMs: Long): Int
internal fun mediaDurationSeconds(durationMs: Long, fallbackDurationSeconds: Int): Int
```

**Step 4: Verify green**

Run the same test command. Expected: pass.

### Task 2: Media3 Polling

**Files:**
- Modify: `android-app/app/src/main/java/com/reelshort/app/MainActivity.kt`
- Modify: `android-app/README.md`
- Modify: `AGENTS.md`

**Step 1: Update `MediaPlayerSurface` signature**

Add:

```kotlin
fallbackDurationSeconds: Int,
onProgress: (positionSeconds: Int, durationSeconds: Int) -> Unit
```

**Step 2: Wire `PlayerScreen`**

Pass `duration` and `onUpdatePlaybackPosition`.

**Step 3: Add polling effect**

Use `LaunchedEffect(player, fallbackDurationSeconds)`:

```kotlin
while (true) {
    val durationSeconds = mediaDurationSeconds(player.duration, fallbackDurationSeconds)
    if (durationSeconds > 0) {
        onProgress(mediaPositionSeconds(player.currentPosition), durationSeconds)
    }
    kotlinx.coroutines.delay(1000)
}
```

**Step 4: Keep debug simulation**

Rename button text from `模拟 25%` to `同步 25%`.

**Step 5: Update docs**

AGENTS and README should mention Media3 progress sync.

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
- no `app-core` contract changes
- polling loop scoped to player composition
- no automatic backend progress reporting
- UI text still fits

Commit:

```powershell
git add .
git commit -m "feat(android): sync media3 playback progress"
```
