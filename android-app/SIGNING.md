# ShortLink Android 固定签名

> **线上 Release APK 必须使用原 ShortLink 正式 keystore。禁止改用 Debug 证书或重新生成签名。**

## 正式签名材料

- 主 Keystore：`C:\Users\Ashu\Desktop\ReelShort\android-app\release-signing\shortlink-release.jks`
- 主配置：`C:\Users\Ashu\Desktop\ReelShort\android-app\release-signing\signing-config.txt`
- 桌面备份：`C:\Users\Ashu\Desktop\ShortLink-Release-Signing-Backup`
- 包名：`com.reelshort.app`
- 正式证书 SHA-256：`c1a149d5352c5c313d8d7d995470da31a3bd298281abba914e559747bb45c2f9`
- 发布脚本：`scripts/publish-android.py`

两份签名材料均只保存在本机，并通过 Git 忽略规则排除。`signing-config.txt` 包含敏感配置，禁止输出内容、截图、提交 Git 或发送到聊天中。换机器或重装系统前必须迁移同一份 keystore 和配置。

## 构建规则

- Debug 变体继续使用 `C:\Users\Ashu\.android\debug.keystore`，仅用于开发和模拟器测试。
- Release 变体从 `ANDROID_SIGNING_*` 环境变量读取正式签名；发布脚本会自动加载上述本地配置。
- 发布脚本在上传前用 `apksigner` 钉死校验正式证书 SHA-256，并校验包名、versionName 和 versionCode。
- 正式 keystore、配置缺失或证书不匹配时必须停止发布。

## 发布命令

```powershell
python scripts/publish-android.py --version X.Y.Z --version-code N --notes ""
```

## 签名切换影响

- 正式签名 `0.8.6 (40)` 与恢复后的正式签名新版本可以原地覆盖升级。
- Debug 签名 `0.8.7 (41)` 不能覆盖升级到正式签名新版本，用户需要卸载后重新安装；卸载会清除本地会话和缓存，服务器账号、积分和订单数据不受影响。
- 从恢复后的新版本开始，所有线上版本必须持续使用上述正式 keystore。
