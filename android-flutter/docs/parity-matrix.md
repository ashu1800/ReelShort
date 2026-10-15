# 功能对齐矩阵（Kotlin `android-app` → Flutter `android-flutter`）

本文档是「功能百分百重构」的审计凭据：**哪些按 1:1 移植、哪些被刻意重写、哪些原有缺陷被保留、哪些偏差是刻意的**。

对齐口径：

- **逻辑层 1:1**：网络契约、数据模型、存储边界、状态机、格式化与业务规则按 Kotlin 逐行移植，行为必须一致（含边界值与错误分支）。
- **表现层重写**：`android-app/app/src/main/java/com/reelshort/app/ui/**` 的布局与交互**不复刻**，按全新设计系统重做（见 [design-system.md](design-system.md)）。因此「像素级一致」不在验收范围内，「行为一致」在。
- 表现层重写时**不得改变**任何业务规则。规则清单见下文第 4 节。
- 任何无法 1:1 对齐之处必须出现在本文档，不允许静默偏离。

---

## 1. 逐层移植对照

| Kotlin 源 | Dart 目标 | 方式 |
| --- | --- | --- |
| `app-core/.../data/AppModels.kt` | `lib/data/models/app_models.dart` | 手写 1:1 |
| `app-core/.../data/AppLanguage.kt` | `lib/data/models/app_language.dart` | 手写 1:1 |
| `app-core/.../network/dto/AppApiDtos.kt` | `lib/data/network/api_dtos.dart` | 手写 1:1（含 `toDomain()` 扩展） |
| `app-core/.../network/OkHttpReelShortApiClient.kt` | `lib/data/network/http_reel_short_api_client.dart` | 手写 1:1（路径/查询/请求体/鉴权头一致） |
| `app-core/.../network/ReelShortApiClient.kt` | `lib/data/network/reel_short_api_client.dart` | 接口 1:1（31 个方法） |
| `app-core/.../data/AppDataSource.kt` | `lib/data/app_data_source.dart` | 接口 1:1（41 个成员） |
| `app-core/.../data/AppRepository.kt` | `lib/data/app_repository.dart` | 手写 1:1 |
| `app-core/.../session/*Store.kt` | `lib/data/sources/*.dart` | 手写 1:1（含原子写与自愈读） |
| `app-core/.../state/AppUiState.kt` | `lib/state/app_ui_state.dart` | **代码生成**（`tool/port_app_ui_state.py`）+ Dart 契约测试锁定 |
| `app-core/.../state/PlaybackState.kt` | `lib/state/playback_state.dart` | 手写 1:1 |
| `app-core/.../state/AppStateController.kt` | `lib/state/app_state_controller.dart` | 手写 1:1，逐方法对照见 [controller-parity.md](controller-parity.md) |
| `app-core/.../session/UpdateCoordinator.kt`、`ShortLinkUpdateClient.kt`、`OkHttpReleaseDownloader.kt`、`ReleaseModels.kt`、`SemanticVersion.kt` | `lib/update/**` | 手写 1:1（安装步骤抽象为接口，见 §3.8） |
| `app/src/main/java/.../ui/format/AppStrings.kt` | `lib/core/formatting/app_strings.dart` | **代码生成**（`tool/port_app_strings.py`）+ JSON golden 契约测试锁定 216 个字段 |
| `app/src/main/java/.../ui/format/UiFormats.kt` | `lib/core/formatting/ui_formats.dart` | 手写 1:1 |
| `app/src/main/java/.../ui/format/RewardBadgeState.kt` | `lib/core/formatting/reward_badge_state.dart` | 手写 1:1 |
| `app/src/main/java/.../ui/format/NavigationContract.kt` | `lib/core/formatting/navigation_contract.dart` | 手写 1:1 |
| `app/src/main/java/.../ui/format/UpdateUiFormat.kt` | `lib/core/formatting/update_ui_format.dart` | 手写 1:1（25 字段英文表） |
| `app/src/main/java/.../ui/**`（Compose 屏幕与组件） | `lib/ui/**` | **完全重写**，不复刻布局与交互 |

### 代码生成 + golden 的用法

`AppStrings`（216 字段）与 `AppUiState`（47 字段 / 4 枚举）规模大且必须逐字一致，因此用 Python 脚本从 Kotlin 源**机械生成** Dart，同时输出 JSON golden。Dart 契约测试读取 golden 并逐字段比对，从而在不引入跨语言测试框架的前提下证明两端一致。

```powershell
python tool/port_app_strings.py   # -> lib/core/formatting/app_strings.dart + test/core/app_strings_golden.json
python tool/port_app_ui_state.py  # -> lib/state/app_ui_state.dart + test/state/app_ui_state_golden.json
```

