# Content Cache API

当前文档记录内容货架和缓存管理接口。App 和后台仍只访问 Spring Boot，不直接访问 Flask 内容源。

## 支持的货架类型

| API 值 | 内容源端点 | 说明 |
| --- | --- | --- |
| `recommend` | `/api/v1/reelshort/recommend` | 首页推荐。 |
| `new-release` | `/api/v1/reelshort/newrelease` | 新剧。 |
| `drama-dub` | `/api/v1/reelshort/dramadub` | 配音分类。 |

## App API

以下 App 内容接口都保留可选 `locale` 参数以求兼容，但**当前只支持英文单语 `en`**：任何传入值都会被解析为 `en`，缓存也只保存一套英文元数据。观看记录、收藏、点赞和评论按 `bookId` 关联，不按语言拆分。

> 历史说明：`zh-TW` 与"按 locale 分桶/双语缓存"的表述已失效。后端 `ContentLocale` 只保留 `ENGLISH`（源码注释明示 app is English-only），Flask 内容源 `SUPPORTED_LOCALES = {"en"}`，Android `AppLanguage` 也只保留 `ENGLISH`。`REELSHORT_CONTENT_REFRESH_LOCALES` 默认 `en`；写多个值只会让同一货架按同一语言被重复刷新，不会产生第二套缓存。

点赞、收藏和评论接口使用本地社交表作为真实数据源；当某个 `bookId` 没有真实互动数据时，后端按 `bookId` 生成稳定的展示基线计数和少量伪评论，不落库，真实用户互动会叠加在展示基线之上。评论列表分页返回（`page` 默认 `0`、`size` 默认 `20`、上限 `50`），真实评论始终排在伪评论之前。

### `GET /api/app/home/recommend?locale={locale}`

返回推荐货架内容。后端优先读取 PostgreSQL 货架缓存；缓存不存在时才调用 Flask 内容源并写入 `content_shelf_cache` 和 `content_book_cache`。后台刷新接口负责主动更新片库元数据。

### `GET /api/app/content/shelves/{shelfType}?locale={locale}`

返回指定货架内容。`shelfType` 必须是 `recommend`、`new-release` 或 `drama-dub`。读取策略与首页推荐一致：缓存优先，缺失时才拉取内容源。

### `GET /api/app/content/books/{bookId}?locale={locale}`

返回已缓存剧集详情。详情来自搜索、推荐或货架写入的 PostgreSQL 内容书缓存；查看分集列表时内容源返回的书籍元信息也会自动回填缓存。没有缓存时返回 `404`。

### `GET /api/app/content/books/{bookId}/episodes?filteredTitle={filteredTitle}&locale={locale}`

返回剧集分集列表。后端优先读取 PostgreSQL 分集缓存；缓存不存在或缓存 JSON 损坏时才调用 Flask 内容源，调用成功后写入 `content_episode_cache`，并顺带把内容源返回的书籍元信息回填到详情缓存。缓存损坏且刷新失败时返回内容源错误，并记录最近错误。

### `GET /api/app/content/books/{bookId}/episodes/{episodeNum}/play?filteredTitle={filteredTitle}&chapterId={chapterId}&locale={locale}`

返回单集播放地址。播放地址具备时效性，正常路径总是实时调用内容源获取最新地址，成功后写入 PostgreSQL 播放缓存；当内容源返回 `502`/`503`（不可用）且已有短期缓存时，才回退返回最后一次播放地址缓存；内容源返回 `404`（该集确实不存在）时不回退。

对 App 的播放响应在内容源字段之外增加 `estimatedRewardPoints`。该字段由后端在每次播放请求中按权威 `duration`、当前积分配置、登录用户的十分位尾数和当日剩余额度计算，不进入 `ContentVideo` 缓存；因此运营修改积分配置或用户账户奖励状态变化后，重新打开分集即可取得新的预计值。旧版 App 可忽略该字段。

播放地址缓存只作为短期故障兜底，不参与片库预热。兜底 TTL 由 `REELSHORT_CONTENT_VIDEO_FALLBACK_TTL` 控制，默认 `10m`；设置为 `0` 可禁用播放地址缓存兜底。

## 元数据刷新策略

首页、货架、剧集详情和分集列表等元数据以 PostgreSQL 自有缓存为主。上游新增短剧时，不依赖 App 首页实时拉上游，而是通过两条路径进入自有库：

