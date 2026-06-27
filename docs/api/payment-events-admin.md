# Admin Payment Events API

后台支付事件接口用于查询内部模拟支付回调处理记录，帮助运营排查充值订单入账、金额不匹配、订单不存在和重复回调。

## 查询支付事件

### `GET /api/admin/payments/events`

权限：

- 需要 Admin Bearer Token。
- 需要 `PAYMENT_EVENT_READ` 权限。

查询参数：

- `status`：可选，`PROCESSED` 或 `REJECTED`。
- `orderNo`：可选，精确匹配充值订单号。
- `paymentChannel`：可选，精确匹配支付渠道。

响应：

```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "providerEventId": "evt_001",
      "orderNo": "RO20260627113000000123456789AB",
      "paymentChannel": "mock-pay",
      "amountCents": 990,
      "status": "PROCESSED",
      "failureReason": null,
      "createdAt": "2026-06-27T12:20:00+08:00",
      "processedAt": "2026-06-27T12:20:00+08:00"
    }
  ],
  "requestId": "uuid",
  "timestamp": "2026-06-27T12:20:00+08:00"
}
```

## 规则

- 默认按 `processedAt` 倒序返回。
- 查询接口只读，不触发订单结算、积分入账或支付事件重试。
- `REJECTED` 事件会返回 `failureReason`。
- 本阶段不提供导出、人工重试和事件详情页。

## 错误

- `401`：缺少或传入无效后台 Token。
- `403`：管理员缺少 `PAYMENT_EVENT_READ` 权限。
- `400`：`status` 参数不是合法支付事件状态。
