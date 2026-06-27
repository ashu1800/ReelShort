# ReelShort Content Provider

Flask 内容源适配服务。同机部署后由 Spring Boot 通过 `ContentProvider` 统一调用，App 和后台不能直接访问本服务。

## 环境变量

- `REELSHORT_SITE_URL`：ReelShort 站点根地址，默认 `https://www.reelshort.com`。
- `REELSHORT_SITE_ID`：ReelShort Next.js 数据路径中的站点 ID，默认 `37`。
- `REELSHORT_NEXT_BUILD_ID`：可选。Next.js build id；未配置时启动后请求会从首页 HTML 自动发现并缓存。
- `REELSHORT_REQUEST_TIMEOUT_SECONDS`：上游请求超时时间，默认 `10`。

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
