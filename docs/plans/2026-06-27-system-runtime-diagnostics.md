# System Runtime Diagnostics Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a read-only admin runtime diagnostics view for backend, JVM, database, Redis, and content-provider health.

**Architecture:** Implement a Spring Boot `backend/system` runtime diagnostics service behind the existing admin RBAC boundary, then expose it in admin-web as a read-only operations page. Keep diagnostics sanitized and return HTTP 200 with dependency-level statuses so partial failures remain visible.

**Tech Stack:** Spring Boot, JDBC `DataSource`, Spring Data Redis `StringRedisTemplate`, `RestClient`, Vue 3, Element Plus, TypeScript.

---

### Task 1: Add Runtime Service Tests

**Files:**
- Create: `backend/src/test/java/com/reelshort/backend/system/SystemRuntimeServiceTests.java`

**Step 1: Write failing tests**

Add tests proving:

- all dependencies `UP` produces overall `UP`
- a failing dependency produces overall `DEGRADED`
- dependency failure detail is sanitized and does not expose exception stack traces

Use small fake checker collaborators if production service introduces a `RuntimeDependencyChecker` boundary.

**Step 2: Run failing test**

Run:

```powershell
cd backend
.\gradlew.bat test --tests "*SystemRuntimeServiceTests" --no-daemon
```

Expected: fail because runtime service classes do not exist.

### Task 2: Implement Runtime Service

**Files:**
- Create: `backend/src/main/java/com/reelshort/backend/system/runtime/SystemRuntimeService.java`
- Create: `backend/src/main/java/com/reelshort/backend/system/runtime/SystemRuntimeResponse.java`
- Create: `backend/src/main/java/com/reelshort/backend/system/runtime/RuntimeDependencyStatus.java`
- Create: `backend/src/main/java/com/reelshort/backend/system/runtime/RuntimeDependencyChecker.java`

**Step 1: Implement minimal service**

Create a service that accepts a list of dependency checkers and returns:

- `status`: `UP` or `DEGRADED`
- `checkedAt`
- application info
- memory info
- dependency statuses

**Step 2: Add production checkers**

Implement database, Redis, and content-provider checkers. Keep error details short and sanitized.

**Step 3: Run service tests**

Run:

```powershell
cd backend
.\gradlew.bat test --tests "*SystemRuntimeServiceTests" --no-daemon
```

Expected: pass.

### Task 3: Add Admin API, Permission, and Docs

**Files:**
- Create: `backend/src/main/java/com/reelshort/backend/system/runtime/SystemRuntimeController.java`
- Modify: `backend/src/main/java/com/reelshort/backend/admin/AdminPermissions.java`
- Modify: `docs/api/admin.md`

**Step 1: Write failing controller test**

Create/extend backend tests to prove:

- `GET /api/admin/system/runtime` requires admin auth
- an admin with `SYSTEM_RUNTIME_READ` receives unified success response
- response includes dependency array

**Step 2: Implement controller and permission**

Add `SYSTEM_RUNTIME_READ` to `AdminPermissions.ALL`, annotate controller with `@RequireAdminPermission`, and return `ApiResponse<SystemRuntimeResponse>`.

**Step 3: Document endpoint**

Document request, permission, response fields, and sanitized diagnostics behavior in `docs/api/admin.md`.

**Step 4: Run targeted backend tests**

Run:

```powershell
cd backend
.\gradlew.bat test --tests "*SystemRuntime*" --tests "*AdminRbacBootstrapServiceTests" --no-daemon
```

Expected: pass.

### Task 4: Add Admin-Web Runtime Diagnostics Page

**Files:**
- Modify: `admin-web/src/services/adminApi.ts`
- Modify: `admin-web/src/router/index.ts`
- Modify: `admin-web/src/App.vue`
- Create: `admin-web/src/views/SystemRuntimeView.vue`
- Modify: `admin-web/README.md`

**Step 1: Add API type and client**

Add `SystemRuntimeStatus`, `RuntimeDependencyStatus`, `SystemRuntimeResponse`, and `fetchSystemRuntime()`.

**Step 2: Add route and menu**

Add `/system-runtime` route and a sidebar menu item named `运行诊断`.

**Step 3: Build page**

Render overall status, service/version/uptime, memory usage, and dependency table. Use existing Element Plus styling patterns.

**Step 4: Run frontend build**

Run:

```powershell
cd admin-web
npm ci
npm run build
```

Expected: build exits 0.

### Task 5: Sync Project Docs and AGENTS

**Files:**
- Modify: `AGENTS.md`
- Modify: `README.md` if current stage text is stale

**Step 1: Update module description**

Mention `backend/system` runtime diagnostics and `admin-web` runtime diagnostics view.

**Step 2: Add change history**

Add at top:

```text
[2026-06-27] system/runtime - 增加后台运行诊断 API、依赖状态检查和后台 Web 诊断页。
```

### Task 6: Review, Verify, Commit, Merge

**Step 1: Review**

Run:

```powershell
git diff --check
git diff --stat
```

Inspect for:

- no sensitive config values exposed
- permission enforced
- dependency failures do not break whole diagnostics response
- frontend route/menu consistency

**Step 2: Full verification**

Run:

```powershell
cd backend
.\gradlew.bat test --no-daemon
cd ..\android-app
.\gradlew.bat :app-core:test --no-daemon
cd ..\content-provider
pytest
cd ..\admin-web
npm ci
npm run build
```

**Step 3: Commit and merge**

Commit on `feature/system-runtime-diagnostics`, merge into `master`, then clean worktree and branch.
