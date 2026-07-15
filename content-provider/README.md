# ReelShort Content Provider

Flask 内容源适配服务。同机部署后由 Spring Boot 通过 `ContentProvider` 统一调用，App 和后台不能直接访问本服务。

## 环境变量

- `REELSHORT_SITE_URL`：ReelShort 站点根地址，默认 `https://www.reelshort.com`。
- `REELSHORT_SITE_ID`：ReelShort Next.js 数据路径中的站点 ID，默认 `37`。
- `REELSHORT_NEXT_BUILD_ID`：可选。Next.js build id；未配置时请求会从站点页面自动发现并缓存。自动发现的 build id 如果在 `_next/data` 请求中返回 404，会清空后重新发现并重试一次；显式配置的 build id 会保持固定，不自动刷新。
- `REELSHORT_REQUEST_TIMEOUT_SECONDS`：上游请求超时时间，默认 `10`。
- `REELSHORT_MAX_RESPONSE_BYTES`：单次上游响应体最大字节数，默认 `5242880`（5 MiB）；超过上限会中止读取并返回内容源错误。
- `REELSHORT_MAX_REDIRECTS`：单次上游请求允许的最大重定向次数，默认 `3`；每一跳都会重新校验目标 URL 和解析后的 IP，拒绝私网、回环、链路本地及其他非公网目标。
- `REELSHORT_CATALOG_SEARCH_KEYWORDS`：可选。英文首页推荐扩容关键词兼容配置，逗号分隔。
- `REELSHORT_CATALOG_SEARCH_KEYWORDS_EN`：可选。英文首页推荐扩容关键词；优先级高于旧 `REELSHORT_CATALOG_SEARCH_KEYWORDS`。
- `REELSHORT_CATALOG_MAX_PAGES_PER_KEYWORD`：每个关键词最多抓取搜索页数，默认 `3`，硬上限 `5`；设为 `0` 可关闭搜索扩容。
- `REELSHORT_CATALOG_MAX_BOOKS`：首页推荐扩容后的最大短剧数，默认 `500`，硬上限 `500`。
- `REELSHORT_CATALOG_REQUEST_WORKERS`：片库扩展搜索请求并发数，默认 `8`，硬上限 `16`；设为 `1` 可改为串行。

首页推荐扩容会限制最多 50 个关键词和最多 200 次上游搜索请求。服务会先按配置生成确定性的关键词和分页计划，关键词之间可并发，单个关键词内部按页顺序请求，遇到空页或失败即停止该关键词后续分页。

内容源只负责抓取第三方元数据和播放地址。所有上游请求受总超时、响应体大小、重定向次数和公网目标校验约束。标题、封面、简介、集数和分集信息由 Spring Boot 成功拉取后沉淀到 PostgreSQL；视频文件不存放在自有服务器，播放页请求时才按需获取播放地址。

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

运行诊断：

```http
GET http://localhost:5000/diagnostics
```

`/diagnostics` 返回内存中的内容源异常摘要，包括最近事件和按事件类型聚合的计数。当前记录的事件包括搜索空结果、Next data 404 后刷新 build id、播放页 HTML 解析失败和播放地址缺失等，用于后台运行诊断判断上游结构是否发生变化。该数据不参与 App 内容读取，服务重启后会清空。

## 内部接口

Spring Boot 当前依赖以下端点：

```http
GET /diagnostics
GET /api/v1/reelshort/search?keywords=love&locale=en
GET /api/v1/reelshort/episodes/{book_id}?filtered_title=love-story&locale=en
GET /api/v1/reelshort/video/{book_id}/{episode_num}?filtered_title=love-story&chapter_id=chapter-1&locale=en
GET /api/v1/reelshort/recommend?locale=en
GET /api/v1/reelshort/newrelease
GET /api/v1/reelshort/dramadub
```

`locale` 可选，默认 `en`，当前只允许 `en`。搜索、首页 fallback 和片库扩展会使用对应 ReelShort Next data locale 路径。

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
