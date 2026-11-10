"""TwistyTV 上游适配的单元测试。

全部离线：解析器吃合成 HTML/RSC 片段，HTTP 由 `RecordingFetcher` 顶掉。
这些用例存在的意义是——上游一改版，最早挂在这里，而不是挂在生产首页。
"""

import json

import pytest

import app as app_module
from fetcher import UpstreamError
from upstreams import twistytv


BOOK_ID = "ns_2105841747766697985"
WATCH_URL = f"/watch/{BOOK_ID}"


def _flight_html(payload: dict, split_at: int = 0) -> str:
    """把 JSON 包成真实页面那种 `self.__next_f.push([1,"…"])` 片段（可跨片段截断）。"""
    text = json.dumps(payload, ensure_ascii=False, separators=(",", ":"))
    chunks = [text] if not split_at else [text[:split_at], text[split_at:]]
    return "".join(
        f"<script>self.__next_f.push([1,{json.dumps(chunk)}])</script>" for chunk in chunks
    )


def _show(**overrides) -> dict:
    show = {
        "id": BOOK_ID,
        "title": "My Unborn Daughter Made Me a Billionaire",
        "posterUrl": "https://cdn.example/cover.webp",
        "category": "NetShort",
        "provider": "netshort",
        "episodeCount": 2,
        "genres": ["romance"],
        "description": "A widow builds an empire.",
    }
    show.update(overrides)
    return show


def _episode(number: int, stream: str = "", episode_id: str = "") -> dict:
    return {
        "number": number,
        "episodeId": episode_id or f"ep{number}",
        "title": f"Episode {number}",
        "thumbnailUrl": "",
        "streamUrl": stream,
    }


class FakeResponse:
    def __init__(self, status_code: int = 200, text: str = "", content: bytes = b""):
        self.status_code = status_code
        self.text = text
        self.content = content

    @property
    def ok(self) -> bool:
        return self.status_code < 400


class RecordingFetcher(twistytv.TwistytvClient):
    """按 URL 片段路由的假传输层，顺带把调用记录留给断言。"""

    def __init__(self, routes: dict, **kwargs):
        super().__init__("https://twistytv.com", 5.0, **kwargs)
        self.routes = routes
        self.calls = []

    def _request(self, url, params=None, *, method="GET", json_body=None, extra_headers=None):
        self.calls.append({"url": url, "params": dict(params or {}), "headers": dict(extra_headers or {})})
        for fragment, response in self.routes.items():
            if fragment in url or (fragment == "api/stream" and params and url.endswith("/api/stream")):
                if isinstance(response, Exception):
                    raise response
                return response(params) if callable(response) else response
        raise UpstreamError(404, "upstream not found")

    def urls(self) -> list[str]:
        return [call["url"] for call in self.calls]


CARD_HTML = """
<section class="row" aria-labelledby="trending">
  <ul><li><a class="show-card" href="/watch/ns_1"><span class="card-media">
    <img src="https://cdn.example/a.webp" alt="A"/><span class="badge-new">NEW</span>
    <span class="show-card-hover"><span class="show-card-hover-desc">Dumped &amp; broke.</span>
    <span class="show-card-hover-meta">30 episodes • Revenge</span></span></span>
    <span class="show-card-title">A &amp; B</span>
    <span class="show-card-meta"><span class="heat">1K</span>Revenge</span></a></li></ul>
</section>
<section class="row" aria-labelledby="new">
  <ul><li><a class="show-card" href="/watch/db_4200"><img src="https://cdn.example/b.webp"/>
    <span class="show-card-title">Fresh One</span>
    <span class="show-card-meta">12 episodes</span></a></li></ul>
</section>
"""


def test_parse_cards_reads_title_poster_description_and_episode_count():
    books = twistytv._parse_cards(CARD_HTML)

    assert [book["book_id"] for book in books] == ["ns_1", "db_4200"]
    assert books[0]["book_title"] == "A & B"
    assert books[0]["filtered_title"] == "a-b"
    assert books[0]["book_pic"] == "https://cdn.example/a.webp"
    assert books[0]["description"] == "Dumped & broke."
    assert books[0]["chapter_count"] == 30
    assert books[1]["chapter_count"] == 12


