# Android Watch Reward Hints Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Show lightweight watch reward stage hints on the Android player page using existing playback progress and reported progress state.

**Architecture:** Keep backend and `app-core` unchanged. Add pure UI helper functions in `MainActivity.kt`, cover them with JVM unit tests, then render the hint in `PlayerScreen`.

**Tech Stack:** Kotlin, Jetpack Compose, existing Android JVM unit tests.

---

### Task 1: Reward Hint Contract

**Files:**
- Create: `android-app/app/src/test/kotlin/com/reelshort/app/WatchRewardHintContractTest.kt`
- Modify: `android-app/app/src/main/java/com/reelshort/app/MainActivity.kt`

**Step 1: Write failing tests**

Cover:
- 0% progress points to 25%
- 20% progress says 5% remaining to 25%
- 25% progress with 0% reported is action ready
- 50% progress with 25% reported is action ready for 50%
- 100% reported says all rewards completed

**Step 2: Verify red**

Run:

```powershell
cd android-app
.\gradlew.bat :app:testDebugUnitTest --no-daemon
```

Expected: fails because `watchRewardHint` does not exist.

**Step 3: Implement helper**

Add:

```kotlin
internal data class WatchRewardHint(
    val title: String,
    val message: String,
    val actionReady: Boolean,
)
```

Add `watchRewardHint(progressPercent, lastReportedProgressPercent)`.

**Step 4: Verify green**

Run the same test command. Expected: pass.

### Task 2: Player UI Wiring

**Files:**
- Modify: `android-app/app/src/main/java/com/reelshort/app/MainActivity.kt`
- Modify: `android-app/README.md`
- Modify: `AGENTS.md`

**Step 1: Render hint**

In `PlayerScreen`, compute hint from `playback.progressPercent` and `playback.lastReportedProgressPercent`.

**Step 2: Add panel**

Render title, message, and `MetaPill(if (actionReady) "可上报" else "继续观看")`.

**Step 3: Keep behavior unchanged**

Do not call `onReportProgress` automatically.

**Step 4: Update docs**

AGENTS and README should mention player reward hints.

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
- no backend/app-core changes
- no automatic reporting
- text fits within compact player page panels

Commit:

```powershell
git add .
git commit -m "feat(android): show watch reward hints"
```
