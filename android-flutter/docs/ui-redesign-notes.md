# UI 重设计工作笔记（Home 除外）

本文件记录 UI 重设计实施过程中的**现场证据与决定**，避免下一次会话丢失上下文。
设计与执行计划见 `docs/plans/2026-10-16-ui-redesign-phase2-phase3.md`；
令牌与组件契约见 `docs/design-system.md`。

## 1. 阶段状态

| 阶段 | 状态 | 提交 |
| --- | --- | --- |
| Phase 0 工具链 + 截图基线 | 完成 | `f1d7a43` |
| Phase 1 设计系统 v2 令牌 | 完成 | `3025683` |
| Phase 2 组件库（核心） | 完成 | `fb98463` |
| Phase 2 收尾（骨架变体、字段/空态契约、spine、消息条） | 完成 | `bc33093` |
| Phase 3 声明式路由 + 全屏子页外壳 | 完成 | `bc33093` |
| Phase 4 逐屏重设计 | 未开始 | — |
| Phase 5 状态作用域化 + 反馈闭环 | 未开始 | — |
| Phase 6 测试/真机验收/文档 | 未开始 | — |

## 2. 截图基线（`artifacts/` 已 gitignore，仅本机）

- `artifacts/ui-review/baseline-before-light-1.0/`：改造前 9 屏（guest 态，light/1.0），
  `01-home` 至 `09-home-poster-tap`，含 `manifest.json`（每张记录当时焦点窗口）。
  **已人工确认**：`04-account`（Me 页 guest 态）、`01-home`（海报栅格）。
- `artifacts/ui-review/baseline-after-login/`：登录态（账号 `ashu1801` 已在本机设备登录）。
  **已人工确认**：`funds.png`。`withdrawal.png` 与 `vip-recharge.png` 字节数相同，
  说明定位点击未命中目标屏（需重新探测坐标后再抓，不要在 Phase 4 之前当作证据）。
- `artifacts/ui-review/phase3/`：Phase 2 收尾 + Phase 3 的真机 smoke 证据（见 §6.3）。

重新抓取：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/android-ui-review.ps1 -SkipInstall `
  -OutputDir artifacts/ui-review/phase4-check
```

脚本自带三层防护：唤醒设备并解除锁屏、启动后校验前台应用、每次交互前再校验；
非 `com.reelshort.app` 前台时**拒绝点击**（此前一次抓图因设备熄屏把点击打到了别的应用）。

## 3. 现场缺陷台账（改造前发现 + 本轮新增）

1. **账户身份卡文案错乱** —— 已在 `d9e6419` 修复：`ashu180 / VIP active · 2026-11-13`，
   真机 `03-account.png` 复核。
2. **改密弹层串了注册文案** —— 已修复：改密只需旧密码与新密码，提示走
   `AppUiCopy.passwordMinimumHint`（`At least 8 characters`）。
3. **资金中心区块标题被底部导航栏截断** —— Phase 3 已为子页统一预留
   `AppSpacing.pageBottomInset`；Funds 仍是自绘页面（Phase 4 收口到同一外壳）。
4. **金额未做千分位与本地化** —— 已修复：`groupedDigits`（真机显示 `29,187`）。
5. **页标题层级不一致** —— 子页已统一为 `AppTypography.screenTitle`（`AppSubPage`），Phase 4-1 的
   Me 页也从 26px `displayLarge` 收敛到 22px `screenTitle`；Funds 页仍是 `titleLarge`，随 Phase 4-2 收敛。
6. **骨架宽度为 0，加载态不可见（本轮真机发现并修复）** —— `AppSkeleton` 的
   `SizedBox(width: widget.width)` 在宽松约束下取宽 0，Home / Favorites / Funds 三处
   `AppSkeletonList` 的加载态一直是纯空白；修法 `widget.width ?? double.infinity`
   + 「父容器必须有界」文档约定，回归用例锁定 328/324 宽（见 §6.3）。
