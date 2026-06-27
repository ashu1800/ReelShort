import os
import re
from urllib.parse import quote

import requests
from flask import Flask, jsonify, request


class UpstreamError(Exception):
    def __init__(self, status_code: int, message: str):
        super().__init__(message)
        self.status_code = status_code
        self.message = message


class ReelShortClient:
    def __init__(self, site_url: str, site_id: str, timeout_seconds: float, build_id: str | None = None):
        self.site_url = site_url.rstrip("/")
        self.site_id = site_id
        self.timeout_seconds = timeout_seconds
        self.build_id = build_id
        self.fixed_build_id = build_id is not None

    @classmethod
    def from_env(cls):
        return cls(
            os.getenv("REELSHORT_SITE_URL", "https://www.reelshort.com"),
            os.getenv("REELSHORT_SITE_ID", "37"),
            float(os.getenv("REELSHORT_REQUEST_TIMEOUT_SECONDS", "10")),
            os.getenv("REELSHORT_NEXT_BUILD_ID"),
        )

    def search(self, keywords: str):
        payload = self._get_data("/search.json", params={"keywords": keywords})
        return [self._map_book(book) for book in self._books(payload)]

    def shelf(self, shelf_name: str):
        payload = self._get_data(f"/{shelf_name}.json")
        return [self._map_book(book) for book in self._books(payload)]

    def episodes(self, book_id: str, filtered_title: str):
        payload = self._movie_payload(book_id, filtered_title)
        chapters = payload.get("pageProps", {}).get("data", {}).get("online_base", [])
        return [
            {"episode": chapter.get("serial_number"), "chapter_id": chapter.get("chapter_id", "")}
            for chapter in chapters
            if chapter.get("chapter_id")
        ]

    def video(self, book_id: str, episode_num: int, filtered_title: str, chapter_id: str):
        slug = f"episode-{episode_num}-{filtered_title}-{book_id}-{chapter_id}"
        payload = self._get_data(
            f"/episodes/{quote(slug, safe='')}.json",
            params={"play_time": "1", "slug": slug},
        )
        episode_data = payload.get("pageProps", {}).get("data", {})
        if not episode_data.get("video_url"):
            raise UpstreamError(404, "upstream not found")

        chapters = self.episodes(book_id, filtered_title)
        next_chapter = None
        for index, chapter in enumerate(chapters):
            if chapter.get("episode") == episode_num and index + 1 < len(chapters):
                next_chapter = chapters[index + 1]
                break
        return {
            "video_url": episode_data.get("video_url", ""),
            "episode": int(episode_data.get("serial_number", episode_num) or episode_num),
            "duration": int(episode_data.get("duration", 0) or 0),
            "next_episode": next_chapter,
        }

    def _movie_payload(self, book_id: str, filtered_title: str):
        slug = f"{filtered_title}-{book_id}"
        return self._get_data(
            f"/movie/{quote(slug, safe='')}.json",
            params={"slug": slug},
        )

    def _get_data(self, data_path: str, params=None):
        build_id = self.build_id or self._discover_build_id()
        try:
            return self._get(f"/_next/data/{quote(build_id, safe='')}/{self.site_id}{data_path}", params=params)
        except UpstreamError as exception:
            if exception.status_code != 404 or self.fixed_build_id:
                raise
            self.build_id = None
            fresh_build_id = self._discover_build_id()
            return self._get(
                f"/_next/data/{quote(fresh_build_id, safe='')}/{self.site_id}{data_path}",
                params=params,
            )

    def _get(self, path: str, params=None):
        try:
            response = requests.get(
                f"{self.site_url}{path}",
                params=params,
                timeout=self.timeout_seconds,
            )
        except requests.RequestException as exception:
            raise UpstreamError(502, "upstream request failed") from exception

        if response.status_code == 404:
            raise UpstreamError(404, "upstream not found")
        if not response.ok:
            raise UpstreamError(502, "upstream request failed")
        try:
            return response.json()
        except requests.JSONDecodeError as exception:
            raise UpstreamError(502, "upstream returned invalid json") from exception

    def _discover_build_id(self):
        try:
            response = requests.get(f"{self.site_url}/{quote(self.site_id, safe='')}", timeout=self.timeout_seconds)
        except requests.RequestException as exception:
            raise UpstreamError(502, "upstream request failed") from exception
        if not response.ok:
            raise UpstreamError(502, "upstream request failed")
        match = re.search(r'"buildId":"([^"]+)"', response.text)
        if not match:
            match = re.search(rf'/{re.escape(self.site_id)}/_next/data/([^/]+)/', response.text)
        if not match:
            raise UpstreamError(502, "upstream build id not found")
        self.build_id = match.group(1)
        return self.build_id

    def _books(self, payload):
        page_props = payload.get("pageProps", {})
        books = page_props.get("books")
        if books is None:
            books = page_props.get("bookList")
        return books or []

    def _map_book(self, book):
        title = book.get("book_title") or book.get("title") or ""
        return {
            "book_id": book.get("book_id") or book.get("_id") or book.get("id") or "",
            "book_title": title,
            "filtered_title": book.get("filtered_title") or self._filtered_title(title),
            "book_pic": book.get("book_pic") or book.get("cover") or "",
            "chapter_count": int(book.get("chapter_count", 0) or 0),
        }

    def _filtered_title(self, title: str):
        value = re.sub(r"[^a-z0-9]+", "-", title.lower()).strip("-")
        return value


def create_app(client=None) -> Flask:
    app = Flask(__name__)
    reelshort_client = client or ReelShortClient.from_env()

    @app.get("/health")
    def health():
        return jsonify({"status": "UP", "service": "reelshort-content-provider"})

    @app.errorhandler(UpstreamError)
    def upstream_error(error: UpstreamError):
        return jsonify({"error": error.message}), error.status_code

    @app.get("/api/v1/reelshort/search")
    def search():
        keywords = request.args.get("keywords", "").strip()
        if not keywords:
            return jsonify({"error": "keywords is required"}), 400
        return jsonify({"results": reelshort_client.search(keywords)})

    def shelf_response(shelf_name: str):
        return jsonify({"books": reelshort_client.shelf(shelf_name)})

    @app.get("/api/v1/reelshort/recommend")
    def recommend():
        return shelf_response("recommend")

    @app.get("/api/v1/reelshort/newrelease")
    def newrelease():
        return shelf_response("newrelease")

    @app.get("/api/v1/reelshort/dramadub")
    def dramadub():
        return shelf_response("dramadub")

    @app.get("/api/v1/reelshort/episodes/<book_id>")
    def episodes(book_id: str):
        filtered_title = request.args.get("filtered_title", "").strip()
        if not filtered_title:
            return jsonify({"error": "filtered_title is required"}), 400
        return jsonify({"episodes": reelshort_client.episodes(book_id, filtered_title)})

    @app.get("/api/v1/reelshort/video/<book_id>/<int:episode_num>")
    def video(book_id: str, episode_num: int):
        filtered_title = request.args.get("filtered_title", "").strip()
        if not filtered_title:
            return jsonify({"error": "filtered_title is required"}), 400
        chapter_id = request.args.get("chapter_id", "").strip()
        if not chapter_id:
            return jsonify({"error": "chapter_id is required"}), 400
        return jsonify(reelshort_client.video(book_id, episode_num, filtered_title, chapter_id))

    return app


def run_app():
    create_app().run(
        host=os.getenv("CONTENT_PROVIDER_HOST", "127.0.0.1"),
        port=int(os.getenv("CONTENT_PROVIDER_PORT", "5000")),
    )


if __name__ == "__main__":
    run_app()

