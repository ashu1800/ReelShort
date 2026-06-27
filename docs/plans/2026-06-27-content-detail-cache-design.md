# Content Detail Cache Design

## Goal

补齐 App 内容详情接口和分集缓存基础能力，让内容模块更接近架构文档中的 `content_books` 与 `content_episodes` 缓存边界。App 仍只访问 Spring Boot；Spring Boot 继续通过 `ContentProvider` 访问 Flask 内容源。

## Current State

当前内容模块已经支持搜索、首页推荐、货架、分集列表和播放地址。搜索和货架会写入 `content_book_cache`，但没有 `GET /api/app/content/books/{bookId}` 剧集详情接口，也没有独立分集缓存表。`getEpisodes` 直接调用内容源，内容源不可用时无法使用已有分集数据兜底。

## Scope

本切片实现：

- 新增 `GET /api/app/content/books/{bookId}`，从 PostgreSQL 内容书缓存返回剧集详情。
- 新增 `content_episode_cache` 表，按 `bookId + filteredTitle` 缓存分集列表。
- `GET /api/app/content/books/{bookId}/episodes` 成功调用内容源后写入分集缓存。
- 内容源获取分集失败时，如果存在缓存，返回缓存；没有缓存则保留原有错误语义。
- 后台内容缓存状态增加分集缓存数量。

本切片不实现：

- 主动按剧集刷新分集缓存的后台接口。
- 分集缓存过期策略。
- 剧集详情实时调用 Flask。详情只基于已缓存 `ContentBookCache`，没有缓存时返回 `404`。
- 内容源多实现切换。

## Data Model

`content_episode_cache`：

- `id`：UUID 主键。
- `book_id`：剧集 ID。
- `filtered_title`：内容源需要的过滤标题。
- `episodes_json`：统一 `ContentEpisode` 列表 JSON。
- `episode_count`：分集数量。
- `refreshed_at`：最近成功刷新时间。
- `last_error`：最近内容源失败信息。

唯一约束：`book_id + filtered_title`。

## API Shape

`GET /api/app/content/books/{bookId}` 返回统一响应包裹的 `ContentBook`：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "bookId": "book-1",
    "title": "Love Story",
    "filteredTitle": "love-story",
    "coverUrl": "https://example.com/cover.jpg",
    "episodeCount": 12
  }
}
```

错误：

- `404`：剧集还没有缓存。客户端应先通过搜索、推荐或货架发现内容。
- `503`：分集列表内容源失败且没有可用缓存。

## Testing

- Controller 测试覆盖详情接口成功和缺失返回 `404`。
- Service 测试覆盖获取分集成功后写入缓存。
- Service 测试覆盖内容源失败时返回已有分集缓存。
- Repository 测试覆盖 `bookId + filteredTitle` 唯一约束和查找。
- 全量后端测试确保已有内容、鉴权、积分、后台接口不回归。