7. **Discover 搜索页在 320px + 3.2× 字号下横向溢出**（`search_screen.dart` 的
   `_DiscoveryGroups`，实测 257/353/305 px 四处 `RenderFlex overflow`）—— 本轮未改该文件，
   留给 Phase 4（design-system §7.8 的「320→1440、任意字号不裁切」承诺在 Discover 页不成立）。
8. **上游错误原样透传**（本轮真机偶遇）—— 生产入口 `shortlink.hjj888.cc` 返回
   Cloudflare 525 时，消息条显示的是整段 Cloudflare JSON（`title/detail/ray_id/...`）。
   属既存错误透传行为，不是本轮改动；Phase 5 的「反馈闭环」应收口为人类可读文案 + 重试入口。
9. **详情页不可达**（parity gap G1）：`AppScreen.detail` 从不被状态机赋值，Phase 3 的
   `AppRouteDestination.detail` 仍是 HomeScreen 占位；Phase 4 接入详情页时需同时给它
   真实返回目标（见 `docs/parity-matrix.md` §8.2「已知偏差」）。

## 4. 实施决定

- **`AppStrings` 不可手改**：它是 `tool/port_app_strings.py` 从 Kotlin 端生成的，
  要求与 Kotlin 逐字一致。重设计新增文案一律进 `lib/core/formatting/app_ui_copy.dart`。
- **工具链**：官方 Dart/Flutter MCP 已在 DSH 面板注册，但 **MCP 工具目录在会话启动时固定**，
  需要重开会话才会出现 `dart` 工具组；在此之前用 `flutter analyze` / `flutter test` /
  `flutter run -d c4e4da7e` / `adb` 截图兜底（见 `docs/agent-tooling.md`）。
  真机排障可加一条捷径：debug 包会打印 Dart VM service URI，`adb forward` 之后用
  `ext.flutter.debugDumpApp` 直接 dump widget 树（本轮定位骨架缺陷即用此法）。
- **不采用** `mcp_flutter`、`Android-Ui-MCP`、社区设计技能（理由见 `docs/agent-tooling.md`）。
- **测试账号**只用于本机验收；凭据不得写入仓库、脚本、文档或提交信息。
  设备上已存在登录会话，改版期间不要卸载应用（卸载会清掉会话与本地偏好）；
  `adb install -r` 覆盖安装不会丢会话。
- **Home 不重设计**：只随共享令牌做等值替换，并用基线截图做回归比对。

## 5. 下一步起点（Phase 4）

按 `docs/plans/2026-10-16-ui-redesign-phase2-phase3.md` 的分工继续逐屏重设计，建议顺序：

1. **详情页**（含 G1 可达性与返回目标）：接入 `EpisodeSpine.full`（44px 命中区已就绪）与
   `AppSubPage` 外壳。
2. **账户页 / 资金页**：身份卡、快捷入口、继续观看卡片改走 `AppListRow`/`AppSectionCard`；
   Funds 收口到 `AppSubPage` 外壳并统一 `screenTitle`。
3. **播放器**：选集抽屉接入新 spine；奖励圆环与浮层继续用现有投影。
4. **搜索页**：修 §3 第 7 条的溢出，并接入骨架变体（`AppSkeletonPosterGrid`）。
5. **首次加载态统一**：三处 `AppSkeletonList` 换成与到达内容同形的变体
   （Home → `AppSkeletonPosterGrid`、Favorites/Funds → `AppSkeletonList` + `AppSkeletonText`）。

## 6. Phase 2 收尾 + Phase 3 落地记录（2026-10-16）

### 6.1 交付

- **Phase 2 收尾**：`AppSkeletonText` / `AppSkeletonCircle` / `AppSkeletonPosterGrid` 三个变体；
  `AppEmptyState` pill 契约（状态不是控件、优先级、无死按钮、3.2× 换行）；
  `AppTextField` 内置显隐切换（≥44px、不抢焦点、不清内容）+ 点击外部收键盘（可关闭）；
  `EpisodeSpine.full` 交互 tick 44×44 命中区（行距同步、视觉几何不变）+ `describe()` 本地化；
  `AppMessageBar` 语调词/关闭 tooltip 走 `AppUiCopy` + 关闭按钮 ≥44×44。
