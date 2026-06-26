# Points API

当前文档记录阶段 1 App 积分账户、积分流水和观看阶段奖励。

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
      "stage": 75,
      "reason": null,
      "createdAt": "2026-06-26T17:30:00+08:00"
    }
  ],
  "requestId": "uuid",
  "timestamp": "2026-06-26T17:30:00+08:00"
}
```

## 观看奖励规则

- 奖励阶段固定为 `25 / 50 / 75 / 100`。
- 每个阶段奖励积分由系统配置 `points.watch.stage-points` 决定，默认 `1`。
- 当 `points.watch.stage-points = 0` 时，后端仍记录阶段已领取，但不增加余额、不生成 0 金额流水。
- 同一用户、同一 `bookId`、同一 `episodeNum`、同一阶段只能发放一次。
- 如果进度从低阶段直接跳到高阶段，会一次性补发所有已达到且未领取的阶段。
- 观看奖励来源为 `WATCH_REWARD`，后台积分调整来源为 `ADMIN_ADJUSTMENT`。
- 非观看奖励流水的 `bookId`、`episodeNum`、`stage` 可以为空，`reason` 记录后台调整原因。

错误：

- `401`：未登录或 Token 无效。
- `403`：用户已禁用。
