# API Docs

后续维护 App API、Admin API 和内部内容源接口说明。

当前接口文档：

- `auth-user.md`：普通用户注册登录（用户名 + 密码 + 图形验证码）。
- `auth-security.md`：App Bearer Token 鉴权、游客可访问范围与凭证锁定。
- `backend-foundation.md`：后端基础响应和健康检查。
- `content-cache.md`：内容缓存、货架和剧集详情（英文单语 `en`）。
- `orders.md`：充值订单查询和 VIP 订单人工确认。
- `payment-callback.md`：内部模拟支付回调和订单结算入口。
- `payment-events-admin.md`：后台支付事件查询。
- `points.md`：积分账户、流水和观看奖励。
- `withdrawals.md`：ERC20 USDT 提现、积分直接换算、人工确认与打款 attempt 作废。
- `rate-limit.md`：后端限流规则与可信代理白名单。
- `system-config.md`：后台系统配置。
- `watch.md`：观看进度和观看记录。
- `admin.md`：后台管理接口。
- `internal-operations.md`：内部运营接口。

> 手机号 + 短信验证码认证、内部手机号开户注册、旧版 Android 更新链路（`/api/app/update/latest`、`/downloads/android/`）与 `zh-TW` 双语内容缓存均已在 2026-07 至 2026-10 的改造中删除，历史计划文档保留当时事实，不作为当前实现依据。

