"""TwistyTV 上游适配（https://twistytv.com）。

TwistyTV 是 Next.js App Router 聚合站，索引三家片源：NetShort(`ns_`) / ReelShort(`rs_`)
/ DramaBox(`db_`)，书目 ID 形如 `ns_2105841747766697985`。它没有公开 JSON API，数据分两处：

* **列表**（首页 / browse / genre / search）：SSR 出来的 `<a class="show-card" href="/watch/ID">`
  卡片，里面就有标题、海报、简介和类型。**集数只有 ReelShort / DramaBox 的卡片有**
  （hover meta 的 "N episodes" 或角标 "46 EP"）；NetShort(`ns_`) 卡片三者皆无，
  只能翻详情页的 `show.episodeCount` 补——见 `_fill_counts`。
* **详情与分集**：`/watch/ID` 页里 RSC flight payload（`self.__next_f.push([1,"..."])`）的
  `{"show":{...},"providerId":...,"episodes":[...]}` 对象。
* **播放地址**：DramaBox / ReelShort 的 `streamUrl` 在 flight payload 里就是直链；
  NetShort 的 `streamUrl` 恒为空，要再问 `GET /api/stream?series=&episode=&provider=`
  （必须带 `Origin: https://twistytv.com`，否则 400）。
* **时长**：上游不给。拿 Range 请求读 MP4 的 `moov/mvhd` 算出来——积分按权威时长发放，
  猜一个默认值会把奖励算错。

本模块只依赖 `requests` + 标准库，没有浏览器/无头内核。
"""

from __future__ import annotations

import json
import os
import queue
import re
import threading
import time
from collections import OrderedDict
from concurrent.futures import ThreadPoolExecutor
from html import unescape
from urllib.parse import quote, urljoin

from fetcher import (
    DEFAULT_MAX_REDIRECTS,
    DEFAULT_MAX_RESPONSE_BYTES,
    HardenedFetcher,
    ProviderDiagnostics,
    UpstreamError,
    _as_dict,
    _as_int,
    _as_list,
)

DEFAULT_LOCALE = "en"

TWISTYTV_SITE_URL_ENV = "TWISTYTV_SITE_URL"
DEFAULT_SITE_URL = "https://twistytv.com"
DEFAULT_TIMEOUT_SECONDS = 15.0
# 首页 / browse 页面约 200-330 KB，留足余量但不放开成"随便多大"。
DEFAULT_PAGE_MAX_BYTES = 4 * 1024 * 1024
# 一集 MP4 的 moov 通常在最前面（faststart）；128 KB 足够，找不到再试尾段。
DURATION_PROBE_BYTES = 128 * 1024
DEFAULT_PAGE_CACHE_SECONDS = 600.0
DEFAULT_DURATION_CACHE_SECONDS = 6 * 3600.0
# 探针失败（CDN 不支持 Range、拿到 m3u8 却没分段等）的冷却时间。
DURATION_ERROR_CACHE_SECONDS = 120.0
# 片库越大缓存越厚，但每页都要真实打上游一次：默认 12 页 ≈ 700 本，只跑在后台定时刷新里。
# 线上 `/browse` 共 175 页（每页 60 本），不设上限会变成一次刷 1 万本、连打 175 次上游。
DEFAULT_MAX_SHELF_PAGES = 12
# genre 页同样分页（`/genre/dubbed?page=2` 与第 1 页零重合），但货架上只留前几页当天花板。
DEFAULT_MAX_GENRE_PAGES = 3
# 集数补抓是"每本书多打一次上游"：一轮最多补这么多本（0 = 关），余下的留给下一轮刷新。
# 700 本书一次全补 = 700 次详情页请求，裸 IP 上必被拦；分轮补则两天就把缓存填满。
# 真正的保护是 `request_interval_seconds`（0.3s ≈ 上限 3.3 req/s），预算只决定单轮时长。
DEFAULT_EPISODE_COUNT_BACKFILL = 100
# 时长探针打的是 CDN 不是站点（NetShort/DramaBox/ReelShort 三个不同域名），且一集只探一次，
# 量级等于一次真实播放；关掉它 = 所有观看都不产生积分，所以默认开着。
DEFAULT_DURATION_PROBE_ENABLED = True
# 刷新时相邻上游请求的最小间隔，避免一秒钟连打 12+ 次被上游风控。
DEFAULT_REQUEST_INTERVAL_SECONDS = 0.3
# NetShort 卡片不给集数，补一次要翻 120 KB 的详情页：一本只补一次，按 7 天缓存。
EPISODE_COUNT_CACHE_SECONDS_ENV = "TWISTYTV_EPISODE_COUNT_CACHE_SECONDS"
DEFAULT_EPISODE_COUNT_CACHE_SECONDS = 7 * 24 * 3600.0
# 货架刷新在后台跑，6 路并发实测上游能吃下；搜索只读缓存，不在请求里现抓。
EPISODE_COUNT_PROBE_CONCURRENCY = 6
# 后台预热队列上限：搜索一次最多塞几十个 ID，这里是防止长期运行无限长。
EPISODE_COUNT_WARM_QUEUE_MAX = 2000
# 一次搜索最多返回多少本：上游单页 100+ 本（q=love 实测 108），一次性全进后端缓存没必要，
# 也让响应体有个确定上限。
SEARCH_RESULT_LIMIT = 200