脚本在字段数量或键集合发生变化时**主动失败**，避免静默漂移；Kotlin 源变更后必须重新生成并让契约测试重新通过。

---

## 2. 被保留的原有实现缺陷

以下问题在 Kotlin 版中真实存在。Flutter 版**保留同样的可观察行为**（不趁机"修好"），以保证功能对齐；同时在此登记，便于后续统一决策。

| # | 位置 | 现象 | Flutter 版处理 |
| --- | --- | --- | --- |
| G1 | `AppScreen.DETAIL` | 状态机从不赋值 `DETAIL`，详情页实际不可达（18 处 `screen = ` 赋值中无一处为 DETAIL） | 忠实移植状态机；仍实现详情界面以便后续接线。**不新增**可达路径 |
| G2 | `PlayerScreen` | 从未调用 `playableMediaUrlOrNull()`，URL 合法性过滤在播放页实际未生效 | 移植该函数并保持其在播放页同样不被调用 |
| G3 | `SearchScreen` 空态 | 空态文案被硬编码英文覆盖（"Find your next drama" / "No dramas matched this search."），"Search again" 退化为不可点击的 `MetaPill`；`searchInitialHint` 成为死字段 | 保留同样的空态文案与不可点击行为；`searchInitialHint` 同样保留但不使用 |
| G4 | `playerSurfaceAspectRatio() = 9f/16f` | 仅测试与预览使用，真实播放页是全屏 + `RESIZE_MODE_ZOOM` | 保留该常量与真实全屏布局 |
| G5 | 评论分页 | 后端返回 `{comments,hasMore,page,size}`，客户端只取 `comments`，`hasMore`/分页参数未使用 | 保留：`listComments` 忽略分页 |
| G6 | `FavoritesScreen.onBack` | 参数从未被调用 | 保留参数与"不被调用"的写法 |
| G7 | 硬编码英文字面量 | 多处绕过 `AppStrings`：`"${chapterCount} short dramas"`、`"${chapterCount} eps"`、锁图标 `"VIP"`、`"$network wallet address"`、`"Current password"`、`"unbind the wallet"`、`"Withdraw N pts"`、`" pts · "` / `" USDT"`、`"N/A"`、`"Hide/Show $label"` | 文案改由统一英文表提供（见 §3.2），**触发条件与展示位置不变** |
| G8 | 更新清单字段 | `minimumVersionCode` 与 `mandatory` 被解析但从不使用（强制更新未实现）；`releaseNotes` 存入却不渲染；清单里的 `apkSha256` 被忽略（摘要取自 `.sha256` 附件） | 原样保留：解析但不用、存但不渲染、摘要只信 `.sha256` |
| G9 | `MainActivity` | `onShowVipPaywall` 从未传给任何 Composable，`C.showVipPaywall()` 不可达 | 保留"付费层只能由播放/详情路径触发"的实际行为 |
| G10 | `AccountScreen.onRefreshAccount` | 从未被调用 → Me 页无下拉刷新 | 保留：Me 页不提供刷新 |
| G11 | `AccountDetailSheet.WITHDRAWALS` / `ORDERS` | 枚举存在但不可达；`accountPrimaryActionLabels()` 声称 4 个入口而实际只渲染 3 个 | 保留枚举与标签列表，界面仍只渲染 3 个入口 |
| G12 | `ListRow.OrderRow` | 死代码 | 不移植死代码本身，但保留 `ReelShortApiClient.getOrders()` 与状态层对订单的加载能力 |
| G13 | 事件计数器 | `withdrawalSubmissionVersion` 无 UI 消费者；`AccountOperation.POINT_TRANSFER` / `WALLET_VERIFICATION` / `PASSWORD_VERIFICATION`、`AccountAction`、`accountActionRequiresConfirmation` 在 UI 中未被使用 | **必须**保留计数器自增（Kotlin 测试断言其存在）与枚举/函数的定义 |
| G14 | `AppScreen.LOGIN` | 渲染 `Unit`，导航栏仍在且无高亮项；整屏 `LoginScreen` / `BrandLockup` 为死代码 | 保留"LOGIN 不渲染内容且导航栏保持"的行为；不移植死代码 `LoginScreen` |
| G15 | 三套字符串表 | `AppStrings`(216) + `UpdateStrings`(25) + `RewardBadgeState` 内联字面量，另有 `UiFormats` 的确认/状态文案 | 三套表**全部保留并各自移植**，不合并——合并会改变字段数量与契约测试断言 |
| G16 | 提现状态色 | 颜色判断 `status == "CONFIRMED"`，而 `withdrawalStatusLabel` 把 `"APPROVED"` 映射为 "Completed" → 已完成的提现在界面上拿不到成功语义色 | 标签映射逐字保留；重写后的界面按**标签语义**取色（见 §3.11）。这是一处刻意的修正 |

