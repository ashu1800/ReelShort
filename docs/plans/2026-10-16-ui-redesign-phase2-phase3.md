# UI 重设计 Phase 2 收尾 + Phase 3 实施计划

- 日期：2026-10-16
- 上游计划：`android-flutter/docs/ui-redesign-notes.md` §1 阶段表与 §5「下一步起点」
- 契约文档：`android-flutter/docs/design-system.md`、`android-flutter/docs/parity-matrix.md`
- 代码根：`android-flutter/`（Flutter 3.47.7 / Dart 3.13.5）

## 1. 目标

完成阶段表中未开始的两行：

1. **Phase 2 收尾**：补齐组件库在 `ui-redesign-notes.md` §5 点名的五处缺口，使 Phase 4 逐屏重设计
   不再需要「边做屏幕边补组件」。
2. **Phase 3**：把 shell 从「按 `state.screen` 命令式切换」改成「由状态推导的声明式页面栈」，
   引入全屏子页外壳，并让系统返回键具备明确的优先级。

验收总纲：`flutter analyze` 零问题、`flutter test` 全绿（含新增用例）、`AppStrings` 保持生成物不变、
Phase 2 新增/修改的组件不存在硬编码的用户可见英文（文案只来自 `AppStrings` 或 `AppUiCopy`）。

## 2. 非目标

- 不做 Phase 4 的逐屏重设计（Home 结构、账户页版式、播放器手势等一律不动）。
- 不引入第三方路由包（`go_router` 等）；用 Flutter 自带 `Navigator` 的声明式 `pages` API。
- 不改状态机语义（`tool/port_app_ui_state.py` 生成的 `AppUiState` 与 Kotlin 1:1 对齐，不手改）。
- 不改后端、admin-web、content-provider、Android Kotlin 版。

## 3. Phase 2 收尾（组件库 5 项）

| # | 项目 | 现状 | 交付 |
| --- | --- | --- | --- |
| 1 | `AppSkeleton` 变体 | 只有单块 `AppSkeleton` 与 `AppSkeletonList` | 新增按到达内容成形的变体：多行文本（末行短）、圆形头像、自适应海报网格；全部继承减少动效下的静态 `surfaceAlt` 平板语义 |
| 2 | `AppEmptyState` pill 契约 | pill 为私有实现，契约（状态而非控件、无点击动作、无焦点）只在文档里 | 用测试锁死契约：无 `onTap`/无焦点/单次播报、`pillLabel` 优先于 `actionLabel`、`onAction` 非空但 `actionLabel` 空白时**不得**渲染死按钮、3.2× 字号下换行不裁剪 |
| 3 | `AppTextField` 密码显隐与键盘收起 | 只有 `obscureText` 布尔，弹层里的密码框没有显隐开关；键盘只能靠 done/go 收起 | 内置显隐切换（≥44px、语义标签走 `AppUiCopy`、不抢焦点、不清空内容）；`onTapOutside` 默认收起键盘（移动端与桌面端一致），done/go 收起保持 |
| 4 | `EpisodeSpine` 44px 命中区与本地化 | `full` 行高 16px、行宽约 23px，命中区远小于 44px；`describe()` 里硬编码 `' in progress'` | 交互型 `full` 行改为「视觉几何不变、行距/行宽提升到 ≥44px」的命中区（与相邻 tick 不重叠）；进度态文案改走 `AppUiCopy` |
| 5 | `AppMessageBar` 文案与关闭按钮命中区 | 语调词与 tooltip 硬编码英文；`IconButton` 用 `compact` 密度，命中区小于 44px | 语调词/关闭 tooltip 改走 `AppUiCopy`；关闭按钮命中区 ≥44×44，视觉仍紧凑 |

统一约束：任何新增/修改组件都必须进 `test/design_system_test.dart` 的渲染矩阵（亮/暗 × 320/1440px ×
1×/2×/3.2× 零布局异常），不得引入裸色值（`AppColors` 之外），不得让动画绕过
`MediaQuery.disableAnimations`。

## 4. Phase 3：声明式路由 + 全屏子页外壳 + 弹层返回优先级

### 4.1 路由契约（Lead 所有）

新增 `android-flutter/lib/ui/routing/app_route_stack.dart`（纯函数，无 widget 依赖）：

- `shellRootDestination(state)`：当前屏所属的一级 Tab。`player` 取 `playerReturnScreen`；`favorites`
  归 `account`，`withdrawal`/`vipRecharge` 归 `funds`。
- `shellSubPageDestinations(state)`：压在一级 Tab 之上的子页，0 或 1 个。
- `buildRouteStack(state)`：`[root, ...subPages]`，`pageKey` 稳定（`shell-root` / `shell-page-<dest>`）。
- `resolveBackOutcome(state)`：返回键优先级 —— `authPromptVisible` → 关认证面板；否则
  `vipPaywallVisible` → 关付费层；否则有子页 → 弹栈；否则 `screen == funds` → 回账户（Kotlin
  `BackHandler`）；否则交给系统退出。
- `shellExitForPoppedDestination(dest)`：被弹出的页面映射到 `backToAccount` / `backToFunds` /
  `backToPlaybackSource`；一级 Tab 返回 null，表示该移除是状态驱动而非用户返回。

纯函数部分由 `android-flutter/test/routing_contract_test.dart` 覆盖，不 pump widget。

### 4.2 Shell 接线（routing 写作者）

