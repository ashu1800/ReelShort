"""上游 HTTP 加固层 + 共享容错工具。

从 `app.py` 抽出来，供多个上游客户端（ReelShort / TwistyTV）共用：公网 IP 逐跳校验、
DNS pinning（保留原始域名 SNI 与证书校验）、响应字节上限、总超时、重定向上限、
https→http 降级拦截、诊断事件记录。

**不要**在新增上游客户端里复制这段逻辑：安全边界散成两份以后必然漂移，只改一份的
那次改动就是漏洞。
"""

from __future__ import annotations

import ipaddress
import json
import math
import socket
import threading
import time
from collections import Counter, deque
from datetime import datetime, timezone
from urllib.parse import urljoin, urlparse

import requests
from requests.adapters import HTTPAdapter
from urllib3.connectionpool import HTTPSConnectionPool

DEFAULT_MAX_RESPONSE_BYTES = 5 * 1024 * 1024
DEFAULT_MAX_REDIRECTS = 3
RESPONSE_CHUNK_SIZE = 64 * 1024
REDIRECT_STATUS_CODES = {301, 302, 303, 307, 308}
UPSTREAM_USER_AGENT = "ShortLinkContentProvider/1.0"

# P3-21：只允许 HTTP/HTTPS 默认端口出网，避免被上游重定向驱动到任意公网 IP:端口。
ALLOWED_UPSTREAM_PORTS = {"http": 80, "https": 443}

# P2-16：诊断 context 中的外部输入（搜索关键词、URL）统一截断，避免诊断数据被撑爆。
DIAGNOSTIC_VALUE_MAX_LENGTH = 200


def _as_int(value, default: int = 0) -> int:
    """容错整数转换：上游字段类型异常时回落到默认值，而不是直接 500。"""
    if isinstance(value, bool):
        return default
    if isinstance(value, int):
        return value
    if isinstance(value, float):
        if not math.isfinite(value):
            return default
        return int(value)
    if isinstance(value, str):
        try:
            return int(float(value.strip()))
        except (TypeError, ValueError):
            return default
    return default


def _as_list(value) -> list:
    """容错列表转换：非 list/tuple 一律视为空列表。"""
    if isinstance(value, (list, tuple)):
        return list(value)
    return []


def _as_dict(value) -> dict:
    """容错字典转换：非 dict 一律视为空字典。"""
    return value if isinstance(value, dict) else {}


def _truncate(value, max_length: int = DIAGNOSTIC_VALUE_MAX_LENGTH):
    """诊断 context 值截断，保持事件可读且不携带超长外部输入。"""
    if value is None:
        return None
    text = str(value)
    return text if len(text) <= max_length else text[:max_length] + "..."


class _PinnedHTTPSAdapter(HTTPAdapter):
    """连接到已校验的 IP，但让 TLS 使用原始域名的 SNI 和证书校验。"""

    def __init__(self, original_hostname: str):
        super().__init__()
        self.original_hostname = original_hostname

    def get_connection(self, url, proxies=None):
        pool = super().get_connection(url, proxies=proxies)
        return self._configure_pool(pool)

    def get_connection_with_tls_context(self, request, verify, proxies=None, cert=None):
        pool = super().get_connection_with_tls_context(request, verify, proxies=proxies, cert=cert)
        return self._configure_pool(pool)

    def _configure_pool(self, pool):
        if isinstance(pool, HTTPSConnectionPool):
            pool.assert_hostname = self.original_hostname
            pool.conn_kw["server_hostname"] = self.original_hostname
        return pool


class UpstreamError(Exception):
    def __init__(self, status_code: int, message: str):
        super().__init__(message)
        self.status_code = status_code
        self.message = message


class ProviderDiagnostics:
    def __init__(self, max_events: int = 20):
        self.max_events = max_events
        self._events = deque(maxlen=max_events)
        self._counters = Counter()
        self._lock = threading.Lock()

    def record(self, event_type: str, **context):
        sanitized = {
            key: _truncate(value) if isinstance(value, (str, bytes)) else value
            for key, value in context.items()
            if value not in (None, "")
        }
        event = {
            "event_type": event_type,
            "observed_at": datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z"),
            "context": sanitized,
        }
        with self._lock:
            self._events.appendleft(event)
            self._counters[event_type] += 1

    def snapshot(self):
        with self._lock:
            return {
                "total_events": sum(self._counters.values()),
                "counters": dict(self._counters),
                "recent_events": list(self._events),
            }


