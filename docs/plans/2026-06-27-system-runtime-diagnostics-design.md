# 后台运行诊断设计

## 背景

ReelShort 已经具备单机部署、数据库迁移、Redis 限流、内容源服务、后台审计和备份恢复基础，但后台仍缺少面向运维的只读运行诊断入口。当前管理员只能看业务数据和审计日志，无法在后台快速判断后端自身、数据库、Redis、内容源和 JVM 运行状态。

本切片补齐 `system/runtime` 基础能力：后台管理员可以通过 Spring Boot API 和后台 Web 查看运行状态摘要。它不是完整监控平台，也不读取服务器日志文件；目标是在单机部署早期提供低成本、可验证、不会暴露敏感信息的诊断视图。

## 方案选择

### 方案 A：直接暴露 Actuator 端点

改动少，但 Actuator 原始信息粒度和安全边界不完全适合后台运营视图，也不方便聚合内容源和 Redis 状态。

### 方案 B：新增后台运行诊断 API

在 `backend/system` 下新增只读服务，聚合 JVM、数据库、Redis、内容源和配置状态，以稳定 DTO 返回。后台 Web 只调用该 API，不直接访问 Actuator。推荐采用该方案。

### 方案 C：引入完整监控栈

例如 Prometheus、Grafana、日志采集和告警平台。长期可以做，但当前单机架构下会增加运维复杂度。

## 接口设计

新增后台接口：

- `GET /api/admin/system/runtime`

权限：

- 新增 `SYSTEM_RUNTIME_READ`
- 默认超级管理员拥有该权限
- 普通 App Token 不能访问

响应结构：

```json
{
  "status": "UP",
  "checkedAt": "2026-06-27T17:30:00+08:00",
  "application": {
    "service": "reelshort-backend",
    "version": "0.0.1-SNAPSHOT",
    "javaVersion": "17",
    "uptimeSeconds": 120
  },
  "memory": {
    "usedBytes": 1000000,
    "maxBytes": 2000000
  },
  "dependencies": [
    {
      "name": "database",
      "status": "UP",
      "detail": "validated"
    },
    {
      "name": "redis",
      "status": "UP",
      "detail": "pong"
    },
    {
      "name": "content-provider",
      "status": "UP",
      "detail": "reachable"
    }
  ]
}
```

总体 `status` 规则：

- 所有依赖为 `UP` 时返回 `UP`
- 任一依赖为 `DOWN` 时返回 `DEGRADED`
- 诊断接口本身仍返回 HTTP 200，具体问题由 dependency status 表达，避免后台页面因单个依赖异常无法展示其它信息

## 后端设计

新增类：

- `SystemRuntimeController`
- `SystemRuntimeService`
- `SystemRuntimeResponse`
- `RuntimeDependencyStatus`

依赖检查：

- 数据库：使用 `DataSource` 获取连接并执行 `Connection#isValid(1)`
- Redis：使用 `StringRedisTemplate` 执行 `PING`
- 内容源：使用配置的 `reelshort.content-provider.base-url` 调用 `/health`

安全边界：

- 不返回数据库 URL、用户名、密码、Redis 地址、内容源完整错误堆栈或环境变量
- 异常只归纳成简短 `detail`
- 只允许后台管理员读取，并受 `SYSTEM_RUNTIME_READ` 控制

## 前端设计

后台 Web 新增“运行诊断”菜单和页面：

- 顶部显示总体状态、服务名、版本、运行时间
- 展示 JVM 内存使用
- 表格展示 database、redis、content-provider 的状态和说明
- 页面加载失败时显示统一错误消息

页面只做只读展示，不提供重启服务、清理缓存、执行恢复等破坏性操作。

## 测试策略

后端：

- 服务测试覆盖所有依赖 `UP` 时总体 `UP`
- 服务测试覆盖单个依赖异常时总体 `DEGRADED`
- Controller 测试覆盖需要后台权限、返回统一响应和 dependency 数组

前端：

- TypeScript 构建验证 API 类型和页面引用
- `npm run build` 验证路由和组件可构建

全量验证：

- `backend/gradlew.bat test --no-daemon`
- `android-app/gradlew.bat :app-core:test --no-daemon`
- `content-provider pytest`
- `admin-web npm ci && npm run build`
