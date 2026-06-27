# Payment Events Admin Design

## Goal

补齐支付回调运营可见性，让后台管理员可以查询内部模拟支付回调事件，用于排查订单入账、金额不匹配、订单不存在和重复回调等问题。

## Scope

本阶段实现：

- 后端新增后台支付事件查询 API。
- 后台 Web 新增支付事件列表视图和侧栏入口。
- 支持按事件状态、订单号和支付渠道筛选。
- 返回事件 ID、平台事件 ID、订单号、渠道、金额、状态、失败原因和处理时间。

本阶段不实现：

- 支付事件详情页。
- 人工重试或重新结算。
- 真实支付平台事件验签。
- 导出、分页和复杂统计。

## Architecture

支付事件仍由 `payment` 模块持久化和查询。后台 API 使用 `admin` 权限体系暴露只读查询入口，避免后台直接访问数据库。前端 `admin-web` 通过既有 Axios 客户端调用 Spring Boot Admin API。

查询接口保持只读，不触发订单状态变化，也不修改积分流水。后续如果需要支付事件详情、重试或导出，可以沿同一 `payment` 模块继续扩展，不影响 App 充值订单和支付回调接口。

## API

新增后台接口：

- `GET /api/admin/payments/events`

查询参数：

- `status`：可选，`PROCESSED` 或 `REJECTED`。
- `orderNo`：可选，精确匹配平台订单号。
- `paymentChannel`：可选，精确匹配渠道。

返回：

- `providerEventId`
- `orderNo`
- `paymentChannel`
- `amountCents`
- `status`
- `failureReason`
- `createdAt`
- `processedAt`

权限：

- 新增后台权限 `PAYMENT_EVENT_READ`。
- 默认超级管理员角色拥有该权限。

## Frontend

后台新增 `PaymentEventsView`：

- 表格展示支付事件。
- 顶部筛选状态、订单号和渠道。
- 失败原因仅在 `REJECTED` 事件显示。
- 侧栏新增“支付事件”入口。
- Dashboard 可增加支付事件入口或指标不作为本阶段强制目标。

## Testing

后端覆盖：

- 无后台 Token 访问失败。
- 有 `PAYMENT_EVENT_READ` 权限可查询事件。
- 状态、订单号、渠道筛选生效。
- 默认按 `processedAt` 倒序返回。

前端覆盖：

- `npm run build` 能通过类型检查和构建。
- 服务客户端包含支付事件查询方法。
