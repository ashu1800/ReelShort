# ShortLink Android 发布（对象存储方式）

本文档描述 ShortLink Android 客户端通过腾讯云 COS 对象存储进行自动更新的发布流程。
旧的 GitHub Actions + SSH 静态发布链路已废弃。

## 架构

```
本地 publish-android.py
  → 手动输入版本号 → gradlew assembleRelease 签名打包
  → 计算 sha256 / size → 上传 APK + sha256 到腾讯 COS
  → POST 发布元数据到后端 /api/internal/release/publish（X-Internal-Super-Token 保护）
  → 后端 upsert app_releases 表

App 启动
  → GET /api/app/release/latest（游客可访问）
  → 后端查最新发布 → 用 COS Java SDK 生成短时预签名下载链接（默认 1 小时）
  → 返回 manifest（apkUrl / sha256Url 指向预签名链接）
  → App 下载 + SHA-256 校验 + 签名校验 + 系统安装器安装

App 0.4.x 仍访问旧路径 `GET /api/app/update/latest`。后端对该路径返回
`https://shortlink.hjj888.cc/downloads/android/ShortLink-vX.Y.Z.apk[.sha256]`
一类稳定 URL，Compose Nginx 将 `/downloads/android/` 转发到后端，后端再 302 到短时 COS 预签名链接。
```

密钥只在服务端，COS 下载链接短时过期，不暴露原始密钥。

## 前置准备

### 1. 本地签名材料

发布脚本需要正式签名材料。设置以下环境变量（或在 `.env` 中配置）：

- `ANDROID_SIGNING_STORE_FILE` — 签名密钥库文件路径（`.jks`）
- `ANDROID_SIGNING_STORE_PASSWORD`
- `ANDROID_SIGNING_KEY_ALIAS`
- `ANDROID_SIGNING_KEY_PASSWORD`

密钥库文件本身被 `.gitignore` 忽略，不入库。

### 2. 发布配置

复制 `.env.example` 为 `.env`，填入：

- `COS_SECRET_ID` / `COS_SECRET_KEY` — 腾讯云 COS 密钥
- `COS_BUCKET` — 存储桶名（如 `update-1259596907`）
- `COS_REGION` — 地域（如 `ap-chengdu`）
- `RELEASE_BACKEND_URL` — 后端地址（如 `https://shortlink.hjj888.cc`）
- `REELSHORT_INTERNAL_SUPER_TOKEN` — 与后端 `REELSHORT_INTERNAL_SUPER_TOKEN` 一致的超级 Token

`.env` 被 `.gitignore` 忽略，切勿提交。

### 3. 后端 COS 配置

后端部署环境需注入以下环境变量（对应 `reelshort.release.*` 属性）：

- `REELSHORT_COS_SECRET_ID`
- `REELSHORT_COS_SECRET_KEY`
- `REELSHORT_COS_REGION`（默认 `ap-chengdu`）
- `REELSHORT_COS_BUCKET`
- `REELSHORT_RELEASE_PRESIGN_TTL`（默认 `1h`）

## 发布步骤

在工作区根目录运行：

```bash
python scripts/publish-android.py
```

交互式提示输入版本号（`X.Y.Z`）、version code 和 release notes。脚本会自动：

1. 用 Gradle 构建签名 release APK
2. 用 `apksigner` / `aapt` 校验签名、正式证书 SHA-256、包名（`com.reelshort.app`）、versionName 和 versionCode
3. 计算 APK 的 SHA-256 和文件大小，生成 `.sha256` 校验文件
4. 自动安装 `cos-python-sdk-v5`（如未安装）
5. 上传 APK 和校验文件到 COS（覆盖同版本号的旧文件）
6. POST 发布元数据到后端，后端写入 `app_releases` 表

也可用参数跳过交互：

```bash
python scripts/publish-android.py \
  --version 0.4.2 \
  --version-code 6 \
  --notes "修复若干问题"
```

参数：

- `--version` — 版本号 `X.Y.Z`
- `--version-code` — 正整数 version code（省略时提示，默认建议为当前最新 + 1）
- `--notes` — release notes 文本
- `--mandatory` — 标记为强制更新（当前 App 未强制执行该标记，仅记录）
- `--minimum-version-code` — 可安装此更新的最低 App version code（默认 1）

脚本任何一步失败都会明确报错并退出，不会产生半成品发布。

## 发布后验证

```bash
# 检查后端返回的最新版本
curl -s https://shortlink.hjj888.cc/api/app/release/latest | python -m json.tool
```

在 App 的「我的」页面手动检查更新，或在 App 冷启动时自动检查。

## 同版本重新发布

对同一个 `versionName` 再次运行脚本会覆盖 COS 上同名的 APK 文件，并 upsert 后端的发布记录
（更新 `apk_object_key` / `sha256` / `published_at` 等字段，保留原 `id`）。

## 安全边界

- COS SecretId/SecretKey 只在服务端环境变量，不入库、不写 `.env.example` 明文。
- 预签名下载链接短时（默认 1h）过期，按需生成，不持久化。
- 发布接口由 `X-Internal-Super-Token` 保护，复用 `reelshort.internal.super-token`。
- `gitleaks` 扫描全历史（push 与 PR），确保无密钥泄漏。
- 发布脚本钉住当前正式签名证书 SHA-256；如果本地环境变量指向了错误 keystore，发布会在上传前失败。
