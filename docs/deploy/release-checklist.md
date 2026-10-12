# Release Quality Baseline Checklist

本文档是 ReelShort 发布前的固定验收入口。任何 Android、backend、content-provider、admin-web 或 infra 变更进入 `master` 前，都应按本清单完成对应验证。

## 1. 分支与提交基线

- 当前工作必须在独立分支或 worktree 中完成，不能直接在 `master` 上开发。
- 合并前确认 `master` 已包含所有目标功能分支，且没有未解释的大量未提交改动。
- 合并前执行：

```powershell
git status --short --branch
git fetch origin master
git log --oneline origin/master..HEAD
```

完成标准：

- 工作区干净，或只包含本次准备提交的改动。
- 本地提交信息能解释本次发布包含的功能和修复。
- 若本地落后远程，先 `git pull --rebase` 并解决冲突。

## 2. 一键发布验证

推送到 `master`/`main` 或发起 PR 时，`.github/workflows/ci.yml` 会自动执行后端 Gradle 测试、content-provider pytest、admin-web 构建 + 契约测试、Android JVM 单测（`ci.yml` 全程不需要签名密钥，Android 打包仍只在本机/发布机执行）；`.github/workflows/security.yml` 继续用 gitleaks 扫描全历史。CI 通过不能替代本节的本地基线与模拟器验收。

推荐优先运行统一脚本：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/verify-release-baseline.ps1
```

修改发布验证脚本本身时，先运行脚本回归测试：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/tests/verify-release-baseline-tests.ps1
```

Android UI 或播放器体验改动完成后，建议在完成 debug APK 编译后执行模拟器截图验收：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/android-ui-smoke.ps1
```

该脚本会安装 debug APK、启动 App、以默认 English UI 文案做 smoke 断言，采集首页、Me 页、继续观看区域以及播放器或登录面板的截图和 UI dump，产物默认输出到 `artifacts/android-ui-smoke/<timestamp>`。

脚本默认执行：

- `python -m pytest content-provider`
- `backend\gradlew.bat test --no-daemon`
- `npm ci`（仅 `admin-web/node_modules` 不存在时）、`npm run build` 和 `npm run test:contracts`
- `android-app\gradlew.bat :app-core:test :app:testDebugUnitTest --no-daemon`
- `android-app\gradlew.bat :app:assembleDebug --no-daemon`（需要 `ANDROID_SIGNING_*`）
- `infra/scripts/verify-backup-scripts.ps1` 静态校验
- `scripts/tests/` 下的发布脚本与基线脚本回归测试
- `git diff --check`、`git diff --cached --check`
- 未提交的已跟踪文件修改检查与未跟踪文件检查

如只验证部分模块，可使用跳过参数：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/verify-release-baseline.ps1 -SkipAndroid
powershell -ExecutionPolicy Bypass -File scripts/verify-release-baseline.ps1 -SkipBackend -SkipContentProvider -SkipAdminWeb
powershell -ExecutionPolicy Bypass -File scripts/verify-release-baseline.ps1 -SkipInfraChecks -SkipScriptTests -SkipAdminWebContracts
```

本机没有签名密钥时，可以只跑 Android JVM 单测、跳过打包步骤：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/verify-release-baseline.ps1 -SkipAndroidPackage
```

需要在有未提交改动的本地工作树上做一次刻意的构建验证时，显式放行脏树：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/verify-release-baseline.ps1 -AllowDirtyTree
```

不传 `-AllowDirtyTree` 时，存在未提交的已跟踪文件修改会让基线检查失败并列出文件清单；`-AllowUntrackedFiles` 同理只应用于有意保留的生成产物。

如果本次包含 Android 代码变更，必须安装最新 APK：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/verify-release-baseline.ps1 -InstallApk
```

如使用雷电模拟器自带 adb：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/verify-release-baseline.ps1 -InstallApk -Adb C:\leidian\LDPlayer14\adb.exe
```

完成标准：

- 统一脚本退出码为 `0`。
- Android 变更已生成并安装最新 debug APK（未跳过打包步骤时）。
- `git diff --check` 无空白错误；Windows 换行提示可接受。
- 不存在未提交的已跟踪文件修改，也没有计划外的未跟踪文件。

## 3. Android 模拟器验收

Android 代码变更必须在模拟器完成手动验收，不能只依赖单元测试或编译。

核心路径：

- 启动 App，首页推荐可加载。
- 切换系统语言不影响内容：内容与后台均为英文单语，App UI 不再提供英文/繁中切换。
- 搜索页点击预设标签并展示海报网格结果。
- 首页或搜索点击短剧后直接进入播放器。
- 播放器加载期间有明确加载状态，选集、返回、点赞、收藏、评论入口可用。
- 奖励进度胶囊在播放进度推进时显示可理解状态，点击可打开说明弹层。
- Me 页登录/游客态布局正常，继续观看、积分流水、观看历史、订单入口可达。

完成标准：

- 窄屏和默认模拟器尺寸下无明显遮挡、截断或重叠。
- 用户关键路径没有纯黑屏、无反馈点击或无法返回的问题。
- 若发现体验问题，应记录到新的修复计划，不把问题静默带入发布。

