# Order Settlement Foundation Design

## Goal

建立订单支付成功后的内部结算边界：同一充值订单只能入账一次，订单状态从 `CREATED` 变为 `PAID`，并为用户增加对应积分流水。当前仍不接入真实支付渠道，也不暴露 App 支付接口。

## Scope

本阶段实现：

- 订单实体支持受控标记为 `PAID`。
- 订单结算服务根据订单号完成积分入账。
- 入账生成 `RECHARGE_ORDER` 积分流水，原因记录订单号。
- 重复结算同一订单保持幂等，不重复增加余额、不重复生成流水。
- 非 `CREATED` 订单不能被重新结算。

本阶段不实现：

- 第三方支付请求。
- 支付回调 HTTP 接口。
- 回调验签。
- 退款和扣回。
- 前端操作按钮。

## Architecture

订单模块仍负责订单生命周期。积分模块仍负责账户和流水写入。订单结算由 `RechargeOrderService` 暴露内部方法承载，内部调用 `PointsService.creditRechargeOrder(...)` 完成积分账户变更。

该边界保持：

- App 不能直接标记支付成功。
- 后台 Web 仍只读订单。
- 真实支付回调未来只需要调用订单结算服务，不直接改积分表。
- 积分余额变更仍必须伴随积分流水。

## Data Model

沿用 `recharge_orders`：

- `status = PAID`
- `payment_channel` 写入内部调用传入的支付渠道。
- `updated_at` 在状态变化时刷新。

沿用 `point_transactions`：

- `source = RECHARGE_ORDER`
- `amount = order.pointAmount`
- `balanceAfter = 入账后余额`
- `reason = order.orderNo`

本阶段不新增支付流水表。后续真实支付接入时，可增加 `payment_events` 或支付回调记录表。

## Idempotency

结算入口按订单号查找订单：

- `CREATED`：执行状态变更和积分入账。
- `PAID`：直接返回当前订单，不重复入账。
- `CANCELLED` / `FAILED` / `REFUNDED`：拒绝结算。

当前单机模块使用订单号级锁保护同一订单结算，同时沿用用户级锁保护积分账户更新。未来真实支付回调跨进程部署时，应进一步考虑订单行锁或唯一支付事件表。

## Testing

新增后端测试覆盖：

- `CREATED` 订单结算后状态为 `PAID`，支付渠道写入，积分余额增加。
- 结算生成一条 `RECHARGE_ORDER` 积分流水。
- 重复结算同一订单不重复增加余额，不重复生成流水。
- 已取消订单拒绝结算。

验证命令：

- `.\gradlew.bat test --tests "*RechargeOrderServiceTests"`
- `.\gradlew.bat test --tests "*PointsServiceTests"`
- `.\gradlew.bat test`
