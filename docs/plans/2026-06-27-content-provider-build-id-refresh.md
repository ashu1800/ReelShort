# Content Provider Build ID Refresh Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Make the Flask ReelShort content provider recover automatically when an auto-discovered Next.js build id becomes stale.

**Architecture:** Keep all behavior inside `ReelShortClient`. Track whether build id refresh is allowed, clear stale auto-discovered ids on `_next/data` 404, rediscover from the site page, and retry the same data request once.

**Tech Stack:** Python, Flask, requests, pytest.

---

### Task 1: Failing Tests

**Files:**
- Modify: `content-provider/tests/test_app.py`

**Step 1: Add stale build id recovery test**

Add a test where `ReelShortClient` starts with an auto-discovered stale build id, the first `_next/data` request returns 404, `_discover_build_id` fetches a new build id, and the original search request succeeds on retry.

**Step 2: Add explicit build id no-refresh test**

Add a test where a client constructed with an explicit build id receives a 404 and raises `UpstreamError(404, ...)` without fetching the site page.

**Step 3: Verify failure**

Run:

```powershell
cd content-provider
pytest tests/test_app.py -q
```

Expected: the stale build id recovery test fails because `_get_data()` does not retry after 404.

### Task 2: Minimal Implementation

**Files:**
- Modify: `content-provider/app.py`

**Step 1: Track refresh eligibility**

Add a constructor flag or equivalent state so clients built with an explicit build id do not auto-refresh on 404, while clients that discover build ids can refresh.

**Step 2: Retry once on stale auto build id**

Update `_get_data()` to catch `UpstreamError` with status `404`, clear `self.build_id`, rediscover, and retry once.

**Step 3: Verify focused tests**

Run:

```powershell
cd content-provider
pytest tests/test_app.py -q
```

Expected: all content-provider tests pass.

### Task 3: Docs And Metadata

**Files:**
- Modify: `content-provider/README.md`
- Modify: `AGENTS.md`

**Step 1: Document behavior**

Document that auto-discovered build ids refresh once on `_next/data` 404, while explicitly configured ids remain fixed.

**Step 2: Update AGENTS changelog**

Prepend a `[2026-06-27] content-provider - ...` entry under `## 变更历史`.

**Step 3: Final verification**

Run:

```powershell
cd content-provider
pytest
cd ..
git diff --check
```

Expected: tests and diff check pass.
