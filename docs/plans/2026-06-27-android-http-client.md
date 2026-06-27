# Android HTTP Client Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a real Spring Boot HTTP implementation of the Android `ReelShortApiClient` contract.

**Architecture:** Keep `app-core` as the JVM-verifiable API layer. Add an OkHttp-based adapter beside the existing fake client, keep network DTOs separate from domain models, and use MockWebServer tests to prove paths, headers, JSON bodies, response mapping, and error handling.

**Tech Stack:** Kotlin 2.0.21, Gradle Kotlin DSL, kotlinx-coroutines, kotlinx-serialization-json 1.7.x, OkHttp 4.12.x, MockWebServer, Kotlin test/JUnit on JVM.

---

### Task 1: Dependencies And Error Type

**Files:**
- Modify: `android-app/build.gradle.kts`
- Modify: `android-app/app-core/build.gradle.kts`
- Create: `android-app/app-core/src/main/kotlin/com/reelshort/app/network/ApiClientException.kt`
- Test: `android-app/app-core/src/test/kotlin/com/reelshort/app/network/OkHttpReelShortApiClientTest.kt`

**Step 1:** Write failing test expecting `OkHttpReelShortApiClient` to throw `ApiClientException` for backend `code != 0`.

**Step 2:** Run `.\gradlew.bat :app-core:test --no-daemon`; expected failure because dependencies/classes are missing.

**Step 3:** Add serialization plugin/dependencies and implement `ApiClientException`.

### Task 2: Auth And Content Requests

**Files:**
- Create: `android-app/app-core/src/main/kotlin/com/reelshort/app/network/dto/AppApiDtos.kt`
- Create: `android-app/app-core/src/main/kotlin/com/reelshort/app/network/OkHttpReelShortApiClient.kt`
- Test: `android-app/app-core/src/test/kotlin/com/reelshort/app/network/OkHttpReelShortApiClientTest.kt`

**Step 1:** Add failing tests for login request/response, home recommend mapping, and search query encoding.

**Step 2:** Implement JSON request helpers, GET/POST helpers, auth DTOs, book DTO mapping, login/register, home shelf, and search.

**Step 3:** Run `.\gradlew.bat :app-core:test --no-daemon`; expected pass for auth/content tests.

### Task 3: Protected Watch Points Orders

**Files:**
- Modify: `android-app/app-core/src/main/kotlin/com/reelshort/app/network/dto/AppApiDtos.kt`
- Modify: `android-app/app-core/src/main/kotlin/com/reelshort/app/network/OkHttpReelShortApiClient.kt`
- Test: `android-app/app-core/src/test/kotlin/com/reelshort/app/network/OkHttpReelShortApiClientTest.kt`

**Step 1:** Add failing tests for Authorization header on App business APIs, episode/video URL mapping, watch progress body, point account/records, and order list.

**Step 2:** Implement protected request helper and remaining `ReelShortApiClient` methods.

**Step 3:** Run `.\gradlew.bat :app-core:test --no-daemon`; expected pass.

### Task 4: Docs Review Verification Merge

**Files:**
- Modify: `android-app/README.md`
- Modify: `AGENTS.md`

**Step 1:** Document `OkHttpReelShortApiClient`, Spring Boot-only boundary, and JVM verification command.

**Step 2:** Update `AGENTS.md` module description/change history for dependency/API implementation changes.

**Step 3:** Review changed files for architecture drift and error handling gaps.

**Step 4:** Run full verification: `android-app/.\\gradlew.bat :app-core:test --no-daemon`, `backend/.\\gradlew.bat test --no-daemon`, `admin-web/npm ci`, `admin-web/npm run build`, `content-provider/pytest`, and `git diff --check`.

**Step 5:** Commit feature branch, merge to `master`, clean worktree/branch; do not push if no remote exists.