class HardenedFetcher:
    """受控 HTTP 客户端：所有出网请求都必须经过 `_request()`。

    子类只需覆盖 `_default_headers()` 追加自己的固定请求头（例如登录 Cookie）。
    """

    def __init__(
        self,
        timeout_seconds: float,
        *,
        max_response_bytes: int = DEFAULT_MAX_RESPONSE_BYTES,
        max_redirects: int = DEFAULT_MAX_REDIRECTS,
        diagnostics: ProviderDiagnostics | None = None,
        resolver=None,
        monotonic=None,
    ):
        if not math.isfinite(timeout_seconds) or timeout_seconds <= 0:
            raise ValueError("timeout_seconds must be finite and greater than zero")
        if max_response_bytes <= 0:
            raise ValueError("max_response_bytes must be greater than zero")
        if max_redirects < 0:
            raise ValueError("max_redirects must be zero or greater")
        self.timeout_seconds = timeout_seconds
        self.diagnostics = diagnostics or ProviderDiagnostics()
        self.max_response_bytes = max_response_bytes
        self.max_redirects = max_redirects
        self.resolver = resolver or self._resolve_addresses
        self.monotonic = monotonic or time.monotonic

    def _default_headers(self) -> dict[str, str]:
        return {}

    def _request(self, url: str, params=None, *, method: str = "GET", json_body=None, extra_headers=None):
        # M11: _validate_url 现在返回 (pinned_url, original_hostname) 用于 DNS pinning
        pinned_url, _ = self._validate_url(url, "upstream_url_blocked", "upstream returned unsafe upstream URL")
        deadline = self.monotonic() + self.timeout_seconds
        current_url = pinned_url
        original_current_url = url
        current_params = params
        visited = {url}
        headers = {"Host": self._host_header(url), "User-Agent": UPSTREAM_USER_AGENT}
        headers.update(self._default_headers())
        if extra_headers:
            headers.update({name: str(value) for name, value in extra_headers.items()})

        for redirect_count in range(self.max_redirects + 1):
            remaining = self.timeout_seconds if redirect_count == 0 else deadline - self.monotonic()
            if remaining <= 0:
                self.diagnostics.record("upstream_deadline_exceeded", url=current_url)
                raise UpstreamError(502, "upstream request deadline exceeded")
            try:
                response = self._request_get(
                    current_url,
                    original_url=original_current_url,
                    method=method,
                    params=current_params,
                    json=json_body if redirect_count == 0 else None,
                    timeout=remaining,
                    allow_redirects=False,
                    stream=True,
                    headers=headers,
                )
            except requests.RequestException as exception:
                raise UpstreamError(502, "upstream request failed") from exception

            if response.status_code not in REDIRECT_STATUS_CODES:
                self._read_limited_response(response, deadline, current_url)
                return response

            if method != "GET":
                # API 调用不接受重定向：跟随会丢掉 body，重发又可能重复提交。
                self._close_response(response)
                self.diagnostics.record("upstream_post_redirect", url=current_url)
                raise UpstreamError(502, "upstream returned unexpected redirect")

            location = getattr(response, "headers", {}).get("Location")
            self._close_response(response)
            if not location:
                raise UpstreamError(502, "upstream redirect missing location")
            if redirect_count >= self.max_redirects:
                self.diagnostics.record("upstream_redirect_limit", url=current_url)
                raise UpstreamError(502, "upstream returned too many redirects")

            redirect_url = urljoin(original_current_url, location)
            # M11: _validate_redirect_url 也返回 pinned URL（先做公网/端口校验，再做协议降级校验）
            pinned_redirect, _ = self._validate_redirect_url(redirect_url)
            # P3-21：禁止 https → http 降级，降级时记录诊断事件并拒绝。
            if urlparse(original_current_url).scheme == "https" and urlparse(redirect_url).scheme == "http":
                self.diagnostics.record(
                    "upstream_downgrade_blocked",
                    url=original_current_url,
                    redirect_url=redirect_url,
                )
                raise UpstreamError(502, "upstream returned unsafe redirect")
            if redirect_url in visited:
                self.diagnostics.record("upstream_redirect_loop", url=redirect_url)
                raise UpstreamError(502, "upstream redirect loop")
            visited.add(redirect_url)
            current_url = pinned_redirect
            original_current_url = redirect_url
            headers["Host"] = self._host_header(redirect_url)
            current_params = None

        raise UpstreamError(502, "upstream returned too many redirects")

    def _request_get(self, pinned_url: str, original_url: str, method: str = "GET", **kwargs):
        parsed = urlparse(original_url)
        if parsed.scheme != "https":
            return requests.request(method, pinned_url, **kwargs)

        session = requests.Session()
        session.trust_env = False
        session.mount("https://", _PinnedHTTPSAdapter(parsed.hostname))
        try:
            response = session.request(method, pinned_url, **kwargs)
            # Keep the session alive until response streaming has completed.
            response._reelshort_session = session
            return response
        except Exception:
            session.close()
            raise

    def _read_limited_response(self, response, deadline: float, url: str):
        if not hasattr(response, "iter_content"):
            return
        content = bytearray()
        deadline_expired = threading.Event()
        remaining = deadline - self.monotonic()
        if remaining <= 0:
            self._close_response(response)
            self.diagnostics.record("upstream_deadline_exceeded", url=url)
            raise UpstreamError(502, "upstream request deadline exceeded")

        def expire_response():
            deadline_expired.set()
            self._close_response(response)

        watchdog = threading.Timer(remaining, expire_response)
        watchdog.daemon = True
        watchdog.start()
        try:
            for chunk in response.iter_content(chunk_size=RESPONSE_CHUNK_SIZE):
                if deadline_expired.is_set() or self.monotonic() >= deadline:
                    deadline_expired.set()
                    break
                if not chunk:
                    continue
                content.extend(chunk)
                if len(content) > self.max_response_bytes:
                    self.diagnostics.record(
                        "upstream_response_too_large", url=url, max_bytes=self.max_response_bytes
                    )
                    raise UpstreamError(502, "upstream response too large")
            watchdog.cancel()
            if deadline_expired.is_set():
                self.diagnostics.record("upstream_deadline_exceeded", url=url)
                raise UpstreamError(502, "upstream request deadline exceeded")
            response._content = bytes(content)
            response._content_consumed = True
        except requests.RequestException as exception:
            if deadline_expired.is_set():
                self.diagnostics.record("upstream_deadline_exceeded", url=url)
                raise UpstreamError(502, "upstream request deadline exceeded") from exception
            raise UpstreamError(502, "upstream request failed") from exception
        finally:
            watchdog.cancel()
            self._close_response(response)

    def _validate_redirect_url(self, url: str) -> tuple[str, str]:
        return self._validate_and_pin_url(url, "upstream_redirect_blocked", "upstream returned unsafe redirect")

    def _validate_url(self, url: str, event_type: str, message: str) -> tuple[str, str]:
        # M11: 返回 pinning 后的 URL（用解析到的 IP 直连，避免 TOCTOU DNS rebinding）
        return self._validate_and_pin_url(url, event_type, message)

    @staticmethod
    def _host_header(url: str) -> str:
        parsed = urlparse(url)
        hostname = f"[{parsed.hostname}]" if ":" in parsed.hostname else parsed.hostname
        if parsed.port is None:
            return hostname
        return f"{hostname}:{parsed.port}"

    def _validate_and_pin_url(self, url: str, event_type: str, message: str) -> tuple[str, str]:
        parsed = urlparse(url)
        if parsed.scheme not in {"http", "https"} or not parsed.hostname:
            self.diagnostics.record(event_type, url=url)
            raise UpstreamError(502, message)
        try:
            original_port = parsed.port
        except ValueError:
            self.diagnostics.record(event_type, url=url)
            raise UpstreamError(502, message)
        # P3-21：端口白名单，只允许 HTTP/HTTPS 默认端口，防止被上游重定向驱动到任意公网 IP:端口。
        effective_port = original_port if original_port is not None else ALLOWED_UPSTREAM_PORTS[parsed.scheme]
        if effective_port not in ALLOWED_UPSTREAM_PORTS.values():
            self.diagnostics.record("upstream_port_blocked", url=url, port=effective_port)
            raise UpstreamError(502, message)
        try:
            # M11: DNS pinning —— 解析后直接用 IP 连接，避免 requests 二次解析导致的 TOCTOU
            addresses = self.resolver(parsed.hostname)
            safe = addresses and all(ipaddress.ip_address(address).is_global for address in addresses)
        except (OSError, ValueError):
            safe = False
        if not safe:
            self.diagnostics.record(event_type, url=url)
            raise UpstreamError(502, message)
        # 用第一个公网 IP 替换 hostname 实现 pinning，保留原 Host header
        pinned_ip = sorted(addresses)[0]
        # M11: 统一用 _replace(netloc=...) 构造，正确处理 IPv4/IPv6/带端口场景
        host_literal = f"[{pinned_ip}]" if ":" in pinned_ip else pinned_ip
        if original_port:
            netloc = f"{host_literal}:{original_port}"
        else:
            netloc = host_literal
        pinned_url = parsed._replace(netloc=netloc).geturl()
        original_host_literal = f"[{parsed.hostname}]" if ":" in parsed.hostname else parsed.hostname
        default_port = 443 if parsed.scheme == "https" else 80
        host_header = (
            original_host_literal
            if original_port in {None, default_port}
            else f"{original_host_literal}:{original_port}"
        )
        return pinned_url, host_header

    @staticmethod
    def _resolve_addresses(hostname: str):
        return {item[4][0] for item in socket.getaddrinfo(hostname, None, type=socket.SOCK_STREAM)}

    @staticmethod
    def _close_response(response):
        close = getattr(response, "close", None)
        if close:
            close()
        session = getattr(response, "_reelshort_session", None)
        if session is not None:
            session.close()

    @staticmethod
    def _json_body(response) -> dict:
        """把响应体解析成 dict；解析不出来按上游错误处理，而不是抛裸 ValueError。"""
        try:
            payload = json.loads(response.text)
        except (TypeError, ValueError) as exception:
            raise UpstreamError(502, "upstream returned invalid json") from exception
        return payload if isinstance(payload, dict) else {}
