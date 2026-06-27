# Deploy Compose Foundation Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add deployable single-machine Docker Compose infrastructure for all runtime services.

**Architecture:** Keep Nginx as the single external HTTP entry, build backend/admin/content-provider images from local modules, use PostgreSQL and Redis managed by Compose, and inject configuration through `.env`.

**Tech Stack:** Docker Compose, Nginx, Spring Boot Gradle, Vue/Vite, Flask, PostgreSQL, Redis.

---

### Task 1: Container Build Files

**Files:**
- Create: `backend/Dockerfile`
- Create: `admin-web/Dockerfile`
- Create: `content-provider/Dockerfile`
- Create: `admin-web/nginx.conf`

**Step 1:** Add a multi-stage backend Dockerfile that builds with Gradle and runs the Spring Boot jar on Java 17.

**Step 2:** Add a multi-stage admin-web Dockerfile that builds with npm and serves static files via Nginx.

**Step 3:** Add a content-provider Dockerfile that installs Python dependencies and runs Flask on port 5000.

**Step 4:** Add admin-web Nginx config for SPA fallback and proxying `/api/` to `backend:8080`.

### Task 2: Compose and Environment

**Files:**
- Modify: `infra/docker-compose.yml`
- Create: `infra/.env.example`
- Modify: `infra/README.md`
- Modify: `docs/deploy/README.md`

**Step 1:** Update Compose to use `.env` variables for database, admin credentials, payment callback secret, backend URL and Flask upstream URL.

**Step 2:** Add health checks for PostgreSQL, Redis, backend, content-provider and Nginx where practical.

**Step 3:** Keep backend/content-provider internal by default; expose Nginx and optional database/cache dev ports.

**Step 4:** Document setup: copy `.env.example` to `.env`, edit secrets, run `docker compose --env-file .env up -d --build`.

### Task 3: Verification, Review, and Merge

**Files:**
- Modify: `AGENTS.md`

**Step 1:** Update AGENTS module history for deploy infrastructure changes.

**Step 2:** Run `.\gradlew.bat test` in `backend`.

**Step 3:** Run `npm run build` in `admin-web`.

**Step 4:** Run `pytest` in `content-provider`.

**Step 5:** Run file-level checks that required Docker/Nginx/env files exist and Compose references valid local paths.

**Step 6:** Run `git diff --check`.

**Step 7:** Review for leaked secrets, accidental public Flask exposure, broken build contexts, unnecessary lockfile churn and docs consistency.

**Step 8:** Fix findings, repeat verification, commit, merge to `master`, verify on `master`, then clean up worktree and branch.
