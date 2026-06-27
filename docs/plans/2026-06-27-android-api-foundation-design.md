# Android API Foundation Design

## Goal

把 Android App 从单文件本地模拟状态推进到可接入 Spring Boot API 的长期架构骨架。当前阶段不实现真实播放器、不要求 Android 编译通过；重点是建立 API client、DTO、Repository 和配置边界，使后续页面可以按模块逐步替换本地数据。

## Context

后端已经具备用户认证、内容搜索/剧集/播放地址、观看进度、积分、订单等 App API。Android 当前 `MainActivity.kt` 内同时包含页面状态、模拟数据和 UI，README 也明确所有页面仍是占位。这个结构适合最早期原型，但继续扩展会让网络协议、业务状态和 Compose UI 混在一起，后续接入真实接口时风险较高。

约束：本机没有 Android SDK，不能把“编译通过”作为本轮验收；本轮只能做文件级 Kotlin 结构、纯 JVM 单元测试可覆盖的逻辑和文档验证。App 仍必须只访问 Spring Boot API，不能直连 Flask 内容源。

## Options

推荐方案：先建立轻量 API foundation，不重构整套 UI。新增纯 Kotlin `app-core` 模块，并在模块内建立 `config`、`network`、`data` 包：`ApiConfig` 管理 base URL，`ApiResponse`/DTO 描述后端响应，`ReelShortApiClient` 定义 `suspend` 客户端边界，`FakeReelShortApiClient` 用于无 SDK 环境和后续 UI 预览，`AppRepository` 封装登录、首页、搜索、详情、播放地址、观看、积分、订单入口。`MainActivity` 暂时保留本地 UI，只把模型迁移到 `data` 包，避免一次性引入 ViewModel、DI 和播放器。

备选方案 1：直接引入 Retrofit/OkHttp 和 ViewModel，全面改造页面。长期更接近生产形态，但当前无 Android SDK 验证，范围过大，容易把编译风险和架构变更混在一起。

备选方案 2：只更新 README，不写代码。风险最低，但不能让后续功能沿稳定边界增量推进。

## Design

- `android-app/app-core/src/main/kotlin/com/reelshort/app/config/ApiConfig.kt`
  - 定义 Spring Boot API base URL，默认 `http://10.0.2.2:8080/api/app`。
  - 构造时去掉尾部 `/`，便于单元测试和后续 HTTP client 拼接。
- `android-app/app-core/src/main/kotlin/com/reelshort/app/network/ApiResponse.kt`
  - 定义与后端统一响应一致的泛型响应模型。
- `android-app/app-core/src/main/kotlin/com/reelshort/app/network/ReelShortApiClient.kt`
  - 定义 App 需要的接口：登录、注册、首页货架、搜索、剧集、播放地址、上报观看进度、观看记录、积分、订单。
  - 接口使用 `suspend` 函数，为后续真实 HTTP 和 ViewModel 协程调用预留非阻塞边界。
  - 当前不实现真实 HTTP；通过接口保证 UI 不感知 Flask 或具体网络库。
- `android-app/app-core/src/main/kotlin/com/reelshort/app/network/FakeReelShortApiClient.kt`
  - 使用样例数据实现接口，供当前 UI 和 JVM 测试使用。
- `android-app/app-core/src/main/kotlin/com/reelshort/app/data/AppModels.kt`
  - 放置 App 页面和 Repository 共用模型，先从 `MainActivity.kt` 迁出已有模型。
- `android-app/app-core/src/main/kotlin/com/reelshort/app/data/AppRepository.kt`
  - 封装 token、内容、观看、积分、订单等业务边界。
  - Repository 只依赖 `ReelShortApiClient`，对外暴露 `suspend` 方法，后续可替换为 Retrofit 实现。
- `MainActivity.kt`
  - 保留 Compose 页面和本地交互，改为使用 `data` 包模型，减少单文件职责。

## Testing

- 新增纯 Kotlin/JVM 测试：
  - `ApiConfigTest` 覆盖 base URL 规范化。
  - `AppRepositoryTest` 使用 coroutine test 覆盖登录后 token 保存、搜索委托、观看进度委托和积分/订单读取。
- 如果 Android SDK 不存在，本轮不运行 Android Gradle 编译。
- 保留全仓验证：后端测试、admin-web build、content-provider pytest、`git diff --check`。

## Out of Scope

- 不实现 Retrofit/OkHttp 真实网络请求。
- 不实现 ViewModel、导航框架、依赖注入。
- 不接入真实 HLS 播放器。
- 不声明 Android 编译通过。
