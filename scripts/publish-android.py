#!/usr/bin/env python3
"""
ReelShort / ShortLink Android release publisher.

Interactive script that:
  1. Prompts for a version number (X.Y.Z).
  2. Builds a signed release APK via Gradle.
  3. Verifies the APK package name / version / signature.
  4. Computes SHA-256 and file sizes.
  5. Uploads the APK and its .sha256 checksum to Tencent COS.
  6. POSTs the release metadata to the backend (X-Internal-Super-Token protected),
     which stores the record and serves short-lived COS pre-signed URLs to the App.

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
import re
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

try:
    from qcloud_cos import CosConfig, CosS3Client
except ImportError:  # pragma: no cover - dependency bootstrapped below
    CosConfig = None
    CosS3Client = None

REPO_ROOT = Path(__file__).resolve().parent.parent
ANDROID_DIR = REPO_ROOT / "android-app"
OBJECT_KEY_PREFIX = "releases/android"
PACKAGE_NAME = "com.reelshort.app"
VERSION_RE = re.compile(r"^\d+\.\d+\.\d+$")


class PublishError(Exception):
    """Raised for any publish failure; the message is user-facing."""


def main() -> int:
    args = parse_args()
    try:
        config = load_config()
        version_name, version_code, release_notes = resolve_inputs(args, config)
        apk_path = build_apk(version_name, version_code)
        verify_apk(apk_path, version_name)
        sha256_hex, apk_size, sha256_path, sha256_size = compute_artifacts(apk_path)
        ensure_cos_dependency()
        upload_to_cos(config, version_name, apk_path, sha256_path)
        publish_to_backend(config, version_name, version_code, release_notes,
                           sha256_hex, apk_size, sha256_size, args.mandatory, args.minimum_version_code)
        print(f"\nPublished ShortLink v{version_name} ({version_code}) successfully.")
        print(f"  APK SHA-256: {sha256_hex}")
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
    parser.add_argument("--notes", help="Release notes text. Prompted if omitted.")
    parser.add_argument("--mandatory", action="store_true", help="Mark the release as mandatory.")
    parser.add_argument("--minimum-version-code", type=int, default=1,
                        help="Minimum app version code that may install this update (default 1).")
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

    required = ["COS_SECRET_ID", "COS_SECRET_KEY", "COS_BUCKET", "COS_REGION",
                "REELSHORT_INTERNAL_SUPER_TOKEN", "RELEASE_BACKEND_URL"]
    missing = [k for k in required if not os.environ.get(k)]
    if missing:
        raise PublishError(
            f"Missing required config: {', '.join(missing)}.\n"
            f"Set them via environment variables or in {env_path} (see .env.example).")
    return {k: os.environ[k] for k in required}


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
        default = f"ShortLink v{version_name} update."
        notes = input(f"Release notes [default: {default}]: ").strip() or default

    return version_name, version_code, notes


def suggest_version_code(config: dict) -> int:
    """Best-effort: query the backend's current latest version code + 1."""
    try:
        import urllib.request
        import urllib.error
        req = urllib.request.Request(
            f"{config['RELEASE_BACKEND_URL'].rstrip('/')}/api/internal/release/latest",
            headers={"X-Internal-Super-Token": config["REELSHORT_INTERNAL_SUPER_TOKEN"]})
        with urllib.request.urlopen(req, timeout=10) as resp:  # noqa: S310 - trusted internal URL
            body = json.loads(resp.read())
            return int(body["data"]["versionCode"]) + 1
    except Exception:
        return 1


def build_apk(version_name: str, version_code: int) -> Path:
    print(f"\n=== Building ShortLink v{version_name} ({version_code}) ===")
    gradlew = str(ANDROID_DIR / ("gradlew.bat" if os.name == "nt" else "gradlew"))
    cmd = [
        gradlew, ":app:assembleRelease",
        f"-PappVersionName={version_name}",
        f"-PappVersionCode={version_code}",
    ]
    print(f"$ {' '.join(cmd)}")
    result = subprocess.run(cmd, cwd=str(ANDROID_DIR))
    if result.returncode != 0:
        raise PublishError("Gradle release build failed.")
    apk = ANDROID_DIR / "app" / "build" / "outputs" / "apk" / "release" / "app-release.apk"
    if not apk.exists():
        raise PublishError(f"Expected APK not found at {apk}.")
    print(f"Built APK: {apk}")
    return apk


