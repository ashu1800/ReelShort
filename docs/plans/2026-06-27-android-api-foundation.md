# Android API Foundation Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build the Android API foundation so the app has Spring Boot-facing client and repository boundaries while remaining verifiable without Android SDK.

**Architecture:** Add a pure Kotlin JVM `:app-core` module for API config, DTO/client contracts, fake client, domain models, and repository tests. Keep the Android Compose `:app` module focused on UI and make it depend on `:app-core`, so later work can replace the fake client with a real HTTP implementation without changing page code.

**Tech Stack:** Kotlin 2.0.21, Gradle Kotlin DSL, kotlinx-coroutines, Kotlin test/JUnit on JVM, Android Gradle Plugin for the existing UI module.

---

### Task 1: Add JVM Core Module

**Files:**
- Modify: `android-app/settings.gradle.kts`
- Create: `android-app/app-core/build.gradle.kts`
- Create: `android-app/app-core/src/main/kotlin/com/reelshort/app/config/ApiConfig.kt`
- Test: `android-app/app-core/src/test/kotlin/com/reelshort/app/config/ApiConfigTest.kt`

**Step 1:** Write failing tests for default API base URL and trailing slash normalization.

**Step 2:** Run `android-app/.\gradlew.bat :app-core:test --no-daemon` and verify it fails because `:app-core` does not exist or classes are missing.

**Step 3:** Add `:app-core` to settings, create Kotlin JVM module, and implement `ApiConfig`.

**Step 4:** Run `android-app/.\gradlew.bat :app-core:test --no-daemon` and verify the config tests pass.

### Task 2: Add API Contracts And Repository

**Files:**
- Create: `android-app/app-core/src/main/kotlin/com/reelshort/app/data/AppModels.kt`
- Create: `android-app/app-core/src/main/kotlin/com/reelshort/app/network/ApiResponse.kt`
- Create: `android-app/app-core/src/main/kotlin/com/reelshort/app/network/ReelShortApiClient.kt`
- Create: `android-app/app-core/src/main/kotlin/com/reelshort/app/network/FakeReelShortApiClient.kt`
- Create: `android-app/app-core/src/main/kotlin/com/reelshort/app/data/AppRepository.kt`
- Test: `android-app/app-core/src/test/kotlin/com/reelshort/app/data/AppRepositoryTest.kt`

**Step 1:** Write failing repository tests for login token persistence, home shelf loading, search delegation, watch progress reporting, point balance, and order listing.

**Step 2:** Run `android-app/.\gradlew.bat :app-core:test --no-daemon` and verify tests fail because contracts/repository are missing.

**Step 3:** Implement models, `suspend` client interface, fake client, and repository minimal logic.

**Step 4:** Run `android-app/.\gradlew.bat :app-core:test --no-daemon` and verify all core tests pass.

### Task 3: Wire Android UI To Core Models

**Files:**
- Modify: `android-app/app/build.gradle.kts`
- Modify: `android-app/app/src/main/java/com/reelshort/app/MainActivity.kt`
- Modify: `android-app/README.md`
- Modify: `AGENTS.md`

**Step 1:** Make `:app` depend on `:app-core`.

**Step 2:** Remove duplicated model definitions from `MainActivity.kt` and import models from `com.reelshort.app.data`.

**Step 3:** Update README to describe `app-core`, the fake client boundary, and the Android SDK verification limitation.

**Step 4:** Update `AGENTS.md` module structure and change history.

### Task 4: Review And Verify

**Files:**
- Inspect all files changed in this feature.

**Step 1:** Review for architecture drift: App must only target Spring Boot API, never Flask.

**Step 2:** Run `android-app/.\gradlew.bat :app-core:test --no-daemon`.

**Step 3:** Run repository-level validation: `backend/.\gradlew.bat test --no-daemon`, `admin-web/npm ci`, `admin-web/npm run build`, `content-provider/pytest`, and `git diff --check`.

**Step 4:** Fix any review or verification issues, then repeat review until no obvious issues remain.

**Step 5:** Commit feature branch and merge to `master`; do not push if no remote exists.
