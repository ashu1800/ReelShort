import hashlib
import importlib.util
import os
import tempfile
import unittest
import urllib.request
from pathlib import Path
from unittest.mock import patch


def load_publisher():
    script = Path(__file__).resolve().parents[1] / "publish-android.py"
    spec = importlib.util.spec_from_file_location("publish_android", script)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


publisher = load_publisher()

# 正式证书 SHA-256：Gradle 打包前的钉死校验仍要求它，发布脚本自 2026-10-16 起不再校验签名。
PINNED_RELEASE_CERT_SHA256 = "c1a149d5352c5c313d8d7d995470da31a3bd298281abba914e559747bb45c2f9"

# App 侧 `.sha256` 旁路文件的解析契约（android-flutter/lib/update/release_downloader.dart）。
APP_CHECKSUM_PATTERN = r"^([a-fA-F0-9]{64})(?:\s+\*?[^\r\n]+)?$"
APP_MAX_CHECKSUM_BYTES = 1024


class _FakeCompletedProcess:
    def __init__(self, stdout=""):
        self.stdout = stdout
        self.stderr = ""
        self.returncode = 0


class _FakeCosClient:
    """只记录调用并回报可配置的远端对象大小，用于验证上传后的大小核对。"""

    def __init__(self, remote_sizes=None, head_fails=False):
        self.remote_sizes = remote_sizes or {}
        self.head_fails = head_fails
        self.uploads = []
        self.heads = []

    def upload_file(self, **kwargs):
        self.uploads.append(kwargs)

    def head_object(self, **kwargs):
        self.heads.append(kwargs)
        if self.head_fails:
            raise RuntimeError("COS unavailable")
        key = kwargs["Key"]
        if key in self.remote_sizes:
            return {"Content-Length": str(self.remote_sizes[key])}
        local = next(u["LocalFilePath"] for u in self.uploads if u["Key"] == key)
        return {"Content-Length": str(Path(local).stat().st_size)}


