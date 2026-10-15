"""Preview release metadata validation; fixtures are not APK signature tests."""
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / "ci/r23-manifest.py"
NAME = "HeliBoard-R2.4-Typing-Preview-debug.apk"
VERSION = "4.1-iosglass.8-ai-r24-typing-preview"

class R24ManifestTest(unittest.TestCase):
    def generate(self, code="4137", version=VERSION):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "r23-artifact").mkdir()
            (root / "r23-artifact" / NAME).write_bytes(b"metadata-fixture-not-an-apk")
            (root / "r22-badging.txt").write_text(
                "package: name='com.jorgeprdz.ioskeyboard.debug' versionCode='" + code +
                "' versionName='" + version + "'")
            (root / "r22-certificate.txt").write_text(
                "Signer #1 certificate SHA-256 digest: " + "a" * 64)
            env = dict(os.environ, HELIBOARD_APK_NAME=NAME, HELIBOARD_EXPECTED_VERSION_NAME=VERSION,
                       HELIBOARD_VERSION_CODE=code, RUNNER_TEMP=directory, EXPECTED_CERT="a" * 64,
                       GITHUB_SHA="b" * 40, GITHUB_RUN_ID="1", GITHUB_SERVER_URL="https://github.com",
                       GITHUB_REPOSITORY="Jorgeprdz/HeliBoard", GITHUB_STEP_SUMMARY="")
            result = subprocess.run([sys.executable, str(SCRIPT)], cwd=directory, env=env,
                                    capture_output=True, text=True)
            manifest = root / "r23-artifact/build-manifest.json"
            return result, json.loads(manifest.read_text()) if manifest.exists() else None
    def test_preview_preserves_identity_and_names_actual_artifact(self):
        result, manifest = self.generate()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(NAME, manifest["apk"])
        self.assertEqual(VERSION, manifest["versionName"])
        self.assertGreater(manifest["versionCode"], 4121)
        self.assertEqual("com.jorgeprdz.ioskeyboard.debug", manifest["package"])
    def test_preview_cannot_reuse_or_downgrade_r23_version(self):
        result, manifest = self.generate(code="4121")
        self.assertNotEqual(0, result.returncode)
        self.assertIsNone(manifest)
    def test_incorrect_preview_label_blocks_publication(self):
        result, manifest = self.generate(version="old-r23")
        self.assertNotEqual(0, result.returncode)
        self.assertIsNone(manifest)