def verify_apk(apk_path: Path, version_name: str) -> None:
    """Verify the APK signature, package name and versionName using the Android build-tools."""
    print("\n=== Verifying APK metadata and signature ===")
    build_tools_dir = find_build_tools()
    apksigner = build_tools_dir / "apksigner"
    aapt = build_tools_dir / "aapt"
    if os.name == "nt":
        apksigner = apksigner.with_suffix(".bat")
        aapt = aapt.with_suffix(".exe")

    run_quiet([str(apksigner), "verify", "--verbose", "--print-certs", str(apk_path)],
              "APK signature verification failed.")
    badging = subprocess.run([str(aapt), "dump", "badging", str(apk_path)],
                             capture_output=True, text=True, check=True).stdout
    if f"package: name='{PACKAGE_NAME}'" not in badging:
        raise PublishError(f"APK package name is not {PACKAGE_NAME}.")
    if f"versionName='{version_name}'" not in badging:
        raise PublishError(f"APK versionName is not '{version_name}'.")
    print("APK signature and metadata verified.")


def find_build_tools() -> Path:
    sdk_root = Path(os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
                    or Path.home() / "AppData" / "Local" / "Android" / "sdk")
    candidates = sorted((sdk_root / "build-tools").glob("*"))
    if not candidates:
        raise PublishError(
            f"Android build-tools not found under {sdk_root / 'build-tools'}.\n"
            f"Set ANDROID_HOME or ANDROID_SDK_ROOT.")
    return candidates[-1]


def compute_artifacts(apk_path: Path) -> tuple[str, int, Path, int]:
    print("\n=== Computing checksums and sizes ===")
    sha256 = hashlib.sha256(apk_path.read_bytes()).hexdigest()
    apk_size = apk_path.stat().st_size
    sha256_path = apk_path.with_suffix(apk_path.suffix + ".sha256")
    sha256_content = f"{sha256}  {apk_path.name}"
    sha256_path.write_text(sha256_content, encoding="utf-8")
    sha256_size = sha256_path.stat().st_size
    print(f"APK size: {apk_size} bytes, SHA-256: {sha256}")
    print(f"Checksum file size: {sha256_size} bytes")
    return sha256, apk_size, sha256_path, sha256_size


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


def upload_to_cos(config: dict, version_name: str, apk_path: Path, sha256_path: Path) -> None:
    print("\n=== Uploading artifacts to COS ===")
    cos_config = CosConfig(
        Region=config["COS_REGION"],
        SecretId=config["COS_SECRET_ID"],
        SecretKey=config["COS_SECRET_KEY"],
        Scheme="https",
    )
    client = CosS3Client(cos_config)
    bucket = config["COS_BUCKET"]
    apk_key = f"{OBJECT_KEY_PREFIX}/ShortLink-v{version_name}.apk"
    sha_key = f"{OBJECT_KEY_PREFIX}/ShortLink-v{version_name}.apk.sha256"

    client.upload_file(Bucket=bucket, Key=apk_key, LocalPath=str(apk_path), EnableMD5=True)
    print(f"Uploaded {apk_key}")
    client.upload_file(Bucket=bucket, Key=sha_key, LocalPath=str(sha256_path), EnableMD5=True)
    print(f"Uploaded {sha_key}")


def publish_to_backend(config: dict, version_name: str, version_code: int, release_notes: str,
                       sha256_hex: str, apk_size: int, sha256_size: int, mandatory: bool,
                       minimum_version_code: int) -> None:
    print("\n=== Publishing release metadata to backend ===")
    import urllib.request
    import urllib.error

    apk_key = f"{OBJECT_KEY_PREFIX}/ShortLink-v{version_name}.apk"
    sha_key = f"{OBJECT_KEY_PREFIX}/ShortLink-v{version_name}.apk.sha256"
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
        "X-Internal-Super-Token": config["REELSHORT_INTERNAL_SUPER_TOKEN"],
    })
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:  # noqa: S310 - trusted internal URL
            body = json.loads(resp.read())
            if body.get("code") != 0:
                raise PublishError(f"Backend rejected publish: {body}")
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        raise PublishError(f"Backend publish failed (HTTP {exc.code}): {detail}") from exc
    print(f"Backend accepted release v{version_name}.")


def run_quiet(cmd: list[str], failure_msg: str) -> None:
    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode != 0:
        raise PublishError(f"{failure_msg}\n{result.stdout}\n{result.stderr}")


if __name__ == "__main__":
    raise SystemExit(main())