- `AppShell` 的 body 换成声明式 `Navigator(pages: buildRouteStack(state) ...)`：
  - 一级 Tab 是唯一的 root page，切 Tab 是**替换** root，不压栈（底部栏高亮判据保持
    `state.screen == tab`）；
  - `favorites` / `withdrawal` / `vipRecharge` / `player` / `detail` 是真正的 pushed page，
    因此自动获得 `appTheme` 里已安装的 reel-change 页面转场（`player` 保留上滑进入）；
  - `onDidRemovePage` 只对「用户返回」派发控制器调用：当被移除页的 key 仍在本帧期望栈里时才派发
    （区分状态驱动收缩，避免点 Tab 时误触发 `backToFunds`）。
- 全屏子页外壳：新增 `lib/ui/components/app_sub_page.dart`，统一提供返回触达（≥44px、语义
  `AppUiCopy.back`）、`screenTitle` 角色标题、安全区、`AppSpacing.pageBottomInset` 预留与可选
  `actions`。`withdrawal` / `vipRecharge` / `favorites` 由 shell 统一包壳，屏内自绘 header、
  私有 `_pagePadding`/`_titlePadding` 一并删除。
- 弹层路由化：`authPromptVisible` / `vipPaywallVisible` 仍由状态机持有，但呈现改为**模态路由**
  （`AppSheet.show`，root navigator）。这样系统返回键先关弹层（路线本身接管），焦点陷阱、
  scrim、`scopesRoute` 语义同时到位；shell 的 `resolveBackOutcome` 作为状态与路由之间的兜底。
- `PopScope` 由 `resolveBackOutcome` 驱动：`exitApp` 才 `canPop: true`，其余情况在回调里执行对应
  动作（关弹层 / `Navigator.maybePop` / `backToAccount`）。

### 4.3 验收

- `flutter test test/navigation_shell_test.dart`（新增）覆盖：返回键矩阵（弹层 > 子页 > Tab 根）、
  子页返回落点、Tab 切换不触发返回动作、`ui_wiring_test.dart` 原用例继续通过。
- 真机 smoke（可选）：`scripts/android-ui-review.ps1` 抓子页与返回转场截图，人工复核。

## 5. 分工与写作用域（互不重叠）

| 角色 | 写作用域 |
| --- | --- |
| Lead | `lib/ui/routing/app_route_stack.dart`、`test/routing_contract_test.dart`、`docs/**`、`AGENTS.md`、终验 |
| components 写作者 | `lib/ui/components/{app_loading,app_empty_state,app_text_field,episode_spine,app_message_bar}.dart`、`lib/core/formatting/app_ui_copy.dart`、`test/design_system_test.dart` |
| routing 写作者 | `lib/ui/app_shell.dart`、`lib/ui/routing/app_router_pages.dart`、`lib/ui/components/app_sub_page.dart`、`lib/ui/screens/{favorites,withdrawal,vip_recharge}_screen.dart`、`lib/ui/sheets/*`（仅在必需时）、`test/navigation_shell_test.dart` |
| verifier | 只读；临时断言文件写在 `test/verify/` 并在完成前删除或明确保留 |

## 6. 风险与回退

- **声明式路由回归风险**：`ui_wiring_test.dart` 的返回键与文本断言是护栏；若页面栈方案在真机上出现
  闪烁或返回落点错误，回退方式是把 `AppShell` 的 body 换回 `AnimatedSwitcher`，保留
  `app_route_stack.dart` 与 `AppSubPage`（两者可独立使用）。
- **弹层路由化风险**：认证面板的状态机语义（关闭时清待播集数、重置模式）不变，只是宿主从
  `Stack` 子节点改为模态路由；若模态路由与键盘 inset 冲突，回退为 `Stack` 子节点并保留
  `resolveBackOutcome` 的关弹层分支。
- **命中区扩大风险**：`EpisodeSpine.full` 行距从 16px 提到 44px 会让长剧列表更高，属预期取舍；
  迷你/内联尺寸不受影响。

## 7. 实施结果（2026-10-16）

- 代码提交：`bc33093`（`feat(android-flutter/ui): Phase 2 收尾组件契约与 Phase 3 声明式路由`），
  19 个文件、+3689/−537，只含 `android-flutter/lib` 与 `android-flutter/test`。
- 验收：`flutter analyze` 0 issue；`flutter test` 全量 **149 用例全绿**
  （`routing_contract_test` 9 + `navigation_shell_test` 17 + `design_system_test` 40 + 其余既有套件）。
- 独立验证：verifier 三轮对抗式复核（task-3 / task-7 / task-9）自己写 90+ 条临时断言取证，
  抓到并修掉两个真实缺陷——`AppTextField` 的 `selectAllOnFocus` 监听器归属（use-after-dispose）、
  返回派发与同轮切 Tab 的 microtask 竞态；并纠正「`onDidRemovePage` 会在 `pages` 收缩时回调」
  的错误认知（框架只在命令式移除时回调）。
- 真机 smoke（`c4e4da7e`，1080×1920，Android 8.1，开发者选项关闭动画）：
  子页外壳（返回箭头 + `screenTitle` + 底部栏保留）、返回链 Withdrawal→Funds→Me、
  播放器返回落在播放来源、加载态骨架满宽可见，均已截图；顺带用
  Dart VM service `ext.flutter.debugDumpApp` 定位到 `AppSkeleton` 在 `width == null` 时宽 0
  的既存缺陷并修复（Home/Favorites/Funds 三处加载态此前一直是纯空白）。
- 真机偶遇生产入口 `shortlink.hjj888.cc` 返回 Cloudflare 525，消息条把整段 Cloudflare JSON
  原样透传——既存行为，按 Phase 4/5 遗留项记档在 `android-flutter/docs/ui-redesign-notes.md` §3。
- 未执行：Phase 4 逐屏重设计、Phase 5 反馈闭环、Phase 6 的完整验收脚本
  （`scripts/android-ui-review.ps1` 的固定场景未整套跑，改用针对性 adb + 截图取证）。