def test_parse_home_rows_splits_sections():
    rows = twistytv._parse_home_rows(CARD_HTML)

    assert set(rows) == {"trending", "new"}
    assert [book["book_id"] for book in rows["new"]] == ["db_4200"]


def test_watch_payload_reassembles_split_flight_chunks():
    payload = {"show": _show(), "providerId": "netshort", "episodes": [_episode(1), _episode(2)]}
    html = _flight_html(payload, split_at=40)

    parsed = twistytv.TwistytvClient()._parse_watch(html, BOOK_ID)

    assert parsed["provider"] == "netshort"
    assert parsed["show"]["title"] == "My Unborn Daughter Made Me a Billionaire"
    assert [item["number"] for item in parsed["episodes"]] == [1, 2]


def test_watch_payload_falls_back_when_show_is_not_the_first_key():
    payload = {"providerId": "reelshort", "show": _show(provider="reelshort"), "episodes": [_episode(1)]}

    parsed = twistytv.TwistytvClient()._parse_watch(_flight_html(payload), BOOK_ID)

    assert parsed["provider"] == "reelshort"
    assert len(parsed["episodes"]) == 1


def test_episodes_maps_book_and_chapter_ids():
    payload = {"show": _show(), "providerId": "netshort", "episodes": [_episode(1), _episode(2)]}
    client = RecordingFetcher({WATCH_URL: FakeResponse(200, _flight_html(payload))})

    result = client.episodes(BOOK_ID, "my-unborn-daughter")

    assert result["book"]["book_id"] == BOOK_ID
    assert result["book"]["chapter_count"] == 2
    assert result["book"]["filtered_title"] == "my-unborn-daughter"
    assert [episode["chapter_id"] for episode in result["episodes"]] == ["ep1", "ep2"]


def test_video_uses_ssr_stream_url_without_extra_upstream_call():
    payload = {"show": _show(provider="dramabox"), "episodes": [_episode(1, stream="https://cdn.example/1.mp4")]}
    client = RecordingFetcher(
        {
            WATCH_URL: FakeResponse(200, _flight_html(payload)),
            "cdn.example": FakeResponse(206, content=_mvhd_payload(timescale=1000, duration=125_000)),
        }
    )

    video = client.video(BOOK_ID, 1, "title", "ep1")

    assert video["video_url"] == "https://cdn.example/1.mp4"
    assert video["duration"] == 125
    assert "/api/stream" not in " ".join(client.urls())


def test_video_asks_stream_endpoint_for_netshort_and_sends_origin():
    payload = {"show": _show(), "episodes": [_episode(1), _episode(2)]}
    client = RecordingFetcher(
        {
            WATCH_URL: FakeResponse(200, _flight_html(payload)),
            "api/stream": FakeResponse(200, json.dumps({"url": "https://ns.example/1.mp4", "shared": True})),
            "ns.example": FakeResponse(206, content=_mvhd_payload(timescale=1000, duration=90_000)),
        }
    )

    video = client.video(BOOK_ID, 1, "title", "ep1")

    assert video["video_url"] == "https://ns.example/1.mp4"
    stream_call = next(call for call in client.calls if call["url"].endswith("/api/stream"))
    assert stream_call["params"] == {"series": BOOK_ID, "episode": "1", "provider": "netshort"}
    assert stream_call["headers"]["Origin"] == "https://twistytv.com"


def test_video_missing_stream_is_404():
    payload = {"show": _show(), "episodes": [_episode(1)]}
    client = RecordingFetcher(
        {
            WATCH_URL: FakeResponse(200, _flight_html(payload)),
            "api/stream": FakeResponse(200, json.dumps({"missing": True})),
        }
    )

    with pytest.raises(UpstreamError) as error:
        client.video(BOOK_ID, 1, "title", "ep1")

    assert error.value.status_code == 404


