# ReelShort Android App

Android 原生客户端骨架，规划使用 Kotlin、Jetpack Compose 和 Android 原生媒体播放能力。

当前已建立阶段 1 的核心页面边界、API 接入边界和纯 Kotlin UI 状态控制边界，Compose UI 已接入 `AppStateController`/`AppUiState`：

- 登录页：通过 `AppStateController` 调用 Spring Boot 登录/注册接口，并写入共享状态和文件型 `SessionStore`。
- 首页：通过 `AppStateController` 加载 Spring Boot 首页货架接口数据。
- 搜索页：通过 `AppStateController` 调用 Spring Boot 搜索接口并维护搜索状态。
- 详情页：通过 `AppStateController` 加载剧集信息和分集列表。
- 播放页：通过 `AppStateController` 获取播放地址并维护纯 Kotlin `PlaybackState`；Compose 页面使用 Media3/ExoPlayer 播放 Spring Boot 返回的媒体 URL，将播放器进度同步到 `PlaybackState`，展示 25/50/75/100 观看奖励阶段提示，并保留播放地址、刷新地址和当前进度上报入口。
- 账户页：通过 `AppStateController` 刷新观看记录、积分和订单快照，展示本地 API 连接诊断，保留后续核心闭环和商业化接口边界。

## 模块结构

- `app`：Android Compose UI 模块，负责页面骨架和本地交互。
- `app-core`：纯 Kotlin JVM 核心模块，包含 Spring Boot API 配置、统一响应模型、App 数据模型、`ReelShortApiClient` 边界、`FakeReelShortApiClient`、`OkHttpReelShortApiClient`、`SessionStore`、`InMemorySessionStore`、`FileSessionStore`、`AppDataSource`、`AppRepository`、`AppUiState`、`PlaybackState`、`AppStateController` 和 `AppUiActions`。

App 只访问 Spring Boot API，不直接访问 Flask 内容源服务。当前 `FakeReelShortApiClient` 用于无 Android SDK 环境下的结构验证；`OkHttpReelShortApiClient` 用于真实 Spring Boot API 访问，并通过 token provider 为受保护 App 业务接口添加 Bearer Token；健康检查使用公开的 `/api/system/health`，不携带 Bearer Token。`SessionStore` 提供纯 Kotlin 会话存储边界，`FileSessionStore` 使用本地 JSON 文件保存登录会话，当前 Android 组合根使用 `filesDir/reelshort-session.json` 恢复登录状态；后续平台层可替换为 DataStore 或加密 SharedPreferences。`AppStateController` 以 `StateFlow<AppUiState>` 暴露登录、启动恢复、登出、首页、搜索、详情、播放、观看上报、积分、观看记录、订单和 API 诊断状态，Compose UI 只负责展示状态和触发动作。`PlaybackState` 保存当前剧集、分集、播放 URL、播放位置、进度百分比、已上报进度和播放地址刷新结果，播放页通过 Media3 消费合法 HTTP/HTTPS 媒体 URL，定时同步播放器当前位置，并只通过用户手动点击上报观看奖励阶段。

当前机器已配置 Android SDK，可构建 debug APK 并安装到雷电模拟器进行基础启动验证。

## 可运行验证

```powershell
cd android-app
.\gradlew.bat :app-core:test --no-daemon
.\gradlew.bat :app:assembleDebug --no-daemon
```

雷电模拟器安装验证：

```powershell
C:\leidian\LDPlayer14\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk
C:\leidian\LDPlayer14\adb.exe shell am start -n com.reelshort.app/.MainActivity
```

## 后续开发环境

- Android SDK 默认安装在 `%LOCALAPPDATA%\Android\Sdk`。
- `local.properties` 中配置 `sdk.dir`，或由 Android Studio 自动生成。
- 后续可将 Media3 播放进度接入自动进度同步和阶段式积分奖励上报。

