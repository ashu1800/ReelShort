# Android File Session Store Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Persist Android App login sessions across process restarts using a pure Kotlin file-backed `SessionStore`.

**Architecture:** Add `FileSessionStore` in `app-core` and keep Android platform code limited to providing `filesDir/reelshort-session.json`. `AppRepository` continues to depend only on the `SessionStore` interface.

**Tech Stack:** Kotlin JVM, kotlinx serialization JSON, java.io.File, kotlin.test.

---

### Task 1: FileSessionStore Tests

**Files:**
- Create: `android-app/app-core/src/test/kotlin/com/reelshort/app/session/FileSessionStoreTest.kt`

**Step 1: Write tests**

Cover:

- Save session creates JSON file and a new store instance can load it.
- Clear removes the saved session.
- Missing file returns `null`.
- Corrupt JSON returns `null`.

**Step 2: Run focused test**

Run:

```powershell
cd android-app
.\gradlew.bat :app-core:test --tests "com.reelshort.app.session.FileSessionStoreTest" --no-daemon
```

Expected: compilation failure because `FileSessionStore` does not exist.

### Task 2: Implement FileSessionStore

**Files:**
- Create: `android-app/app-core/src/main/kotlin/com/reelshort/app/session/FileSessionStore.kt`

**Step 1: Implement DTO and JSON**

Use an internal `@Serializable` DTO for `username`, `token`, and `tokenType`.

**Step 2: Implement atomic-ish write**

Write JSON to a temp file in the same directory, then replace the target file.

**Step 3: Verify tests**

Run focused tests, then full `:app-core:test`.

### Task 3: Repository And Android Wiring

**Files:**
- Modify: `android-app/app-core/src/test/kotlin/com/reelshort/app/data/AppRepositoryTest.kt`
- Modify: `android-app/app/src/main/java/com/reelshort/app/MainActivity.kt`

**Step 1: Add repository persistence test**

Use `FileSessionStore` with a temp file to verify repository recreation restores token.

**Step 2: Wire Android factory**

Change `AndroidAppFactory.createActions()` to accept `filesDir: File` and construct `FileSessionStore(File(filesDir, "reelshort-session.json"))`.

### Task 4: Docs And Metadata

**Files:**
- Modify: `android-app/README.md`
- Modify: `AGENTS.md`

Document that Android composition root now uses file-backed session persistence, while encrypted platform storage remains a future hardening step.

### Task 5: Verification

Run:

```powershell
cd android-app
.\gradlew.bat :app-core:test --no-daemon
.\gradlew.bat :app:assembleDebug --no-daemon
cd ..\content-provider
pytest
cd ..\backend
.\gradlew.bat test --no-daemon
cd ..
git diff --check
```

Expected:

- `:app-core:test`, `pytest`, backend tests, and diff check pass.
- `:app:assembleDebug` fails only because Android SDK is not configured.
