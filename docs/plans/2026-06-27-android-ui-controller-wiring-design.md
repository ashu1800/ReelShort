# Android UI 状态控制器接线设计

## 背景

Android `app-core` 已经具备 Spring Boot API 客户端、Repository、SessionStore、`AppStateController` 和可测试的 `AppUiState`。但 `android-app/app` 的 Compose UI 仍使用本地 `AppState.sample()` 和页面内状态，登录、搜索、详情、播放、观看上报、积分和订单页面没有真正调用 `AppStateController`。

本切片目标是把 Android UI 从本地 sample state 推进到“页面消费 `AppUiState`、操作委托给 `AppStateController`”的结构。由于当前机器没有 Android SDK，本切片不声明完整 Android UI 编译通过；验证重点放在 `app-core` 的可测试适配逻辑和文件级审查。

## 方案选择

### 方案 A：直接在 Compose 中调用 Repository

改动看似少，但网络、会话和页面状态会散落在 UI 层，后续接播放器和错误处理会变得难维护。

### 方案 B：Compose 只接 `AppStateController`

推荐方案。UI 层只收集 `StateFlow<AppUiState>` 并调用 controller 方法；网络、会话、错误处理和状态迁移继续由 `app-core` 负责。这样 Android 平台层很薄，核心逻辑仍可用 JVM 测试覆盖。

### 方案 C：引入完整 Android ViewModel/DI 框架

例如 Hilt、DataStore、Navigation Compose。长期可以做，但当前环境缺 Android SDK，且会引入较大依赖和验证成本。

## 架构设计

新增 app 层组合根：

- `AndroidAppFactory`：创建 `ApiConfig`、`OkHttpReelShortApiClient`、`InMemorySessionStore`、`AppRepository` 和 `AppStateController`。
- `ReelShortApp(controller)`：收集 controller state，渲染 UI。

保留当前单 Activity 架构，不引入 Navigation Compose。页面切换继续由 `AppUiState.screen` 决定。

## UI 数据流

1. `MainActivity` 创建 controller。
2. `ReelShortApp` 使用 `collectAsState()` 读取 `AppUiState`。
3. 首次进入时调用 `restoreSession()`。
4. 登录、注册、搜索、打开详情、打开播放、上报进度、加载账户、登出都通过 coroutine 调用 controller。
5. 页面只根据 state 展示 loading、error、home/search/detail/player/account 数据。

## 状态适配

当前 UI 有 `Home/Search/Detail/Player/History/Points/Orders` 多个 tab，而 `AppUiState` 当前只有 `LOGIN/HOME/SEARCH/DETAIL/PLAYER/ACCOUNT`。本切片不扩大 core 状态机，先把 `ACCOUNT` 页面合并展示观看记录、积分和订单，并用底部导航入口进入对应核心 screen。

## 错误处理

- `AppUiState.errorMessage` 非空时在页面顶部展示错误。
- UI 提供关闭错误入口，调用 `clearError()`。
- loading 时显示统一进度状态，按钮禁用避免重复提交。

## 验证策略

当前机器没有 Android SDK，因此不执行 `:app:assembleDebug`。验证方式：

- `app-core` 新增 JVM 测试，覆盖 UI action facade 调用 controller 的登录、搜索、打开详情和登出流程。
- 运行 `android-app/gradlew.bat :app-core:test --no-daemon`。
- 文件级审查 `MainActivity.kt`，确认不再使用 `AppState.sample()` 作为业务状态源。
- 全量项目回归仍运行后端测试、content-provider pytest、admin-web build。
