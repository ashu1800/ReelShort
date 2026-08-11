# Official-Only Android Signing Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Ensure every ShortLink Android APK variant is signed only by the restored official certificate.

**Architecture:** Replace the dedicated Debug signing config with one environment-backed `official` signing config shared by Debug and Release. Every APK or app-bundle packaging task validates that the configured keystore exists and contains the pinned official certificate before packaging starts; the publisher independently keeps its post-build APK certificate verification.

**Tech Stack:** Android Gradle Plugin, Kotlin Gradle DSL, Java `KeyStore`/`MessageDigest`, Python `unittest`, Android `apksigner` and `aapt`.

---

### Task 1: Define the all-variant signing contract

**Files:**
- Modify: `scripts/tests/test_publish_android.py`
- Test: `scripts/tests/test_publish_android.py`

**Step 1: Replace the old signing source contract test**

Update `test_release_build_uses_environment_backed_release_signing` to require:

```python
def test_all_android_variants_use_only_official_signing(self):
    gradle_file = Path(__file__).resolve().parents[2] / "android-app" / "app" / "build.gradle.kts"
    gradle = gradle_file.read_text(encoding="utf-8")

    self.assertIn('environmentVariable("ANDROID_SIGNING_STORE_FILE")', gradle)
    self.assertIn('create("official")', gradle)
    self.assertEqual(2, gradle.count('signingConfig = signingConfigs.getByName("official")'))
    self.assertIn(publisher.EXPECTED_RELEASE_CERT_SHA256, gradle)
    self.assertIn("validateOfficialSigning", gradle)
    self.assertNotIn("fixedDebug", gradle)
    self.assertNotIn("debug.keystore", gradle)
```

**Step 2: Run the focused test and verify RED**

Run:

```powershell
python -m unittest scripts.tests.test_publish_android.PublishAndroidTests.test_all_android_variants_use_only_official_signing
```

Expected: FAIL because the current Gradle file still creates `fixedDebug` and does not create `official`.

**Step 3: Commit the failing contract test only after the RED evidence is recorded**

```powershell
git add scripts/tests/test_publish_android.py
git commit -m "test(android): require official signing for all variants"
```

### Task 2: Make Gradle fail closed on the official certificate

**Files:**
- Modify: `android-app/app/build.gradle.kts`
- Test: `scripts/tests/test_publish_android.py`

**Step 1: Add the official certificate verifier**

Import Java security APIs and define the pinned digest:

```kotlin
import java.security.KeyStore
import java.security.MessageDigest

val expectedOfficialCertSha256 =
    "c1a149d5352c5c313d8d7d995470da31a3bd298281abba914e559747bb45c2f9"
```

Add a helper that:

1. Checks all four `ANDROID_SIGNING_*` values are present.
2. Checks the configured keystore file exists.
3. Loads the JKS using the configured store password.
4. Reads the configured alias certificate.
5. Computes lowercase SHA-256 and compares it with `expectedOfficialCertSha256`.
6. Fails without logging passwords or private-key data.

**Step 2: Replace all signing configs with one official config**

Remove `fixedDebugKeystore` and `create("fixedDebug")`. Always create `official`, populate it only when configuration is complete, and assign it to both build types:

```kotlin
signingConfigs {
    create("official") {
        if (officialSigningConfigured) {
            storeFile = file(signingStoreFile!!)
            storePassword = signingStorePassword
            keyAlias = signingKeyAlias
            keyPassword = signingKeyPassword
        }
    }
}

buildTypes {
    getByName("debug") {
        signingConfig = signingConfigs.getByName("official")
    }
    getByName("release") {
        // Existing minification and ProGuard settings remain unchanged.
        signingConfig = signingConfigs.getByName("official")
    }
}
```

**Step 3: Validate before every app package/bundle task**

Match `packageDebug`, `packageRelease`, `bundleDebug`, `bundleRelease`, and Android-test packaging variants. Call `validateOfficialSigning()` in `doFirst` so no APK/AAB can fall back to another certificate.

**Step 4: Run the focused test and verify GREEN**

Run:

```powershell
python -m unittest scripts.tests.test_publish_android.PublishAndroidTests.test_all_android_variants_use_only_official_signing
```

Expected: PASS.

**Step 5: Run all publisher tests**

Run:

```powershell
python -m unittest scripts.tests.test_publish_android
```

Expected: 7 tests pass with no failures.

