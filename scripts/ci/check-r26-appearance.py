#!/usr/bin/env python3
"""Keep approved visual blobs while allowing required functional build/provider additions."""
import hashlib
import json
import subprocess
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
BASE = "9451fa9a7ab7994e32c5cbb1b13b8914d741a290"
manifest = json.loads((ROOT / "experiments/r243/approved-icon-manifest.json").read_text())
for name, expected in manifest["resources"].items():
    data = (ROOT / name).read_bytes()
    actual = hashlib.sha1(b"blob " + str(len(data)).encode() + b"\0" + data).hexdigest()
    assert actual == expected, "Approved icon changed: " + name

# Compare against the actual starting revision, not new self-approved visual hashes.
subprocess.run(["git", "cat-file", "-e", BASE], cwd=ROOT, check=True)
changed = subprocess.check_output([
    "git", "diff", "--name-only", BASE, "--", "app/src/main/res",
    "app/src/main/java/helium314/keyboard/latin/common/dynamic",
    "app/src/main/java/helium314/keyboard/keyboard/MainKeyboardView.java",
    "app/src/main/java/helium314/keyboard/keyboard/Key.java",
    "app/src/main/java/helium314/keyboard/settings/screens/AppearanceScreen.kt",
], cwd=ROOT, text=True).splitlines()
# User authorized One UI 8 for secondary menus on 2026-11-07. Only NEW scoped
# menu resources are exempt; approved typing resources/renderers remain immutable.
assert set(changed) <= {
    "app/src/main/res/xml/gif_provider_paths.xml",
    "app/src/main/res/values/oneui_menu.xml",
    "app/src/main/res/values-night/oneui_menu.xml",
    "app/src/main/res/drawable/oneui_menu_dialog.xml",
}, changed
gradle = (ROOT / "app/build.gradle.kts").read_text()
assert 'applicationId = "com.jorgeprdz.ioskeyboard"' in gradle
assert "minSdk = 21" in gradle
assert "if (ownerSigning != null) signingConfig = ownerSigning" in gradle
ns = "{http://schemas.android.com/apk/res/android}"
app = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot().find("application")
assert app.get(ns + "icon") == "@mipmap/ic_launcher"
assert app.get(ns + "roundIcon") == "@mipmap/ic_launcher_round"
print("R26 appearance PASS: approved icons, existing visual resources/renderers and installation identity preserved")
