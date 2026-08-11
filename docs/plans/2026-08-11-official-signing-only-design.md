# Android 全变体统一正式签名设计

## 背景

ShortLink 已恢复原正式 keystore，但当前 Gradle 仍为 `Debug` 变体显式配置本机 `debug.keystore`，只有 `Release` 变体使用正式证书。这会允许项目继续产出 Debug 证书 APK，与“以后所有打包签名只使用正式版签名”的要求冲突。

## 目标

- 保留 `Debug` 和 `Release` 两个构建变体。
- 两个变体产出的 APK 都只使用同一个 ShortLink 正式证书。
- 正式签名配置缺失、不完整或证书不匹配时停止构建或发布。
- 项目源码、当前文档和上下文不再引用 `fixedDebug`、`.android/debug.keystore` 或 Debug 证书。
- 正式签名密码和 keystore 继续只保存在 Git 忽略的本机位置。

## 构建设计

Gradle 只保留一个环境变量驱动的正式签名配置。`Debug` 和 `Release` 构建类型都绑定该配置：`Debug` 仍保持 Android 的可调试属性，`Release` 继续启用代码压缩和资源收缩，但证书完全一致。

所有 APK 打包任务在执行前检查 `ANDROID_SIGNING_STORE_FILE`、`ANDROID_SIGNING_STORE_PASSWORD`、`ANDROID_SIGNING_KEY_ALIAS` 和 `ANDROID_SIGNING_KEY_PASSWORD`。缺少任何一项时直接失败，避免 Android Gradle Plugin 回退到自动生成的 Debug 证书或产出未签名 APK。

用户目录下可能被其他 Android 项目使用的全局 `.android/debug.keystore` 不物理删除；ReelShort 源码和脚本彻底停止引用它。

## 发布与安全边界

`scripts/publish-android.py` 继续从 Git 忽略的 `android-app/release-signing/signing-config.txt` 加载正式签名环境变量，并钉死证书 SHA-256：

`c1a149d5352c5c313d8d7d995470da31a3bd298281abba914e559747bb45c2f9`

发布脚本只上传优化后的 `Release` APK。Debug APK 仅用于开发和模拟器，但即使手工打包也必须使用同一正式证书。任何新的或替换后的 keystore 都会在发布校验阶段被拒绝。

## 测试设计

- 先更新发布契约测试，要求 Gradle 不包含 `fixedDebug` 或 `.android/debug.keystore`，并要求 `Debug`、`Release` 都绑定唯一正式签名配置。
- 先运行测试确认它因现有 `fixedDebug` 配置而失败，再修改 Gradle 使其通过。
- 运行发布脚本单元测试、Android JVM 单元测试、Lint 和 `git diff --check`。
- 分别构建 Debug 与 Release APK，使用 `apksigner` 验证两者证书 SHA-256 均为固定正式证书，并使用 `aapt` 复核包名和版本元数据。

## 文档与上下文

更新 `AGENTS.md`、`android-app/SIGNING.md`、`android-app/README.md` 和当前发布文档，醒目标注：ReelShort 所有 APK 变体只能使用原正式签名。历史设计文档保留当时事实，不作为当前发布规则。
