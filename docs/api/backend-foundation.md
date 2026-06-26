# Backend Foundation API

当前文档记录阶段 1 后端基础能力中已经实现的接口和内部契约。

## 统一响应结构

成功响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {},
  "requestId": "uuid-or-client-request-id",
  "timestamp": "2026-06-26T14:00:00+08:00"
}
```

错误响应：

```json
{
  "code": 404,
  "message": "resource not found",
  "path": "/api/not-found",
  "requestId": "uuid-or-client-request-id",
  "timestamp": "2026-06-26T14:00:00+08:00"
}
```

请求可通过 `X-Request-Id` 传入请求标识；未传入时后端自动生成，并在响应头返回。

当前错误分层：

- `400`：请求参数缺失、空白或类型不正确。
- `404`：接口路径不存在，或内容源明确返回资源不存在。
- `502`：内容源返回非 404 的 HTTP 错误响应。
- `503`：内容源连接失败、超时或请求无法完成。
- `500`：未分类的后端内部错误。

## 已实现接口

### `GET /api/system/health`

返回 Spring Boot 业务服务健康状态。

### `GET /api/app/content/search?keywords={keywords}`

通过 `ContentProvider` 调用 Flask ReelShort 内容源搜索剧集，并转换为平台内部内容模型。

### `GET /api/app/home/recommend`

通过内容缓存服务获取推荐货架。内容源调用成功后写入 PostgreSQL 缓存；内容源不可用且缓存存在时返回最后一次可用缓存。

### `GET /api/app/content/shelves/{shelfType}`

通过内容缓存服务获取指定货架。支持 `recommend`、`new-release`、`drama-dub`。

### `GET /api/app/content/books/{bookId}/episodes?filteredTitle={filteredTitle}`

通过 `ContentProvider` 获取指定剧集的分集列表。

### `GET /api/app/content/books/{bookId}/episodes/{episodeNum}/play?filteredTitle={filteredTitle}&chapterId={chapterId}`

通过 `ContentProvider` 获取指定分集的 HLS 播放地址、时长和下一集信息。

## 内容源适配契约

`ContentProvider` 当前定义：

- `search(String keywords)`
- `getShelf(ContentShelfType shelfType)`
- `getEpisodes(String bookId, String filteredTitle)`
- `getVideoUrl(String bookId, int episodeNum, String filteredTitle, String chapterId)`

当前实现为 `FlaskContentProvider`，默认内容源地址：

```properties
reelshort.content-provider.base-url=http://127.0.0.1:5000
spring.http.client.connect-timeout=2s
spring.http.client.read-timeout=5s
```
