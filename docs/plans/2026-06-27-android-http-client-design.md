# Android HTTP Client Design

## Goal

把 Android `app-core` 从“只有 fake client 的 API 边界”推进到“具备真实 Spring Boot HTTP 适配器”。本阶段仍不改 Compose UI 的本地状态，不要求 Android SDK 编译；重点是让后续 ViewModel/UI 接入真实 API 时只替换 `ReelShortApiClient` 实现，而不是重新设计协议层。

## Context

`app-core` 已有 `ApiConfig`、`ReelShortApiClient`、`FakeReelShortApiClient`、`AppRepository` 和 JVM 单元测试。后端已提供注册/登录、首页推荐、搜索/货架、剧集、播放地址、观看进度、积分账户/流水、订单列表等 App API。当前缺口是没有真实网络实现，也没有统一处理后端 `ApiResponse`、HTTP 错误和 Bearer Token 的客户端边界。

约束：本机没有 Android SDK，不能声明 Android UI 模块编译通过；本轮必须通过纯 JVM 测试验证。App 仍只访问 Spring Boot `/api/app/**`，不能直连 Flask 内容源。

## Options

推荐方案：在 `app-core` 内新增 `OkHttpReelShortApiClient`，使用 OkHttp + kotlinx.serialization。OkHttp 同时支持 Android 和 JVM，MockWebServer 可以覆盖请求路径、请求体、Authorization 头和错误映射。kotlinx.serialization 使用 `ignoreUnknownKeys` 解析后端统一响应，降低后端字段扩展对客户端的破坏。依赖版本选择兼容当前 Kotlin 2.0.21 的 OkHttp 4.12.x 和 kotlinx.serialization 1.7.x。

备选方案 1：引入 Retrofit。类型化程度更高，但需要更多 DTO/service 注解和依赖，当前 API 数量不大，先用薄 OkHttp adapter 更直接。

备选方案 2：继续只保留 fake client。最省事，但 Android 架构仍无法真正连到 Spring Boot，后续 UI 接入会一次性承担网络、解析和状态改造风险。

## Design

- Gradle：
  - `app-core` 引入 `org.jetbrains.kotlin.plugin.serialization`、`kotlinx-serialization-json`、`okhttp` 和测试用 `mockwebserver`。
- 网络错误：
  - 新增 `ApiClientException(statusCode, code, message)`。
  - 非 2xx、空响应体、后端 `code != 0` 都映射为该异常。
- DTO：
  - 在 `network/dto` 定义只属于网络层的 `@Serializable` 请求/响应 DTO。
  - DTO 转换为 `data` 包领域模型，避免领域模型绑死 JSON 字段。
- `OkHttpReelShortApiClient`：
  - 构造参数包含 `ApiConfig`、可注入 `OkHttpClient`、可注入 token provider。
  - 登录/注册不带 Authorization；其他 `/api/app/**` 业务接口从 token provider 获取 token 并添加 `Authorization: Bearer <token>`。
  - 路径只拼接到 `ApiConfig.baseUrl` 下的 Spring Boot App API，不包含 Flask/content-provider 地址。
- 订单：
  - 本阶段实现订单列表读取；创建订单接口属于后续 App 商业化 UI 切片。

## Testing

- 使用 MockWebServer 验证：
  - 登录 POST `/auth/login` 的 JSON 请求和响应解析。
  - 首页推荐 GET `/home/recommend` 解析为 `BookSummary`。
  - 搜索 query 参数正确编码。
  - 业务请求携带 Bearer Token。
  - 观看进度请求字段映射到后端契约。
  - 非 2xx 或后端 `code != 0` 抛出 `ApiClientException`。
- 保留全仓验证：`android-app :app-core:test`、后端测试、admin-web build、content-provider pytest、`git diff --check`。

## Out of Scope

- 不把 Compose UI 改为联网加载。
- 不引入 ViewModel、依赖注入或本地 token 持久化。
- 不接入真实 HLS 播放器。
- 不实现支付下单或订单创建 UI。
