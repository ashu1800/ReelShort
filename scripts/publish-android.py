#!/usr/bin/env python3
"""
ReelShort / ShortLink Android release publisher.

Interactive script that:
  1. Prompts for a version number (X.Y.Z).
  2. Builds an optimized release APK signed by the pinned release certificate.
  3. Verifies the APK package name / version.
  4. Computes SHA-256 and file size, then verifies them.
  5. Uploads the APK and its .sha256 checksum to Tencent COS and re-checks the size
     of both uploaded objects.
  6. POSTs the release metadata to the backend (X-Internal-Super-Token protected),
     which stores the record and serves short-lived COS pre-signed URLs to the App.

发布阶段不再校验 APK 签名：正式签名由 Gradle 打包前的钉死证书校验保证
(见 android-app/SIGNING.md)，自动更新链路的完整性依据是清单里的
apkSha256/sizeBytes 与下载后的 SHA-256、文件大小比对。

Configuration is read from a local .env file in the repo root (gitignored) or from
environment variables. No secrets are committed to the repository.

Usage:
    python scripts/publish-android.py
    python scripts/publish-android.py --version 0.4.2
    python scripts/publish-android.py --version 0.4.2 --version-code 6 --notes "Bug fixes"
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import shutil
import re
import subprocess
import sys
from pathlib import Path

try:
    from qcloud_cos import CosConfig, CosS3Client
except ImportError:  # pragma: no cover - dependency bootstrapped below
    CosConfig = None
    CosS3Client = None

REPO_ROOT = Path(__file__).resolve().parent.parent
ANDROID_DIR = REPO_ROOT / "android-app"
FLUTTER_DIR = REPO_ROOT / "android-flutter"
OBJECT_KEY_PREFIX = "releases/android"
PACKAGE_NAME = "com.reelshort.app"
VERSION_RE = re.compile(r"^\d+\.\d+\.\d+$")
RELEASE_SIGNING_DIR = ANDROID_DIR / "release-signing"
RELEASE_SIGNING_CONFIG = RELEASE_SIGNING_DIR / "signing-config.txt"
RELEASE_KEYSTORE = RELEASE_SIGNING_DIR / "shortlink-release.jks"
# App 侧 ReleaseDownloader/OkHttpReleaseDownloader 对 .sha256 旁路文件的解析约束：
# 最长 1024 字节，且内容必须匹配 `<64 位十六进制摘要>[  <文件名>]`。
CHECKSUM_PATTERN = re.compile(r"^([a-fA-F0-9]{64})(?:\s+\*?[^\r\n]+)?$")
MAX_CHECKSUM_BYTES = 1024
SHA256_HEX_PATTERN = re.compile(r"^[0-9a-f]{64}$")
# 生产入口在 Cloudflare 后面，其 browser-integrity 校验会按 User-Agent 拦截（Python 默认的
# "Python-urllib/x.y" 会拿到 403 "error code: 1010"）。所有后端请求都必须显式带上这个 UA，
# 否则 fetch_latest_release 会静默返回 None，导致 versionCode 预检被跳过、交互式建议值退化成 1。
PUBLISHER_USER_AGENT = "ShortLinkPublisher/1.0"


class PublishError(Exception):
    """Raised for any publish failure; the message is user-facing."""


def main() -> int:
    args = parse_args()
    try:
        config = load_config()
        version_name, version_code, release_notes = resolve_inputs(args, config)
        preflight_release(config, version_name, version_code)
        apk_path = build_apk(version_name, version_code,
                             config.get("REELSHORT_COS_BUCKET", ""), config.get("REELSHORT_COS_REGION", ""),
                             source=args.source)
        verify_apk_metadata(apk_path, version_name, version_code)
        sha256_hex, apk_size, sha256_path, sha256_size = compute_artifacts(
            apk_path, apk_object_key(version_name))
        verify_artifacts(apk_path, sha256_hex, apk_size, sha256_path, sha256_size)
        ensure_cos_dependency()
        upload_to_cos(config, version_name, apk_path, sha256_path)
        publish_to_backend(config, version_name, version_code, release_notes,
                           sha256_hex, apk_size, sha256_size, args.mandatory, args.minimum_version_code)
        print(f"\nPublished ShortLink v{version_name} ({version_code}) successfully.")
        print(f"  APK SHA-256: {sha256_hex}")
        print(f"  APK size:    {apk_size} bytes")
        print(f"  The App will detect the new version on next launch.")
        return 0
    except PublishError as exc:
        print(f"\nERROR: {exc}", file=sys.stderr)
        return 1
    except subprocess.CalledProcessError as exc:
        print(f"\nERROR: command failed (exit {exc.returncode}): {' '.join(exc.cmd or [])}",
              file=sys.stderr)
        return exc.returncode or 2


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Build, upload and publish a ShortLink release.")
    parser.add_argument("--version", help="Version name in X.Y.Z form. Prompted if omitted.")
    parser.add_argument("--version-code", type=int,
                        help="Positive integer version code. Defaults to last published + 1.")
    parser.add_argument("--notes", help="Optional release notes text. Defaults to empty when omitted.")
    parser.add_argument("--mandatory", action="store_true", help="Mark the release as mandatory.")
    parser.add_argument("--minimum-version-code", type=int, default=1,
                        help="Minimum app version code that may install this update (default 1).")
    parser.add_argument("--source", choices=("flutter", "kotlin"), default="flutter",
                        help="Which app tree to build. The Flutter app is the release target since "
                             "the Kotlin app entered maintenance (default: flutter).")
    return parser.parse_args()


def load_config() -> dict:
    """Load config from a .env file at repo root, then environment variables."""
    env_path = REPO_ROOT / ".env"
    if env_path.exists():
        for line in env_path.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, _, value = line.partition("=")
            key, value = key.strip(), value.strip().strip('"').strip("'")
            os.environ.setdefault(key, value)

    load_release_signing_environment(RELEASE_SIGNING_CONFIG, RELEASE_KEYSTORE)

    required = ["COS_SECRET_ID", "COS_SECRET_KEY", "COS_BUCKET", "COS_REGION",
                "REELSHORT_INTERNAL_SUPER_TOKEN", "RELEASE_BACKEND_URL"]
    missing = [k for k in required if not os.environ.get(k)]
    if missing:
        raise PublishError(
            f"Missing required config: {', '.join(missing)}.\n"
            f"Set them via environment variables or in {env_path} (see .env.example).")
    return {k: os.environ[k] for k in required}


def load_release_signing_environment(config_path: Path, keystore_path: Path) -> None:
    """Load ignored local signing values without overriding explicit environment variables."""
    if config_path.exists():
        for raw_line in config_path.read_text(encoding="utf-8").splitlines():
            line = raw_line.strip()
            if not line or line.startswith("#"):
                continue
            if line.startswith("export "):
                line = line[7:].lstrip()
            if "=" not in line:
                continue
            key, _, value = line.partition("=")
            key = key.strip()
            value = value.strip().strip('"').strip("'")
            if key.startswith("ANDROID_SIGNING_"):
                os.environ.setdefault(key, value)

    if keystore_path.exists():
        os.environ.setdefault("ANDROID_SIGNING_STORE_FILE", str(keystore_path.resolve()))


def resolve_inputs(args: argparse.Namespace, config: dict) -> tuple[str, int, str]:
    version_name = (args.version or input("Enter version (X.Y.Z): ")).strip()
    if not VERSION_RE.match(version_name):
        raise PublishError(f"Version '{version_name}' must match X.Y.Z (e.g. 0.4.2).")

    if args.version_code:
        version_code = args.version_code
    else:
        prompt = f"Enter version code (positive integer, e.g. {suggest_version_code(config)}): "
        raw = input(prompt).strip()
        if not raw.isdigit() or int(raw) <= 0:
            raise PublishError("Version code must be a positive integer.")
        version_code = int(raw)

    notes = args.notes
    if notes is None:
        notes = input("Release notes [optional]: ").strip()

    return version_name, version_code, notes


def fetch_latest_release(config: dict) -> dict | None:
    """Best-effort: read the currently published release manifest, or None when unavailable."""
    try:
        import urllib.request
        req = urllib.request.Request(
            f"{config['RELEASE_BACKEND_URL'].rstrip('/')}/api/internal/release/latest",
            headers={
                "X-Internal-Super-Token": config["REELSHORT_INTERNAL_SUPER_TOKEN"],
                "User-Agent": PUBLISHER_USER_AGENT,
            })
        with urllib.request.urlopen(req, timeout=10) as resp:  # noqa: S310 - trusted internal URL
            return json.loads(resp.read()).get("data") or None
    except Exception:
        return None


def suggest_version_code(config: dict) -> int:
    """Best-effort: query the backend's current latest version code + 1."""
    latest = fetch_latest_release(config)
    if not latest:
        return 1
    try:
        return int(latest["versionCode"]) + 1
    except Exception:
        return 1


