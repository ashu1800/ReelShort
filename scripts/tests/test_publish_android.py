import importlib.util
import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


def load_publisher():
    script = Path(__file__).resolve().parents[1] / "publish-android.py"
    spec = importlib.util.spec_from_file_location("publish_android", script)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


publisher = load_publisher()


class PublishAndroidTests(unittest.TestCase):
    def test_accepts_pinned_release_certificate(self):
        output = (
            "Verified using v2 scheme (APK Signature Scheme v2): true\n"
            f"Signer #1 certificate SHA-256 digest: {publisher.EXPECTED_RELEASE_CERT_SHA256}\n"
        )

        self.assertEqual(
            publisher.EXPECTED_RELEASE_CERT_SHA256,
            publisher.verify_expected_certificate(output),
        )

    def test_rejects_debug_certificate(self):
        output = (
            "Verified using v2 scheme (APK Signature Scheme v2): true\n"
            "Signer #1 certificate SHA-256 digest: "
            "7bb921b325b079400c9fa4fc322e6a903571f688f35ce6ab284baf32d0cca436\n"
        )

        with self.assertRaises(publisher.PublishError):
            publisher.verify_expected_certificate(output)

    def test_all_android_variants_use_only_official_signing(self):
        gradle_file = Path(__file__).resolve().parents[2] / "android-app" / "app" / "build.gradle.kts"
        gradle = gradle_file.read_text(encoding="utf-8")

        self.assertIn('environmentVariable("ANDROID_SIGNING_STORE_FILE")', gradle)
        self.assertIn('create("official")', gradle)
        self.assertEqual(2, gradle.count('signingConfig = signingConfigs.getByName("official")'))
        self.assertIn(publisher.EXPECTED_RELEASE_CERT_SHA256, gradle)
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


if __name__ == "__main__":
    unittest.main()
