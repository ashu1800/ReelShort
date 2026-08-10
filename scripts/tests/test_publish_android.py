import importlib.util
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
    def test_accepts_pinned_debug_certificate(self):
        output = (
            "Verified using v2 scheme (APK Signature Scheme v2): true\n"
            f"Signer #1 certificate SHA-256 digest: {publisher.EXPECTED_DEBUG_CERT_SHA256}\n"
        )

        self.assertEqual(
            publisher.EXPECTED_DEBUG_CERT_SHA256,
            publisher.verify_expected_certificate(output),
        )

    def test_rejects_former_release_certificate(self):
        output = (
            "Verified using v2 scheme (APK Signature Scheme v2): true\n"
            "Signer #1 certificate SHA-256 digest: "
            "c1a149d5352c5c313d8d7d995470da31a3bd298281abba914e559747bb45c2f9\n"
        )

        with self.assertRaises(publisher.PublishError):
            publisher.verify_expected_certificate(output)

    def test_release_build_uses_fixed_debug_signing(self):
        gradle_file = Path(__file__).resolve().parents[2] / "android-app" / "app" / "build.gradle.kts"
        gradle = gradle_file.read_text(encoding="utf-8")

        self.assertIn('create("fixedDebug")', gradle)
        self.assertIn('getByName("debug")', gradle)
        self.assertIn('signingConfig = signingConfigs.getByName("fixedDebug")', gradle)
        self.assertNotIn("ANDROID_SIGNING_", gradle)

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
