# Android 正式签名恢复设计

## 目标

恢复删除前的 ShortLink 正式 Android 签名材料，并将线上 Release APK 重新固定为原正式证书签名。Debug 构建继续使用 Android 默认 Debug 签名。

## 方案

- 从 Windows 卷影副本恢复 `shortlink-release.jks` 和本地签名配置，主副本放在仓库内被 Git 忽略的 `android-app/release-signing/`，额外备份到桌面醒目目录。
- 发布脚本读取本地签名配置并向 Gradle 注入 `ANDROID_SIGNING_*` 环境变量，任何缺失配置都在构建前失败。
- Gradle 仅为 Release 变体配置正式签名；发布脚本使用 `apksigner` 钉死原正式证书 SHA-256，拒绝 Debug 证书或其他证书。
- keystore、密码和本地配置始终不进入 Git，不在命令输出、日志或文档中暴露。

## 发布与兼容性

下一版本使用 `0.8.8 (42)`，发布说明保持为空，`mandatory=false`。当前已安装 Debug 签名 `0.8.7 (41)` 的设备不能覆盖安装正式签名版本，需要卸载后重新安装；服务端账号、积分和订单数据不受卸载影响。

## 验证

- `keytool` 验证恢复 keystore 的证书 SHA-256 为 `c1a149d5352c5c313d8d7d995470da31a3bd298281abba914e559747bb45c2f9`。
- 发布脚本单元测试验证只接受正式证书并拒绝 Debug 证书。
- 构建 Release APK 后用 `apksigner` 和 `aapt` 验证证书、包名、版本号和 versionCode。
- 上传后核对 `/api/app/release/latest`，并重新下载 APK 验证文件 SHA-256 和签名证书。