---

## 3. 刻意差异（必须逐条确认）

| # | 主题 | Kotlin 版行为 | Flutter 版行为 | 理由 |
| --- | --- | --- | --- | --- |
| D1 | 视觉与交互 | 深色 + 金色单主题；Compose 布局 | 全新浅色/深色双主题；重新设计的布局与交互 | **用户明确要求**："UI 布局和交互不要复刻，重新设计一套浅色/深色主题" |
| D2 | 错误文案 | `app-core` 泄漏中文错误串（如 `"积分状态暂时加载失败，播放不受影响。"` 与 `userFacingErrorMessage` 内的中文），出现在全英文界面 | 统一由**单一英文表**渲染 | 内容与界面为英文单语（项目既定约束）。**触发条件、消息存在与否、`UiMessageType`、会话清理行为完全不变**；逐条中文↔英文对照见 [controller-parity.md](controller-parity.md) |
| D3 | 加载反馈 | 阻塞式居中弹窗（`LoadingFeedbackMode.CENTER_DIALOG`） | 骨架屏 + 顶部细进度条 + 按钮内联 spinner | 阻塞弹窗会遮断上下文；闸门与去重语义（并发保护、账号操作串行）**完全保留** |
| D4 | HTTP 超时 | OkHttp 默认 10s 连接 / 10s 读取 | 整请求 60s 截止 | `http` 包默认无读取超时。内容源冷缓存时单次上游抓取实测 4–15s，10s 会误杀；60s 与后端自身的读超时一致，且仍有上界，界面不会永久挂起 |
| D5 | 查询编码 | `addQueryParameter` 将空格编码为 `%20` | 手动把 `+` 归一为 `%20` | 两端解码结果相同，归一化只为保持请求串一致（便于抓包对比） |
| D6 | 播放器状态 | Media3 `Int` 常量（`STATE_IDLE=1` 等） | `PlayerPhase` 枚举 | Flutter 用 `video_player`，没有 Media3。覆盖层判定规则（错误 > 封面加载 > 缓冲 > 无）与优先级**逐条保留** |
| D7 | 金额运算 | `BigDecimal` + `RoundingMode.DOWN`/`HALF_UP` + `stripTrailingZeros` | 手写定点小数（按位缩放整数） | 二进制浮点无法复现 `setScale`/去尾零的字符串结果；自实现可精确对齐 `1536 → "1.5 KB"` 一类断言 |
| D8 | 更新安装 | 直接构造 `ACTION_VIEW` intent 调起系统安装器 | 抽象为 `UpdateInstaller` 接口，Android 侧提供实现 | 平台相关代码必须可测；状态机（权限矩阵、失败映射）保持 1:1 |
| D9 | 字体 | 系统默认字体 | 系统默认字体族，通过字重/字距/大小与 `FontFeature.tabularFigures()` 区分显示/正文/数字三类角色 | 不引入字体二进制、不做运行时字体下载，避免联网与体积风险 |
| D10 | Launcher 图标 | 深色 + 金色自适应图标 | 深色 + 紫色（accentPrimary）自适应图标，保留"竖版画框 + 竖排集数轨"的品牌剪影 | 与新主题统一。属可见的品牌调整，故在此登记 |
| D11 | 提现状态色 | 见 G16：颜色判断用了 `"CONFIRMED"`，已完成的提现没有成功色 | 按 `withdrawalStatusLabel` 的标签语义取色 | 修正一处明显的视觉错误；标签映射本身逐字保留 |
| D12 | 冗余的 `meta` 依赖 | 无 | `pubspec.yaml` 引入 `meta` 以支持生成文件里的 `@immutable` | 生成代码需要；`meta` 由 Flutter SDK 固定版本，不引入额外传递依赖 |
| D13 | Gradle 依赖源 | `google()` / `mavenCentral()` | 阿里云镜像优先，上游仓库兜底 | 本机 `maven.google.com` 不稳定；保留上游兜底使其他网络环境同样可用 |
| D14 | CI 打包 | `ci.yml` 只跑 Android JVM 单测 | 新增 `android-flutter` job：`pub get` / `analyze` / `test`，**不打包** | 打包任务会校验钉死的正式证书，缺 `ANDROID_SIGNING_*` 时故意失败；CI 不持有签名密钥 |

---

## 4. 必须逐条保持的业务规则

以下 51 条规则来自 Kotlin 源与既有测试，是「功能百分百」的实际判据。Flutter 版实现后由移植的 Dart 测试覆盖。

### 4.1 播放与奖励

