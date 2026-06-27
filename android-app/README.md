# ReelShort Android App

Android 原生客户端骨架，规划使用 Kotlin、Jetpack Compose 和 Android 原生媒体播放能力。

当前已建立阶段 1 的核心页面边界、API 接入边界和纯 Kotlin UI 状态控制边界：

- 登录页：后续通过 `AppStateController` 调用 Spring Boot 登录/注册接口，并写入共享状态和 `SessionStore`。
- 首页：通过 `AppStateController` 加载 Spring Boot 首页货架接口数据。
- 搜索页：通过 `AppStateController` 调用 Spring Boot 搜索接口并维护搜索状态。
- 详情页：通过 `AppStateController` 加载剧集信息和分集列表。
- 播放页：HLS 播放器区域占位，后续接入 Android 原生媒体播放方案。
- 观看记录、积分、订单页：通过 `AppStateController` 刷新账户快照，保留后续核心闭环和商业化接口边界。

## 模块结构

- `app`：Android Compose UI 模块，负责页面骨架和本地交互。
- `app-core`：纯 Kotlin JVM 核心模块，包含 Spring Boot API 配置、统一响应模型、App 数据模型、`ReelShortApiClient` 边界、`FakeReelShortApiClient`、`OkHttpReelShortApiClient`、`SessionStore`、`AppDataSource`、`AppRepository`、`AppUiState` 和 `AppStateController`。

App 后续只访问 Spring Boot API，不直接访问 Flask 内容源服务。当前 `FakeReelShortApiClient` 用于无 Android SDK 环境下的结构验证和 UI 占位；`OkHttpReelShortApiClient` 用于真实 Spring Boot API 访问，并通过 token provider 为受保护 App 业务接口添加 Bearer Token。`SessionStore` 提供纯 Kotlin 会话存储边界，当前有 `InMemorySessionStore` 用于 JVM 测试，后续 Android 平台层可替换为 DataStore 或加密 SharedPreferences。`AppStateController` 以 `StateFlow<AppUiState>` 暴露登录、启动恢复、登出、首页、搜索、详情、播放、观看上报、积分、观看记录和订单状态，后续 Compose UI 只负责展示状态和触发动作。

当前机器未配置 Android SDK，因此本阶段只执行 `app-core` 的 JVM 单元测试，不声明 Android UI 模块编译通过。

## 可运行验证

```powershell
cd android-app
.\gradlew.bat :app-core:test --no-daemon
```

## 后续开发环境

- 安装 Android Studio。
- 安装 Android SDK 和 Android SDK Build-Tools。
- 在 `local.properties` 中配置 `sdk.dir`，或由 Android Studio 自动生成。

