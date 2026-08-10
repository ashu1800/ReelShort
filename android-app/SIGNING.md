# ShortLink Android 固定签名

> **所有 APK 都使用下面这一个固定 Debug keystore。不要再使用或生成正式签名。**

## 唯一签名材料

- Keystore：`C:\Users\Ashu\.android\debug.keystore`
- 包名：`com.reelshort.app`
- Key alias：`androiddebugkey`
- 证书 SHA-256：`7bb921b325b079400c9fa4fc322e6a903571f688f35ce6ab284baf32d0cca436`
- 发布脚本：`scripts/publish-android.py`

Gradle 的 Debug 变体和发布用的优化 Release 变体都使用这份 keystore。发布脚本会在上传前钉死校验证书 SHA-256；文件缺失或证书不同会终止发布。

这份 keystore 不能由 Gradle重新生成。换机器或重装系统前必须安全迁移同一个文件，否则新 APK 无法覆盖旧版本。

## 发布命令

```powershell
python scripts/publish-android.py --version X.Y.Z --version-code N --notes ""
```

脚本仍构建 `assembleRelease`，因此 APK 保留代码压缩、资源收缩和非 debuggable 属性，只是签名证书固定为上述 Debug 证书。

## 证书切换影响

- Debug 证书签名的 `0.8.5 (39)` 可以直接覆盖升级到后续更高 versionCode。
- 正式证书签名的 `0.8.6 (40)` 必须卸载一次才能安装 Debug 证书版本；卸载会清除本地会话和缓存，服务器账号、积分和订单数据不受影响。
- 完成一次切换后，只要始终使用上述固定 keystore，后续版本可以正常覆盖升级。
