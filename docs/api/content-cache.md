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

返回推荐货架内容。后端优先调用 Flask 内容源；调用成功后写入 PostgreSQL 缓存；内容源不可用且已有缓存时返回最后一次可用缓存。

### `GET /api/app/content/shelves/{shelfType}`

返回指定货架内容。`shelfType` 必须是 `recommend`、`new-release` 或 `drama-dub`。

错误：

- `400`：未知货架类型。
- `404`：内容源明确返回资源不存在且无可用缓存。
- `502`：内容源返回非 404 HTTP 错误且无可用缓存。
- `503`：内容源不可用且无可用缓存。

## Admin API

### `GET /api/admin/content/cache`

返回内容缓存状态。

响应字段：

- `bookCount`：当前缓存的剧集索引数量。
- `shelves`：每个货架的缓存状态。
- `shelves[].shelfType`
- `shelves[].itemCount`
- `shelves[].refreshedAt`
- `shelves[].lastError`

### `POST /api/admin/content/cache/shelves/{shelfType}/refresh`

强制刷新指定货架缓存。成功后写入后台审计日志 `CONTENT_CACHE_REFRESHED`。

刷新失败时返回内容源错误，不伪装成功；如果此前有缓存，失败原因会记录到该货架的 `lastError`。
