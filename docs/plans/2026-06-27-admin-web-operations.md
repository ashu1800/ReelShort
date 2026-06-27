# Admin Web Operations Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build the admin operations UI on top of existing Spring Boot Admin APIs.

**Architecture:** Keep the Vue SPA as a thin operational client. `adminApi.ts` owns typed HTTP calls, views own presentation and input validation, and Spring Boot remains the only authority for permissions and business rules.

**Tech Stack:** Vue 3, TypeScript, Vite, Vue Router, Pinia, Axios, Element Plus.

---

### Task 1: Extend Admin API Client

**Files:**
- Modify: `admin-web/src/services/adminApi.ts`

**Step 1:** Add response types for user detail, watch records, point records, and system configs.

**Step 2:** Add API functions for user detail, status update, points adjustment, user records, system config list/update, and content shelf refresh.

**Step 3:** Run `npm run build`.

### Task 2: User Detail and Operations

**Files:**
- Modify: `admin-web/src/views/UsersView.vue`
- Modify: `admin-web/src/style.css`

**Step 1:** Add row click or detail button to open a user detail drawer.

**Step 2:** Load user detail, watch records, and point records when drawer opens.

**Step 3:** Add user status action with confirmation.

**Step 4:** Add points adjustment form with local validation.

**Step 5:** Refresh detail and list after successful operations.

**Step 6:** Run `npm run build`.

### Task 3: System Config Operations

**Files:**
- Create: `admin-web/src/views/SystemConfigsView.vue`
- Modify: `admin-web/src/router/index.ts`
- Modify: `admin-web/src/App.vue`
- Modify: `admin-web/src/style.css`

**Step 1:** Add `/system-configs` route.

**Step 2:** Add sidebar navigation entry.

**Step 3:** Build table with config key, value, description, updatedAt, and save action.

**Step 4:** Run `npm run build`.

### Task 4: Content Cache Refresh

**Files:**
- Modify: `admin-web/src/views/ContentCacheView.vue`
- Modify: `admin-web/src/style.css`

**Step 1:** Add shelf selector with `recommend`, `new-release`, and `drama-dub`.

**Step 2:** Add refresh action calling `POST /api/admin/content/cache/shelves/{shelfType}/refresh`.

**Step 3:** Reload cache status after refresh.

**Step 4:** Run `npm run build`.

### Task 5: Docs, Review, and Merge

**Files:**
- Modify: `AGENTS.md`
- Modify: `admin-web/README.md`

**Step 1:** Update module description and change history.

**Step 2:** Run `git diff --check`.

**Step 3:** Run `npm run build`.

**Step 4:** Review changed files for API alignment, UX consistency, and state refresh bugs.

**Step 5:** Fix review findings and repeat review until no obvious issues remain.

**Step 6:** Commit, merge into `master`, verify build on `master`, then remove worktree and branch.
