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

## 已实现接口

### `GET /api/system/health`

返回 Spring Boot 业务服务健康状态。

### `GET /api/app/content/search?keywords={keywords}`

通过 `ContentProvider` 调用 Flask ReelShort 内容源搜索剧集，并转换为平台内部内容模型。

## 内容源适配契约

`ContentProvider` 当前定义：

- `search(String keywords)`
- `getEpisodes(String bookId, String filteredTitle)`
- `getVideoUrl(String bookId, int episodeNum, String filteredTitle, String chapterId)`

当前实现为 `FlaskContentProvider`，默认内容源地址：

```properties
reelshort.content-provider.base-url=http://127.0.0.1:5000
```

