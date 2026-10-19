import json
import math
import threading

import pytest
import time

import app as app_module
from app import INTERNAL_TOKEN_ENV, INTERNAL_TOKEN_HEADER, ReelShortClient, UpstreamError, create_app


INTERNAL_TOKEN = "test-internal-token"


@pytest.fixture(autouse=True)
def resolve_test_hosts_to_public_address(monkeypatch):
    monkeypatch.setattr(ReelShortClient, "_resolve_addresses", staticmethod(lambda host: ["93.184.216.34"]))
    monkeypatch.setattr(
        ReelShortClient,
        "_request_get",
        lambda self, pinned_url, original_url, **kwargs: app_module.requests.get(original_url, **kwargs),
    )


@pytest.fixture(autouse=True)
def configure_internal_token(monkeypatch):
    # P2-16：除 /health 外所有端点都要求内部共享密钥，测试显式配置。
    monkeypatch.setenv(INTERNAL_TOKEN_ENV, INTERNAL_TOKEN)


def internal_token_headers():
    return {INTERNAL_TOKEN_HEADER: INTERNAL_TOKEN}


class AuthedTestClient:
    """给 Flask 测试客户端的所有请求自动补上内部 Token 请求头。"""

    def __init__(self, client):
        self._client = client

    def open(self, *args, **kwargs):
        headers = dict(kwargs.pop("headers", None) or {})
        headers.update(internal_token_headers())
        return self._client.open(*args, headers=headers, **kwargs)

    def get(self, *args, **kwargs):
        return self.open(*args, method="GET", **kwargs)

    def post(self, *args, **kwargs):
        return self.open(*args, method="POST", **kwargs)

    def put(self, *args, **kwargs):
        return self.open(*args, method="PUT", **kwargs)

    def delete(self, *args, **kwargs):
        return self.open(*args, method="DELETE", **kwargs)

    def __getattr__(self, name):
        return getattr(self._client, name)


def authed_test_client(app):
    return AuthedTestClient(app.test_client())


BOOK = {
    "book_id": "book-1",
    "book_title": "Love Story",
    "filtered_title": "love-story",
    "book_pic": "https://example.com/cover.jpg",
    "description": "A secret marriage turns dangerous.",
    "chapter_count": 12,
}


class FakeReelShortClient:
    def __init__(self):
        self.calls = []

    def search(self, keywords, locale="en"):
        self.calls.append(("search", keywords, locale))
        return [BOOK | {"book_title": f"Search {keywords}", "description": locale}]

    def shelf(self, shelf_name, locale="en"):
        self.calls.append(("shelf", shelf_name, locale))
        return [BOOK | {"book_title": shelf_name, "description": locale}]

    def episodes(self, book_id, filtered_title, locale="en"):
        self.calls.append(("episodes", book_id, filtered_title, locale))
        return {
            "book": BOOK,
            "episodes": [
                {"episode": 1, "chapter_id": "chapter-1", "title": "Opening Trap", "description": "A deal goes wrong."},
                {"episode": 2, "chapter_id": "chapter-2", "title": "Second Move", "description": "The secret spreads."},
            ],
        }

    def video(self, book_id, episode_num, filtered_title, chapter_id, locale="en"):
        self.calls.append(("video", book_id, episode_num, filtered_title, chapter_id, locale))
        return {
            "video_url": "https://cdn.example.com/video.m3u8",
            "episode": episode_num,
            "duration": 120,
            "next_episode": {
                "episode": episode_num + 1,
                "chapter_id": "chapter-2",
                "title": "Second Move",
                "description": "The secret spreads.",
            },
        }


class FailingReelShortClient(FakeReelShortClient):
    def search(self, keywords, locale="en"):
        raise UpstreamError(502, "upstream failed")


@pytest.fixture
def client():
    return authed_test_client(create_app(client=FakeReelShortClient()))


def test_health_returns_up(client):
    response = client.get("/health")

    assert response.status_code == 200
    assert response.get_json() == {
        "service": "reelshort-content-provider",
        "status": "UP",
    }


def test_diagnostics_returns_empty_snapshot_for_fake_client(client):
    response = client.get("/diagnostics")

    assert response.status_code == 200
    assert response.get_json() == {
        "service": "reelshort-content-provider",
        "status": "UP",
        "diagnostics": {
            "total_events": 0,
            "counters": {},
            "recent_events": [],
        },
    }


def test_search_returns_spring_boot_contract(client):
    response = client.get("/api/v1/reelshort/search?keywords=love")

    assert response.status_code == 200
    assert response.get_json() == {
        "results": [BOOK | {"book_title": "Search love", "description": "en"}],
    }


def test_search_rejects_unsupported_locale(client):
    response = client.get("/api/v1/reelshort/search?keywords=love&locale=zh-CN")

    assert response.status_code == 400
    assert response.get_json() == {"error": "unsupported locale"}


def test_search_requires_keywords(client):
    response = client.get("/api/v1/reelshort/search")

    assert response.status_code == 400
    assert response.get_json() == {"error": "keywords is required"}


def test_upstream_error_maps_to_configured_status():
    app = create_app(client=FailingReelShortClient())

    response = authed_test_client(app).get("/api/v1/reelshort/search?keywords=love")

    assert response.status_code == 502
    assert response.get_json() == {"error": "upstream failed"}


@pytest.mark.parametrize(
    ("path", "shelf_name"),
    [
        ("/api/v1/reelshort/recommend", "recommend"),
        ("/api/v1/reelshort/newrelease", "newrelease"),
        ("/api/v1/reelshort/dramadub", "dramadub"),
    ],
)
def test_shelf_endpoints_return_books(client, path, shelf_name):
    response = client.get(path)

    assert response.status_code == 200
    assert response.get_json() == {
        "books": [BOOK | {"book_title": shelf_name, "description": "en"}],
    }


def test_episodes_return_spring_boot_contract(client):
    response = client.get("/api/v1/reelshort/episodes/book-1?filtered_title=love-story")

    assert response.status_code == 200
    assert response.get_json() == {
        "book": BOOK,
        "episodes": [
            {"episode": 1, "chapter_id": "chapter-1", "title": "Opening Trap", "description": "A deal goes wrong."},
            {"episode": 2, "chapter_id": "chapter-2", "title": "Second Move", "description": "The secret spreads."},
        ],
    }


def test_episodes_require_filtered_title(client):
    response = client.get("/api/v1/reelshort/episodes/book-1")

    assert response.status_code == 400
    assert response.get_json() == {"error": "filtered_title is required"}


def test_map_book_preserves_description_from_upstream_alias():
    mapped = ReelShortClient("https://site.example", "id", 3)._map_book(
        {
            "book_id": "book-1",
            "book_title": "Love Story",
            "book_pic": "cover.jpg",
            "book_intro": "A hidden marriage is exposed.",
            "chapter_count": 12,
        }
    )

    assert mapped["description"] == "A hidden marriage is exposed."


def test_map_book_uses_stable_ascii_filtered_title_when_localized_title_has_no_slug():
    mapped = ReelShortClient("https://site.example", "id", 3)._map_book(
        {
            "book_id": "689950ba89597816250bcc7e",
            "book_title": "愛情轟炸",
            "book_pic": "cover.jpg",
            "special_desc": "一段繁體中文簡介。",
            "chapter_count": 12,
        }
    )

    assert mapped["book_title"] == "愛情轟炸"
    assert mapped["filtered_title"] == "book-689950ba89597816250bcc7e"


def test_map_book_preserves_special_desc_from_current_reelshort_payload():
    mapped = ReelShortClient("https://site.example", "id", 3)._map_book(
        {
            "book_id": "book-1",
            "book_title": "Love Story",
            "book_pic": "cover.jpg",
            "special_desc": "A Marine returns home to a family betrayal.",
            "chapter_count": 12,
        }
    )

    assert mapped["description"] == "A Marine returns home to a family betrayal."


def test_map_chapters_preserves_title_and_description_from_upstream_aliases():
    mapped = ReelShortClient("https://site.example", "id", 3)._map_chapters(
        [
            {
                "serial_number": 1,
                "chapter_id": "chapter-1",
                "chapter_title": "Opening Trap",
                "chapter_desc": "A deal goes wrong.",
            }
        ]
    )

    assert mapped == [
        {
            "episode": 1,
            "chapter_id": "chapter-1",
            "title": "Opening Trap",
            "description": "A deal goes wrong.",
        }
    ]


def test_video_returns_spring_boot_contract(client):
    response = client.get(
        "/api/v1/reelshort/video/book-1/1?filtered_title=love-story&chapter_id=chapter-1"
    )

    assert response.status_code == 200
    assert response.get_json() == {
        "video_url": "https://cdn.example.com/video.m3u8",
        "episode": 1,
        "duration": 120,
        "next_episode": {
            "episode": 2,
            "chapter_id": "chapter-2",
            "title": "Second Move",
            "description": "The secret spreads.",
        },
    }


