# Admin Web Operations Design

## Goal

完善 Vue 后台运营界面，把后端已经具备的用户状态管理、积分调整、观看/积分记录、系统配置和内容缓存刷新能力暴露给运营人员使用。

## Scope

本阶段只接入已有 Spring Boot Admin API，不新增后端业务接口。后台 Web 继续只访问 `/api/admin/**`，不直接访问 PostgreSQL、Redis 或 Flask 内容源。

## Approach

采用增量扩展现有后台壳的方案：

- `UsersView` 从只读列表升级为列表加详情抽屉。
- 用户详情抽屉中展示用户详情、状态变更、积分调整、观看记录和积分流水。
- 新增 `SystemConfigsView`，展示系统配置并支持单项编辑提交。
- `ContentCacheView` 增加按货架刷新缓存的操作入口。
- `adminApi.ts` 补齐 typed API 方法，视图层只处理展示、输入校验和操作反馈。

该方案避免扩张后端边界，也不提前建设复杂权限菜单。后端 RBAC 仍作为最终权限裁决；前端遇到 `403` 时显示操作失败。

## Components

### API Layer

`admin-web/src/services/adminApi.ts` 增加：

- `fetchUserDetail(userId)`
- `updateUserStatus(userId, status)`
- `adjustUserPoints(userId, amount, reason)`
- `fetchUserWatchRecords(userId)`
- `fetchUserPointRecords(userId)`
- `fetchSystemConfigs()`
- `updateSystemConfig(configKey, value)`
- `refreshContentShelf(shelfType)`

### User Operations

`UsersView` 保持表格为主，点击用户后打开抽屉。抽屉内使用 tabs 划分：

- 概览：用户状态、积分、记录数量、创建时间。
- 运营操作：启用/禁用、积分调整。
- 观看记录：最近观看进度。
- 积分流水：积分变动记录。

积分调整前端只做基础校验：金额不能为 `0`，原因必填。余额不能小于 `0` 由后端负责校验。

### System Configs

新增 `/system-configs` 路由和导航。页面展示配置 key、value、description、updatedAt，并支持行内编辑单个配置值。保存后使用后端返回值更新本地表格。

### Content Cache

内容缓存页面增加货架刷新按钮。第一阶段使用固定候选值：

- `recommend`
- `new`
- `dubbed`

如果后端不支持某个值，错误由后端返回并在前端显示。

## Error Handling

- API 调用失败时展示简洁错误提示，不吞掉页面已有数据。
- 401 继续沿用已有 Axios interceptor 清理会话并跳转登录。
- 403、400、404 显示操作失败提示，后续可在统一错误层解析后端 message。

## Testing

本阶段前端项目尚未引入单元测试框架，验证方式为：

- `npm run build` 验证 TypeScript 和生产构建。
- `git diff --check` 验证格式空白。
- 人工审查 API 路径、DTO 字段和后端文档/控制器一致性。

