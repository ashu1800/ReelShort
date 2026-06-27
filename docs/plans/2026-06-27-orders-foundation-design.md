# Orders Foundation Design

## Goal

建立商业化预留的订单基础模块。当前不接入真实支付、不发放积分、不改变现有积分流水，只先固定充值订单的数据边界、状态机、App 创建/查询接口和 Admin 查询接口。

## Scope

本阶段实现：

- 用户创建充值订单。
- 用户查询自己的订单列表。
- 后台查询所有订单。
- 订单状态只允许在服务端受控创建和读取，暂不提供支付回调、确认支付或取消接口。
- 订单不修改积分账户，不生成积分流水。

本阶段不实现：

- 支付渠道请求。
- 支付回调验签。
- 充值到账。
- 退款。
- 权益包。
- 风控审核流。

## Module Boundary

新增 `backend/order` 包，避免把订单逻辑塞进 `points`。订单引用用户 ID，但不直接修改用户、积分账户或积分流水。

订单模块后续可以扩展支付模块和权益模块：

- `orders` 管订单生命周期。
- `points` 只在支付成功事件确认后记账。
- `admin` 只暴露后台查询和审计能力，不绕过订单服务直接改表。

## Domain Model

`RechargeOrder` 字段：

- `id`：UUID。
- `userId`：UUID。
- `orderNo`：业务订单号，唯一。
- `amountCents`：充值金额，单位分。
- `pointAmount`：计划到账积分。
- `status`：`CREATED` / `PAID` / `CANCELLED` / `FAILED` / `REFUNDED`。
- `paymentChannel`：支付渠道，第一阶段可为空。
- `createdAt`：创建时间。
- `updatedAt`：更新时间。

第一阶段只创建 `CREATED` 订单，预留其他状态枚举用于后续支付和退款。

## Public API

App API：

- `POST /api/app/orders/recharge`
- `GET /api/app/orders`

Admin API：

- `GET /api/admin/orders`

请求：

```json
{
  "amountCents": 990,
  "pointAmount": 99
}
```

校验：

- `amountCents > 0`
- `pointAmount > 0`

## Permissions

新增后台权限：

- `ORDER_READ`

默认超级管理员引导时获得该权限。后台订单查询必须使用 Admin Token 和权限校验。

## Testing

使用后端现有 JUnit、MockMvc、Spring Data JPA 测试方式：

- Repository 保存订单并按创建时间倒序返回。
- Service 创建订单，生成唯一订单号，校验金额和积分。
- App Controller 只能返回当前用户自己的订单。
- Admin Controller 需要后台权限，并返回全部订单。
- 创建订单不改变积分账户余额。

