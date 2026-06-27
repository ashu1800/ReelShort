# Content Provider ReelShort API Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Implement the Flask ReelShort content provider endpoints consumed by Spring Boot.

**Architecture:** Keep Flask routes thin and inject a `ReelShortClient` for upstream access. Tests use a fake client through `create_app(client=...)` so API contracts are verified without network calls.

**Tech Stack:** Python, Flask, requests, pytest.

---

### Task 1: Test Harness

**Files:**
- Modify: `content-provider/requirements.txt`
- Create: `content-provider/tests/test_app.py`

**Step 1:** Add `pytest` to requirements.

**Step 2:** Write a failing health/search test using `create_app(client=fake_client)`.

**Step 3:** Run `python -m pytest`.

### Task 2: Client and Route Injection

**Files:**
- Modify: `content-provider/app.py`

**Step 1:** Add `ReelShortClient` and `UpstreamError`.

**Step 2:** Change `create_app` to accept an optional client.

**Step 3:** Implement `/health` and `/api/v1/reelshort/search`.

**Step 4:** Run `python -m pytest`.

### Task 3: Shelf Endpoints

**Files:**
- Modify: `content-provider/tests/test_app.py`
- Modify: `content-provider/app.py`

**Step 1:** Add failing tests for `/recommend`, `/newrelease`, and `/dramadub`.

**Step 2:** Implement shelf routes using shared helper code.

**Step 3:** Run `python -m pytest`.

### Task 4: Episodes and Video

**Files:**
- Modify: `content-provider/tests/test_app.py`
- Modify: `content-provider/app.py`

**Step 1:** Add failing tests for episodes and video contracts.

**Step 2:** Add validation tests for missing required query parameters.

**Step 3:** Implement endpoints.

**Step 4:** Run `python -m pytest`.

### Task 5: Docs, Review, and Merge

**Files:**
- Modify: `content-provider/README.md`
- Modify: `AGENTS.md`

**Step 1:** Document environment variables and endpoint contracts.

**Step 2:** Update AGENTS module description and change history.

**Step 3:** Run `git diff --check`.

**Step 4:** Run `python -m pytest`.

**Step 5:** Review implementation for API contract alignment and error handling.

**Step 6:** Fix findings, repeat review, commit, merge to `master`, and clean worktree.