@pytest.mark.parametrize(
    ("query", "message"),
    [
        ("chapter_id=chapter-1", "filtered_title is required"),
        ("filtered_title=love-story", "chapter_id is required"),
    ],
)
def test_video_requires_filtered_title_and_chapter_id(client, query, message):
    response = client.get(f"/api/v1/reelshort/video/book-1/1?{query}")

    assert response.status_code == 400
    assert response.get_json() == {"error": message}


def test_reelshort_client_builds_search_data_url(monkeypatch):
    captured = {}

    class FakeResponse:
        status_code = 200
        ok = True

        def json(self):
            return {"pageProps": {"books": []}}

    def fake_get(url, params, timeout, **kwargs):
        captured["url"] = url
        captured["params"] = params
        captured["timeout"] = timeout
        return FakeResponse()

    monkeypatch.setattr("app.requests.get", fake_get)

    client = ReelShortClient("https://site.example", "id", 3, build_id="build-1")
    client.search("love story")

    assert captured == {
        "url": "https://site.example/_next/data/build-1/en/search.json",
        "params": {"keywords": "love story"},
        "timeout": 3,
    }


def test_reelshort_client_records_empty_search_diagnostic(monkeypatch):
    class FakeResponse:
        status_code = 200
        ok = True

        def json(self):
            return {"pageProps": {"books": []}}

    monkeypatch.setattr("app.requests.get", lambda *args, **kwargs: FakeResponse())

    client = ReelShortClient("https://site.example", "id", 3, build_id="build-1")
    assert client.search("missing") == []

    diagnostics = client.diagnostics.snapshot()
    assert diagnostics["total_events"] == 1
    assert diagnostics["counters"] == {"search_empty": 1}
    assert diagnostics["recent_events"][0]["event_type"] == "search_empty"
    assert diagnostics["recent_events"][0]["context"] == {
        "keywords": "missing",
        "locale": "en",
    }


def test_reelshort_client_maps_search_page_props(monkeypatch):
    class FakeResponse:
        status_code = 200
        ok = True
        headers = {"Content-Type": "application/json"}
        text = ""

        def json(self):
            return {
                "pageProps": {
                    "books": [
                        {
                            "_id": "book-1",
                            "book_title": "Love Story",
                            "book_pic": "https://example.com/cover.jpg",
                            "chapter_count": 12,
                        }
                    ]
                }
            }

    monkeypatch.setattr("app.requests.get", lambda *args, **kwargs: FakeResponse())

    results = ReelShortClient("https://site.example", "id", 3, build_id="build-1").search("love")

    assert results == [
        {
            "book_id": "book-1",
            "book_title": "Love Story",
            "filtered_title": "love-story",
            "book_pic": "https://example.com/cover.jpg",
            "description": "",
            "chapter_count": 12,
        }
    ]


def test_reelshort_client_maps_home_fallback_shelf_when_legacy_data_is_empty(monkeypatch):
    calls = []

    class FakeResponse:
        status_code = 200
        ok = True
        text = ""

        def __init__(self, payload):
            self.payload = payload

        def json(self):
            return self.payload

    def fake_get(url, params=None, timeout=None, **kwargs):
        calls.append(url)
        if url.endswith("/_next/data/build-1/en/recommend.json"):
            return FakeResponse({})
        if url.endswith("/_next/data/build-1/en.json"):
            return FakeResponse(
                {
                    "pageProps": {
                        "fallback": {
                            "/api/ms/hall/webInfo": {
                                "bookShelfList": [
                                    {
                                        "bookshelf_name": "New Release",
                                        "books": [
                                            {
                                                "book_id": "book-2",
                                                "book_title": "New Release Story",
                                                "book_pic": "https://example.com/new.jpg",
                                                "chapter_count": 8,
                                            }
                                        ],
                                    },
                                    {
                                        "bookshelf_name": "TOP",
                                        "books": [
                                            {
                                                "book_id": "book-3",
                                                "book_title": "Top Story",
                                                "book_pic": "https://example.com/top.jpg",
                                                "chapter_count": 20,
                                            }
                                        ],
                                    },
                                ]
                            }
                        }
                    }
                }
            )
        raise AssertionError(f"unexpected URL {url}")

    monkeypatch.setattr("app.requests.get", fake_get)
    monkeypatch.setenv("REELSHORT_CATALOG_MAX_PAGES_PER_KEYWORD", "0")

    client = ReelShortClient("https://site.example", "37", 3)
    client.build_id = "build-1"

    results = client.shelf("recommend")

    assert calls == [
        "https://site.example/_next/data/build-1/en/recommend.json",
        "https://site.example/_next/data/build-1/en.json",
    ]
    assert results == [
        {
            "book_id": "book-2",
            "book_title": "New Release Story",
            "filtered_title": "new-release-story",
            "book_pic": "https://example.com/new.jpg",
            "description": "",
            "chapter_count": 8,
        },
        {
            "book_id": "book-3",
            "book_title": "Top Story",
            "filtered_title": "top-story",
            "book_pic": "https://example.com/top.jpg",
            "description": "",
            "chapter_count": 20,
        },
    ]


def test_reelshort_client_maps_home_fallback_shelf_when_legacy_data_returns_404(monkeypatch):
    calls = []

    class FakeResponse:
        text = ""

        def __init__(self, status_code, payload=None):
            self.status_code = status_code
            self.ok = 200 <= status_code < 300
            self.payload = payload or {}

        def json(self):
            return self.payload

    def fake_get(url, params=None, timeout=None, **kwargs):
        calls.append(url)
        if url.endswith("/_next/data/build-1/en/recommend.json"):
            return FakeResponse(404)
        if url.endswith("/_next/data/fresh-build/en/recommend.json"):
            return FakeResponse(404)
        if url.endswith("/_next/data/fresh-build/en.json"):
            return FakeResponse(
                200,
                {
                    "pageProps": {
                        "fallback": {
                            "/api/ms/hall/webInfo": {
                                "bookShelfList": [
                                    {
                                        "bookshelf_name": "TOP",
                                        "books": [
                                            {
                                                "book_id": "book-4",
                                                "book_title": "Fallback Story",
                                                "book_pic": "https://example.com/fallback.jpg",
                                                "chapter_count": 16,
                                            }
                                        ],
                                    }
                                ]
                            }
                        }
                    }
                },
            )
        raise AssertionError(f"unexpected URL {url}")

    def fake_discover(self):
        self.build_id = "fresh-build"
        return self.build_id

    monkeypatch.setattr("app.requests.get", fake_get)
    monkeypatch.setattr(ReelShortClient, "_discover_build_id", fake_discover)
    monkeypatch.setenv("REELSHORT_CATALOG_MAX_PAGES_PER_KEYWORD", "0")

    client = ReelShortClient("https://site.example", "37", 3)
    client.build_id = "build-1"

    results = client.shelf("recommend")

    assert calls == [
        "https://site.example/_next/data/build-1/en/recommend.json",
        "https://site.example/_next/data/fresh-build/en/recommend.json",
        "https://site.example/_next/data/fresh-build/en.json",
    ]
    assert results == [
        {
            "book_id": "book-4",
            "book_title": "Fallback Story",
            "filtered_title": "fallback-story",
            "book_pic": "https://example.com/fallback.jpg",
            "description": "",
            "chapter_count": 16,
        }
    ]


def test_reelshort_client_expands_recommend_with_search_catalog(monkeypatch):
    calls = []

    class FakeResponse:
        status_code = 200
        ok = True
        text = ""

        def __init__(self, payload):
            self.payload = payload

        def json(self):
            return self.payload

    def fake_get(url, params=None, timeout=None, **kwargs):
        calls.append({"url": url, "params": params})
        if url.endswith("/_next/data/build-1/en/recommend.json"):
            return FakeResponse({})
        if url.endswith("/_next/data/build-1/en.json"):
            return FakeResponse(
                {
                    "pageProps": {
                        "fallback": {
                            "/api/ms/hall/webInfo": {
                                "bookShelfList": [
                                    {
                                        "bookshelf_name": "TOP",
                                        "books": [
                                            {
                                                "book_id": "home-1",
                                                "book_title": "Home Story",
                                                "book_pic": "https://example.com/home.jpg",
                                                "chapter_count": 12,
                                            }
                                        ],
                                    }
                                ]
                            }
                        }
                    }
                }
            )
        if url.endswith("/_next/data/build-1/en/search.json"):
            keyword = params["keywords"]
            return FakeResponse(
                {
                    "pageProps": {
                        "books": [
                            {
                                "book_id": f"{keyword}-1",
                                "book_title": f"{keyword.title()} Story",
                                "book_pic": f"https://example.com/{keyword}.jpg",
                                "chapter_count": 20,
                            }
                        ]
                    }
                }
            )
        raise AssertionError(f"unexpected URL {url}")

    monkeypatch.setattr("app.requests.get", fake_get)
    monkeypatch.setenv("REELSHORT_CATALOG_SEARCH_KEYWORDS", "love, revenge")
    monkeypatch.setenv("REELSHORT_CATALOG_MAX_PAGES_PER_KEYWORD", "1")
    monkeypatch.setenv("REELSHORT_CATALOG_MAX_BOOKS", "10")

    client = ReelShortClient("https://site.example", "37", 3)
    client.build_id = "build-1"

    results = client.shelf("recommend")

    assert [book["book_id"] for book in results] == ["home-1", "love-1", "revenge-1"]
    assert calls == [
        {"url": "https://site.example/_next/data/build-1/en/recommend.json", "params": None},
        {"url": "https://site.example/_next/data/build-1/en.json", "params": None},
        {
            "url": "https://site.example/_next/data/build-1/en/search.json",
            "params": {"keywords": "love"},
        },
        {
            "url": "https://site.example/_next/data/build-1/en/search.json",
            "params": {"keywords": "revenge"},
        },
    ]


