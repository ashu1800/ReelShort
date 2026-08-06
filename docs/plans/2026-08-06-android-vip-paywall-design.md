# Android 锁集 VIP 原地付费弹层设计

## 目标

第 8 集及以后继续作为 VIP 内容。未开通 VIP 的用户点击锁集时保留详情页或播放器上下文，直接显示现有 TRC20 USDT 支付弹层；游客先完成登录，再自动进入同一付费层。支付确认后关闭弹层并继续播放原目标分集。

## 状态与流程

- `PointAccount` 映射后端现有 `vip`、`vipUntil`，`AppUiState` 持有权益、付费层 loading/visible 和待续播分集。
- `DEFAULT_VIP_FREE_EPISODES = 7` 是 Android 唯一免费集数常量，详情、播放器和 `AppStateController` 共用。
- 所有已登录分集请求统一经过 `requestEpisodePlayback`。免费集或有效 VIP 直接取播放地址；非 VIP 锁集先打开付费层，不请求视频。
- 游客锁集将目标保存在 `pendingVipEpisode`，登录成功后加载权益、钱包、最新订单和订单列表，再显示付费层。
- 播放接口明确返回 `403 VIP required` 时进入同一付费流程；其他 `401/403` 保持原鉴权和封禁处理。
- 用户主动关闭付费层即清除待续播目标。改密和退出会话同时清理所有 VIP UI 状态。

## 支付界面

`VipPaymentBottomSheet` 在 `MainActivity` 根层渲染，因此可覆盖详情页、Me 页和全屏播放器。Me 页只触发全局打开回调，不再维护局部弹层状态。

锁集场景显示锁图标、目标集数、30 天价格和单一主操作。待支付订单显示精确金额、订单号、TRC20 地址、二维码、复制、倒计时和刷新。按钮异步期间禁用；内容支持垂直滚动、系统导航栏安全边距、48dp 触控和 TalkBack 描述。二维码失败不影响地址复制。

## 刷新与容错

待支付订单在弹层显示期间每 10 秒静默刷新，不使用全局 loading。`PENDING` 复用现有订单；`EXPIRED`、`REJECTED` 或本地倒计时结束后允许重新创建。刷新失败保留弹层和已有订单信息，仅展示可恢复错误。

检测到 `vip=true` 后关闭付费层，并通过统一播放入口继续 `pendingVipEpisode`。后端播放接口仍是最终权限校验，不修改 API、数据库或 TRC20 规则。

## 验证

- app-core 状态机测试覆盖锁集拦截、游客登录、历史续播、VIP 403、generic 403、订单互斥、主动关闭和确认后续播。
- Android 契约测试覆盖根层共享组件、Me 页去重和免费集数常量复用。
- 最终执行 Android 全量单元测试、Debug APK 构建与发布质量基线。
