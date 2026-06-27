# Backend Flyway Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add Flyway-managed database migrations for the Spring Boot backend and switch the default JPA behavior from schema mutation to schema validation.

**Architecture:** Add Flyway dependencies, create `db/migration/V1__initial_schema.sql` for the current JPA model, set default `ddl-auto=validate`, and add integration tests proving Flyway creates the schema before JPA validates it.

**Tech Stack:** Spring Boot 3.4, Spring Data JPA, Flyway, PostgreSQL, H2 test database.

---

### Task 1: Add Failing Migration Test

**Files:**
- Create: `backend/src/test/java/com/reelshort/backend/system/DatabaseMigrationTests.java`

**Step 1: Write failing test**

Create a `@SpringBootTest` using H2 PostgreSQL mode with:

```properties
spring.datasource.url=jdbc:h2:mem:flyway-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH
spring.jpa.hibernate.ddl-auto=validate
spring.flyway.enabled=true
```

Assert:

- Flyway migration `1` is successful.
- Core tables exist: `users`, `access_tokens`, `admin_users`, `roles`, `permissions`, `content_book_cache`, `watch_records`, `point_accounts`, `recharge_orders`, `payment_events`, `system_configs`.
- Duplicate `users.username` fails.

**Step 2: Run test to verify it fails**

Run:

```powershell
cd backend
.\gradlew.bat test --tests com.reelshort.backend.system.DatabaseMigrationTests --no-daemon
```

Expected: fail because Flyway dependency and migration do not exist, and JPA validate has no schema.

### Task 2: Add Flyway Dependencies and Config

**Files:**
- Modify: `backend/build.gradle`
- Modify: `backend/src/main/resources/application.properties`

**Step 1: Add dependencies**

Add:

```gradle
implementation 'org.flywaydb:flyway-core'
implementation 'org.flywaydb:flyway-database-postgresql'
```

**Step 2: Change JPA default**

Set:

```properties
spring.jpa.hibernate.ddl-auto=${REELSHORT_JPA_DDL_AUTO:validate}
spring.flyway.enabled=${REELSHORT_FLYWAY_ENABLED:true}
spring.flyway.locations=classpath:db/migration
```

### Task 3: Add Initial Migration

**Files:**
- Create: `backend/src/main/resources/db/migration/V1__initial_schema.sql`

**Step 1: Create schema**

Create all current entity tables and critical unique constraints.

**Step 2: Run targeted test**

Run:

```powershell
cd backend
.\gradlew.bat test --tests com.reelshort.backend.system.DatabaseMigrationTests --no-daemon
```

Expected: pass.

### Task 4: Full Backend Regression

**Files:**
- Existing backend tests

**Step 1: Run backend test suite**

Run:

```powershell
cd backend
.\gradlew.bat test --no-daemon
```

Expected: pass.

### Task 5: Update Documentation

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/deploy/*` if deployment docs mention schema handling

**Step 1: Update AGENTS**

Add:

```text
[2026-06-27] backend/db - 增加 Flyway 初始迁移、默认 JPA schema validate 和数据库迁移验证测试。
```

Update backend/system or infra module description if needed.

### Task 6: Review, Verify, Commit, Merge

**Step 1: Review**

Run:

```powershell
git diff --check
git diff --stat
```

Inspect migration for missing tables, unique constraints, and H2/PostgreSQL compatibility.

**Step 2: Full verification**

Run:

```powershell
cd backend
.\gradlew.bat test --no-daemon
```

Also run Android core, content provider, and admin web regression before merge.

**Step 3: Commit and merge**

Commit on `feature/backend-flyway`, merge into `master`, clean worktree and branch.