def test_reelshort_client_recommend_catalog_keeps_home_order_and_deduplicates(monkeypatch):
    class FakeResponse:
        status_code = 200
        ok = True
        text = ""

        def __init__(self, payload):
            self.payload = payload

        def json(self):
            return self.payload

    def fake_get(url, params=None, timeout=None, **kwargs):
        if url.endswith("/_next/data/build-1/en/recommend.json"):
            return FakeResponse({})
        if url.endswith("/_next/data/build-1/en.json"):
            return FakeResponse(
                {
                    "pageProps": {
                        "fallback": {
                            "/api/ms/hall/webInfo": {
                                "bookShelfList": [
                                    {
                                        "books": [
                                            {"book_id": "home-1", "book_title": "Home One"},
                                            {"book_id": "shared-1", "book_title": "Home Shared"},
                                        ]
                                    }
                                ]
                            }
                        }
                    }
                }
            )
        if url.endswith("/_next/data/build-1/en/search.json"):
            return FakeResponse(
                {
                    "pageProps": {
                        "books": [
                            {"book_id": "shared-1", "book_title": "Search Shared"},
                            {"book_id": "search-1", "book_title": "Search One"},
                        ]
                    }
                }
            )
        raise AssertionError(f"unexpected URL {url}")

    monkeypatch.setattr("app.requests.get", fake_get)
    monkeypatch.setenv("REELSHORT_CATALOG_SEARCH_KEYWORDS", "love")
    monkeypatch.setenv("REELSHORT_CATALOG_MAX_PAGES_PER_KEYWORD", "1")
    monkeypatch.setenv("REELSHORT_CATALOG_MAX_BOOKS", "10")

    client = ReelShortClient("https://site.example", "37", 3)
    client.build_id = "build-1"

    results = client.shelf("recommend")

    assert [book["book_id"] for book in results] == ["home-1", "shared-1", "search-1"]
    assert results[1]["book_title"] == "Home Shared"


def test_reelshort_client_recommend_catalog_skips_failed_search_keyword(monkeypatch):
    class FakeResponse:
        ok = True
        text = ""

        def __init__(self, status_code, payload=None):
            self.status_code = status_code
            self.ok = 200 <= status_code < 300
            self.payload = payload or {}

        def json(self):
            return self.payload

    def fake_get(url, params=None, timeout=None, **kwargs):
        if url.endswith("/_next/data/build-1/en/recommend.json"):
            return FakeResponse(200, {})
        if url.endswith("/_next/data/build-1/en.json"):
            return FakeResponse(
                200,
                {
                    "pageProps": {
                        "fallback": {
                            "/api/ms/hall/webInfo": {
                                "bookShelfList": [{"books": [{"book_id": "home-1", "book_title": "Home One"}]}]
                            }
                        }
                    }
                },
            )
        if url.endswith("/_next/data/build-1/en/search.json") and params["keywords"] == "broken":
            return FakeResponse(502)
        if url.endswith("/_next/data/build-1/en/search.json"):
            return FakeResponse(200, {"pageProps": {"books": [{"book_id": "love-1", "book_title": "Love One"}]}})
        raise AssertionError(f"unexpected URL {url}")

    monkeypatch.setattr("app.requests.get", fake_get)
    monkeypatch.setenv("REELSHORT_CATALOG_SEARCH_KEYWORDS", "broken,love")
    monkeypatch.setenv("REELSHORT_CATALOG_MAX_PAGES_PER_KEYWORD", "1")
    monkeypatch.setenv("REELSHORT_CATALOG_MAX_BOOKS", "10")

    client = ReelShortClient("https://site.example", "37", 3)
    client.build_id = "build-1"

    results = client.shelf("recommend")

    assert [book["book_id"] for book in results] == ["home-1", "love-1"]


def test_reelshort_client_recommend_catalog_respects_max_books(monkeypatch):
    class FakeResponse:
        status_code = 200
        ok = True
        text = ""

        def __init__(self, payload):
            self.payload = payload

        def json(self):
            return self.payload

    def fake_get(url, params=None, timeout=None, **kwargs):
        if url.endswith("/_next/data/build-1/en/recommend.json"):
            return FakeResponse({})
        if url.endswith("/_next/data/build-1/en.json"):
            return FakeResponse(
                {
                    "pageProps": {
                        "fallback": {
                            "/api/ms/hall/webInfo": {
                                "bookShelfList": [
                                    {
                                        "books": [
                                            {"book_id": "home-1", "book_title": "Home One"},
                                            {"book_id": "home-2", "book_title": "Home Two"},
                                        ]
                                    }
                                ]
                            }
                        }
                    }
                }
            )
        if url.endswith("/_next/data/build-1/en/search.json"):
            return FakeResponse(
                {
                    "pageProps": {
                        "books": [
                            {"book_id": "search-1", "book_title": "Search One"},
                            {"book_id": "search-2", "book_title": "Search Two"},
                        ]
                    }
                }
            )
        raise AssertionError(f"unexpected URL {url}")

    monkeypatch.setattr("app.requests.get", fake_get)
    monkeypatch.setenv("REELSHORT_CATALOG_SEARCH_KEYWORDS", "love")
    monkeypatch.setenv("REELSHORT_CATALOG_MAX_PAGES_PER_KEYWORD", "1")
    monkeypatch.setenv("REELSHORT_CATALOG_MAX_BOOKS", "3")

    client = ReelShortClient("https://site.example", "37", 3)
    client.build_id = "build-1"

    results = client.shelf("recommend")

    assert [book["book_id"] for book in results] == ["home-1", "home-2", "search-1"]


def test_reelshort_client_recommend_catalog_reads_search_pages_until_empty(monkeypatch):
    calls = []

    class FakeResponse:
        status_code = 200
        ok = True
        text = ""

        def __init__(self, payload):
            self.payload = payload

        def json(self):
            return self.payload

    def fake_get(url, params=None, timeout=None, **kwargs):
        calls.append({"url": url, "params": params})
        if url.endswith("/_next/data/build-1/en/recommend.json"):
            return FakeResponse({})
        if url.endswith("/_next/data/build-1/en.json"):
            return FakeResponse({"pageProps": {"fallback": {"/api/ms/hall/webInfo": {"bookShelfList": []}}}})
        if url.endswith("/_next/data/build-1/en/search.json"):
            page = params.get("page", 1)
            return FakeResponse(
                {
                    "pageProps": {
                        "books": [
                            {"book_id": f"love-{page}", "book_title": f"Love Page {page}"}
                        ] if page < 3 else []
                    }
                }
            )
        raise AssertionError(f"unexpected URL {url}")

    monkeypatch.setattr("app.requests.get", fake_get)
    monkeypatch.setenv("REELSHORT_CATALOG_SEARCH_KEYWORDS", "love")
    monkeypatch.setenv("REELSHORT_CATALOG_MAX_PAGES_PER_KEYWORD", "3")
    monkeypatch.setenv("REELSHORT_CATALOG_MAX_BOOKS", "10")

    client = ReelShortClient("https://site.example", "37", 3)
    client.build_id = "build-1"

    results = client.shelf("recommend")

    assert [book["book_id"] for book in results] == ["love-1", "love-2"]
    assert [call["params"] for call in calls if call["url"].endswith("/search.json")] == [
        {"keywords": "love"},
        {"keywords": "love", "page": 2},
        {"keywords": "love", "page": 3},
    ]


