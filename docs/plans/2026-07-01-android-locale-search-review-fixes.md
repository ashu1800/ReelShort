# Android Locale Search Review Fixes Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 修复本轮审查发现的 locale 缓存、语言状态一致性、全量本地化、搜索结果布局和 provider locale 路径问题，并把搜索页体验收敛到可上线的商用品质。

**Architecture:** 后端继续保持内容缓存按 locale 分桶，但修正 `content_book_cache` 主键生成和迁移长度风险；Android 侧把语言切换改为“状态先切换、请求按同一语言刷新、失败不分叉”，同时把自有 UI 文案统一收口到 `AppStrings`；搜索页维持首页同款深色影院感视觉，但在有 query 时优先展示结果；content-provider 对 locale 货架路径改为 locale 优先、旧路径 fallback。

**Tech Stack:** Kotlin + Jetpack Compose + app-core tests, Spring Boot + Flyway + JUnit, Flask + pytest, ADB emulator install

---

### Task 1: 修复内容缓存主键长度风险

**Files:**
- Modify: `backend/src/main/java/com/reelshort/backend/content/ContentBookCache.java`
- Modify: `backend/src/main/resources/db/migration/V6__content_locale_cache.sql`
- Test: `backend/src/test/java/com/reelshort/backend/content/ContentCacheServiceTests.java`
- Test: `backend/src/test/java/com/reelshort/backend/system/DatabaseMigrationTests.java`

**Step 1: 写失败测试，覆盖真实长度 `book_id + TRADITIONAL_CHINESE` 保存场景**

在 `ContentCacheServiceTests` 或新建 repository 级测试中构造真实长度 `book_id`，保存 `ContentBookCache.from(book, ContentLocale.TRADITIONAL_CHINESE)`，断言保存成功且能按 `(book_id, locale)` 查回。

**Step 2: 运行单测确认当前失败**

Run: `backend\.\gradlew.bat test --no-daemon --tests com.reelshort.backend.content.ContentCacheServiceTests`

Expected: 因 `id varchar(36)` 或实体字段长度不足导致失败。

**Step 3: 实现最小修复**

- 将 `ContentBookCache.key(bookId, locale)` 改为稳定定长 key，优先使用 `UUID.nameUUIDFromBytes(...)`。
- 保持英文和繁中都走同一稳定 key 规则，避免只有非英文特判。
- 将 `ContentBookCache.id` 长度和 `V6__content_locale_cache.sql` 中 `content_book_cache_v6.id` 调整为适配 UUID 的固定长度；若使用 UUID 字符串则为 `36`，迁移插入时也要用新 key 逻辑，不能继续写旧 `book_id`。
- 补 migration test，确保从旧表升级后 `ENGLISH` 历史数据也能正确落库。

**Step 4: 重新运行相关测试**

Run: `backend\.\gradlew.bat test --no-daemon --tests com.reelshort.backend.content.ContentCacheServiceTests --tests com.reelshort.backend.system.DatabaseMigrationTests`

Expected: PASS

**Step 5: 提交**

```bash
git add backend/src/main/java/com/reelshort/backend/content/ContentBookCache.java backend/src/main/resources/db/migration/V6__content_locale_cache.sql backend/src/test/java/com/reelshort/backend/content/ContentCacheServiceTests.java backend/src/test/java/com/reelshort/backend/system/DatabaseMigrationTests.java
git commit -m "fix(content): stabilize locale cache keys"
```

### Task 2: 修复 Android 语言切换状态分叉

**Files:**
- Modify: `android-app/app-core/src/main/kotlin/com/reelshort/app/state/AppStateController.kt`
- Modify: `android-app/app-core/src/main/kotlin/com/reelshort/app/state/AppUiState.kt`（仅在需要新增过渡字段时）
- Test: `android-app/app-core/src/test/kotlin/com/reelshort/app/state/AppStateControllerTest.kt`

**Step 1: 写失败测试**

新增测试覆盖：
- `setLanguage()` 成功时：先更新 `state.language`，再刷新首页，搜索状态被清空。
- `setLanguage()` 在 `loadHomeShelf()` 失败时：UI 仍保持新语言，错误提示出现，不能出现“偏好是新语言、UI 仍是旧语言”的分叉。

**Step 2: 运行目标测试确认失败**

Run: `android-app\.\gradlew.bat :app-core:test --no-daemon --tests "com.reelshort.app.state.AppStateControllerTest"`

Expected: 现有实现因 `saveLanguagePreference()` 和 `loadHomeShelf()` 顺序导致断言失败。

**Step 3: 实现最小修复**

- `setLanguage()` 开始时先把 `AppUiState.language` 更新为目标语言，并清空搜索 query/results。
- 然后保存 preference；若保存失败，显示本地化错误并保留当前 UI 语言，不回滚到旧值。
- 首页刷新使用刚切换的语言；成功则写入对应语言缓存，失败则保留 UI 语言并显示错误。
- 确保 repository 和 UI 都使用同一语言源。

