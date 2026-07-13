# Watch API

当前文档记录阶段 1 App 观看进度和观看历史接口。

所有接口都需要普通用户 Bearer Token：

```http
Authorization: Bearer <token>
```

## `POST /api/app/watch/progress`

上报当前用户的单集观看进度。

请求：

```json
{
  "bookId": "book-1",
  "bookTitle": "Love Story",
  "filteredTitle": "love-story",
  "episodeNum": 1,
  "chapterId": "chapter-1",
  "positionSeconds": 30,
  "durationSeconds": 120
}
```

响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": "uuid",
    "bookId": "book-1",
    "bookTitle": "Love Story",
    "filteredTitle": "love-story",
    "episodeNum": 1,
    "chapterId": "chapter-1",
    "positionSeconds": 30,
    "durationSeconds": 120,
    "progressPercent": 25,
    "awardedStages": [],
    "awardedPoints": 0,
    "updatedAt": "2026-06-26T17:30:00+08:00",
    "rewardClaimed": false,
    "rewardStatus": "NOT_COMPLETE"
  },
  "requestId": "uuid",
  "timestamp": "2026-06-26T17:30:00+08:00"
}
```

规则：

- 同一用户、同一 `bookId`、同一 `episodeNum` 重复上报会更新同一条记录。
- `progressPercent` 由后端根据 `positionSeconds / durationSeconds` 计算，最大为 `100`。
- 当 `positionSeconds > durationSeconds` 时，后端按 `durationSeconds` 保存。
- `awardedStages` 是兼容字段，固定为空数组；`awardedPoints` 表示本次实际发放积分。
- 只有使用服务端权威时长计算出的进度达到 `100%` 才会发奖。
- `rewardStatus` 为 `NOT_COMPLETE`、`AWARDED`、`AWARDED_PARTIAL`、`ALREADY_CLAIMED`、`DAILY_LIMIT_REACHED` 或 `DURATION_UNAVAILABLE`。
- `DURATION_UNAVAILABLE` 时观看记录仍保存，但不会创建领取记录，可在播放接口取得时长后重试。

## `GET /api/app/watch/history`

返回当前用户观看历史，按 `updatedAt` 倒序排列。

历史响应中的 `awardedStages` 固定为空数组，`awardedPoints` 固定为 `0`；单集快照通过 `rewardClaimed` 表示是否已经领取。

错误：

- `401`：未登录或 Token 无效。
- `403`：用户已禁用。
- `400`：请求字段缺失、空白或数值不合法。
