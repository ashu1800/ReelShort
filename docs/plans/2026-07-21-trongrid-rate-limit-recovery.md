# TronGrid Rate Limit Recovery Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Prevent public TronGrid HTTP 429 bursts from blocking payout preflight while preserving single-shot transaction broadcasting.

**Architecture:** Centralize pacing and bounded retry in the replay-safe `TronClient` HTTP path, explicitly exclude broadcast, and cache the chain fee parameter pair for five minutes. Make timing configurable and injectable for deterministic tests.

**Tech Stack:** Java 17, Spring Boot configuration properties, Java HTTP Client, JUnit 5, local HTTP test server, Gradle.

---

### Task 1: Add Pacing And Safe 429 Retry

**Files:**
- Modify: `backend/src/main/java/com/reelshort/backend/withdrawal/TronProperties.java`
- Modify: `backend/src/main/resources/application.properties`
- Modify: `infra/docker-compose.yml`
- Modify: `backend/src/test/java/com/reelshort/backend/withdrawal/TronClientTests.java`
- Modify: `backend/src/main/java/com/reelshort/backend/withdrawal/TronClient.java`

1. Add failing tests for replay-safe 429 recovery, retry exhaustion Chinese message, Retry-After timing, request spacing and single-shot broadcast.
2. Run focused tests and confirm failures come from missing retry/pacing behavior.
3. Add validated timing/retry properties and environment mappings.
4. Add a package-private timing-injection constructor, synchronized request-slot reservation, and a replay-safe `postJson` retry loop.
5. Route broadcast through a non-retrying call and retain the existing unknown-result handling.
6. Run focused tests until green and commit.

### Task 2: Cache Chain Fee Parameters

**Files:**
- Modify: `backend/src/test/java/com/reelshort/backend/withdrawal/TronClientTests.java`
- Modify: `backend/src/main/java/com/reelshort/backend/withdrawal/TronClient.java`

1. Add a failing test that runs two fee quotes and asserts only one `getchainparameters` request while simulations/resources still run twice.
2. Add an immutable cached fee-price pair with monotonic expiry and synchronized refresh.
3. Do not cache failed refreshes, balances, simulations or resources.
4. Run focused tests and commit.

### Task 3: Verify, Review, Integrate And Deploy

**Files:**
- Modify: `docs/api/withdrawals.md`
- Modify: `AGENTS.md`

1. Document pacing, bounded replay-safe retry, cache TTL, optional API key and broadcast exclusion. Add the required change-history entry for configuration and behavior changes.
2. Run backend full tests/build and the release baseline.
3. Request independent review and fix all Critical/Important findings with regression tests.
4. Fetch, push the feature branch, fast-forward `master`, rerun merged backend tests and push `master`.
5. Back up production database/source/config/image, upload the Git archive, build and recreate only backend.
6. Verify all services healthy, deployed commit/image match, a controlled local 429 test exercises retry, and the current payout fee simulation completes without 429. Do not broadcast a transaction.