**Step 4: 重新运行测试**

Run: `android-app\.\gradlew.bat :app-core:test --no-daemon --tests "com.reelshort.app.state.AppStateControllerTest"`

Expected: PASS

**Step 5: 提交**

```bash
git add android-app/app-core/src/main/kotlin/com/reelshort/app/state/AppStateController.kt android-app/app-core/src/test/kotlin/com/reelshort/app/state/AppStateControllerTest.kt
git commit -m "fix(app-core): keep language state consistent"
```

### Task 3: 完成全 App 自有文案本地化

**Files:**
- Modify: `android-app/app/src/main/java/com/reelshort/app/ui/format/AppStrings.kt`
- Modify: `android-app/app/src/main/java/com/reelshort/app/ui/format/EmptyStates.kt`
- Modify: `android-app/app/src/main/java/com/reelshort/app/ui/format/UiFormats.kt`
- Modify: `android-app/app/src/main/java/com/reelshort/app/ui/screens/home/HomeScreen.kt`
- Modify: `android-app/app/src/main/java/com/reelshort/app/ui/screens/favorites/FavoritesScreen.kt`
- Modify: `android-app/app/src/main/java/com/reelshort/app/ui/screens/account/AccountScreen.kt`
- Modify: `android-app/app/src/main/java/com/reelshort/app/ui/screens/auth/AuthScreen.kt`
- Modify: `android-app/app/src/main/java/com/reelshort/app/ui/screens/player/PlayerScreen.kt`
- Test: `android-app/app/src/test/kotlin/com/reelshort/app/VisualTextContractTest.kt`
- Test: `android-app/app/src/test/kotlin/com/reelshort/app/ContentEmptyStateContractTest.kt`
- Test: `android-app/app/src/test/kotlin/com/reelshort/app/EpisodeSelectorTextContractTest.kt`
- Test: `android-app/app/src/test/kotlin/com/reelshort/app/RewardBadgeContractTest.kt`

**Step 1: 先写 contract tests**

至少覆盖：
- 英文模式下首页、收藏、账户、认证、播放器、选集、奖励、空态关键文案不再出现中文。
- `zh-TW` 模式下对应关键文案为繁中。

**Step 2: 运行测试确认当前失败**

Run: `android-app\.\gradlew.bat :app:testDebugUnitTest --no-daemon --tests "com.reelshort.app.VisualTextContractTest" --tests "com.reelshort.app.ContentEmptyStateContractTest" --tests "com.reelshort.app.EpisodeSelectorTextContractTest" --tests "com.reelshort.app.RewardBadgeContractTest"`

Expected: FAIL，尤其是账户页、首页、播放器仍有中文硬编码。

**Step 3: 实现最小修复**

- 扩充 `AppStrings`，集中管理导航、首页、搜索、收藏、账户、登录/注册、播放器、评论、空态、错误、诊断、选集、奖励、语言选择文案。
- `EmptyStates.kt` / `UiFormats.kt` 改为显式接收 `AppLanguage` 或 `AppStrings`。
- 屏幕层全部通过 `strings(language)` 取文案，不直接写死中文。
- 保留上游内容标题/简介原样，不做翻译。

**Step 4: 重新运行相关测试**

Run: `android-app\.\gradlew.bat :app:testDebugUnitTest --no-daemon`

Expected: PASS

**Step 5: 提交**

```bash
git add android-app/app/src/main/java/com/reelshort/app/ui/format/AppStrings.kt android-app/app/src/main/java/com/reelshort/app/ui/format/EmptyStates.kt android-app/app/src/main/java/com/reelshort/app/ui/format/UiFormats.kt android-app/app/src/main/java/com/reelshort/app/ui/screens/home/HomeScreen.kt android-app/app/src/main/java/com/reelshort/app/ui/screens/favorites/FavoritesScreen.kt android-app/app/src/main/java/com/reelshort/app/ui/screens/account/AccountScreen.kt android-app/app/src/main/java/com/reelshort/app/ui/screens/auth/AuthScreen.kt android-app/app/src/main/java/com/reelshort/app/ui/screens/player/PlayerScreen.kt android-app/app/src/test/kotlin/com/reelshort/app/VisualTextContractTest.kt android-app/app/src/test/kotlin/com/reelshort/app/ContentEmptyStateContractTest.kt android-app/app/src/test/kotlin/com/reelshort/app/EpisodeSelectorTextContractTest.kt android-app/app/src/test/kotlin/com/reelshort/app/RewardBadgeContractTest.kt
git commit -m "fix(app): complete locale text coverage"
```

### Task 4: 调整搜索页结果优先级与商用品质布局

