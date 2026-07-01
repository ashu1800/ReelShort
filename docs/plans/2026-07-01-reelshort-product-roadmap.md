# ReelShort Product Roadmap Implementation Plan

## Goal

建立后续产品和工程开发路线图，作为后续按优先级依次开发的总入口。路线图按风险和商业价值排序，先稳定发布质量和内容源，再增强 Android 播放体验、商业化链路、可观测性与风控。

## Architecture

后续开发按阶段推进，每个阶段独立设计、实现、验证和发布，避免跨 Android、backend、content-provider、infra 的大范围耦合改动一次性进入主分支。

核心边界：

- Android App 每次功能改动后必须编译、安装到模拟器并完成关键路径手动验收。
- 后端和 content-provider 改动必须覆盖单元测试、接口验证和部署验证。
- 视频文件不存自有服务器；标题、封面、简介、分集等元数据沉淀在自有 PostgreSQL，播放地址按需从上游获取。
- 每个阶段完成后更新 `AGENTS.md` 变更历史，并按仓库规范提交、推送、合并。

## Tech Stack

- Android：Kotlin、Jetpack Compose、Material3、Media3、OkHttp
- Backend：Spring Boot、Flyway、PostgreSQL、Redis
- Content Provider：Flask、pytest、ReelShort Next data/API 适配
- Admin Web：Vue、Element Plus、Pinia、Axios
- Infra：Docker Compose、Nginx、Certbot、PostgreSQL/Redis 备份恢复

## Phase 1: 发布质量基线

### 目标

收口当前大 diff，形成可发布、可回滚、可部署的稳定基线，避免后续功能开发建立在不确定状态上。

### 主要改动

- 整理当前 `master` 与功能分支提交，确保所有已完成工作合并到主分支。
- 拆分或标记大功能提交，建立清晰的发布变更清单。
- 固定本地验证命令，形成发布前 checklist。
- 增加最小 CI 或本地一键验证脚本，覆盖 backend、content-provider、Android app-core/app 单元测试和构建。
- 梳理部署回滚步骤，明确数据库迁移、Docker 镜像和 Android APK 的回滚方式。

### 验证命令

```powershell
python -m pytest content-provider
backend\.\gradlew.bat test --no-daemon
android-app\.\gradlew.bat :app-core:test :app:testDebugUnitTest :app:assembleDebug --no-daemon
git diff --check
```

### 模拟器或服务器验收点

- 安装最新 debug APK 到模拟器，验证首页、搜索、播放、Me 页、语言切换、登录、续播、积分胶囊。
- 服务器部署 backend、content-provider 后，验证首页推荐、搜索、分集、播放地址和后台缓存刷新。
- 确认 Nginx、HTTPS、PostgreSQL、Redis、日志和备份脚本均可用。

### 完成标准

- `master` 处于可构建、可部署、可回滚状态。
- 所有必要验证命令通过。
- 最新 APK 已安装到模拟器并完成关键路径验收。
- 服务器可通过正式域名访问核心 App API。

## Phase 2: 内容源与缓存运维闭环

### 目标

让片库刷新、缓存健康和上游异常可观测，确保 App 首页和搜索优先读取自有缓存，上游异常时不造成首页空白。

### 主要改动

- 后台展示内容刷新任务状态：开始时间、结束时间、耗时、locale、总数、成功数、失败关键词、失败原因。
- 为 `content_book_cache`、`content_shelf_cache`、`content_episode_cache` 增加缓存健康视图和刷新审计。
- provider 增加上游结构变化告警：字段缺失、Next data 404、搜索页空结果异常、播放页解析失败。
- 明确视频地址缓存 TTL 或禁用长期持久化播放地址，避免返回过期 URL。
- 增加后台一键刷新 `en` / `zh-TW` 推荐和搜索关键词片库的操作反馈。
- 增加缓存刷新失败时的降级策略文档和后台提示。

### 验证命令

```powershell
python -m pytest content-provider
backend\.\gradlew.bat test --no-daemon
git diff --check
```

### 模拟器或服务器验收点

- 后台触发推荐货架刷新后，可看到刷新数量、locale、耗时和错误摘要。
- 连续请求 `https://reelshort.hjj888.cc/api/app/home/recommend?locale=en` 不应每次触发大量上游请求。
- 上游搜索某个关键词失败时，首页缓存仍能返回已有内容。
- 播放某一集时才出现视频地址相关上游请求。

### 完成标准

- 首页和主要货架默认从自有数据库缓存读取。
- 管理员能判断内容刷新是否健康。
- 上游结构变化时有后台告警或日志可定位。
- 视频 URL 缓存策略明确且不会长期返回过期地址。

## Phase 3: Android 播放体验

### 目标

将播放器体验提升到更接近商用短剧产品标准，在弱网、切集、失败、续播和奖励进度场景都有明确反馈。

### 主要改动