**Step 6: Commit Gradle implementation**

```powershell
git add android-app/app/build.gradle.kts scripts/tests/test_publish_android.py
git commit -m "fix(android): enforce official signing for all variants"
```

### Task 3: Make the current project context unambiguous

**Files:**
- Modify: `AGENTS.md`
- Modify: `android-app/SIGNING.md`
- Modify: `android-app/README.md`
- Modify: `docs/deploy/publish-android.md`

**Step 1: Update active signing instructions**

Document all of the following:

- Debug and Release APKs use the same official keystore and certificate.
- `Debug` means debuggable build behavior only; it no longer means Debug certificate.
- ReelShort never reads `C:\Users\Ashu\.android\debug.keystore`.
- Missing official configuration or a non-matching certificate stops packaging.
- The official certificate SHA-256 remains `c1a149...c2f9`.
- The global Debug keystore is left untouched because it may belong to other projects.

**Step 2: Update `AGENTS.md` module description and change history**

At the top of `## 变更历史`, add:

```text
[2026-08-11] android-app/release/scripts/docs - 移除 ReelShort 的 Debug 证书签名路径；Debug 与 Release 变体统一使用恢复后的原正式 keystore，并在所有打包任务前钉死校验证书 SHA-256。Debug 仅保留可调试构建属性，不再代表 Debug 证书。
```

Update the Android release module and build command descriptions to match the new invariant.

**Step 3: Verify active documentation no longer instructs Debug signing**

Run:

```powershell
rg -n "fixedDebug|\.android[/\\]debug\.keystore|Debug 变体.*Debug|Debug keystore" AGENTS.md android-app scripts docs/deploy
```

Expected: no active signing instruction refers to the Debug keystore. Historical files under `docs/plans` are intentionally excluded.

**Step 4: Commit documentation**

```powershell
git add AGENTS.md android-app/SIGNING.md android-app/README.md docs/deploy/publish-android.md
git commit -m "docs(android): mark official signing as mandatory"
```

### Task 4: Build and verify both APK variants

**Files:**
- Verify: `android-app/app/build/outputs/apk/debug/app-debug.apk`
- Verify: `android-app/app/build/outputs/apk/release/app-release.apk`

**Step 1: Load ignored signing values into the current PowerShell process**

Read key/value pairs from the main workspace `android-app/release-signing/signing-config.txt`, set the four `ANDROID_SIGNING_*` environment variables without printing their values, and point `ANDROID_SIGNING_STORE_FILE` to the restored `shortlink-release.jks`.

**Step 2: Run Android tests and Lint**

Run from `android-app`:

```powershell
.\gradlew.bat :app-core:test :app:testDebugUnitTest lint
```

Expected: `BUILD SUCCESSFUL`.

**Step 3: Build both variants**

Run:

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleRelease -PappVersionName=0.8.8 -PappVersionCode=42
```

Expected: `BUILD SUCCESSFUL` and both APK files exist.

**Step 4: Verify both certificates and metadata**

Run `apksigner verify --print-certs` on both APKs. Expected SHA-256 for both:

```text
c1a149d5352c5c313d8d7d995470da31a3bd298281abba914e559747bb45c2f9
```

Run `aapt dump badging` on both APKs. Expected package `com.reelshort.app`, versionName `0.8.8`, and versionCode `42`.

**Step 5: Run repository checks**

```powershell
python -m unittest scripts.tests.test_publish_android
git diff --check
git status --short
```

Expected: tests pass, no whitespace errors, and only intended files are changed.

### Task 5: Synchronize and integrate

**Files:**
- Verify all changed files from Tasks 1-4.

**Step 1: Fetch and rebase the feature branch**

```powershell
git fetch origin codex/official-signing-only
git pull --rebase origin codex/official-signing-only
```

Expected: no conflicts.

**Step 2: Push the completed feature branch**

```powershell
git push origin codex/official-signing-only
```

**Step 3: Fast-forward `master` and push**

From the main workspace:

```powershell
git fetch origin master
git merge --ff-only codex/official-signing-only
git push origin master
```

Expected: `master` and `origin/master` point to the same final commit. Preserve the unrelated untracked `.github/workflows/android-flutter-ci.yml` file.

**Step 4: Final online-neutrality check**

Confirm no publish command ran and `/api/app/release/latest` is unchanged. This task changes future packaging only and must not create a new online release.
