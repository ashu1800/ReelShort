import importlib.util
import unittest
from pathlib import Path


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

        self.assertEqual(publisher.EXPECTED_RELEASE_CERT_SHA256, publisher.verify_expected_certificate(output))

    def test_rejects_unexpected_release_certificate(self):
        output = (
            "Verified using v2 scheme (APK Signature Scheme v2): true\n"
            "Signer #1 certificate SHA-256 digest: "
            "0000000000000000000000000000000000000000000000000000000000000000\n"
        )

        with self.assertRaises(publisher.PublishError):
            publisher.verify_expected_certificate(output)

    def test_rejects_version_code_mismatch(self):
        badging = "package: name='com.reelshort.app' versionCode='27' versionName='0.7.5'"

        with self.assertRaises(publisher.PublishError):
            publisher.verify_badging(badging, "0.7.5", 28)


if __name__ == "__main__":
    unittest.main()
