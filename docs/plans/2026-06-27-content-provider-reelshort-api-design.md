# Content Provider ReelShort API Design

## Goal

把 `content-provider` 从只提供 `/health` 的 Flask 空壳升级为 Spring Boot 可调用的同机 ReelShort 内容源服务。

## Scope

本阶段实现 Spring Boot `FlaskContentProvider` 已经依赖的内部契约：

- `GET /api/v1/reelshort/search?keywords=...`
- `GET /api/v1/reelshort/episodes/{book_id}?filtered_title=...`
- `GET /api/v1/reelshort/video/{book_id}/{episode_num}?filtered_title=...&chapter_id=...`
- `GET /api/v1/reelshort/recommend`
- `GET /api/v1/reelshort/newrelease`
- `GET /api/v1/reelshort/dramadub`

Flask 服务仍然只作为内部内容源服务。App 和后台不得直接访问它。

## Upstream Boundary

引入 `ReelShortClient` 封装第三方 API 调用，Flask 路由只负责参数校验、调用 client、返回 Spring Boot 期望的 JSON 结构。

默认上游地址通过环境变量配置：

- `REELSHORT_UPSTREAM_BASE_URL`，默认 `https://reelshort-api.vercel.app`
- `REELSHORT_REQUEST_TIMEOUT_SECONDS`，默认 `10`

如果第三方部署地址变化，只改环境变量或部署配置，不改 Spring Boot 和 App。

## Response Contract

返回结构与 Java `FlaskContentProvider` 保持一致：

搜索：

```json
{
  "results": [
    {
      "book_id": "book-1",
      "book_title": "Title",
      "filtered_title": "title",
      "book_pic": "https://example.com/cover.jpg",
      "chapter_count": 12
    }
  ]
}
```

货架：

```json
{
  "books": []
}
```

分集：

```json
{
  "episodes": [
    { "episode": 1, "chapter_id": "chapter-1" }
  ]
}
```

播放：

```json
{
  "video_url": "https://cdn.example.com/video.m3u8",
  "episode": 1,
  "duration": 120,
  "next_episode": { "episode": 2, "chapter_id": "chapter-2" }
}
```

## Error Handling

- 参数缺失返回 `400`。
- 上游返回 `404` 时透传为 `404`，让 Spring Boot 区分内容不存在。
- 上游超时、连接失败或非 2xx/404 返回 `502`。
- 返回错误体形如 `{"error": "..."}`，Spring Boot 不依赖错误体字段。

## Testing

使用 Flask `test_client` 和 fake upstream client 做单元级契约测试，不在测试中访问真实第三方 API。

覆盖：

- 健康检查。
- 搜索、货架、分集、播放端点 JSON 契约。
- 参数缺失返回 `400`。
- 上游 `404` 映射为 `404`。
- 上游失败映射为 `502`。

