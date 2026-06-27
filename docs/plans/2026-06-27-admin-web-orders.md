# Admin Web Orders Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a read-only order management view to the Vue admin web app.

**Architecture:** Keep the admin web as a thin client over existing Spring Boot Admin APIs. `adminApi.ts` owns typed HTTP calls, views own presentation and loading state, and backend RBAC remains the authority for `ORDER_READ`.

**Tech Stack:** Vue 3, TypeScript, Vite, Vue Router, Pinia, Axios, Element Plus.

---

### Task 1: Extend Admin API Client

**Files:**
- Modify: `admin-web/src/services/adminApi.ts`

**Step 1:** Add `RechargeOrderStatus` and `RechargeOrder` types matching `docs/api/orders.md`.

**Step 2:** Add `fetchOrders()` that calls `GET /api/admin/orders`.

**Step 3:** Run `npm run build`.

### Task 2: Add Orders View

**Files:**
- Create: `admin-web/src/views/OrdersView.vue`
- Modify: `admin-web/src/style.css`

**Step 1:** Build a page header with refresh action.

**Step 2:** Load orders on mount using `fetchOrders()`.

**Step 3:** Show summary metrics for total orders, `CREATED` orders, and total `amountCents`.

**Step 4:** Show the order table with amount formatting, status tags, and empty payment channel fallback.

**Step 5:** Run `npm run build`.

### Task 3: Wire Route, Navigation, and Dashboard

**Files:**
- Modify: `admin-web/src/router/index.ts`
- Modify: `admin-web/src/App.vue`
- Modify: `admin-web/src/views/DashboardView.vue`

**Step 1:** Add `/orders` route.

**Step 2:** Add sidebar navigation item.

**Step 3:** Add order metrics to dashboard by loading `fetchOrders()` with existing dashboard data.

**Step 4:** Run `npm run build`.

### Task 4: Docs, Review, and Merge

**Files:**
- Modify: `AGENTS.md`
- Modify: `admin-web/README.md`

**Step 1:** Update admin-web module description and change history.

**Step 2:** Update README API list with `GET /api/admin/orders`.

**Step 3:** Run `git diff --check`.

**Step 4:** Run `npm run build`.

**Step 5:** Review changed files for API alignment, UI consistency, loading/error behavior, and accidental payment semantics.

**Step 6:** Fix findings and repeat review.

**Step 7:** Commit, merge into `master`, verify build on `master`, then remove worktree and branch.
