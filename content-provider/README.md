# ReelShort Content Provider

Flask 内容源适配服务。同机部署后由 Spring Boot 通过 `ContentProvider` 统一调用，App 和后台不能直接访问本服务。

## 内容上游与切换

本服务同时适配两个上游，按 **请求头 `X-Content-Upstream`** 选择（缺省 `reelshort`，只为向后兼容；后端正常都会带）：

| 上游 | 名称 | 说明 |
| --- | --- | --- |
| TwistyTV | `twistytv` | **默认**。聚合站，索引 NetShort / ReelShort / DramaBox 三家；无账号体系、无付费墙。 |
| ReelShort | `reelshort` | **备选**。官方站，锁集需要已登录账号权益，见下文。 |

切换入口是后台系统配置 `content.upstream`（`TWISTYTV` / `REELSHORT`），不需要改环境变量或重启；后端会立即清空货架缓存并重抓。书目/分集缓存按 `bookId` 天然隔离（TwistyTV 是 `ns_`/`rs_`/`db_` 前缀，ReelShort 是纯数字），互不覆盖。

`/diagnostics` 按上游分别返回快照（`upstreams.twistytv` / `upstreams.reelshort`，另给 `active_upstream`），顶层 `diagnostics` 跟随请求头 `X-Content-Upstream` 指向的当前上游——后端的诊断页与告警只读顶层字段，固定成 reelshort 会让默认上游的故障事件彻底不可见。

`/api/v1/reelshort/{session,credentials,account,chapters}` 是 **ReelShort 专属**端点，永远打到 ReelShort 客户端——切到 TwistyTV 之后仍要能管理备用账号。

### TwistyTV 抓取要点

- 站点是 Next.js App Router：列表走 SSR HTML（`<a class="show-card" href="/watch/ID">`），详情与分集在 `/watch/ID` 的 RSC flight payload（`self.__next_f.push([1,"…"])`）里，解析时先拼分片再 `json.raw_decode`。
- 播放地址：DramaBox / ReelShort 的 `streamUrl` 在 payload 里就是直链；**NetShort 恒为空**，要再请求 `GET /api/stream?series=&episode=&provider=`，且必须带 `Origin: https://twistytv.com`（缺了返回 400）。
- **时长**上游不给，自己探：MP4 读 Range 里的 `moov/mvhd`（DramaBox/NetShort 实测 faststart，头 128 KB 内就有）；ReelShort 的 `streamUrl` 其实是 **m3u8**（URL 无扩展名，只能按内容嗅探），改为解析主播放列表的首个变体并累加 `#EXTINF` 分段。拿不到时返回 0 并记 `twistytv_duration_unknown`，播放不阻断，但那一集不产生奖励。
- 货架映射：`recommend` ← 首页全部 row + `/browse` 前 N 页；`new-release` ← 首页 “New releases” 行；`drama-dub` ← `/genre/dubbed`。
- **集数**：ReelShort(`rs_`) / DramaBox(`db_`) 的卡片自带（hover meta 的 `N episodes` 或角标 `46 EP`）；**NetShort(`ns_`) 卡片三个位置都没有集数**（hover meta 只有题材，`show-card-meta` 只有播放量/厂牌），只能翻 `/watch/{id}` 取 `show.episodeCount`。因此货架刷新（后台定时任务）会按本补抓，结果按 `TWISTYTV_EPISODE_COUNT_CACHE_SECONDS`（默认 7 天）缓存；搜索不在请求里现抓（一次搜索几十本、每本约 120 KB），只填已缓存的，其余丢给单个后台线程预热，下次搜索就是对的。`episodes()` / `video()` 本来就要翻详情页，会把集数顺手写进缓存。
- 进程内缓存：watch 页解析结果（默认 600s）、每集时长（6h）与集数（7d）。因此**仍然必须单 worker**（`gunicorn -w 1`）。

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
- `REELSHORT_UPSTREAM_COOKIE`：可选。ReelShort **登录态** Cookie 请求头的完整值（浏览器登录后复制，形如 `a=1; b=2`）。未配置时按匿名抓取，Header 集合与引入该能力前完全一致；配置后每个上游请求都会带上它。值中不允许出现换行（会被当作请求头注入直接拒绝）。
- `TWISTYTV_SITE_URL`：TwistyTV 站点根地址，默认 `https://twistytv.com`。
- `TWISTYTV_REQUEST_TIMEOUT_SECONDS`：TwistyTV 请求超时，默认 `15`。
- `TWISTYTV_MAX_RESPONSE_BYTES`：TwistyTV 单次响应上限，默认 `4194304`（4 MiB）；列表页本身有 200-330 KB。
- `TWISTYTV_PAGE_CACHE_SECONDS`：watch 页解析结果缓存时长，默认 `600`。
- `TWISTYTV_MAX_SHELF_PAGES`：`recommend` 货架合并 `/browse` 的页数上限，默认 `3`。
- `TWISTYTV_EPISODE_COUNT_CACHE_SECONDS`：NetShort 卡片缺集数时按本翻详情页补到的结果缓存时长，默认 `604800`（7 天），上限 30 天。
- `REELSHORT_ACCOUNT_FILE`：可选。后台下发账号的落盘位置，默认 `/srv/reelshort/content-provider/.account.json`（权限 `0600`）；容器镜像里是 `/app/data/.account.json`，由 Compose 卷 `content-provider-account` 承载。

## 上游登录态（解锁锁集）