class PublishAndroidTests(unittest.TestCase):
    def _write_apk(self, root: Path, name: str = "app-release.apk") -> Path:
        apk = root / name
        apk.write_bytes(b"shortlink-apk-fixture" * 37)
        return apk

    def test_publish_stage_does_not_verify_apk_signature(self):
        """
        发布阶段不再要求发布包带签名：只跑 aapt 核对包名/版本，不再调用 apksigner，
        也不再暴露钉死证书校验函数；完整性改由 SHA-256 与文件大小保证。
        """
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            apk = self._write_apk(root)
            commands = []

            def fake_run(cmd, **_kwargs):
                commands.append([str(part) for part in cmd])
                return _FakeCompletedProcess(
                    "package: name='com.reelshort.app' versionCode='45' versionName='0.9.3'\n"
                )

            with patch.object(publisher, "find_build_tools", return_value=root), \
                    patch.object(publisher.subprocess, "run", fake_run):
                publisher.verify_apk_metadata(apk, "0.9.3", 45)

        self.assertEqual(1, len(commands))
        self.assertTrue(Path(commands[0][0]).name.startswith("aapt"))
        self.assertNotIn("apksigner", " ".join(commands[0]))
        self.assertFalse(hasattr(publisher, "verify_expected_certificate"))
        self.assertFalse(hasattr(publisher, "EXPECTED_RELEASE_CERT_SHA256"))

        source = (Path(__file__).resolve().parents[1] / "publish-android.py").read_text(encoding="utf-8")
        self.assertNotIn("--print-certs", source)
        self.assertNotIn("apksigner ", source)

    def test_publish_stage_still_checks_package_name_and_version(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            apk = self._write_apk(root)

            def fake_run(_cmd, **_kwargs):
                return _FakeCompletedProcess(
                    "package: name='com.reelshort.app' versionCode='44' versionName='0.9.2'\n"
                )

            with patch.object(publisher, "find_build_tools", return_value=root), \
                    patch.object(publisher.subprocess, "run", fake_run):
                with self.assertRaises(publisher.PublishError):
                    publisher.verify_apk_metadata(apk, "0.9.3", 45)

    def test_computes_and_verifies_sha256_and_size(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            apk = self._write_apk(root)
            payload = apk.read_bytes()

            digest, size, sha256_path, sha256_size = publisher.compute_artifacts(
                apk, "ShortLink-v0.9.3.apk"
            )

            self.assertEqual(hashlib.sha256(payload).hexdigest(), digest)
            self.assertEqual(len(payload), size)
            self.assertEqual((digest, size), publisher.hash_file(apk))
            self.assertEqual(f"{digest}  ShortLink-v0.9.3.apk", sha256_path.read_text(encoding="utf-8"))
            self.assertEqual(sha256_path.stat().st_size, sha256_size)

            # 不抛异常即为通过：清单里的 apkSha256/sizeBytes 与磁盘文件、旁路文件三者一致。
            publisher.verify_artifacts(apk, digest, size, sha256_path, sha256_size)

    def test_sidecar_contract_matches_the_app_parser(self):
        """
        发布脚本写出的 `.sha256` 必须能被 App 的下载器解析：同一个正则、同一个 1024 字节上限。
        """
        dart_source = (
            Path(__file__).resolve().parents[2]
            / "android-flutter"
            / "lib"
            / "update"
            / "release_downloader.dart"
        ).read_text(encoding="utf-8")

        self.assertEqual(APP_CHECKSUM_PATTERN, publisher.CHECKSUM_PATTERN.pattern)
        self.assertEqual(APP_MAX_CHECKSUM_BYTES, publisher.MAX_CHECKSUM_BYTES)
        self.assertIn(APP_CHECKSUM_PATTERN, dart_source)
        self.assertIn(f"const int maxChecksumBytes = {APP_MAX_CHECKSUM_BYTES};", dart_source)

        digest = "a" * 64
        self.assertIsNotNone(publisher.CHECKSUM_PATTERN.match(f"{digest}  ShortLink-v0.9.3.apk"))
        self.assertIsNotNone(publisher.CHECKSUM_PATTERN.match(digest))
        self.assertIsNone(publisher.CHECKSUM_PATTERN.match("a" * 63))

    def test_rejects_apk_size_that_does_not_match_disk(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            apk = self._write_apk(root)
            digest, size, sha256_path, sha256_size = publisher.compute_artifacts(apk, "ShortLink-v0.9.3.apk")

            with self.assertRaises(publisher.PublishError):
                publisher.verify_artifacts(apk, digest, size + 1, sha256_path, sha256_size)

            with self.assertRaises(publisher.PublishError):
                publisher.verify_artifacts(apk, digest.upper(), size, sha256_path, sha256_size)

    def test_rejects_sidecar_digest_mismatch(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            apk = self._write_apk(root)
            digest, size, sha256_path, _ = publisher.compute_artifacts(apk, "ShortLink-v0.9.3.apk")

            sha256_path.write_text(f"{'b' * 64}  ShortLink-v0.9.3.apk", encoding="utf-8")

            with self.assertRaises(publisher.PublishError):
                publisher.verify_artifacts(apk, digest, size, sha256_path, sha256_path.stat().st_size)

    def test_rejects_oversized_sidecar(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            apk = self._write_apk(root)
            digest, size, sha256_path, _ = publisher.compute_artifacts(apk, "ShortLink-v0.9.3.apk")

            sha256_path.write_text(f"{digest}  {'c' * publisher.MAX_CHECKSUM_BYTES}", encoding="utf-8")

            with self.assertRaises(publisher.PublishError):
                publisher.verify_artifacts(apk, digest, size, sha256_path, sha256_path.stat().st_size)

    def test_rejects_sidecar_without_a_parsable_digest(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            apk = self._write_apk(root)
            digest, size, sha256_path, _ = publisher.compute_artifacts(apk, "ShortLink-v0.9.3.apk")

            sha256_path.write_text("not a checksum", encoding="utf-8")

            with self.assertRaises(publisher.PublishError):
                publisher.verify_artifacts(apk, digest, size, sha256_path, sha256_path.stat().st_size)

    def test_upload_rechecks_uploaded_object_sizes(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            apk = self._write_apk(root)
            sha256_path = root / "app-release.apk.sha256"
            sha256_path.write_text(f"{'d' * 64}  ShortLink-v0.9.3.apk", encoding="utf-8")
            client = _FakeCosClient()

            publisher.upload_to_cos({"COS_BUCKET": "bucket"}, "0.9.3", apk, sha256_path, client=client)

        self.assertEqual(
            [
                ("bucket", "releases/android/ShortLink-v0.9.3.apk"),
                ("bucket", "releases/android/ShortLink-v0.9.3.apk.sha256"),
            ],
            [(upload["Bucket"], upload["Key"]) for upload in client.uploads],
        )
        self.assertEqual(
            ["releases/android/ShortLink-v0.9.3.apk", "releases/android/ShortLink-v0.9.3.apk.sha256"],
            [head["Key"] for head in client.heads],
        )

    def test_upload_fails_when_cos_object_size_differs(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            apk = self._write_apk(root)
            sha256_path = root / "app-release.apk.sha256"
            sha256_path.write_text(f"{'d' * 64}  ShortLink-v0.9.3.apk", encoding="utf-8")
            client = _FakeCosClient(remote_sizes={"releases/android/ShortLink-v0.9.3.apk": 7})

            with self.assertRaises(publisher.PublishError) as context:
                publisher.upload_to_cos({"COS_BUCKET": "bucket"}, "0.9.3", apk, sha256_path, client=client)

        self.assertIn("incomplete", str(context.exception))

    def test_upload_fails_when_cos_cannot_report_size(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            apk = self._write_apk(root)
            sha256_path = root / "app-release.apk.sha256"
            sha256_path.write_text(f"{'d' * 64}  ShortLink-v0.9.3.apk", encoding="utf-8")

            with self.assertRaises(publisher.PublishError):
                publisher.upload_to_cos(
                    {"COS_BUCKET": "bucket"},
                    "0.9.3",
                    apk,
                    sha256_path,
                    client=_FakeCosClient(head_fails=True),
                )

    def test_release_object_keys_are_stable(self):
        self.assertEqual("releases/android/ShortLink-v0.9.3.apk", publisher.apk_object_key("0.9.3"))
        self.assertEqual(
            "releases/android/ShortLink-v0.9.3.apk.sha256",
            publisher.sha256_object_key("0.9.3"),
        )

    def test_all_android_variants_use_only_official_signing(self):
        """
        发布脚本不再校验签名，正式签名完全由 Gradle 打包前的钉死证书门禁保证：
        这里继续锁定该门禁不被移除或换成 Debug 证书。
        """
        gradle_file = Path(__file__).resolve().parents[2] / "android-app" / "app" / "build.gradle.kts"
        gradle = gradle_file.read_text(encoding="utf-8")

        self.assertIn('environmentVariable("ANDROID_SIGNING_STORE_FILE")', gradle)
        self.assertIn('create("official")', gradle)
        self.assertEqual(2, gradle.count('signingConfig = signingConfigs.getByName("official")'))
        self.assertIn(PINNED_RELEASE_CERT_SHA256, gradle)
        self.assertIn("validateOfficialSigning", gradle)
        self.assertIn("gradle.taskGraph.whenReady", gradle)
        self.assertNotIn("fixedDebug", gradle)
        self.assertNotIn("debug.keystore", gradle)

    def test_loads_ignored_signing_config_without_overwriting_explicit_environment(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            config_path = root / "signing-config.txt"
            keystore_path = root / "shortlink-release.jks"
            config_path.write_text(
                "ANDROID_SIGNING_STORE_PASSWORD=store-secret\n"
                "ANDROID_SIGNING_KEY_ALIAS=release-key\n"
                "ANDROID_SIGNING_KEY_PASSWORD=key-secret\n",
                encoding="utf-8",
            )
            keystore_path.write_bytes(b"test keystore placeholder")

            with patch.dict(
                os.environ,
                {"ANDROID_SIGNING_KEY_ALIAS": "explicit-alias"},
                clear=True,
            ):
                publisher.load_release_signing_environment(config_path, keystore_path)

                self.assertEqual(str(keystore_path.resolve()), os.environ["ANDROID_SIGNING_STORE_FILE"])
                self.assertEqual("store-secret", os.environ["ANDROID_SIGNING_STORE_PASSWORD"])
                self.assertEqual("explicit-alias", os.environ["ANDROID_SIGNING_KEY_ALIAS"])
                self.assertEqual("key-secret", os.environ["ANDROID_SIGNING_KEY_PASSWORD"])

    def test_local_signing_config_is_gitignored(self):
        gitignore_path = Path(__file__).resolve().parents[2] / ".gitignore"

        self.assertIn(
            "android-app/release-signing/signing-config.txt",
            gitignore_path.read_text(encoding="utf-8").splitlines(),
        )

    def test_rejects_version_code_mismatch(self):
        badging = "package: name='com.reelshort.app' versionCode='27' versionName='0.7.5'"

        with self.assertRaises(publisher.PublishError):
            publisher.verify_badging(badging, "0.7.5", 28)

    @patch("builtins.input", return_value="")
    def test_empty_interactive_release_notes_are_preserved(self, _input):
        args = publisher.argparse.Namespace(
            version="0.8.2",
            version_code=35,
            notes=None,
            mandatory=False,
            minimum_version_code=1,
        )

        _, _, notes = publisher.resolve_inputs(args, {})

        self.assertEqual("", notes)

    def test_release_preflight_sends_publisher_user_agent(self):
        """
        生产入口在 Cloudflare 后面，Python 默认 UA 会被 browser-integrity 校验拦成 403
        "error code: 1010"，fetch_latest_release 会静默返回 None 并让 versionCode 预检失效
        （交互式建议值退化为 1）。这里锁定该请求始终携带发布者 UA。
        """
        captured = {}

        class _Response:
            def __enter__(self):
                return self

            def __exit__(self, *_exc):
                return False

            def read(self):
                return b'{"code":0,"message":"success","data":{"versionName":"0.9.0","versionCode":42}}'

        def fake_urlopen(req, timeout=None):
            captured["headers"] = {k.lower(): v for k, v in req.headers.items()}
            captured["url"] = req.full_url
            return _Response()

        config = {"RELEASE_BACKEND_URL": "https://example.test/", "REELSHORT_INTERNAL_SUPER_TOKEN": "token"}

        with patch.object(urllib.request, "urlopen", fake_urlopen):
            latest = publisher.fetch_latest_release(config)

        self.assertEqual(42, latest["versionCode"])
        self.assertEqual("https://example.test/api/internal/release/latest", captured["url"])
        self.assertEqual(publisher.PUBLISHER_USER_AGENT, captured["headers"]["user-agent"])
        self.assertNotIn("python-urllib", captured["headers"]["user-agent"].lower())

    def test_version_code_suggestion_advances_past_published_release(self):
        config = {"RELEASE_BACKEND_URL": "https://example.test", "REELSHORT_INTERNAL_SUPER_TOKEN": "token"}

        with patch.object(publisher, "fetch_latest_release", return_value={"versionCode": 42}):
            self.assertEqual(43, publisher.suggest_version_code(config))

        with patch.object(publisher, "fetch_latest_release", return_value=None):
            self.assertEqual(1, publisher.suggest_version_code(config))


if __name__ == "__main__":
    unittest.main()
