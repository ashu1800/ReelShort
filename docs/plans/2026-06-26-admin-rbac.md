# Admin RBAC Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add persistent admin account, role, permission, and API permission enforcement foundations while preserving the default local admin login.

**Architecture:** The backend keeps a modular monolith shape. Admin auth moves from config-only credentials to JPA entities and repositories, with a bootstrap service seeding the default admin and `SUPER_ADMIN` role from `AdminProperties`. API authorization is enforced by a lightweight annotation and Spring MVC interceptor so controller paths and response contracts stay stable.

**Tech Stack:** Java 17, Spring Boot 3.4, Spring Security, Spring MVC, Spring Data JPA, H2 tests, PostgreSQL-compatible schema naming.

---

### Task 1: Admin RBAC Persistence Model

**Files:**
- Create: `backend/src/main/java/com/reelshort/backend/admin/AdminUser.java`
- Create: `backend/src/main/java/com/reelshort/backend/admin/AdminUserStatus.java`
- Create: `backend/src/main/java/com/reelshort/backend/admin/AdminRole.java`
- Create: `backend/src/main/java/com/reelshort/backend/admin/AdminPermission.java`
- Create: `backend/src/main/java/com/reelshort/backend/admin/AdminUserRepository.java`
- Create: `backend/src/main/java/com/reelshort/backend/admin/AdminRoleRepository.java`
- Create: `backend/src/main/java/com/reelshort/backend/admin/AdminPermissionRepository.java`
- Test: `backend/src/test/java/com/reelshort/backend/admin/AdminRbacBootstrapServiceTests.java`

**Step 1:** Write failing tests for default admin bootstrap creating admin user, `SUPER_ADMIN` role, and required permissions.

**Step 2:** Run `.\gradlew.bat --no-daemon --console=plain test --tests com.reelshort.backend.admin.AdminRbacBootstrapServiceTests` and confirm failure because the bootstrap service/entities do not exist.

**Step 3:** Add JPA entities and repositories with explicit table names: `admin_users`, `roles`, `permissions`, `admin_user_roles`, `role_permissions`.

**Step 4:** Add `AdminRbacBootstrapService` to seed default records on startup in a transaction.

**Step 5:** Run the focused test and confirm pass.

### Task 2: Persistent Admin Login and Token Principal

**Files:**
- Modify: `backend/src/main/java/com/reelshort/backend/admin/AdminAuthService.java`
- Modify: `backend/src/main/java/com/reelshort/backend/admin/AdminToken.java`
- Modify: `backend/src/main/java/com/reelshort/backend/admin/AdminBearerTokenAuthenticationFilter.java`
- Modify: `backend/src/main/java/com/reelshort/backend/admin/AdminPrincipal.java`
- Modify: `backend/src/main/java/com/reelshort/backend/admin/CurrentAdmin.java`
- Modify: `backend/src/main/java/com/reelshort/backend/admin/CurrentAdminArgumentResolver.java`
- Test: `backend/src/test/java/com/reelshort/backend/admin/AdminAuthServiceTests.java`
- Test: `backend/src/test/java/com/reelshort/backend/admin/AdminAuthControllerTests.java`

**Step 1:** Write failing tests proving admin login reads the persisted admin account and authenticated principal contains admin user ID and permissions.

**Step 2:** Run focused admin auth tests and confirm failure.

**Step 3:** Update login to load `AdminUser` from `AdminUserRepository`, reject missing/disabled users, and issue tokens with `adminUserId`.

**Step 4:** Update the admin token filter to reload the admin user and permission set before creating `AdminPrincipal`.

**Step 5:** Run focused tests and confirm pass.

### Task 3: Admin Permission Enforcement

**Files:**
- Create: `backend/src/main/java/com/reelshort/backend/admin/RequireAdminPermission.java`
- Create: `backend/src/main/java/com/reelshort/backend/admin/AdminPermissionInterceptor.java`
- Modify: `backend/src/main/java/com/reelshort/backend/system/web/WebMvcConfig.java`
- Modify: `backend/src/main/java/com/reelshort/backend/admin/AdminUserController.java`
- Modify: `backend/src/main/java/com/reelshort/backend/admin/AdminAuditController.java`
- Modify: `backend/src/main/java/com/reelshort/backend/content/AdminContentCacheController.java`
- Modify: `backend/src/main/java/com/reelshort/backend/system/config/AdminSystemConfigController.java`
- Test: `backend/src/test/java/com/reelshort/backend/admin/AdminPermissionControllerTests.java`

**Step 1:** Write failing MVC test creating a limited admin with only `USER_READ`, logging in, and verifying user list succeeds but points adjustment returns `403`.

**Step 2:** Run focused test and confirm failure because permission enforcement is absent.

**Step 3:** Add `@RequireAdminPermission` and interceptor that checks `AdminPrincipal.permissions()`.

**Step 4:** Annotate existing Admin API methods with the required permission code.

**Step 5:** Run focused test and existing admin controller tests.

### Task 4: Documentation and Project Metadata

**Files:**
- Modify: `docs/api/admin.md`
- Modify: `AGENTS.md`

**Step 1:** Document persistent admin bootstrap, permissions, and `403` semantics.

**Step 2:** Update `AGENTS.md` module description/change history for `backend/admin`.

**Step 3:** Run `git diff --check`.

### Task 5: Final Verification and Merge

**Files:**
- All touched files.

**Step 1:** Run `.\gradlew.bat --no-daemon --console=plain test` from `backend`.

**Step 2:** Review `git diff --stat` and full diff for scope.

**Step 3:** Commit on `feature/admin-rbac` with `feat(admin): add rbac foundation`.

**Step 4:** Merge into `master` with a merge commit or fast-forward according to current branch state.

**Step 5:** Run full backend tests again on `master`, confirm clean status, then remove the worktree and delete the feature branch if merged.