- **Phase 3**：`lib/ui/routing/app_route_stack.dart`（纯函数契约：页面栈、返回优先级、被弹页到
  控制器调用的映射）、`app_router_pages.dart`（Page/Widget 映射 + `RootTabSwitcher` 交叉淡入 +
  `PlayerPage` 上滑转场）、`AppSubPage` 全屏子页外壳、认证面板/VIP 付费层改为 root navigator
  模态路由、全局消息条宿主上移到 `MaterialApp.builder`（root Navigator 之上，自带透明 Overlay）。

### 6.2 验证

- `flutter analyze`：0 issue（写作者、verifier 各自复跑）。
- `flutter test`：verifier 全量 **147 用例全绿**（Phase 3 交付时）；骨架修复后写作者 +
  verifier 增量合计 40 条 `design_system_test` 与 17 条 `navigation_shell_test`。
- verifier 两轮对抗式复核（task-3 / task-7）各自独立取证，抓到并修掉两个真实缺陷：
  `AppTextField` 的 `selectAllOnFocus` 监听器归属（use-after-dispose）、返回派发与同轮切 Tab
  的 microtask 竞态；并纠正了「`onDidRemovePage` 会因 `pages` 收缩回调」的错误认知
  （框架只在命令式移除时回调）。
- 临时断言文件全部删除，`git status` 无验证残留。

### 6.3 真机 smoke（c4e4da7e，1080×1920，Android 8.1，开发者选项关闭动画）

证据在 `artifacts/ui-review/phase3/`：

| 截图 | 结论 |
| --- | --- |
| `01-home` / `19-back-from-player` | 首页海报与封面正常；播放器返回落在播放来源（Home） |
| `03-account` | Me 页身份卡 `ashu180 / VIP active · 2026-11-13`、金额千分位 |
| `11-withdrawal` | 子页外壳生效：返回箭头 + `Withdraw`（`screenTitle`）+ 内容，底部栏保留 |
| `12-back-to-funds` / `14-back-once` | 返回链：Withdrawal → Funds → Me（Kotlin `BackHandler` 契约） |
| `15-skeleton-t06` | 修复后加载态出现 5 条满宽 `surfaceAlt` 骨架（修复前该区域只有 canvas） |
| `17-funds-loaded` | 数据到达后内容替换骨架，无布局跳动 |
| `16-skeleton-t26` | 偶遇生产入口 Cloudflare 525，消息条正确浮在内容之上（错误文案本身见 §3 第 8 条） |

排障手法（可复用）：debug 包 `adb logcat` 里的 Dart VM service URI + `adb forward` +
`ext.flutter.debugDumpApp` 可以直接看到那一刻的 widget 树，比只看截图快得多。

## 7. Phase 4 逐屏重设计

Phase 4 才是**外观真正变化**的阶段：前四个阶段（令牌/组件/收尾/路由）都是等值替换与底座，
所以「界面看起来和之前一样」在 Phase 4 之前是预期结果。第一屏选 **Me（账户页）**，
做完在真机截图对比后再决定后续屏幕顺序。

### 7.1 Me 页重设计规格（冻结，2026-10-16）

现状问题：身份卡、快捷入口、客人入口、钱包行、静态行、版本行全是各自的 `Container` 手写卡片
（Phase 2 的 `AppPanel`/`AppSectionCard`/`AppListRow` 一个没用），入口是 3 张全宽大卡 +
4 处 `SizedBox(height: 16)` 硬编码间距，标题用 `displayLarge` 而子页用 `screenTitle`。

目标结构（自上而下，全部走令牌与组件，禁止裸 px/裸色）：

1. **页面标题**：`AppTypography.screenTitle` + `copy.accountTab`，页面内边距用
   `appSpacingOf(context).pagePadding`（= 现在的 18/12/18/28）。
