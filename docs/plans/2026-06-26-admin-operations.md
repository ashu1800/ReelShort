# Admin Operations Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add backend admin point adjustment and audit log foundations.

**Architecture:** Keep all balance mutation inside `points`, and let `admin` orchestrate authenticated operations and audit logging. Extend point transactions to support non-watch references while preserving existing watch reward behavior.

**Tech Stack:** Java 17, Spring Boot 3.4, Spring Security, Spring Data JPA, MockMvc, H2 tests.

---

### Task 1: Point Adjustment Contract Tests

**Files:**
- Modify: `backend/src/test/java/com/reelshort/backend/admin/AdminUserControllerTests.java`

**Steps:**
1. Write failing MockMvc test for `POST /api/admin/users/{userId}/points/adjust` with positive amount.
2. Assert response detail balance changes, `/api/admin/users/{userId}/point-records` includes `ADMIN_ADJUSTMENT`, and `reason` is returned.
3. Write failing tests for zero amount, blank reason, and negative adjustment below zero.
4. Run `.\gradlew.bat --no-daemon --console=plain test --tests com.reelshort.backend.admin.AdminUserControllerTests`.

### Task 2: Points Model Expansion

**Files:**
- Modify: `backend/src/main/java/com/reelshort/backend/points/PointTransaction.java`
- Modify: `backend/src/main/java/com/reelshort/backend/points/PointTransactionResponse.java`
- Modify: `backend/src/test/java/com/reelshort/backend/points/PointsServiceTests.java`

**Steps:**
1. Add tests for admin adjustment transaction response with nullable watch fields and `reason`.
2. Make `bookId`, `episodeNum`, and `stage` nullable for non-watch sources.
3. Add `reason` column and response field.
4. Keep `watchReward` factory behavior unchanged except reason may be null.

### Task 3: Points Adjustment Service

**Files:**
- Modify: `backend/src/main/java/com/reelshort/backend/points/PointAccount.java`
- Modify: `backend/src/main/java/com/reelshort/backend/points/PointAwardTransaction.java`
- Modify: `backend/src/main/java/com/reelshort/backend/points/PointsService.java`

**Steps:**
1. Add `adjustByAdmin(UUID userId, int amount, String reason)` on `PointsService`.
2. Reuse user-level lock and transaction boundary.
3. Reject resulting negative balances with `AdminException(400, "insufficient point balance")`.
4. Return current account response or balance value for admin response composition.

### Task 4: Audit Log Tests and Implementation

**Files:**
- Create: `backend/src/main/java/com/reelshort/backend/admin/AdminAuditLog.java`
- Create: `backend/src/main/java/com/reelshort/backend/admin/AdminAuditLogRepository.java`
- Create: `backend/src/main/java/com/reelshort/backend/admin/AdminAuditLogResponse.java`
- Create: `backend/src/main/java/com/reelshort/backend/admin/AdminAuditService.java`
- Create: `backend/src/main/java/com/reelshort/backend/admin/CurrentAdmin.java`
- Create: `backend/src/main/java/com/reelshort/backend/admin/CurrentAdminArgumentResolver.java`
- Modify: `backend/src/main/java/com/reelshort/backend/system/web/WebMvcConfig.java`
- Modify: `backend/src/test/java/com/reelshort/backend/admin/AdminUserControllerTests.java`

**Steps:**
1. Write failing tests for `GET /api/admin/audit-logs`, including status change and point adjustment log entries.
2. Add current-admin argument resolver.
3. Implement audit entity/repository/service/response.
4. Wire controller endpoint.

### Task 5: Admin Controller Integration

**Files:**
- Create: `backend/src/main/java/com/reelshort/backend/admin/AdminPointAdjustRequest.java`
- Modify: `backend/src/main/java/com/reelshort/backend/admin/AdminUserController.java`
- Modify: `backend/src/main/java/com/reelshort/backend/admin/AdminUserService.java`

**Steps:**
1. Add adjust endpoint and request validation.
2. Write audit log on point adjustment and user status change.
3. Run admin targeted tests until green.

### Task 6: Docs, Review, and Merge

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/api/admin.md`
- Modify: `docs/api/points.md`

**Steps:**
1. Document point adjustment, audit logs, and `reason` field.
2. Run `.\gradlew.bat --no-daemon --console=plain test --rerun-tasks`.
3. Run `git diff --check`.
4. Review for balance consistency, nullable transaction fields, audit completeness, security boundary leaks, and docs drift.
5. Fix findings with tests first when behavior changes.
6. Commit, merge to `master`, verify again, then remove worktree.
