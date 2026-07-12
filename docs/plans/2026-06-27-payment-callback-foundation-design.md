# Payment Callback Foundation Design

## Goal

建立支付回调基础边界，为后续接入真实支付渠道预留稳定路径。当前只实现“内部模拟支付回调”：校验共享密钥、记录支付事件、校验订单金额、调用订单结算服务，不发起真实支付、不做第三方验签。

## Scope

本阶段实现：

- 新增 `backend/payment` 模块。
- 新增内部回调接口 `POST /api/internal/payments/recharge/callback`。
- 回调请求使用 `X-Payment-Callback-Secret` 共享密钥鉴权。
- 记录支付事件，使用 `providerEventId` 保证同一事件幂等。
- 校验回调金额必须等于订单金额。
- 回调成功后调用 `RechargeOrderService.settlePaid(...)`。

本阶段不实现：

- 第三方支付下单。
- 真实支付平台验签。
- 退款回调。
- 支付事件后台页面。
- App 支付页面。

## Architecture

新增 `payment` 包，负责支付回调适配和支付事件记录。订单状态转换仍由 `order` 模块负责，积分入账仍由 `points` 模块负责。

数据流：

1. 支付回调 controller 校验共享密钥。
2. `PaymentCallbackService` 根据 `providerEventId` 查询支付事件。
3. 如果事件已处理，直接返回已有处理结果。
4. 如果是新事件，校验订单存在且金额一致。
5. 保存支付事件并调用 `RechargeOrderService.settlePaid(orderNo, paymentChannel)`。

该方案把真实支付接入点固定在 `payment` 模块，避免未来回调直接改订单表或积分表。

## Data Model

新增 `payment_events`：

- `id`：UUID。
- `provider_event_id`：支付渠道事件 ID，唯一。
- `order_no`：平台订单号。
- `payment_channel`：支付渠道。
- `amount_cents`：支付金额。
- `status`：`PROCESSED` / `REJECTED`。
- `failure_reason`：拒绝原因。
- `created_at`：事件创建时间。
- `processed_at`：处理时间。

当前只记录成功处理和业务拒绝，不记录原始报文。真实支付接入时可扩展原始报文摘要、签名串和验签结果。

## Security

共享密钥配置：

- `reelshort.payment.callback-secret`
- 本阶段实施当时的默认值只用于本地测试；当前固定开发值仅允许显式 `app-dev` 和测试 profile 使用，生产必须通过环境变量提供独立强密钥，缺失或使用已知开发值时拒绝启动。

接口不使用 App Token 或 Admin Token。它是内部支付回调用入口，部署时应通过 Nginx 或内网限制来源。

## Error Handling

- 密钥错误：`401 unauthorized`。
- 字段校验失败：`400 bad request`。
- 订单不存在或金额不匹配：`400`，并记录 `REJECTED` 支付事件。
- 重复事件：返回第一次处理结果，不重复结算。

## Testing

覆盖：

- 无密钥或密钥错误返回 `401`。
- 合法回调结算订单并增加积分。
- 重复 `providerEventId` 不重复入账。
- 金额不匹配返回 `400`，订单不入账。
- 事件记录唯一约束生效。

验证命令：

- `.\gradlew.bat test --tests "*PaymentCallbackControllerTests"`
- `.\gradlew.bat test --tests "*PaymentCallbackServiceTests"`
- `.\gradlew.bat test`
