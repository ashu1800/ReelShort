# Android Content States Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Improve Android home/search/detail empty states so real content-source failures or empty responses produce useful, polished UI instead of thin placeholders.

**Architecture:** Keep all behavior in the Android UI module. Add small pure Kotlin helpers beside `MainActivity.kt` so text contracts can be unit tested without Compose UI testing.

**Tech Stack:** Kotlin, Jetpack Compose, Material3, existing Gradle/JVM unit tests.

---

### Task 1: Text Contract

**Files:**
- Create: `android-app/app/src/test/kotlin/com/reelshort/app/ContentEmptyStateContractTest.kt`
- Modify: `android-app/app/src/main/java/com/reelshort/app/MainActivity.kt`

**Step 1: Write failing tests**

Cover:
- home empty state title/action
- search empty state before a query
- search empty state after zero-result query
- detail empty state before selection
- detail empty state after selected book has zero episodes

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
internal data class ContentEmptyState(
    val title: String,
    val message: String,
    val actionLabel: String? = null,
)
```

Add pure helper functions in `MainActivity.kt`.

**Step 4: Verify green**

Run the same test command. Expected: pass.

### Task 2: Compose Wiring

**Files:**
- Modify: `android-app/app/src/main/java/com/reelshort/app/MainActivity.kt`
- Modify: `AGENTS.md`

**Step 1: Update EmptyState UI**

Change `EmptyState(message)` to accept `ContentEmptyState` and render title, message, and optional action label.

**Step 2: Wire pages**

- `HomeScreen`: use `homeEmptyState()`.
- `SearchScreen`: use `searchEmptyState(state.searchQuery, state.searchResults.size)`.
- `DetailScreen`: use `detailEmptyState(book, episodes.size)`.

**Step 3: Update AGENTS**

Add changelog and module description update for Android content states.

### Task 3: Verification and Review

Run:

```powershell
cd android-app
.\gradlew.bat :app-core:test --no-daemon
.\gradlew.bat :app:testDebugUnitTest --no-daemon
.\gradlew.bat :app:assembleDebug --no-daemon
cd ..
git diff --check
```

Install and launch on LDPlayer:

```powershell
C:\leidian\LDPlayer14\adb.exe -s emulator-5554 install -r android-app\app\build\outputs\apk\debug\app-debug.apk
C:\leidian\LDPlayer14\adb.exe -s emulator-5554 shell am start -n com.reelshort.app/.MainActivity
C:\leidian\LDPlayer14\adb.exe -s emulator-5554 logcat -d -t 200 | Select-String -Pattern 'FATAL EXCEPTION|AndroidRuntime'
```

Review for:
- no model/API changes
- no hardcoded business behavior
- no text overflow-prone button labels
- no regressions to content list rendering

Commit:

```powershell
git add .
git commit -m "feat(android): improve content empty states"
```