# 上游 provider 白名单：未知值一律拒绝，避免把任意字符串拼进上游 URL。
KNOWN_PROVIDERS = {"netshort", "reelshort", "dramabox"}

# 货架名（后端 ContentShelfType.apiValue）→ 抓取来源。
SHELF_SOURCES = {
    "recommend": ("home", "browse"),
    "newrelease": ("home:new",),
    "dramadub": ("genre:dubbed",),
}

# 列表页正文里出现这些、或 200 却一张卡都解析不出来，就当上游"软 404"处理。
# 上游改版/维护页/反爬页全是 200 + 无卡片，静默返回空数组会让后端的货架缓存被写成空。
_SOFT_FAILURE_MARKERS = ("just a moment", "cf-chl", "checking your browser", "access denied", "site is down")

_PUSH_RE = re.compile(r'self\.__next_f\.push\(\[1,("(?:[^"\\]|\\.)*")\]\)', re.S)
_SECTION_RE = re.compile(r'<section class="row" aria-labelledby="([a-z0-9-]+)"')
_CARD_RE = re.compile(r'<a class="show-card" href="/watch/([a-z]{2}_[0-9A-Za-z]+)"(.*?)</a>', re.S)
_TITLE_RE = re.compile(r'<span class="show-card-title">(.*?)</span>', re.S)
_DESC_RE = re.compile(r'<span class="show-card-hover-desc">(.*?)</span>', re.S)
_META_RE = re.compile(r'<span class="show-card-hover-meta">(.*?)</span>', re.S)
_CARD_META_RE = re.compile(r'<span class="show-card-meta">(.*?)</span>', re.S)
_IMG_RE = re.compile(r'<img[^>]*\ssrc="([^"]+)"')
_EPISODE_COUNT_RE = re.compile(r"(\d+)\s+episodes?", re.I)
# 海报角标：`<span class="poster-tag poster-tag-episodes" …>46<!-- --> EP</span>`。
_EPISODE_TAG_RE = re.compile(r'poster-tag-episodes"[^>]*>\s*(\d+)', re.I)
_TAG_RE = re.compile(r"<[^>]+>")
# 首页 Top 10 与 hero 区用的是另一套 class，卡片结构不同（`top10-link` / `hero-link`）。
_HOME_LINK_RE = re.compile(r'<a class="(?:top10|hero)-link"[^>]*href="/watch/([a-z]{2}_[0-9A-Za-z]+)"', re.S)
# 注意：卡片里没有"播放角标"这类可用来区分列表卡与详情页剧照的稳定标记
# （实测首页/浏览/搜索的卡片只有 poster-tag-provider / badge-new / heat），
# 所以不做额外过滤——多过滤一次就会把真实书目整批筛掉。


class _TtlCache:
    """进程内小缓存：上游页面解析一次就够本进程用一阵，避免每集都重抓整页。"""

    def __init__(self, ttl_seconds: float, capacity: int = 256, monotonic=None):
        self.ttl_seconds = ttl_seconds
        self.capacity = max(1, capacity)
        self._monotonic = monotonic or time.monotonic
        self._entries: OrderedDict[str, tuple[float, object]] = OrderedDict()
        self._lock = threading.Lock()

    def get(self, key: str):
        with self._lock:
            entry = self._entries.get(key)
            if entry is None:
                return None
            stored_at, value = entry
            if self._monotonic() - stored_at > self.ttl_seconds:
                self._entries.pop(key, None)
                return None
            self._entries.move_to_end(key)
            return value

    def put(self, key: str, value) -> None:
        with self._lock:
            self._entries[key] = (self._monotonic(), value)
            self._entries.move_to_end(key)
            while len(self._entries) > self.capacity:
                self._entries.popitem(last=False)


def _text(fragment: str) -> str:
    """把卡片里的 HTML 片段压成纯文本：去标签、解实体、收空白。"""
    if not fragment:
        return ""
    return " ".join(unescape(_TAG_RE.sub(" ", fragment)).split())


def _slugify(title: str) -> str:
    return re.sub(r"[^a-z0-9]+", "-", (title or "").lower()).strip("-")


def _flight_text(html: str) -> str:
    """把 RSC flight 的多个 `self.__next_f.push([1,"…"])` 片段拼回一段。

    每个片段都是一个 JSON 字符串字面量（已经被 JS 转义过一次），`json.loads` 正好能
    把那一层转义解掉；分片边界可能把一个对象切成两半，所以必须**先拼再解析**。
    """
    parts = []
    for match in _PUSH_RE.finditer(html or ""):
        try:
            parts.append(json.loads(match.group(1)))
        except ValueError:
            continue
    return "".join(parts)


def _decode_after(text: str, anchor: str):
    """从 anchor 之后的第一个 `{` 或 `[` 起解一个完整 JSON 值，返回 (值, 结束位置)。"""
    index = text.find(anchor)
    if index < 0:
        return None, -1
    openers = [position for position in (text.find("{", index), text.find("[", index)) if position >= 0]
    if not openers:
        return None, -1
    try:
        value, end = json.JSONDecoder().raw_decode(text, min(openers))
    except ValueError:
        return None, -1
    return value, end


