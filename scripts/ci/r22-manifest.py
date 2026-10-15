"""Validate the assembled APK against release invariants; publish public metadata only."""
import hashlib
import json
import os
from pathlib import Path
import re

root = Path("r22-artifact")
apk = root / "HeliBoard-R2.2-debug.apk"
temp = Path(os.environ["RUNNER_TEMP"])
badging = (temp / "r22-badging.txt").read_text()
package = re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging, re.M)
assert package, "Missing APK package metadata"
assert package[1] == "com.jorgeprdz.ioskeyboard.debug", "Wrong applicationId"
assert int(package[2]) == 4106, "Unexpected versionCode"
certs = re.findall(r"^Signer #\d+ certificate SHA-256 digest: (\w+)$",
                   (temp / "r22-certificate.txt").read_text(), re.M)
assert len(certs) == 1, "Unexpected signer count"
assert certs[0].lower() == os.environ["EXPECTED_CERT"], "Wrong signing identity"
sha = hashlib.sha256(apk.read_bytes()).hexdigest()
manifest = dict(apk=apk.name,package=package[1],versionCode=int(package[2]),
                versionName=package[3],sha256=sha,certificateSha256=certs[0].lower(),
                commit=os.environ["GITHUB_SHA"],runId=os.environ["GITHUB_RUN_ID"],
                runUrl=os.environ["GITHUB_SERVER_URL"]+"/"+os.environ["GITHUB_REPOSITORY"]+
                       "/actions/runs/"+os.environ["GITHUB_RUN_ID"],
                deviceAcceptance="NOT VERIFIED",
                installedCertificateCompatibility="NOT VERIFIED: installer must compare")
(root / "build-manifest.json").write_text(json.dumps(manifest,indent=2)+"\n")
(root / "SHA256SUMS.txt").write_text(sha+"  "+apk.name+"\n")
print(json.dumps(manifest,indent=2))
