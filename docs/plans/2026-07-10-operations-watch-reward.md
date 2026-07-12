# Operations Watch Reward Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add internal operations APIs for querying a user's points account, fetching a watch-reward task, and simulating watch progress to award points through the existing idempotent reward flow.

**Architecture:** Implement a backend-only internal operations module protected by `X-Internal-Super-Token`. Reuse `WatchService.reportProgress()` and existing points reward logic rather than adding a direct point-credit path. Do not change Android or public App APIs.

**Tech Stack:** Spring Boot, Java 17, JPA repositories, MockMvc tests, existing `WatchService`, `PointsService`, content cache repositories, and admin audit logging.

---

### Task 1: Internal Operations Security Contract

**Files:**
- Test: `backend/src/test/java/com/reelshort/backend/operations/InternalOperationsControllerTests.java`
- Create: `backend/src/main/java/com/reelshort/backend/operations/InternalOperationsController.java`
- Create: `backend/src/main/java/com/reelshort/backend/operations/InternalOperationsService.java`
- Reuse: `backend/src/main/java/com/reelshort/backend/auth/InternalPhoneUserController.java`

**Step 1: Write the failing test**

Add a `@SpringBootTest(properties = "reelshort.internal.super-token=test-super-token")` + `@AutoConfigureMockMvc` test class.

Test `GET /api/internal/operations/users/{userId}/points/account`:
- no `X-Internal-Super-Token` returns `401` and message `unauthorized`
- wrong token returns `403` and message `forbidden`

**Step 2: Run test to verify it fails**

Run:

```powershell
cd backend
.\gradlew.bat test --tests "com.reelshort.backend.operations.InternalOperationsControllerTests" --no-daemon
```

Expected: fail because controller does not exist.

**Step 3: Write minimal implementation**

Create `InternalOperationsController` under `/api/internal/operations`.

Inject `InternalProperties` or the same config used by `InternalPhoneUserController`. Add a private token guard equivalent to the existing internal register controller:

```java
private void requireInternalToken(String token) {
    if (!StringUtils.hasText(token)) {
        throw new AuthException(401, "unauthorized");
    }
    if (!StringUtils.hasText(internalProperties.superToken()) || !internalProperties.superToken().equals(token)) {
        throw new AuthException(403, "forbidden");
    }
}
```

Return a temporary success body from the points endpoint after passing the guard.

**Step 4: Run test to verify it passes**

Run the same Gradle command. Expected: pass.

---

### Task 2: Points Account Query

**Files:**
- Modify: `backend/src/test/java/com/reelshort/backend/operations/InternalOperationsControllerTests.java`
- Modify: `backend/src/main/java/com/reelshort/backend/operations/InternalOperationsController.java`
- Modify: `backend/src/main/java/com/reelshort/backend/operations/InternalOperationsService.java`
- Create: `backend/src/main/java/com/reelshort/backend/operations/InternalPointsAccountResponse.java`

**Step 1: Write the failing test**

In `InternalOperationsControllerTests`:
- create an app user through `POST /api/internal/users/register-phone`
- adjust points through existing admin or directly seed `PointAccount`
- call `GET /api/internal/operations/users/{userId}/points/account` with the correct token
- assert response contains `userId`, `account`, `status`, `balance`, `frozenPoints`, `availablePoints`

**Step 2: Run test to verify it fails**

Expected: endpoint returns placeholder or missing fields.

**Step 3: Write minimal implementation**

In `InternalOperationsService.pointsAccount(userId)`:
- load `UserAccount` by id or throw `AuthException(404, "user not found")`
- get `PointAccountResponse` from `PointsService.account(userId)`
- return `InternalPointsAccountResponse`

**Step 4: Run test to verify it passes**

Run targeted operations tests.

---

### Task 3: Watch Reward Task Selection