def test_video_unknown_episode_is_404():
    payload = {"show": _show(), "episodes": [_episode(1)]}
    client = RecordingFetcher({WATCH_URL: FakeResponse(200, _flight_html(payload))})

    with pytest.raises(UpstreamError) as error:
        client.video(BOOK_ID, 9, "title", "ep9")

    assert error.value.status_code == 404


def test_watch_page_is_parsed_once_per_book():
    payload = {"show": _show(), "episodes": [_episode(1, stream="https://cdn.example/1.mp4")]}
    client = RecordingFetcher(
        {
            WATCH_URL: FakeResponse(200, _flight_html(payload)),
            "cdn.example": FakeResponse(206, content=_mvhd_payload(1000, 60_000)),
        }
    )

    client.episodes(BOOK_ID, "title")
    client.video(BOOK_ID, 1, "title", "ep1")

    assert sum(1 for url in client.urls() if url.endswith("/watch/" + BOOK_ID)) == 1


def test_duration_is_cached_across_calls():
    payload = {"show": _show(), "episodes": [_episode(1, stream="https://cdn.example/1.mp4")]}
    client = RecordingFetcher(
        {
            WATCH_URL: FakeResponse(200, _flight_html(payload)),
            "cdn.example": FakeResponse(206, content=_mvhd_payload(1000, 60_000)),
        }
    )

    assert client.video(BOOK_ID, 1, "title", "ep1")["duration"] == 60
    assert client.video(BOOK_ID, 1, "title", "ep1")["duration"] == 60
    assert client.urls().count("https://cdn.example/1.mp4") == 1


def test_duration_falls_back_to_tail_range_when_moov_is_at_the_end():
    payload = {"show": _show(), "episodes": [_episode(1, stream="https://cdn.example/late.mp4")]}
    responses = [
        FakeResponse(206, content=b"\x00" * 64),
        FakeResponse(206, content=_mvhd_payload(1000, 42_000)),
    ]
    client = RecordingFetcher(
        {WATCH_URL: FakeResponse(200, _flight_html(payload))}
    )
    client.routes["late.mp4"] = lambda params=None: responses.pop(0)

    assert client.video(BOOK_ID, 1, "title", "ep1")["duration"] == 42


def test_unknown_duration_is_zero_and_reported():
    payload = {"show": _show(), "episodes": [_episode(1, stream="https://cdn.example/garbage.mp4")]}
    client = RecordingFetcher(
        {
            WATCH_URL: FakeResponse(200, _flight_html(payload)),
            "garbage.mp4": FakeResponse(206, content=b"not a video"),
        }
    )

    assert client.video(BOOK_ID, 1, "title", "ep1")["duration"] == 0
    counters = client.diagnostics.snapshot()["counters"]
    assert counters["twistytv_duration_unknown"] == 1


def test_shelf_dramadub_reads_dubbed_genre_page():
    client = RecordingFetcher({"/genre/dubbed": FakeResponse(200, CARD_HTML)})

    books = client.shelf("dramadub")

    assert [book["book_id"] for book in books] == ["ns_1", "db_4200"]


def test_shelf_recommend_merges_home_rows_and_browse_pages():
    client = RecordingFetcher(
        {
            "/browse?page=1": FakeResponse(200, CARD_HTML),
            "/browse?page=2": FakeResponse(200, ""),
            "https://twistytv.com/": FakeResponse(200, CARD_HTML),
        }
    )

    books = client.shelf("recommend")

    assert [book["book_id"] for book in books] == ["ns_1", "db_4200"]
    assert any("/browse?page=1" in url for url in client.urls())


def test_search_uses_search_page_not_suggest():
    client = RecordingFetcher({"/search?q=love": FakeResponse(200, CARD_HTML)})

    books = client.search("love")

    assert [book["book_id"] for book in books] == ["ns_1", "db_4200"]
    assert client.urls() == ["https://twistytv.com/search?q=love"]


