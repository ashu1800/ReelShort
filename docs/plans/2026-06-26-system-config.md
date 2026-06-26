# System Config Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add persistent admin-managed system configuration and wire watch reward points to configuration.

**Architecture:** Keep configuration in the `system` module with a whitelist registry of supported keys. Admin endpoints update configurations and write audit logs; business modules read typed values through `SystemConfigService`.

**Tech Stack:** Java 17, Spring Boot 3.4, Spring Data JPA, Spring MVC, MockMvc, H2 tests.

---

### Task 1: Admin Config Contract Tests

**Files:**
- Create: `backend/src/test/java/com/reelshort/backend/system/SystemConfigControllerTests.java`

**Steps:**
1. Write failing test for admin querying `/api/admin/system/configs` and seeing default keys.
2. Write failing test for updating `points.watch.stage-points`.
3. Write failing tests for unknown key and invalid value.
4. Write failing test that App token cannot access config endpoints.
5. Run targeted tests and confirm failure because endpoints do not exist.

### Task 2: Config Persistence and Service

**Files:**
- Create: `backend/src/main/java/com/reelshort/backend/system/config/SystemConfig.java`
- Create: `backend/src/main/java/com/reelshort/backend/system/config/SystemConfigRepository.java`
- Create: `backend/src/main/java/com/reelshort/backend/system/config/SystemConfigDefinition.java`
- Create: `backend/src/main/java/com/reelshort/backend/system/config/SystemConfigRegistry.java`
- Create: `backend/src/main/java/com/reelshort/backend/system/config/SystemConfigResponse.java`
- Create: `backend/src/main/java/com/reelshort/backend/system/config/SystemConfigUpdateRequest.java`
- Create: `backend/src/main/java/com/reelshort/backend/system/config/SystemConfigService.java`

**Steps:**
1. Implement definition whitelist for supported keys.
2. Implement list/update/read typed integer/string methods.
3. Validate values before saving.
4. Use defaults when no row exists.

### Task 3: Admin Config Controller and Audit

**Files:**
- Create: `backend/src/main/java/com/reelshort/backend/system/config/AdminSystemConfigController.java`
- Modify: `backend/src/main/java/com/reelshort/backend/admin/AdminAuditService.java` only if helper extraction is useful.

**Steps:**
1. Implement admin config list endpoint.
2. Implement admin config update endpoint with `CurrentAdmin`.
3. Write `SYSTEM_CONFIG_UPDATED` audit log.
4. Run config controller tests until green.

### Task 4: Points Rule Integration

**Files:**
- Modify: `backend/src/main/java/com/reelshort/backend/points/PointsService.java`
- Modify: `backend/src/main/java/com/reelshort/backend/points/PointAwardTransaction.java`
- Modify: `backend/src/test/java/com/reelshort/backend/points/PointsServiceTests.java`
- Modify: `backend/src/test/java/com/reelshort/backend/admin/AdminUserControllerTests.java` if needed.

**Steps:**
1. Add failing service or controller test proving updated `points.watch.stage-points` changes subsequent watch reward amount.
2. Inject `SystemConfigService` into points service.
3. Use configured stage points instead of constant.
4. If configured value is `0`, claim stage but do not write zero-amount transaction.

### Task 5: Docs, Review, and Merge

**Files:**
- Modify: `AGENTS.md`
- Create: `docs/api/system-config.md`
- Modify: `docs/api/admin.md`
- Modify: `docs/api/points.md`

**Steps:**
1. Document config APIs and supported keys.
2. Run `.\gradlew.bat --no-daemon --console=plain test --rerun-tasks`.
3. Run `git diff --check`.
4. Review for config validation gaps, default persistence behavior, audit completeness, security boundary leaks, and docs drift.
5. Fix findings with tests first when behavior changes.
6. Commit, merge to `master`, verify again, then remove worktree.
