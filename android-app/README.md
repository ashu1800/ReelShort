# ReelShort Android App

Android 原生客户端骨架，规划使用 Kotlin、Jetpack Compose 和 Android 原生媒体播放能力。

当前已建立阶段 1 的核心页面边界和 API 接入边界：

- 登录页：本地模拟账号密码状态，后续替换为 Spring Boot 登录接口。
- 首页：推荐内容列表占位，后续对接 Spring Boot 首页货架接口。
- 搜索页：本地搜索占位，后续对接 Spring Boot 搜索接口。
- 详情页：剧集信息和分集列表占位，后续对接 Spring Boot 剧集详情接口。
- 播放页：HLS 播放器区域占位，后续接入 Android 原生媒体播放方案。
- 观看记录、积分、订单页：保留后续核心闭环和商业化接口边界。

## 模块结构

- `app`：Android Compose UI 模块，负责页面骨架和本地交互。
- `app-core`：纯 Kotlin JVM 核心模块，包含 Spring Boot API 配置、统一响应模型、App 数据模型、`ReelShortApiClient` 边界、`FakeReelShortApiClient` 和 `AppRepository`。

App 后续只访问 Spring Boot API，不直接访问 Flask 内容源服务。当前 `FakeReelShortApiClient` 只用于无 Android SDK 环境下的结构验证和 UI 占位，真实网络实现会在后续替换到同一个 `ReelShortApiClient` 接口下。

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

