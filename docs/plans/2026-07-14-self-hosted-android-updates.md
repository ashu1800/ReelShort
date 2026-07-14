# ShortLink Self-Hosted Android Updates Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Move ShortLink update metadata and APK downloads from GitHub Releases to `shortlink.hjj888.cc`, with per-IP throttling and atomic signed releases.

**Architecture:** Docker Nginx serves a read-only host release directory and a static `latest.json` response. Android maps that manifest into the existing update state machine and keeps all current APK integrity checks. GitHub Actions only signs, builds, validates, and uploads release files through a dedicated unprivileged SSH account.

**Tech Stack:** Kotlin, OkHttp, kotlinx.serialization, Jetpack Compose update state, Nginx, Docker Compose, GitHub Actions, OpenSSH.

---

## Constraints

- Do not run unit tests, Lint, or Android emulator tests in this implementation or release workflow.
- Keep package/signature/version/SHA verification because these are release integrity checks, not test suites.
- Preserve the existing production `.env` and service data.

### Task 1: Self-hosted update manifest client

**Files:**
- Create: `android-app/app-core/src/main/kotlin/com/reelshort/app/update/ShortLinkUpdateClient.kt`
- Modify: `android-app/app/src/main/java/com/reelshort/app/ui/ReelShortViewModel.kt`

**Steps:**
1. Add a strict HTTPS manifest client for `https://shortlink.hjj888.cc/api/app/update/latest`.
2. Validate canonical SemVer, positive size, expected APK/checksum names, and `shortlink.hjj888.cc` asset hosts.
3. Map the manifest to existing `ReleaseInfo` and `ReleaseAsset` types.
4. Replace `GitHubReleaseUpdateClient` in the Android composition root.

### Task 2: Nginx downloads and throttling

**Files:**
- Modify: `admin-web/nginx.conf`
- Modify: `infra/docker-compose.yml`
- Modify: `infra/README.md`

**Steps:**
1. Add a shared per-IP connection zone.
2. Serve `/api/app/update/latest` from `/srv/releases/android/latest.json` with `no-store`.
3. Serve `/downloads/android/` read-only with one connection per IP, 2 MiB unthrottled, then 1 MiB/s.
4. Mount `../releases/android` read-only into the Nginx container.
5. Document host directory ownership and update endpoints.

### Task 3: Fast signed upload workflow

**Files:**
- Modify: `.github/workflows/android-release.yml`

**Steps:**
1. Remove GitHub Release existence checks and GitHub Release creation.
2. Generate APK, checksum, and `latest.json` in `dist`.
3. Configure OpenSSH from repository secrets.
4. Upload `.part` files with SCP.
5. Atomically rename APK/checksum first and `latest.json` last.

### Task 4: Production release account and secrets

**Steps:**
1. Generate a dedicated Ed25519 deployment key outside the repository.
2. Create server user `shortlink-release` without sudo.
3. Give the user write access only to `/opt/reelshort/releases/android`.
4. Add the deployment public key to that user's `authorized_keys`.
5. Configure GitHub repository secrets for host, port, user, private key, and pinned host key.

### Task 5: Version and documentation

**Files:**
- Modify: `android-app/app/build.gradle.kts`
- Modify: `AGENTS.md`
- Modify: `docs/deploy/release-checklist.md`

**Steps:**
1. Bump ShortLink to `0.4.0 (4)`.
2. Replace GitHub Release update instructions with self-hosted release instructions.
3. Record the architecture and workflow changes in `AGENTS.md`.

### Task 6: Build, deploy, and release

**Steps:**
1. Run `git diff --check`.
2. Run Android `:app:assembleDebug` only; do not run tests or Lint.
3. Deploy updated code to `/opt/reelshort` while preserving `infra/.env`.
4. Rebuild only the Nginx service and verify its configuration/health.
5. Commit, push, merge to `master`, create `v0.4.0`, and let the fast workflow upload files.
6. Verify `latest.json`, Range responses, content length, throttling headers, SHA-256, and APK signature metadata.
