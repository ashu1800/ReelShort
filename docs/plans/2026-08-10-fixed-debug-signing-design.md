# Android 固定 Debug 签名设计

## 目标

ShortLink 后续所有 Android 发布统一使用本机固定 Debug keystore 的证书签名，避免当前已安装 Debug 签名版本在更新时因证书不一致而失败。正式签名材料全部删除，不再作为发布路径。

## 方案

- 固定 keystore：`C:\Users\Ashu\.android\debug.keystore`
- 固定证书 SHA-256：`7bb921b325b079400c9fa4fc322e6a903571f688f35ce6ab284baf32d0cca436`
- Gradle 仍构建 `release` 变体，保留压缩、资源收缩和非 debuggable 属性，但 `release` 变体使用固定 Debug keystore 签名。
- 发布脚本继续校验包名、版本号、versionCode、APK 签名、SHA-256 和文件大小，并将证书钉死值改为上述 Debug 证书。
- 删除仓库工作区和用户目录中的正式 keystore、正式签名配置及正式密钥备份。
- 在 `android-app/SIGNING.md`、Android README、发布文档、发布清单和 `AGENTS.md` 中明确固定 Debug 签名规则及迁移影响。

## 兼容性

- 使用相同 Debug 证书的 `0.8.5 (39)` 可直接覆盖升级到后续更高 versionCode。
- 已安装正式证书签名的 `0.8.6 (40)` 无法直接切换回 Debug 证书，必须卸载一次；之后可连续覆盖升级。
- 切换模型不会影响签名，前提是继续使用文档指定路径中的同一 keystore。换机器前必须安全迁移这份固定 Debug keystore，不能让 Gradle自动生成新证书。

## 验证

- 发布脚本测试先验证固定 Debug 证书允许、其他证书拒绝。
- 构建更高 versionCode 的 release APK，使用 `apksigner` 核对固定 Debug 证书和 `aapt` 核对包名/版本。
- 在当前安装 `0.8.5 (39)` Debug 证书的模拟器执行覆盖安装，验证不再出现 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`。
- 检查正式签名材料路径均已删除，且仓库文档不再要求 `ANDROID_SIGNING_*`。
