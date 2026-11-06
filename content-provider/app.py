import os
import re
import base64
import gzip
import zlib
import hmac
import json
import hashlib
import ipaddress
import math
import random
import socket
import threading
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from collections import Counter, deque
from datetime import datetime, timezone
from html import unescape
from urllib.parse import quote, urljoin, urlparse

import requests
from flask import Flask, jsonify, request
from requests.adapters import HTTPAdapter
from urllib3.connectionpool import HTTPSConnectionPool

try:  # 只用于加密上游登录口令；缺依赖时降级为"不能登录"，不影响匿名抓取。
    from Crypto.Cipher import AES as _AES
    from Crypto.Util.Padding import pad as _aes_pad
except ImportError:  # pragma: no cover - CI/生产都装 pycryptodome
    _AES = None
    _aes_pad = None


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

SUPPORTED_LOCALES = {"en"}
DEFAULT_LOCALE = "en"

MAX_CATALOG_KEYWORDS = 50
MAX_CATALOG_PAGES_PER_KEYWORD = 5
MAX_CATALOG_REQUESTS = 200
MAX_CATALOG_REQUEST_WORKERS = 16
DEFAULT_MAX_RESPONSE_BYTES = 5 * 1024 * 1024
DEFAULT_MAX_REDIRECTS = 3
RESPONSE_CHUNK_SIZE = 64 * 1024
REDIRECT_STATUS_CODES = {301, 302, 303, 307, 308}
UPSTREAM_USER_AGENT = "ShortLinkContentProvider/1.0"

# P3-21：只允许 HTTP/HTTPS 默认端口出网，避免被上游重定向驱动到任意公网 IP:端口。
ALLOWED_UPSTREAM_PORTS = {"http": 80, "https": 443}

# P2-16：诊断 context 中的外部输入（搜索关键词、URL）统一截断，避免诊断数据被撑爆。
DIAGNOSTIC_VALUE_MAX_LENGTH = 200

# P2-16：内部共享密钥。除 /health 外的所有端点都要求请求头 X-Content-Provider-Token。
INTERNAL_TOKEN_HEADER = "X-Content-Provider-Token"
INTERNAL_TOKEN_ENV = "CONTENT_PROVIDER_INTERNAL_TOKEN"

# 上游登录态：ReelShort 只对前 1–12 集公开 video_url，锁集的 payload 里 video_url 为空。
# 这里放浏览器里登录后复制的完整 Cookie 请求头（`a=1; b=2`），配置后随每个上游请求发出。
# 留空表示匿名抓取（行为与配置前完全一致）。
UPSTREAM_COOKIE_ENV = "REELSHORT_UPSTREAM_COOKIE"

# 上游账号登录：H5 接口用邮箱+密码换 session，之后的请求带 session/uid + HMAC 签名。
# 账号权益（会员）决定锁集能不能拿到 video_url；免费账号只能看到公开窗口。
# 凭据只从环境变量读，绝不写进仓库或日志。
UPSTREAM_EMAIL_ENV = "REELSHORT_UPSTREAM_EMAIL"
UPSTREAM_PASSWORD_ENV = "REELSHORT_UPSTREAM_PASSWORD"
# 签名密钥来自上游 H5 bundle（公开常量），上游轮换时用环境变量覆盖，避免改代码。
UPSTREAM_API_SECRET_ENV = "REELSHORT_UPSTREAM_API_SECRET"
UPSTREAM_CHANNEL_ENV = "REELSHORT_UPSTREAM_CHANNEL_ID"
DEFAULT_UPSTREAM_API_SECRET = "zj8N6zKEdrK8d1MxwHSvExdgQ868q1yT"
DEFAULT_UPSTREAM_CHANNEL = "WEB41001"
UPSTREAM_CLIENT_VERSION = "2.4.00"
UPSTREAM_API_VERSION = "1.0.4"
UPSTREAM_LOGIN_PATH = "/api/video/user/userLogin"
UPSTREAM_USER_INFO_PATH = "/api/video/user/getUserInfo"
UPSTREAM_CHAPTER_LIST_PATH = "/api/video/book/getChapterList"
UPSTREAM_CHAPTER_INFO_PATH = "/api/video/book/getChapterInfo"
# 上游对失效/伪造 session 的统一返回码，触发重新登录。
UPSTREAM_SESSION_EXPIRED_CODE = 103
# 学分集列表单页上限（实测 42 集一页返回全部）。
UPSTREAM_CHAPTER_PAGE_SIZE = 100
# 登录口令加密：H5 端是 gzip → AES-128-CBC/PKCS7 → base64，key/iv 取自同一个 bundle。
UPSTREAM_LOGIN_AES_KEY = "VvRSNGFynLBW7aCP"
UPSTREAM_LOGIN_AES_IV = "gLn8sxqpzyNjehDP"
# 邮箱密码登录固定参数（H5 端写死）：sid=4 / login_mode=2。
UPSTREAM_LOGIN_SID = 4
UPSTREAM_LOGIN_MODE_EMAIL_PASSWORD = 2
# 后台下发的账号在这里留一份（0600）：内容源重启后不用等后端再推一次。
UPSTREAM_ACCOUNT_FILE_ENV = "REELSHORT_ACCOUNT_FILE"
DEFAULT_UPSTREAM_ACCOUNT_FILE = "/srv/reelshort/content-provider/.account.json"


def _encrypt_login_password(password: str) -> str:
    """复刻上游登录口令加密：utf8 → zlib → base64 → AES-128-CBC/PKCS7 → base64。

    中间那一层容易被忽略：H5 端先把 deflate 结果转成 base64 **字符串** 再交给 AES，
    而不是把压缩字节直接塞进去。密文与浏览器抓包逐字节一致（见测试向量）。
    """
    if _AES is None or _aes_pad is None:
        return ""
    inner = base64.b64encode(zlib.compress(password.encode("utf-8"))).decode("ascii")
    cipher = _AES.new(UPSTREAM_LOGIN_AES_KEY.encode(), _AES.MODE_CBC, UPSTREAM_LOGIN_AES_IV.encode())
    return base64.b64encode(cipher.encrypt(_aes_pad(inner.encode("ascii"), _AES.block_size))).decode("ascii")