1. VIP 锁：仅当 `!vipActive && number > 7`（`DEFAULT_VIP_FREE_EPISODES`）。
2. 锁定集仍可路由——锁只是视觉态，由状态层决定弹认证面板还是付费层。
3. 奖励圆环仅 VIP 可见。
4. 圆心数值：结算前用后端 `estimatedRewardPoints`，结算后用 `awardedPoints`（命中每日上限时为 0），并 `coerceAtLeast(0)`。
5. 圆环进度 = `progressPercent.coerceIn(0,100) / 100`。
6. 圆环状态优先级：错误 > 上报中 > 每日上限 > 时长不可用 > 已领取 > 进度≥100 > 等待。
7. 圆心字号：1–2 位 `12f`、3 位 `10f`、≥4 位 `8f`。
8. 无障碍文案仅在 `"1"` 时用单数 "point"。
9. `+N pt` 浮层要求 `rewardAwardVersion` 自增**且** `awardedPoints > 0`，**1100 ms** 后隐藏。
10. 进度保存窗口：位置增量 ≥ **15 s**；播放器心跳 1 s。
11. 完成时必定保存，且不重复保存。
12. 非法时长/位置永不保存。
13. 上报失败退避：`[5,15,60]` 秒阶梯，上限 300 s，超出后维持 60 s。
14. 上报去重：进行中的上报只更新本地状态；100% 完成上报先暂存再以深度 1 重发，不可丢弃。
15. 领取后停表：`rewardClaimed` 为真时心跳循环直接 `return`。
16. 自动播放恒为 `true`；`ON_RESUME` 调 `play()`。
17. `initialPositionSeconds > 0` 时 `seekTo(s * 1000)`。
18. 时长兜底：`player.duration <= 0` 时用服务端 `durationSeconds`；采样用 `ceil(ms/1000)`。
19. 覆盖层优先级：错误 > 封面加载（URL 为空 / IDLE / 未 ready）> 缓冲 > 无。
20. 重试通过自增 `retryTrigger` 重建播放器（URL 不变）。
21. "下一集"仅当按 `(number, chapterId)` 存在后继时可见；最后一集不显示。
22. 看完后续播：进度 <100% 留在本集；=100% 进下一集；无下一集则停留。
23. 选集单元状态：选中为"当前"；否则 100 为"已看"；>0 显示 `{n}%`；0 无标签。
24. 观看状态键 `(bookId == book?.id && episode == episode.number)`，进度裁剪到 0..100。
25. 重复点击同一集（`number` 与 `chapterId` 都相同）只关闭选集面板。
26. `formatCount`：≥1e6 → `%.1fM`；≥1000 → 整数 `{n/1000}k`；>0 原样；**0 → 空串**。
27. 评论提交闸门：空白禁用发送；提交后清空草稿；状态层再 `trim()` 并要求非空；成功后重新拉取评论。
28. 头像字符 = 用户名首字母大写；用户名为空 → `"?"`。
29. 社交请求 401 且上下文 ∈ {ACCOUNT, PLAYBACK, SOCIAL} → 打开认证面板。
30. 返回目标 = `playerReturnScreen ?? HOME`；SEARCH / FAVORITES / ACCOUNT 各自回到自己。
31. Home/Search 网格列数 `GridCells.Adaptive(136.dp)`（即 `max(1, floor(可用宽度/136dp))`）。
32. Favorites 固定 2 列。
33. 仅 Home 支持下拉刷新；只改 `isHomeRefreshing`；失败保留旧数据并弹顶部错误。
34. 搜索提交先 `trim()`，空白忽略且无副作用。
35. 结果优先布局判据：`query.isNotBlank() || resultCount > 0`。
36. 搜索空态文案覆盖为 "Find your next drama" / "No dramas matched this search."；"Search again" 退化为不可点击 `MetaPill`。
37. 标签点击立即写入查询并提交。
38. 输入框状态同步依赖 `(state.searchQuery, state.language)`。
39. 游客点海报不跳转：停留原页 + 弹认证面板，并按集数写入 `pendingVipEpisode`（>7）或 `pendingPlaybackEpisode`。
40. 登录后自动续播：有 VIP 待播 → 付费层；否则按观看历史恢复集数进入播放器。
41. 付费层自动续播仅在 VIP 生效且 `pendingRequestVersion == playbackRequestVersion` 时发生。
42. 海报标题行数：`fontScale >= 1.3f` 时 1 行，否则 2 行。
43. 海报占位字母 = 前两个单词首字母；无单词 → `"RS"`。
44. 海报集数文案 `"{n} eps"`。
45. 集号文案 `"EP " + padStart(2,'0')`；集标题 `"EP nn · title"`。
46. 集副标题回退链：分集简介 trim → 短剧简介 trim。
47. 稳定列表 key：短剧按 `id`；分集详情页 `"{chapterId}-{number}"`、播放器 `"{chapterId}:{number}"`；评论按 `id`。
48. 详情空态：无短剧 → "Pick a drama first"；有短剧无分集 → "Episodes unavailable"。
49. 空态动作可点击性：提供 `onAction` → 按钮；未提供 → 仅 `MetaPill`。
50. 收藏游客态：空列表、无错误、复用 "No favorites yet"。
51. 其他常量：注册可提交 = `!isLoading && username 非空 && password.length >= 8 && password == confirm && captchaAnswer 非空 && captchaLoaded`；二次确认动作集合 = `{WALLET_UNBIND, POINT_TRANSFER, WITHDRAWAL}`；表单提交后不自动关闭；提现手续费向上取整、USDT 向下截断到 2 位。