2. **身份 hero**（用令牌直接搭建，不套 `AppPanel`：`AppPanel` 只能给 premiumSurface + `hairline`
   描边，给不出规格要求的 `accentPrimary` + `strokeEmphasis` 重点描边）：
   - 登录态：VIP 用 `premiumSurface` 底 + `accentPrimary` 描边，非 VIP 用
     `surface` + `hairline`；头像 56px（VIP `accentPrimary`/`onAccent`，否则
     `surfaceAlt`/`textPrimary`）；用户名 `displayLarge`；VIP 状态做成 chip
     （`accentSuccess`/`accentWarning` 14% 底 + 图标 + `accountVipStatusActive`/`Inactive`），
     有效期用 `accountVipExpiryPrefix` + `dateOnlyLabel(state.vipUntil)`；
     **账号快照未到达时不得断言「VIP not active」**（VIP 状态未知 → 中性占位，避免冷启动误报）。
   - 游客态：同一 hero 放 `accountGuestUser` + `accountGuestStatus`，下面两个 `isExpanded` 按钮：
     主 `accountGuestSignIn`（Sign in）、次 `accountGuestRegister`（Create account，行为同现在的
     `showRegisterAuthMode()` + `showAuthPrompt()`）。`ui_wiring_test` 依赖这两个文案，
     必须保持「Sign in 至少一处、Create account 恰好一处」；monogram 回退到
     `accountGuestUser` 首字母（不是 `?`）。
3. **快捷入口**（仅登录态）：`AppSectionHeader(AppUiCopy.accountShortcutsSection)` +
   一个 `AppSectionCard` 内 3 行 `AppListRow`（Favorites / Watch history / Change password，
   `leadingIcon` + `showChevron`），点击行为与文案保持现状。
4. **继续观看**（登录态且 `watchHistory` 非空）：`AppSectionHeader(accountRecentWatchTitle)` +
   右侧保留 `size/limit` pill；卡片轨道沿用 `PosterCard`（136 宽）与
   `accountContinueWatchingLimit()`，行为不变。
5. **设置区**：`AppSectionHeader(AppUiCopy.accountSettingsSection)` + 一个 `AppSectionCard`：
   钱包行（登录态；副标题 = 地址或 `accountWalletNetworkHint`，**地址单行省略号**，尾部
   `TextButton` 文案仍是 `accountWalletBindAction`/`accountWalletReplaceAction` 且不折行，
   保证 `ui_wiring_test` 的 `find.text('Bind wallet')` 可点）、银行卡只读行（登录态）、
   客服邮箱行（`SelectableText` 长按复制；`AppUiCopy.accountSupportEmailCopied` 的显式复制反馈
   属 Phase 5「反馈闭环」，本轮不做）、版本/更新行（保留 `_VersionRow` 的检查更新行为）。
   **整卡不得塌成一个语义节点**：每行自成节点，且不可点行的 trailing 控件（Check、邮箱）
   必须保留可读/可点的语义节点（`AppListRow` 的语义契约，见 §7.2）。
6. **退出登录**（登录态）：`AppButton(variant: destructive)`，文案 `accountSignOutTitle`，
   仍不做二次确认（parity）。

不做的事：不新增业务入口（不加积分/提现入口）、不改 4 个弹层（观看历史/改密/钱包/确认）的行为、
不改 `AppStrings`（生成物）、不动 shell/路由。

### 7.2 AppListRow 语义契约（本轮修复）

`AppListRow` 对「无 `onTap` 的行」原来用 `Semantics(label:, excludeSemantics: true)` 且
`container: false`，把整行的后代语义全部替换成一条标签。单独一行没问题，但一旦和可点行同装进
`AppSectionCard`，整张卡会被合并成**一个**语义节点：客服邮箱与版本行的 Check 按钮在 TalkBack 下
既读不到也点不到（Me 页重设计首次把可点/不可点行混装，暴露了这条既存缺陷）。

契约（修复后，`design_system_test.dart` 锁定）：

* 每一行（无论是否可点）都是**自己的**语义节点（`container: true`），卡片不会塌成一条；
* 可点行的 label + `onTap` 挂在行节点上，行内 `InkWell` 用 `excludeFromSemantics: true`，
  避免出现第二个节点；
