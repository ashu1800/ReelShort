# Android UI 状态控制层设计

## 背景

Android 已经具备纯 Kotlin `app-core`、`AppRepository`、`ReelShortApiClient` 抽象、Fake API 和 OkHttp 真实 Spring Boot API Client。当前 Compose UI 仍主要是本地模拟状态，缺少一个可测试的状态控制层来承接登录、首页、搜索、详情、播放地址、观看上报、观看记录、积分和订单等页面流程。

本阶段目标是在不依赖 Android SDK 的前提下，先把 UI 业务状态放入 `app-core`。后续 Compose UI 只负责展示 `AppUiState` 并调用控制层方法，避免页面层直接拼接 API 流程。

## 方案选择

### 方案 A：Compose ViewModel 直接接入 Repository

优点是接近最终 Android UI 形态。缺点是本机没有 Android SDK，无法稳定编译和测试 UI/ViewModel 模块；同时业务状态会过早绑定 Android 框架。

### 方案 B：在 `app-core` 增加纯 Kotlin 状态控制层

优点是可用 JVM 单元测试覆盖核心交互流程，不依赖 Android SDK；后续 Compose ViewModel 可以很薄，只需桥接 `StateFlow<AppUiState>`。缺点是需要先抽象一层 `AppDataSource`，让控制器不直接绑定具体 Repository。

### 方案 C：继续在 `MainActivity` 内部维护本地状态

优点是改动少。缺点是状态和接口编排会分散在 UI 代码里，后续接入登录态恢复、播放器、错误处理和刷新逻辑时会快速膨胀。

推荐采用方案 B。

## 架构设计

新增 `AppDataSource` 接口，描述 Android UI 状态层需要的应用数据能力。`AppRepository` 实现该接口，继续作为对 `ReelShortApiClient` 的业务封装。这样状态控制器面向稳定应用能力编程，测试可使用内存 Fake DataSource。

新增 `AppUiState`，集中表达 UI 当前状态：

- 登录会话：`session`
- 页面状态：`screen`
- 通用状态：`isLoading`、`errorMessage`
- 内容状态：`homeShelf`、`searchQuery`、`searchResults`、`selectedBook`、`episodes`
- 播放状态：`selectedEpisode`、`currentVideoUrl`
- 用户状态：`watchHistory`、`pointAccount`、`orders`

新增 `AppStateController`，持有 `MutableStateFlow<AppUiState>`，对外暴露只读 `StateFlow<AppUiState>`。控制器提供协程方法：

- `login(username, password)`：登录成功后保存 session，加载首页并进入首页。
- `register(username, password)`：注册成功后执行与登录相同的状态初始化。
- `refreshHome()`：刷新首页货架。
- `search(query)`：更新搜索词和搜索结果，进入搜索页。
- `openBook(book)`：加载剧集，进入详情页。
- `openPlayer(episode)`：基于当前选中剧集获取播放地址，进入播放页。
- `reportProgress(positionSeconds, durationSeconds)`：上报当前选中剧集观看进度，并刷新观看记录和积分账户。
- `loadAccountSnapshot()`：刷新观看记录、积分账户和订单。
- `clearError()`：清理错误提示。

## 数据流

UI 调用 `AppStateController` 方法。控制器设置 `isLoading` 并清理旧错误，调用 `AppDataSource`，成功后一次性写入新的 `AppUiState`；失败时保留已有业务数据，关闭 loading，并写入 `errorMessage`。

App 仍只通过 Spring Boot API 获取内容和播放 URL。状态层不会接触 Flask 内容源，也不会保存持久化 Token；Token 持久化放到后续登录态恢复模块。

## 错误处理

本阶段统一把异常转成用户可展示的 `errorMessage`。失败不清空已有首页、搜索、剧集、播放和账户数据，避免网络失败导致界面突然空白。未选择书籍时调用 `openPlayer` 或 `reportProgress` 会返回明确错误状态，不直接抛出到 UI。

## 测试策略

只测试 `app-core` JVM 单元测试：

- 初始状态为登录页、无 loading、无错误。
- 登录成功后保存 session、加载首页、进入首页。
- 登录失败后保留登录页并写入错误。
- 搜索成功后进入搜索页并更新结果。
- 打开书籍后加载剧集并进入详情页。
- 打开分集后获取播放地址并进入播放页。
- 上报观看进度后刷新观看记录和积分。

不声明 Android UI 模块编译通过，因为当前本机没有 Android SDK。
