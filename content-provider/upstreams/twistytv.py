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
DEFAULT_MAX_SHELF_PAGES = 3
# NetShort 卡片不给集数，补一次要翻 120 KB 的详情页：一本只补一次，按 7 天缓存。
EPISODE_COUNT_CACHE_SECONDS_ENV = "TWISTYTV_EPISODE_COUNT_CACHE_SECONDS"
DEFAULT_EPISODE_COUNT_CACHE_SECONDS = 7 * 24 * 3600.0
# 货架刷新在后台跑，6 路并发实测上游能吃下；搜索只读缓存，不在请求里现抓。
EPISODE_COUNT_PROBE_CONCURRENCY = 6
# 后台预热队列上限：搜索一次最多塞几十个 ID，这里是防止长期运行无限长。
EPISODE_COUNT_WARM_QUEUE_MAX = 2000

# 上游 provider 白名单：未知值一律拒绝，避免把任意字符串拼进上游 URL。
KNOWN_PROVIDERS = {"netshort", "reelshort", "dramabox"}

# 货架名（后端 ContentShelfType.apiValue）→ 抓取来源。
SHELF_SOURCES = {
    "recommend": ("home", "browse"),
    "newrelease": ("home:new",),
    "dramadub": ("genre:dubbed",),
}

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


def _parse_cards(chunk: str) -> list[dict]:
    """解析一段 HTML 里的短剧卡片。列表页（首页/browse/genre/search）通用。"""
    books = []
    for match in _CARD_RE.finditer(chunk or ""):
        book_id, body = match.group(1), match.group(2)
        title = _text(_TITLE_RE.search(body).group(1)) if _TITLE_RE.search(body) else ""
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


