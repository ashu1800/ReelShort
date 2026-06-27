# Admin Dashboard Summary Design

## Goal

把后台控制台从“前端并行拉多个列表再本地聚合”升级为“后端提供单一运营摘要 API”。控制台只请求一个接口即可拿到用户、订单、内容缓存、支付事件和最近审计日志摘要。

## Context

当前 `DashboardView.vue` 会并行调用 `fetchUsers()`、`fetchContentCacheStatus()`、`fetchAuditLogs()` 和 `fetchOrders()`，再在前端计算用户总数、禁用用户、订单数量等指标。这有几个问题：

- 控制台为了几个指标拉全量列表，数据量增长后会变慢。
- 控制台依赖多个后台接口和权限，任一接口失败会影响整体体验。
- 指标语义散落在前端，不利于后续运营口径统一。
- 支付事件等已有模块没有进入统一 dashboard 摘要。

## Options

推荐方案：新增 `GET /api/admin/dashboard/summary`，由后端聚合统计和最近审计日志。前端控制台只调用该接口。后端第一阶段使用现有 repository 的 `count`、`findAll` 和少量内存聚合，保持实现简单；后续数据增长后再把热点指标替换为专用 count query。

备选方案 1：继续前端并行请求，但加容错。改动小，但仍保留全量拉列表和指标口径分散的问题。

备选方案 2：引入独立报表模块和物化统计表。可扩展性更强，但当前单机早期阶段过重，且会增加定时汇总和一致性问题。

## Design

- 新增后台权限：
  - `DASHBOARD_READ`：读取后台控制台摘要。
  - 默认超级管理员自动拥有该权限。
- 新增后端接口：
  - `GET /api/admin/dashboard/summary`
  - 要求后台 Token 和 `DASHBOARD_READ` 权限。
- 新增响应模型 `AdminDashboardSummaryResponse`：
  - `users.total`
  - `users.disabled`
  - `orders.total`
  - `orders.created`
  - `orders.paid`
  - `orders.totalAmountCents`
  - `payments.total`
  - `payments.processed`
  - `payments.rejected`
  - `content.bookCount`
  - `content.episodeCacheCount`
  - `content.shelfCount`
  - `auditLogs.latest`，最多 5 条，复用审计日志响应结构。
- 新增 `AdminDashboardService`：
  - 从现有 repository 聚合摘要。
  - 审计日志按创建时间倒序取前 5 条；如果 repository 还没有分页方法，新增 `findTop5ByOrderByCreatedAtDesc()`。
- 前端：
  - `adminApi.ts` 增加 `fetchDashboardSummary()` 类型和 API 方法。
  - `DashboardView.vue` 改为调用单一接口，并展示用户、订单、支付、内容和最近审计日志。
  - 控制台不再直接依赖 `fetchUsers()`、`fetchOrders()`、`fetchContentCacheStatus()` 和 `fetchAuditLogs()`。

## Test Plan

- 后端 MockMvc 测试：
  - 未登录访问 dashboard summary 返回 `401`。
  - 使用普通 App Token 返回 `401`。
  - 后台管理员访问返回统一成功响应。
  - 返回用户、订单、支付、内容缓存和最近 5 条审计日志摘要。
- Service 测试：
  - 覆盖 paid/created/rejected 等状态聚合。
  - 覆盖审计日志只返回最近 5 条。
- 前端构建：
  - `npm run build` 验证 TypeScript 类型与模板绑定。
- 全量验证：
  - 后端全量测试、admin-web build、content-provider pytest、`git diff --check`。

## Out of Scope

- 不做复杂时间范围筛选。
- 不做趋势图、同比环比和导出。
- 不引入统计缓存或物化视图。
- 不改变现有列表接口和页面行为。
