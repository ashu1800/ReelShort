# Fixed Debug Signing Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Make every future ShortLink Android release use the existing fixed Debug certificate and remove the obsolete formal signing materials.

**Architecture:** Keep the optimized Android `release` build type, but sign it with a dedicated `fixedDebug` signing config that points to the current user's `.android/debug.keystore`. Pin that certificate in the publisher so a missing or regenerated Debug keystore fails before upload. Update all active release documentation and project context to describe the one-time certificate transition.

**Tech Stack:** Gradle Kotlin DSL, Python `unittest`, Android build-tools (`apksigner`, `aapt`), PowerShell/ADB, Markdown.

---

### Task 1: Pin the Debug certificate in the publisher

**Files:**
- Modify: `scripts/tests/test_publish_android.py`
- Modify: `scripts/publish-android.py`

**Step 1: Write the failing tests**

- Rename the acceptance test to require `EXPECTED_DEBUG_CERT_SHA256`.
- Add a rejection case for the former formal certificate.
- Add a source contract test that requires the Android release build to use `fixedDebug` and forbids `ANDROID_SIGNING_*`.

**Step 2: Run the tests to verify RED**

Run: `python -m unittest scripts.tests.test_publish_android`

Expected: FAIL because the publisher still exposes `EXPECTED_RELEASE_CERT_SHA256` and Gradle still uses environment-provided release signing.

**Step 3: Implement the minimal publisher change**

- Replace the formal certificate constant with the fixed Debug SHA-256.
- Rename certificate verification messaging to explicitly identify the fixed Debug certificate.
- Keep `:app:assembleRelease` and the existing APK metadata, SHA-256, COS upload and backend registration flow.

**Step 4: Run the focused tests**

Run: `python -m unittest scripts.tests.test_publish_android`

Expected: the certificate tests pass; the Gradle contract remains failing until Task 2.

### Task 2: Sign the optimized release variant with the fixed Debug keystore

**Files:**
- Modify: `android-app/app/build.gradle.kts`

**Step 1: Implement the fixed signing config**

- Remove all `ANDROID_SIGNING_*` environment-variable handling.
- Create `fixedDebug` using `${System.getProperty("user.home")}/.android/debug.keystore`, alias `androiddebugkey`, and the standard Debug keystore credentials.
- Require the keystore file to exist so Gradle cannot silently create a new certificate for releases.
- Assign `fixedDebug` to the `release` build type while keeping minification and resource shrinking.

**Step 2: Run tests to verify GREEN**

Run: `python -m unittest scripts.tests.test_publish_android`

Expected: PASS.

**Step 3: Build and inspect a release APK**

Run: `android-app\gradlew.bat :app:assembleRelease -PappVersionName=0.8.7 -PappVersionCode=41 --no-daemon`

Expected: build exits 0 and `app-release.apk` is signed by `7bb921...cca436`.

### Task 3: Remove formal signing material and update context

**Files:**
- Create: `android-app/SIGNING.md`
- Modify: `android-app/README.md`
- Modify: `docs/deploy/publish-android.md`
- Modify: `docs/deploy/release-checklist.md`
- Modify: `AGENTS.md`
- Delete locally: `android-app/release-signing/*`
- Delete locally: `C:\Users\Ashu\.shortlink-signing\*`

**Step 1: Write the current signing documentation**

- Record the fixed Debug keystore path, certificate SHA-256, build semantics, publisher command and migration impact.
- Remove active references to formal certificates and `ANDROID_SIGNING_*`.
- Add the required `AGENTS.md` module description and top change-history entry.

**Step 2: Delete formal key material**

- Resolve and verify each exact path.
- Delete only the named formal JKS, DPAPI backup, password configuration and obsolete ignored README.
- Leave `C:\Users\Ashu\.android\debug.keystore` intact.

**Step 3: Verify documentation and filesystem state**

Run: `rg -n "ANDROID_SIGNING_|正式签名材料|EXPECTED_RELEASE_CERT|c1a149d5" AGENTS.md android-app scripts docs/deploy`

Expected: no active formal-signing instruction remains.

### Task 4: Verify upgrade compatibility and integrate

**Files:**
- No additional source files.

**Step 1: Verify APK metadata and signature**

Run `apksigner verify --print-certs` and `aapt dump badging` against `android-app/app/build/outputs/apk/release/app-release.apk`.

Expected: certificate `7bb921...cca436`, package `com.reelshort.app`, version `0.8.7 (41)`.

**Step 2: Verify an in-place upgrade from the existing Debug install**

Run: `adb install -r android-app/app/build/outputs/apk/release/app-release.apk`

Expected: `Success`, replacing the installed Debug-signed `0.8.5 (39)` without uninstalling.

**Step 3: Run final checks**

Run:

```powershell
python -m unittest scripts.tests.test_publish_android
git diff --check
git status --short
```

Expected: tests pass, no whitespace errors, and only intended files are changed.

**Step 4: Commit, synchronize and push**

Commit code/docs with `fix(android): standardize debug signing`, fetch/rebase from `origin/master`, push the feature branch, then merge to `master` according to repository workflow.
