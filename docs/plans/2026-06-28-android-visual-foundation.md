# Android Visual Foundation Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Upgrade the Android App from default Material3 scaffolding to a simple, cinematic, content-first visual foundation without changing business behavior.

**Architecture:** Keep the existing single-activity Compose structure and app-core state model. Add small UI helpers inside the Android app module, refactor existing composables in `MainActivity.kt`, and keep API/state wiring unchanged.

**Tech Stack:** Kotlin, Jetpack Compose, Material3, Android Gradle.

---

### Task 1: Visual Contract Test

**Files:**
- Create: `android-app/app/src/test/kotlin/com/reelshort/app/VisualTextContractTest.kt`
- Modify: `android-app/app/build.gradle.kts`

**Step 1: Add app unit test dependency**

Add Kotlin test dependency for the `app` module:

```kotlin
testImplementation(kotlin("test"))
```

**Step 2: Write visual text contract test**

Create tests for navigation labels and default API URL availability. The test should verify that bottom navigation uses full labels instead of single-character icon labels.

**Step 3: Run focused app tests**

Run:

```powershell
cd android-app
.\gradlew.bat :app:testDebugUnitTest --no-daemon
```

Expected: fail until visual label constants exist.

### Task 2: Theme And Visual Primitives

**Files:**
- Modify: `android-app/app/src/main/java/com/reelshort/app/MainActivity.kt`

**Step 1: Add theme primitives**

Add color constants and `ReelShortTheme` using Material3 dark color scheme.

**Step 2: Add reusable UI helpers**

Add helpers for background, action button, section heading, metadata text and status surfaces.

**Step 3: Run focused app tests**

Run:

```powershell
cd android-app
.\gradlew.bat :app:testDebugUnitTest --no-daemon
```

Expected: pass.

### Task 3: Login And Shell Redesign

**Files:**
- Modify: `android-app/app/src/main/java/com/reelshort/app/MainActivity.kt`

**Step 1: Refactor login page**

Apply the dark cinematic background, concise brand hierarchy, compact input group and strong primary action.

**Step 2: Refactor app shell**

Update `Scaffold`, top bar, loading strip, error banner and bottom navigation to use the visual foundation.

**Step 3: Build Android app**

Run:

```powershell
cd android-app
.\gradlew.bat :app:assembleDebug --no-daemon
```

Expected: build succeeds.

### Task 4: Content Screens Redesign

**Files:**
- Modify: `android-app/app/src/main/java/com/reelshort/app/MainActivity.kt`

**Step 1: Refactor home and search screens**

Use content-first spacing, clear search surface and upgraded book rows.

**Step 2: Refactor detail, player and account screens**

Keep functionality unchanged while applying consistent dark surfaces, readable lists and focused actions.

**Step 3: Build Android app**

Run:

```powershell
cd android-app
.\gradlew.bat :app:assembleDebug --no-daemon
```

Expected: build succeeds.

### Task 5: Verification And Review

**Files:**
- Modify: `AGENTS.md`

**Step 1: Update AGENTS**

Add a changelog entry for Android visual foundation.

**Step 2: Run verification**

Run:

```powershell
cd android-app
.\gradlew.bat :app-core:test --no-daemon
.\gradlew.bat :app:testDebugUnitTest --no-daemon
.\gradlew.bat :app:assembleDebug --no-daemon
cd ..
git diff --check
```

**Step 3: Emulator verification**

Install and start the debug APK in LDPlayer. Check `pidof com.reelshort.app` and recent logcat for no `FATAL EXCEPTION`.

**Step 4: Review and fix**

Review visual code for layout risks, hardcoded text overflow, touch targets, contrast and business behavior changes. Fix findings and repeat verification before commit.
