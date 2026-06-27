# Infra Backup Restore Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a single-machine backup and restore foundation for PostgreSQL data and deployment configuration.

**Architecture:** Add PowerShell scripts under `infra/scripts`, ignore generated backup archives, document the recovery runbook, and provide a static verifier that does not require Docker on the current machine.

**Tech Stack:** PowerShell, Docker Compose command contract, PostgreSQL `pg_dump`/`pg_restore`, Git ignore validation.

---

### Task 1: Add Failing Static Verification Script Contract

**Files:**
- Create: `infra/scripts/verify-backup-scripts.ps1`

**Step 1: Write verifier first**

The verifier should fail until backup and restore scripts exist. It must check:

- `infra/scripts/backup.ps1` exists
- `infra/scripts/restore.ps1` exists
- `infra/backups/` is ignored by Git
- `backup.ps1` contains `pg_dump`, `-Fc`, `docker compose`, and `manifest.json`
- `restore.ps1` contains `pg_restore`, `--clean`, `--if-exists`, and `ConfirmRestore`

**Step 2: Run verifier to confirm failure**

Run:

```powershell
powershell -ExecutionPolicy Bypass -File infra/scripts/verify-backup-scripts.ps1
```

Expected: fail because the scripts do not exist.

### Task 2: Add Ignore Rule and Backup Script

**Files:**
- Modify: `.gitignore`
- Create: `infra/scripts/backup.ps1`

**Step 1: Ignore generated backups**

Add:

```gitignore
infra/backups/
```

**Step 2: Implement backup script**

Use Docker Compose to execute:

```powershell
docker compose --env-file $EnvFile -f infra/docker-compose.yml exec -T postgres pg_dump -U $PostgresUser -d $PostgresDb -Fc
```

Write output to `database.dump`, copy config files, write `manifest.json`, and clean old backup folders according to retention days.

### Task 3: Add Restore Script

**Files:**
- Create: `infra/scripts/restore.ps1`

**Step 1: Implement restore script**

Require `-ConfirmRestore`. Validate the backup directory and run:

```powershell
docker compose --env-file $EnvFile -f infra/docker-compose.yml exec -T postgres pg_restore --clean --if-exists --no-owner -U $PostgresUser -d $PostgresDb
```

### Task 4: Make Verifier Pass

**Files:**
- Modify: `infra/scripts/verify-backup-scripts.ps1`

**Step 1: Run verifier**

Run:

```powershell
powershell -ExecutionPolicy Bypass -File infra/scripts/verify-backup-scripts.ps1
```

Expected: pass without Docker.

### Task 5: Add Runbook Documentation

**Files:**
- Create: `docs/deploy/backup-restore.md`
- Modify: `docs/deploy/README.md`
- Modify: `infra/README.md`
- Modify: `AGENTS.md`

**Step 1: Document backup and restore**

Include:

- backup command
- restore command
- warning to stop backend before restore
- backup contents
- retention policy
- Redis non-persistence note
- recovery drill checklist

Add AGENTS history:

```text
[2026-06-27] infra/backup - 增加单机 PostgreSQL/配置备份恢复脚本、静态校验和恢复演练文档。
```

### Task 6: Review, Verify, Commit, Merge

**Step 1: Review**

Run:

```powershell
git diff --check
git diff --stat
powershell -ExecutionPolicy Bypass -File infra/scripts/verify-backup-scripts.ps1
```

Inspect scripts for destructive restore guards and path safety.

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

Commit on `feature/infra-backup-restore`, merge into `master`, then clean worktree and branch.
