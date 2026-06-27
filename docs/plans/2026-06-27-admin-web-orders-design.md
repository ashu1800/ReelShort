# Admin Web Orders Design

## Goal

把后端已经完成的充值订单基础模块接入 Vue 后台，让运营人员可以在后台查看所有用户的充值订单。当前只做只读订单管理，不做支付确认、退款、到账或订单状态变更。

## Scope

本阶段实现：

- 后台侧栏新增“订单管理”入口。
- 新增 `/orders` 路由和订单列表页。
- 前端调用 `GET /api/admin/orders`。
- 展示订单号、用户 ID、充值金额、计划积分、状态、支付渠道、创建时间和更新时间。
- 控制台增加订单总数、创建中订单和充值金额概览。

本阶段不实现：

- 订单搜索、分页和导出。
- 支付状态变更。
- 退款、到账、支付回调处理。
- 前端权限菜单裁剪。权限仍由后端 `ORDER_READ` 裁决。

## Approach

采用增量扩展现有后台壳的方案：

- `adminApi.ts` 增加 `RechargeOrder` 类型和 `fetchOrders()`。
- `OrdersView.vue` 使用 Element Plus 表格呈现只读数据。
- `DashboardView.vue` 并行加载订单数据，补充商业化预留指标。
- `router/index.ts` 和 `App.vue` 增加路由与菜单项。
- `admin-web/README.md` 和 `AGENTS.md` 同步模块说明。

该方案保持后台 Web 作为薄客户端，不把支付或积分记账逻辑放进前端。

## UI

订单页沿用现有后台工作台风格：紧凑表格、清晰状态标签、顶部刷新按钮和少量统计指标。不做卡片化营销布局，也不引入新的视觉系统。

关键展示规则：

- 金额从 `amountCents` 格式化为人民币元。
- `paymentChannel` 为空时显示 `未接入`。
- `CREATED` 状态使用中性色标签，其他预留状态使用不同语义颜色。
- 用户 ID 和订单号使用等宽文本，便于运营复制和核对。

## Error Handling

- 加载失败时显示页面级错误提示。
- 401 继续由现有 Axios interceptor 清理会话。
- 403 时页面显示“订单列表加载失败”，后续可统一解析后端 message。
- 刷新按钮不清空已有数据，避免临时失败导致页面完全空白。

## Testing

当前前端未引入单元测试框架，本阶段验证方式为：

- `npm run build` 验证 TypeScript 和生产构建。
- `git diff --check` 验证空白格式。
- 人工审查 API 路径与 `docs/api/orders.md`、后端 `AdminOrderController` 一致。