def test_reelshort_client_recommend_catalog_does_not_fetch_after_empty_page(monkeypatch):
    calls = []

    class FakeResponse:
        status_code = 200
        ok = True
        text = ""

        def __init__(self, payload):
            self.payload = payload

        def json(self):
            return self.payload

    def fake_get(url, params=None, timeout=None, **kwargs):
        calls.append({"url": url, "params": params})
        if url.endswith("/_next/data/build-1/en/recommend.json"):
            return FakeResponse({})
        if url.endswith("/_next/data/build-1/en.json"):
            return FakeResponse({"pageProps": {"fallback": {"/api/ms/hall/webInfo": {"bookShelfList": []}}}})
        if url.endswith("/_next/data/build-1/en/search.json"):
            page = params.get("page", 1)
            if page > 2:
                raise AssertionError(f"unexpected page after empty result: {page}")
            return FakeResponse({"pageProps": {"books": [{"book_id": "love-1", "book_title": "Love"}] if page == 1 else []}})
        raise AssertionError(f"unexpected URL {url}")

    monkeypatch.setattr("app.requests.get", fake_get)
    monkeypatch.setenv("REELSHORT_CATALOG_SEARCH_KEYWORDS", "love")
    monkeypatch.setenv("REELSHORT_CATALOG_MAX_PAGES_PER_KEYWORD", "5")
    monkeypatch.setenv("REELSHORT_CATALOG_REQUEST_WORKERS", "8")

    client = ReelShortClient("https://site.example", "37", 3)
    client.build_id = "build-1"

    results = client.shelf("recommend")

    assert [book["book_id"] for book in results] == ["love-1"]
    assert [call["params"] for call in calls if call["url"].endswith("/search.json")] == [
        {"keywords": "love"},
        {"keywords": "love", "page": 2},
    ]


def test_reelshort_client_recommend_catalog_clamps_search_limits(monkeypatch):
    calls = []

    class FakeResponse:
        status_code = 200
        ok = True
        text = ""

        def __init__(self, payload):
            self.payload = payload

        def json(self):
            return self.payload

    def fake_get(url, params=None, timeout=None, **kwargs):
        calls.append({"url": url, "params": params})
        if url.endswith("/_next/data/build-1/en/recommend.json"):
            return FakeResponse({})
        if url.endswith("/_next/data/build-1/en.json"):
            return FakeResponse({"pageProps": {"fallback": {"/api/ms/hall/webInfo": {"bookShelfList": []}}}})
        if url.endswith("/_next/data/build-1/en/search.json"):
            keyword = params["keywords"]
            page = params.get("page", 1)
            return FakeResponse({"pageProps": {"books": [{"book_id": f"{keyword}-{page}", "book_title": keyword}]}})
        raise AssertionError(f"unexpected URL {url}")

    monkeypatch.setattr("app.requests.get", fake_get)
    monkeypatch.setenv("REELSHORT_CATALOG_SEARCH_KEYWORDS", ",".join(f"kw{i}" for i in range(60)))
    monkeypatch.setenv("REELSHORT_CATALOG_MAX_PAGES_PER_KEYWORD", "99")
    monkeypatch.setenv("REELSHORT_CATALOG_MAX_BOOKS", "500")
    monkeypatch.setenv("REELSHORT_CATALOG_REQUEST_WORKERS", "999")

    client = ReelShortClient("https://site.example", "37", 3)
    client.build_id = "build-1"

    results = client.shelf("recommend")

    search_calls = [call for call in calls if call["url"].endswith("/search.json")]
    assert len(search_calls) == 200
    assert len(results) == 200
    assert {call["params"]["keywords"] for call in search_calls} == {f"kw{i}" for i in range(40)}
    assert max(call["params"].get("page", 1) for call in search_calls) == 5


def test_reelshort_client_recommend_catalog_uses_deterministic_keyword_plan(monkeypatch):
    calls = []

    class FakeResponse:
        status_code = 200
        ok = True
        text = ""

        def __init__(self, payload):
            self.payload = payload

        def json(self):
            return self.payload

    def fake_get(url, params=None, timeout=None, **kwargs):
        calls.append({"url": url, "params": params})
        if url.endswith("/_next/data/build-1/en/recommend.json"):
            return FakeResponse({})
        if url.endswith("/_next/data/build-1/en.json"):
            return FakeResponse({"pageProps": {"fallback": {"/api/ms/hall/webInfo": {"bookShelfList": []}}}})
        if url.endswith("/_next/data/build-1/en/search.json"):
            keyword = params["keywords"]
            page = params.get("page", 1)
            books = [] if keyword == "kw0" else [{"book_id": f"{keyword}-{page}", "book_title": keyword}]
            return FakeResponse({"pageProps": {"books": books}})
        raise AssertionError(f"unexpected URL {url}")

    monkeypatch.setattr("app.requests.get", fake_get)
    monkeypatch.setenv("REELSHORT_CATALOG_SEARCH_KEYWORDS", ",".join(f"kw{i}" for i in range(41)))
    monkeypatch.setenv("REELSHORT_CATALOG_MAX_PAGES_PER_KEYWORD", "5")
    monkeypatch.setenv("REELSHORT_CATALOG_MAX_BOOKS", "500")
    monkeypatch.setenv("REELSHORT_CATALOG_REQUEST_WORKERS", "8")

    client = ReelShortClient("https://site.example", "37", 3)
    client.build_id = "build-1"

    results = client.shelf("recommend")

    search_calls = [call for call in calls if call["url"].endswith("/search.json")]
    assert "kw40-1" not in [book["book_id"] for book in results]
    assert {call["params"]["keywords"] for call in search_calls} == {f"kw{i}" for i in range(40)}
    assert [call["params"] for call in search_calls if call["params"]["keywords"] == "kw0"] == [
        {"keywords": "kw0"}
    ]


def test_reelshort_client_recommend_catalog_parallel_fetch_keeps_configured_order(monkeypatch):
    class FakeResponse:
        status_code = 200
        ok = True
        text = ""

        def __init__(self, payload):
            self.payload = payload

        def json(self):
            return self.payload

    def fake_get(url, params=None, timeout=None, **kwargs):
        if url.endswith("/_next/data/build-1/en/recommend.json"):
            return FakeResponse({})
        if url.endswith("/_next/data/build-1/en.json"):
            return FakeResponse({"pageProps": {"fallback": {"/api/ms/hall/webInfo": {"bookShelfList": []}}}})
        if url.endswith("/_next/data/build-1/en/search.json"):
            keyword = params["keywords"]
            if keyword == "love":
                time.sleep(0.05)
            return FakeResponse({"pageProps": {"books": [{"book_id": f"{keyword}-1", "book_title": keyword}]}})
        raise AssertionError(f"unexpected URL {url}")

    monkeypatch.setattr("app.requests.get", fake_get)
    monkeypatch.setenv("REELSHORT_CATALOG_SEARCH_KEYWORDS", "love,revenge")
    monkeypatch.setenv("REELSHORT_CATALOG_MAX_PAGES_PER_KEYWORD", "1")
    monkeypatch.setenv("REELSHORT_CATALOG_REQUEST_WORKERS", "2")

    client = ReelShortClient("https://site.example", "37", 3)
    client.build_id = "build-1"

    results = client.shelf("recommend")

    assert [book["book_id"] for book in results] == ["love-1", "revenge-1"]


def test_reelshort_client_maps_episode_page_props(monkeypatch):
    captured = {}

    class FakeResponse:
        status_code = 200
        ok = True

        def json(self):
            return {
                "pageProps": {
                    "data": {
                        "online_base": [
                            {
                                "serial_number": 1,
                                "chapter_id": "chapter-1",
                                "chapter_title": "Opening Trap",
                                "chapter_desc": "A deal goes wrong.",
                            },
                            {
                                "serial_number": 2,
                                "chapter_id": "chapter-2",
                                "chapter_title": "Second Move",
                                "chapter_desc": "The secret spreads.",
                            },
                        ]
                    }
                }
            }

    def fake_get(url, params, timeout, **kwargs):
        captured["url"] = url
        captured["params"] = params
        return FakeResponse()

    monkeypatch.setattr("app.requests.get", fake_get)

    episodes = ReelShortClient("https://site.example", "id", 3, build_id="build-1").episodes(
        "book/1",
        "love-story",
    )

    assert captured == {
        "url": "https://site.example/_next/data/build-1/en/movie/love-story-book%2F1.json",
        "params": {"slug": "love-story-book/1"},
    }
    assert episodes == {
        "book": {
            "book_id": "book/1",
            "book_title": "",
            "filtered_title": "love-story",
            "book_pic": "",
            "description": "",
            "chapter_count": 2,
        },
        "episodes": [
            {"episode": 1, "chapter_id": "chapter-1", "title": "Opening Trap", "description": "A deal goes wrong."},
            {"episode": 2, "chapter_id": "chapter-2", "title": "Second Move", "description": "The secret spreads."},
        ],
    }


