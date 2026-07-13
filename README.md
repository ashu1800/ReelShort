# ReelShort / ShortLink

ReelShort 是一个可单机部署的开源聚合播放平台，包含 Android 原生 App（ShortLink）、Vue 后台管理网站、Spring Boot 模块化单体后端、Flask 内容源服务、PostgreSQL 和 Redis。

> 本项目不提供或授权任何第三方视频、图片、商标和元数据。使用者必须自行确认内容来源、接口调用和部署行为符合所在地法律及第三方服务条款。

## 模块

- `backend`：Spring Boot 核心业务服务。
- `admin-web`：Vue + Element Plus 后台管理网站，包含用户、订单、内容缓存、系统配置、运行诊断和审计视图。
- `android-app`：Kotlin + Jetpack Compose Android 客户端骨架。
- `content-provider`：Flask 内容源适配服务。
- `infra`：单机部署、备份恢复和基础设施配置。
- `docs`：接口、部署、计划文档。

## 当前能力

后端已具备用户认证、内容聚合、观看记录、积分、订单、后台运营、RBAC、限流、数据库迁移和运行诊断能力；后台 Web 覆盖核心运营视图；Android App 已支持游客浏览、认证、播放、观看进度、账户和商业化基础流程。

## Android 发布

稳定版通过 [GitHub Releases](https://github.com/ashu1800/ReelShort/releases) 发布。App 会在冷启动时静默检查最新稳定版，也可在 Me 页手动检查；APK 下载完成并通过摘要、包名、版本和签名校验后，由 Android 系统安装器请求用户确认更新。

## App 本地联调

Windows + 雷电模拟器环境可使用 `app-dev` profile 直接联调 App：

```powershell
powershell -ExecutionPolicy Bypass -File infra/scripts/start-app-local-dev.ps1
```

如果 `8080` 已被占用，可指定本地后端端口：

```powershell
powershell -ExecutionPolicy Bypass -File infra/scripts/start-app-local-dev.ps1 -BackendPort 18080
```

详细说明见 `docs/deploy/app-local-dev.md`。

## 发布质量基线

发布前优先运行统一验证脚本：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/verify-release-baseline.ps1
```

Android 改动还必须安装最新 APK 到模拟器并完成手动验收：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/verify-release-baseline.ps1 -InstallApk
```

完整发布清单见 `docs/deploy/release-checklist.md`。

## 安全与许可证

- 安全问题请按 [SECURITY.md](SECURITY.md) 私密报告。
- 源代码采用 [Apache License 2.0](LICENSE)；第三方内容与商标不包含在授权范围内。
