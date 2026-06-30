# ReelShort Content Provider

Flask 内容源适配服务。同机部署后由 Spring Boot 通过 `ContentProvider` 统一调用，App 和后台不能直接访问本服务。

## 环境变量

- `REELSHORT_SITE_URL`：ReelShort 站点根地址，默认 `https://www.reelshort.com`。
- `REELSHORT_SITE_ID`：ReelShort Next.js 数据路径中的站点 ID，默认 `37`。
- `REELSHORT_NEXT_BUILD_ID`：可选。Next.js build id；未配置时请求会从站点页面自动发现并缓存。自动发现的 build id 如果在 `_next/data` 请求中返回 404，会清空后重新发现并重试一次；显式配置的 build id 会保持固定，不自动刷新。
- `REELSHORT_REQUEST_TIMEOUT_SECONDS`：上游请求超时时间，默认 `10`。
- `REELSHORT_CATALOG_SEARCH_KEYWORDS`：可选。首页推荐扩容使用的搜索关键词，逗号分隔；为空时使用内置英文短剧关键词。
- `REELSHORT_CATALOG_MAX_PAGES_PER_KEYWORD`：每个关键词最多抓取搜索页数，默认 `3`；设为 `0` 可关闭搜索扩容。
- `REELSHORT_CATALOG_MAX_BOOKS`：首页推荐扩容后的最大短剧数，默认 `500`。
- `REELSHORT_CATALOG_REQUEST_WORKERS`：片库扩展搜索请求并发数，默认 `8`；设为 `1` 可改为串行。

## 本地运行

```powershell
python -m venv .venv
.\.venv\Scripts\python -m pip install -r requirements.txt
.\.venv\Scripts\python app.py
```

健康检查：

```http
GET http://localhost:5000/health
```

## 内部接口

Spring Boot 当前依赖以下端点：

```http
GET /api/v1/reelshort/search?keywords=love
GET /api/v1/reelshort/episodes/{book_id}?filtered_title=love-story
GET /api/v1/reelshort/video/{book_id}/{episode_num}?filtered_title=love-story&chapter_id=chapter-1
GET /api/v1/reelshort/recommend
GET /api/v1/reelshort/newrelease
GET /api/v1/reelshort/dramadub
```

返回结构会被规整为 Spring Boot `FlaskContentProvider` 期望的 JSON：

- 搜索：`{"results": [...]}`
- 货架：`{"books": [...]}`
- 分集：`{"episodes": [...]}`
- 播放：`{"video_url": "...", "episode": 1, "duration": 120, "next_episode": {...}}`

## 测试

```powershell
python -m pytest
```

测试使用 fake upstream client，不访问真实第三方 API。
