# Points Foundation Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build App points account, points records, and idempotent watch-stage rewards.

**Architecture:** Keep account and transaction mutation inside `points`. Let `watch` publish saved progress to `PointsService` inside the same transaction and include award results in watch responses.

**Tech Stack:** Java 17, Spring Boot 3.4, Spring Web, Spring Validation, Spring Data JPA, current-user auth context.

---

### Task 1: API Contract Tests

**Files:**
- Create: `backend/src/test/java/com/reelshort/backend/points/PointsControllerTests.java`
- Modify: `backend/src/test/java/com/reelshort/backend/watch/WatchControllerTests.java`

**Steps:**
1. Write failing tests for account query, transaction query, watch reward at 25%, duplicate report no duplicate reward, and jump to 75%.
2. Run targeted tests and verify they fail because points endpoints and rewards do not exist.

### Task 2: Points Persistence Model

**Files:**
- Create: `backend/src/main/java/com/reelshort/backend/points/PointAccount.java`
- Create: `backend/src/main/java/com/reelshort/backend/points/PointTransaction.java`
- Create: `backend/src/main/java/com/reelshort/backend/points/WatchRewardClaim.java`
- Create repositories for each entity.
- Test: `backend/src/test/java/com/reelshort/backend/points/PointsRepositoryTests.java`

**Steps:**
1. Write repository tests for user account uniqueness and watch reward claim uniqueness.
2. Implement entities and repositories.
3. Run repository tests.

### Task 3: Points Service

**Files:**
- Create: `backend/src/main/java/com/reelshort/backend/points/PointsService.java`
- Create response records for account, transactions, and watch rewards.
- Modify: `backend/src/main/java/com/reelshort/backend/watch/WatchService.java`
- Modify: `backend/src/main/java/com/reelshort/backend/watch/WatchRecordResponse.java`
- Test: `backend/src/test/java/com/reelshort/backend/points/PointsServiceTests.java`

**Steps:**
1. Write service tests for balance creation, stage calculation, duplicate prevention, and multi-stage rewards.
2. Implement service and integrate watch response award summary.
3. Run service tests.

### Task 4: Points Controller

**Files:**
- Create: `backend/src/main/java/com/reelshort/backend/points/PointsController.java`
- Modify: `backend/src/test/java/com/reelshort/backend/points/PointsControllerTests.java`

**Steps:**
1. Implement `/api/app/points/account` and `/api/app/points/records`.
2. Run controller tests.
3. Run full backend tests.

### Task 5: Docs, Review, and Merge

**Files:**
- Modify: `AGENTS.md`
- Create: `docs/api/points.md`
- Modify: `docs/api/watch.md`

**Steps:**
1. Document points APIs and watch reward response.
2. Run `.\gradlew.bat --no-daemon --console=plain test --rerun-tasks`.
3. Run `git diff --check master`.
4. Review for duplicate awards, balance/transaction mismatch, user isolation, docs drift, and missing tests.
5. Fix review findings with tests first when behavior changes.
6. Commit, merge to `master`, verify again, then remove worktree.