def test_reelshort_client_uses_book_info_when_movie_data_returns_404(monkeypatch):
    calls = []

    class FakeResponse:
        text = ""

        def __init__(self, status_code, payload=None):
            self.status_code = status_code
            self.ok = 200 <= status_code < 300
            self.payload = payload or {}

        def json(self):
            return self.payload

    def fake_get(url, params=None, timeout=None, **kwargs):
        calls.append({"url": url, "params": params})
        if url.endswith("/_next/data/build-1/en/movie/fiancee-s-betrayal-6a2b.json"):
            return FakeResponse(404)
        if url.endswith("/api/video/book/getBookInfo"):
            return FakeResponse(
                200,
                {
                    "code": 0,
                    "msg": "success",
                    "data": {
                        "online_base": [
                            {"serial_number": 0, "chapter_id": "trailer", "chapter_type": 2},
                            {
                                "serial_number": 1,
                                "chapter_id": "chapter-1",
                                "chapter_type": 1,
                                "chapter_title": "Opening Trap",
                                "chapter_desc": "A deal goes wrong.",
                            },
                            {
                                "serial_number": 2,
                                "chapter_id": "chapter-2",
                                "chapter_type": 1,
                                "chapter_title": "Second Move",
                                "chapter_desc": "The secret spreads.",
                            },
                        ]
                    },
                },
            )
        raise AssertionError(f"unexpected URL {url}")

    monkeypatch.setattr("app.requests.get", fake_get)

    episodes = ReelShortClient("https://site.example", "37", 3, build_id="build-1").episodes(
        "6a2b",
        "fiancee-s-betrayal",
    )

    assert calls == [
        {
            "url": "https://site.example/_next/data/build-1/en/movie/fiancee-s-betrayal-6a2b.json",
            "params": {"slug": "fiancee-s-betrayal-6a2b"},
        },
        {
            "url": "https://site.example/api/video/book/getBookInfo",
            "params": {"book_id": "6a2b"},
        },
    ]
    assert episodes == {
        "book": {
            "book_id": "6a2b",
            "book_title": "",
            "filtered_title": "fiancee-s-betrayal",
            "book_pic": "",
            "description": "",
            "chapter_count": 3,
        },
        "episodes": [
            {"episode": 1, "chapter_id": "chapter-1", "title": "Opening Trap", "description": "A deal goes wrong."},
            {"episode": 2, "chapter_id": "chapter-2", "title": "Second Move", "description": "The secret spreads."},
        ],
    }


def test_reelshort_client_uses_book_description_when_episode_description_is_missing(monkeypatch):
    class FakeResponse:
        def __init__(self, status_code, payload=None):
            self.status_code = status_code
            self.ok = 200 <= status_code < 300
            self.payload = payload or {}

        def json(self):
            return self.payload

    def fake_get(url, params=None, timeout=None, **kwargs):
        if url.endswith("/_next/data/build-1/en/movie/love-story-book-1.json"):
            return FakeResponse(
                200,
                {
                    "pageProps": {
                        "data": {
                            "special_desc": "A hidden marriage is exposed.",
                            "online_base": [
                                {"serial_number": 1, "chapter_id": "chapter-1", "chapter_type": 1},
                            ],
                        }
                    }
                },
            )
        raise AssertionError(f"unexpected URL {url}")

    monkeypatch.setattr("app.requests.get", fake_get)

    episodes = ReelShortClient("https://site.example", "37", 3, build_id="build-1").episodes(
        "book-1",
        "love-story",
    )

    assert episodes == {
        "book": {
            "book_id": "book-1",
            "book_title": "",
            "filtered_title": "love-story",
            "book_pic": "",
            "description": "A hidden marriage is exposed.",
            "chapter_count": 1,
        },
        "episodes": [
            {
                "episode": 1,
                "chapter_id": "chapter-1",
                "title": "",
                "description": "A hidden marriage is exposed.",
            }
        ],
    }


def test_reelshort_client_fetches_video_episode_page(monkeypatch):
    calls = []

    class FakeResponse:
        status_code = 200
        ok = True

        def __init__(self, payload):
            self.payload = payload

        def json(self):
            return self.payload

    def fake_get(url, params, timeout, **kwargs):
        calls.append({"url": url, "params": params})
        if "/episodes/" in url:
            return FakeResponse(
                {
                    "pageProps": {
                        "data": {
                            "video_url": "https://cdn.example.com/1.m3u8",
                            "serial_number": 1,
                            "duration": 90,
                        }
                    }
                }
            )
        return FakeResponse(
            {
                "pageProps": {
                    "data": {
                        "online_base": [
                            {"serial_number": 1, "chapter_id": "chapter-1"},
                            {
                                "serial_number": 2,
                                "chapter_id": "chapter-2",
                                "chapter_title": "Second Move",
                                "chapter_desc": "The secret spreads.",
                            },
                        ]
                    }
                }
            }
        )

    monkeypatch.setattr("app.requests.get", fake_get)

    video = ReelShortClient("https://site.example", "id", 3, build_id="build-1").video(
        "book-1",
        1,
        "love-story",
        "chapter-1",
    )

    assert calls[0] == {
        "url": "https://site.example/_next/data/build-1/en/episodes/episode-1-love-story-book-1-chapter-1.json",
        "params": {"play_time": "1", "slug": "episode-1-love-story-book-1-chapter-1"},
    }
    assert video == {
        "video_url": "https://cdn.example.com/1.m3u8",
        "episode": 1,
        "duration": 90,
        "next_episode": {
            "episode": 2,
            "chapter_id": "chapter-2",
            "title": "Second Move",
            "description": "The secret spreads.",
        },
    }


def test_reelshort_client_falls_back_to_episode_html_when_legacy_video_data_returns_404(monkeypatch):
    calls = []

    class FakeResponse:
        def __init__(self, status_code, payload=None, text=""):
            self.status_code = status_code
            self.ok = 200 <= status_code < 300
            self.payload = payload or {}
            self.text = text

        def json(self):
            return self.payload

    html = """
        <html><body>
        <script id="__NEXT_DATA__" type="application/json">
        {"props":{"pageProps":{"data":{
          "video_url":"https://cdn.example.com/html-1.m3u8",
          "serial_number":1,
          "duration":128
        }}}}
        </script>
        </body></html>
    """

    def fake_get(url, params=None, timeout=None, **kwargs):
        calls.append({"url": url, "params": params})
        if url.endswith("/_next/data/build-1/en/episodes/episode-1-fiancee-s-betrayal-book-1-chapter-1.json"):
            return FakeResponse(404)
        if url.endswith("/episodes/episode-1-fiancee-s-betrayal-book-1-chapter-1"):
            return FakeResponse(200, text=html)
        if url.endswith("/_next/data/build-1/en/movie/fiancee-s-betrayal-book-1.json"):
            return FakeResponse(
                200,
                {
                    "pageProps": {
                        "data": {
                            "online_base": [
                                {"serial_number": 1, "chapter_id": "chapter-1"},
                                {"serial_number": 2, "chapter_id": "chapter-2"},
                            ]
                        }
                    }
                },
            )
        raise AssertionError(f"unexpected URL {url}")

    monkeypatch.setattr("app.requests.get", fake_get)

    video = ReelShortClient("https://site.example", "37", 3, build_id="build-1").video(
        "book-1",
        1,
        "fiancee-s-betrayal",
        "chapter-1",
    )

    assert calls[:2] == [
        {
            "url": "https://site.example/_next/data/build-1/en/episodes/episode-1-fiancee-s-betrayal-book-1-chapter-1.json",
            "params": {"play_time": "1", "slug": "episode-1-fiancee-s-betrayal-book-1-chapter-1"},
        },
        {
            "url": "https://site.example/episodes/episode-1-fiancee-s-betrayal-book-1-chapter-1",
            "params": {"play_time": "1"},
        },
    ]
    assert video == {
        "video_url": "https://cdn.example.com/html-1.m3u8",
        "episode": 1,
        "duration": 128,
        "next_episode": {"episode": 2, "chapter_id": "chapter-2", "title": "", "description": ""},
    }


def test_reelshort_client_refreshes_auto_discovered_build_id_after_data_404(monkeypatch):
    calls = []

    class FakeResponse:
        ok = True
        text = '{"buildId":"fresh-build"}'

        def __init__(self, status_code, payload=None):
            self.status_code = status_code
            self.payload = payload or {}
            self.ok = 200 <= status_code < 300

        def json(self):
            return self.payload

    def fake_get(url, params=None, timeout=None, **kwargs):
        calls.append({"url": url, "params": params})
        if "stale-build" in url:
            return FakeResponse(404)
        if url == "https://site.example/id":
            return FakeResponse(200)
        return FakeResponse(200, {"pageProps": {"books": [BOOK]}})

    monkeypatch.setattr("app.requests.get", fake_get)

    client = ReelShortClient("https://site.example", "id", 3)
    client.build_id = "stale-build"

    results = client.search("love")

    assert results == [BOOK]
    assert client.build_id == "fresh-build"
    diagnostics = client.diagnostics.snapshot()
    assert diagnostics["counters"] == {"next_data_404": 1}
    assert diagnostics["recent_events"][0]["context"]["data_path"] == "/search.json"
    assert calls == [
        {
            "url": "https://site.example/_next/data/stale-build/en/search.json",
            "params": {"keywords": "love"},
        },
        {"url": "https://site.example/id", "params": None},
        {
            "url": "https://site.example/_next/data/fresh-build/en/search.json",
            "params": {"keywords": "love"},
        },
    ]