### 4.2 账户 / 资金 / VIP 契约

- 底部四个一级 Tab：Home / Discover / **Funds center** / Me；无全局 TopAppBar。
- Tab 高亮判据严格为 `state.screen == tab`；切换 `Crossfade` 220 ms。
- `LOGIN` 屏不渲染内容但导航栏保留；`PLAYER` 为全屏（在 shell 之外）。
- 登录闸门只要求用户名与密码非空（无最小长度）。
- 进入 REGISTER 且无验证码图片时自动拉取。
- 表单错误不做行内展示，只用全局顶部消息（4 s 自动消失、可点击关闭）。
- 关闭认证面板会清除待播集数、重置模式并自增 `vipPaywallGeneration`。
- `AccountScreen` 无刷新、无提现/VIP/诊断入口。
- "继续观看"最多 2 条，超过显示 `records.size/2` 角标。
- 游客面板入口 `["Sign in","Create account"]` → LOGIN / REGISTER。
- 钱包弹层只提供 ERC20（标签硬编码）；解绑需二次确认，绑定/更换不需要。
- 改密表单无确认字段且提交后不自动关闭。
- 客服邮箱为可选中文本；退出登录无二次确认。
- 提现提交为两步确认；输入框仅数字；失焦且为空时切换占位文案。
- VIP 充值页**不**轮询；全局 VIP 弹层在存在 PENDING/CONFIRMED 订单时每 10 s 轮询。
- VIP 倒计时按 `expiresAt` 渲染 `M:SS`，解析失败按 0。
- 二维码载荷为裸 Base58 TRC20 地址（无 scheme/金额/memo）；标签 `vipPayAddress = "Send USDT (TRC20) to:"`；内容描述 `vipPayQrHint = "Scan to pay"`；编码失败时不渲染；"Copy" 写入剪贴板；`VipRechargeScreen` 地址为空显示 `"N/A"`。
- 奖励/积分：每完整 60 s 计 1 分；同一用户同一集只发一次；每日自动奖励上限默认 1000 分、全局下浮上限默认 35%、按服务器自然日重置；仅 `WATCH_REWARD` 受限。
- 提现：默认 50 分 = 0.14 USDT；手续费按整数积分向上取整；USDT 向下截断 2 位；最低 10 USDT；最低积分动态计算。
- 同一用户同时只允许一笔 `PENDING` 提现（重复提交 → 409）；支持 `Idempotency-Key`（≤128 字符）。
- 钱包绑定/更换/解绑需要当前密码，错误 → **403**（不当作会话失效）；银行卡固定 410 且界面只读说明。
- 401 清理本地会话但**保留**记住密码；登录/注册失败与"当前密码错误"**不**清理会话；`vipPaywallVisible && 401` 额外清空支付态；403 封禁（disabled/blacklisted/user status）清理会话、清空支付态并关闭认证面板。
- 冷启动：有缓存 → 单次 update 渲染缓存且 `isLoading = false`，随后静默刷新并写回缓存；无缓存 → 走网络；失败 → 内容错误文案但保留会话。
- 登录后恢复优先级：VIP 待播集 > 普通待播集（按历史重选集数）> 原地刷新账户快照 > 回首页并重新加载货架。

### 4.3 自更新

