# 后台数据统计 API

后台「数据统计」页使用，统计 App 自然观看、社交互动、运营观看任务（刷积分）与全站积分快照。

所有接口都需要管理员 Bearer Token，并具备 `DASHBOARD_READ` 权限：

```http
Authorization: Bearer <admin-token>
```

## `GET /api/admin/statistics`

查询参数：

| 参数 | 可选值 | 默认 | 说明 |
| --- | --- | --- | --- |
| `range` | `today` / `7d` / `30d` / `all` | `all` | 时间窗，大小写不敏感；非法值返回 400 |

时间窗按 `Asia/Shanghai` 自然日切分：

- `today`：上海时区今天 `00:00` 起。
- `7d`：今天往前 6 天 `00:00` 起（含今天，共 7 天）。
- `30d`：今天往前 29 天 `00:00` 起（含今天，共 30 天）。
- `all`：不限时间，`since` 返回 `null`。

响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "range": "7d",
    "since": "2026-11-05T00:00:00+08:00",
    "app": {
      "plays": 0,
      "likes": 0,
      "favorites": 0,
      "comments": 0
    },
    "watchTask": {
      "plays": 0,
      "points": 0,
      "averagePoints": "0.00"
    },
    "totalPoints": "12345.6"
  },
  "requestId": "uuid",
  "timestamp": "2026-11-11T10:00:00+08:00"
}
```

字段口径：

- `app.plays`：`watch_records` 中 `source='APP'` 且 `updated_at >= since` 的行数；`all` 时为全部 App 观看行。每用户每集一行，重复观看不会重复计数。
- `app.likes` / `app.favorites` / `app.comments`：对应表 `created_at >= since` 的行数；`all` 时为全表行数。
- `watchTask.plays`：`watch_records` 中 `source='OPERATIONS'` 且 `updated_at >= since` 的行数。
- `watchTask.points`：与这些运营观看行同键（`user_id + book_id + episode_num`）的 `watch_episode_reward_claims.awarded_points` 之和。App 自然观看同键的领取记录不会计入。
- `watchTask.averagePoints`：`points / plays`，保留 2 位小数（`HALF_UP`）；`plays = 0` 时固定为 `"0.00"`。
- `totalPoints`：当前全站积分快照，等于所有 `point_accounts` 的 `(balance * 10 + fractional_part) / 10`，保留 1 位小数并去掉无意义尾零（例如 `12345.6`、`100`、`0`）。**与 `range` 无关**，任何时间窗都返回同一快照。
- `since`：ISO-8601 字符串，带 `+08:00` 偏移；`all` 时为 `null`。

## 观看来源与历史数据

- 观看记录的 `source` 由后端写入：App 观看接口固定 `APP`，内部运营刷积分入口（`InternalOperationsService.reportWatchProgress`）固定 `OPERATIONS`。
- `source` 列由 Flyway `V37__watch_record_source.sql` 新增。**V37 之前的历史观看行产生时还没有来源标记，无法区分，统一按 `APP`（App 自然观看）统计**；因此升级前的运营刷积分量会体现在 `app.plays` 而不是 `watchTask.plays`，这是已知口径偏差。
- 观看记录按 `(user_id, book_id, episode_num)` 唯一复用：同一集已有观看行时只会更新进度，**来源保持首次落库的值不变**。因此运营任务若命中用户已自然观看过的同一集，该次不会改成 `OPERATIONS`，也就不计入 `watchTask.plays`。
- 运营任务积分也受同一限制：历史运营观看行对应的领取记录会随观看行按 `APP` 处理，不计入 `watchTask.points`。
