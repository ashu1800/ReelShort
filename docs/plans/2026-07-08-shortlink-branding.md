# ShortLink Branding Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 将 Android App 用户可见品牌改为 `ShortLink`，并接入一套专属 adaptive launcher icon。

**Architecture:** 用户可见品牌集中通过 Android resource 和 Compose 文案 helper 输出；代码包名和 API 命名保持稳定。Launcher icon 使用 Android vector/adaptive icon 资源，避免外部图片依赖并保持构建可重复。

**Tech Stack:** Android XML resources, Kotlin, Jetpack Compose, Gradle, Android adaptive icons

---

### Task 1: 品牌文案契约

**Files:**
- Modify: `android-app/app/src/test/kotlin/com/reelshort/app/VisualTextContractTest.kt`
- Modify: `android-app/app/src/main/java/com/reelshort/app/ui/format/UiFormats.kt`
- Modify: `android-app/app/src/main/java/com/reelshort/app/ui/format/AppStrings.kt`
- Modify: `android-app/app/src/main/java/com/reelshort/app/ui/screens/auth/AuthScreen.kt`
- Modify: `android-app/app/src/main/res/values/strings.xml`

**Step 1: Write the failing test**
- Add contract assertions that the visible brand name is `ShortLink`.
- Assert account fallback/status strings no longer show `ReelShort`.

**Step 2: Run test to verify it fails**
- Run: `.\gradlew.bat :app:testDebugUnitTest --tests com.reelshort.app.VisualTextContractTest --no-daemon`
- Expected: FAIL because the current visible brand is still `ReelShort`.

**Step 3: Write minimal implementation**
- Add a brand helper if needed.
- Change user-facing strings and `strings.xml` app name to `ShortLink`.
- Update `AuthScreen` brand lockup to use the helper instead of hardcoded text.

**Step 4: Run test to verify it passes**
- Run: `.\gradlew.bat :app:testDebugUnitTest --tests com.reelshort.app.VisualTextContractTest --no-daemon`
- Expected: PASS.

**Step 5: Commit**
- `git add android-app/app/src/test/kotlin/com/reelshort/app/VisualTextContractTest.kt android-app/app/src/main/java/com/reelshort/app/ui/format/UiFormats.kt android-app/app/src/main/java/com/reelshort/app/ui/format/AppStrings.kt android-app/app/src/main/java/com/reelshort/app/ui/screens/auth/AuthScreen.kt android-app/app/src/main/res/values/strings.xml`
- `git commit -m "feat(android): rename visible app brand to ShortLink"`

### Task 2: 专属 launcher icon

**Files:**
- Modify: `android-app/app/src/main/AndroidManifest.xml`
- Create: `android-app/app/src/main/res/drawable/ic_launcher_background.xml`
- Create: `android-app/app/src/main/res/drawable/ic_launcher_foreground.xml`
- Create: `android-app/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- Create: `android-app/app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`
- Modify: `AGENTS.md`

**Step 1: Write the failing test**
- No JVM unit test is useful for Android resource merge here; use Android resource compilation as the failing/passing contract.

**Step 2: Implement resource wiring**
- Set `android:icon="@mipmap/ic_launcher"` and `android:roundIcon="@mipmap/ic_launcher_round"`.
- Add adaptive icon XML files.
- Add vector foreground/background resources.

**Step 3: Run verification**
- Run: `.\gradlew.bat :app:assembleDebug --no-daemon`
- Expected: PASS, proving resources are valid and manifest wiring resolves.

**Step 4: Commit**
- `git add AGENTS.md android-app/app/src/main/AndroidManifest.xml android-app/app/src/main/res/drawable android-app/app/src/main/res/mipmap-anydpi-v26`
- `git commit -m "feat(android): add ShortLink launcher icon"`

### Task 3: Full Android verification

**Files:**
- No production changes unless verification finds defects.

**Step 1: Run full Android verification**
- Run: `.\gradlew.bat :app-core:test :app:testDebugUnitTest :app:assembleDebug --no-daemon`
- Expected: PASS.

**Step 2: Install to emulator**
- Run: `adb install -r android-app\app\build\outputs\apk\debug\app-debug.apk`
- Expected: `Success`.

**Step 3: Manual checks**
- App label in launcher/recent apps is `ShortLink`.
- Auth sheet brand text is `ShortLink`.
- Me page account status uses `ShortLink`.
- Launcher icon is visible and not default Android.