def test_reelshort_client_does_not_refresh_explicit_build_id_after_data_404(monkeypatch):
    calls = []

    class FakeResponse:
        status_code = 404
        ok = False

        def json(self):
            return {}

    def fake_get(url, params=None, timeout=None, **kwargs):
        calls.append(url)
        return FakeResponse()

    monkeypatch.setattr("app.requests.get", fake_get)

    client = ReelShortClient("https://site.example", "id", 3, build_id="fixed-build")

    with pytest.raises(UpstreamError) as error:
        client.search("love")

    assert error.value.status_code == 404
    assert calls == ["https://site.example/_next/data/fixed-build/en/search.json"]


def test_reelshort_client_retries_auto_build_id_404_only_once(monkeypatch):
    calls = []

    class FakeResponse:
        text = '{"buildId":"fresh-build"}'

        def __init__(self, status_code):
            self.status_code = status_code
            self.ok = 200 <= status_code < 300

        def json(self):
            return {}

    def fake_get(url, params=None, timeout=None, **kwargs):
        calls.append({"url": url, "params": params})
        if url == "https://site.example/id":
            return FakeResponse(200)
        return FakeResponse(404)

    monkeypatch.setattr("app.requests.get", fake_get)

    client = ReelShortClient("https://site.example", "id", 3)
    client.build_id = "stale-build"

    with pytest.raises(UpstreamError) as error:
        client.search("missing")

    assert error.value.status_code == 404
    assert calls == [
        {
            "url": "https://site.example/_next/data/stale-build/en/search.json",
            "params": {"keywords": "missing"},
        },
        {"url": "https://site.example/id", "params": None},
        {
            "url": "https://site.example/_next/data/fresh-build/en/search.json",
            "params": {"keywords": "missing"},
        },
    ]


def test_reelshort_client_discovers_build_id_from_site_id_page(monkeypatch):
    captured = {}

    class FakeResponse:
        status_code = 200
        ok = True
        text = '{"buildId":"build-xyz"}'

    def fake_get(url, timeout, **kwargs):
        captured["url"] = url
        captured["timeout"] = timeout
        return FakeResponse()

    monkeypatch.setattr("app.requests.get", fake_get)

    build_id = ReelShortClient("https://site.example", "id", 7)._discover_build_id()

    assert build_id == "build-xyz"
    assert captured == {
        "url": "https://site.example/id",
        "timeout": 7,
    }


def test_content_provider_runtime_host_and_port_are_environment_driven(monkeypatch):
    captured = {}

    class FakeApp:
        def run(self, host, port):
            captured["host"] = host
            captured["port"] = port

    monkeypatch.setattr(app_module, "create_app", lambda: FakeApp())
    monkeypatch.setenv("CONTENT_PROVIDER_HOST", "0.0.0.0")
    monkeypatch.setenv("CONTENT_PROVIDER_PORT", "5050")

    app_module.run_app()

    assert captured == {
        "host": "0.0.0.0",
        "port": 5050,
    }


class StreamingResponse:
    def __init__(self, status_code=200, body=b"{}", headers=None):
        self.status_code = status_code
        self.ok = 200 <= status_code < 300
        self.headers = headers or {}
        self._body = body
        self._content = False

    def iter_content(self, chunk_size):
        yield from (self._body[index : index + chunk_size] for index in range(0, len(self._body), chunk_size))

    @property
    def text(self):
        content = self._content if isinstance(self._content, bytes) else self._body
        return content.decode()

    def json(self):
        return json.loads(self.text)

    def close(self):
        pass


def test_reelshort_client_follows_public_redirect_without_requests_auto_redirect(monkeypatch):
    calls = []
    responses = [
        StreamingResponse(302, headers={"Location": "https://cdn.example/data.json"}),
        StreamingResponse(body=b'{"ok": true}'),
    ]

    def fake_get(url, **kwargs):
        calls.append((url, kwargs))
        return responses.pop(0)

    monkeypatch.setattr("app.requests.get", fake_get)
    client = ReelShortClient(
        "https://site.example", "id", 3, build_id="build", resolver=lambda host: ["93.184.216.34"]
    )

    assert client._get("/data.json") == {"ok": True}
    assert [call[0] for call in calls] == ["https://site.example/data.json", "https://cdn.example/data.json"]
    assert all(call[1]["allow_redirects"] is False and call[1]["stream"] is True for call in calls)


def test_reelshort_client_keeps_original_host_for_relative_redirect(monkeypatch):
    calls = []
    responses = [
        StreamingResponse(302, headers={"Location": "/next.json"}),
        StreamingResponse(body=b'{"ok": true}'),
    ]

    def fake_get(url, **kwargs):
        calls.append((url, kwargs["headers"]["Host"]))
        return responses.pop(0)

    monkeypatch.setattr("app.requests.get", fake_get)
    client = ReelShortClient(
        "https://site.example", "id", 3, build_id="build", resolver=lambda host: ["93.184.216.34"]
    )

    assert client._get("/data.json") == {"ok": True}
    assert calls == [
        ("https://site.example/data.json", "site.example"),
        ("https://site.example/next.json", "site.example"),
    ]


def test_reelshort_client_preserves_explicit_port_in_host_header(monkeypatch):
    captured = {}

    def fake_get(url, **kwargs):
        captured["host"] = kwargs["headers"]["Host"]
        return StreamingResponse(body=b'{"ok": true}')

    monkeypatch.setattr("app.requests.get", fake_get)
    # P3-21：只允许 80/443，因此这里用默认端口验证 Host header 仍保留原始域名与端口。
    client = ReelShortClient(
        "http://site.example:80", "id", 3, build_id="build", resolver=lambda host: ["93.184.216.34"]
    )

    assert client._get("/data.json") == {"ok": True}
    assert captured["host"] == "site.example:80"


def test_reelshort_client_brackets_pinned_ipv6_url():
    client = ReelShortClient(
        "https://site.example", "id", 3, resolver=lambda host: ["2001:4860:4860::8888"]
    )

    pinned, original_host = client._validate_url("https://site.example:443/data.json", "blocked", "blocked")

    assert pinned == "https://[2001:4860:4860::8888]:443/data.json"
    # 443 是 https 默认端口，Host header 保持裸域名（与既有行为一致）。
    assert original_host == "site.example"


def test_reelshort_client_rejects_non_whitelisted_upstream_port():
    client = ReelShortClient(
        "https://site.example", "id", 3, resolver=lambda host: ["93.184.216.34"]
    )

    with pytest.raises(UpstreamError, match="unsafe upstream URL"):
        client._validate_url("https://site.example:8443/data.json", "blocked", "unsafe upstream URL")

    assert client.diagnostics.snapshot()["counters"] == {"upstream_port_blocked": 1}


def test_pinned_https_transport_uses_original_hostname_for_tls(monkeypatch):
    monkeypatch.undo()
    captured = {}

    class FakeResponse:
        pass

    class FakeSession:
        def mount(self, prefix, adapter):
            captured["prefix"] = prefix
            captured["adapter"] = adapter

        def get(self, url, **kwargs):
            captured["url"] = url
            captured["headers"] = kwargs["headers"]
            return FakeResponse()

        def close(self):
            captured["closed"] = True

    monkeypatch.setattr(app_module.requests, "Session", FakeSession)
    client = ReelShortClient(
        "https://site.example", "id", 3, resolver=lambda host: ["93.184.216.34"]
    )

    response = client._request_get(
        "https://93.184.216.34/data.json",
        original_url="https://site.example/data.json",
        headers={"Host": "site.example"},
    )

    assert captured["prefix"] == "https://"
    assert captured["adapter"].original_hostname == "site.example"
    assert captured["url"] == "https://93.184.216.34/data.json"
    assert captured["headers"]["Host"] == "site.example"
    assert response._reelshort_session is not None


def test_pinned_https_adapter_configures_requests_232_connection_pool():
    adapter = app_module._PinnedHTTPSAdapter("site.example")
    prepared = app_module.requests.Request("GET", "https://93.184.216.34/data.json").prepare()

    pool = adapter.get_connection_with_tls_context(prepared, True, proxies={})

    assert pool.assert_hostname == "site.example"
    assert pool.conn_kw["server_hostname"] == "site.example"
    adapter.close()


def test_reelshort_client_rejects_redirect_to_private_network(monkeypatch):
    monkeypatch.setattr(
        "app.requests.get",
        lambda *args, **kwargs: StreamingResponse(302, headers={"Location": "http://127.0.0.1/admin"}),
    )
    client = ReelShortClient(
        "https://site.example",
        "id",
        3,
        build_id="build",
        resolver=lambda host: ["127.0.0.1"] if host == "127.0.0.1" else ["93.184.216.34"],
    )

    with pytest.raises(UpstreamError, match="unsafe redirect"):
        client._get("/data.json")

    assert client.diagnostics.snapshot()["counters"] == {"upstream_redirect_blocked": 1}