# --------------------------------------------------------------- 路由与时长解析


class _StubClient:
    def __init__(self, marker: str):
        self.marker = marker
        self.diagnostics = app_module.ProviderDiagnostics()

    def search(self, keywords, locale="en"):
        return [{"book_id": self.marker}]

    def shelf(self, shelf_name, locale="en"):
        return [{"book_id": self.marker}]

    def episodes(self, book_id, filtered_title, locale="en"):
        return {"book": {"book_id": book_id}, "episodes": []}

    def video(self, book_id, episode_num, filtered_title, chapter_id, locale="en"):
        return {"video_url": "https://cdn.example/v.mp4", "episode": episode_num, "duration": 1}


@pytest.fixture
def routed_client():
    application = app_module.create_app(
        clients={"reelshort": _StubClient("rs"), "twistytv": _StubClient("tt")},
        internal_token="router-token",
    )
    return application.test_client()


def _headers(upstream=None):
    headers = {"X-Content-Provider-Token": "router-token"}
    if upstream is not None:
        headers[app_module.UPSTREAM_HEADER] = upstream
    return headers


def test_default_upstream_is_reelshort_for_backward_compatibility(routed_client):
    response = routed_client.get("/api/v1/reelshort/recommend", headers=_headers())

    assert response.get_json() == {"books": [{"book_id": "rs"}]}


def test_upstream_header_selects_twistytv(routed_client):
    response = routed_client.get(
        "/api/v1/reelshort/recommend", headers=_headers("twistytv")
    )

    assert response.get_json() == {"books": [{"book_id": "tt"}]}


def test_unknown_upstream_is_rejected(routed_client):
    response = routed_client.get("/api/v1/reelshort/search?keywords=x", headers=_headers("porn"))

    assert response.status_code == 400
    assert response.get_json() == {"error": "unknown upstream"}


def test_diagnostics_reports_every_upstream(routed_client):
    payload = routed_client.get("/diagnostics", headers=_headers()).get_json()

    assert set(payload["upstreams"]) == {"reelshort", "twistytv"}
    assert payload["diagnostics"] == {"total_events": 0, "counters": {}, "recent_events": []}


def test_diagnostics_follow_the_active_upstream():
    reelshort_stub = _StubClient("rs")
    twistytv_stub = _StubClient("tt")
    twistytv_stub.diagnostics.record("twistytv_watch_failed", book_id="b1")
    application = app_module.create_app(
        clients={"reelshort": reelshort_stub, "twistytv": twistytv_stub},
        internal_token="router-token",
    )
    client = application.test_client()
    twistytv_headers = {"X-Content-Provider-Token": "router-token", app_module.UPSTREAM_HEADER: "twistytv"}

    payload = client.get("/diagnostics", headers=twistytv_headers).get_json()

    # 后端只读顶层 diagnostics（诊断页 + 告警水位线）：固定取 reelshort 会把默认上游的故障藏起来。
    assert payload["active_upstream"] == "twistytv"
    assert payload["diagnostics"]["counters"] == {"twistytv_watch_failed": 1}
    assert client.get("/diagnostics", headers={"X-Content-Provider-Token": "router-token"}).get_json()[
        "diagnostics"
    ]["counters"] == {}


def test_new_release_records_a_diagnostic_when_the_home_row_is_gone():
    home = CARD_HTML.replace('aria-labelledby="new"', 'aria-labelledby="fresh"')
    client = RecordingFetcher(
        {
            "/browse?page=1": FakeResponse(200, CARD_HTML),
            "/browse?page=2": FakeResponse(200, ""),
            "https://twistytv.com/": FakeResponse(200, home),
        }
    )

    books = client.shelf("newrelease")

    assert [book["book_id"] for book in books] == ["ns_1", "db_4200"]
    assert client.diagnostics.snapshot()["counters"]["twistytv_home_row_missing"] == 1


