# ReelShort

ReelShort 是一个长期单机部署的聚合播放平台，包含 Android 原生 App、Vue 后台管理网站、Spring Boot 模块化单体后端、Flask 内容源服务、PostgreSQL 和 Redis。

## 模块

- `backend`：Spring Boot 核心业务服务。
- `admin-web`：Vue + Element Plus 后台管理网站，包含用户、订单、内容缓存、系统配置、运行诊断和审计视图。
- `android-app`：Kotlin + Jetpack Compose Android 客户端骨架。
- `content-provider`：Flask 内容源适配服务。
- `infra`：单机部署、备份恢复和基础设施配置。
- `docs`：接口、部署、计划文档。

## 当前阶段

当前已进入阶段 1 核心闭环建设。后端已具备用户认证、内容聚合、观看记录、积分、订单、后台运营、RBAC、限流、数据库迁移和运行诊断基础能力；后台 Web 已覆盖核心运营视图；Android 仍在按原生 App 架构继续补齐真实播放体验。

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
