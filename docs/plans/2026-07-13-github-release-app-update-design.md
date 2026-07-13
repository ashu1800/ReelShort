# GitHub 开源与 Android Release 自动更新设计

## 目标

将现有 `ashu1800/ReelShort` 安全公开，并以 GitHub Releases 作为 ShortLink Android App 的稳定版更新源。App 在冷启动时静默检查，也允许用户从 Me 页主动检查；发现新版后在 App 内下载、展示进度并完成完整性验证，最后交给 Android 系统安装器确认安装。

## 架构

- `app-core` 负责 SemVer、GitHub Release 数据模型、稳定版查询和资产契约，保持纯 JVM 可测。
- Android `app` 负责下载生命周期、缓存文件、包信息与签名验证、未知来源授权、系统安装器和 Compose 更新界面。
- 更新状态独立于业务 `AppStateController`，由 `ReelShortViewModel` 暴露第二条 `StateFlow`，避免 Android 平台能力污染核心业务状态。
- Release 标签固定为 `vX.Y.Z`，资产固定为 `ShortLink-vX.Y.Z.apk` 和对应 `.sha256`。

## 安全与失败边界

- 仓库只有在当前树和完整历史敏感信息扫描通过后才能公开。
- APK 下载到私有缓存，限制为 250 MiB；取消、失败或超限删除临时文件。
- 安装前校验 SHA-256、包名、递增的 `versionCode` 和当前安装包签名证书。
- 自动检查失败保持静默，手动检查失败提供重试；任何校验失败都不得打开安装器。
- Android 不支持普通 App 静默安装，最终安装必须由用户确认。

## 发布

GitHub Actions 在 `v*` 标签上执行测试、Lint、正式签名构建、签名验证、摘要生成和稳定 Release 创建。正式 keystore 仅存在于 GitHub Secrets 和仓库外的 Windows DPAPI `CurrentUser` 加密备份中。