def preflight_release(config: dict, version_name: str, version_code: int) -> None:
    """
    Refuse an obviously invalid release before building and uploading the APK:
    the backend also rejects a decreasing versionCode, but by then the objects are already in COS
    and someone has to clean them up by hand.
    """
    latest = fetch_latest_release(config)
    if latest is None:
        print("WARNING: cannot read the current release manifest; skipping the versionCode preflight "
              "(the backend still rejects a decreasing versionCode).")
        return
    current_code = int(latest.get("versionCode", 0))
    current_name = latest.get("versionName", "unknown")
    if version_code < current_code:
        raise PublishError(
            f"versionCode {version_code} is lower than the published {current_code} (v{current_name}); "
            "the backend would reject this release after the APK upload. Use a higher versionCode.")
    if version_code == current_code:
        print(f"NOTE: versionCode {version_code} already exists (published v{current_name}); "
              f"re-publishing v{version_name} overwrites that release's object keys.")


def build_apk(version_name: str, version_code: int, cos_bucket: str = "", cos_region: str = "",
              source: str = "flutter") -> Path:
    print(f"\n=== Building ShortLink v{version_name} ({version_code}) ===")
    required_signing = [
        "ANDROID_SIGNING_STORE_FILE",
        "ANDROID_SIGNING_STORE_PASSWORD",
        "ANDROID_SIGNING_KEY_ALIAS",
        "ANDROID_SIGNING_KEY_PASSWORD",
    ]
    missing_signing = [key for key in required_signing if not os.environ.get(key)]
    if missing_signing:
        raise PublishError(
            "Release signing is not configured. Missing: " + ", ".join(missing_signing) +
            f". Restore {RELEASE_SIGNING_CONFIG} or set the signing environment variables."
        )
    if source == "flutter":
        cmd = [
            find_flutter_bin(),
            "build", "apk", "--release",
            f"--build-name={version_name}",
            f"--build-number={version_code}",
        ]
        print(f"$ {' '.join(cmd)}")
        result = subprocess.run(cmd, cwd=str(FLUTTER_DIR))
        if result.returncode != 0:
            raise PublishError("Flutter release build failed.")
        apk = FLUTTER_DIR / "build" / "app" / "outputs" / "flutter-apk" / "app-release.apk"
    else:
        gradlew = str(ANDROID_DIR / ("gradlew.bat" if os.name == "nt" else "gradlew"))
        cmd = [
            gradlew, ":app:assembleRelease",
            f"-PappVersionName={version_name}",
            f"-PappVersionCode={version_code}",
        ]
        # 把 .env 里的 COS 桶/地域传给 App 构建：App 只接受该桶的精确下载域名，避免两处配置漂移。
        if cos_bucket:
            cmd.append(f"-PreelshortCosBucket={cos_bucket}")
        if cos_region:
            cmd.append(f"-PreelshortCosRegion={cos_region}")
        print(f"$ {' '.join(cmd)}")
        result = subprocess.run(cmd, cwd=str(ANDROID_DIR))
        if result.returncode != 0:
            raise PublishError("Gradle release build failed.")
        apk = ANDROID_DIR / "app" / "build" / "outputs" / "apk" / "release" / "app-release.apk"
    if not apk.exists():
        raise PublishError(f"Expected APK not found at {apk}.")
    print(f"Built APK: {apk}")
    return apk


