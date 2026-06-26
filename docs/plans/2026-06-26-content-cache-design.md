# Content Cache Design

## Goal

补齐内容域的长期基础能力：App 可通过 Spring Boot 获取推荐、新剧、配音等内容货架；Spring Boot 将内容源结果缓存到 PostgreSQL；后台可查看缓存状态并主动刷新缓存。

## Recommended Approach

采用 PostgreSQL 持久缓存作为第一步，而不是马上引入 Redis 或后台调度。原因是当前阶段已有 JPA/H2 测试基础，PostgreSQL 缓存能满足“Flask 不可用时仍返回最后可用数据”的核心要求，也符合 Redis 丢失不影响正确性的架构约束。

备选方案：

- 只做内存缓存：实现快，但服务重启后丢失，不能满足长期单机部署和缓存状态管理。
- 直接引入 Redis：更贴近热点缓存目标，但当前项目还没有 Redis 依赖和部署验证，容易扩大范围。
- PostgreSQL 缓存优先：可测试、可审计、可重启保留，后续再加 Redis 短缓存。

本切片选择 PostgreSQL 缓存优先。

## Architecture

- `ContentProvider` 新增 `getShelf(ContentShelfType shelfType)`，隐藏 Flask 的具体端点。
- `FlaskContentProvider` 将 `RECOMMEND`、`NEW_RELEASE`、`DRAMA_DUB` 映射到 `/recommend`、`/newrelease`、`/dramadub`。
- `ContentCacheService` 作为内容业务入口，App 和 Admin Controller 不再直接调用 provider。
- `ContentShelfCache` 保存货架类型、内容 JSON、条目数量、刷新时间、最近错误。
- `ContentBookCache` 保存搜索和货架中出现过的剧集索引，作为后台缓存状态的基础统计。
- `AdminContentCacheController` 提供缓存状态和货架刷新接口，并写入后台审计日志。

## Public Interfaces

App:

- `GET /api/app/home/recommend`
- `GET /api/app/content/shelves/{shelfType}`

Admin:

- `GET /api/admin/content/cache`
- `POST /api/admin/content/cache/shelves/{shelfType}/refresh`

支持的 `shelfType`：

- `recommend`
- `new-release`
- `drama-dub`

## Data Flow

App 请求推荐或货架时，后端先调用 Flask 内容源。调用成功后写入 PostgreSQL 缓存并返回新数据。调用失败时，如果存在对应货架缓存，返回缓存数据；如果没有缓存，则按现有内容源错误分层返回 `502/503/404`。

后台刷新货架时，后端强制调用 Flask 内容源并更新缓存。刷新失败时记录最近错误并返回明确错误，不伪装成功。

## Error Handling

- 未知货架类型返回 `400 bad request`。
- 内容源可用时以内容源为准，并覆盖缓存。
- 内容源不可用且有缓存时返回缓存，保证 App 首页可用性。
- 内容源不可用且无缓存时沿用 `ContentProviderException` 的统一错误响应。

## Test Strategy

- Provider 测试覆盖三个 Flask 货架端点映射。
- Service 测试覆盖成功刷新、失败回退缓存、无缓存时抛出内容源异常。
- App Controller 测试覆盖推荐和货架响应。
- Admin Controller 测试覆盖缓存状态、刷新、App Token 不能访问后台缓存接口。
- 全量后端测试确保已有搜索、剧集、播放、观看、积分、后台能力不回归。
