# Orders API

当前文档记录充值订单基础接口。公开 API 只负责创建和查询充值订单，不接入真实支付，不提供支付确认入口。后端内部已预留结算边界：未来支付回调确认成功后，由订单服务将订单标记为 `PAID` 并调用积分服务入账。

## App 接口

App 订单接口使用普通用户 Bearer Token：

```http
Authorization: Bearer <app-token>
```

### `POST /api/app/orders/recharge`

创建充值订单。

请求：

```json
{
  "amountCents": 990,
  "pointAmount": 99
}
```

规则：

- `amountCents` 为充值金额，单位分，必须大于 `0`。
- `pointAmount` 为计划到账积分，必须大于 `0`。
- 创建后订单状态固定为 `CREATED`。
- `paymentChannel` 当前为空，后续接入支付渠道时再写入。
- 创建订单不会修改积分账户余额；只有内部结算确认支付成功后才会入账。

响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": "uuid",
    "userId": "uuid",
    "orderNo": "RO20260627113000000123456789AB",
    "amountCents": 990,
    "pointAmount": 99,
    "status": "CREATED",
    "paymentChannel": null,
    "createdAt": "2026-06-27T11:30:00+08:00",
    "updatedAt": "2026-06-27T11:30:00+08:00"
  },
  "requestId": "uuid",
  "timestamp": "2026-06-27T11:30:00+08:00"
}
```

### `GET /api/app/orders`

返回当前用户自己的充值订单，按创建时间倒序排列。

不会返回其他用户订单。

## Admin 接口

后台订单接口使用管理员 Bearer Token，并要求 `ORDER_READ` 权限。

### `GET /api/admin/orders`

返回所有用户的充值订单，按创建时间倒序排列。

字段同 App 订单响应列表项。

## 状态范围

当前枚举预留：

- `CREATED`
- `PAID`
- `CANCELLED`
- `FAILED`
- `REFUNDED`

公开接口只会创建 `CREATED` 状态订单，且没有公开接口可以变更订单状态。

内部结算边界：

- `CREATED` 订单可被内部结算服务标记为 `PAID`。
- `PAID` 订单重复结算保持幂等，不重复增加积分。
- `CANCELLED`、`FAILED`、`REFUNDED` 订单不能结算入账。
- 结算入账生成 `RECHARGE_ORDER` 积分流水，`reason` 记录订单号。

当前内部模拟支付回调见 `docs/api/payment-callback.md`。真实支付平台下单、平台验签、退款和风控流程仍属于后续支付模块范围。

## 错误

- `400`：请求字段缺失或金额、积分数量不合法。
- `401`：未提供有效 App Token 或 Admin Token。
- `403`：后台账号已登录但缺少 `ORDER_READ` 权限。
