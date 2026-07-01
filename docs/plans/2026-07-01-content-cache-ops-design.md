# Content Cache Operations Design

## Goal

建立内容缓存运维闭环，让后台可以判断推荐货架刷新是否成功、各 locale 缓存是否健康、最近失败原因是什么。

## Recommended Approach

本阶段采用后端刷新状态闭环优先方案。后端新增刷新运行记录，所有定时刷新和后台手动刷新都写入 PostgreSQL；后台缓存状态接口返回按 locale 分桶的货架健康和最近刷新任务；后台 Web 在内容缓存页展示这些数据。

## Scope

本批只做内容源与缓存运维闭环的第一阶段：

- 保存刷新任务状态：触发来源、货架、locale、状态、开始时间、结束时间、耗时、返回数量、错误。
- 扩展后台缓存状态响应：总缓存数量、按 locale 的货架状态、最近刷新记录。
- 后台内容缓存页显示 locale、最近刷新任务和错误摘要。
- 文档更新 API 和运维说明。

本批不做：

- 不新增复杂指标系统或趋势图。
- 不改 App API 和 Android 客户端。
- 不在片库刷新时预抓视频流。
- 不引入新的外部监控依赖。

## Data Model

新增表 `content_refresh_runs`：

- `id uuid primary key`
- `trigger_source varchar(32)`：`ADMIN` 或 `SCHEDULED`
- `shelf_type varchar(32)`
- `locale varchar(32)`
- `status varchar(32)`：`SUCCESS` 或 `FAILED`
- `started_at timestamptz`
- `finished_at timestamptz`
- `duration_millis bigint`
- `item_count integer`
- `error_message varchar(1024)`

刷新记录只用于运维观测，不参与 App 内容读取路径。

## Backend Flow

- `AdminContentCacheController.refreshShelf()` 调用新的记录型刷新入口，trigger source 为 `ADMIN`。
- `ContentMetadataRefreshService.refreshShelves()` 调用同一记录型刷新入口，trigger source 为 `SCHEDULED`。
- `ContentCacheService.refreshShelf()` 保持原有业务能力，继续给其他路径使用。
- `ContentCacheService` 提供记录型刷新入口，封装刷新记录创建、成功/失败更新和最近记录查询，避免为当前阶段增加额外服务层。
- `ContentCacheService.status()` 返回所有 `ContentLocale` 与 `ContentShelfType` 组合的状态，避免只看到英文缓存。

## Admin Web Flow

- `ContentCacheStatus` 类型增加 `videoCacheCount`、`recentRefreshRuns` 和 shelf `locale`。
- 内容缓存页增加 locale 选择，手动刷新时带 `locale` 参数。
- 表格展示：货架、locale、条目数、最近刷新、最近错误。
- 增加最近刷新任务表：来源、货架、locale、状态、耗时、数量、错误。

## Error Handling

- 刷新成功：记录 `SUCCESS`、`itemCount`、`durationMillis`，清空本次错误。
- 刷新失败：记录 `FAILED`、错误信息和耗时，保留原有 `ContentProviderException` 行为，后台手动刷新仍返回错误。
- 单个定时任务失败不影响其他 shelf/locale 继续刷新。

## Testing

- 后端服务测试：成功刷新写入成功 run；provider 失败写入失败 run；定时刷新按 locale 分别记录。
- 后端 controller 测试：状态接口返回 locale shelf 状态和最近刷新记录；手动刷新带 locale 写入 ADMIN run。
- Flyway 迁移测试：新表存在并可写入。
- admin-web 构建验证类型和页面编译。