def _soft_failure(html: str) -> bool:
    """HTTP 200 但不是真正的列表页：人机校验页、维护页、封禁页。"""
    lowered = (html or "").lower()
    return any(marker in lowered for marker in _SOFT_FAILURE_MARKERS)


def _parse_cards(chunk: str) -> list[dict]:
    """解析一段 HTML 里的短剧卡片。列表页（首页/browse/genre/search）通用。"""
    books = []
    for match in _CARD_RE.finditer(chunk or ""):
        book_id, body = match.group(1), match.group(2)
        title_match = _TITLE_RE.search(body)
        title = _text(title_match.group(1)) if title_match else ""
        if not title:
            continue
        poster = _IMG_RE.search(body)
        meta_match = _META_RE.search(body) or _CARD_META_RE.search(body)
        meta = _text(meta_match.group(1)) if meta_match else ""
        count_match = _EPISODE_COUNT_RE.search(meta)
        tag_match = _EPISODE_TAG_RE.search(body)
        chapter_count = _as_int(count_match.group(1)) if count_match else 0
        if not chapter_count and tag_match:
            chapter_count = _as_int(tag_match.group(1))
        books.append(
            {
                "book_id": book_id,
                "book_title": title,
                "filtered_title": _slugify(title),
                "book_pic": unescape(poster.group(1)) if poster else "",
                "description": _text(_DESC_RE.search(body).group(1)) if _DESC_RE.search(body) else "",
                "chapter_count": chapter_count,
            }
        )
    return books


def _parse_home_links(chunk: str) -> list[dict]:
    """首页 Top 10 / hero 区的书目（另一套 class，只有标题、海报和链接）。"""
    books = []
    for match in _HOME_LINK_RE.finditer(chunk or ""):
        body = chunk[match.end() : match.end() + 1200]
        aria = re.search(r'aria-label="(?:\d+\.\s*)?([^"]+)"', chunk[max(0, match.start() - 240) : match.end()])
        title_match = _TITLE_RE.search(body) or re.search(r'<span class="line-clamp-4">(.*?)</span>', body, re.S)
        poster = _IMG_RE.search(body)
        title = _text(title_match.group(1)) if title_match else _text(aria.group(1)) if aria else ""
        title = title or (unescape(aria.group(1)).strip() if aria else "")
        if not title:
            continue
        books.append(
            {
                "book_id": match.group(1),
                "book_title": title,
                "filtered_title": _slugify(title),
                "book_pic": unescape(poster.group(1)) if poster else "",
                "description": "",
                "chapter_count": 0,
            }
        )
    return books


def _parse_home_rows(html: str) -> dict[str, list[dict]]:
    """首页按 `<section class="row" aria-labelledby="…">` 切段，得到 {行名: 卡片列表}。"""
    marks = [(match.start(), match.group(1)) for match in _SECTION_RE.finditer(html or "")]
    rows: dict[str, list[dict]] = {}
    for position, (start, name) in enumerate(marks):
        end = marks[position + 1][0] if position + 1 < len(marks) else len(html)
        rows[name] = _parse_cards(html[start:end]) or _parse_home_links(html[start:end])
    return rows


def _parse_mp4_duration(data: bytes) -> int:
    """从 MP4 的 `moov/mvhd` 里取时长（秒）。取不到返回 0。"""
    if not data:
        return 0
    index = data.find(b"mvhd")
    if index < 0:
        return 0
    box = data[index + 4 :]
    if len(box) < 20:
        return 0
    version = box[0]
    try:
        if version == 1:
            timescale = int.from_bytes(box[20:24], "big")
            duration = int.from_bytes(box[24:32], "big")
        else:
            timescale = int.from_bytes(box[12:16], "big")
            duration = int.from_bytes(box[16:20], "big")
    except (IndexError, ValueError):
        return 0
    if not timescale:
        return 0
    seconds = round(duration / timescale)
    return seconds if 0 < seconds < 24 * 3600 else 0


def _bounded_float_env(name: str, default: float, maximum: float) -> float:
    try:
        value = float(os.getenv(name, str(default)))
    except (TypeError, ValueError):
        return default
    if value <= 0 or value > maximum:
        return default
    return value


def _bounded_int_env(name: str, default: int, maximum: int) -> int:
    try:
        value = int(os.getenv(name, str(default)))
    except (TypeError, ValueError):
        return default
    return value if 1 <= value <= maximum else default


def _flag_env(name: str, default: bool) -> bool:
    value = os.getenv(name)
    if value is None or not value.strip():
        return default
    return value.strip().lower() in {"1", "true", "yes", "on"}


