# Payment Callback API

当前文档记录支付回调基础边界。本阶段只实现内部模拟支付回调，不接入真实支付平台，不发起支付下单，不处理退款。

## 内部回调接口

### `POST /api/internal/payments/recharge/callback`

该接口用于支付渠道回调适配。当前使用共享密钥模拟支付渠道验签，后续接入真实支付平台时应替换为对应平台的签名校验。

请求头：

```http
X-Payment-Callback-Secret: <secret>
```

配置：

- `reelshort.payment.callback-secret`：回调共享密钥。
- 固定开发值仅允许显式 `app-dev` 和测试 profile 使用；生产部署必须通过环境变量提供至少 32 字符且具有足够熵的独立密钥，缺失或使用已知开发值时后端拒绝启动。

请求：

```json
{
  "providerEventId": "evt_001",
  "orderNo": "RO20260627113000000123456789AB",
  "paymentChannel": "mock-pay",
  "amountCents": 990
}
```

字段：

- `providerEventId`：支付渠道事件 ID，必须唯一。
- `orderNo`：平台充值订单号。
- `paymentChannel`：支付渠道标识。
- `amountCents`：支付金额，单位分。

成功响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "providerEventId": "evt_001",
    "orderNo": "RO20260627113000000123456789AB",
    "status": "PROCESSED",
    "orderStatus": "PAID",
    "failureReason": null
  },
  "requestId": "uuid",
  "timestamp": "2026-06-27T12:20:00+08:00"
}
```

## 处理规则

- 密钥不匹配时返回 `401`。
- 同一 `providerEventId` 重复回调时返回首次处理结果，不重复结算；当前单机部署下同一事件会在后端进程内串行处理。
- 回调金额必须等于订单金额。
- 金额不匹配、订单不存在等业务拒绝会记录 `REJECTED` 支付事件。
- 处理成功后记录 `PROCESSED` 支付事件，并调用订单结算服务将订单标记为 `PAID`。
- 订单结算负责积分入账，生成 `RECHARGE_ORDER` 积分流水。
- 后续如果扩展为多实例部署，支付回调幂等锁需要升级为数据库行锁或 Redis 分布式锁。

## 非目标

- 不对 App 暴露支付确认能力。
- 不允许后台 Web 手动触发支付成功。
- 不实现真实支付平台验签。
- 不处理退款、撤销和风控审核。

## 错误

- `400`：字段校验失败、订单不存在或金额不匹配。
- `401`：缺少或传入错误回调共享密钥。
