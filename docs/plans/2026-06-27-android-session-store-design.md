# Android 登录态持久化与恢复设计

## 背景

Android `app-core` 已有 Spring Boot API Client、Repository、UI 状态控制层和 JVM 测试。当前登录成功后 token 只保存在 `AppRepository.currentToken` 内存字段里，App 重启后无法恢复会话，OkHttp token provider 也缺少统一的 session 来源。

本阶段目标是在纯 Kotlin core 层建立会话存储边界，不依赖 Android SDK。真实 Android 持久化实现留给后续平台层，本阶段先提供接口、内存实现和恢复流程。

## 方案选择

### 方案 A：直接在 Compose/Android 层使用 DataStore

优点是接近最终形态。缺点是当前机器没有 Android SDK，无法稳定编译验证；同时会让登录态恢复逻辑过早耦合 UI 层。

### 方案 B：在 `app-core` 增加 `SessionStore` 抽象

优点是纯 Kotlin 可测试，Repository、OkHttp token provider 和状态控制器都能共享同一会话来源。后续 Android 层只需要实现 `SessionStore`，不改变业务状态层和 API client 边界。

### 方案 C：继续只用 `currentToken`

优点是零改动。缺点是不能恢复登录态，后续播放器、观看上报、积分等受保护接口都会在重启后丢失 token。

推荐采用方案 B。

## 架构设计

新增 `SessionStore` 接口：

- `loadSession()`：读取当前保存的 `AuthSession`
- `saveSession(session)`：保存登录/注册成功后的会话
- `clearSession()`：清空会话

新增 `InMemorySessionStore`，用于 JVM 测试和无 Android SDK 环境下的默认实现。后续 Android 平台层可增加基于 DataStore 或加密 SharedPreferences 的实现。

`AppRepository` 构造函数接收 `SessionStore`，默认使用内存实现。登录/注册成功后保存 session，并同步 `currentToken`。新增：

- `restoreSession()`：从存储读取 session，恢复 `currentToken`
- `clearSession()`：清理存储和 `currentToken`

`AppDataSource` 增加对应方法，让 `AppStateController` 不依赖具体 Repository。

`AppStateController` 新增：

- `restoreSession()`：启动时调用。存在 session 时恢复到首页并加载首页货架；没有 session 时保持登录页；恢复过程中获取首页失败则清空 session 并写入错误。
- `logout()`：清空 session，重置为登录页状态。

## Token Provider 数据流

真实 Android 组装时使用同一个 `SessionStore`：

1. 创建 `SessionStore`
2. `OkHttpReelShortApiClient(tokenProvider = { sessionStore.loadSession()?.token })`，其中 `tokenProvider` 是 suspend 函数，允许直接读取异步会话存储。
3. `AppRepository(apiClient, sessionStore)`
4. `AppStateController(repository)`

这样登录、恢复和受保护接口请求都共享同一 token 来源。

## 错误处理

无 session 时不作为错误处理，状态保持登录页。恢复 session 后加载首页失败说明 session 可能过期或网络不可用，本阶段保守清空 session，写入错误，并停留登录页。协程取消仍继续向上抛出，不转换为 UI 错误。

## 测试策略

只测试 `app-core` JVM 单元测试：

- 登录成功会保存 session，新的 Repository 可恢复该 session。
- 注册成功会保存 session。
- 清理 session 会清空 token 和存储。
- 状态控制器无 session 恢复时保持登录页。
- 状态控制器有 session 恢复时进入首页并加载货架。
- 恢复失败时清空 session 并记录错误。
- logout 后状态重置为登录页。
