# Android Cover Images Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Display real ReelShort content cover images in the Android App while preserving the existing cinematic fallback poster.

**Architecture:** Keep `app-core` models and API mapping unchanged. Add image loading only in the Android app module through Coil Compose, and centralize blank URL handling in a small testable helper.

**Tech Stack:** Kotlin, Jetpack Compose, Coil 3, Android Gradle.

---

### Task 1: Cover URL Contract Test

**Files:**
- Create: `android-app/app/src/test/kotlin/com/reelshort/app/CoverImageContractTest.kt`
- Modify: `android-app/app/src/main/java/com/reelshort/app/MainActivity.kt`

**Step 1: Write failing helper test**

Create tests that assert blank cover URLs become `null` and real URLs are preserved:

```kotlin
assertEquals(null, " ".coverUrlOrNull())
assertEquals("https://example.com/a.jpg", " https://example.com/a.jpg ".coverUrlOrNull())
```

**Step 2: Run focused app tests**

Run:

```powershell
cd android-app
.\gradlew.bat :app:testDebugUnitTest --no-daemon
```

Expected: fail until `coverUrlOrNull` exists.

### Task 2: Add Coil And Network Permission

**Files:**
- Modify: `android-app/app/build.gradle.kts`
- Modify: `android-app/app/src/main/AndroidManifest.xml`
- Modify: `AGENTS.md`

**Step 1: Add Coil dependencies**

Add:

```kotlin
implementation("io.coil-kt.coil3:coil-compose:3.5.0")
implementation("io.coil-kt.coil3:coil-network-okhttp:3.5.0")
```

**Step 2: Add Android network permission**

Add:

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

**Step 3: Update AGENTS**

Record the dependency and module behavior change.

### Task 3: Implement Poster Image Loading

**Files:**
- Modify: `android-app/app/src/main/java/com/reelshort/app/MainActivity.kt`

**Step 1: Implement `coverUrlOrNull`**

Add an internal extension that trims strings and returns null for blanks.

**Step 2: Update `PosterBlock`**

Add `coverUrl: String?` parameter. Use `AsyncImage` when non-null. Preserve the current gradient fallback for null/error/loading states.

**Step 3: Wire book cover URLs**

Pass `book.coverUrl` from `BookRow` and `BookHero`.

### Task 4: Verification And Review

**Files:**
- No new files expected.

**Step 1: Run Android verification**

```powershell
cd android-app
.\gradlew.bat :app-core:test --no-daemon
.\gradlew.bat :app:testDebugUnitTest --no-daemon
.\gradlew.bat :app:assembleDebug --no-daemon
cd ..
git diff --check
```

**Step 2: Run emulator verification**

Install and start APK in LDPlayer. Check process and logcat for no `FATAL EXCEPTION`.

**Step 3: Review**

Check for missing permission, blank image URLs, fallback rendering, unnecessary model changes and visual regressions.
