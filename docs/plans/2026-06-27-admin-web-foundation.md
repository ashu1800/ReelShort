# Admin Web Foundation Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build a usable Vue admin foundation with login, session, protected layout, and basic backend data views.

**Architecture:** Keep the admin website as a Vue + Element Plus SPA. A Pinia session store owns token persistence. A shared Axios client injects the admin token and normalizes API responses. Routes are protected by a router guard and views fetch data directly through small service functions.

**Tech Stack:** Vue 3, TypeScript, Vite, Vue Router, Pinia, Axios, Element Plus, lucide-equivalent Element Plus icons.

---

### Task 1: API Types and Session

**Files:**
- Modify: `admin-web/src/stores/session.ts`
- Modify: `admin-web/src/services/http.ts`
- Create: `admin-web/src/services/adminApi.ts`

**Step 1:** Define `ApiResponse<T>`, admin auth/user/cache/audit types, and API service functions.

**Step 2:** Extend session store with token persistence, login state, `setSession`, and `clearSession`.

**Step 3:** Add Axios request interceptor for Bearer token and response interceptor for 401 cleanup.

**Step 4:** Run `npm run build`.

### Task 2: Routing and Layout

**Files:**
- Modify: `admin-web/src/router/index.ts`
- Modify: `admin-web/src/App.vue`
- Modify: `admin-web/src/style.css`
- Create: `admin-web/src/views/LoginView.vue`

**Step 1:** Add `/login`, `/`, `/users`, `/content-cache`, `/audit-logs` routes with auth metadata.

**Step 2:** Add route guard redirecting anonymous users to `/login`.

**Step 3:** Build login page using Element Plus form and session store.

**Step 4:** Update shell layout with navigation, active route, admin name, and logout.

**Step 5:** Run `npm run build`.

### Task 3: Data Views

**Files:**
- Modify: `admin-web/src/views/DashboardView.vue`
- Create: `admin-web/src/views/UsersView.vue`
- Create: `admin-web/src/views/ContentCacheView.vue`
- Create: `admin-web/src/views/AuditLogsView.vue`

**Step 1:** Dashboard fetches user/cache/audit summaries.

**Step 2:** Users view displays user table with status and point balance.

**Step 3:** Content cache view displays cache counters and shelf status table.

**Step 4:** Audit logs view displays latest audit entries.

**Step 5:** Each view handles loading, error, and empty states.

**Step 6:** Run `npm run build`.

### Task 4: Docs and Review

**Files:**
- Modify: `AGENTS.md`
- Possibly modify: `admin-web/README.md`

**Step 1:** Update project module description/change history for Admin Web foundation.

**Step 2:** Run `git diff --check`.

**Step 3:** Run `npm run build`.

**Step 4:** Review diff for scope and UX consistency.

**Step 5:** Commit, merge into `master`, rerun `npm run build` on `master`, then clean worktree and branch.