- 后台 `POST /api/admin/content/cache/shelves/{shelfType}/refresh` 强制刷新指定货架。
- 后台 `POST /api/admin/content/cache/shelves/{shelfType}/refresh-locales` 按 `ContentLocale` 枚举逐个刷新；当前枚举只有 `en`，因此等价于刷新一次英文货架。
- 后端定时任务默认每 6 小时刷新 `recommend` 货架的 `en` 元数据，可通过 `REELSHORT_CONTENT_REFRESH_ENABLED`、`REELSHORT_CONTENT_REFRESH_INTERVAL`、`REELSHORT_CONTENT_REFRESH_SHELVES` 和 `REELSHORT_CONTENT_REFRESH_LOCALES` 调整。
- 面向 App 的读路径只读缓存；缓存缺失时的上游调用发生在数据库事务之外，同一 `(shelfType, locale)` 的并发 miss 由 single-flight 去重，只有一次真正打到上游。
- 后台手动刷新和后端定时刷新都会写入 `content_refresh_runs`，用于后台查看最近刷新来源、locale、状态、耗时、返回数量和失败原因。
- Flask 内容源提供 `/diagnostics` 内部诊断端点，记录搜索空结果、Next data 404、播放页 HTML 解析失败、播放地址缺失等最近事件；后端运行诊断页会展示结构化事件总数、类型计数和最近事件上下文，方便定位上游结构变化。

视频文件和播放流不存放在自有服务器；只有播放页请求单集播放地址时才按需调用内容源。

错误：

- `400`：未知货架类型。
- `404`：内容源明确返回资源不存在且无可用缓存，或剧集详情未缓存。
- `502`：内容源返回非 404 HTTP 错误且无可用缓存。
- `503`：内容源不可用且无可用缓存。

## Admin API

### `GET /api/admin/content/cache`

返回内容缓存状态。

响应字段：

- `bookCount`：当前缓存的剧集索引数量。
- `episodeCacheCount`：当前缓存的剧集分集列表数量。
- `videoCacheCount`：当前缓存的播放地址数量；播放地址只作为内容源不可用时的短 TTL 兜底，不参与片库预热。
- `shelves`：每个货架和 locale 组合的缓存状态。
- `shelves[].shelfType`
- `shelves[].locale`
- `shelves[].itemCount`
- `shelves[].refreshedAt`
- `shelves[].lastError`
- `shelves[].health`：`HEALTHY`、`STALE`、`EMPTY`、`ERROR` 或 `MISSING`。
- `shelves[].healthMessage`：后台可展示的健康说明。`STALE` 表示该货架超过 12 小时未成功刷新；`ERROR` 优先展示最近错误。
- `recentRefreshRuns`：最近 10 次货架刷新运行记录。
- `recentRefreshRuns[].triggerSource`：`ADMIN` 或 `SCHEDULED`。
- `recentRefreshRuns[].shelfType`
- `recentRefreshRuns[].locale`
- `recentRefreshRuns[].status`：`SUCCESS` 或 `FAILED`。
- `recentRefreshRuns[].startedAt`
- `recentRefreshRuns[].finishedAt`
- `recentRefreshRuns[].durationMillis`
- `recentRefreshRuns[].itemCount`
- `recentRefreshRuns[].errorMessage`

### `POST /api/admin/content/cache/shelves/{shelfType}/refresh`

强制刷新指定货架缓存，支持可选 `locale` 参数（当前仅 `en`，传其他值同样解析为 `en`）。成功后写入 PostgreSQL 元数据缓存、刷新运行记录和后台审计日志 `CONTENT_CACHE_REFRESHED`。

刷新失败时返回内容源错误，不伪装成功；如果此前有缓存，失败原因会记录到该货架的 `lastError`，同时写入一条 `FAILED` 刷新运行记录。

### `POST /api/admin/content/cache/shelves/{shelfType}/refresh-locales`

按后端支持的 locale 枚举逐个刷新指定货架。当前枚举只有一个值 `en`，因此本接口等价于刷新一次英文货架；接口仍保留逐个 locale 循环与逐行结果的形态，以便将来新增语言时不需要改协议。每个 locale 写入独立的 `content_refresh_runs` 运行记录，批量操作本身写入后台审计日志 `CONTENT_CACHE_REFRESHED_LOCALES`。

单个 locale 刷新失败不会中断其他 locale。只要请求本身合法，接口返回 `200`，并在响应行里用 `status=FAILED` 表达局部失败；未知 `shelfType` 仍返回 `400`。

响应 `data[]` 字段：

- `shelfType`：货架 API 值，例如 `recommend`。
- `locale`：刷新语言，当前固定为 `en`。
- `status`：`SUCCESS` 或 `FAILED`。
- `itemCount`：成功刷新时写入的短剧数量；失败时为 `0`。
- `errorMessage`：失败原因；成功时为 `null`。
