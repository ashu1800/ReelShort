# Android 文件会话存储设计

## 背景

Android 组合根当前使用 `InMemorySessionStore`。用户登录后 token 只保存在进程内存中，App 进程被系统回收或用户重启 App 后无法恢复登录状态。`AppStateController.restoreSession()` 已有恢复流程，但缺少真正持久化的 `SessionStore` 实现。

当前优先级是 App 和数据来源主链路，不扩展后端复杂度。因此本模块只在 Android 客户端侧补会话持久化。

## 目标

- 在 `app-core` 新增纯 Kotlin/JVM 可测试的 `FileSessionStore`。
- 登录/注册后将 `AuthSession` 写入本地 JSON 文件。
- App 重启后通过同一文件恢复 `AuthSession`。
- 登出时删除本地会话文件。
- Android 组合根使用 `filesDir/reelshort-session.json`，替代 `InMemorySessionStore`。

## 非目标

- 不引入 Android DataStore、Room 或加密 SharedPreferences。
- 不实现 token 自动续期。
- 不修改后端 token 生命周期。
- 不声明 Android `app` 模块在当前无 SDK 环境下编译通过。

## 方案

`FileSessionStore` 接收 `java.io.File`，使用 kotlinx serialization 将 `AuthSession` 映射为内部 DTO 并读写 JSON。写入使用临时文件再 `renameTo`，降低半写入风险。读取时如果文件不存在返回 `null`；如果文件损坏或字段缺失，返回 `null`，避免启动恢复被坏文件阻断。`clearSession()` 删除会话文件和临时文件。

Android 侧 `AndroidAppFactory.createActions(filesDir)` 构建 `FileSessionStore(File(filesDir, "reelshort-session.json"))`。`MainActivity` 从 `filesDir` 传入。

## 验证策略

- `FileSessionStoreTest` 覆盖保存、跨实例恢复、清除、缺失文件、损坏 JSON。
- `AppRepositoryTest` 使用 `FileSessionStore` 覆盖仓库重建后 token 恢复。
- 运行 `android-app :app-core:test`。
- 运行 `content-provider pytest` 和 `backend test` 作为主链路回归。
- `:app:assembleDebug` 只用于确认当前失败原因仍是缺 Android SDK。
