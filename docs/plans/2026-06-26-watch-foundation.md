# Watch Foundation Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build authenticated watch progress reporting and watch history for App users.

**Architecture:** Keep watch persistence and business rules in the `watch` module. Use `CurrentUser` from auth/security to scope records, JPA unique constraints for idempotent upsert, and unified API envelopes for responses.

**Tech Stack:** Java 17, Spring Boot 3.4, Spring Web, Validation, Spring Data JPA, Spring Security-backed current user.

---

### Task 1: API Contract Tests

**Files:**
- Create: `backend/src/test/java/com/reelshort/backend/watch/WatchControllerTests.java`

**Steps:**
1. Write failing tests for unauthenticated progress report `401`, authenticated progress report success, duplicate upsert, history ordering, and validation errors.
2. Run targeted tests and verify they fail because watch endpoints do not exist.

### Task 2: Persistence Model

**Files:**
- Create: `backend/src/main/java/com/reelshort/backend/watch/WatchRecord.java`
- Create: `backend/src/main/java/com/reelshort/backend/watch/WatchRecordRepository.java`
- Test: `backend/src/test/java/com/reelshort/backend/watch/WatchRecordRepositoryTests.java`

**Steps:**
1. Write repository tests for unique `(userId, bookId, episodeNum)` and newest-first query.
2. Implement entity and repository.
3. Run repository tests.

### Task 3: Watch Service

**Files:**
- Create: `backend/src/main/java/com/reelshort/backend/watch/WatchService.java`
- Create: `backend/src/main/java/com/reelshort/backend/watch/WatchProgressRequest.java`
- Create: `backend/src/main/java/com/reelshort/backend/watch/WatchRecordResponse.java`
- Test: `backend/src/test/java/com/reelshort/backend/watch/WatchServiceTests.java`

**Steps:**
1. Write service tests for percent calculation, clamping, duplicate update, and user isolation.
2. Implement service and response mapping.
3. Run service tests.

### Task 4: Watch Controller

**Files:**
- Create: `backend/src/main/java/com/reelshort/backend/watch/WatchController.java`
- Modify: `backend/src/test/java/com/reelshort/backend/watch/WatchControllerTests.java`

**Steps:**
1. Implement `/api/app/watch/progress` and `/api/app/watch/history`.
2. Run controller tests.
3. Run full backend tests.

### Task 5: Docs, Review, and Merge

**Files:**
- Modify: `AGENTS.md`
- Create: `docs/api/watch.md`

**Steps:**
1. Document watch APIs and current constraints.
2. Run `.\gradlew.bat --no-daemon --console=plain test --rerun-tasks`.
3. Run `git diff --check master`.
4. Review for auth bypass, user isolation bugs, progress calculation bugs, docs drift, and missing tests.
5. Fix findings with tests first when behavior changes.
6. Commit, merge to `master`, verify again, then remove worktree.
