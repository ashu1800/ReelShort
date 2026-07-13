# System Config API

当前文档记录后台系统配置接口。系统配置用于长期运营参数，不用于保存用户私有数据。

所有接口都需要后台管理员 Bearer Token：

```http
Authorization: Bearer <admin-token>
```

## 支持的配置键

| Key | 默认值 | 规则 | 说明 |
| --- | --- | --- | --- |
| `points.watch.stage-points` | `1` | 整数，`0..1000` | 单个观看阶段奖励积分。 |
| `points.daily-earned.maximum` | `1000` | 整数，`0..1000000` | 每账号每日自动获取积分上限，`0` 表示不限制。 |
| `withdraw.minimum-points` | `100` | 整数，`1..1000000` | 最低提现积分。 |
| `withdraw.usdt-per-point` | `0.001` | 非负小数，最多 8 位小数，最大 `100` | 1 积分对应的 USDT 数量。 |
| `points.transfer.minimum-points` | `1` | 整数，`1..1000000` | 用户积分交易最低积分。 |
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
      "key": "points.watch.stage-points",
      "value": "1",
      "description": "Points awarded for each watch progress stage.",
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