### Android 更新发布（对象存储方式）

- 版本号必须为 `X.Y.Z`，并与 Android `versionName` 完全一致；`versionCode` 必须单调递增。
- 发布使用本地脚本 `python scripts/publish-android.py`，详细前置准备和参数见 `docs/deploy/publish-android.md`。
- 脚本完成签名构建、`apksigner` / 固定正式证书 SHA-256 / 包名 / versionName / versionCode 校验、SHA-256 和大小计算后，上传 APK 和 `.sha256` 到腾讯 COS，再 POST 发布元数据到后端 `POST /api/internal/release/publish`。
- 后端在 `app_releases` 表 upsert 发布记录，App 清单只从 `https://shortlink.hjj888.cc/api/app/release/latest` 获取并直接返回 COS 预签名下载 URL；旧版 `/api/app/update/latest` 与 `/downloads/android/` 兼容链路已删除。
- 线上 Release APK 必须使用 `android-app/release-signing/shortlink-release.jks` 的固定正式证书；换机器时迁移同一 keystore 和本地配置，禁止重新生成。COS 密钥、签名密码和超级 Token 只在 gitignored 本地文件或部署环境变量中，禁止写入仓库或日志。

发布后执行：

```powershell
curl -s https://shortlink.hjj888.cc/api/app/release/latest | python -m json.tool
```

确认返回的 `versionName`、`versionCode`、`apkSha256` 和预签名 `apkUrl` 正确后，在 App「我的」页面手动检查更新。

## 4. 服务器部署验收

服务器发布 backend、content-provider 或 infra 后执行：

```powershell
# 后端健康检查必须从容器内直连；公网 /actuator/health 会命中后台 SPA 兜底并返回 200 HTML
docker compose --env-file .env exec backend curl -fsS http://127.0.0.1:8080/actuator/health

curl "https://shortlink.hjj888.cc/api/app/home/recommend?locale=en"
curl -s -o NUL -w "%{http_code}`n" -X POST "https://shortlink.hjj888.cc/api/app/wallet/bank-card"
```

预期：健康检查返回 `{"status":"UP"}`；推荐接口返回正常 JSON；银行卡接口返回 `410`（需要登录鉴权时先带 App Token）。

需要确认的部署事实：

- `REELSHORT_TRUSTED_PROXIES` 已填成 Nginx 所在 Docker 子网（留空或填错会让全部匿名客户端共用一个限流键）。
- `CONTENT_PROVIDER_INTERNAL_TOKEN` 在 backend 与 content-provider 两侧取值一致。
- `REELSHORT_WITHDRAWAL_PAYOUT_ENABLED=false`（自动打款执行能力保持关闭）。
- 备份只落在 `infra/backups/`，仓库根目录没有生产库转储。

后台或服务器侧需要确认：

- Nginx HTTPS 证书有效，`shortlink.hjj888.cc` 可访问。
- backend、content-provider、PostgreSQL、Redis 容器或进程均处于健康状态。
- 首页推荐优先从自有 PostgreSQL 缓存返回，不因上游短暂失败直接空白。
- 播放地址只在播放时按需拉取，不在片库刷新时预抓视频流。
- 播放地址缓存只作为短期 5xx 兜底，确认 `REELSHORT_CONTENT_VIDEO_FALLBACK_TTL` 符合发布预期；默认 `10m`，设为 `0` 可禁用兜底。
- 后台缓存刷新可执行，并能看到推荐货架数量变化。

完成标准：

- 域名和 HTTPS 可用。
- App API 核心接口返回正常 JSON。
- 内容缓存刷新和播放地址路径都能通过服务器日志定位。

## 5. 回滚边界

发布前必须明确回滚方式：

- 代码回滚：记录上一个可用 Git commit，必要时重新部署该 commit 对应镜像或构建产物。
- 数据库回滚：Flyway 迁移默认只前进；涉及 schema 的发布必须先备份 PostgreSQL。
- Android 回滚：已安装更高 `versionCode` 后不能原地降级；发现线上问题应撤下尚未安装的 Release，并以更高 `versionCode` 发布修复版本。
- 内容缓存回滚：如刷新后内容异常，优先停止刷新任务并恢复数据库备份，或重新执行一次货架刷新（内容源当前只提供英文单语，不存在按语言回滚）。
- Nginx/证书回滚：保留上一个 Nginx 配置，证书续期失败时不要删除旧证书。

完成标准：

- 发布负责人知道如何恢复上一版本服务。
- 数据库结构变更前已经备份。
- 任何不可逆操作都必须先写清影响范围。

## 6. 发布记录

每次发布后记录：

- 发布日期和 commit hash。
- 涉及模块。
- 已运行的验证命令。
- Android APK 是否已安装模拟器验收。
- 服务器接口和后台缓存刷新是否已验证。
- 已知风险和后续修复计划。

建议记录在对应 `docs/plans/*.md` 阶段文档或发布记录文档中，并同步更新 `AGENTS.md` 变更历史。