def find_flutter_bin() -> str:
    """Locate the flutter CLI: FLUTTER_BIN env, PATH, then the documented install path."""
    override = os.environ.get("FLUTTER_BIN")
    if override:
        return override
    path_hit = shutil.which("flutter") or shutil.which("flutter.bat")
    if path_hit:
        return path_hit
    fallback = Path.home() / "flutter" / "3.47.7" / "bin" / ("flutter.bat" if os.name == "nt" else "flutter")
    if fallback.exists():
        return str(fallback)
    raise PublishError(
        "flutter CLI not found. Install the Flutter SDK, put `flutter` on PATH, "
        "or set FLUTTER_BIN to the full binary path.")


def verify_apk_metadata(apk_path: Path, version_name: str, version_code: int) -> None:
    """
    Verify the APK package name, versionName and versionCode with `aapt`.

    APK 签名刻意不在发布阶段校验（原 `apksigner` + 钉死正式证书校验已移除）：正式签名由
    Gradle 打包前的 `validateOfficialSigning` 门禁保证，发布链路的完整性依据改成核对
    SHA-256 与文件大小，也就是 App 下载后会比对的那两条元数据。
    """
    print("\n=== Verifying APK metadata ===")
    aapt = find_build_tools() / "aapt"
    if os.name == "nt":
        aapt = aapt.with_suffix(".exe")
    badging = subprocess.run([str(aapt), "dump", "badging", str(apk_path)],
                             capture_output=True, text=True, check=True).stdout
    verify_badging(badging, version_name, version_code)
    print("APK metadata verified (APK signature is intentionally not checked at publish time).")