- 拒绝 `versionCode <= 0` 与降级包（`release.versionCode < currentVersionCode`）；仅当 `release.version > currentVersion` 才提示。
- 只接受精确桶域名 `update-1259596907.cos.ap-chengdu.myqcloud.com` 的 HTTPS 地址（不接受端口、userinfo、fragment；路径必须以 `/<apkName>` 结尾）。
- 清单 JSON 硬上限 65 536 B；摘要附件上限 1 024 B，格式 `^([a-fA-F0-9]{64})(?:\s+\*?[^\r\n]+)?$`。
- APK 上限 250 MB（262 144 000 B）+ 32 MB 剩余空间余量。
- 流式下载到 `cacheDir/updates/<apkName>.part`，SHA-256 在 UI isolate 之外计算，校验后原子搬移。
- 下载 UA `ShortLink-Android-Updater`；清单 UA `ShortLink-Android/<versionName>`。
- OkHttp 超时 connect 15 s / read 60 s / call 15 min。
- 安装经 `FileProvider`（authority `com.reelshort.app.update-files`），`ACTION_VIEW` + `application/vnd.android.package-archive`，`FLAG_GRANT_READ_URI_PERMISSION | FLAG_ACTIVITY_NEW_TASK`，先显式 `com.android.packageinstaller` 再隐式兜底。
- 安装后清理：立即删除同级文件，APK 延迟 60 000 ms 删除。
- 状态机：`checkForUpdate` 在 Checking/Downloading 期间为 no-op；空 release + 异常 → 手动 `Failed(reason, manual=true, release=null)` / 自动 `Idle`；`downloadAvailable` 仅来自 `Available` 或 `Failed(release != null)`；`dismiss()` 在 Downloading/Verifying 期间无效；`downloadCancelled()` → `Available(release)`；`onInstallPermissionResult` 仅在 `PermissionRequired` 生效；`installationFailed()` → `Failed(INSTALL, manual=true, release)`。
- 失败原因映射：`InsufficientStorage`/`DownloadTooLarge` → STORAGE；`InvalidChecksum`/`SecurityException` → VERIFICATION；`InvalidRelease` → INVALID_RELEASE；HTTP → NETWORK；其他 → DOWNLOAD。
- 弹窗显示判据：Available/Downloading/Verifying/PermissionRequired/ReadyToInstall 为真，`Failed` 取 `release != null`，Idle/Checking/UpToDate 为假。
- `UpdateDialog` 的 `Failed` 状态标题**恒为** `checkFailed`，正文为 `failureMessage(reason)`。

---

## 5. 存储与安全语义对齐

| 关注点 | Kotlin 版 | Flutter 版 |
| --- | --- | --- |
| 会话 | `EncryptedSharedPreferences("reelshort-session")`，键 `username`/`phoneE164`/`token`/`tokenType` | `flutter_secure_storage`，同样的键，同样的「读不到即无会话」语义 |
| 加密不可用 | fail-closed：删除旧明文会话文件，仅保留进程内会话，**绝不**写明文 Token；删除失败则 `check` 失败拒绝启动 | 等价实现：`FailClosedSessionStoreFactory.create` 先删旧明文文件（失败即抛 `StateError`），再退回 `InMemorySessionStore` |
| 记住密码 | `EncryptedSharedPreferences("reelshort-credentials")`；创建失败时退化为"空存储"（读恒 null、写忽略） | 同样的退化语义 |
| 语言偏好 | 明文 `SharedPreferences("reelshort-language")`，键 `locale` | `SharedPreferences`，同样的文件与键 |
| 首页缓存 | `filesDir/home-shelf-cache.json`，写 `.tmp` 再 rename，损坏时自愈删除并返回空列表 | 同等实现（`path_provider` 取目录），原子写与自愈读一致 |
| 自动备份 | `allowBackup="false"` | 同 |
| 明文流量 | `usesCleartextTraffic="false"` | 同 |

---

## 6. 验收与证据

| 项 | 命令 | 通过标准 |
| --- | --- | --- |
| 静态分析 | `flutter analyze` | `No issues found!`（含 info 级） |
| 单元/Widget 测试 | `flutter test` | 全绿（含 `test/ui_wiring_test.dart` 的 5 条宿主接线回归） |
| 契约测试 | `flutter test test/core/app_strings_contract_test.dart test/state/app_ui_state_contract_test.dart` | 216 字段与 47 字段/4 枚举逐项匹配 golden |
| Debug 构建 | `flutter build apk --debug`（需 `ANDROID_SIGNING_*`） | 产物生成，签名校验通过 |
| Release 构建 | `flutter build apk --release` | 产物生成，R8 后运行正常 |
| 签名证书 | `apksigner verify --print-certs` | SHA-256 = `c1a149d5352c5c313d8d7d995470da31a3bd298281abba914e559747bb45c2f9` |
| 真机/模拟器 | `emulator-5554`（API 34） | 冷启动、游客浏览、登录、播放续播、奖励入账、资金中心、自更新入口均可用 |
| CI | `.github/workflows/ci.yml` 的 `android-flutter` job | analyze + test 通过 |

---

## 7. 接线审计修复记录（2026-10-15）

逻辑层 1:1 移植完成后，对照 Kotlin 宿主层（`MainActivity.kt`、`MainShell.kt`、各 Screen）做了一次
UI 宿主接线审计，修复了「状态机在跑、界面没接线」的一批缺口。全部为**向 Kotlin 行为对齐**的修复，
不引入新的刻意差异：

