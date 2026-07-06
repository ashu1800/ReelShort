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

推荐优先运行统一脚本：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/verify-release-baseline.ps1
```

修改发布验证脚本本身时，先运行脚本回归测试：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/tests/verify-release-baseline-tests.ps1
```

脚本默认执行：

- `python -m pytest content-provider`
- `backend\gradlew.bat test --no-daemon`
- `npm ci`（仅 `admin-web/node_modules` 不存在时）和 `npm run build`
- `android-app\gradlew.bat :app-core:test :app:testDebugUnitTest :app:assembleDebug --no-daemon`
- `git diff --check`

如只验证部分模块，可使用跳过参数：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/verify-release-baseline.ps1 -SkipAndroid
powershell -ExecutionPolicy Bypass -File scripts/verify-release-baseline.ps1 -SkipBackend -SkipContentProvider -SkipAdminWeb
```

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
- Android 变更已生成并安装最新 debug APK。
- `git diff --check` 无空白错误；Windows 换行提示可接受。

## 3. Android 模拟器验收

Android 代码变更必须在模拟器完成手动验收，不能只依赖单元测试或编译。

核心路径：

- 启动 App，首页推荐可加载。
- 切换 English / 繁體中文，首页和搜索预设标签随语言切换。
- 搜索页点击预设标签并展示海报网格结果。
- 首页或搜索点击短剧后直接进入播放器。
- 播放器加载期间有明确加载状态，选集、返回、点赞、收藏、评论入口可用。
- 奖励进度胶囊在播放进度推进时显示可理解状态，点击可打开说明弹层。
- Me 页登录/游客态布局正常，继续观看、积分流水、观看历史、订单入口可达。

完成标准：

- 窄屏和默认模拟器尺寸下无明显遮挡、截断或重叠。
- 用户关键路径没有纯黑屏、无反馈点击或无法返回的问题。
- 若发现体验问题，应记录到新的修复计划，不把问题静默带入发布。

## 4. 服务器部署验收

服务器发布 backend、content-provider 或 infra 后执行：

```powershell
curl https://reelshort.hjj888.cc/actuator/health
curl "https://reelshort.hjj888.cc/api/app/home/recommend?locale=en"
curl "https://reelshort.hjj888.cc/api/app/home/recommend?locale=zh-TW"
```

后台或服务器侧需要确认：

- Nginx HTTPS 证书有效，`reelshort.hjj888.cc` 可访问。
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
- Android 回滚：保留上一个可用 APK，必要时重新安装旧 APK 验证。
- 内容缓存回滚：如刷新后内容异常，优先停止刷新任务并恢复数据库备份或重新刷新稳定 locale。
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
