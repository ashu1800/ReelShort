# Orders API

当前文档记录历史充值订单只读接口。当前没有真实充值商品和支付渠道，公开充值下单接口明确返回 `400 recharge is not supported`，不会创建订单；历史订单和内部支付回调仍可查询、结算和审计。

## App 接口

App 订单接口使用普通用户 Bearer Token：

```http
Authorization: Bearer <app-token>
```

### `POST /api/app/orders/recharge`

接口保留用于兼容旧版客户端，但当前始终返回 `400 recharge is not supported`，并且不会创建订单。

规则：

- 客户端提交的 `pointAmount` 不会被接受或写入，App 不能指定兑换比例。
- 当前公开接口不会创建 `CREATED` 订单；历史订单继续只读展示。

响应：

```json
{
  "code": 400,
  "message": "recharge is not supported",
  "requestId": "uuid",
  "timestamp": "2026-06-27T11:30:00+08:00"
}
```

### `GET /api/app/orders`

返回当前用户自己的充值订单，按创建时间倒序排列。

不会返回其他用户订单。

## Admin 接口

后台订单接口使用管理员 Bearer Token，并要求 `ORDER_READ` 权限。

> 前端现状：admin-web **没有**订单管理页面，侧栏也不再有该入口（原先指向不存在的页面）。接口保留在后端，供脚本或后续页面直接调用。

### `GET /api/admin/orders`

返回所有用户的充值订单，按创建时间倒序排列。

字段同 `GET /api/app/orders` 的历史订单列表项。

> VIP 订单不属于本文档范围：后台的 `GET /api/admin/vip/orders`、人工确认与拒绝见 `docs/api/admin.md`。

## VIP TRC20 订单的收款匹配窗口

`VipPaymentMatcher` 只有在下列条件全部成立时才把一笔链上转账算作某个 VIP 订单的付款：订单快照网络为 `TRC20`、收款地址与 USDT 合约**逐字符相等**、金额等于订单 `payableAmount`、转账成功且确认数达到 `reelshort.tron.required-confirmations`（默认 20）、付款区块时间落在

```
[createdAt, expiresAt + reelshort.vip.confirmation-grace]    // 默认宽限 10 分钟
```

宽限期的含义：页面倒计时归零后订单仍是 `PENDING`，这 10 分钟内到达的**按时**付款照常自动确认（旧实现把这个窗口锁死在 `expiresAt`，倒计时与链上落块有秒级偏差时只能等人工补单）。

订单在 `expiresAt + grace` 之后转 `EXPIRED` 并释放金额槽位。定时扫描（`reelshort.vip.auto-confirm-interval`，默认 120 秒）在同一轮里还会回看 `reelshort.vip.expired-recovery-window`（默认 24 小时）内已 `EXPIRED` 的订单——这是为了补偿扫描中断（TronGrid 故障、分页硬上限）导致按时付款没被及时确认的情况，命中后按 `VIP_ORDER_RECOVERED_AFTER_EXPIRY` 自动补单，规则与人工补单完全一致。同一金额同时命中 `PENDING` 与 `EXPIRED` 订单时优先确认 `PENDING`。

## 状态范围

当前枚举预留：

- `CREATED`
- `PAID`
- `CANCELLED`
- `FAILED`
- `REFUNDED`

公开接口不会创建或变更充值订单状态。

内部结算边界：

- `CREATED` 订单可被内部结算服务标记为 `PAID`。
- `PAID` 订单重复结算保持幂等，不重复增加积分。
- `CANCELLED`、`FAILED`、`REFUNDED` 订单不能结算入账。
- 结算入账生成 `RECHARGE_ORDER` 积分流水，`reason` 记录订单号。

当前内部模拟支付回调见 `docs/api/payment-callback.md`。真实支付平台下单、平台验签、退款和风控流程仍属于后续支付模块范围。

## 错误

- `400`：充值暂不支持，或历史内部请求字段不合法。
- `401`：未提供有效 App Token 或 Admin Token。
- `403`：后台账号已登录但缺少 `ORDER_READ` 权限。