class TwistytvClient(HardenedFetcher):
    """把 twistytv.com 映射成后端 `ContentProvider` 的四个方法。"""

    def __init__(
        self,
        site_url: str = DEFAULT_SITE_URL,
        timeout_seconds: float = DEFAULT_TIMEOUT_SECONDS,
        *,
        max_response_bytes: int = DEFAULT_PAGE_MAX_BYTES,
        max_redirects: int = DEFAULT_MAX_REDIRECTS,
        diagnostics: ProviderDiagnostics | None = None,
        page_cache_seconds: float = DEFAULT_PAGE_CACHE_SECONDS,
        duration_cache_seconds: float = DEFAULT_DURATION_CACHE_SECONDS,
        episode_count_cache_seconds: float = DEFAULT_EPISODE_COUNT_CACHE_SECONDS,
        max_shelf_pages: int = DEFAULT_MAX_SHELF_PAGES,
        max_genre_pages: int = DEFAULT_MAX_GENRE_PAGES,
        episode_count_backfill: int = DEFAULT_EPISODE_COUNT_BACKFILL,
        duration_probe_enabled: bool = DEFAULT_DURATION_PROBE_ENABLED,
        request_interval_seconds: float = DEFAULT_REQUEST_INTERVAL_SECONDS,
        resolver=None,
        monotonic=None,
    ):
        super().__init__(
            timeout_seconds,
            max_response_bytes=max_response_bytes,
            max_redirects=max_redirects,
            diagnostics=diagnostics,
            resolver=resolver,
            monotonic=monotonic,
        )
        self.site_url = (site_url or DEFAULT_SITE_URL).rstrip("/")
        self.max_shelf_pages = max(1, max_shelf_pages)
        self.max_genre_pages = max(1, max_genre_pages)
        self.episode_count_backfill = max(0, int(episode_count_backfill or 0))
        self.duration_probe_enabled = bool(duration_probe_enabled)
        # 风控：只在生成列表时（货架/集数补抓）节流；用户点开的详情/播放是一次性请求，不排。
        self.request_interval_seconds = max(0.0, float(request_interval_seconds or 0.0))
        self._throttle_lock = threading.Lock()
        self._next_request_at = 0.0
        self._page_cache = _TtlCache(page_cache_seconds, capacity=256, monotonic=self.monotonic)
        self._duration_cache = _TtlCache(duration_cache_seconds, capacity=4096, monotonic=self.monotonic)
        # 失败结果单独用短 TTL：既不让每次播放都重试探针，也不把一次抖动钉死 6 小时。
        self._duration_error_cache = _TtlCache(
            min(duration_cache_seconds, DURATION_ERROR_CACHE_SECONDS), capacity=4096, monotonic=self.monotonic
        )
        # 集数：NetShort 卡片没有这个字段，只能按本翻详情页，结果长期缓存。
        self._episode_counts = _TtlCache(
            episode_count_cache_seconds, capacity=8192, monotonic=self.monotonic
        )
        # 失败的集数同样要冷却：不缓存的话，一个解析不动的 ID 会被每次搜索重新入队、
        # 每轮都再打一次详情页（实测 5 次搜索 = 100 次详情页请求）。
        self._episode_count_errors = _TtlCache(
            min(episode_count_cache_seconds, DURATION_ERROR_CACHE_SECONDS),
            capacity=8192,
            monotonic=self.monotonic,
        )
        # 简介：`/browse` 的卡片**没有** hover-desc（实测 60/60 为空），`/genre`、`/search`
        # 只有一部分有；唯一权威来源是详情页的 `show.description`。和集数一起按轮补。
        self._descriptions = _TtlCache(
            episode_count_cache_seconds, capacity=8192, monotonic=self.monotonic
        )
        # 搜索不在请求里现抓，缺的 ID 交给单个守护线程按序补，补完下次搜索就正确。
        self._warm_lock = threading.Lock()
        self._warm_queue: queue.Queue[str] = queue.Queue()
        self._warm_pending: set[str] = set()
        self._warm_thread: threading.Thread | None = None

    @classmethod
    def from_env(cls):
        return cls(
            os.getenv(TWISTYTV_SITE_URL_ENV) or DEFAULT_SITE_URL,
            _bounded_float_env("TWISTYTV_REQUEST_TIMEOUT_SECONDS", DEFAULT_TIMEOUT_SECONDS, 120.0),
            max_response_bytes=_bounded_int_env(
                "TWISTYTV_MAX_RESPONSE_BYTES", DEFAULT_PAGE_MAX_BYTES, 32 * 1024 * 1024
            ),
            page_cache_seconds=_bounded_float_env(
                "TWISTYTV_PAGE_CACHE_SECONDS", DEFAULT_PAGE_CACHE_SECONDS, 86400.0
            ),
            episode_count_cache_seconds=_bounded_float_env(
                EPISODE_COUNT_CACHE_SECONDS_ENV, DEFAULT_EPISODE_COUNT_CACHE_SECONDS, 30 * 24 * 3600.0
            ),
            max_shelf_pages=_bounded_int_env("TWISTYTV_MAX_SHELF_PAGES", DEFAULT_MAX_SHELF_PAGES, 175),
            max_genre_pages=_bounded_int_env("TWISTYTV_MAX_GENRE_PAGES", DEFAULT_MAX_GENRE_PAGES, 60),
            episode_count_backfill=_bounded_int_env(
                "TWISTYTV_EPISODE_COUNT_BACKFILL", DEFAULT_EPISODE_COUNT_BACKFILL, 100000
            ),
            duration_probe_enabled=_flag_env("TWISTYTV_DURATION_PROBE_ENABLED", DEFAULT_DURATION_PROBE_ENABLED),
            request_interval_seconds=_bounded_float_env(
                "TWISTYTV_REQUEST_INTERVAL_SECONDS", DEFAULT_REQUEST_INTERVAL_SECONDS, 10.0
            ),
        )

    # ------------------------------------------------------------------ HTTP

    def _pace(self) -> None:
        """两个列表请求之间至少隔 `request_interval_seconds`，同一进程全局生效。

        一次货架刷新会连打十几页，不留间隔在裸 IP 上很容易被当成爬虫（403/挑战页）。
        只按「请求开始时刻」排队：锁内只算一个时间片，睡眠放在锁外——否则 6 路并发
        会退化成一条串行流水线，每本书都要等前一本的完整往返。
        """
        interval = self.request_interval_seconds
        if interval <= 0:
            return
        with self._throttle_lock:
            now = self.monotonic()
            slot = max(now, self._next_request_at)
            self._next_request_at = slot + interval
        delay = slot - now
        if delay > 0:
            time.sleep(delay)

    def _get_text(self, path: str, event_type: str, *, paced: bool = False) -> str:
        if paced:
            self._pace()
        response = self._request(f"{self.site_url}{path}")
        if response.status_code == 404:
            raise UpstreamError(404, "upstream not found")
        if response.status_code >= 400:
            self.diagnostics.record(event_type, path=path, status_code=response.status_code)
            raise UpstreamError(502, "upstream request failed")
        return response.text

    def _expect_cards(self, html: str, event_type: str, books: list[dict], **context) -> list[dict]:
        """200 但解析不出卡片（上游改版 / 人机验证页 / 维护页）不当作"上游没内容"。

        静默返回空数组的代价是把后端货架缓存写成空，比报错更难查。
        """
        if books:
            return books
        self.diagnostics.record(event_type, soft_failure=_soft_failure(html), **context)
        raise UpstreamError(502, "upstream returned an unparsable page")

    def _stream_url(self, book_id: str, provider: str, episode_num: int) -> str:
        if provider not in KNOWN_PROVIDERS:
            self.diagnostics.record("twistytv_unknown_provider", book_id=book_id, provider=provider)
            return ""
        response = self._request(
            f"{self.site_url}/api/stream",
            params={"series": book_id, "episode": str(episode_num), "provider": provider},
            # 上游要求同源来源头，缺了直接 400（实测）。
            extra_headers={"Origin": self.site_url, "Referer": f"{self.site_url}/watch/{book_id}"},
        )
        if response.status_code == 404:
            return ""
        if response.status_code >= 400:
            self.diagnostics.record(
                "twistytv_stream_rejected",
                book_id=book_id,
                episode_num=episode_num,
                provider=provider,
                status_code=response.status_code,
            )
            return ""
        payload = self._json_body(response)
        if payload.get("missing"):
            return ""
        return str(payload.get("url") or "")

    # ------------------------------------------------------------- 列表能力

    def search(self, keywords: str, locale: str = DEFAULT_LOCALE):
        html = self._get_text(f"/search?q={quote(keywords, safe='')}", "twistytv_search_failed")
        books = self._expect_cards(html, "twistytv_search_unparsable", _parse_cards(html), keywords=keywords)
        if len(books) > SEARCH_RESULT_LIMIT:
            self.diagnostics.record("twistytv_search_truncated", keywords=keywords, total=len(books))
            books = books[:SEARCH_RESULT_LIMIT]
        self._fill_counts(books, wait=False)
        return books

    def shelf(self, shelf_name: str, locale: str = DEFAULT_LOCALE):
        sources = SHELF_SOURCES.get(shelf_name)
        if not sources:
            self.diagnostics.record("twistytv_unknown_shelf", shelf_name=shelf_name)
            raise UpstreamError(404, "upstream not found")
        books = []
        for source in sources:
            if source == "home":
                html = self._get_text("/", "twistytv_home_failed", paced=True)
                rows = _parse_home_rows(html)
                home_books = [card for cards in rows.values() for card in cards]
                home_books += _parse_home_links(html)
                books.extend(self._expect_cards(html, "twistytv_home_unparsable", home_books, shelf=shelf_name))
            elif source.startswith("home:"):
                html = self._get_text("/", "twistytv_home_failed", paced=True)
                row_name = source.split(":", 1)[1]
                row_books = _parse_home_rows(html).get(row_name, [])
                if not row_books:
                    # 选择器漂移时**不能**拿 browse 兜底：那会让 `new-release` 变成第二个
                    # recommend（用户看不到新剧，运维还以为是上游改版）。坐等给后端报错，
                    # 让旧缓存继续服务（后端 reason=EXISTING_CACHE）。
                    self.diagnostics.record("twistytv_home_row_missing", row=row_name)
                    raise UpstreamError(502, "upstream home row changed")
                books.extend(row_books)
            elif source.startswith("genre:"):
                slug = source.split(":", 1)[1]
                books.extend(self._paged_books(f"/genre/{quote(slug, safe='')}", self.max_genre_pages, "genre"))
            elif source == "browse":
                books.extend(self._paged_books("/browse", self.max_shelf_pages, "browse"))
        unique = self._dedupe(books)
        if not unique:
            # 所有来源都空：不能把空货架写进缓存（会把线上首页刷成空白）。
            self.diagnostics.record("twistytv_shelf_empty", shelf_name=shelf_name)
            raise UpstreamError(502, "upstream returned no books")
        # 货架走后台定时刷新：允许在这里按轮预算补齐 NetShort 缺的集数（见 _fill_counts）。
        self._fill_counts(unique, wait=True)
        return unique

    def _paged_books(self, path: str, max_pages: int, label: str) -> list[dict]:
        """把 `?page=N` 翻到底：下游货架缓存越大越好，但每页都是一次真实上游请求。

        提前结束的条件：(a) 空页/无卡片；(b) 与已抓集合 0 重合（上游忽略 page 参数时
        会一直返回同一页，继续翻只会重复打上游）；(c) 上限页数。
        """
        books: list[dict] = []
        seen: set[str] = set()
        for page in range(1, max_pages + 1):
            try:
                html = self._get_text(
                    f"{path}?page={page}" if "?" not in path else f"{path}&page={page}",
                    f"twistytv_{label}_failed",
                    paced=True,
                )
            except UpstreamError as exception:
                if exception.status_code == 404 and page > 1:
                    break
                raise
            page_books = _parse_cards(html)
            if page == 1:
                self._expect_cards(html, f"twistytv_{label}_unparsable", page_books, page=page)
            if not page_books:
                break
            new_ids = [book["book_id"] for book in page_books if book["book_id"] not in seen]
            if not new_ids:
                self.diagnostics.record(f"twistytv_{label}_page_repeated", page=page)
                break
            seen.update(new_ids)
            books.extend(page_books)
        return books

    @staticmethod
    def _dedupe(books: list[dict]) -> list[dict]:
        """按 book_id 去重，并把后出现条目的简介/集数补到先出现的那条上。

        同一本书会同时出现在首页（有简介）、genre（有简介）和 `/browse`（没有）里；
        保留第一次出现能保持顺序，但不合并就永远是"先到者赢"，`/browse` 在前的批次
        会把已有简介整批抹成空。
        """
        merged: dict[str, dict] = {}
        for book in books:
            book_id = book.get("book_id")
            if not book_id:
                continue
            kept = merged.get(book_id)
            if kept is None:
                merged[book_id] = book
                continue
            if not kept.get("description") and book.get("description"):
                kept["description"] = book["description"]
            if not _as_int(kept.get("chapter_count")) and _as_int(book.get("chapter_count")):
                kept["chapter_count"] = book["chapter_count"]
            if not kept.get("book_pic") and book.get("book_pic"):
                kept["book_pic"] = book["book_pic"]
        return list(merged.values())

    # ------------------------------------------------------------- 集数补齐

    def _fill_counts(self, books: list[dict], *, wait: bool) -> None:
        """给缺集数的书目补 `chapter_count`（NetShort 卡片上本来就没有这个数字）。

        这是"每本书多打一次上游"：700 本货架 = 700 次详情页请求，一次刷完在裸 IP 上
        必然被风控。所以按轮补：每轮只补 `episode_count_backfill` 本（0 = 关），余下的
        留给下一次刷新（默认 6h 一轮），几天就把缓存填满，之后走 7 天 TTL 只做增量。

        `wait=True`（货架刷新，后台定时任务）同步补齐；`wait=False`（搜索）只填缓存，
        其余丢给后台线程按序补，不把搜索拖成十几秒。
        """
        missing: list[str] = []
        for book in books:
            book_id = str(book.get("book_id") or "")
            if not book_id:
                continue
            if _as_int(book.get("chapter_count")) <= 0:
                cached = _as_int(self._episode_counts.get(book_id))
                if cached > 0:
                    book["chapter_count"] = cached
            cached_description = self._descriptions.get(book_id)
            if not book.get("description") and cached_description:
                book["description"] = cached_description
            if _as_int(book.get("chapter_count")) <= 0 or not book.get("description"):
                missing.append(book_id)
        if not missing:
            return
        limit = self.episode_count_backfill
        if limit <= 0:
            self.diagnostics.record("twistytv_episode_count_backfill_disabled", missing=len(missing))
            return
        if not wait:
            self._queue_warm_counts(missing)
            return
        # 超出一轮预算的留给下一轮：只补前 N 本，并把剩余量记进诊断。
        if len(missing) > limit:
            self.diagnostics.record(
                "twistytv_episode_count_backfill_capped", missing=len(missing), budget=limit
            )
            missing = missing[:limit]
        with ThreadPoolExecutor(max_workers=min(EPISODE_COUNT_PROBE_CONCURRENCY, len(missing))) as executor:
            counts = dict(zip(missing, executor.map(self._probe_count, missing)))
        for book in books:
            book_id = str(book.get("book_id") or "")
            if _as_int(book.get("chapter_count")) <= 0:
                count = counts.get(book_id, 0)
                if count > 0:
                    book["chapter_count"] = count
            if not book.get("description"):
                description = self._descriptions.get(book_id)
                if description:
                    book["description"] = description

    def _probe_count(self, book_id: str) -> int:
        """翻详情页取 `show.episodeCount`；失败返回 0，不写正缓存以免把抖动钉死一周。"""
        if self._episode_count_errors.get(book_id) is not None:
            return 0
        self._pace()
        try:
            payload = self._watch_payload(book_id)
        except UpstreamError:
            self.diagnostics.record("twistytv_episode_count_failed", book_id=book_id)
            self._episode_count_errors.put(book_id, 0)
            return 0
        show = _as_dict(payload.get("show"))
        description = str(show.get("description") or "").strip()
        if description:
            self._descriptions.put(book_id, description)
        count = _as_int(show.get("episodeCount")) or len(_as_list(payload.get("episodes")))
        if count > 0:
            self._episode_counts.put(book_id, count)
        return count

    def _remember_count(self, book_id: str, count: int) -> None:
        """详情/播放路径本来就拿到了整页数据，顺手把集数记进缓存。"""
        if book_id and count > 0:
            self._episode_counts.put(book_id, count)

    def _queue_warm_counts(self, book_ids: list[str]) -> None:
        """把缺集数的 ID 交给单个守护线程补齐。

        `_warm_pending` 从入队到探针结束一直占着，所以"探针进行中"的重复搜索不会重复排队；
        探针失败则由 `_episode_count_errors` 冷却，不会在下一轮搜索里被重新排队。
        """
        with self._warm_lock:
            for book_id in book_ids:
                if book_id in self._warm_pending or _as_int(self._episode_counts.get(book_id)) > 0:
                    continue
                if self._warm_queue.qsize() >= EPISODE_COUNT_WARM_QUEUE_MAX:
                    break
                self._warm_pending.add(book_id)
                self._warm_queue.put(book_id)
            if self._warm_thread is None or not self._warm_thread.is_alive():
                self._warm_thread = threading.Thread(
                    target=self._warm_loop, name="twistytv-episode-counts", daemon=True
                )
                self._warm_thread.start()

    def _warm_loop(self) -> None:
        # 单线程按序补，既不会和货架刷新抢上游，也不会把 16 个 gunicorn 线程用满。
        while True:
            book_id = self._warm_queue.get()
            try:
                self._probe_count(book_id)
            except Exception:  # 单本失败不能把常驻线程带走
                self.diagnostics.record("twistytv_episode_count_failed", book_id=book_id)
            finally:
                # 探完才释放：探针进行的这段时间里用户再搜同一个词，不该把同一 ID 再排一遍。
                with self._warm_lock:
                    self._warm_pending.discard(book_id)
                self._warm_queue.task_done()

    # ------------------------------------------------------------- 详情/播放

    def _watch_payload(self, book_id: str) -> dict:
        """解析 `/watch/{id}`：返回 {show, episodes, provider}。

        首页卡片与搜索结果都是同一套 id，解析结果按 book_id 缓存——`episodes()` 与
        `video()` 会连着调，不缓存就每次播放都白抓一次 120 KB 页面。
        """
        cached = self._page_cache.get(book_id)
        if cached is not None:
            return cached
        html = self._get_text(f"/watch/{quote(book_id, safe='')}", "twistytv_watch_failed")
        payload = self._parse_watch(html, book_id)
        if payload is None:
            self.diagnostics.record("twistytv_watch_unparsed", book_id=book_id)
            raise UpstreamError(502, "upstream payload changed")
        self._page_cache.put(book_id, payload)
        return payload

    def _parse_watch(self, html: str, book_id: str):
        flight = _flight_text(html)
        if not flight:
            return None
        envelope, _ = _decode_after(flight, '{"show":{"id":"%s"' % book_id)
        show = None
        episodes = []
        if isinstance(envelope, dict):
            show = envelope.get("show")
            episodes = _as_list(envelope.get("episodes"))
        if not isinstance(show, dict):
            # `show` 不一定是对象首键：退化成先解 show，再按位置找 episodes。
            show, show_end = _decode_after(flight, '"show":{"id":"%s"' % book_id)
            if not isinstance(show, dict):
                return None
            episodes, _ = _decode_after(flight[show_end:], '"episodes":[')
            episodes = _as_list(episodes)
        provider = str(show.get("provider") or "").strip().lower()
        return {"show": show, "episodes": episodes, "provider": provider}

    def episodes(self, book_id: str, filtered_title: str, locale: str = DEFAULT_LOCALE):
        payload = self._watch_payload(book_id)
        show = _as_dict(payload["show"])
        items = [_as_dict(item) for item in payload["episodes"]]
        title = str(show.get("title") or "").strip()
        book = {
            "book_id": str(show.get("id") or book_id),
            "book_title": title,
            "filtered_title": filtered_title or _slugify(title),
            "book_pic": str(show.get("posterUrl") or ""),
            "description": str(show.get("description") or ""),
            "chapter_count": _as_int(show.get("episodeCount")) or len(items),
        }
        # 这里已经把整页拿下来了，顺手喂给集数缓存，省掉列表页的补抓。
        self._remember_count(book["book_id"], book["chapter_count"])
        return {
            "book": book,
            "episodes": [self._map_episode(item) for item in items if _as_int(item.get("number")) > 0],
        }

    @staticmethod
    def _map_episode(item: dict) -> dict:
        return {
            "episode": _as_int(item.get("number")),
            # 后端播放接口用 chapter_id 做缓存键，这里用上游自己的分集 id。
            "chapter_id": str(item.get("episodeId") or item.get("number") or ""),
            "title": str(item.get("title") or ""),
            # TwistyTV 没有分集简介；App 会回退显示短剧简介。
            "description": "",
        }

    def video(
        self,
        book_id: str,
        episode_num: int,
        filtered_title: str,
        chapter_id: str,
        locale: str = DEFAULT_LOCALE,
    ):
        payload = self._watch_payload(book_id)
        episodes = [_as_dict(item) for item in payload["episodes"]]
        # 播放路径同样已经拿到整页，顺手把集数记下（下一轮货架刷新就不用再翻详情页）。
        show = _as_dict(payload.get("show"))
        self._remember_count(book_id, _as_int(show.get("episodeCount")) or len(episodes))
        position = next(
            (index for index, item in enumerate(episodes) if _as_int(item.get("number")) == episode_num),
            None,
        )
        if position is None:
            self.diagnostics.record("twistytv_episode_missing", book_id=book_id, episode_num=episode_num)
            raise UpstreamError(404, "upstream not found")
        episode = episodes[position]
        video_url = str(episode.get("streamUrl") or "")
        if not video_url:
            # NetShort 的 streamUrl 恒为空，要单独换一次签名直链。
            video_url = self._stream_url(book_id, payload["provider"], episode_num)
        if not video_url:
            self.diagnostics.record(
                "twistytv_video_missing",
                book_id=book_id,
                episode_num=episode_num,
                provider=payload["provider"],
            )
            raise UpstreamError(404, "upstream not found")
        next_episode = (
            self._map_episode(episodes[position + 1]) if position + 1 < len(episodes) else None
        )
        return {
            "video_url": video_url,
            "episode": episode_num,
            "duration": self._duration(book_id, episode_num, video_url),
            "next_episode": next_episode,
        }

    # ------------------------------------------------------------- 时长探针

    def _duration(self, book_id: str, episode_num: int, video_url: str) -> int:
        key = f"{book_id}|{episode_num}"
        cached = self._duration_cache.get(key)
        if cached is not None:
            return cached
        if self._duration_error_cache.get(key) is not None:
            return 0
        if not self.duration_probe_enabled:
            # 探针是"每集一次额外的 CDN 请求"：默认关掉，积分为 0 比把 IP 打进风控好。
            # 打开后行为与以前一致（失败也会进 120s 负缓存）。
            self.diagnostics.record("twistytv_duration_probe_disabled", book_id=book_id, episode_num=episode_num)
            return 0
        seconds = self._probe_duration(video_url)
        if seconds <= 0:
            # 失败也要缓存：探针可能已经拉过 MB 级响应，每次开场重试会把单一 worker 拖死。
            self._duration_error_cache.put(key, 0)
            self.diagnostics.record("twistytv_duration_unknown", book_id=book_id, episode_num=episode_num)
            return 0
        self._duration_cache.put(key, seconds)
        return seconds

    def _probe_duration(self, video_url: str, depth: int = 0) -> int:
        """取时长：先按 MP4 的 mvhd 读，遇到 HLS 就累加播放列表的分段时长。

        实测 TwistyTV 的三家片源返回两种格式：DramaBox / NetShort 是 MP4，
        ReelShort 的 `streamUrl` 其实是 m3u8（URL 上没有扩展名，只能靠内容判断）。
        只认 MP4 的话 ReelShort 那一半内容的时长为 0，等于那些集不产积分。
        """
        for byte_range in (f"bytes=0-{DURATION_PROBE_BYTES - 1}", f"bytes=-{DURATION_PROBE_BYTES}"):
            try:
                response = self._request(video_url, extra_headers={"Range": byte_range})
            except UpstreamError:
                # 上游已拒绝/超限：再发一次尾段只会重复同一个失败，白等一个 15s 预算。
                return 0
            body = getattr(response, "content", b"") or b""
            if body.startswith(b"#EXTM3U"):
                seconds = self._hls_duration(video_url, body, depth)
                if seconds > 0 or depth > 0:
                    # 只要解析出了时长就收工；尾段 Range 对 m3u8 没有意义。
                    return seconds
                # 主播放列表解不动时继续试尾段：有的 CDN 对 m3u8 的 Range 直接返回 MP4。
                continue
            if response.status_code != 206 and len(body) > DURATION_PROBE_BYTES:
                # CDN 忽略了 Range，返回了整个文件：尾段请求同样会被忽略。
                return _parse_mp4_duration(body)
            seconds = _parse_mp4_duration(body)
            if seconds > 0:
                return seconds
        return 0

    def _hls_duration(self, playlist_url: str, body: bytes, depth: int) -> int:
        text = body.decode("utf-8", "replace")
        media_playlist = "#EXT-X-STREAM-INF" not in text
        if not media_playlist and depth < 2:
            # 主播放列表：取第一条变体（按上游排序即首选清晰度）再解析媒体播放列表。
            lines = text.splitlines()
            for index, line in enumerate(lines):
                if line.startswith("#EXT-X-STREAM-INF"):
                    for candidate in lines[index + 1 :]:
                        if candidate and not candidate.startswith("#"):
                            return self._probe_duration(urljoin(playlist_url, candidate.strip()), depth + 1)
                    break
            self.diagnostics.record("twistytv_hls_variant_missing", url=playlist_url)
            return 0
        total = sum(float(value) for value in re.findall(r"#EXTINF:\s*([0-9.]+)", text))
        if total <= 0:
            # 区分"m3u8 解不动"和"MP4 里没有 mvhd"：前者是上游格式变了，后者是 CDN 的事。
            self.diagnostics.record("twistytv_hls_unparsed", url=playlist_url, depth=depth)
            return 0
        return int(round(total))
