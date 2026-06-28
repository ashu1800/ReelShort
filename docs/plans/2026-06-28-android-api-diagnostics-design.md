# Android API 连接诊断设计

## 背景

Android App 已经能通过 `OkHttpReelShortApiClient` 访问 Spring Boot App API，雷电模拟器也已经可用于安装和启动验证。下一步要做真实数据联调时，最常见的问题不是业务代码，而是 App 当前连到哪个后端地址、模拟器能不能访问本机 Spring Boot、后端是否启动。

本阶段不扩展后端业务能力，只在 App 侧增加轻量连接诊断，让账户页能展示当前 API 地址并手动检查公开的 `/api/system/health`。

## 目标

- App 展示当前 Spring Boot App API base URL。
- App 从 `http://.../api/app` 推导公开健康检查地址 `http://.../api/system/health`。
- `OkHttpReelShortApiClient` 增加未鉴权健康检查能力。
- `AppStateController` 保存诊断状态，账户页提供手动刷新入口。
- 雷电模拟器联调时可以快速判断“App 是否连上后端”。

## 非目标

- 不新增后端接口。
- 不直连 Flask 内容源。
- 不做自动轮询、网络质量测速或复杂诊断树。
- 不把诊断功能做成独立设置系统。

## 设计

`ApiConfig` 继续持有 App API base URL，并新增 `systemHealthUrl`。当 base URL 以 `/api/app` 结尾时，将其替换为 `/api/system/health`；否则在当前 base 后追加 `/system/health` 作为保守兜底。

`ReelShortApiClient` 新增 `checkSystemHealth()`，返回 `ApiHealthStatus`。OkHttp 实现直接请求 `config.systemHealthUrl`，不携带 Bearer Token，并解析后端统一响应中的 `status` 字段。Fake client 返回固定 `UP`，用于无后端环境下的 UI 状态测试。

`AppDataSource`、`AppRepository`、`AppStateController` 增加对应边界。`AppUiState` 保存 `apiBaseUrl` 和 `apiHealthStatus`，账户页展示“开发连接”面板：API 地址、健康状态、最近错误或状态说明、刷新按钮。

## 验收

- `ApiConfig` 能从默认 `http://10.0.2.2:8080/api/app` 推导 `http://10.0.2.2:8080/api/system/health`。
- OkHttp 客户端健康检查不发送 Authorization header。
- 状态控制器能刷新健康状态并写入 UI state。
- 账户页能看到当前 API 地址和刷新入口。
- `:app-core:test`、`:app:testDebugUnitTest`、`:app:assembleDebug` 通过。
- APK 能安装并在雷电模拟器启动，无关键崩溃日志。
