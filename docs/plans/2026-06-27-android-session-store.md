# Android Session Store Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a pure Kotlin session storage boundary so Android login state and bearer token can survive app recreation and be restored into repository/state controller flows.

**Architecture:** Introduce `SessionStore` and `InMemorySessionStore` in `app-core`; make `AppRepository` persist/restore/clear sessions; expose those operations through `AppDataSource`; extend `AppStateController` with restore and logout flows.

**Tech Stack:** Kotlin JVM, kotlinx.coroutines, kotlinx.coroutines test, kotlin.test.

---

### Task 1: Repository Session Store Tests

**Files:**
- Modify: `android-app/app-core/src/test/kotlin/com/reelshort/app/data/AppRepositoryTest.kt`

**Step 1: Write failing tests**

Add tests proving:

- `login` saves session into a shared store.
- a new repository can `restoreSession()` from that store and set `currentToken`.
- `register` saves session.
- `clearSession()` removes stored session and clears `currentToken`.

**Step 2: Run test to verify it fails**

Run:

```powershell
cd android-app
.\gradlew.bat :app-core:test --no-daemon
```

Expected: compilation fails because `SessionStore`, `InMemorySessionStore`, `restoreSession`, and `clearSession` do not exist.

### Task 2: State Controller Session Tests

**Files:**
- Modify: `android-app/app-core/src/test/kotlin/com/reelshort/app/state/AppStateControllerTest.kt`

**Step 1: Write failing tests**

Add tests proving:

- `restoreSession()` with no session keeps login screen and no error.
- `restoreSession()` with session loads home and enters home screen.
- `restoreSession()` failure clears the stored session and records error.
- `logout()` clears session and resets state to login.

### Task 3: Implement Session Store

**Files:**
- Create: `android-app/app-core/src/main/kotlin/com/reelshort/app/session/SessionStore.kt`
- Create: `android-app/app-core/src/main/kotlin/com/reelshort/app/session/InMemorySessionStore.kt`

**Step 1: Add interface and memory implementation**

Define suspend functions `loadSession`, `saveSession`, and `clearSession`.

### Task 4: Wire Repository

**Files:**
- Modify: `android-app/app-core/src/main/kotlin/com/reelshort/app/data/AppDataSource.kt`
- Modify: `android-app/app-core/src/main/kotlin/com/reelshort/app/data/AppRepository.kt`

**Step 1: Add data source methods**

Add `restoreSession()` and `clearSession()`.

**Step 2: Persist and restore sessions**

Inject `SessionStore` into `AppRepository`, save session after login/register, restore `currentToken` from store, and clear both store and memory token.

### Task 5: Wire State Controller

**Files:**
- Modify: `android-app/app-core/src/main/kotlin/com/reelshort/app/state/AppStateController.kt`

**Step 1: Add restore and logout flows**

Implement `restoreSession()` and `logout()` while preserving cancellation behavior.

### Task 6: Update Documentation

**Files:**
- Modify: `android-app/README.md`
- Modify: `AGENTS.md`

**Step 1: Document session boundary**

Add:

```text
[2026-06-27] android-app/session - 增加纯 Kotlin 会话存储边界、Repository Token 恢复和状态控制器启动恢复/登出流程。
```

### Task 7: Review, Verify, Commit, Merge

**Step 1: Review**

Run:

```powershell
git diff --check
git diff --stat
```

Inspect changed files for cancellation handling, session clearing, and token provider compatibility.

**Step 2: Verify**

Run:

```powershell
cd android-app
.\gradlew.bat :app-core:test --no-daemon
```

Then run broader regression checks before merge.

**Step 3: Commit and merge**

Commit on `feature/android-session-store`, merge to `master`, and clean the worktree.