def _parse_home_rows(html: str) -> dict[str, list[dict]]:
    """首页按 `<section class="row" aria-labelledby="…">` 切段，得到 {行名: 卡片列表}。"""
    marks = [(match.start(), match.group(1)) for match in _SECTION_RE.finditer(html or "")]
    rows: dict[str, list[dict]] = {}
    for position, (start, name) in enumerate(marks):
        end = marks[position + 1][0] if position + 1 < len(marks) else len(html)
        rows[name] = _parse_cards(html[start:end])
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
            max_shelf_pages=_bounded_int_env("TWISTYTV_MAX_SHELF_PAGES", DEFAULT_MAX_SHELF_PAGES, 20),
        )

    # ------------------------------------------------------------------ HTTP

    def _get_text(self, path: str, event_type: str) -> str:
        response = self._request(f"{self.site_url}{path}")
        if response.status_code == 404:
            raise UpstreamError(404, "upstream not found")
        if response.status_code >= 400:
            self.diagnostics.record(event_type, path=path, status_code=response.status_code)
            raise UpstreamError(502, "upstream request failed")
        return response.text

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
        books = _parse_cards(html)
        if not books:
            self.diagnostics.record("twistytv_search_empty", keywords=keywords)
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
                html = self._get_text("/", "twistytv_home_failed")
                for cards in _parse_home_rows(html).values():
                    books.extend(cards)
            elif source.startswith("home:"):
                html = self._get_text("/", "twistytv_home_failed")
                row_name = source.split(":", 1)[1]
                row_books = _parse_home_rows(html).get(row_name, [])
                if not row_books:
                    # 上游改版把 section id 改掉时，这里会退化成"和 recommend 一样的全目录"。
                    # 记一条诊断，让运维能看到是选择器漂移，而不是以为这个货架本来就这样。
                    self.diagnostics.record("twistytv_home_row_missing", row=row_name)
                books.extend(row_books or self._browse_books())
            elif source.startswith("genre:"):
                slug = source.split(":", 1)[1]
                html = self._get_text(f"/genre/{quote(slug, safe='')}", "twistytv_genre_failed")
                books.extend(_parse_cards(html))
            elif source == "browse":
                books.extend(self._browse_books())
        unique = self._dedupe(books)
        # 货架走的是后台定时刷新，允许在这里把 NetShort 缺的集数补齐。
        self._fill_counts(unique, wait=True)
        return unique

    def _browse_books(self) -> list[dict]:
        books = []
        for page in range(1, self.max_shelf_pages + 1):
            try:
                html = self._get_text(f"/browse?page={page}", "twistytv_browse_failed")
            except UpstreamError as exception:
                if exception.status_code == 404:
                    break
                raise
            page_books = _parse_cards(html)
            if not page_books:
                break
            books.extend(page_books)
        return books

    @staticmethod
    def _dedupe(books: list[dict]) -> list[dict]:
        seen = set()
        unique = []
        for book in books:
            book_id = book.get("book_id")
            if not book_id or book_id in seen:
                continue
            seen.add(book_id)
            unique.append(book)
        return unique

    # ------------------------------------------------------------- 集数补齐

    def _fill_counts(self, books: list[dict], *, wait: bool) -> None:
        """给缺集数的书目补 `chapter_count`（NetShort 卡片上本来就没有这个数字）。

        `wait=True` 用于货架刷新：调用方是后台定时任务，多花二十来秒无所谓，但结果要正确。
        `wait=False` 用于搜索：一次搜索几十本、每本 120 KB 详情页，现抓会把搜索拖到十几秒，
        所以只填缓存里已有的，其余丢给后台线程按序预热，下次搜索自然正确。
        """
        missing: list[str] = []
        for book in books:
            if _as_int(book.get("chapter_count")) > 0:
                continue
            book_id = str(book.get("book_id") or "")
            if not book_id:
                continue
            cached = _as_int(self._episode_counts.get(book_id))
            if cached > 0:
                book["chapter_count"] = cached
            else:
                missing.append(book_id)
        if not missing:
            return
        if not wait:
            self._queue_warm_counts(missing)
            return
        with ThreadPoolExecutor(max_workers=min(EPISODE_COUNT_PROBE_CONCURRENCY, len(missing))) as executor:
            counts = dict(zip(missing, executor.map(self._probe_count, missing)))
        for book in books:
            if _as_int(book.get("chapter_count")) > 0:
                continue
            count = counts.get(str(book.get("book_id") or ""), 0)
            if count > 0:
                book["chapter_count"] = count

    def _probe_count(self, book_id: str) -> int:
        """翻详情页取 `show.episodeCount`；失败返回 0，不写缓存以免把抖动钉死一周。"""
        try:
            payload = self._watch_payload(book_id)
        except UpstreamError:
            self.diagnostics.record("twistytv_episode_count_failed", book_id=book_id)
            return 0
        show = _as_dict(payload.get("show"))
        count = _as_int(show.get("episodeCount")) or len(_as_list(payload.get("episodes")))
        if count > 0:
            self._episode_counts.put(book_id, count)
        return count

    def _remember_count(self, book_id: str, count: int) -> None:
        """详情/播放路径本来就拿到了整页数据，顺手把集数记进缓存。"""
        if book_id and count > 0:
            self._episode_counts.put(book_id, count)

    def _queue_warm_counts(self, book_ids: list[str]) -> None:
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
                return self._hls_duration(video_url, body, depth)
            if response.status_code != 206 and len(body) > DURATION_PROBE_BYTES:
                # CDN 忽略了 Range，返回了整个文件：尾段请求同样会被忽略。
                return _parse_mp4_duration(body)
            seconds = _parse_mp4_duration(body)
            if seconds > 0:
                return seconds
        return 0

    def _hls_duration(self, playlist_url: str, body: bytes, depth: int) -> int:
        text = body.decode("utf-8", "replace")
        if "#EXT-X-STREAM-INF" in text and depth < 2:
            # 主播放列表：取第一条变体（按上游排序即首选清晰度）再解析媒体播放列表。
            lines = text.splitlines()
            for index, line in enumerate(lines):
                if line.startswith("#EXT-X-STREAM-INF"):
                    for candidate in lines[index + 1 :]:
                        if candidate and not candidate.startswith("#"):
                            return self._probe_duration(urljoin(playlist_url, candidate.strip()), depth + 1)
                    break
            return 0
        total = sum(float(value) for value in re.findall(r"#EXTINF:\s*([0-9.]+)", text))
        return int(round(total)) if total > 0 else 0