def test_duration_probe_failure_is_not_retried_for_each_play():
    payload = {"show": _show(), "episodes": [_episode(1, stream="https://cdn.example/broken.mp4")]}
    client = RecordingFetcher({WATCH_URL: FakeResponse(200, _flight_html(payload))})
    client.routes["broken.mp4"] = UpstreamError(502, "upstream request failed")

    assert client.video(BOOK_ID, 1, "title", "ep1")["duration"] == 0
    assert client.urls().count("https://cdn.example/broken.mp4") == 1  # 失败后不再补发尾段请求

    assert client.video(BOOK_ID, 1, "title", "ep1")["duration"] == 0
    # 负缓存：同一次抖动不该让每次播放都去重试探针（每次都可能拖回 MB 级响应）。
    assert client.urls().count("https://cdn.example/broken.mp4") == 1


def _mvhd_payload(timescale: int, duration: int, version: int = 0) -> bytes:
    """构造一个带 moov/mvhd 的最小字节串，供时长解析用例使用。"""
    if version == 1:
        body = (
            bytes([1, 0, 0, 0])
            + (0).to_bytes(8, "big")
            + (0).to_bytes(8, "big")
            + timescale.to_bytes(4, "big")
            + duration.to_bytes(8, "big")
        )
    else:
        body = (
            bytes([0, 0, 0, 0])
            + (0).to_bytes(4, "big")
            + (0).to_bytes(4, "big")
            + timescale.to_bytes(4, "big")
            + duration.to_bytes(4, "big")
        )
    mvhd = (len(body) + 8).to_bytes(4, "big") + b"mvhd" + body
    moov = (len(mvhd) + 8).to_bytes(4, "big") + b"moov" + mvhd
    return b"\x00\x00\x00\x18ftypmp42" + moov


def test_hls_master_playlist_resolves_variant_and_sums_segments():
    payload = {"show": _show(provider="reelshort"), "episodes": [_episode(1, stream="https://cdn.example/master.m3u8")]}
    client = RecordingFetcher(
        {
            WATCH_URL: FakeResponse(200, _flight_html(payload)),
            "master.m3u8": FakeResponse(
                206,
                content=b"#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=800000\nindex.m3u8\n",
            ),
            "index.m3u8": FakeResponse(
                206,
                content=b"#EXTM3U\n#EXTINF:5.005,\nseg0.ts\n#EXTINF:6.5,\nseg1.ts\n#EXT-X-ENDLIST\n",
            ),
        }
    )

    # ReelShort 的 streamUrl 没有扩展名，实测返回 m3u8——只认 MP4 的话这半边内容没有时长。
    assert client.video(BOOK_ID, 1, "title", "ep1")["duration"] == 12
    assert "https://cdn.example/index.m3u8" in client.urls()


def test_hls_media_playlist_without_segments_is_zero():
    payload = {"show": _show(provider="reelshort"), "episodes": [_episode(1, stream="https://cdn.example/empty.m3u8")]}
    client = RecordingFetcher(
        {
            WATCH_URL: FakeResponse(200, _flight_html(payload)),
            "empty.m3u8": FakeResponse(206, content=b"#EXTM3U\n#EXT-X-ENDLIST\n"),
        }
    )

    assert client.video(BOOK_ID, 1, "title", "ep1")["duration"] == 0


def test_parse_mp4_duration_handles_both_mvhd_versions():
    assert twistytv._parse_mp4_duration(_mvhd_payload(1000, 125_000)) == 125
    assert twistytv._parse_mp4_duration(_mvhd_payload(1000, 125_000, version=1)) == 125


def test_parse_mp4_duration_rejects_garbage():
    assert twistytv._parse_mp4_duration(b"") == 0
    assert twistytv._parse_mp4_duration(b"no boxes here") == 0
    # timescale 为 0 时不能做除法
    assert twistytv._parse_mp4_duration(_mvhd_payload(0, 1000)) == 0
