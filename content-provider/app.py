import os
import re
import json
from concurrent.futures import ThreadPoolExecutor, as_completed
from html import unescape
from urllib.parse import quote

import requests
from flask import Flask, jsonify, request


DEFAULT_CATALOG_SEARCH_KEYWORDS = (
    "love",
    "billionaire",
    "ceo",
    "marriage",
    "revenge",
    "baby",
    "divorce",
    "mafia",
    "werewolf",
    "alpha",
    "luna",
    "contract",
    "pregnant",
    "secret",
    "husband",
    "wife",
    "boss",
    "romance",
    "family",
    "doctor",
    "queen",
)

MAX_CATALOG_KEYWORDS = 50
MAX_CATALOG_PAGES_PER_KEYWORD = 5
MAX_CATALOG_REQUESTS = 200
MAX_CATALOG_REQUEST_WORKERS = 16


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
        books = []
        try:
            payload = self._get_data(f"/{shelf_name}.json")
            books = self._books(payload)
        except UpstreamError as exception:
            if exception.status_code != 404 or shelf_name not in {"recommend", "newrelease", "dramadub"}:
                raise
        if not books and shelf_name in {"recommend", "newrelease", "dramadub"}:
            books = self._home_shelf_books(shelf_name)
        if shelf_name == "recommend":
            books = self._expanded_recommend_books(books)
        return [self._map_book(book) for book in books]

    def episodes(self, book_id: str, filtered_title: str):
        try:
            book_data = self._movie_payload(book_id, filtered_title).get("pageProps", {}).get("data", {})
        except UpstreamError as exception:
            if exception.status_code != 404:
                raise
            book_data = self._book_info(book_id)
        return {
            "book": self._map_book(self._with_book_context(book_id, filtered_title, book_data)),
            "episodes": self._map_chapters(
                book_data.get("online_base", []),
                fallback_description=self._first_text(
                    book_data, ("description", "introduction", "book_intro", "summary", "special_desc", "desc")
                ),
            ),
        }

    @staticmethod
    def _with_book_context(book_id: str, filtered_title: str, book_data: dict):
        view = dict(book_data)
        view.setdefault("book_id", book_id)
        if not view.get("book_id"):
            view["book_id"] = book_id
        view.setdefault("filtered_title", filtered_title)
        if not view.get("filtered_title"):
            view["filtered_title"] = filtered_title
        if not view.get("chapter_count") and isinstance(view.get("online_base"), list):
            view.setdefault("chapter_count", len(view["online_base"]))
        return view

    def video(self, book_id: str, episode_num: int, filtered_title: str, chapter_id: str):
        slug = f"episode-{episode_num}-{filtered_title}-{book_id}-{chapter_id}"
        try:
            payload = self._get_data(
                f"/episodes/{quote(slug, safe='')}.json",
                params={"play_time": "1", "slug": slug},
            )
        except UpstreamError as exception:
            if exception.status_code != 404:
                raise
            payload = self._episode_html_payload(slug)
        episode_data = payload.get("pageProps", {}).get("data", {})
        if not episode_data.get("video_url"):
            raise UpstreamError(404, "upstream not found")

        chapters = self.episodes(book_id, filtered_title).get("episodes", [])
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

    def _book_info_chapters(self, book_id: str):
        return self._book_info(book_id).get("online_base", [])

    def _book_info(self, book_id: str):
        payload = self._get("/api/video/book/getBookInfo", params={"book_id": book_id})
        if payload.get("code") not in {0, None}:
            raise UpstreamError(404, "upstream not found")
        return payload.get("data", {})

    def _episode_html_payload(self, slug: str):
        html = self._get_text(f"/episodes/{quote(slug, safe='')}", params={"play_time": "1"})
        match = re.search(
            r'<script id="__NEXT_DATA__" type="application/json">(.*?)</script>',
            html,
            re.DOTALL,
        )
        if not match:
            raise UpstreamError(404, "upstream not found")
        try:
            next_data = json.loads(unescape(match.group(1)))
        except json.JSONDecodeError as exception:
            raise UpstreamError(502, "upstream returned invalid json") from exception
        return next_data.get("props", {})

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

    def _get_text(self, path: str, params=None):
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
        return response.text

    def _discover_build_id(self):
        try:
            response = requests.get(f"{self.site_url}/{quote(self.site_id, safe='')}", timeout=self.timeout_seconds)
        except requests.RequestException as exception:
            raise UpstreamError(502, "upstream request failed") from exception
        match = re.search(r'"buildId":"([^"]+)"', response.text)
        if not match:
            match = re.search(rf'/{re.escape(self.site_id)}/_next/data/([^/]+)/', response.text)
        if not match:
            try:
                response = requests.get(f"{self.site_url}/", timeout=self.timeout_seconds)
            except requests.RequestException as exception:
                raise UpstreamError(502, "upstream request failed") from exception
            if not response.ok:
                raise UpstreamError(502, "upstream request failed")
            match = re.search(r'"buildId":"([^"]+)"', response.text)
        if not match:
            raise UpstreamError(502, "upstream build id not found")
        self.build_id = match.group(1)
        return self.build_id

    def _home_shelf_books(self, shelf_name: str):
        payload = self._get_locale_data(".json")
        web_info = payload.get("pageProps", {}).get("fallback", {}).get("/api/ms/hall/webInfo", {})
        shelves = web_info.get("bookShelfList") or []
        selected_shelves = self._select_home_shelves(shelves, shelf_name)
        books = []
        seen = set()
        for shelf in selected_shelves:
            for book in shelf.get("books") or []:
                book_id = book.get("book_id") or book.get("_id") or book.get("id")
                if book_id and book_id not in seen:
                    seen.add(book_id)
                    books.append(book)
        return books

    def _get_locale_data(self, data_path: str, params=None):
        build_id = self.build_id or self._discover_build_id()
        return self._get(f"/_next/data/{quote(build_id, safe='')}/en{data_path}", params=params)

    def _expanded_recommend_books(self, base_books):
        max_books = self._bounded_int_env("REELSHORT_CATALOG_MAX_BOOKS", default=500, maximum=500)
        if max_books <= 0:
            return []

        books = []
        seen = set()
        for book in base_books:
            if self._append_unique_book(books, seen, book, max_books):
                return books

        max_pages = self._bounded_int_env(
            "REELSHORT_CATALOG_MAX_PAGES_PER_KEYWORD",
            default=3,
            maximum=MAX_CATALOG_PAGES_PER_KEYWORD,
        )
        search_pages = self._catalog_search_pages(self._catalog_search_keywords(), max_pages)
        for search_books in search_pages:
            for book in search_books:
                if self._append_unique_book(books, seen, book, max_books):
                    return books
        return books

    def _catalog_search_pages(self, keywords, max_pages: int):
        if max_pages <= 0:
            return []

        keyword_limit = min(MAX_CATALOG_KEYWORDS, max(1, MAX_CATALOG_REQUESTS // max_pages))
        keywords = keywords[:keyword_limit]
        workers = self._bounded_int_env(
            "REELSHORT_CATALOG_REQUEST_WORKERS",
            default=8,
            maximum=MAX_CATALOG_REQUEST_WORKERS,
        )
        if workers <= 1:
            return self._catalog_search_keywords_sequential(keywords, max_pages)
        return self._catalog_search_keywords_parallel(keywords, max_pages, workers)

    def _catalog_search_keywords_sequential(self, keywords, max_pages: int):
        return [
            pages
            for keyword in keywords
            for pages in self._catalog_search_keyword_pages(keyword, max_pages)
        ]

    def _catalog_search_keywords_parallel(self, keywords, max_pages: int, workers: int):
        results = {}
        with ThreadPoolExecutor(max_workers=workers) as executor:
            futures = {
                executor.submit(self._catalog_search_keyword_pages, keyword, max_pages): keyword_index
                for keyword_index, keyword in enumerate(keywords)
            }
            for future in as_completed(futures):
                keyword_index = futures[future]
                results[keyword_index] = future.result()
        return [
            page_books
            for keyword_index in sorted(results)
            for page_books in results[keyword_index]
        ]

    def _catalog_search_keyword_pages(self, keyword: str, max_pages: int):
        pages = []
        for page in range(1, max_pages + 1):
            books = self._fetch_catalog_search_books(keyword, page)
            if not books:
                break
            pages.append(books)
        return pages

    def _fetch_catalog_search_books(self, keyword: str, page: int):
        params = {"keywords": keyword}
        if page > 1:
            params["page"] = page
        try:
            payload = self._get_locale_data("/search.json", params=params)
        except UpstreamError:
            return None
        return self._books(payload)

    def _append_unique_book(self, books, seen, book, max_books: int):
        book_id = self._book_id(book)
        if not book_id or book_id in seen:
            return len(books) >= max_books
        seen.add(book_id)
        books.append(book)
        return len(books) >= max_books

    def _catalog_search_keywords(self):
        raw_keywords = os.getenv("REELSHORT_CATALOG_SEARCH_KEYWORDS", "")
        keywords = [keyword.strip() for keyword in raw_keywords.split(",") if keyword.strip()]
        return (keywords or list(DEFAULT_CATALOG_SEARCH_KEYWORDS))[:MAX_CATALOG_KEYWORDS]

    def _bounded_int_env(self, name: str, default: int, maximum: int):
        try:
            value = int(os.getenv(name, str(default)))
        except ValueError:
            return default
        return min(max(value, 0), maximum)

    def _select_home_shelves(self, shelves, shelf_name: str):
        if shelf_name == "recommend":
            return [shelf for shelf in shelves if shelf.get("books")]
        labels = {
            "newrelease": ("new release", "new-release", "new"),
            "dramadub": ("drama dub", "drama-dub", "dub"),
        }[shelf_name]
        matched = [
            shelf for shelf in shelves
            if any(label in str(shelf.get("bookshelf_name", "")).lower() for label in labels)
        ]
        return matched or [shelf for shelf in shelves if shelf.get("books")]

    def _books(self, payload):
        page_props = payload.get("pageProps", {})
        books = page_props.get("books")
        if books is None:
            books = page_props.get("bookList")
        return books or []

    def _map_book(self, book):
        title = book.get("book_title") or book.get("title") or ""
        return {
            "book_id": self._book_id(book),
            "book_title": title,
            "filtered_title": book.get("filtered_title") or self._filtered_title(title),
            "book_pic": book.get("book_pic") or book.get("cover") or "",
            "description": self._first_text(
                book, ("description", "introduction", "book_intro", "summary", "special_desc", "desc")
            ),
            "chapter_count": int(book.get("chapter_count", 0) or 0),
        }

    def _map_chapters(self, chapters, fallback_description: str = ""):
        return [
            {
                "episode": chapter.get("serial_number"),
                "chapter_id": chapter.get("chapter_id", ""),
                "title": self._first_text(chapter, ("chapter_title", "chapter_name", "title", "name")),
                "description": self._first_text(
                    chapter, ("description", "intro", "summary", "chapter_desc", "desc")
                ) or fallback_description,
            }
            for chapter in chapters
            if chapter.get("chapter_id") and int(chapter.get("serial_number", 0) or 0) > 0
        ]

    def _book_id(self, book):
        return book.get("book_id") or book.get("_id") or book.get("id") or ""

    def _first_text(self, payload, keys):
        for key in keys:
            value = payload.get(key)
            if isinstance(value, str) and value.strip():
                return value.strip()
        return ""

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
        return jsonify(reelshort_client.episodes(book_id, filtered_title))

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

