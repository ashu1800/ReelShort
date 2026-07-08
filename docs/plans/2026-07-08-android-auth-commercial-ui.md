# Android 认证页商业级重设计 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 将 Android 认证页重构为默认登录、注册为次级入口的商业级认证流程页，减少视觉噪音并提升流程清晰度。

**Architecture:** 在 `AppUiState` 增加认证模式状态，`MainActivity` / `AuthBottomSheet` 根据模式渲染不同字段与主 CTA。认证页复用现有底部弹层和品牌区，只重排结构，不改后端和认证接口。测试围绕状态切换、文案与按钮可见性、倒计时触发与触控可达性展开。

**Tech Stack:** Kotlin, Jetpack Compose, Material3, `app-core` 状态层, Compose 单元测试, Android emulator smoke test

---

### Task 1: 引入认证模式状态

**Files:**
- Modify: `android-app/app-core/src/main/kotlin/com/reelshort/app/state/AppUiState.kt`
- Modify: `android-app/app-core/src/main/kotlin/com/reelshort/app/state/AppStateController.kt`
- Test: `android-app/app-core/src/test/kotlin/com/reelshort/app/state/AppStateControllerTest.kt`

**Step 1: Write the failing test**
- 添加测试，验证认证弹层默认处于登录模式，触发公开播放/账户认证时仍保持这个默认值。
- 添加测试，验证切换到注册模式后，状态可以回到登录模式。

**Step 2: Run test to verify it fails**
- Run: `.\gradlew.bat :app-core:test --tests com.reelshort.app.state.AppStateControllerTest --no-daemon`
- Expected: FAIL，因为认证模式状态还不存在。

**Step 3: Write minimal implementation**
- 在 `AppUiState` 增加 `authMode` 之类的状态字段。
- 在 `AppStateController` 增加切换认证模式的方法。
- 保持默认值为登录模式。

**Step 4: Run test to verify it passes**
- Run: `.\gradlew.bat :app-core:test --tests com.reelshort.app.state.AppStateControllerTest --no-daemon`
- Expected: PASS。

**Step 5: Commit**
- `git add android-app/app-core/src/main/kotlin/com/reelshort/app/state/AppUiState.kt android-app/app-core/src/main/kotlin/com/reelshort/app/state/AppStateController.kt android-app/app-core/src/test/kotlin/com/reelshort/app/state/AppStateControllerTest.kt`
- `git commit -m "feat(auth-ui): add auth mode state"`

### Task 2: 重排认证弹层结构

**Files:**
- Modify: `android-app/app/src/main/java/com/reelshort/app/ui/screens/auth/AuthScreen.kt`
- Modify: `android-app/app/src/main/java/com/reelshort/app/MainActivity.kt`
- Test: `android-app/app/src/test/kotlin/com/reelshort/app/VisualTextContractTest.kt`

**Step 1: Write the failing test**
- 添加 contract 测试，验证默认登录模式下只显示登录主按钮，注册入口为次级文字动作。
- 添加 contract 测试，验证注册模式下只显示注册主按钮，验证码字段与 `Send code` 只在注册模式出现。

**Step 2: Run test to verify it fails**
- Run: `.\gradlew.bat :app:testDebugUnitTest --tests com.reelshort.app.VisualTextContractTest --no-daemon`
- Expected: FAIL，因为 UI 还没有按模式拆分。

**Step 3: Write minimal implementation**
- 把认证表单拆成登录模式和注册模式两套渲染。
- 默认渲染登录模式。
- 让注册入口变成次级文字链接。
- 保留现有 `AuthBottomSheet` 动画、边距和安全区处理。

**Step 4: Run test to verify it passes**
- Run: `.\gradlew.bat :app:testDebugUnitTest --tests com.reelshort.app.VisualTextContractTest --no-daemon`
- Expected: PASS。

**Step 5: Commit**
- `git add android-app/app/src/main/java/com/reelshort/app/ui/screens/auth/AuthScreen.kt android-app/app/src/main/java/com/reelshort/app/MainActivity.kt android-app/app/src/test/kotlin/com/reelshort/app/VisualTextContractTest.kt`
- `git commit -m "feat(auth-ui): separate login and register flows"`

### Task 3: 收紧文案与控件层级

**Files:**
- Modify: `android-app/app/src/main/java/com/reelshort/app/ui/format/AppStrings.kt`
- Modify: `android-app/app/src/main/java/com/reelshort/app/ui/format/UiFormats.kt`
- Modify: `android-app/app/src/main/java/com/reelshort/app/ui/components/Buttons.kt`
- Test: `android-app/app/src/test/kotlin/com/reelshort/app/VisualTextContractTest.kt`

**Step 1: Write the failing test**
- 添加 contract 测试，验证认证页的模式切换文案、主按钮/次按钮层级和验证码标签语义正确。

**Step 2: Run test to verify it fails**
- Run: `.\gradlew.bat :app:testDebugUnitTest --tests com.reelshort.app.VisualTextContractTest --no-daemon`
- Expected: FAIL，因为文案和按钮层级还没收敛。

**Step 3: Write minimal implementation**
- 补充登录/注册模式相关文案。
- 让主按钮与次级链接的视觉关系更清晰。
- 保持触控目标和 disabled 状态一致。

**Step 4: Run test to verify it passes**
- Run: `.\gradlew.bat :app:testDebugUnitTest --tests com.reelshort.app.VisualTextContractTest --no-daemon`
- Expected: PASS。

**Step 5: Commit**
- `git add android-app/app/src/main/java/com/reelshort/app/ui/format/AppStrings.kt android-app/app/src/main/java/com/reelshort/app/ui/format/UiFormats.kt android-app/app/src/main/java/com/reelshort/app/ui/components/Buttons.kt android-app/app/src/test/kotlin/com/reelshort/app/VisualTextContractTest.kt`
- `git commit -m "feat(auth-ui): polish commercial auth copy"`

### Task 4: 编译、安装和模拟器验收

**Files:**
- No new files; verify existing Android code paths

**Step 1: Write the failing test**
- 不新增测试文件，直接用构建和模拟器验收覆盖最终交互。

**Step 2: Run verification**
- Run: `.\gradlew.bat :app-core:test :app:testDebugUnitTest :app:assembleDebug --no-daemon`
- Run: `adb install -r android-app\app\build\outputs\apk\debug\app-debug.apk`
- Run: emulator smoke test for login mode, register mode, SMS send success, countdown start, and button visibility.

**Expected**
- 构建通过。
- 安装成功。
- 登录和注册两种模式都能在模拟器里顺畅切换。

**Step 3: Commit**
- `git add .`
- `git commit -m "test(auth-ui): verify commercial auth redesign"`
