import hashlib
import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(__file__))
from make_latest_json import build_manifest


class MakeLatestJsonTest(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.TemporaryDirectory()
        self.apk = os.path.join(self.dir.name, "app-release.apk")
        with open(self.apk, "wb") as f:
            f.write(b"fake apk bytes")

    def tearDown(self):
        self.dir.cleanup()

    def test_builds_manifest_with_hash_and_url(self):
        m = build_manifest(self.apk, 200, "0.2.0", "https://example.com/dl/", "заметки")
        self.assertEqual(m["versionCode"], 200)
        self.assertEqual(m["apkUrl"], "https://example.com/dl/app-release.apk")
        self.assertEqual(m["sha256"], hashlib.sha256(b"fake apk bytes").hexdigest())
        self.assertEqual(m["sizeBytes"], len(b"fake apk bytes"))

    def test_rejects_non_https_or_missing_slash(self):
        with self.assertRaises(SystemExit):
            build_manifest(self.apk, 1, "x", "http://example.com/dl/", "")
        with self.assertRaises(SystemExit):
            build_manifest(self.apk, 1, "x", "https://example.com/dl", "")

    def test_rejects_missing_file_and_bad_code(self):
        with self.assertRaises(SystemExit):
            build_manifest("/no/such.apk", 1, "x", "https://e.com/", "")
        with self.assertRaises(SystemExit):
            build_manifest(self.apk, 0, "x", "https://e.com/", "")


if __name__ == "__main__":
    unittest.main()
