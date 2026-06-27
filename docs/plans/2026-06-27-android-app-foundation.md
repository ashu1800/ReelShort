# Android App Foundation Implementation Plan

**Goal:** Build a Compose UI/state skeleton for the Android App core viewing loop.

**Architecture:** Keep the first Android slice in `MainActivity.kt` with clear state and screen functions. Use local sample data and explicit navigation events now, so later Repository/ViewModel/API integration can replace sample data without changing screen boundaries.

**Tech Stack:** Kotlin, Android Gradle Plugin, Jetpack Compose, Material3.

---

### Task 1: Compose App Shell and State

**Files:**
- Modify: `android-app/app/src/main/java/com/reelshort/app/MainActivity.kt`

**Step 1:** Replace placeholder text with `ReelShortApp` state root.

**Step 2:** Add `AppScreen`, `AppState`, and sample models for content, episodes, watch records, point records and orders.

**Step 3:** Add navigation functions for login, logout, tab selection, search, opening detail and opening player.

### Task 2: Core Screens

**Files:**
- Modify: `android-app/app/src/main/java/com/reelshort/app/MainActivity.kt`

**Step 1:** Add Login screen.

**Step 2:** Add Home and Search screens.

**Step 3:** Add Detail and Player screens.

**Step 4:** Add History, Points and Orders screens.

**Step 5:** Add shared shell, top bar, bottom navigation and compact list components.

### Task 3: Docs, Validation, Review, and Merge

**Files:**
- Modify: `android-app/README.md`
- Modify: `AGENTS.md`

**Step 1:** Document current Android App foundation and Android SDK limitation.

**Step 2:** Update AGENTS module description/change history.

**Step 3:** Run file-level validation for expected Kotlin declarations.

**Step 4:** Run `git diff --check`.

**Step 5:** Review for direct Flask references, missing App API boundary notes, text overflow risk, and unnecessary dependencies.

**Step 6:** Fix findings, repeat validation, commit, merge to `master`, verify on `master`, then clean up worktree and branch.