def verify_badging(badging: str, version_name: str, version_code: int) -> None:
    if f"package: name='{PACKAGE_NAME}'" not in badging:
        raise PublishError(f"APK package name is not {PACKAGE_NAME}.")
    if f"versionCode='{version_code}'" not in badging:
        raise PublishError(f"APK versionCode is not {version_code}.")
    if f"versionName='{version_name}'" not in badging:
        raise PublishError(f"APK versionName is not '{version_name}'.")


def find_build_tools() -> Path:
    sdk_root = Path(os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
                    or Path.home() / "AppData" / "Local" / "Android" / "sdk")
    candidates = sorted((sdk_root / "build-tools").glob("*"))
    if not candidates:
        raise PublishError(
            f"Android build-tools not found under {sdk_root / 'build-tools'}.\n"
            f"Set ANDROID_HOME or ANDROID_SDK_ROOT.")
    return candidates[-1]


def apk_object_key(version_name: str) -> str:
    """COS object key of the published APK."""
    return f"{OBJECT_KEY_PREFIX}/ShortLink-v{version_name}.apk"


def sha256_object_key(version_name: str) -> str:
    """COS object key of the published `.sha256` sidecar."""
    return f"{apk_object_key(version_name)}.sha256"


def hash_file(path: Path) -> tuple[str, int]:
    """
    Hash [path] in one streaming pass and return `(lowercase SHA-256, byte count)`.

    摘要和长度来自同一遍读取，避免大文件在读取过程中被改动导致两者互相矛盾。
    """
    digest = hashlib.sha256()
    size = 0
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
            size += len(chunk)
    return digest.hexdigest(), size


