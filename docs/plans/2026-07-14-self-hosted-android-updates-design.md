# ShortLink 自托管 Android 更新设计

## 目标

ShortLink App 不再访问 GitHub API 或 GitHub Release 下载资产。版本清单和正式 APK 统一由 `shortlink.hjj888.cc` 提供，GitHub Actions 只承担使用现有正式签名密钥构建 APK 并通过 SSH 上传服务器的工作。

## 架构

- 版本清单：`GET https://shortlink.hjj888.cc/api/app/update/latest`
- APK 下载：`GET https://shortlink.hjj888.cc/downloads/android/ShortLink-vX.Y.Z.apk`
- 摘要下载：`GET https://shortlink.hjj888.cc/downloads/android/ShortLink-vX.Y.Z.apk.sha256`
- 服务器目录：`/opt/reelshort/releases/android`
- Docker Nginx 只读挂载该目录并直接提供清单和下载文件。
- App 继续执行 SHA-256、包名、递增 `versionCode` 和正式签名证书校验，最终安装仍交给 Android 系统安装器确认。

## 下载治理

- 每个客户端 IP 同时只允许一个下载连接。
- 每个响应前 2 MiB 不限速，之后限制为 1 MiB/s。
- 保留 HTTP Range 和 `Accept-Ranges`，支持断点续传。
- 只允许 GET/HEAD，不提供目录索引和上传能力。
- `latest.json` 使用 `no-store`，APK 使用不可变长缓存。

## 发布流程

1. `vX.Y.Z` 标签或手动指定标签触发快速 Release 工作流。
2. 工作流校验标签与 Android `versionName` 一致。
3. 从 GitHub Secrets 恢复现有正式 keystore 并构建 Release APK。
4. 使用 `apksigner` 和 `aapt` 校验签名、包名和版本。
5. 生成 APK、SHA-256 和 `latest.json`。
6. 使用专用无 sudo 的 `shortlink-release` SSH 用户上传 `.part` 临时文件。
7. 服务器端在同一目录原子重命名，最后替换 `latest.json`，保证客户端不会看到半包。

Release 工作流不运行单元测试、Lint 或 Android 模拟器测试。

## 安全边界

- 正式 keystore 继续只存放在 GitHub Secrets，不写入仓库或服务器。
- GitHub 使用独立 Ed25519 部署私钥，服务器公钥只授权给 `shortlink-release` 用户。
- `shortlink-release` 无 sudo，只拥有 `/opt/reelshort/releases/android` 写权限。
- GitHub Actions 固定服务器 SSH host key，拒绝未知主机或中间人替换。
- App 拒绝非 HTTPS、自有域名之外的清单和资产 URL。

## 运维与回滚

- 已安装的 Android 版本不能降级；回滚只影响尚未更新的客户端。
- 已发布错误版本需要使用更高 `versionCode` 发布修复版本。
- 历史 APK 可保留用于审计，`latest.json` 永远最后切换。
- 初期使用现有服务器带宽；用户量增加后可保持接口不变，将下载 URL 切换到对象存储。
