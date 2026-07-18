# Points API

当前文档记录 App 积分账户、积分流水和按视频时长计算的观看奖励。

所有接口都需要普通用户 Bearer Token：

```http
Authorization: Bearer <token>
```

## `GET /api/app/points/account`

返回当前用户积分账户。首次查询会自动创建余额为 `0` 的账户。

响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "balance": 3
  },
  "requestId": "uuid",
  "timestamp": "2026-06-26T17:30:00+08:00"
}
```

## `GET /api/app/points/records`

返回当前用户积分流水，按 `createdAt` 倒序排列。

响应：

```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "id": "uuid",
      "amount": 1,
      "balanceAfter": 3,
      "source": "WATCH_REWARD",
      "bookId": "book-1",
      "episodeNum": 1,
      "stage": null,
      "reason": null,
      "createdAt": "2026-06-26T17:30:00+08:00"
    }
  ],
  "requestId": "uuid",
  "timestamp": "2026-06-26T17:30:00+08:00"
}
```

## 观看奖励规则

- 视频播放完成（进度达到 `100%`）后一次性发放，不再使用 `25 / 50 / 75 / 100` 阶段。
- 奖励积分为 `max(1, floor(服务端视频时长 / points.watch.seconds-per-point))`，默认每完整 `60` 秒 `1` 积分。
- 公平模式开启时按十分位累计，极短视频最低 `0.1` 积分；每次实际进入整数余额的进位值同时用于 API 响应、`WATCH_REWARD` 流水和单集领取记录。
- 视频真实时长由播放接口从上游取得并作为元数据缓存；客户端提交的时长不参与积分计算。
- 同一用户、同一 `bookId`、同一 `episodeNum` 永久只领取一次，重复播放不补发。
- 每账号每日基础上限由 `points.daily-earned.maximum` 控制，默认 `1000`；`0` 表示不限制。
- `points.daily-earned.fluctuation-percent` 默认 `35`，每个账号每天随机分配 `0..35` 的向下浮动百分比。
- 当日有效上限为 `floor(基础上限 * (100 - 随机浮动值) / 100)`；规则按服务器自然日快照，后台修改次日生效。
- 超过剩余额度时只发剩余积分；额度为零时仍记录该视频已领取，不生成零金额流水，也不允许次日补发。
- 观看奖励来源为 `WATCH_REWARD`，后台积分调整来源为 `ADMIN_ADJUSTMENT`，充值订单入账来源为 `RECHARGE_ORDER`。
- 新观看奖励流水的 `stage` 为空；非观看奖励流水的 `bookId`、`episodeNum`、`stage` 也可以为空。
- `RECHARGE_ORDER` 流水只由后端内部订单结算边界生成，公开 App API 不能直接创建充值流水。

后台 `POST /api/admin/users/{userId}/points/adjust` 必须提交最长 64 字符的 `idempotencyKey`。服务端按管理员、目标用户和请求键组成作用域，同一作用域重复请求返回首次结果且不重复改余额或写流水。

错误：

- `401`：未登录或 Token 无效。
- `403`：用户已禁用。
