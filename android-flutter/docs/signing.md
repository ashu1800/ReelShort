# ShortLink Flutter 签名与发布

Flutter 重构版**沿用 `android-app` 的正式签名身份**，不使用 Debug 证书。这样线上已安装的 Kotlin 版应用可以被 Flutter 版原地覆盖升级。

## 固定身份

| 项 | 值 |
| --- | --- |
| 包名 / `applicationId` / `namespace` | `com.reelshort.app` |
| 正式证书 SHA-256 | `c1a149d5352c5c313d8d7d995470da31a3bd298281abba914e559747bb45c2f9` |
| Keystore | `android-app/release-signing/shortlink-release.jks` |
| Keystore 本地配置 | `android-app/release-signing/signing-config.txt`（gitignored） |

Keystore 与本地配置都已被 `.gitignore` 忽略，**不得**提交、打印、截图或粘贴到任何对话与日志中。Flutter 侧的 `.gitignore` 额外忽略了 `android/key.properties`、`android/local.properties`、`*.jks`、`*.keystore`、`*.p12`。

## 构建时注入

`android/app/build.gradle.kts` 从环境变量读取签名材料，**没有** `key.properties` 兜底，也**没有** Debug keystore 兜底：

| 环境变量 | 含义 |
| --- | --- |
| `ANDROID_SIGNING_STORE_FILE` | JKS 的绝对路径 |
| `ANDROID_SIGNING_STORE_PASSWORD` | keystore 口令 |
| `ANDROID_SIGNING_KEY_ALIAS` | 正式证书别名 |
| `ANDROID_SIGNING_KEY_PASSWORD` | 私钥口令 |

四个变量必须同时存在；任一为空即视为「未配置签名」。

`signing-config.txt` 只包含后三个键（不含 `STORE_FILE`），`STORE_FILE` 由调用方显式指向仓库内的 JKS。

### 一次性会话注入（不落盘、不回显）

```powershell
$cfg = @{}
Get-Content 'D:\Sync\AICoding\ReelShort\android-app\release-signing\signing-config.txt' | ForEach-Object {
  if ($_ -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$') {
    $cfg[$matches[1]] = $matches[2].Trim().Trim('"').Trim("'")
  }
}
$env:ANDROID_SIGNING_STORE_FILE    = 'D:\Sync\AICoding\ReelShort\android-app\release-signing\shortlink-release.jks'
$env:ANDROID_SIGNING_STORE_PASSWORD = $cfg['ANDROID_SIGNING_STORE_PASSWORD']
$env:ANDROID_SIGNING_KEY_ALIAS      = $cfg['ANDROID_SIGNING_KEY_ALIAS']
$env:ANDROID_SIGNING_KEY_PASSWORD   = $cfg['ANDROID_SIGNING_KEY_PASSWORD']

Set-Location 'D:\Sync\AICoding\ReelShort\android-flutter'
& 'C:\Users\12801\flutter\3.47.7\bin\flutter.bat' build apk --release
```

这些变量只在当前 PowerShell 进程内有效，不会被写入 `local.properties` 或任何项目文件。

## Fail-closed 校验

`android/app/build.gradle.kts` 在 `gradle.taskGraph.whenReady` 中，对任何名称匹配
`^(assemble|bundle|package)([A-Za-z0-9]+)?(Debug|Release)$` 的任务执行 `validateOfficialSigning()`：

1. 四个环境变量缺失 → `Official signing requires ANDROID_SIGNING_STORE_FILE, ANDROID_SIGNING_STORE_PASSWORD, ANDROID_SIGNING_KEY_ALIAS, and ANDROID_SIGNING_KEY_PASSWORD`
2. JKS 文件不存在 → `Official signing keystore was not found: <path>`
3. 别名不存在 → `Official signing certificate alias was not found in the configured keystore`
4. 证书 SHA-256 不等于钉死值 → `Configured signing certificate does not match the pinned ShortLink official certificate`
5. 其余失败 → `Official signing keystore validation failed.`

这意味着：

- 缺少签名材料时 **Debug 构建同样失败**。这是刻意设计——`Debug` 只表示可调试构建属性，不代表 Debug 证书。
- **CI 不执行任何打包任务**（见 `.github/workflows/ci.yml` 的 `android-flutter` job），只跑分析与测试。
- 未配置签名时无法产出可用于真机验证的 APK；真机验证需要在本机注入上表四个变量。

## 变体行为

| 变体 | 签名 | 优化 |
| --- | --- | --- |
| `debug` | `official`（配置存在时） | 不混淆 |
| `release` | `official`（配置存在时） | `isMinifyEnabled = true`、`isShrinkResources = true`、`proguard-rules.pro` |

`proguard-rules.pro` 保留 Flutter 插件注册面（`io.flutter.plugins.**`、`io.flutter.plugin.**`、`flutter_secure_storage`）以及反射所需的注解与泛型签名。

## 版本号

版本由 `pubspec.yaml` 的 `version: 0.9.6+48` 决定，`build.gradle.kts` 把它读成 `versionName` / `versionCode`。

`-PappVersionName=... -PappVersionCode=...` 可以覆盖它，方便发布脚本不改仓库：

```powershell
& $FLUTTER build apk --release --build-name=0.9.3 --build-number=45
```

`versionCode` 必须为正整数，否则构建直接失败。当前线上版本是 `0.9.5 (47)`，因此 `48` 起步才能覆盖升级。

打印当前版本：

```powershell
Set-Location android-flutter\android
.\gradlew.bat :app:printAppVersion
# 输出：0.9.2/44
```

## 构建产物校验

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\build-tools\36.0.0\apksigner.bat" verify --print-certs `
  android-flutter\build\app\outputs\flutter-apk\app-release.apk
```

输出的 `Signer #1 certificate SHA-256 digest` 必须等于固定证书 SHA-256。安装前还应确认：

- `versionCode` 高于设备上已安装版本；
- `aapt2 dump badging` 的 `package: name='com.reelshort.app'`。

## 安全边界

- 关闭 App 自动备份（`android:allowBackup="false"`）。
- 关闭全局明文流量（`android:usesCleartextTraffic="false"`）。
- 会话与「记住密码」凭据使用平台加密存储（`flutter_secure_storage`）；加密不可用时按 fail-closed 处理，只保留进程内会话，**绝不**降级写入明文 Token。
- 自更新只接受精确 COS 桶域名 `update-1259596907.cos.ap-chengdu.myqcloud.com` 的 HTTPS 地址，先核对文件大小与 SHA-256（不做 APK 签名校验，签名由打包前的钉死证书门禁保证）再交给系统安装器；安装通过 `authority=com.reelshort.app.update-files` 的 `FileProvider` 暴露 `cacheDir/updates/` 下的单个文件。

## 相关文档

- 构建环境与命令：[toolchain.md](toolchain.md)
- Kotlin 版发布流程与脚本：`android-app/SIGNING.md`、`docs/deploy/publish-android.md`
- 与 Kotlin 版的差异清单：[parity-matrix.md](parity-matrix.md)
