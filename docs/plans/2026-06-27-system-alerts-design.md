# System Alerts Design

## 背景

项目已具备后台运行诊断和系统日志查看，但阶段 2 仍缺少“异常告警”能力。管理员现在需要主动打开诊断页才能发现数据库、Redis、内容源等依赖异常，系统不会沉淀可追踪的异常事件。

## 目标

新增后台异常告警模块，将运行诊断中的异常状态保存为可查看、可确认、可恢复的告警记录。第一阶段只做单机内置告警，不接入短信、邮件、Webhook 或外部告警平台。

## 方案选择

推荐方案：复用 `SystemRuntimeService.snapshot()` 的诊断结果，由 `SystemAlertService` 评估依赖状态并写入 `system_alerts` 表。后台提供只读列表和确认接口，Vue 后台新增“异常告警”页面。该方案改动集中、可测试，也符合当前单机部署定位。

备选方案一：只在前端运行诊断页显示异常。实现最少，但异常不会持久化，管理员无法追踪发生时间、恢复时间和处理状态。

备选方案二：直接引入外部告警平台。能力更完整，但当前没有多机或外部通知需求，会增加部署复杂度。

## 告警模型

`system_alerts` 表字段：

- `id`：UUID 主键。
- `alert_key`：告警唯一键，例如 `runtime:dependency:redis`。
- `severity`：`WARNING` 或 `CRITICAL`。
- `status`：`OPEN`、`ACKNOWLEDGED`、`RESOLVED`。
- `title`：告警标题。
- `detail`：脱敏说明。
- `first_seen_at`：首次发现时间。
- `last_seen_at`：最近仍异常时间。
- `acknowledged_at` / `acknowledged_by`：确认信息。
- `resolved_at`：恢复时间。

唯一约束：`alert_key`。同一个异常重复出现时更新同一条记录，而不是不断插入重复告警。

## 后端设计

新增 `backend/system/alerts` 子模块：

- `SystemAlert`：JPA 实体。
- `SystemAlertRepository`：按状态和时间查询。
- `SystemAlertService`：评估运行诊断，创建/更新/恢复告警，提供查询和确认。
- `SystemAlertController`：`/api/admin/system/alerts` 查询和确认接口。
- `SystemAlertEvaluationService`：后台触发诊断评估，可由运行诊断页刷新时调用。

权限：

- `SYSTEM_ALERT_READ`：查看告警。
- `SYSTEM_ALERT_WRITE`：确认告警。

告警生成规则：

- 当整体状态 `DEGRADED` 时，为每个 `DOWN` 依赖生成告警。
- 依赖告警 key 为 `runtime:dependency:<name>`。
- `database` 为 `CRITICAL`，其他依赖为 `WARNING`。
- 依赖恢复为 `UP` 时，对应 open/acknowledged 告警标记为 `RESOLVED`。
- 评估异常不会影响运行诊断接口可用性。

## 前端设计

后台 Web 新增“异常告警”页面：

- 页面路径：`/system-alerts`。
- 默认展示全部告警，支持按状态筛选。
- 提供“刷新诊断并评估”按钮。
- 对 `OPEN` 告警提供“确认”操作。

## 测试

后端测试覆盖：

- DOWN 依赖创建告警。
- 重复 DOWN 只更新同一告警。
- 依赖恢复后标记 resolved。
- 管理员查询和确认告警。
- 未登录、缺少权限、默认管理员权限引导。

前端通过 TypeScript 构建验证页面、路由和 API 类型。
