# ShortLink Android 唯一正式签名

> **ReelShort 的所有 APK 变体（Debug、Release）都必须使用原 ShortLink 正式 keystore。项目不再使用 Debug 证书。**

## 正式签名材料

- 主 Keystore：`C:\Users\Ashu\Desktop\ReelShort\android-app\release-signing\shortlink-release.jks`
- 主配置：`C:\Users\Ashu\Desktop\ReelShort\android-app\release-signing\signing-config.txt`
- 桌面备份：`C:\Users\Ashu\Desktop\ShortLink-Release-Signing-Backup`
- 包名：`com.reelshort.app`
- 正式证书 SHA-256：`c1a149d5352c5c313d8d7d995470da31a3bd298281abba914e559747bb45c2f9`
- 发布脚本：`scripts/publish-android.py`

两份签名材料均只保存在本机，并通过 Git 忽略规则排除。`signing-config.txt` 包含敏感配置，禁止输出内容、截图、提交 Git 或发送到聊天中。换机器或重装系统前必须迁移同一份 keystore 和配置。

## 构建规则

- Debug 和 Release 变体都从 `ANDROID_SIGNING_*` 环境变量读取同一份正式签名；`Debug` 仅表示可调试构建属性，不代表 Debug 证书。
- ReelShort 不读取、不创建也不依赖 `C:\Users\Ashu\.android\debug.keystore`。该文件如被其他 Android 项目使用，保留在用户目录中，不属于本项目签名材料。
- Debug 与 Release 的 APK/AAB 打包任务会在执行前校验 keystore、alias 和正式证书 SHA-256；配置缺失或证书不匹配时直接失败。
- 发布脚本**不再校验 APK 签名**：上传前用 `aapt` 校验包名、versionName 和 versionCode，并核对 APK 的 SHA-256 与文件大小（上传后再回核 COS 对象大小）。
- 正式 keystore、配置缺失或证书不匹配时 Gradle 打包任务直接失败，发布流程随之停止，不存在"跳过签名继续发布"的路径。

## 发布命令

```powershell
python scripts/publish-android.py --version X.Y.Z --version-code N --notes ""
```

## 签名切换影响

- 正式签名 `0.8.6 (40)`、`0.8.8 (42)` 及后续版本可以原地覆盖升级。
- 历史 Debug 签名 `0.8.7 (41)` 无法覆盖安装正式签名版本，仍需卸载后重新安装；卸载只清除本地会话和缓存，服务器账号、积分和订单数据不受影响。
- 从 `0.8.8 (42)` 起，所有 Debug、Release 和线上版本都必须持续使用上述正式 keystore。
