"""Behavioral regressions for the owner-authorized non-blocking certificate audit."""
import contextlib
import io
import json
import os
from pathlib import Path
import unittest
from unittest.mock import patch

MANIFEST_SOURCE = globals().get("MANIFEST_SOURCE")
if MANIFEST_SOURCE is None:
    MANIFEST_SOURCE = (Path(__file__).resolve().parents[1] / "ci/r22-manifest.py").read_text()

CERT = "a" * 64
OTHER = "b" * 64
BADGING = "package: name='com.jorgeprdz.ioskeyboard.debug' versionCode='4106' versionName='test'"

class ManifestCertificateTest(unittest.TestCase):
    def generate(self, certificate, badging=BADGING):
        # Mock only filesystem/environment boundaries; execute the real manifest script.
        inputs = {"r22-badging.txt": badging, "r22-certificate.txt": certificate}
        outputs = {}
        def read_text(path, *args, **kwargs):
            return inputs[path.name]
        def write_text(path, content, *args, **kwargs):
            outputs[path.name] = content
            return len(content)
        env = dict(RUNNER_TEMP="/runner-temp", EXPECTED_CERT=CERT,
                   GITHUB_SHA="c" * 40, GITHUB_RUN_ID="123", GITHUB_STEP_SUMMARY="",
                   GITHUB_SERVER_URL="https://github.com",
                   GITHUB_REPOSITORY="Jorgeprdz/HeliBoard")
        with patch.dict(os.environ, env), \
             patch.object(Path, "read_text", read_text), \
             patch.object(Path, "read_bytes", return_value=b"abc"), \
             patch.object(Path, "write_text", write_text), \
             contextlib.redirect_stdout(io.StringIO()):
            exec(compile(MANIFEST_SOURCE, "r22-manifest.py", "exec"), {})
        manifest = json.loads(outputs["build-manifest.json"])
        self.assertEqual(manifest["sha256"],
                         "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
        self.assertEqual(outputs["SHA256SUMS.txt"],
                         manifest["sha256"] + "  HeliBoard-R2.2-debug.apk\n")
        return manifest

    def test_matching_certificate_records_pass(self):
        result = self.generate("Signer #1 certificate SHA-256 digest: " + CERT + "\n")
        self.assertEqual(result["certificateSha256"], CERT)
        self.assertEqual(result["certificateAudit"], "PASS")

    def test_different_identity_publishes_actual_certificate_with_warning(self):
        result = self.generate("Signer #1 certificate SHA-256 digest: " + OTHER + "\n")
        self.assertEqual(result["certificateSha256"], OTHER)
        self.assertEqual(result["certificateAudit"], "WARNING")
        self.assertTrue(result["certificateWarnings"])

    def test_unrecognized_output_does_not_invent_certificate_or_block_publication(self):
        result = self.generate("New signer output format\n")
        self.assertEqual(result["certificateSha256"], "")
        self.assertEqual(result["certificateAudit"], "WARNING")
        self.assertEqual(result["certificateReport"], "New signer output format\n")

    def test_platform_range_format_extracts_real_digest(self):
        result = self.generate("Signer (minSdkVersion=28, maxSdkVersion=35) #1 certificate SHA-256 digest: " + CERT.upper() + "\n")
        self.assertEqual(result["certificateSha256"], CERT)
        self.assertEqual(result["certificateAudit"], "PASS")

    def test_repeated_same_digest_is_one_identity(self):
        result = self.generate(("Signer #1 certificate SHA-256 digest: " + CERT + "\n") * 2)
        self.assertEqual(result["certificateSha256"], CERT)
        self.assertEqual(result["certificateAudit"], "PASS")

    def test_multiple_identities_preserve_evidence_and_warn(self):
        result = self.generate("Signer #1 certificate SHA-256 digest: " + CERT +
                               "\nSigner #2 certificate SHA-256 digest: " + OTHER + "\n")
        self.assertEqual(result["certificateSha256"], "")
        self.assertEqual(result["certificateSha256s"], [CERT, OTHER])
        self.assertEqual(result["certificateAudit"], "WARNING")

    def test_wrong_package_still_blocks(self):
        with self.assertRaisesRegex(AssertionError, "Wrong applicationId"):
            self.generate("", BADGING.replace("com.jorgeprdz.ioskeyboard.debug", "wrong.package"))

    def test_wrong_version_still_blocks(self):
        with self.assertRaisesRegex(AssertionError, "Unexpected versionCode"):
            self.generate("", BADGING.replace("4106", "1"))

if __name__ == "__main__":
    unittest.main()