def compute_artifacts(apk_path: Path, published_name: str) -> tuple[str, int, Path, int]:
    """
    Compute the APK SHA-256 and file size and write the `.sha256` sidecar next to the APK.

    [published_name] is the object name the APK is published under, so the sidecar stays
    self-consistent with the COS object and `sha256sum -c` works on a downloaded copy.
    """
    print("\n=== Computing checksums and sizes ===")
    sha256, apk_size = hash_file(apk_path)
    sha256_path = apk_path.with_suffix(apk_path.suffix + ".sha256")
    sha256_path.write_text(f"{sha256}  {published_name}", encoding="utf-8")
    sha256_size = sha256_path.stat().st_size
    print(f"APK size: {apk_size} bytes, SHA-256: {sha256}")
    print(f"Checksum file size: {sha256_size} bytes")
    return sha256, apk_size, sha256_path, sha256_size


def verify_artifacts(apk_path: Path, sha256_hex: str, apk_size: int, sha256_path: Path,
                     sha256_size: int) -> None:
    """
    发布前核对 APK 的 SHA-256 与文件大小（发布阶段不再校验 APK 签名）。

    核对的就是 App 更新链路真正依赖的两组数据：
      * 发布到后端的 `apkSha256` / `apkSizeBytes` 必须自洽且与磁盘文件一致；
      * `.sha256` 旁路文件必须能被 App 的下载器解析（≤1024 字节），且摘要与 APK 一致。
    """
    print("\n=== Verifying SHA-256 and file size ===")
    if not SHA256_HEX_PATTERN.fullmatch(sha256_hex):
        raise PublishError(f"APK SHA-256 '{sha256_hex}' is not 64 lowercase hex characters.")
    if apk_size <= 0:
        raise PublishError("APK size must be positive.")
    current_size = apk_path.stat().st_size
    if current_size != apk_size:
        raise PublishError(
            f"APK changed while it was hashed ({apk_size} bytes hashed, {current_size} bytes on disk). "
            "Wait for the build to finish writing the file and re-run the publish.")
    if not sha256_path.is_file():
        raise PublishError(f"Checksum file was not written: {sha256_path}")
    payload = sha256_path.read_bytes()
    if len(payload) != sha256_size:
        raise PublishError(
            f"Checksum file changed after it was written ({sha256_size} bytes recorded, "
            f"{len(payload)} bytes on disk).")
    if sha256_size > MAX_CHECKSUM_BYTES:
        raise PublishError(
            f"Checksum file is {sha256_size} bytes; the App rejects `.sha256` sidecars above "
            f"{MAX_CHECKSUM_BYTES} bytes.")
    text = payload.decode("utf-8", errors="replace").strip()
    match = CHECKSUM_PATTERN.match(text)
    if not match:
        raise PublishError(
            "Checksum file must hold a SHA-256 digest, optionally followed by the file name; "
            f"got: {text!r}")
    if match.group(1).lower() != sha256_hex:
        raise PublishError(
            f"Checksum file digest {match.group(1)} does not match the APK digest {sha256_hex}.")
    print(f"SHA-256 and file size verified: {sha256_hex} / {apk_size} bytes")


def ensure_cos_dependency() -> None:
    global CosConfig, CosS3Client
    if CosS3Client is not None:
        return
    print("\n=== Installing cos-python-sdk-v5 ===")
    result = subprocess.run([sys.executable, "-m", "pip", "install", "cos-python-sdk-v5", "-q"])
    if result.returncode != 0:
        raise PublishError("Failed to install cos-python-sdk-v5.")
    from qcloud_cos import CosConfig as _CosConfig, CosS3Client as _CosS3Client
    CosConfig, CosS3Client = _CosConfig, _CosS3Client


