# SPDX-License-Identifier: GPL-3.0-only
import hashlib,json,xml.etree.ElementTree as ET
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
def main():
 manifest=json.loads((Path(__file__).parent/"approved-icon-manifest.json").read_text())
 def blob(path):
  data=(ROOT/path).read_bytes();return hashlib.sha1(b"blob "+str(len(data)).encode()+b"\0"+data).hexdigest()
 for path,sha in {**manifest["resources"],**manifest["unchangedIdentity"]}.items():
  if blob(path)!=sha:raise AssertionError("Approved icon/identity changed: "+path)
 actual={str(p.relative_to(ROOT)) for p in (ROOT/"app/src").rglob("*ic_launcher*") if p.is_file()}
 if actual!=set(manifest["resources"]):raise AssertionError("Unexpected launcher resource override: "+str(actual-set(manifest["resources"])))
 seen=0
 for path in (ROOT/"app/src").rglob("*.xml"):
  if "/res/values" not in str(path):continue
  for item in ET.parse(path).getroot().findall("string"):
   if item.get("name")=="english_ime_name":
    if item.text!="Tactic Keyboard":raise AssertionError("Variant overrides visible brand: "+str(path))
    seen+=1
 if seen!=4:raise AssertionError("Unexpected app label resource layout; expected main, debug, debugNoMinify and isolated r252cDebug")
 for path in ["app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml","app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml"]:
  icon=ET.parse(ROOT/path).getroot()
  if [item.tag for item in icon]!=["background","foreground","monochrome"]:raise AssertionError("Adaptive/themed icon incomplete")
 print("Branding PASS: exact PR #7 icon resources, no legacy overrides; labels and installation identifiers preserved. One UI physical appearance NOT VERIFIED.")
if __name__=="__main__":main()
