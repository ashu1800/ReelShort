# ReelShort / ShortLink

ReelShort 是一个可单机部署的开源聚合播放平台，包含 Android 原生 App（ShortLink）、Vue 后台管理网站、Spring Boot 模块化单体后端、Flask 内容源服务、PostgreSQL 和 Redis。

> 本项目不提供或授权任何第三方视频、图片、商标和元数据。使用者必须自行确认内容来源、接口调用和部署行为符合所在地法律及第三方服务条款。

## 模块

- `backend`：Spring Boot 模块化单体后端，提供 App 业务接口与后台管理接口。
- `admin-web`：Vue 3 + Element Plus 后台管理网站，包含控制台、用户运营、提现管理、VIP 订单、内容缓存、系统配置、运行诊断、异常告警、系统日志和审计日志视图。
- `android-app`：Kotlin + Jetpack Compose Android 客户端（用户可见品牌 `ShortLink`），含纯 Kotlin `app-core` 模块。
- `content-provider`：Flask 内容源适配服务，仅供后端同机内部调用。
- `infra`：单机部署、备份恢复和基础设施配置。
- `docs`：接口、部署、计划文档。

## 当前能力

后端已具备用户认证（用户名 + 密码 + 图形验证码）、内容聚合（英文单语 `en`）、观看记录、积分、提现人工确认、VIP 订单、后台运营、RBAC、限流、数据库迁移和运行诊断能力；后台 Web 覆盖核心运营视图；Android App 已支持游客浏览、认证、播放、观看进度、账户和商业化基础流程。

> 系统当前不提供短信验证码/手机号开户，不提供自动签名打款（`REELSHORT_WITHDRAWAL_PAYOUT_ENABLED` 默认 `false`），也不再提供 `zh-TW` 双语内容缓存。

## Android 发布

Release APK 由本地脚本 `python scripts/publish-android.py` 签名构建后上传腾讯云 COS，后端以 `app_releases` 表记录版本清单并在 `/api/app/release/latest` 返回短时预签名下载链接；App 在冷启动或 Me 页手动检查更新，APK 下载完成并通过 SHA-256 完整性校验后由 Android 系统安装器请求用户确认。旧的 GitHub Releases 与自托管静态目录链路均已删除。

详细流程见 `docs/deploy/publish-android.md`。

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

本机没有 `ANDROID_SIGNING_*` 签名密钥时，可只跑 JVM 单测、跳过 APK 打包：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/verify-release-baseline.ps1 -SkipAndroidPackage
```

脚本会检查未提交的已跟踪文件修改与未跟踪文件；在有意保留改动的本地工作树上验证时显式加 `-AllowDirtyTree`。

Android 改动还必须安装最新 APK 到模拟器并完成手动验收：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/verify-release-baseline.ps1 -InstallApk
```

完整发布清单见 `docs/deploy/release-checklist.md`。

## 安全与许可证

- 安全问题请按 [SECURITY.md](SECURITY.md) 私密报告。
- 源代码采用 [Apache License 2.0](LICENSE)；第三方内容与商标不包含在授权范围内。
