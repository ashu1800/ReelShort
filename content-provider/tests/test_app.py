import pytest

from app import ReelShortClient, UpstreamError, create_app


BOOK = {
    "book_id": "book-1",
    "book_title": "Love Story",
    "filtered_title": "love-story",
    "book_pic": "https://example.com/cover.jpg",
    "chapter_count": 12,
}


class FakeReelShortClient:
    def search(self, keywords):
        return [BOOK | {"book_title": f"Search {keywords}"}]

    def shelf(self, shelf_name):
        return [BOOK | {"book_title": shelf_name}]

    def episodes(self, book_id, filtered_title):
        return [
            {"episode": 1, "chapter_id": "chapter-1"},
            {"episode": 2, "chapter_id": "chapter-2"},
        ]

    def video(self, book_id, episode_num, filtered_title, chapter_id):
        return {
            "video_url": "https://cdn.example.com/video.m3u8",
            "episode": episode_num,
            "duration": 120,
            "next_episode": {"episode": episode_num + 1, "chapter_id": "chapter-2"},
        }


class FailingReelShortClient(FakeReelShortClient):
    def search(self, keywords):
        raise UpstreamError(502, "upstream failed")


@pytest.fixture
def client():
    return create_app(client=FakeReelShortClient()).test_client()


def test_health_returns_up(client):
    response = client.get("/health")

    assert response.status_code == 200
    assert response.get_json() == {
        "service": "reelshort-content-provider",
        "status": "UP",
    }


def test_search_returns_spring_boot_contract(client):
    response = client.get("/api/v1/reelshort/search?keywords=love")

    assert response.status_code == 200
    assert response.get_json() == {
        "results": [BOOK | {"book_title": "Search love"}],
    }


def test_search_requires_keywords(client):
    response = client.get("/api/v1/reelshort/search")

    assert response.status_code == 400
    assert response.get_json() == {"error": "keywords is required"}


def test_upstream_error_maps_to_configured_status():
    app = create_app(client=FailingReelShortClient())

    response = app.test_client().get("/api/v1/reelshort/search?keywords=love")

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
        "books": [BOOK | {"book_title": shelf_name}],
    }


def test_episodes_return_spring_boot_contract(client):
    response = client.get("/api/v1/reelshort/episodes/book-1?filtered_title=love-story")

    assert response.status_code == 200
    assert response.get_json() == {
        "episodes": [
            {"episode": 1, "chapter_id": "chapter-1"},
            {"episode": 2, "chapter_id": "chapter-2"},
        ],
    }


def test_episodes_require_filtered_title(client):
    response = client.get("/api/v1/reelshort/episodes/book-1")

    assert response.status_code == 400
    assert response.get_json() == {"error": "filtered_title is required"}


def test_video_returns_spring_boot_contract(client):
    response = client.get(
        "/api/v1/reelshort/video/book-1/1?filtered_title=love-story&chapter_id=chapter-1"
    )

    assert response.status_code == 200
    assert response.get_json() == {
        "video_url": "https://cdn.example.com/video.m3u8",
        "episode": 1,
        "duration": 120,
        "next_episode": {"episode": 2, "chapter_id": "chapter-2"},
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

    def fake_get(url, params, timeout):
        captured["url"] = url
        captured["params"] = params
        captured["timeout"] = timeout
        return FakeResponse()

    monkeypatch.setattr("app.requests.get", fake_get)

    client = ReelShortClient("https://site.example", "id", 3, build_id="build-1")
    client.search("love story")

    assert captured == {
        "url": "https://site.example/_next/data/build-1/id/search.json",
        "params": {"keywords": "love story"},
        "timeout": 3,
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
            "chapter_count": 12,
        }
    ]


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
                            {"serial_number": 1, "chapter_id": "chapter-1"},
                            {"serial_number": 2, "chapter_id": "chapter-2"},
                        ]
                    }
                }
            }

    def fake_get(url, params, timeout):
        captured["url"] = url
        captured["params"] = params
        return FakeResponse()

    monkeypatch.setattr("app.requests.get", fake_get)

    episodes = ReelShortClient("https://site.example", "id", 3, build_id="build-1").episodes(
        "book/1",
        "love-story",
    )

    assert captured == {
        "url": "https://site.example/_next/data/build-1/id/movie/love-story-book%2F1.json",
        "params": {"slug": "love-story-book/1"},
    }
    assert episodes == [
        {"episode": 1, "chapter_id": "chapter-1"},
        {"episode": 2, "chapter_id": "chapter-2"},
    ]


def test_reelshort_client_fetches_video_episode_page(monkeypatch):
    calls = []

    class FakeResponse:
        status_code = 200
        ok = True

        def __init__(self, payload):
            self.payload = payload

        def json(self):
            return self.payload

    def fake_get(url, params, timeout):
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
                            {"serial_number": 2, "chapter_id": "chapter-2"},
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
        "url": "https://site.example/_next/data/build-1/id/episodes/episode-1-love-story-book-1-chapter-1.json",
        "params": {"play_time": "1", "slug": "episode-1-love-story-book-1-chapter-1"},
    }
    assert video == {
        "video_url": "https://cdn.example.com/1.m3u8",
        "episode": 1,
        "duration": 90,
        "next_episode": {"episode": 2, "chapter_id": "chapter-2"},
    }


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