- **自更新宿主**（原 A1/A2/A3）：`main.dart` 新增 `_UpdateHost`，对应 Kotlin `MainActivity` 的
  `UpdateDialog` 常驻组合、`LaunchedEffect(PermissionRequired)` 自动拉起 unknown-sources 设置页、
  返回后 `onInstallPermissionResult()` 自动续装、`UpToDate`/`Failed(release==null)` 的 4 s toast。
  手动检查更新入口落在账户页版本行（`versionTitle` + `versionName (versionCode)` + Check）。
- **系统返回键**（原 A4）：`AppShell` 挂 `PopScope`，PLAYER/FAVORITES/FUNDS/WITHDRAWAL/VIP_RECHARGE
  五个界面按 Kotlin `BackHandler` 语义回上一级而不是退出 App（`backToPlaybackSource` /
  `backToAccount` / `backToFunds`）。
- **全局覆盖层**：消息条、认证面板、VIP 付费弹层从 shell 内部提升到宿主 Stack，覆盖播放器
  （Kotlin zIndex 2 的语义）；去掉 `LOGIN` 屏不显示消息的抑制；消息条以 `messageTrigger` 为 key
  并在消失时 `clearError()`，同文案的连续错误可重新显示。
- **播放器**（原 A-1/A-2/A-3）：补右侧动作栏（点赞/评论/收藏 + `formatCount`）、评论弹层、
  奖励说明弹层与圆环点击；错误覆盖层改接 `video_player` 的初始化/平台错误（原先错接
  `rewardReportError`，视频源损坏会永久转圈），Retry 重建播放器（同 URL 也重建）、错误层补
  Next/Back；奖励上报失败降级为顶部横幅；奖励圆环按 `visualState` 取色、仅在
  `vipActive && estimatedRewardPoints != null` 时显示；补底部信息区（书名/集标题/简介/进度条/
  选集入口）；`rewardClaimed` 后停止采样；后台暂停播放；采样统一走 `playbackProgressSample`
  （floor 语义）；选集格补 VIP 锁与共享 `episodeWatchStatus` 投影，当前集判定按
  `(number, chapterId)`。