**Files:**
- Modify: `backend/src/test/java/com/reelshort/backend/operations/InternalOperationsControllerTests.java`
- Modify: `backend/src/main/java/com/reelshort/backend/operations/InternalOperationsController.java`
- Modify: `backend/src/main/java/com/reelshort/backend/operations/InternalOperationsService.java`
- Create: `backend/src/main/java/com/reelshort/backend/operations/InternalWatchRewardTaskResponse.java`
- Reuse: `backend/src/main/java/com/reelshort/backend/watch/WatchRecordRepository.java`
- Reuse: `backend/src/main/java/com/reelshort/backend/content/ContentBookCacheRepository.java`
- Reuse: `backend/src/main/java/com/reelshort/backend/content/ContentEpisodeCacheRepository.java`

**Step 1: Write the failing tests**

Add tests:
- existing watch record at 24% returns `nextRewardStage=25` for the same book and episode
- existing watch record at 25% with 25 already claimed returns `nextRewardStage=50`
- no watch records falls back to content cache and returns an available episode with `durationSeconds=300`

Seed watch records and content cache using existing entity factories/repositories. If repository APIs are missing, add the smallest repository methods needed.

**Step 2: Run tests to verify they fail**

Expected: task endpoint does not exist or returns no task.

**Step 3: Write minimal implementation**

Add `GET /api/internal/operations/users/{userId}/watch-reward-task`.

Service rules:
- reject missing user with `404 user not found`
- reject non-`ACTIVE` user with `403 user is not active`
- inspect user's watch records ordered by `updatedAt desc`
- calculate next stage from `[25, 50, 75, 100]` using claimed stages and progress
- if no usable watch record, load one cached book and one cached episode for locale `en`
- return `canReport=true` when a stage exists; otherwise `canReport=false`
- default `durationSeconds=300`

**Step 4: Run tests to verify they pass**

Run targeted operations tests.

---

### Task 4: Simulated Watch Progress Report

**Files:**
- Modify: `backend/src/test/java/com/reelshort/backend/operations/InternalOperationsControllerTests.java`
- Modify: `backend/src/main/java/com/reelshort/backend/operations/InternalOperationsController.java`
- Modify: `backend/src/main/java/com/reelshort/backend/operations/InternalOperationsService.java`
- Create: `backend/src/main/java/com/reelshort/backend/operations/InternalWatchProgressRequest.java`
- Create: `backend/src/main/java/com/reelshort/backend/operations/InternalWatchProgressResponse.java`

**Step 1: Write the failing tests**

Add tests:
- posting `progressPercent=100` awards stages `[25,50,75,100]` and increases balance by configured stage points
- repeating the same request awards no additional points
- posting 25 then 75 only awards missing 50 and 75 on the second call
- invalid progress `30` returns `400 invalid progress stage`

**Step 2: Run tests to verify they fail**

Expected: endpoint missing.

**Step 3: Write minimal implementation**

Add `POST /api/internal/operations/users/{userId}/watch-progress`.

Request:

```java
public record InternalWatchProgressRequest(
    @NotBlank String bookId,
    @Min(1) int episodeNum,
    @Min(0) long positionSeconds,
    @Min(1) long durationSeconds,
    @Min(1) @Max(100) int progressPercent,
    @NotBlank String reason
) {}
```

Validate `progressPercent` is exactly one of 25/50/75/100.

Build a `WatchProgressRequest` and call `watchService.reportProgress(userId, request)`. Return book, episode, progress, awarded stages, awarded points, and current account snapshot.

**Step 4: Run tests to verify they pass**

Run targeted operations tests.

---

### Task 5: User Status and Audit Logging

**Files:**
- Modify: `backend/src/test/java/com/reelshort/backend/operations/InternalOperationsControllerTests.java`
- Modify: `backend/src/main/java/com/reelshort/backend/operations/InternalOperationsService.java`
- Reuse: `backend/src/main/java/com/reelshort/backend/admin/AdminAuditService.java`

**Step 1: Write the failing tests**

Add tests:
- `DISABLED` user calling task endpoint returns `403 user is not active`
- `BLACKLISTED` user calling progress endpoint returns `403 user is not active`
- successful progress simulation writes an audit log containing `internal-operations`, target user id, book id, episode, progress and reason

