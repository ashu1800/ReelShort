# Android 锁集 VIP 原地付费弹层实施记录

## 实施范围

1. 扩展积分账户 DTO、领域模型和 UI 状态，接入后端现有 VIP 权益字段。
2. 收口免费集数常量和统一播放入口，覆盖详情点击、播放器选集、首页历史恢复和观看记录恢复。
3. 建立游客登录、VIP 403 兜底、付费层关闭和支付确认自动续播状态机。
4. 调整 VIP 订单创建与刷新，保留当前页面、串行防重，并对待支付订单每 10 秒静默刷新。
5. 从 Me 页抽取 `VipPaymentBottomSheet`，在应用根层复用，并补齐锁集上下文、订单信息、倒计时、复制和可访问性。
6. 更新 Android 状态测试、UI 契约测试和项目文档。

## 不变边界

- 不新增或修改后端接口。
- 不新增 Flyway 迁移，不改变数据库结构。
- 不改变 VIP 价格、有效期、收款地址和 TRC20 确认规则。
- 后端播放接口的 VIP `403` 继续作为最终权限校验。

## 验证命令

```powershell
cd android-app
.\gradlew.bat :app-core:test :app:testDebugUnitTest :app:assembleDebug --no-daemon

cd ..
powershell -ExecutionPolicy Bypass -File scripts/verify-release-baseline.ps1 -SkipBackend -SkipContentProvider -InstallApk
```