def _decrypt_api_response(text: str) -> dict | None:
    """解密上游的加密响应：AES-128-CBC → base64 → zlib → JSON。

    上游对部分响应（例如带会话校验失败、或 H5 端特有请求）会返回一段 base64 密文
    而不是 JSON；不处理就会当成"上游返回非法 JSON"整体失败。
    解不出来返回 None，调用方继续按错误处理。
    """
    if _AES is None or not text:
        return None
    candidate = text.strip()
    if len(candidate) < 24 or len(candidate) % 4:
        return None
    try:
        raw = base64.b64decode(candidate, validate=True)
        if not raw or len(raw) % 16:
            return None
        plain = _AES.new(
            UPSTREAM_LOGIN_AES_KEY.encode(), _AES.MODE_CBC, UPSTREAM_LOGIN_AES_IV.encode()
        ).decrypt(raw)
        pad = plain[-1]
        if 1 <= pad <= 16:
            plain = plain[:-pad]
        payload = json.loads(zlib.decompress(base64.b64decode(plain)))
    except (ValueError, TypeError, zlib.error, UnicodeDecodeError):
        return None
    return payload if isinstance(payload, dict) else None
def _mask_email(email: str) -> str:
    """后台展示用的脱敏邮箱：`operator@example.com` → `as***@163.com`。"""
    address = (email or "").strip()
    if "@" not in address:
        return "" if not address else address[:2] + "***"
    local, _, domain = address.partition("@")
    prefix = local[:2] if len(local) > 2 else local[:1]
    return f"{prefix}***@{domain}"


def _epoch_to_iso(value) -> str | None:
    """上游时间戳是秒级 epoch；0 或非法一律当"没有"，返回 UTC ISO-8601。"""
    seconds = _as_int(value, 0)
    if seconds <= 0:
        return None
    try:
        moment = datetime.fromtimestamp(seconds, timezone.utc)
    except (OverflowError, OSError, ValueError):
        return None
    return moment.strftime("%Y-%m-%dT%H:%M:%SZ")


def _unknown_vip() -> dict:
    """没拿到会员信息时统一返回的骨架：全部 None，让后台显示"未知"而不是"非会员"。"""
    return {
        "known": False,
        "coins": None,
        "vipActive": None,
        "vipStatus": None,
        "vipExpireAt": None,
        "vipRenew": None,
        "adFreeExpireAt": None,
    }


def _api_sign(payload: dict, secret: str) -> str:
    """复刻上游 H5 请求签名：键升序拼 `k=v`，用 HMAC-SHA256 出十六进制摘要。

    空值与 `null` 不参与签名；对象/数组按紧凑 JSON 序列化后再参与，
    与浏览器端 `Object.keys().sort()` + `JSON.stringify` 的行为一致。
    """
    parts: list[tuple[str, str]] = []
    for name, value in payload.items():
        if value is None:
            continue
        text = json.dumps(value, separators=(",", ":")) if isinstance(value, (dict, list)) else str(value)
        if text in ("", "null"):
            continue
        parts.append((str(name), text))
    parts.sort(key=lambda item: item[0])
    joined = "&".join(f"{name}={value}" for name, value in parts)
    return hmac.new(secret.encode(), joined.encode(), hashlib.sha256).hexdigest()


def _parse_cookie_header(raw: str | None) -> dict[str, str]:
    """把 `Cookie:` 请求头的值解析成字典；缺少等号的片段直接忽略。

    换行必须拒绝：这个值来自环境变量，混入换行等于允许往上游请求里注入额外请求头。
    """
    cookies: dict[str, str] = {}
    if not raw:
        return cookies
    if "\r" in raw or "\n" in raw:
        raise ValueError(f"{UPSTREAM_COOKIE_ENV} must not contain line breaks")
    for part in raw.split(";"):
        name, separator, value = part.partition("=")
        name = name.strip()
        if not name or not separator:
            continue
        cookies[name] = value.strip()
    return cookies


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


