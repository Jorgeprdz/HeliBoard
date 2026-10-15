"""Validate the assembled APK against release invariants; publish public metadata only."""
import hashlib
import json
import os
from pathlib import Path
import re

root = Path("r23-artifact")
apk_name = os.environ.get("HELIBOARD_APK_NAME", "HeliBoard-R2.3-debug.apk")
assert apk_name in ("HeliBoard-R2.3-debug.apk", "HeliBoard-R2.4-Typing-Preview-debug.apk"), "Unexpected APK name"
apk = root / apk_name
temp = Path(os.environ["RUNNER_TEMP"])
badging = (temp / "r22-badging.txt").read_text()
package = re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging, re.M)
assert package, "Missing APK package metadata"
assert package[1] == "com.jorgeprdz.ioskeyboard.debug", "Wrong applicationId"
assert int(package[2]) == int(os.environ.get("HELIBOARD_VERSION_CODE", "4107")), "Unexpected versionCode"
expected_name = os.environ.get("HELIBOARD_EXPECTED_VERSION_NAME")
if expected_name:
    assert package[3] == expected_name, "Unexpected versionName"
    assert int(package[2]) > 4121, "R2.4 must upgrade R2.3"
# Owner explicitly authorized certificate identity/audit failures to be non-blocking.
# Cryptographic APK verification remains mandatory in the workflow before this script.
certificate_report = (temp / "r22-certificate.txt").read_text()
certs = sorted(set(digest.lower() for digest in re.findall(
    r"^(?:Signer\b|V[0-9]+ Signer\b)[^\r\n]*?certificate SHA-256 digest:[ \t]*([0-9a-fA-F]{64})[ \t]*$",
    certificate_report, re.M)))
warnings = []
if len(certs) != 1:
    warnings.append("Certificate audit found %d distinct identities; identity NOT VERIFIED" % len(certs))
elif certs[0] != os.environ["EXPECTED_CERT"].lower():
    warnings.append("Certificate differs from expected build identity; owner authorized publication")
for warning in warnings:
    print("::warning title=Certificate audit (non-blocking)::" + warning)
summary = os.environ.get("GITHUB_STEP_SUMMARY")
if summary:
    with open(summary, "a") as report:
        report.write("\nCertificate identity audit: **%s** (owner-authorized non-blocking policy).\n"
                     % ("WARNING" if warnings else "PASS"))
        for warning in warnings:
            report.write("- " + warning + "\n")
sha = hashlib.sha256(apk.read_bytes()).hexdigest()
manifest = dict(apk=apk.name,package=package[1],versionCode=int(package[2]),
                versionName=package[3],sha256=sha,certificateSha256=certs[0] if len(certs) == 1 else "",
                certificateSha256s=certs,
                certificateAudit="WARNING" if warnings else "PASS",
                certificateWarnings=warnings,certificateReport=certificate_report,
                commit=os.environ["GITHUB_SHA"],runId=os.environ["GITHUB_RUN_ID"],
                runUrl=os.environ["GITHUB_SERVER_URL"]+"/"+os.environ["GITHUB_REPOSITORY"]+
                       "/actions/runs/"+os.environ["GITHUB_RUN_ID"],
                deviceAcceptance="NOT VERIFIED",
                installedCertificateCompatibility="NOT VERIFIED: compare actual installed signer before any in-place update")
(root / "build-manifest.json").write_text(json.dumps(manifest,indent=2)+"\n")
(root / "SHA256SUMS.txt").write_text(sha+"  "+apk.name+"\n")
print(json.dumps(manifest,indent=2))
