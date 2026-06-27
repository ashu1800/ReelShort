# Admin Web Foundation Design

## Goal

把后台管理网站从占位控制台推进到可连接 Spring Boot Admin API 的基础运营界面。后台 Web 仍只访问 Spring Boot，不直接访问数据库、Redis 或 Flask 内容源。

## Current State

当前 `admin-web` 只有一个 Dashboard 占位页、单一路由、基础 Axios 实例和空 Pinia session。后端已经具备管理员登录、RBAC、用户列表/详情、内容缓存状态、系统配置和审计日志接口，但前端没有登录、Token 注入、路由守卫或数据视图。

## Scope

本切片实现：

- 登录页：调用 `POST /api/admin/auth/login`，保存管理员 Token。
- 会话持久化：刷新页面后保留 Token 和管理员名，退出后清理。
- HTTP 拦截器：Admin API 自动携带 `Authorization: Bearer <token>`，收到 `401` 后清理会话并回到登录页。
- 路由守卫：未登录访问后台页面跳转登录。
- 基础布局：左侧导航、顶部管理员信息和退出按钮。
- 用户列表视图：调用 `GET /api/admin/users`。
- 内容缓存视图：调用 `GET /api/admin/content/cache`，展示 `bookCount`、`episodeCacheCount` 和货架状态。
- 审计日志视图：调用 `GET /api/admin/audit-logs`。

本切片不实现：

- 用户状态变更、积分调整表单。
- 系统配置编辑。
- 角色/权限管理 UI。
- 复杂分页、筛选、导出。
- 前端自动化 E2E；本阶段使用 TypeScript 构建验证。

## UX

后台是运营工具，不做落地页。第一屏登录后进入工作台，展示用户、缓存和日志入口。界面保持密度适中、导航明确、操作少而稳定；所有数据视图提供加载、错误和空状态。

## API Contracts

使用统一响应：

```ts
type ApiResponse<T> = {
  code: number
  message: string
  data: T
  requestId: string
  timestamp: string
}
```

基础端点：

- `POST /api/admin/auth/login`
- `GET /api/admin/users`
- `GET /api/admin/content/cache`
- `GET /api/admin/audit-logs`

## Testing

- `npm run build` 必须通过。
- TypeScript 类型覆盖 API 响应、用户列表、缓存状态和审计日志。
- 手工审查路由守卫和 HTTP 拦截器，确保 App Token/匿名状态不会被前端误用。