上游只对每部剧前 **1–12 集**公开 `video_url`；第 13 集起，分集页 payload 里 `video_url` 为 `null`（`duration` 仍然有），内容源据此返回 404。配置一个已登录的 ReelShort 账号后，播放地址是否解锁取决于**账号权益**（免费账号仍然只有公开窗口）。

### 方式一：账号密码自动登录（推荐）

内容源会用邮箱+密码调用上游 H5 登录接口换取 `session`，之后的请求自动带上 `session`/`uid` 与 HMAC 签名；`session` 过期（上游返回 `code=103`）时会自动重登一次。

```bash
REELSHORT_UPSTREAM_EMAIL='you@example.com'
REELSHORT_UPSTREAM_PASSWORD='********'
# 可选：上游轮换签名密钥/渠道号时覆盖，默认值已内置并对着线上 bundle 核对过
# REELSHORT_UPSTREAM_API_SECRET='...'
# REELSHORT_UPSTREAM_CHANNEL_ID='WEB41001'
```

凭据**不是只从环境变量读**：后台「系统配置 → ReelShort 上游账号」保存后会通过 `POST /api/v1/reelshort/credentials` 下发给本服务，并覆盖环境变量里的初始值（环境变量只是首次部署的引导值）。保存路径有三处，都按明文凭据对待：

- Spring Boot `system_configs` 表的两行（`content.reelshort.email` / `content.reelshort.password`）；后台接口只回脱敏邮箱，通用配置接口既不列出也不允许改这两行；
- 本服务进程内存（`ReelShortClient`），不写日志（诊断事件里邮箱截断到 40 字符，session 只记 12 位指纹）；
- 本机文件 `REELSHORT_ACCOUNT_FILE`（`0600`），用于内容源重启后自恢复。

所以数据库转储（`infra/backups/`）和这个文件都等同账号密码文件：不要提交仓库、不要外发，备份按凭据加密保存。

另外两条部署约束：

- **必须是单 worker**（`gunicorn -w 1 --threads 16`）：`session`/`uid` 只存在进程内存里，多 worker 会各存一份，后台下发的账号只会命中其中一个，另一个永远读到旧账号。
- 登录失败（口令错、上游抖动）后 **60 秒内不再重试**，锁集请求按匿名窗口继续，不会每个请求都排队等一轮登录超时（`UPSTREAM_LOGIN_RETRY_COOLDOWN_SECONDS`）。

链路与浏览器一致：**先建匿名访客会话 → 提交加密口令登录**。细节：

- 公共参数 `apiVersion=1.0.4 / channelId=WEB41001 / clientVer=2.4.00 / devId / lang=en / ts / session / uid` 进请求头，同时用 `HMAC-SHA256` 生成 `sign`（键升序拼 `k=v`，密钥内置）；
- 口令加密是 `utf8 → zlib → base64 → AES-128-CBC/PKCS7 → base64`，与浏览器抓包逐字节一致；
- 上游部分响应直接返回 base64 密文，内容源会自动 `AES → base64 → zlib → JSON` 解开；
- `session` 过期（上游返回 `code=103`）自动重登一次；登录失败不会抛异常，也不会把游客会话当成登录态。

登录成功后，锁集在 `video()` 里会走兜底：分集页没有 `video_url` 时再问一次上游分集接口（`getChapterInfo`，带账号权益），拿到地址就用，仍然拿不到就维持 404 与 `video_url_missing` 诊断。

验证（`account=1` 会先登录一次，幂等）：

```http
GET /api/v1/reelshort/session?account=1&book_id=<bookId>&filtered_title=<slug>&episode_num=13
X-Content-Provider-Token: <内部密钥>
```

返回里 `accountConfigured` 表示账号已配置、`loginAttempt` 是本次登录结果、`sessionFingerprint` 是当前 session 的 12 位短指纹（不泄露 token）、`probe.playable` 表示该集现在能不能取到地址。

**注意**：登录成功 ≠ 锁集解锁。上游的播放地址由**账号权益**决定：免费账号登录后仍然只能看公开窗口（例如 1–12 集），开通会员后同一套凭据无需改动即可拿到锁集地址。用 `video_url_missing` 事件里的 `authenticated=true` 可以区分"没登录"和"登录了但权益不够"。

### 可播窗口查询（免费账号也能用）

上游分集列表里每集带 `play_info`：**非空 = 已解锁，`null` = 锁集**。免费账号也能拿到这个标记，所以一次请求就能知道整季的可播范围，不必逐集探测：

```http
GET /api/v1/reelshort/chapters/<bookId>
X-Content-Provider-Token: <内部密钥>
```

返回 `chapter_count`、`playable_episodes`（可播集数数组）、`locked_from`（第一集锁集）与逐集 `{episode, chapter_id, duration, playable}`。后端可用它把选集面板置灰、跳过自动下一集，而不是让用户点进去才 404。

### 方式二：手工 Cookie（备用）

1. 浏览器登录 `https://www.reelshort.com`；
2. DevTools → Network → 任选一个站点请求 → Request Headers → 复制整行 `Cookie:` 的值；
3. 写入内容源环境文件（不要提交到仓库）：

```bash
REELSHORT_UPSTREAM_COOKIE='sessionid=...; ...'
```

4. 重启服务后用上面的 `/session` 端点验证。

**过期处理**：账号密码方式会自动重登；Cookie 方式失效后需要手工替换。`/diagnostics` 里 `video_url_missing` 事件的 `context.authenticated` 表示当时有没有登录态：`true` 说明"我们有登录态但上游仍没给地址"，是账号权益不够（例如免费账号），不是配置没生效。

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