def test_reelshort_client_rejects_private_initial_site_url(monkeypatch):
    requested = False

    def fake_get(*args, **kwargs):
        nonlocal requested
        requested = True
        return StreamingResponse()

    monkeypatch.setattr("app.requests.get", fake_get)
    client = ReelShortClient(
        "http://internal.example", "id", 3, build_id="build", resolver=lambda host: ["10.0.0.8"]
    )

    with pytest.raises(UpstreamError, match="unsafe upstream URL"):
        client._get("/data.json")

    assert requested is False
    assert client.diagnostics.snapshot()["counters"] == {"upstream_url_blocked": 1}


def test_reelshort_client_rejects_response_larger_than_limit(monkeypatch):
    monkeypatch.setattr("app.requests.get", lambda *args, **kwargs: StreamingResponse(body=b"123456789"))
    client = ReelShortClient("https://site.example", "id", 3, build_id="build", max_response_bytes=8)

    with pytest.raises(UpstreamError, match="response too large"):
        client._get_text("/large")

    assert client.diagnostics.snapshot()["counters"] == {"upstream_response_too_large": 1}


def test_reelshort_client_rejects_redirect_loop(monkeypatch):
    monkeypatch.setattr(
        "app.requests.get",
        lambda *args, **kwargs: StreamingResponse(302, headers={"Location": "/loop"}),
    )
    client = ReelShortClient(
        "https://site.example", "id", 3, build_id="build", resolver=lambda host: ["93.184.216.34"]
    )

    with pytest.raises(UpstreamError, match="redirect loop"):
        client._get_text("/loop")


def test_reelshort_client_enforces_redirect_limit(monkeypatch):
    count = 0

    def fake_get(url, **kwargs):
        nonlocal count
        count += 1
        return StreamingResponse(302, headers={"Location": f"/hop-{count}"})

    monkeypatch.setattr("app.requests.get", fake_get)
    client = ReelShortClient(
        "https://site.example",
        "id",
        3,
        build_id="build",
        resolver=lambda host: ["93.184.216.34"],
        max_redirects=2,
    )

    with pytest.raises(UpstreamError, match="too many redirects"):
        client._get_text("/start")

    assert count == 3


def test_reelshort_client_uses_remaining_total_deadline_for_redirects(monkeypatch):
    times = iter([10.0, 11.25, 13.1])
    timeouts = []

    def fake_get(url, **kwargs):
        timeouts.append(kwargs["timeout"])
        return StreamingResponse(302, headers={"Location": f"/next-{len(timeouts)}"})

    monkeypatch.setattr("app.requests.get", fake_get)
    client = ReelShortClient(
        "https://site.example",
        "id",
        3,
        build_id="build",
        resolver=lambda host: ["93.184.216.34"],
        monotonic=lambda: next(times),
    )

    with pytest.raises(UpstreamError, match="deadline exceeded"):
        client._get_text("/start")

    assert timeouts == [3.0, 1.75]


def test_reelshort_client_deadline_closes_blocked_stream(monkeypatch):
    released = threading.Event()

    class BlockingResponse(StreamingResponse):
        def iter_content(self, chunk_size):
            released.wait(1)
            if False:
                yield b""

        def close(self):
            released.set()

    monkeypatch.setattr("app.requests.get", lambda *args, **kwargs: BlockingResponse())
    client = ReelShortClient("https://site.example", "id", 0.05, build_id="build")

    started = time.monotonic()
    with pytest.raises(UpstreamError, match="deadline exceeded"):
        client._get_text("/blocked")

    assert time.monotonic() - started < 0.5
    assert released.is_set()
    assert client.diagnostics.snapshot()["counters"] == {"upstream_deadline_exceeded": 1}


@pytest.mark.parametrize(
    ("kwargs", "field"),
    [
        ({"timeout_seconds": 0}, "timeout_seconds"),
        ({"timeout_seconds": -1}, "timeout_seconds"),
        ({"max_response_bytes": 0}, "max_response_bytes"),
        ({"max_response_bytes": -1}, "max_response_bytes"),
        ({"max_redirects": -1}, "max_redirects"),
    ],
)
def test_reelshort_client_rejects_invalid_security_limits(kwargs, field):
    with pytest.raises(ValueError, match=field):
        ReelShortClient("https://site.example", "id", **({"timeout_seconds": 3, **kwargs}), build_id="build")


@pytest.mark.parametrize("timeout_seconds", [math.nan, math.inf, -math.inf])
def test_reelshort_client_rejects_non_finite_timeout(timeout_seconds):
    with pytest.raises(ValueError, match="timeout_seconds"):
        ReelShortClient("https://site.example", "id", timeout_seconds, build_id="build")


def test_reelshort_client_from_env_rejects_invalid_numeric_limits(monkeypatch):
    monkeypatch.setenv("REELSHORT_REQUEST_TIMEOUT_SECONDS", "not-a-number")
    with pytest.raises(ValueError, match="REELSHORT_REQUEST_TIMEOUT_SECONDS"):
        ReelShortClient.from_env()


def test_empty_build_id_env_still_refreshes_on_404(monkeypatch):
    """An empty REELSHORT_NEXT_BUILD_ID must mean "discover", not "pin empty".

    The deployed env file carries `REELSHORT_NEXT_BUILD_ID=`, which os.getenv
    returns as `""`. Treating that as a pinned id disabled the 404-triggered
    refresh, so every shelf whose upstream JSON had moved failed permanently
    with `upstream not found` instead of falling back to the home shelf.
    """
    monkeypatch.setenv("REELSHORT_NEXT_BUILD_ID", "")
    client = ReelShortClient.from_env()
    assert client.build_id is None
    assert client.fixed_build_id is False

    # The same rule holds for a caller that passes the empty string directly.
    assert ReelShortClient("https://site.example", "id", 3, build_id="").fixed_build_id is False


def test_stale_build_id_refreshes_when_shelf_json_disappears(monkeypatch):
    """A 404 on the shelf JSON must refresh the build id and use the fallback."""
    calls = []

    def fake_get(url, **_):
        calls.append(url)

        class Response:
            status_code = 200
            ok = True
            text = ""

            @staticmethod
            def json():
                return {
                    "pageProps": {
                        "fallback": {
                            "/api/ms/hall/webInfo": {
                                "bookShelfList": [
                                    {
                                        "bookshelf_name": "New Release",
                                        "books": [{"book_id": "book-1", "book_title": "Shelf Story"}],
                                    }
                                ]
                            }
                        }
                    }
                }

        return Response()

    monkeypatch.setattr("app.requests.get", fake_get)
    monkeypatch.setattr(ReelShortClient, "_discover_build_id", lambda self: "fresh")
    monkeypatch.setenv("REELSHORT_CATALOG_MAX_PAGES_PER_KEYWORD", "0")

    # The fixed id must be False for the refresh path to run at all.
    client = ReelShortClient("https://site.example", "37", 3)
    assert client.fixed_build_id is False
    client.build_id = "stale"
    assert client.shelf("recommend")[0]["book_id"] == "book-1"
    assert calls[0].startswith("https://site.example/_next/data/stale/")


# ---------------------------------------------------------------------------
# P2-6 / P2-16 / P3-21：内容源健壮性、出网收紧与内部鉴权
# ---------------------------------------------------------------------------


class ContractStubClient:
    """直接返回上游形态数据的桩，用于验证映射层的容错转换。"""

    def __init__(
        self,
        *,
        video_payload=None,
        episodes_payload=None,
        episodes_error=None,
        book_info_payload=None,
        search_payload=None,
        shelf_payload=None,
    ):
        self.diagnostics = app_module.ProviderDiagnostics()
        self._video_payload = video_payload
        self._episodes_payload = episodes_payload
        self._episodes_error = episodes_error
        self._book_info_payload = book_info_payload
        self._search_payload = search_payload
        self._shelf_payload = shelf_payload
        self.episodes_calls = 0

    def search(self, keywords, locale="en"):
        return self._search_payload if self._search_payload is not None else []

    def shelf(self, shelf_name, locale="en"):
        return self._shelf_payload if self._shelf_payload is not None else []

    def episodes(self, book_id, filtered_title, locale="en"):
        self.episodes_calls += 1
        if self._episodes_error is not None:
            raise self._episodes_error
        return self._episodes_payload if self._episodes_payload is not None else {"book": {}, "episodes": []}

    def video(self, book_id, episode_num, filtered_title, chapter_id, locale="en"):
        return self._video_payload if self._video_payload is not None else {}


def token_gate_app(token=INTERNAL_TOKEN, client=None):
    return create_app(client=client or ContractStubClient(), internal_token=token)


def test_non_health_endpoint_requires_internal_token(monkeypatch):
    app = token_gate_app()

    response = app.test_client().get("/diagnostics")

    assert response.status_code == 401
    assert response.get_json() == {"error": "missing internal token"}


def test_non_health_endpoint_rejects_invalid_internal_token():
    app = token_gate_app()

    response = app.test_client().get("/diagnostics", headers={INTERNAL_TOKEN_HEADER: "wrong-token"})

    assert response.status_code == 403
    assert response.get_json() == {"error": "invalid internal token"}