class ReelShortClient:
    def __init__(
        self,
        site_url: str,
        site_id: str,
        timeout_seconds: float,
        build_id: str | None = None,
        diagnostics: ProviderDiagnostics | None = None,
        max_response_bytes: int = DEFAULT_MAX_RESPONSE_BYTES,
        max_redirects: int = DEFAULT_MAX_REDIRECTS,
        resolver=None,
        monotonic=None,
        cookies: dict[str, str] | None = None,
        email: str | None = None,
        password: str | None = None,
        api_secret: str | None = None,
        channel_id: str | None = None,
        account_file: str | None = None,
    ):
        self.site_url = site_url.rstrip("/")
        self.site_id = site_id
        self.timeout_seconds = timeout_seconds
        # 上游登录态 Cookie：为空时匿名请求，行为与未引入该能力前逐字一致。
        self.cookies = dict(cookies or {})
        # 上游账号：同时配了邮箱与密码才会尝试登录；缺一个就保持匿名。
        self.email = (email or "").strip()
        self.password = password or ""
        self.account_file = account_file
        # 后台改过的账号优先于环境变量：环境变量只是首次部署的引导值。
        persisted = self._read_persisted_account()
        if persisted is not None:
            self.email, self.password = persisted
        self.api_secret = api_secret or DEFAULT_UPSTREAM_API_SECRET
        self.channel_id = channel_id or DEFAULT_UPSTREAM_CHANNEL
        self.session_token = ""
        self.uid = "0"
        # 游客会话（session_token 非空）不等于登录成功，只有登录成功才置真。
        self.logged_in = False
        self._login_lock = threading.RLock()
        # 每次登录都会带一个新的设备号；进程内保持不变，避免上游把会话和设备绑定判为异常。
        self._device_id = "".join(
            random.choice("abcdefghijklmnopqrstuvwxyz0123456789") for _ in range(12)
        ) + str(int(time.time() * 1000))
        # An empty build id means "discover it", not "pin the empty string".
        # `REELSHORT_NEXT_BUILD_ID=` in an env file yields `""`, and treating
        # that as a pin disables the 404-triggered build-id refresh: every shelf
        # whose upstream JSON disappeared then failed permanently with 404.
        build_id = build_id or None
        self.build_id = build_id
        self.fixed_build_id = build_id is not None
        self.diagnostics = diagnostics or ProviderDiagnostics()
        self.max_response_bytes = max_response_bytes
        self.max_redirects = max_redirects
        if not math.isfinite(timeout_seconds) or timeout_seconds <= 0:
            raise ValueError("timeout_seconds must be finite and greater than zero")
        if max_response_bytes <= 0:
            raise ValueError("max_response_bytes must be greater than zero")
        if max_redirects < 0:
            raise ValueError("max_redirects must be zero or greater")
        self.resolver = resolver or self._resolve_addresses
        self.monotonic = monotonic or time.monotonic

    @classmethod
    def from_env(cls):
        try:
            timeout_seconds = float(os.getenv("REELSHORT_REQUEST_TIMEOUT_SECONDS", "10"))
        except (TypeError, ValueError) as exception:
            raise ValueError("REELSHORT_REQUEST_TIMEOUT_SECONDS must be numeric") from exception
        try:
            max_response_bytes = int(os.getenv("REELSHORT_MAX_RESPONSE_BYTES", str(DEFAULT_MAX_RESPONSE_BYTES)))
        except (TypeError, ValueError) as exception:
            raise ValueError("REELSHORT_MAX_RESPONSE_BYTES must be an integer") from exception
        try:
            max_redirects = int(os.getenv("REELSHORT_MAX_REDIRECTS", str(DEFAULT_MAX_REDIRECTS)))
        except (TypeError, ValueError) as exception:
            raise ValueError("REELSHORT_MAX_REDIRECTS must be an integer") from exception
        return cls(
            # P3-23：上游站点地址必须与"本平台公网域名"区分开——后端用 REELSHORT_SITE_URL 表示
            # 自己的站点，若原样传给内容源，内容源会去抓自己（拿不到 Next.js buildId，全站内容 502）。
            # 因此这里优先读 REELSHORT_UPSTREAM_SITE_URL，仅为兼容旧部署才回退到 REELSHORT_SITE_URL。
            os.getenv("REELSHORT_UPSTREAM_SITE_URL") or os.getenv("REELSHORT_SITE_URL", "https://www.reelshort.com"),
            os.getenv("REELSHORT_SITE_ID", "37"),
            timeout_seconds,
            os.getenv("REELSHORT_NEXT_BUILD_ID") or None,
            max_response_bytes=max_response_bytes,
            max_redirects=max_redirects,
            cookies=_parse_cookie_header(os.getenv(UPSTREAM_COOKIE_ENV)),
            email=os.getenv(UPSTREAM_EMAIL_ENV),
            password=os.getenv(UPSTREAM_PASSWORD_ENV),
            api_secret=os.getenv(UPSTREAM_API_SECRET_ENV) or DEFAULT_UPSTREAM_API_SECRET,
            channel_id=os.getenv(UPSTREAM_CHANNEL_ENV) or DEFAULT_UPSTREAM_CHANNEL,
            account_file=os.getenv(UPSTREAM_ACCOUNT_FILE_ENV, DEFAULT_UPSTREAM_ACCOUNT_FILE),
        )

    @property
    def authenticated(self) -> bool:
        return bool(self.cookies) or self.logged_in

    @property
    def account_configured(self) -> bool:
        """配了邮箱和密码才算"能登录"；只配一个按未配置处理。"""
        return bool(self.email and self.password)

    def session_fingerprint(self) -> str:
        """当前 session 的稳定短指纹（不泄露 token 内容）。"""
        if not self.session_token:
            return ""
        return hashlib.sha256(self.session_token.encode("utf-8")).hexdigest()[:12]

    def set_account(self, email: str, password: str) -> None:
        """后台下发的账号：覆盖环境变量里的初始值，并作废当前登录态。

        密码只留在进程内存（以及 0600 的本地副本）里，不落日志、不回显。
        """
        self.email = (email or "").strip()
        self.password = password or ""
        self.session_token = ""
        self.uid = "0"
        self.logged_in = False
        self._write_persisted_account()

    def _read_persisted_account(self):
        """读取上次后台下发的账号；读不到（首次部署/无文件）返回 None。"""
        if not self.account_file:
            return None
        try:
            with open(self.account_file, encoding="utf-8") as handle:
                payload = json.load(handle)
        except (OSError, ValueError):
            return None
        email = str(payload.get("email") or "").strip()
        password = payload.get("password")
        if not email or not isinstance(password, str) or not password:
            return None
        return email, password

    def _write_persisted_account(self) -> None:
        if not self.account_file:
            return
        try:
            directory = os.path.dirname(self.account_file)
            if directory:
                os.makedirs(directory, exist_ok=True)
            payload = json.dumps({"email": self.email, "password": self.password})
            descriptor = os.open(self.account_file, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
            with os.fdopen(descriptor, "w", encoding="utf-8") as handle:
                handle.write(payload)
        except OSError as exception:
            # 写不下去不影响本次登录，只是重启后要等后端再推一次。
            self.diagnostics.record("upstream_account_file_write_failed", message=_truncate(str(exception), 120))

    def account_status(self) -> dict:
        """给后台看的登录态摘要（邮箱脱敏、session 只给指纹）。"""
        return {
            "configured": self.account_configured,
            "emailMasked": _mask_email(self.email),
            "authenticated": self.authenticated,
            "loggedIn": self.logged_in,
            "sessionFingerprint": self.session_fingerprint(),
        }

    def account_vip(self) -> dict:
        """上游账号的会员状态与到期时间（一次 `getUserInfo`）。

        后台最关心的两件事：现在是不是会员、会员到哪天到期。上游把这两项放在
        `user_info.account.vip_status` / `vip_expire`（秒级时间戳）里，
        免费账号两者都是 0。查不到时返回 `known=False`，不冒充"非会员"。
        """
        if not self.ensure_session():
            return _unknown_vip()
        try:
            payload = self._api_call("POST", UPSTREAM_USER_INFO_PATH, body={"target_uid": self.uid})
        except UpstreamError as exception:
            self.diagnostics.record(
                "upstream_user_info_failed",
                status_code=exception.status_code,
                message=_truncate(exception.message, 120),
            )
            return _unknown_vip()
        account = _as_dict(_as_dict(_as_dict(payload.get("data")).get("user_info")).get("account"))
        if not account:
            return _unknown_vip()
        expire_at = _epoch_to_iso(account.get("vip_expire"))
        status = _as_int(account.get("vip_status"), 0)
        # vip_status 是上游的即时判定；没有到期时间时以它为准，避免把长期会员显示成非会员。
        active = _as_int(account.get("vip_expire"), 0) > time.time() if expire_at else status > 0
        return {
            "known": True,
            "coins": _as_int(account.get("coins"), 0),
            "vipActive": bool(active),
            "vipStatus": status,
            "vipExpireAt": expire_at,
            "vipRenew": _as_int(account.get("is_vip_renew"), 0) > 0,
            "adFreeExpireAt": _epoch_to_iso(account.get("ad_free_expiration_time")),
        }

    def _bootstrap_session(self) -> bool:
        """建立匿名访客会话。

        上游多数接口（包括登录、发验证码、查邮箱）都要求先带一个 session，
        否则统一回 `101 ERROR`。浏览器的顺序也是先进站拿 guest session 再登录。
        """
        try:
            payload = self._api_call(
                "POST",
                UPSTREAM_LOGIN_PATH,
                body={"email": "", "password": "", "dev_model": "h5", "sid": 0},
                auth=False,
            )
        except UpstreamError as exception:
            self.diagnostics.record(
                "upstream_guest_session_failed",
                status_code=exception.status_code,
                message=_truncate(exception.message, 120),
            )
            return False
        data = _as_dict(payload.get("data"))
        token = str(data.get("session") or "")
        if not token:
            return False
        self.session_token = token
        self.uid = str(_as_dict(data.get("user_info")).get("uid") or 0)
        return True

    def login(self) -> bool:
        """用环境变量里的账号登录上游，成功后带上 session/uid。

        先建匿名会话再登录（与浏览器一致）；登录失败不抛异常：
        抓取继续按匿名窗口工作，只记一条诊断事件。
        """
        if not self.account_configured:
            return False
        with self._login_lock:
            encrypted = _encrypt_login_password(self.password)
            if not encrypted:
                self.diagnostics.record("upstream_login_unsupported", reason="crypto library unavailable")
                return False
            if not self.session_token and not self._bootstrap_session():
                self.diagnostics.record("upstream_login_skipped", reason="no guest session")
                return False
            body = {
                "sid": UPSTREAM_LOGIN_SID,
                "login_mode": UPSTREAM_LOGIN_MODE_EMAIL_PASSWORD,
                "login_email": self.email,
                "login_pass": encrypted,
                "dev_model": "h5",
            }
            try:
                payload = self._api_call("POST", UPSTREAM_LOGIN_PATH, body=body, auth=True)
            except UpstreamError as exception:
                self.diagnostics.record(
                    "upstream_login_failed",
                    email=_truncate(self.email, 40),
                    message=_truncate(exception.message, 120),
                )
                return False
            data = _as_dict(payload.get("data"))
            token = str(data.get("session") or "")
            user_info = _as_dict(data.get("user_info"))
            user_type = _as_int(user_info.get("sid"))
            if payload.get("code") != 0 or not token or user_type == 0:
                # 上游对"口令不对/账号异常"也会回 code=0，但给的是游客身份（sid=0）。
                # 这种会话不能算登录成功，否则会出现"以为登录了、锁集仍 404"的假象。
                self.diagnostics.record(
                    "upstream_login_rejected",
                    email=_truncate(self.email, 40),
                    code=_truncate(payload.get("code"), 32),
                    message=_truncate(payload.get("msg"), 120),
                    user_type=user_type,
                )
                return False
            self.session_token = token
            self.uid = str(user_info.get("uid") or 0)
            self.logged_in = True
            self.diagnostics.record(
                "upstream_login_ok",
                uid=self.uid,
                session_fingerprint=self.session_fingerprint(),
            )
            return True

    def ensure_session(self) -> bool:
        """保证有可用的 session；没有就登录一次。匿名模式返回 False。"""
        if not self.account_configured:
            return False
        if self.session_token:
            return True
        return self.login()

    def chapters(self, book_id: str) -> dict:
        """整季分集 + 每集当前是否解锁（`play_info` 非空即已解锁）。

        免费账号也能调用：它对锁集返回的 `play_info` 是 null，
        所以一次请求就能知道这本书的可播窗口，不必逐集探测。
        """
        self.ensure_session()
        collected: list[dict] = []
        chapter_count = 0
        page = 1
        while True:
            payload = self._api_call(
                "GET",
                UPSTREAM_CHAPTER_LIST_PATH,
                params={"book_id": book_id, "page": page, "page_size": UPSTREAM_CHAPTER_PAGE_SIZE},
            )
            data = _as_dict(payload.get("data"))
            chapter_count = _as_int(data.get("chapter_count"), chapter_count)
            page_items = _as_list(data.get("chapter_list"))
            for item in page_items:
                chapter = _as_dict(item)
                episode = _as_int(chapter.get("serial_number"))
                collected.append(
                    {
                        "episode": episode,
                        "chapter_id": str(chapter.get("chapter_id") or ""),
                        "duration": _as_int(chapter.get("duration")),
                        "playable": bool(chapter.get("play_info")),
                    }
                )
            if len(page_items) < UPSTREAM_CHAPTER_PAGE_SIZE or page >= 20:
                break
            page += 1
        collected.sort(key=lambda item: item["episode"])
        playable = [item["episode"] for item in collected if item["playable"]]
        return {
            "book_id": book_id,
            "chapter_count": chapter_count or len(collected),
            "authenticated": self.authenticated,
            "playable_episodes": playable,
            "locked_from": playable[-1] + 1 if playable else (1 if collected else None),
            "chapters": collected,
        }

    def chapter_info(self, book_id: str, chapter_id: str) -> dict:
        """单集详情（登录态下带上用户权益）：会员账号的锁集会在这里给出 video_url。"""
        self.ensure_session()
        payload = self._api_call(
            "GET", UPSTREAM_CHAPTER_INFO_PATH, params={"book_id": book_id, "chapter_id": chapter_id}
        )
        return _as_dict(payload.get("data"))

    def _unlocked_video_url(self, book_id: str, chapter_id: str) -> str:
        """锁集兜底：用账号权益再问一次分集接口，拿不到就返回空串（保持 404 语义）。"""
        if not self.account_configured:
            return ""
        try:
            info = self.chapter_info(book_id, chapter_id)
        except UpstreamError as exception:
            self.diagnostics.record(
                "chapter_info_unavailable",
                book_id=book_id,
                chapter_id=chapter_id,
                status_code=exception.status_code,
            )
            return ""
        url = str(info.get("video_url") or "")
        if url:
            self.diagnostics.record("video_unlocked_via_account", book_id=book_id, chapter_id=chapter_id)
        return url

    def cookie_fingerprint(self) -> str:
        """配置的 Cookie 的稳定短指纹，用来核对"换的是哪一套"而不泄露内容。"""
        if not self.cookies:
            return ""
        raw = "; ".join(f"{name}={value}" for name, value in self.cookies.items())
        return hashlib.sha256(raw.encode("utf-8")).hexdigest()[:12]

    def search(self, keywords: str, locale: str = DEFAULT_LOCALE):
        payload = self._get_locale_data("/search.json", locale=locale, params={"keywords": keywords})
        books = self._books(payload)
        if not books:
            self.diagnostics.record("search_empty", keywords=keywords, locale=locale)
        return [self._map_book(book) for book in books]

    def shelf(self, shelf_name: str, locale: str = DEFAULT_LOCALE):
        books = []
        try:
            payload = self._get_locale_data(f"/{shelf_name}.json", locale=locale)
            books = self._books(payload)
        except UpstreamError as exception:
            if exception.status_code != 404:
                raise
        if not books and shelf_name in {"recommend", "newrelease", "dramadub"}:
            books = self._home_shelf_books(shelf_name, locale)
        if shelf_name == "recommend":
            books = self._expanded_recommend_books(books, locale)
        return [self._map_book(book) for book in books]

    def episodes(self, book_id: str, filtered_title: str, locale: str = DEFAULT_LOCALE):
        try:
            page_props = _as_dict(self._movie_payload(book_id, filtered_title, locale).get("pageProps"))
            book_data = _as_dict(page_props.get("data"))
        except UpstreamError as exception:
            if exception.status_code != 404:
                raise
            book_data = self._book_info(book_id)
        return {
            "book": self._map_book(self._with_book_context(book_id, filtered_title, book_data)),
            "episodes": self._map_chapters(
                _as_list(book_data.get("online_base")),
                fallback_description=self._first_text(
                    book_data, ("description", "introduction", "book_intro", "summary", "special_desc", "desc")
                ),
            ),
        }

    @staticmethod
    def _with_book_context(book_id: str, filtered_title: str, book_data: dict):
        view = dict(_as_dict(book_data))
        view.setdefault("book_id", book_id)
        if not view.get("book_id"):
            view["book_id"] = book_id
        view.setdefault("filtered_title", filtered_title)
        if not view.get("filtered_title"):
            view["filtered_title"] = filtered_title
        if not view.get("chapter_count") and isinstance(view.get("online_base"), list):
            view.setdefault("chapter_count", len(view["online_base"]))
        return view

    def video(
        self,
        book_id: str,
        episode_num: int,
        filtered_title: str,
        chapter_id: str,
        locale: str = DEFAULT_LOCALE,
    ):
        slug = f"episode-{episode_num}-{filtered_title}-{book_id}-{chapter_id}"
        try:
            payload = self._get_locale_data(
                f"/episodes/{quote(slug, safe='')}.json",
                locale=locale,
                params={"play_time": "1", "slug": slug},
            )
        except UpstreamError as exception:
            if exception.status_code != 404:
                raise
            payload = self._episode_html_payload(slug)
        episode_data = _as_dict(_as_dict(payload).get("pageProps")).get("data")
        episode_data = _as_dict(episode_data)
        video_url = str(episode_data.get("video_url") or "")
        if not video_url:
            # 页面数据只反映匿名可见窗口；配了账号时再问一次分集接口，
            # 会员权益下的锁集会在那里给出真正的 video_url。
            video_url = self._unlocked_video_url(book_id, chapter_id)
        if not video_url:
            self.diagnostics.record(
                "video_url_missing",
                book_id=book_id,
                episode_num=episode_num,
                filtered_title=filtered_title,
                chapter_id=chapter_id,
                # 区分"我们没登录"和"登录了但这集仍锁着"：后者说明登录态有效，
                # 是账号权益不够，不是配置问题。
                authenticated=self.authenticated,
            )
            raise UpstreamError(404, "upstream not found")

        # P2-6：next_episode 只是播放页的便利元数据，上游分集接口失败不能连带已取得的 video_url 一起失败。
        try:
            chapters = _as_list(self.episodes(book_id, filtered_title, locale).get("episodes"))
        except UpstreamError as exception:
            self.diagnostics.record(
                "next_episode_unavailable",
                book_id=book_id,
                episode_num=episode_num,
                status_code=exception.status_code,
                message=exception.message,
            )
            chapters = []
        next_chapter = None
        for index, chapter in enumerate(chapters):
            if _as_int(_as_dict(chapter).get("episode")) == episode_num and index + 1 < len(chapters):
                next_chapter = chapters[index + 1]
                break
        return {
            "video_url": video_url,
            "episode": _as_int(episode_data.get("serial_number"), episode_num) or episode_num,
            "duration": _as_int(episode_data.get("duration")),
            "next_episode": next_chapter,
        }

    def probe_episode(self, book_id: str, filtered_title: str, episode_num: int, locale: str = DEFAULT_LOCALE):
        """探测某一集当前能不能取到播放地址，用来验证登录态 Cookie 是否还在生效。

        不抛异常：探测失败本身就是结果的一部分（Cookie 过期、账号权益不足、上游抖动）。
        """
        try:
            episodes = _as_list(self.episodes(book_id, filtered_title, locale).get("episodes"))
        except UpstreamError as exception:
            return {"episode": episode_num, "playable": False, "reason": f"episodes unavailable ({exception.status_code})"}
        chapter = next(
            (item for item in episodes if _as_int(_as_dict(item).get("episode")) == episode_num),
            None,
        )
        if chapter is None:
            return {"episode": episode_num, "playable": False, "reason": "episode not found upstream"}
        chapter_id = str(_as_dict(chapter).get("chapter_id") or "")
        try:
            video = self.video(book_id, episode_num, filtered_title, chapter_id, locale)
        except UpstreamError as exception:
            return {"episode": episode_num, "playable": False, "reason": exception.message}
        return {
            "episode": episode_num,
            "playable": bool(video.get("video_url")),
            "duration": video.get("duration"),
        }

    def _movie_payload(self, book_id: str, filtered_title: str, locale: str = DEFAULT_LOCALE):
        slug = f"{filtered_title}-{book_id}"
        try:
            return self._get_locale_data(f"/movie/{quote(slug, safe='')}.json", locale=locale, params={"slug": slug})
        except UpstreamError as exception:
            if locale == DEFAULT_LOCALE or exception.status_code != 404:
                raise
            return self._get_data(
                f"/movie/{quote(slug, safe='')}.json",
                params={"slug": slug},
            )

    def _book_info_chapters(self, book_id: str):
        return _as_list(self._book_info(book_id).get("online_base"))

    def _book_info(self, book_id: str):
        payload = _as_dict(self._get("/api/video/book/getBookInfo", params={"book_id": book_id}))
        code = payload.get("code")
        if code != 0:
            # P2-6：上游 code != 0 既可能是“资源不存在”，也可能是上游内部错误。
            # 统一映射为 502 并记录诊断事件，让后端按 5xx 走缓存兜底，而不是把错误结论固化成 404。
            self.diagnostics.record(
                "book_info_upstream_error",
                book_id=book_id,
                code=_truncate(code, 32),
                message=_truncate(payload.get("msg") or payload.get("message"), 120),
            )
            raise UpstreamError(502, "upstream request failed")
        return _as_dict(payload.get("data"))

    def _episode_html_payload(self, slug: str):
        html = self._get_text(f"/episodes/{quote(slug, safe='')}", params={"play_time": "1"})
        match = re.search(
            r'<script id="__NEXT_DATA__" type="application/json">(.*?)</script>',
            html,
            re.DOTALL,
        )
        if not match:
            self.diagnostics.record("episode_html_missing_next_data", slug=slug)
            raise UpstreamError(404, "upstream not found")
        try:
            next_data = json.loads(unescape(match.group(1)))
        except json.JSONDecodeError as exception:
            self.diagnostics.record("episode_html_invalid_json", slug=slug)
            raise UpstreamError(502, "upstream returned invalid json") from exception
        return next_data.get("props", {})

    def _get_data(self, data_path: str, params=None):
        build_id = self.build_id or self._discover_build_id()
        try:
            return self._get(f"/_next/data/{quote(build_id, safe='')}/{self.site_id}{data_path}", params=params)
        except UpstreamError as exception:
            if exception.status_code != 404 or self.fixed_build_id:
                raise
            self.diagnostics.record("next_data_404", data_path=data_path)
            self.build_id = None
            fresh_build_id = self._discover_build_id()
            return self._get(
                f"/_next/data/{quote(fresh_build_id, safe='')}/{self.site_id}{data_path}",
                params=params,
            )

    def _get(self, path: str, params=None):
        response = self._request(f"{self.site_url}{path}", params=params)

        if response.status_code == 404:
            raise UpstreamError(404, "upstream not found")
        if not response.ok:
            raise UpstreamError(502, "upstream request failed")
        try:
            return response.json()
        except requests.JSONDecodeError as exception:
            raise UpstreamError(502, "upstream returned invalid json") from exception

    def _get_text(self, path: str, params=None):
        response = self._request(f"{self.site_url}{path}", params=params)

        if response.status_code == 404:
            raise UpstreamError(404, "upstream not found")
        if not response.ok:
            raise UpstreamError(502, "upstream request failed")
        return response.text

    def _api_common(self) -> dict:
        """上游 H5 接口的公共参数：既参与签名，也逐项放进请求头。"""
        return {
            "apiVersion": UPSTREAM_API_VERSION,
            "channelId": self.channel_id,
            "clientVer": UPSTREAM_CLIENT_VERSION,
            "devId": self._device_id,
            "lang": DEFAULT_LOCALE,
            "ts": int(time.time()),
            "session": self.session_token,
            "uid": self.uid,
        }

    def _api_call(self, method: str, path: str, params=None, body=None, auth: bool = True,
            _retried: bool = False) -> dict:
        """签名调用上游 H5 接口；session 过期时自动重登一次再重试。"""
        common = self._api_common()
        if not auth:
            common["session"] = ""
            common["uid"] = "0"
        signed = {**(body or {}), **(params or {}), **common}
        extra_headers = {name: str(value) for name, value in common.items()}
        extra_headers["sign"] = _api_sign(signed, self.api_secret)
        url = f"{self.site_url}{path}"
        if method == "GET":
            response = self._request(url, params=params, extra_headers=extra_headers)
        else:
            extra_headers["Content-Type"] = "application/json"
            response = self._request(url, method="POST", json_body=body or {}, extra_headers=extra_headers)
        if not response.ok:
            self.diagnostics.record("upstream_api_http_error", path=path, status_code=response.status_code)
            raise UpstreamError(502, "upstream request failed")
        text = response.text or ""
        payload = None
        if text.lstrip().startswith("{"):
            try:
                payload = response.json()
            except requests.JSONDecodeError:
                payload = None
        if not isinstance(payload, dict):
            # 上游有时直接回一段 base64 密文（见 `_decrypt_api_response`）。
            payload = _decrypt_api_response(text)
        if not isinstance(payload, dict):
            self.diagnostics.record("upstream_api_invalid_json", path=path, bytes=len(text))
            raise UpstreamError(502, "upstream returned invalid json")
        if (
            payload.get("code") == UPSTREAM_SESSION_EXPIRED_CODE
            and auth
            and not _retried
            and self.account_configured
        ):
            self.diagnostics.record("upstream_session_expired", path=path)
            self.session_token = ""
            self.uid = "0"
            self.logged_in = False
            if self.login():
                return self._api_call(method, path, params=params, body=body, auth=auth, _retried=True)
        return payload

    def _discover_build_id(self):
        response = self._request(f"{self.site_url}/{quote(self.site_id, safe='')}")
        match = re.search(r'"buildId":"([^"]+)"', response.text)
        if not match:
            match = re.search(rf'/{re.escape(self.site_id)}/_next/data/([^/]+)/', response.text)
        if not match:
            response = self._request(f"{self.site_url}/")
            if not response.ok:
                raise UpstreamError(502, "upstream request failed")
            match = re.search(r'"buildId":"([^"]+)"', response.text)
        if not match:
            raise UpstreamError(502, "upstream build id not found")
        self.build_id = match.group(1)
        return self.build_id

    def _request(self, url: str, params=None, *, method: str = "GET", json_body=None, extra_headers=None):
        # M11: _validate_url 现在返回 (pinned_url, original_hostname) 用于 DNS pinning
        pinned_url, _ = self._validate_url(url, "upstream_url_blocked", "upstream returned unsafe upstream URL")
        deadline = self.monotonic() + self.timeout_seconds
        current_url = pinned_url
        original_current_url = url
        current_params = params
        visited = {url}
        headers = {"Host": self._host_header(url), "User-Agent": UPSTREAM_USER_AGENT}
        if extra_headers:
            headers.update({name: str(value) for name, value in extra_headers.items()})
        if self.cookies:
            # 只在配置了登录态时发送，匿名请求的 Header 集合与之前完全一致。
            headers["Cookie"] = "; ".join(f"{name}={value}" for name, value in self.cookies.items())

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

    def _home_shelf_books(self, shelf_name: str, locale: str = DEFAULT_LOCALE):
        payload = self._get_locale_data(".json", locale=locale)
        page_props = _as_dict(payload.get("pageProps"))
        fallback = _as_dict(page_props.get("fallback"))
        web_info = _as_dict(fallback.get("/api/ms/hall/webInfo"))
        shelves = _as_list(web_info.get("bookShelfList"))
        selected_shelves = self._select_home_shelves(shelves, shelf_name)
        books = []
        seen = set()
        for shelf in selected_shelves:
            for book in _as_list(_as_dict(shelf).get("books")):
                book = _as_dict(book)
                book_id = book.get("book_id") or book.get("_id") or book.get("id")
                if book_id and book_id not in seen:
                    seen.add(book_id)
                    books.append(book)
        return books

    def _get_locale_data(self, data_path: str, locale: str = DEFAULT_LOCALE, params=None):
        build_id = self.build_id or self._discover_build_id()
        try:
            return self._get(f"/_next/data/{quote(build_id, safe='')}/{quote(locale, safe='-')}{data_path}", params=params)
        except UpstreamError as exception:
            if exception.status_code != 404 or self.fixed_build_id:
                raise
            self.diagnostics.record("next_data_404", data_path=data_path, locale=locale)
            self.build_id = None
            fresh_build_id = self._discover_build_id()
            return self._get(
                f"/_next/data/{quote(fresh_build_id, safe='')}/{quote(locale, safe='-')}{data_path}",
                params=params,
            )

    def _expanded_recommend_books(self, base_books, locale: str = DEFAULT_LOCALE):
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
        search_pages = self._catalog_search_pages(self._catalog_search_keywords(locale), max_pages, locale)
        for search_books in search_pages:
            for book in search_books:
                if self._append_unique_book(books, seen, book, max_books):
                    return books
        return books

    def _catalog_search_pages(self, keywords, max_pages: int, locale: str = DEFAULT_LOCALE):
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
            return self._catalog_search_keywords_sequential(keywords, max_pages, locale)
        return self._catalog_search_keywords_parallel(keywords, max_pages, workers, locale)

    def _catalog_search_keywords_sequential(self, keywords, max_pages: int, locale: str = DEFAULT_LOCALE):
        return [
            pages
            for keyword in keywords
            for pages in self._catalog_search_keyword_pages(keyword, max_pages, locale)
        ]

    def _catalog_search_keywords_parallel(self, keywords, max_pages: int, workers: int, locale: str = DEFAULT_LOCALE):
        results = {}
        with ThreadPoolExecutor(max_workers=workers) as executor:
            futures = {
                executor.submit(self._catalog_search_keyword_pages, keyword, max_pages, locale): keyword_index
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

    def _catalog_search_keyword_pages(self, keyword: str, max_pages: int, locale: str = DEFAULT_LOCALE):
        pages = []
        for page in range(1, max_pages + 1):
            books = self._fetch_catalog_search_books(keyword, page, locale)
            if not books:
                break
            pages.append(books)
        return pages

    def _fetch_catalog_search_books(self, keyword: str, page: int, locale: str = DEFAULT_LOCALE):
        params = {"keywords": keyword}
        if page > 1:
            params["page"] = page
        try:
            payload = self._get_locale_data("/search.json", locale=locale, params=params)
        except UpstreamError:
            return None
        books = self._books(payload)
        if not books:
            self.diagnostics.record("catalog_search_empty", keywords=keyword, page=page, locale=locale)
        return books

    def _append_unique_book(self, books, seen, book, max_books: int):
        book_id = self._book_id(book)
        if not book_id or book_id in seen:
            return len(books) >= max_books
        seen.add(book_id)
        books.append(book)
        return len(books) >= max_books

    def _catalog_search_keywords(self, locale: str = DEFAULT_LOCALE):
        raw_keywords = os.getenv(
            "REELSHORT_CATALOG_SEARCH_KEYWORDS_EN",
            os.getenv("REELSHORT_CATALOG_SEARCH_KEYWORDS", ""),
        )
        defaults = list(DEFAULT_CATALOG_SEARCH_KEYWORDS)
        keywords = [keyword.strip() for keyword in raw_keywords.split(",") if keyword.strip()]
        return (keywords or defaults)[:MAX_CATALOG_KEYWORDS]

    def _bounded_int_env(self, name: str, default: int, maximum: int):
        value = _as_int(os.getenv(name), default)
        return min(max(value, 0), maximum)

    def _select_home_shelves(self, shelves, shelf_name: str):
        shelves = [_as_dict(shelf) for shelf in _as_list(shelves)]
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
        page_props = _as_dict(_as_dict(payload).get("pageProps"))
        books = page_props.get("books")
        if books is None:
            books = page_props.get("bookList")
        return _as_list(books)

    def _map_book(self, book):
        book = _as_dict(book)
        title = book.get("book_title") or book.get("title") or ""
        book_id = self._book_id(book)
        return {
            "book_id": book_id,
            "book_title": title,
            "filtered_title": book.get("filtered_title")
            or self._filtered_title(book.get("title_en") or book.get("slug") or title)
            or (f"book-{book_id}" if book_id else ""),
            "book_pic": book.get("book_pic") or book.get("cover") or "",
            "description": self._first_text(
                book, ("description", "introduction", "book_intro", "summary", "special_desc", "desc")
            ),
            "chapter_count": _as_int(book.get("chapter_count")),
        }

    def _map_chapters(self, chapters, fallback_description: str = ""):
        mapped = []
        for chapter in _as_list(chapters):
            chapter = _as_dict(chapter)
            serial_number = _as_int(chapter.get("serial_number"))
            if not chapter.get("chapter_id") or serial_number <= 0:
                continue
            mapped.append(
                {
                    "episode": serial_number,
                    "chapter_id": chapter.get("chapter_id", ""),
                    "title": self._first_text(chapter, ("chapter_title", "chapter_name", "title", "name")),
                    "description": self._first_text(
                        chapter, ("description", "intro", "summary", "chapter_desc", "desc")
                    )
                    or fallback_description,
                }
            )
        return mapped

    def _book_id(self, book):
        book = _as_dict(book)
        return book.get("book_id") or book.get("_id") or book.get("id") or ""

    def _first_text(self, payload, keys):
        payload = _as_dict(payload)
        for key in keys:
            value = payload.get(key)
            if isinstance(value, str) and value.strip():
                return value.strip()
        return ""

    def _filtered_title(self, title: str):
        value = re.sub(r"[^a-z0-9]+", "-", title.lower()).strip("-")
        return value


def create_app(client=None, internal_token=None) -> Flask:
    app = Flask(__name__)
    reelshort_client = client or ReelShortClient.from_env()
    expected_token = internal_token if internal_token is not None else os.getenv(INTERNAL_TOKEN_ENV, "")
    # 配了账号就绪前先登录一次：让"已配置"和"已登录"在启动后就是同一个状态，
    # 而不是等到第一集锁集播放才懒加载。登录失败只记诊断，不影响匿名抓取。
    if getattr(reelshort_client, "account_configured", False):
        try:
            reelshort_client.ensure_session()
        except Exception:  # noqa: BLE001 - 启动期任何登录异常都不该拦住服务
            pass

    @app.get("/health")
    def health():
        return jsonify({"status": "UP", "service": "reelshort-content-provider"})

    @app.before_request
    def require_internal_token():
        # P2-16：/health 保持匿名可达（容器健康检查），其余端点要求内部共享密钥。
        if request.path == "/health":
            return None
        if not expected_token:
            # fail-closed：未配置内部密钥时拒绝除 /health 之外的全部请求。
            return jsonify({"error": "content provider internal token is not configured"}), 503
        provided = request.headers.get(INTERNAL_TOKEN_HEADER, "")
        if not provided:
            return jsonify({"error": "missing internal token"}), 401
        if not hmac.compare_digest(provided, expected_token):
            return jsonify({"error": "invalid internal token"}), 403
        return None

    @app.get("/diagnostics")
    def diagnostics():
        snapshot = getattr(reelshort_client, "diagnostics", ProviderDiagnostics()).snapshot()
        return jsonify({
            "status": "UP",
            "service": "reelshort-content-provider",
            "diagnostics": snapshot,
        })

    @app.errorhandler(UpstreamError)
    def upstream_error(error: UpstreamError):
        return jsonify({"error": error.message}), error.status_code

    @app.errorhandler(Exception)
    def unexpected_error(error):
        # P2-16：未预期异常记录诊断事件并返回 502，而不是裸 500 栈。
        diagnostics_recorder = getattr(reelshort_client, "diagnostics", None)
        if diagnostics_recorder is not None:
            diagnostics_recorder.record(
                "unexpected_error",
                error_type=type(error).__name__,
                message=_truncate(error, 200),
                path=request.path,
            )
        app.logger.exception("unexpected content provider error")
        return jsonify({"error": "content provider internal error"}), 502

    def request_locale():
        locale = request.args.get("locale", DEFAULT_LOCALE).strip() or DEFAULT_LOCALE
        if locale not in SUPPORTED_LOCALES:
            return None
        return locale

    def locale_error():
        return jsonify({"error": "unsupported locale"}), 400

    @app.get("/api/v1/reelshort/search")
    def search():
        locale = request_locale()
        if locale is None:
            return locale_error()
        keywords = request.args.get("keywords", "").strip()
        if not keywords:
            return jsonify({"error": "keywords is required"}), 400
        return jsonify({"results": reelshort_client.search(keywords, locale)})

    def shelf_response(shelf_name: str):
        locale = request_locale()
        if locale is None:
            return locale_error()
        return jsonify({"books": reelshort_client.shelf(shelf_name, locale)})

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
        locale = request_locale()
        if locale is None:
            return locale_error()
        filtered_title = request.args.get("filtered_title", "").strip()
        if not filtered_title:
            return jsonify({"error": "filtered_title is required"}), 400
        return jsonify(reelshort_client.episodes(book_id, filtered_title, locale))

    @app.get("/api/v1/reelshort/session")
    def session_status():
        """上游登录态状态页：账号是否已登录、账号身份，以及（可选）指定集能不能播。

        带 `account=1` 时先用环境变量里的账号登录一次（幂等），返回真实登录结果；
        带 `book_id` + `filtered_title` + `episode_num` 时做一次真实探测，
        用来确认账号权益是否已经能解锁锁集。
        """
        locale = request_locale()
        if locale is None:
            return locale_error()
        login_attempt = None
        if request.args.get("account", "").strip() in {"1", "true", "yes"}:
            login_attempt = reelshort_client.login()
        payload = {
            "authenticated": reelshort_client.authenticated,
            "accountConfigured": reelshort_client.account_configured,
            "loginAttempt": login_attempt,
            "sessionFingerprint": reelshort_client.session_fingerprint(),
            "cookieFingerprint": reelshort_client.cookie_fingerprint(),
        }
        book_id = request.args.get("book_id", "").strip()
        filtered_title = request.args.get("filtered_title", "").strip()
        episode_num = request.args.get("episode_num", "").strip()
        if book_id and filtered_title and episode_num.isdigit():
            payload["probe"] = reelshort_client.probe_episode(book_id, filtered_title, int(episode_num), locale)
        return jsonify(payload)

    @app.post("/api/v1/reelshort/credentials")
    def update_credentials():
        """后台下发 ReelShort 账号（覆盖环境变量里的初始值）。

        加号：后台改完账号立刻生效，不需要改文件、不用重启服务。
        密码只存进程内存，响应里只回脱敏邮箱与登录结果。
        """
        payload = request.get_json(silent=True) or {}
        email = str(payload.get("email") or "").strip()
        password = payload.get("password")
        if not email or not isinstance(password, str) or not password:
            return jsonify({"error": "email and password are required"}), 400
        reelshort_client.set_account(email, password)
        authenticated = reelshort_client.ensure_session()
        account = reelshort_client.account_status()
        account["vip"] = reelshort_client.account_vip() if authenticated else _unknown_vip()
        return jsonify({"authenticated": authenticated, "account": account})

    @app.get("/api/v1/reelshort/account")
    def account_summary():
        """后台账号卡片：登录态 + 会员状态 + 会员到期时间。"""
        payload = reelshort_client.account_status()
        payload["vip"] = reelshort_client.account_vip()
        return jsonify(payload)

    @app.get("/api/v1/reelshort/chapters/<book_id>")
    def chapters(book_id: str):
        """整季分集 + 每集能否播放（`play_info` 为空即上游锁集）。

        免费账号也能拿到可播窗口，后端据此做选集置灰，不必逐集探测。
        """
        locale = request_locale()
        if locale is None:
            return locale_error()
        return jsonify(reelshort_client.chapters(book_id))

    @app.get("/api/v1/reelshort/video/<book_id>/<int:episode_num>")
    def video(book_id: str, episode_num: int):
        locale = request_locale()
        if locale is None:
            return locale_error()
        filtered_title = request.args.get("filtered_title", "").strip()
        if not filtered_title:
            return jsonify({"error": "filtered_title is required"}), 400
        chapter_id = request.args.get("chapter_id", "").strip()
        if not chapter_id:
            return jsonify({"error": "chapter_id is required"}), 400
        return jsonify(reelshort_client.video(book_id, episode_num, filtered_title, chapter_id, locale))

    return app


def run_app():
    """本地/调试入口（Werkzeug 开发服务器）。生产由 gunicorn 以 `app:create_app()` 承载。"""
    host = os.getenv("CONTENT_PROVIDER_HOST", "127.0.0.1")
    if not os.getenv(INTERNAL_TOKEN_ENV) and host not in {"127.0.0.1", "localhost", "::1"}:
        # P2-16：非 loopback 监听必须配置内部共享密钥，否则除 /health 外的请求都会被拒绝。
        raise RuntimeError(
            f"{INTERNAL_TOKEN_ENV} must be set when CONTENT_PROVIDER_HOST is not loopback"
        )
    create_app().run(
        host=host,
        port=_as_int(os.getenv("CONTENT_PROVIDER_PORT", "5000"), 5000),
    )


if __name__ == "__main__":
    run_app()

