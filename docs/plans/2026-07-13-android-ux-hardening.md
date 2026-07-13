# Android UX Hardening Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 按使用体验复审优先级修复 Android App 的提交安全、响应式布局、反馈语义、表单与无障碍问题。

**Architecture:** 保持 `AppStateController` + `AppUiState` 单向状态流，通过少量明确状态字段承载账户提交、消息和短信成功事件；Compose 层用共享 helper 和组件实现确认、滚动、输入用途及自适应布局。避免引入新的导航框架或事件总线。

**Tech Stack:** Kotlin、Coroutines、StateFlow、Jetpack Compose、Material3、JUnit4、Gradle。

---

### Task 1: 账户提交去重与确认契约

**Files:**
- Modify: `android-app/app-core/src/main/kotlin/com/reelshort/app/state/AppUiState.kt`
- Modify: `android-app/app-core/src/main/kotlin/com/reelshort/app/state/AppStateController.kt`
- Modify: `android-app/app-core/src/test/kotlin/com/reelshort/app/state/AppStateControllerTest.kt`
- Modify: `android-app/app/src/main/java/com/reelshort/app/ui/screens/account/AccountScreen.kt`
- Create: `android-app/app/src/test/kotlin/com/reelshort/app/AccountActionContractTest.kt`

**Steps:**
1. 添加失败测试：并发调用同一资金操作只触发一次数据源；完成或失败后提交状态恢复。
2. 运行目标测试并确认因提交状态缺失而失败。
3. 最小实现账户操作状态和 Controller 去重边界。
4. 添加失败测试：钱包解绑、转账、提现需要确认，普通操作不需要。
5. 实现确认弹层和提交中按钮禁用/进度文案。
6. 运行目标测试并确认通过。

### Task 2: 弹层滚动与动态字体布局

**Files:**
- Modify: `android-app/app/src/main/java/com/reelshort/app/ui/screens/account/AccountScreen.kt`
- Modify: `android-app/app/src/main/java/com/reelshort/app/ui/components/PosterCard.kt`
- Modify: `android-app/app/src/test/kotlin/com/reelshort/app/VisualTextContractTest.kt`
- Create: `android-app/app/src/test/kotlin/com/reelshort/app/ResponsiveLayoutContractTest.kt`

**Steps:**
1. 添加失败测试：账户表单必须完全展开、可滚动并包含 IME/导航栏安全边距。
2. 添加失败测试：大字体时账户卡片不得使用阻断内容的固定高度，海报覆盖文案使用紧凑策略。
3. 运行测试确认 RED。
4. 实现共享 `SheetForm` 滚动与安全边距、移除固定卡片高度并调整海报文本。
5. 运行目标测试确认 GREEN。

### Task 3: 类型化消息反馈

**Files:**
- Modify: `android-app/app-core/src/main/kotlin/com/reelshort/app/state/AppUiState.kt`
- Modify: `android-app/app-core/src/main/kotlin/com/reelshort/app/state/AppStateController.kt`
- Modify: `android-app/app/src/main/java/com/reelshort/app/MainActivity.kt`
- Replace: `android-app/app/src/main/java/com/reelshort/app/ui/components/TopErrorToast.kt`
- Modify: `android-app/app/src/main/java/com/reelshort/app/ui/theme/Color.kt`
- Modify: controller and UI contract tests

**Steps:**
1. 添加失败测试：成功路径产生 `SUCCESS`，异常路径产生 `ERROR`，清除消息保持兼容。
2. 添加失败测试：消息视觉样式和无障碍实时区域按类型选择。
3. 运行测试确认 RED。
4. 实现 `UiMessage`/`UiMessageType`、类型化顶部消息条和语义色。
5. 迁移所有成功消息，运行目标测试确认 GREEN。

### Task 4: 验证码成功触发器

**Files:**
- Modify: `android-app/app-core/src/main/kotlin/com/reelshort/app/state/AppUiState.kt`
- Modify: `android-app/app-core/src/main/kotlin/com/reelshort/app/state/AppStateController.kt`
- Modify: `android-app/app/src/main/java/com/reelshort/app/ui/MainShell.kt`
- Modify: `android-app/app/src/main/java/com/reelshort/app/ui/screens/account/AccountScreen.kt`
- Modify: relevant controller/UI tests

**Steps:**
1. 添加失败测试：钱包/改密短信成功推进倒计时事件，失败不推进。
2. 运行测试确认 RED。
3. 实现成功触发器并把倒计时启动移出点击回调。
4. 运行目标测试确认 GREEN。

### Task 5: 银行卡占位与输入体验

**Files:**
- Modify: `android-app/app/src/main/java/com/reelshort/app/ui/components/TextField.kt`
- Modify: `android-app/app/src/main/java/com/reelshort/app/ui/screens/auth/AuthScreen.kt`
- Modify: `android-app/app/src/main/java/com/reelshort/app/ui/screens/account/AccountScreen.kt`
- Modify/Create: UI contract tests

**Steps:**
1. 添加失败测试：银行卡不可提交；手机号/验证码/金额/密码使用正确输入用途；密码支持可见性切换。
2. 运行测试确认 RED。
3. 实现字段用途枚举、键盘/IME/autofill/密码切换和信息型银行卡弹层。
4. 运行目标测试确认 GREEN。

### Task 6: 横屏网格与海报语义

**Files:**
- Modify: `android-app/app/src/main/java/com/reelshort/app/ui/screens/home/HomeScreen.kt`
- Modify: `android-app/app/src/main/java/com/reelshort/app/ui/screens/search/SearchScreen.kt`
- Modify: `android-app/app/src/main/java/com/reelshort/app/ui/components/Poster.kt`
- Modify: `android-app/app/src/main/java/com/reelshort/app/ui/components/PosterCard.kt`
- Modify/Create: responsive and accessibility contract tests

**Steps:**
1. 添加失败测试：网格列数随宽度自适应；可点击海报只提供一次组合语义。
2. 运行测试确认 RED。
3. 实现 `GridCells.Adaptive` 和海报卡父级语义。
4. 运行目标测试确认 GREEN。

### Task 7: 文档、全量验证和复审

**Files:**
- Modify: `AGENTS.md`

**Steps:**
1. 更新 Android 模块描述和 2026-07-13 变更历史。
2. 运行 `git diff --check`。
3. 运行 `gradlew :app-core:test :app:testDebugUnitTest :app:assembleDebug --no-daemon`。
4. 逐文件复审最终 diff，修复 Critical/Important 问题并重跑测试。
5. 合并到 `master` 后重新构建 APK，覆盖安装雷电模拟器。
6. 验证正常字号、150% 字体、横竖屏和关键资金弹层，检查进程与崩溃日志。
7. 按 `AGENTS.md` 执行暂存、同步、提交、推送和主分支合并。
