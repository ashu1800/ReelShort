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
- 优先返回该用户最近观看记录中仍有未领取奖励阶段的分集。
- 没有观看记录时，从 English 内容缓存中选择一条有分集的短剧。
- 内容缓存没有视频真实时长时，默认返回 `durationSeconds=300`。

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
    "nextRewardStage": 25,
    "targetProgressPercent": 25,
    "alreadyClaimedStages": [],
    "canReport": true
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
  "positionSeconds": 75,
  "durationSeconds": 300,
  "progressPercent": 25,
  "reason": "ops simulated playback"
}
```

约束：

- 只允许 `progressPercent` 为 `25`、`50`、`75`、`100`。
- `DISABLED` 和 `BLACKLISTED` 用户返回 `403 user is not active`。
- 重复上报同一用户、同一短剧、同一集、同一阶段不会重复发积分。
- 模拟到高阶段时，现有奖励逻辑会补齐未领取的低阶段。
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
    "progressPercent": 25,
    "awardedStages": [25],
    "awardedPoints": 1,
    "balance": 121,
    "frozenPoints": 20,
    "availablePoints": 101
  }
}
```