**Step 2: Run tests to verify they fail**

Expected: current service allows non-active users or no audit exists.

**Step 3: Write minimal implementation**

In service methods:
- check `UserStatus.ACTIVE` before task and progress operations
- call `adminAuditService.record("internal-operations", "Simulated watch reward ...")` or existing method signature
- include awarded stages and points in audit detail string

**Step 4: Run tests to verify they pass**

Run targeted operations tests.

---

### Task 6: Admin Web Not Required, Docs Required

**Files:**
- Modify: `docs/api/auth-user.md` or create `docs/api/internal-operations.md`
- Modify: `AGENTS.md`

**Step 1: Write docs**

Create `docs/api/internal-operations.md` documenting:
- auth header
- points account endpoint
- watch reward task endpoint
- simulated progress endpoint
- idempotency and disabled/blacklisted restrictions

Update `AGENTS.md`:
- add backend operations module description if new package is significant
- add change history line `[2026-07-10] backend/operations - ...`

**Step 2: Verify docs are present**

Run:

```powershell
Test-Path docs\api\internal-operations.md
rg -n "operations|watch-reward-task|watch-progress" AGENTS.md docs\api\internal-operations.md
```

Expected: paths and text found.

---

### Task 7: Full Verification

**Files:** none

**Step 1: Run backend tests**

```powershell
backend\.\gradlew.bat test --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

**Step 2: Run content-provider tests**

```powershell
python -m pytest content-provider
```

Expected: all tests pass.

**Step 3: Run Android tests/build**

This change does not touch Android. Run only if Android files changed:

```powershell
android-app\.\gradlew.bat :app-core:test :app:testDebugUnitTest :app:assembleDebug --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

**Step 4: Run diff check**

```powershell
git diff --check
```

Expected: no whitespace errors.

**Step 5: Commit**

```powershell
git add .
git fetch origin master
git commit -m "feat(operations): add simulated watch rewards"
git push origin master
```

---

### Task 8: Server Deployment and Smoke Test

**Files:** deployment only

**Step 1: Deploy backend**

Copy changed backend/docs files to `/opt/reelshort` or pull latest `master`, then run:

```bash
cd /opt/reelshort/infra
docker compose --env-file .env up -d --build backend
```

**Step 2: Verify health**

```bash
curl -fsS https://shortlink.hjj888.cc/actuator/health
```

Expected: `{"status":"UP"}`.

**Step 3: Smoke test internal API**

内部运营接口不得经公网 Nginx 携带 `X-Internal-Super-Token` 调用。公网地址
`https://shortlink.hjj888.cc/api/internal/...` 应返回 `404`；部署主机上请通过
Compose backend 容器内的 loopback 执行以下 smoke，或从可信内网直连 backend 服务。

```bash
USER_ID="<userId>"
docker compose --env-file .env exec -e USER_ID="$USER_ID" -T backend sh -lc 'curl -i "http://127.0.0.1:8080/api/internal/operations/users/${USER_ID}/points/account" -H "X-Internal-Super-Token: <server token>"'
```

Expected: `200` with account snapshot for a valid user.

```bash
docker compose --env-file .env exec -e USER_ID="$USER_ID" -T backend sh -lc 'curl -i "http://127.0.0.1:8080/api/internal/operations/users/${USER_ID}/watch-reward-task" -H "X-Internal-Super-Token: <server token>"'
```

Expected: `200` with a task or a clear no-task response.

```bash
docker compose --env-file .env exec -e USER_ID="$USER_ID" -T backend sh -lc 'curl -i -X POST "http://127.0.0.1:8080/api/internal/operations/users/${USER_ID}/watch-progress" -H "X-Internal-Super-Token: <server token>" -H "Content-Type: application/json" --data-binary '\''{"bookId":"<bookId>","episodeNum":1,"positionSeconds":75,"durationSeconds":300,"progressPercent":25,"reason":"ops smoke test"}'\'''
```

Expected: `200`, awarded points if the stage was not already claimed.