- 增加首帧封面过渡：视频准备前显示封面和加载状态，减少黑屏感。
- 播放失败状态提供重试、返回、切换下一集等明确操作。
- 选集弹层展示观看状态：已看、看到百分比、当前集、锁定或异常状态预留。
- 继续观看卡使用真实海报和进度信息，提升 Me 页续播效率。
- 增加关键 Compose/UI contract 测试和截图验收脚本，覆盖搜索、Me 页、播放器、奖励胶囊。
- 梳理播放器状态机：加载、缓冲、播放、错误、切集、退出，避免旧请求覆盖新 UI。

### 验证命令

```powershell
android-app\.\gradlew.bat :app-core:test :app:testDebugUnitTest :app:assembleDebug --no-daemon
adb install -r android-app\app\build\outputs\apk\debug\app-debug.apk
git diff --check
```

### 模拟器或服务器验收点

- 首页点击短剧进入播放器，准备期间显示封面和加载状态。
- 快速切集不会被旧视频地址覆盖。
- 播放失败时可重试或切换分集。
- 返回首页后再次点击同一短剧，进入上次观看集和位置。
- 奖励胶囊在 25/50/75/100 阶段正常推进并展示同步状态。

### 完成标准

- 弱网、失败、续播、选集、积分路径均有明确反馈。
- Android 单元测试和构建通过。
- 最新 APK 已安装到模拟器并完成关键路径手动验收。

## Phase 4: 商业化链路

### 目标

从模拟订单走向真实可验收的商业闭环，让用户积分获取、充值、消耗、订单状态和后台对账形成闭环。

### 主要改动

- 接入真实支付 sandbox，保留现有模拟支付作为开发和测试入口。
- 完善订单状态展示：待支付、支付中、已支付、失败、关闭、退款预留。
- 设计积分消耗场景，例如付费解锁、会员权益或高价值内容观看门槛。
- 后台增加对账视图：支付事件、订单、积分流水、用户账户变更链路。
- 增加异常订单处理：重复回调、金额不一致、超时未支付、入账失败补偿。
- 明确商业化配置项和灰度开关，避免未完成支付链路影响普通播放。

### 验证命令

```powershell
backend\.\gradlew.bat test --no-daemon
git diff --check
```

如改动 admin-web：

```powershell
cd admin-web
npm install
npm run build
```

### 模拟器或服务器验收点

- App 可创建订单并看到订单状态变化。
- 支付 sandbox 回调能幂等入账。
- 后台能查询订单、支付事件、积分流水和异常原因。
- 支付失败或取消不影响现有播放、登录、搜索功能。

### 完成标准

- 用户能完成充值或积分获取/消耗闭环。
- 后台能审计和处理异常订单。
- 支付回调、订单结算和积分入账具备幂等保护。

## Phase 5: 可观测性与风控

### 目标

让线上运行问题可发现、可定位、可控制，降低播放失败、接口异常、上游波动和滥用行为的运维风险。

### 主要改动

- 后端增加核心指标：接口耗时、错误率、鉴权失败、限流命中、内容源失败、支付回调异常。
- provider 增加上游请求指标：关键词请求数、空结果、解析失败、播放地址失败、上游耗时。
- Android 增加关键错误上报：播放失败、视频加载超时、登录失败、内容接口失败、奖励上报失败。
- 后台系统健康概览增加证书、数据库、Redis、磁盘、日志、备份最近成功时间。
- 关键接口增加限流审计：登录、注册、播放地址、进度上报、支付回调、后台操作。
- 增加基础告警策略：连续播放失败、内容刷新数量异常、数据库连接失败、证书临期。

### 验证命令

```powershell
python -m pytest content-provider
backend\.\gradlew.bat test --no-daemon
android-app\.\gradlew.bat :app-core:test :app:testDebugUnitTest :app:assembleDebug --no-daemon
git diff --check
```

### 模拟器或服务器验收点

- 后台健康页能看到 backend、content-provider、PostgreSQL、Redis、证书和备份状态。
- 主动制造 provider 上游失败时，后台可看到错误趋势或告警。
- 播放失败能在后端或后台聚合为可定位记录。
- 限流命中有审计日志，且不会误伤正常播放路径。

### 完成标准

- 后台能看到服务健康和核心错误趋势。
- 关键接口有审计、限流和异常定位能力。
- 线上问题不再只能靠用户截图和手动翻日志定位。

## Execution Rules

- 每个阶段开始前先写阶段设计文档或修复计划。
- 每个阶段只在独立分支或 worktree 开发，验证完成后再合并到 `master`。
- Android 改动必须执行编译、安装模拟器和手动验收。
- 后端 schema 改动必须有 Flyway 迁移和迁移测试。
- content-provider 改动必须覆盖 pytest 和至少一个真实或模拟上游结构验证。
- 文档、部署说明和 `AGENTS.md` 变更历史随阶段同步更新。
