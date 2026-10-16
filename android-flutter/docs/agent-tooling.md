# Flutter 客户端 UI 改版的 Agent 工具链

本文档记录 UI 重设计（除 Home 外全部界面）期间使用的 MCP、技能与真机验证流程，供后续会话复用。
`cwd` 约定：除非特别说明，命令都在 `android-flutter/` 下执行。

## 1. 官方 Dart / Flutter MCP（推荐，项目级）

官方 MCP 服务器随 Dart SDK 提供（本机 Dart 3.13.5，`dart mcp-server --help` 已验证可用），
提供 `analyze_files`、`hot_reload`、`hot_restart`、`widget_inspector`、`get_runtime_errors`、
`run_tests`、`dart_format`、`list_devices`、`flutter_driver_command`、`vm_service`。

**仓库内配置**：仓库根 `.mcp.json`（通用 `mcpServers` 格式，供其他 agent/IDE 复用）。

**DSH 侧注册（一次性）**：在 MCP 面板新增一条 stdio 服务器，填写：

| 字段 | 值 |
| --- | --- |
| 名称 | `dart` |
| 传输 | `stdio` |
| 命令 | `C:\Users\12801\flutter\3.47.7\bin\dart.bat`（或 PATH 上的 `dart`） |
| 参数 | `mcp-server --enable flutter_app_lifecycle,flutter_driver` |
| 工作目录 | `D:\Sync\AICoding\ReelShort\android-flutter` |

注册后**必须重新开始会话**：MCP 工具目录在会话启动时固定，当前会话不会出现新工具。

**校验**：新会话中确认存在 `dart` 工具组（至少 `analyze_files` / `hot_reload` /
`widget_inspector` / `list_devices`），并调用 `list_devices` 应能看到真机 `c4e4da7e`。

## 2. 无 MCP 时的兜底工作流（本会话默认）

```powershell
# 静态检查与单测
& "C:\Users\12801\flutter\3.47.7\bin\flutter.bat" analyze
& "C:\Users\12801\flutter\3.47.7\bin\flutter.bat" test

# 真机热调试（--print-dtd 便于后续接 DTD/VM Service）
& "C:\Users\12801\flutter\3.47.7\bin\flutter.bat" run -d c4e4da7e --print-dtd

# 截图 / 无障碍树
adb exec-out screencap -p > shot.png
adb shell uiautomator dump /sdcard/ui.xml; adb pull /sdcard/ui.xml .
```

构建 debug APK 需要正式签名环境变量（Gradle 打包前 fail-closed 校验钉死证书）。
本地值在 gitignored 的 `android-app/release-signing/signing-config.txt`：

```powershell
$signingDir = "..\android-app\release-signing"
Get-Content "$signingDir\signing-config.txt" | ForEach-Object {
    if ($_ -match '^\s*(ANDROID_SIGNING_[A-Z_]+)\s*=\s*(.+)$') {
        Set-Item -Path "env:$($Matches[1])" -Value $Matches[2].Trim('"').Trim("'")
    }
}
# signing-config.txt 不含 STORE_FILE，需要按 keystore 路径补上（发布脚本也是这么注入的）
$env:ANDROID_SIGNING_STORE_FILE = (Resolve-Path "$signingDir\shortlink-release.jks").Path
& "C:\Users\12801\flutter\3.47.7\bin\flutter.bat" build apk --debug
```

产出的 debug APK 版本取自 `pubspec.yaml`（当前 `0.9.2+44`）。设备上若已装更高 versionCode 的
正式包，`adb install -r` 会被 `INSTALL_FAILED_VERSION_DOWNGRADE` 拒绝，可用
`-AllowDowngrade`（脚本内部传 `adb install -r -d`）。

## 3. UI 截图复核脚本

```powershell
# 亮色 + 常规字号基线
powershell -ExecutionPolicy Bypass -File scripts/android-ui-review.ps1

# 暗色 + 1.3 倍字号
powershell -ExecutionPolicy Bypass -File scripts/android-ui-review.ps1 -Mode dark -FontScale 1.3

# 不重装（已装同版本时）
powershell -ExecutionPolicy Bypass -File scripts/android-ui-review.ps1 -SkipInstall
```

产物落在 `artifacts/ui-review/<时间戳>-<模式>-<字号>/`（`/artifacts/` 已被 gitignore）：
每个界面一张 PNG 加一份 `manifest.json`。默认 APK 路径为
`android-flutter/build/app/outputs/flutter-apk/app-debug.apk`，用 `-ApkPath` 覆盖。

**已知限制**：Flutter 界面在 `uiautomator dump` 里通常只有一个渲染面，脚本因此以
"比例点击 + 截图"为主，不依赖文本断言；登录态界面（资金余额、提现表单、VIP 待支付订单、
账户钱包/历史）需要测试账号，由人工在设备上登录后补拍。

## 4. 使用的技能（已随 flutter/agent-plugins 与 dart-lang/skills 安装）

| 阶段 | 技能 |
| --- | --- |
| 视觉方向与打磨 | `frontend-design`、`frontend-ui-engineering` |
| 布局/溢出/响应式 | `flutter-fix-layout-issues`、`flutter-build-responsive-layout` |
| 组件预览与回归 | `flutter-add-widget-preview`、`flutter-add-widget-test` |
| 真机端到端 | `flutter-add-integration-test`、`dart-run-static-analysis` |
| 收尾评审 | `code-review-and-quality`、`code-simplification`、`doubt-driven-development` |

## 5. 评估后不采用的工具（记录理由）

- `Arenukvern/mcp_flutter`（flutter-mcp-toolkit）：需要把客户端库注入 App 才能拿到可视化快照，
  给发布产物引入额外依赖面，收益与"adb 截图 + integration_test"重叠。
- `infiniV/Android-Ui-MCP`：同类方案，偏向 React Native/原生。
- 社区设计技能（`emilkowalski-skills`、`sleekdotdesign-agent-skills`、
  `nextlevelbuilder-ui-ux-pro-max-skill` 等）：第三方内容，使用前必须 vendor 进仓库并逐份审阅
  `SKILL.md`（供应链风险）；本次不使用。
