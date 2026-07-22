# System Config API

当前文档记录后台系统配置接口。系统配置用于长期运营参数，不用于保存用户私有数据。

所有接口都需要后台管理员 Bearer Token：

```http
Authorization: Bearer <admin-token>
```

## 支持的配置键

| Key | 默认值 | 规则 | 说明 |
| --- | --- | --- | --- |
| `points.watch.seconds-per-point` | `60` | 整数，`1..86400` | 完成视频后每获得 1 积分所需秒数。 |
| `points.daily-earned.maximum` | `1000` | 整数，`0..1000000` | 每账号每日自动获取积分上限，`0` 表示不限制。 |
| `points.daily-earned.fluctuation-percent` | `35` | 整数，`0..100` | 每账号每日随机向下浮动百分比的上限。 |
| `withdraw.usdt-per-50-points` | `0.14` | 正数，最多 8 位小数，最大 `100` | 每 50 积分对应的 USDT 金额。 |
| `withdraw.fee-percent` | `10` | 整数，`0..99` | 从用户提交积分中扣除的提现手续费百分比。 |
| `content.recommendation.strategy` | `LATEST` | `LATEST` / `POPULAR` | 首页推荐策略占位。 |

## `GET /api/admin/system/configs`

返回所有支持的配置。未持久化的配置会返回默认值，`updatedAt` 为 `null`。

响应：

```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "key": "points.watch.seconds-per-point",
      "value": "60",
      "description": "Completed video seconds required for one watch reward point.",
      "updatedAt": null
    }
  ],
  "requestId": "uuid",
  "timestamp": "2026-06-26T18:00:00+08:00"
}
```

## `POST /api/admin/system/configs/{configKey}`

更新指定配置。

请求：

```json
{
  "value": "2"
}
```

更新成功会写入后台审计日志，动作为 `SYSTEM_CONFIG_UPDATED`。

错误：

- `400`：配置值为空或不符合配置规则。
- `401`：未提供有效后台 Token，或使用了 App Token。
- `404`：配置键不在白名单中。
