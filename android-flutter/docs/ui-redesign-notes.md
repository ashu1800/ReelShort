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
5. **页标题层级不一致** —— 子页已统一为 `AppTypography.screenTitle`（`AppSubPage`）；
   Me 页（26px `displayLarge`）与 Funds 页（`titleLarge`）仍是 Phase 4 的收敛项。
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
