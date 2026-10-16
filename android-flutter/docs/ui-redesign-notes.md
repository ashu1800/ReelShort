# UI 重设计工作笔记（Home 除外）

本文件记录 UI 重设计实施过程中的**现场证据与决定**，避免下一次会话丢失上下文。
设计与执行计划见 `AGENTS.md` 变更历史与本文档；令牌与组件契约见 `docs/design-system.md`。

## 1. 阶段状态

| 阶段 | 状态 | 提交 |
| --- | --- | --- |
| Phase 0 工具链 + 截图基线 | 完成 | `f1d7a43` |
| Phase 1 设计系统 v2 令牌 | 完成 | `3025683` |
| Phase 2 组件库（核心） | 完成 | `fb98463` |
| Phase 2 收尾（骨架变体、字段/空态契约、spine、消息条） | 未开始 | — |
| Phase 3 声明式路由 + 全屏子页外壳 | 未开始 | — |
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
  `favorites.png`/`poster-tap.png` 未确认。

重新抓取：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/android-ui-review.ps1 -SkipInstall `
  -OutputDir artifacts/ui-review/phase4-check
```

脚本自带三层防护：唤醒设备并解除锁屏、启动后校验前台应用、每次交互前再校验；
非 `com.reelshort.app` 前台时**拒绝点击**（此前一次抓图因设备熄屏把点击打到了别的应用）。

## 3. 已确认的现场缺陷（改造前，均可在 Phase 4 修掉）

1. **账户身份卡文案错乱**：Me 页身份卡副标题渲染为
   `Instance of 'AppStrings'.accountVipStatusActive · 2026-11-13 00:00:00`
   ——既泄漏了对象 `toString()`，又把字段名当文案拼了出来；同一处的到期时间是未格式化的原始时间戳。
2. **改密弹层串了注册文案**：改密面板底部提示为
   `Pick a username, set a password of at least 8 characters, and solve the captcha.`
   ——改密只需要旧密码与新密码，不应提示选用户名或解验证码。
3. **资金中心区块标题被底部导航栏截断**：`Point records` 标题在首屏被压到导航栏下方半个字高
   （列表未为底部栏预留 `pageBottomInset`）。
4. **金额未做千分位与本地化**：`Total points 29184`、`Available points 29184` 直接渲染原始整数。
5. **页标题层级不一致**：Me 页 26px（`displayLarge`）、Funds center 16px（`titleLarge`）
   ——已由 Phase 1 的 `screenTitle` 角色收敛，Phase 4 落到各屏。
6. （审计已记录，待验证）播放器浅色主题下选集格「近黑字压近黑底」；详情页不可达；
   收藏页固定两列 + 不可滚动。

## 4. 实施决定

- **`AppStrings` 不可手改**：它是 `tool/port_app_strings.py` 从 Kotlin 端生成的，
  要求与 Kotlin 逐字一致。重设计新增文案一律进 `lib/core/formatting/app_ui_copy.dart`。
- **工具链**：官方 Dart/Flutter MCP 已在 DSH 面板注册，但 **MCP 工具目录在会话启动时固定**，
  需要重开会话才会出现 `dart` 工具组；在此之前用 `flutter analyze` / `flutter test` /
  `flutter run -d c4e4da7e` / `adb` 截图兜底（见 `docs/agent-tooling.md`）。
- **不采用** `mcp_flutter`、`Android-Ui-MCP`、社区设计技能（理由见 `docs/agent-tooling.md`）。
- **测试账号**只用于本机验收；凭据不得写入仓库、脚本、文档或提交信息。
  设备上已存在登录会话，改版期间不要卸载应用（卸载会清掉会话与本地偏好）。
- **Home 不重设计**：只随共享令牌做等值替换，并用基线截图做回归比对。

## 5. 下一步起点

Phase 2 收尾（`AppSkeleton` 变体、`AppEmptyState` 的 pill 契约、`AppTextField` 密码显隐与
键盘收起、`EpisodeSpine` 的 44px 命中区与本地化、`AppMessageBar` 文案与关闭按钮命中区），
随后按计划进入 Phase 3（声明式路由 + 全屏子页 + 弹层返回优先级）。