* 行内的说明文字（leading + title + subtitle）用 `ExcludeSemantics` 包住，由行节点的 label
  统一播报（不重复朗读）；
* `trailing` 交互控件（按钮等）**不被**行节点的语义排除，保留自己的可读/可点节点；
  不可点行不得暴露任何 tap 动作。

### 7.3 Phase 4-1 结果（Me 页）

- 结构：`screenTitle` 页标题（22px，原 26px `displayLarge`）+ `pagePadding` → 身份 hero →
  `SHORTCUTS`（一张卡 3 行 `AppListRow`）→ `CONTINUE WATCHING`（`size/limit` pill + PosterCard 轨道）
  → `SETTINGS`（钱包 / 银行卡 / 客服 / 版本四项一张卡）→ destructive 退出登录；`account_screen.dart`
  +361/−418 后继续下调，手写 `Container` 卡全部换成组件与令牌（新增行零裸色值、零硬编码间距）。
- 验证：`flutter analyze` 0 issue；`flutter test` 全量 166+ 用例全绿；verifier 四轮对抗式复核
  （task-11/13/16 与语义专项）抓到并修掉：**AppListRow 语义塌陷**（中等，混装可点/不可点行导致整卡
  一个节点、邮箱与 Check 在 TalkBack 下消失）、hero **VIP 未知态误报**（冷启动先显示
  "VIP not active"）、游客 monogram 显示 `?`、钱包地址折 6 行、钱包动作 `Replace wa…` 截断、
  客服邮箱在 360dp 下断词折行。修复后 hero 占位与真 chip 的高度差：1× +2.0px / 2× −7.0px /
  3.2× −16.4px（原 +166px）。
- 真机：登录态截图来自小米真机 `c4e4da7e`（`artifacts/ui-review/phase4/27-me-fixed-top.png` 等），
  游客态与交互来自**雷电模拟器**（Android 14 / 720×1280 @320dpi = 360×640dp，
  `artifacts/ui-review/ldplayer/`）：guest hero、模态认证面板（含密码显隐）、Funds 游客页、
  Android 14 预测性返回（面板先关、Funds→Me 落点一致）、首页骨架可见。
- 已知取舍与后续项：
  1. `AppListRow.trailingTakesIntrinsicWidth`（默认 false）目前只有钱包行开启；开启后 trailing 取
     固有宽度，并被 **行宽的一半** 封顶（不是「自由空间的一半」，LayoutBuilder 在 padding 之外，
     按后者理解会少算 16px），`textScaler > 1.6` 时自动退回 1:1 分摊。标签型动作建议开启，
     值型 trailing 保持默认；值型长文本必须放 subtitle，否则会被 1:1 分摊挤到断词——客服邮箱就是
     这么修的。**宽度口径**：真 Roboto 下 `Replace wallet` 95.67px / `Bind wallet` 72.68px，
     360dp 与 320dp 都完整渲染；widget 测试的等宽测试字体约为 Roboto 的 **2.1 倍**宽，纯测试里
     会误判成截断，判断真机需载入真实字体或做解析式测算。
  2. 客服行用了屏幕内私有 `_SupportRow`（`AppListRow.subtitle` 只接受 `String`，装不下
     `SelectableText`）。**硬约束：出现第二个消费者时必须抽取，不得第三次复制**——给
     `AppListRow` 加 `subtitleWidget` 槽后换回共享行；抽取时必须同时补「widget subtitle 保留自身
     语义节点、行节点不再合并它」的测试与 §7.2 段落（subtitle 可以自带语义等于给共享组件引入
     第二种语义模式，正是 §7.2 塌陷缺陷的同源风险）。
  3. `SelectableText` 无 `overflow`：极端字号下邮箱是裁剪而非省略号（`maxLines: 1` 保证不折行）。
  4. 3.2× 下 VIP chip 标签视觉省略（语义仍播报完整文案），这是把高度差压到 ≤40px 的代价。
  5. 四个弹层内部（观看历史/改密/钱包/确认）仍有 16 处历史硬编码间距，留待 Phase 4/5 清理。



