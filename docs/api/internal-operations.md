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
    "account": "shortlink_user",
    "status": "ACTIVE",
    "balance": 120,
    "frozenPoints": 20,
    "availablePoints": 100,
    "vip": true,
    "vipUntil": "2030-01-15 12:30:00",
    "walletAddress": "0x0000000000000000000000000000000000000001"
  }
}
```

`account` 是账号唯一标识 `username`（认证模型已收敛为用户名，不再有手机号账号）。

## `GET /api/internal/operations/users/{userId}/watch-reward-task`

获取一条可模拟观看并领取积分的视频任务。

规则：

- 只允许 `ACTIVE` 用户，且只有 VIP 用户能领取观看奖励（非 VIP 返回 `403 non-VIP users cannot earn watch rewards`）。
- **续播优先**：取该用户最近一条观看记录所属短剧。该集尚未领取奖励时直接返回该集（`currentProgressPercent` 为该集已有进度）；该集已领取奖励时，返回同一部短剧其后第一个未被领取的分集（逐集跳过已领奖集，可容忍重看旧集造成的记录回退），`currentProgressPercent` 为 `0`。
- 「已完成」以**该集存在奖励领取记录**为准，与观看历史里的 `rewardClaimed` 一致。因此「进度 100% 但未领取」（例如非 VIP 期间看完、或当时拿不到权威时长）时仍返回该集本身，便于补发奖励，不会跳到下一集。
- **换剧兜底**：没有观看记录，或最近这部短剧已播完全部可播分集（后续分集不存在或全部已领奖）时，从 English 内容缓存中随机选择一部该用户**没有任何观看记录**的短剧，返回其**第 1 集**。
- 只认最近一条观看记录：更早的短剧即使仍有没看完的分集，也不会回退续播。
- 后端按需调用内容源获得真实视频时长；没有权威时长的候选不会被返回。同一部短剧内若某集时长取不到，会继续试其后分集；若**内容源对该短剧调用失败**（日志记 WARN），则放弃该短剧换下一部候选并继续处理，不做逐集重试。
- 注意：若最近这条记录所在的那一集本身取不到权威时长（内容源异常或上游没给时长），端点会**退化为上面的换剧兜底**，返回另一部未观看短剧的第 1 集（日志记 WARN）。若希望这种场景直接失败而不是换剧，需要调整该分支语义。
- 每日额度用尽后继续上报仍会把该集标记为已领取（0 积分，`rewardStatus=DAILY_LIMIT_REACHED`），因此运营脚本应在响应 `dailyRemainingPoints=0` 时停止，避免把没有收益的分集逐个「烧」成已领取。
- 全部候选都不可用时返回 `404 watch reward task not found`。

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

`episodeNum` 在续播场景可能大于 `1`；`currentProgressPercent` 只在「返回已有记录的那一集」时非 `0`。

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