def test_valid_internal_token_allows_non_health_endpoint():
    app = token_gate_app()

    response = app.test_client().get("/diagnostics", headers={INTERNAL_TOKEN_HEADER: INTERNAL_TOKEN})

    assert response.status_code == 200
    assert response.get_json()["service"] == "reelshort-content-provider"


def test_health_stays_anonymous_even_without_internal_token():
    app = token_gate_app(token="")

    response = app.test_client().get("/health")

    assert response.status_code == 200
    assert response.get_json() == {"service": "reelshort-content-provider", "status": "UP"}


def test_unconfigured_internal_token_fails_closed_for_content_endpoints():
    app = token_gate_app(token="")

    response = app.test_client().get("/api/v1/reelshort/search?keywords=love")

    assert response.status_code == 503
    assert response.get_json() == {"error": "content provider internal token is not configured"}


def test_unexpected_exception_records_diagnostics_and_returns_502():
    class ExplodingClient(ContractStubClient):
        def search(self, keywords, locale="en"):
            raise RuntimeError("boom")

    stub = ExplodingClient()
    app = create_app(client=stub, internal_token=INTERNAL_TOKEN)

    response = authed_test_client(app).get("/api/v1/reelshort/search?keywords=love")

    assert response.status_code == 502
    assert response.get_json() == {"error": "content provider internal error"}
    assert stub.diagnostics.snapshot()["counters"] == {"unexpected_error": 1}
    event = stub.diagnostics.snapshot()["recent_events"][0]
    assert event["context"]["error_type"] == "RuntimeError"


def test_diagnostic_context_truncates_long_external_input():
    stub = ContractStubClient()
    long_keywords = "k" * 500

    stub.diagnostics.record("search_empty", keywords=long_keywords)

    context = stub.diagnostics.snapshot()["recent_events"][0]["context"]["keywords"]
    assert len(context) <= app_module.DIAGNOSTIC_VALUE_MAX_LENGTH + 3
    assert context.startswith("k" * 10)


def test_video_keeps_video_url_when_next_episode_lookup_fails(monkeypatch):
    class FakeResponse:
        status_code = 200
        ok = True

        def json(self):
            return {
                "pageProps": {
                    "data": {
                        "video_url": "https://cdn.example.com/video.m3u8",
                        "serial_number": 2,
                        "duration": 90,
                    }
                }
            }

    monkeypatch.setattr("app.requests.get", lambda *args, **kwargs: FakeResponse())
    client = ReelShortClient("https://site.example", "id", 3, build_id="build-1")
    monkeypatch.setattr(
        client,
        "episodes",
        lambda *args, **kwargs: (_ for _ in ()).throw(UpstreamError(502, "upstream request failed")),
    )

    video = client.video("book-1", 2, "love-story", "chapter-2")

    assert video == {
        "video_url": "https://cdn.example.com/video.m3u8",
        "episode": 2,
        "duration": 90,
        "next_episode": None,
    }
    assert client.diagnostics.snapshot()["counters"] == {"next_episode_unavailable": 1}


def test_video_tolerates_malformed_duration_and_serial_number(monkeypatch):
    class FakeResponse:
        status_code = 200
        ok = True

        def json(self):
            return {
                "pageProps": {
                    "data": {
                        "video_url": "https://cdn.example.com/video.m3u8",
                        "serial_number": "not-a-number",
                        "duration": "not-a-number",
                    }
                }
            }

    monkeypatch.setattr("app.requests.get", lambda *args, **kwargs: FakeResponse())
    client = ReelShortClient("https://site.example", "id", 3, build_id="build-1")
    monkeypatch.setattr(
        client,
        "episodes",
        lambda *args, **kwargs: {"book": {}, "episodes": [{"episode": 2, "chapter_id": "chapter-2"}]},
    )

    video = client.video("book-1", 2, "love-story", "chapter-2")

    assert video["episode"] == 2
    assert video["duration"] == 0
    assert video["next_episode"] is None


def test_map_book_tolerates_non_numeric_chapter_count():
    mapped = ReelShortClient("https://site.example", "id", 3)._map_book(
        {"book_id": "book-1", "book_title": "Love Story", "chapter_count": "not-a-number"}
    )

    assert mapped["chapter_count"] == 0


def test_map_chapters_tolerates_non_numeric_serial_number():
    mapped = ReelShortClient("https://site.example", "id", 3)._map_chapters(
        [
            {"serial_number": "1", "chapter_id": "chapter-1"},
            {"serial_number": "abc", "chapter_id": "chapter-2"},
            {"serial_number": 3, "chapter_id": "chapter-3"},
        ]
    )

    assert [episode["episode"] for episode in mapped] == [1, 3]


def test_home_shelf_books_tolerate_malformed_fallback_structure(monkeypatch):
    client = ReelShortClient("https://site.example", "id", 3)
    monkeypatch.setattr(
        client,
        "_get_locale_data",
        lambda *args, **kwargs: {
            "pageProps": {"fallback": {"/api/ms/hall/webInfo": {"bookShelfList": "not-a-list"}}}
        },
    )

    assert client._home_shelf_books("recommend") == []


def test_book_info_maps_upstream_error_code_to_502_and_records_diagnostics(monkeypatch):
    client = ReelShortClient("https://site.example", "id", 3)
    monkeypatch.setattr(
        client,
        "_get",
        lambda path, params=None: {"code": 500, "msg": "internal error", "data": None},
    )

    with pytest.raises(UpstreamError) as error:
        client._book_info("book-1")

    assert error.value.status_code == 502
    assert client.diagnostics.snapshot()["counters"] == {"book_info_upstream_error": 1}


def test_book_info_returns_data_when_code_is_zero(monkeypatch):
    client = ReelShortClient("https://site.example", "id", 3)
    monkeypatch.setattr(
        client,
        "_get",
        lambda path, params=None: {"code": 0, "data": {"online_base": [{"serial_number": 1}]}},
    )

    assert client._book_info("book-1") == {"online_base": [{"serial_number": 1}]}


def test_book_info_tolerates_missing_data_payload(monkeypatch):
    client = ReelShortClient("https://site.example", "id", 3)
    monkeypatch.setattr(client, "_get", lambda path, params=None: {"code": 0})

    assert client._book_info("book-1") == {}


def test_reelshort_client_rejects_redirect_to_non_whitelisted_port(monkeypatch):
    monkeypatch.setattr(
        "app.requests.get",
        lambda *args, **kwargs: StreamingResponse(302, headers={"Location": "https://cdn.example:8443/data.json"}),
    )
    client = ReelShortClient(
        "https://site.example", "id", 3, build_id="build", resolver=lambda host: ["93.184.216.34"]
    )

    with pytest.raises(UpstreamError, match="unsafe redirect"):
        client._get("/data.json")

    assert client.diagnostics.snapshot()["counters"] == {"upstream_port_blocked": 1}


def test_reelshort_client_rejects_https_to_http_downgrade(monkeypatch):
    monkeypatch.setattr(
        "app.requests.get",
        lambda *args, **kwargs: StreamingResponse(302, headers={"Location": "http://cdn.example/data.json"}),
    )
    client = ReelShortClient(
        "https://site.example", "id", 3, build_id="build", resolver=lambda host: ["93.184.216.34"]
    )

    with pytest.raises(UpstreamError, match="unsafe redirect"):
        client._get("/data.json")

    assert client.diagnostics.snapshot()["counters"] == {"upstream_downgrade_blocked": 1}


def test_reelshort_client_allows_https_redirect_on_default_port(monkeypatch):
    responses = [
        StreamingResponse(302, headers={"Location": "https://cdn.example/data.json"}),
        StreamingResponse(body=b'{"ok": true}'),
    ]
    monkeypatch.setattr("app.requests.get", lambda *args, **kwargs: responses.pop(0))
    client = ReelShortClient(
        "https://site.example", "id", 3, build_id="build", resolver=lambda host: ["93.184.216.34"]
    )

    assert client._get("/data.json") == {"ok": True}
    assert client.diagnostics.snapshot()["counters"] == {}


def test_run_app_requires_internal_token_for_non_loopback_host(monkeypatch):
    monkeypatch.setenv("CONTENT_PROVIDER_HOST", "0.0.0.0")
    monkeypatch.delenv(INTERNAL_TOKEN_ENV, raising=False)

    with pytest.raises(RuntimeError, match=INTERNAL_TOKEN_ENV):
        app_module.run_app()


def test_run_app_allows_loopback_host_without_internal_token(monkeypatch):
    captured = {}

    class FakeApp:
        def run(self, host, port):
            captured["host"] = host
            captured["port"] = port

    monkeypatch.setattr(app_module, "create_app", lambda: FakeApp())
    monkeypatch.setenv("CONTENT_PROVIDER_HOST", "127.0.0.1")
    monkeypatch.setenv("CONTENT_PROVIDER_PORT", "5000")
    monkeypatch.delenv(INTERNAL_TOKEN_ENV, raising=False)

    app_module.run_app()

    assert captured == {"host": "127.0.0.1", "port": 5000}
