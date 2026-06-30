# Content Cache API

当前文档记录内容货架和缓存管理接口。App 和后台仍只访问 Spring Boot，不直接访问 Flask 内容源。

## 支持的货架类型

| API 值 | 内容源端点 | 说明 |
| --- | --- | --- |
| `recommend` | `/api/v1/reelshort/recommend` | 首页推荐。 |
| `new-release` | `/api/v1/reelshort/newrelease` | 新剧。 |
| `drama-dub` | `/api/v1/reelshort/dramadub` | 配音分类。 |

## App API

### `GET /api/app/home/recommend`

返回推荐货架内容。后端优先读取 PostgreSQL 货架缓存；缓存不存在时才调用 Flask 内容源并写入 `content_shelf_cache` 和 `content_book_cache`。后台刷新接口负责主动更新片库元数据。

### `GET /api/app/content/shelves/{shelfType}`

返回指定货架内容。`shelfType` 必须是 `recommend`、`new-release` 或 `drama-dub`。读取策略与首页推荐一致：缓存优先，缺失时才拉取内容源。

### `GET /api/app/content/books/{bookId}`

返回已缓存剧集详情。详情来自搜索、推荐或货架写入的 PostgreSQL 内容书缓存；查看分集列表时内容源返回的书籍元信息也会自动回填缓存。没有缓存时返回 `404`。

### `GET /api/app/content/books/{bookId}/episodes?filteredTitle={filteredTitle}`

返回剧集分集列表。后端优先读取 PostgreSQL 分集缓存；缓存不存在或缓存 JSON 损坏时才调用 Flask 内容源，调用成功后写入 `content_episode_cache`，并顺带把内容源返回的书籍元信息回填到详情缓存。缓存损坏且刷新失败时返回内容源错误，并记录最近错误。

### `GET /api/app/content/books/{bookId}/episodes/{episodeNum}/play?filteredTitle={filteredTitle}&chapterId={chapterId}`

返回单集播放地址。播放地址具备时效性，正常路径总是实时调用内容源获取最新地址，成功后写入 PostgreSQL 播放缓存；当内容源返回 `502`/`503`（不可用）且已有缓存时，回退返回最后一次播放地址缓存（尽力而为，旧地址可能已失效）；内容源返回 `404`（该集确实不存在）时不回退。

## 元数据刷新策略

首页、货架、剧集详情和分集列表等元数据以 PostgreSQL 自有缓存为主。上游新增短剧时，不依赖 App 首页实时拉上游，而是通过两条路径进入自有库：

- 后台 `POST /api/admin/content/cache/shelves/{shelfType}/refresh` 强制刷新指定货架。
- 后端定时任务默认每 6 小时刷新 `recommend` 货架，可通过 `REELSHORT_CONTENT_REFRESH_ENABLED`、`REELSHORT_CONTENT_REFRESH_INTERVAL` 和 `REELSHORT_CONTENT_REFRESH_SHELVES` 调整。

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
- `shelves`：每个货架的缓存状态。
- `shelves[].shelfType`
- `shelves[].itemCount`
- `shelves[].refreshedAt`
- `shelves[].lastError`

### `POST /api/admin/content/cache/shelves/{shelfType}/refresh`

强制刷新指定货架缓存。成功后写入 PostgreSQL 元数据缓存和后台审计日志 `CONTENT_CACHE_REFRESHED`。

刷新失败时返回内容源错误，不伪装成功；如果此前有缓存，失败原因会记录到该货架的 `lastError`。