**Files:**
- Modify: `android-app/app/src/main/java/com/reelshort/app/ui/screens/search/SearchScreen.kt`
- Modify: `android-app/app/src/main/java/com/reelshort/app/ui/format/AppStrings.kt`
- Test: `android-app/app/src/test/kotlin/com/reelshort/app/SearchDiscoveryContractTest.kt`

**Step 1: 写失败测试**

新增 contract：
- 当 `searchQuery` 非空或 `searchResults` 非空时，结果头部和结果列表先于 discovery groups 渲染。
- 初始无 query 时仍先展示 hero + 标签，保持内容发现页定位。

**Step 2: 运行测试确认失败**

Run: `android-app\.\gradlew.bat :app:testDebugUnitTest --no-daemon --tests "com.reelshort.app.SearchDiscoveryContractTest"`

Expected: FAIL

**Step 3: 实现最小修复**

- 将 `SearchScreen` 拆成两种内容顺序：
  - 初始态：Hero -> Tags -> Hint
  - 搜索态：Hero -> Results Header -> Results/Empty -> Continue Exploring Tags
- 保持首页同款海报网格、深色背景、金色强调，不引入多余卡片层级。
- 结果为空时也把空态放在 tags 前，减少用户滚动成本。

**Step 4: 重新运行测试**

Run: `android-app\.\gradlew.bat :app:testDebugUnitTest --no-daemon --tests "com.reelshort.app.SearchDiscoveryContractTest"`

Expected: PASS

**Step 5: 提交**

```bash
git add android-app/app/src/main/java/com/reelshort/app/ui/screens/search/SearchScreen.kt android-app/app/src/test/kotlin/com/reelshort/app/SearchDiscoveryContractTest.kt
git commit -m "fix(app): prioritize discovery search results"
```

### Task 5: 修复 provider 货架 locale 路径优先级

**Files:**
- Modify: `content-provider/app.py`
- Test: `content-provider/tests/test_app.py`

**Step 1: 写失败测试**

新增 pytest 覆盖：
- `locale=zh-TW` 请求 shelf 时优先走 locale next-data 路径。
- locale 路径 404 时才 fallback 旧路径或首页 fallback。
- `recommend/newrelease/dramadub` 在繁中下不会因为旧路径可用而直接回英文货架。

**Step 2: 运行测试确认失败**

Run: `python -m pytest content-provider/tests/test_app.py -k shelf`

Expected: FAIL

**Step 3: 实现最小修复**

- `shelf()` 先尝试 locale 对应路径。
- 仅在 locale 路径 404 或空结果时，才 fallback 到旧站点路径。
- `recommend` 保持首页货架 + 扩展搜索聚合顺序不变。

**Step 4: 重新运行测试**

Run: `python -m pytest content-provider/tests/test_app.py -k shelf`

Expected: PASS

**Step 5: 提交**

```bash
git add content-provider/app.py content-provider/tests/test_app.py
git commit -m "fix(provider): prefer locale shelf data"
```

### Task 6: 文档同步与端到端验证

**Files:**
- Modify: `AGENTS.md`
- Optional Modify: `docs/api/backend-foundation.md`
- Optional Modify: `docs/api/content-cache.md`

**Step 1: 更新变更历史**

在 `AGENTS.md` 顶部追加：
- Android locale 文案全量收口
- 搜索页结果优先展示
- backend locale 缓存 key 修复
- provider locale shelf 路径修复

**Step 2: 跑完整验证**

Run:

```bash
python -m pytest content-provider
backend\.\gradlew.bat test --no-daemon
android-app\.\gradlew.bat :app-core:test :app:testDebugUnitTest :app:assembleDebug --no-daemon
adb install -r android-app\app\build\outputs\apk\debug\app-debug.apk
git diff --check
```

Expected:
- `content-provider` 全绿
- backend 全绿
- Android `app-core` / `app` 单测和 debug APK 构建成功
- APK 成功安装到模拟器
- `git diff --check` 无 whitespace error

**Step 3: 模拟器手动验收**

必须逐项验证：
- 默认英文启动，首页/账户/收藏/播放器不再出现中文硬编码。
- 切换到 `繁體中文` 后，导航、搜索标签、账户、播放器关键文案即时切换。
- 语言切换时即使首页刷新失败，UI 文案语言也不回退错乱。
- 搜索页输入关键词或点击标签后，结果区直接出现在标签区之前。
- 搜索页整体视觉仍与首页一致，具备商用级稳定观感。
- `zh-TW` 首页、搜索、分集点击播放链路正常。

**Step 4: 最终提交**

```bash
git add .
git fetch origin codex/android-locale-search-redesign
git pull --rebase origin codex/android-locale-search-redesign
git commit -m "fix(locale): address review issues across app and provider"
git push origin codex/android-locale-search-redesign
```

**Step 5: 合并准备**

在所有验证完成后，再决定是否合并到 `master`；如需合并，先做一次最终 code review，再执行非交互式 merge。
