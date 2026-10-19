# ShortLink Flutter 工具链

本文件说明 `android-flutter/` 的构建环境、常用命令与已知约束。应用本身是 `android-app` 的 Flutter 重构版，功能对齐但不是 UI 复刻。

## 版本矩阵

| 组件 | 版本 | 说明 |
| --- | --- | --- |
| Flutter | `3.47.7` stable（framework `abaf9c5237`） | 安装于 `C:\Users\12801\flutter\3.47.7` |
| Dart | `3.13.5` | 随 Flutter 分发 |
| DevTools | `2.60.0` | 随 Flutter 分发 |
| JDK | Temurin `17.0.20.1+1` | `JAVA_HOME=C:\Users\12801\jdk-17\jdk-17.0.20.1+1` |
| Gradle | `9.3.1`（wrapper） | 首次构建会下载发行包 |
| Android Gradle Plugin | `9.1.0` | `android/settings.gradle.kts` |
| Kotlin (Gradle 插件) | `2.4.0` | 仅用于宿主 `MainActivity`，业务逻辑全在 Dart |
| Android SDK Platform | `android-36` | `compileSdk = 36` |
| Android Build-Tools | `36.0.0` | |
| `minSdk` / `targetSdk` | `26` / `35` | 与 `android-app` 一致 |
| `applicationId` | `com.reelshort.app` | 与已发布应用一致，可覆盖升级 |
| versionName / versionCode | `0.9.4` / `46` | 由 `pubspec.yaml` 的 `version:` 决定 |

Flutter SDK 的 `bin` 目录已写入**用户级** `PATH`；已经打开的终端需要重启才会生效，因此本文命令都直接调用绝对路径。

## 常用命令

所有命令在 `android-flutter/` 目录下运行，下面用 `$FLUTTER` 表示 `C:\Users\12801\flutter\3.47.7\bin\flutter.bat`。

```powershell
# 解析依赖
& $FLUTTER pub get

# 静态分析（必须输出 "No issues found!"，info 级也会导致非 0 退出）
& $FLUTTER analyze

# 单元测试与 Widget 测试
& $FLUTTER test

# 单个测试文件
& $FLUTTER test test/core/generated_contract_test.dart

# 契约数据重新生成（AppStrings / AppUiState）
python tool/port_app_strings.py
python tool/port_app_ui_state.py

# 构建 Debug APK（需要正式签名环境变量，见 docs/signing.md）
& $FLUTTER build apk --debug

# 构建优化 Release APK
& $FLUTTER build apk --release

# 安装到已连接设备并启动
& $FLUTTER install --release
```

### 发布相关入口

发布已切换到 Flutter 应用（Kotlin 版进入维护淘汰）：`scripts/publish-android.py` 默认构建 `android-flutter/`（`--source kotlin` 可回退旧链路），flutter CLI 按 FLUTTER_BIN > PATH > `C:\Users\12801\flutter\3.47.7\bin` 发现；

- 后端 `GET /api/app/release/latest`（游客可访问）返回版本清单与 COS 预签名下载地址。
- 打包需要一个 versionCode 高于线上 `0.9.3 (45)` 的 APK：`pubspec.yaml` 已设为 `0.9.4+46`。
- 正式 APK 必须使用 `android-app/release-signing/shortlink-release.jks` 的原正式证书签名，详见 [signing.md](signing.md)。

## 环境约束与已知问题

1. **Gradle 依赖源**：本机 `maven.google.com` 不稳定（`flutter doctor` 报信号灯超时）。`android/settings.gradle.kts` 与 `android/build.gradle.kts` 已把阿里云镜像排在 `google()` / `mavenCentral()` **之前**作为首选源，上游仓库保留为兜底，因此在其他网络环境下同样可用。
2. **CI 不打包 APK**：`.github/workflows/ci.yml` 的 `android-flutter` job 只跑 `pub get` / `analyze` / `test`。原因见下方签名约束——任何 `assemble*` / `bundle*` / `package*` 任务都会校验正式证书，缺少 `ANDROID_SIGNING_*` 时构建**故意失败**，CI 不持有签名密钥。
3. **无 NDK**：宿主 `build.gradle.kts` 保留 `ndkVersion = flutter.ndkVersion`，但本项目不含需要本地编译的代码，AGP 不会请求 NDK。
4. **无字体资源**：设计系统使用平台默认字体族，通过字重、字距、大小与 `FontFeature.tabularFigures()` 区分显示/正文/数字三类角色，不下载也不打包字体二进制。
5. **`flutter analyze` 的严格度**：`analysis_options.yaml` 启用 `strict-casts`、`strict-raw-types`，并把 `dead_code` / `unused_element` / `unused_import` / `unused_local_variable` 提升为 **error**。分析必须零问题，包括 info。

## 设备验证

本机可用设备（`flutter devices`）：

- `emulator-5554` — Android 14（API 34），满足 `minSdk 26`。

端到端验证顺序：

```powershell
# 1. 构建 Release APK（需要签名环境变量）
& $FLUTTER build apk --release

# 2. 校验签名证书确实是钉死的正式证书
& "$env:LOCALAPPDATA\Android\Sdk\build-tools\36.0.0\apksigner.bat" verify --print-certs `
  build\app\outputs\flutter-apk\app-release.apk

# 3. 安装并启动
& $FLUTTER install --release
adb -s emulator-5554 shell am start -n com.reelshort.app/.MainActivity

# 4. 抓取日志
adb -s emulator-5554 logcat -d -t 200
```

`apksigner verify --print-certs` 输出的证书 SHA-256 必须等于
`c1a149d5352c5c313d8d7d995470da31a3bd298281abba914e559747bb45c2f9`。

## 与 `android-app` 的关系

- `android-app/` 是**只读参考实现**，承载功能契约与既有测试；本次重构不修改它。
- 逻辑层（网络、模型、存储边界、状态机、格式化契约）按 Kotlin 逐行对齐移植；表现层（`ui/`）完全重写。
- 无法 1:1 对齐的差异统一记录在 [parity-matrix.md](parity-matrix.md)。