def upload_to_cos(config: dict, version_name: str, apk_path: Path, sha256_path: Path,
                  client=None) -> None:
    print("\n=== Uploading artifacts to COS ===")
    if client is None:
        cos_config = CosConfig(
            Region=config["COS_REGION"],
            SecretId=config["COS_SECRET_ID"],
            SecretKey=config["COS_SECRET_KEY"],
            Scheme="https",
        )
        client = CosS3Client(cos_config)
    bucket = config["COS_BUCKET"]
    apk_key = apk_object_key(version_name)
    sha_key = sha256_object_key(version_name)

    client.upload_file(Bucket=bucket, Key=apk_key, LocalFilePath=str(apk_path), EnableMD5=True)
    print(f"Uploaded {apk_key}")
    client.upload_file(Bucket=bucket, Key=sha_key, LocalFilePath=str(sha256_path), EnableMD5=True)
    print(f"Uploaded {sha_key}")
    verify_uploaded_size(client, bucket, apk_key, apk_path)
    verify_uploaded_size(client, bucket, sha_key, sha256_path)


def verify_uploaded_size(client, bucket: str, key: str, local_path: Path) -> None:
    """核对 COS 对象大小与本地文件一致；App 下载后会按清单里的同一大小做校验。"""
    try:
        head = client.head_object(Bucket=bucket, Key=key)
    except Exception as exc:  # noqa: BLE001 - 统一转成用户可读的发布错误
        raise PublishError(f"Uploaded object {key} could not be verified: {exc}") from exc
    remote_size = head.get("Content-Length", head.get("content-length"))
    if remote_size is None:
        raise PublishError(
            f"COS did not report Content-Length for {key}; the file size cannot be verified.")
    local_size = local_path.stat().st_size
    if int(remote_size) != local_size:
        raise PublishError(
            f"COS object {key} is {int(remote_size)} bytes but the local file is {local_size} bytes. "
            "The upload is incomplete and the App would reject the download.")
    print(f"Verified COS object size: {key} = {local_size} bytes")


def publish_to_backend(config: dict, version_name: str, version_code: int, release_notes: str,
                       sha256_hex: str, apk_size: int, sha256_size: int, mandatory: bool,
                       minimum_version_code: int) -> None:
    print("\n=== Publishing release metadata to backend ===")
    import urllib.request
    import urllib.error

    apk_key = apk_object_key(version_name)
    sha_key = sha256_object_key(version_name)
    payload = {
        "versionName": version_name,
        "versionCode": version_code,
        "apkObjectKey": apk_key,
        "sha256ObjectKey": sha_key,
        "apkSizeBytes": apk_size,
        "sha256SizeBytes": sha256_size,
        "apkSha256": sha256_hex,
        "title": f"ShortLink v{version_name}",
        "releaseNotes": release_notes,
        "mandatory": mandatory,
        "minimumVersionCode": minimum_version_code,
    }
    url = f"{config['RELEASE_BACKEND_URL'].rstrip('/')}/api/internal/release/publish"
    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(url, data=data, method="POST", headers={
        "Content-Type": "application/json",
        "User-Agent": PUBLISHER_USER_AGENT,
        "X-Internal-Super-Token": config["REELSHORT_INTERNAL_SUPER_TOKEN"],
    })
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:  # noqa: S310 - trusted internal URL
            body = json.loads(resp.read())
            if body.get("code") != 0:
                raise PublishError(f"Backend rejected publish: {body}")
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        raise PublishError(
            f"Backend publish failed (HTTP {exc.code}): {detail}\n"
            f"The APK and checksum are already uploaded to COS ({apk_key}, {sha_key}) but the release is "
            "NOT active until the backend accepts it. Fix the cause and re-run, or delete those two COS "
            "objects if you want to abandon this version.") from exc
    except urllib.error.URLError as exc:
        raise PublishError(
            f"Backend publish failed (network error: {exc.reason}).\n"
            f"The APK and checksum are already uploaded to COS ({apk_key}, {sha_key}) but the release is "
            "NOT active. Re-run the publish step, or delete those two COS objects before publishing again.") from exc
    print(f"Backend accepted release v{version_name}.")


if __name__ == "__main__":
    raise SystemExit(main())
