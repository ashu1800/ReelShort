# 运营模拟观看奖励设计

## 目标

为外部运营系统或脚本提供内部接口，按“用户 + 短剧 + 集数 + 播放进度”模拟真实播放上报，复用现有观看奖励幂等逻辑给用户发放积分。

## 接口边界

接口只放在内部运营命名空间，不进入 App API，也不放到公开后台页面首版：

- `GET /api/internal/operations/users/{userId}/points/account`
- `GET /api/internal/operations/users/{userId}/watch-reward-task`
- `POST /api/internal/operations/users/{userId}/watch-progress`

所有接口使用 `X-Internal-Super-Token` 校验，复用现有 `REELSHORT_INTERNAL_SUPER_TOKEN`。V1 不新增独立运营 Token。

## 数据流

查询积分接口返回用户账号、状态、总积分、冻结积分和可用积分，供运营侧在模拟前后核对余额。

获取任务接口返回一条可获取观看积分的视频任务。后端优先从该用户已有观看记录中找到未完成 100% 的记录；没有记录时，从自有内容缓存中选择一条有分集的短剧。返回字段包含短剧标题、简介、集数、分集标题、默认视频时长、当前进度、已领取阶段和下一奖励阶段。

模拟进度接口接收 `bookId`、`episodeNum`、`positionSeconds`、`durationSeconds`、`progressPercent` 和 `reason`。后端复用 `WatchService.reportProgress()` 更新观看记录，并复用现有奖励逻辑按 25/50/75/100 阶段幂等发放积分。

## 规则

- 只允许 `ACTIVE` 用户模拟观看奖励。
- `DISABLED` 或 `BLACKLISTED` 用户拒绝操作。
- `progressPercent` 限制为 25、50、75、100。
- 重复调用同一阶段不会重复发积分。
- 模拟到高阶段时，按现有逻辑补齐未领取的低阶段。
- 内容没有真实时长时，任务接口默认返回 300 秒。
- 每次模拟上报写后台审计日志，包含管理员标识 `internal-operations`、目标用户、短剧、集数、进度、实际发放阶段、发放积分和原因。

## 返回示例

积分查询：

```json
{
  "userId": "00000000-0000-0000-0000-000000000000",
  "account": "+14155550101",
  "status": "ACTIVE",
  "balance": 120,
  "frozenPoints": 20,
  "availablePoints": 100
}
```

任务查询：

```json
{
  "bookId": "book-1",
  "bookTitle": "Example Drama",
  "bookDescription": "Description",
  "episodeNum": 3,
  "episodeTitle": "Episode 3",
  "durationSeconds": 300,
  "currentProgressPercent": 24,
  "nextRewardStage": 25,
  "targetProgressPercent": 25,
  "alreadyClaimedStages": [],
  "canReport": true
}
```

进度上报：

```json
{
  "bookId": "book-1",
  "episodeNum": 3,
  "progressPercent": 75,
  "awardedStages": [25, 50, 75],
  "awardedPoints": 3,
  "balance": 123,
  "frozenPoints": 20,
  "availablePoints": 103
}
```

## 验收点

- 内部 Token 缺失或错误时拒绝。
- 查询积分能反映冻结积分和可用积分。
- 获取任务能返回下一可领取奖励阶段。
- 模拟到 100% 能补齐 25/50/75/100 未领取阶段。
- 重复模拟不重复发积分。
- 禁用和拉黑用户无法模拟。
- 审计日志记录完整。
