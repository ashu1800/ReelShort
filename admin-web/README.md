# ReelShort Admin Web

Vue + TypeScript + Element Plus 后台管理网站。后台只访问 Spring Boot Admin API，不直接访问数据库、Redis 或 Flask 内容源。

## Commands

```bash
npm install
npm run dev
npm run build
```

## API

默认 API 根路径为 `/api/admin`，可通过 `VITE_API_BASE_URL` 覆盖。

当前基础视图：

- 登录页：`POST /api/admin/auth/login`
- 控制台：用户、缓存和审计摘要
- 用户管理：`GET /api/admin/users`
- 内容缓存：`GET /api/admin/content/cache`
- 审计日志：`GET /api/admin/audit-logs`
