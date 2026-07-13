# Internal Operations API

内部运营接口只供运营脚本或受控运营系统调用，不面向 App 用户开放。

所有接口必须携带：

```http
X-Internal-Super-Token: <REELSHORT_INTERNAL_SUPER_TOKEN>
```

Token 缺失返回 `401 unauthorized`，Token 错误返回 `403 forbidden`。

## `GET /api/internal/operations/users/{userId}/points/account`

查询账号积分快照。

响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "userId": "00000000-0000-0000-0000-000000000000",
    "account": "+14155550101",
    "status": "ACTIVE",
    "balance": 120,
    "frozenPoints": 20,
    "availablePoints": 100
  }
}
```

## `GET /api/internal/operations/users/{userId}/watch-reward-task`

获取一条可模拟观看并领取积分的视频任务。

规则：

- 只允许 `ACTIVE` 用户。
- 优先返回该用户最近观看记录中尚未领取完整视频奖励的分集。
- 没有观看记录时，从 English 内容缓存中选择一条有分集的短剧。
- 后端按需调用内容源获得真实视频时长；没有权威时长的候选不会返回。

响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "bookId": "book-1",
    "bookTitle": "Example Drama",
    "bookDescription": "Description",
    "filteredTitle": "example-drama",
    "episodeNum": 1,
    "chapterId": "chapter-1",
    "episodeTitle": "Episode 1",
    "durationSeconds": 300,
    "currentProgressPercent": 0,
    "nextRewardStage": null,
    "targetProgressPercent": null,
    "alreadyClaimedStages": [],
    "canReport": true,
    "estimatedRewardPoints": 5,
    "dailyEffectiveLimit": 850,
    "dailyEarnedPoints": 120,
    "dailyRemainingPoints": 730,
    "rewardClaimed": false
  }
}
```

## `POST /api/internal/operations/users/{userId}/watch-progress`

运营侧模拟播放后上报进度。后端复用 App 观看进度和奖励发放路径，不直接手工加积分。

请求：

```json
{
  "bookId": "book-1",
  "bookTitle": "Example Drama",
  "filteredTitle": "example-drama",
  "episodeNum": 1,
  "chapterId": "chapter-1",
  "positionSeconds": 300,
  "durationSeconds": 300,
  "progressPercent": 100,
  "reason": "ops simulated playback"
}
```

约束：

- 只允许 `progressPercent=100`，且视频必须存在服务端权威时长。
- `DISABLED` 和 `BLACKLISTED` 用户返回 `403 user is not active`。
- 同一用户、同一短剧、同一集只发一次，重复模拟不会重复发积分。
- 模拟观看复用 `WATCH_REWARD` 自动奖励路径，会与 App 观看奖励共享 `points.daily-earned.maximum` 每账号每日上限。
- 成功上报会写后台审计日志，操作者标记为 `internal-operations`。

响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "bookId": "book-1",
    "episodeNum": 1,
    "progressPercent": 100,
    "awardedStages": [],
    "awardedPoints": 5,
    "balance": 125,
    "frozenPoints": 20,
    "availablePoints": 105,
    "rewardClaimed": true,
    "rewardStatus": "AWARDED"
  }
}
```