- **账户页**（原 A1/A2/A3/A4）：补完整观看历史弹层（行可点续播）；钱包弹层按
  `walletSheetShouldDismiss` 在绑定/更换/**解绑**成功后自动关闭（原先解绑后不关）、补非空门控、
  地址预填、解绑确认弹窗改用 `accountConfirmationTitle/Body` 共享文案并在提交中冻结按钮；
  改密补 `oldPassword.isNotBlank() && newPassword.length >= 8` 门控；补游客 Sign in / Create account
  直达入口，钱包/改密/银行卡入口仅登录可见；续看 pill 改为 Kotlin 的 `size/limit` 语义。
- **提现记录**（原 A4）：行补 `network · address` 与 `withdrawalRecordDetail`（拒绝原因/交易哈希）。
- **VIP 充值**（原 A5）：过期 PENDING 订单倒计时归零后自动落入创建分支（`activePending` 语义），
  不再卡在待支付详情；全局付费弹层 busy 标志补 `accountOperation != null` 项。
- **搜索/首页**：修复搜索框在任意重建时清空未提交输入的缺陷（改为仅 `searchQuery`/语言变化时
  再同步）；补显式 Search 按钮、结果头（`Results` + query + `N dramas`）、发现标签恢复分组标题；
  首页空态改用共享 `homeEmptyState` 文案并接上真实的 "去搜索" 动作。
- **平台细节**：`AnnotatedRegion<SystemUiOverlayStyle>` 按主题亮度设置系统栏图标对比度；
  `_PlayerScreenState`/`_WalletSheet` 的 `dispose` 不再对已失活元素做 inherited 查找。

`test/ui_wiring_test.dart` 固化其中可测的五条：游客账户入口、钱包弹层解绑路径自动关闭、
观看历史弹层、播放器动作栏、Funds 系统返回。

---

## 8. 真机验收发现与修复（2026-10-15，接 §7）

在真机 `c4e4da7e` 上按 §6 清单走验收，`flutter analyze` / `flutter test` 全绿之外又暴露了三个
**只有真机+真实后端才能发现**的缺陷，全部修复：

| # | 现象 | 根因 | 修复 |
| --- | --- | --- | --- |
| R1 | Discover（搜索）Tab 整页空白，logcat 抛 `RenderFlex is not a subtype of RenderSliver` | 发现页的分组标签 Column 被直接放进 `CustomScrollView.slivers`（重写前就存在的结构缺陷，原 `_DiscoveryChips()` 同病；analyze 与无覆盖搜索页的 widget 测试都发现不了） | 包进 `SliverToBoxAdapter`；新增挂载搜索页的冒烟测试锁死回归 |
| R2 | 登录/注册成功后面板重开、会话丢失，Me 页仍是 Guest；logcat 显示登录后全部快照请求 401 且**无 Authorization 头** | `HttpReelShortApiClient` 的 `authenticated` 标志传给了 `_request()` 但从未传到 `_send()`——**所有 GET 类鉴权接口自重构以来从未带过 token**（Fake 数据源的 JVM 测试完全绕过 http 客户端，故全绿） | `_request` 改为返回 `(request, authenticated)` record，`_send` 解构取用，标志不再可能被丢弃；`integration_test/acceptance_test.dart` 在真机+真实后端锁定：登录稳定、`openBook` 拿到真实播放地址（38 集） |
| R3 | （防御性）冷启动 `restoreSession` 的安全存储读取在真机首访需数秒，若用户在其完成前完成登录，迟到的恢复会用持久化（可能为空）会话覆盖刚建立的新 token | Kotlin 端存储快从未触发，属移植继承的并发窗口 | repository 层：`currentToken != null` 时 restore 不再覆盖；controller 层：恢复完成时已登录则不覆盖 session/凭据 |

另外两条真机观察记档：

- **游客点海报 → 停留原页弹出认证面板**（契约规则 39）与**覆盖层宿主**在真机上验证通过；
  注册流程的表单门控（验证码 4 位上限、错误验证码全局错误条、答错作废）行为与契约一致。
- `flutter test integration_test` **每次运行结束会卸载应用**，数据随之清空——凡「冷启动恢复为
  Guest」的观察先排除这一点再查存储；跨进程会话持久化需人工安装后登录→杀进程→冷启动复核。

`integration_test/acceptance_test.dart` 为长期真机验收资产（真实后端、真实对象图），
带签名环境变量运行：`flutter test integration_test -d <device>`。

---

## 8.1 首轮 UI 评审打磨（2026-10-15，接 §8）

真机验收通过后对重构 UI 做了一轮评审，以下不足已修复（验收账号凭据同步改为
`--dart-define` 注入，不再出现在任何源码里）：

| # | 不足 | 修复 |
| --- | --- | --- |
| P1 | 首页/搜索/继续观看的标题渲染成 `filteredTitle` 搜索 slug（如 `the-alpha-s-forbidden-bunny`）——对 Kotlin `PosterCard` 用 `book.title` 的**视觉回归** | 全部展示位改回 `book.title`；继续观看回退链与 Kotlin 一致（`title` 非空 → `record.bookTitle`） |
| P2 | 播放器底部信息区固定 16px 底边距，全面屏手势条压住选集入口 | 底部信息与右侧动作栏均按 `MediaQuery.viewPaddingOf.bottom` 抬升 |
| P3 | 播放器无任何播放控制 | 新增：点击暂停/恢复（暂停时显示居中播放按钮）；中间区域竖滑切上/下一集（列表顺序，阈值 80px 或速度 400）；左侧 1/4 区域竖滑调亮度、右侧 1/4 调音量（带居中指示胶囊，`screen_brightness` 应用级亮度、退出播放器恢复系统值） |
| P4 | 双击点赞缺失 | 双击视频表面触发点赞（mediumImpact 触感） |
| P5 | 首页/资金/提现/VIP 页标题贴状态栏 | 各页补 `SafeArea(top)`；资金页 SafeArea 提到 Column 外层使标题一同内缩 |
| P6 | 海报 → 播放器直切无过渡 | 播放器并入 shell 的 AnimatedSwitcher，入场 slide-up + 淡入、退出反向 |
| P7 | 奖励圆环点击目标 44px | 放大到 48dp（圆环视觉保持 44） |
| P8 | 下拉刷新指示器为平台默认配色 | 使用设计系统的 `accentPrimary`/`surface` |
| P9 | 点赞/收藏无触感反馈 | 切换时 `HapticFeedback.selectionClick` |
| P10 | 评论弹层固定 0.7 屏高，键盘弹出时顶部可能被裁 | 高度按键盘内边距收缩（`min(0.7h, h - inset - 120)`） |

进度条可拖动快进**刻意未做**（Kotlin 版同样无此能力，属后续功能而非对齐范畴）。
真机复核：标题人类可读、播放中画面、点击暂停出现播放按钮、上滑切到 EP 02、底部选集栏
完整位于手势条上方，均截图确认。

---

## 9. 相关文档

- 设计系统与主题：[design-system.md](design-system.md)
- 状态机逐方法对照与中文→英文文案映射：[controller-parity.md](controller-parity.md)
- 构建环境与命令：[toolchain.md](toolchain.md)
- 签名与发布：[signing.md](signing.md)
