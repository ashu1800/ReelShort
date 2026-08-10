# Android Release Signing Restoration Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Restore the original ShortLink release certificate, make Release builds fail closed on that certificate, and publish Android 0.8.8 (42).

**Architecture:** Keep signing secrets in ignored local files. The publisher loads those values into environment variables, Gradle consumes them only for the Release signing config, and the publisher independently verifies the built APK certificate before upload.

**Tech Stack:** Python 3, unittest, Gradle Kotlin DSL, Android build-tools (`apksigner`, `aapt`), Tencent COS.

---

### Task 1: Restore and verify local signing material

**Files:**
- Restore locally: `android-app/release-signing/shortlink-release.jks`
- Restore locally: `android-app/release-signing/signing-config.txt`

**Steps:**
1. Copy both files from the verified Windows shadow copy without printing configuration values.
2. Copy the same files to `C:\Users\Ashu\Desktop\ShortLink-Release-Signing-Backup` and restrict ACLs to the current user.
3. Compare SHA-256 hashes between the primary and backup copies.
4. Run `keytool -list -v` and verify the expected release certificate SHA-256.

### Task 2: Add release-signing regression tests

**Files:**
- Modify: `scripts/tests/test_publish_android.py`

**Steps:**
1. Change the accepted certificate test to the pinned release certificate.
2. Change the rejected certificate test to the fixed Debug certificate.
3. Assert the Gradle Release build uses `ANDROID_SIGNING_*` while Debug is not assigned the Release signing config.
4. Add a test for loading the ignored signing configuration without overwriting explicit environment variables.
5. Run `python -m unittest scripts.tests.test_publish_android` and confirm the new assertions fail before implementation.

### Task 3: Restore fail-closed Release signing

**Files:**
- Modify: `android-app/app/build.gradle.kts`
- Modify: `scripts/publish-android.py`

**Steps:**
1. Restore the environment-backed Gradle Release signing config.
2. Add publisher loading for `android-app/release-signing/signing-config.txt` and the restored keystore path.
3. Pin the publisher to the original release certificate SHA-256.
4. Run the publisher unit tests and confirm they pass.

### Task 4: Update signing documentation

**Files:**
- Modify: `android-app/SIGNING.md`
- Modify: `android-app/README.md`
- Modify: `docs/deploy/publish-android.md`
- Modify: `docs/deploy/release-checklist.md`
- Modify: `AGENTS.md`

**Steps:**
1. Document the restored primary and backup locations without recording passwords.
2. Document the certificate fingerprint and the Debug-to-release reinstall boundary.
3. Update module descriptions and prepend the required change-history entry.

### Task 5: Verify, integrate, and publish

**Steps:**
1. Run publisher tests, Android core/unit tests, Lint, Release assembly, and `git diff --check`.
2. Verify the generated APK certificate and metadata with Android build-tools.
3. Commit, fetch/rebase if required, push the feature branch, and merge it to `master`.
4. Run `python scripts/publish-android.py --version 0.8.8 --version-code 42 --notes ""` from `master`.
5. Verify `/api/app/release/latest`, download the published APK, and compare its SHA-256 and certificate.
