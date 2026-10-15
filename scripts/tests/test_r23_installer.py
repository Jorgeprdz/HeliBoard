"""Exercise installer gates using controlled gh/ADB/tool boundaries; no device writes."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
import zipfile

SHA = "a" * 40
CERT = "b" * 64
SCRIPT = Path(__file__).resolve().parents[1] / "termux/heliboard-r23-auto-install.sh"

FAKE = r'''#!/usr/bin/env python3
import os,sys,json,hashlib,zipfile,shutil
from pathlib import Path
tool=Path(sys.argv[0]).name
args=sys.argv[1:]
mode=os.environ.get("CASE","good")
sha="a"*40
cert="b"*64
root=Path(os.environ["FIXTURE"])
def apk(p):
    with zipfile.ZipFile(p,"w") as z: z.writestr("AndroidManifest.xml","fixture")
if tool=="gh":
    if args[:2]==["auth","status"]: sys.exit(0)
    if args[0]=="api":
        url=args[1]
        if url.endswith("/permissions"): print("true")
        elif "/workflows/" in url: print("355197651")
        else:
            print(json.dumps(dict(head_sha=("c"*40 if mode=="wrongsha" else sha),
                head_branch="feat/heliboard-ai-r2-3-full-auto-cloud",
                repository=dict(full_name="Jorgeprdz/HeliBoard"),workflow_id=355197651,
                status=("in_progress" if mode=="pending" else "completed"),
                conclusion=("failure" if mode=="failed" else "success"))))
    elif args[:2]==["run","download"]:
        (root/"downloaded").touch()
        dest=Path(args[args.index("-D")+1])
        apk(dest/"HeliBoard-R2.3-debug.apk")
        digest=hashlib.sha256((dest/"HeliBoard-R2.3-debug.apk").read_bytes()).hexdigest()
        if mode=="badsha": digest="0"*64
        (dest/"SHA256SUMS.txt").write_text(digest+"  HeliBoard-R2.3-debug.apk\n")
        (dest/"build-manifest.json").write_text(json.dumps(dict(
            commit=sha,runId="123",package="com.jorgeprdz.ioskeyboard.debug",
            apk="HeliBoard-R2.3-debug.apk",versionCode=4107,sha256=digest,certificateSha256=cert)))
        if mode=="extra": (dest/"unexpected").touch()
elif tool=="aapt":
    v="4106" if sys.argv[-1].endswith("installed.apk") else "4107"
    print("package: name='com.jorgeprdz.ioskeyboard.debug' versionCode='"+v+"' versionName='fixture'")
elif tool=="apksigner":
    if mode=="invalidcert": sys.exit(1)
    if mode=="incompatible" and sys.argv[-1].endswith("installed.apk"): cert="d"*64
    print(("V2 Signer: " if mode=="v2" else "Signer #1 ")+"certificate SHA-256 digest: "+cert)
elif tool=="adb":
    if args==["devices"]:
        print("List of devices attached")
        if mode!="nodevice": print("galaxy\tdevice")
        if mode=="multiple": print("other\tdevice")
    elif "getprop" in args: print("SM-S931B")
    elif "path" in args: print("package:/data/app/fixture/base.apk")
    elif "pull" in args:
        dest=Path(args[-1])
        if dest.name=="post.apk": shutil.copyfile(root/"installed",dest)
        else:
            apk(dest)
            with zipfile.ZipFile(dest,"a") as z: z.writestr("old-version","4106")
    elif "install" in args:
        assert args[-2]=="-r"
        shutil.copyfile(args[-1],root/"installed")
'''
class R23InstallerTest(unittest.TestCase):
    def run_case(self, mode, dry=False):
        with tempfile.TemporaryDirectory(prefix="r23-test-") as d:
            root=Path(d)
            bin=root/"bin";bin.mkdir()
            for tool in ("gh","adb","aapt","apksigner"):
                file=bin/tool;file.write_text(FAKE);file.chmod(0o755)
            env=dict(os.environ, PATH=str(bin)+":"+os.environ["PATH"],
                     FIXTURE=d, CASE=mode, XDG_STATE_HOME=str(root/"state"), TMPDIR=d)
            args=["bash",str(SCRIPT),"--run-id","123","--sha",SHA,"--once","--no-fallback"]
            if dry: args.append("--dry-run")
            result=subprocess.run(args,env=env,text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,timeout=10)
            return result, (root/"installed").exists(), (root/"downloaded").exists(), list(root.glob("heliboard-r23.*"))
    def test_success_and_cleanup(self):
        result,installed,_,temps=self.run_case("good")
        self.assertEqual(0,result.returncode,result.stdout)
        self.assertTrue(installed)
        self.assertIn("INSTALLED & VERIFIED",result.stdout)
        self.assertEqual([],temps)
    def test_v2_signer_installs_without_false_mismatch(self):
        result,installed,_,temps=self.run_case("v2")
        self.assertEqual(0,result.returncode,result.stdout)
        self.assertTrue(installed);self.assertEqual([],temps)

    def test_dry_run_never_installs(self):
        result,installed,_,temps=self.run_case("good",True)
        self.assertEqual(0,result.returncode,result.stdout)
        self.assertFalse(installed)
        self.assertIn("DRY-RUN PASS",result.stdout)
        self.assertEqual([],temps)
    def test_failed_stale_and_pending_runs_do_not_download(self):
        for case in ("failed","wrongsha","pending"):
            with self.subTest(case=case):
                result,installed,downloaded,temps=self.run_case(case)
                self.assertNotEqual(0,result.returncode,result.stdout)
                self.assertFalse(installed);self.assertFalse(downloaded);self.assertEqual([],temps)
    def test_corruption_signature_and_device_gates(self):
        for case in ("badsha","extra","invalidcert","incompatible","nodevice","multiple"):
            with self.subTest(case=case):
                result,installed,_,temps=self.run_case(case)
                self.assertNotEqual(0,result.returncode,result.stdout)
                self.assertFalse(installed);self.assertEqual([],temps)

if __name__=="__main__": unittest.main()
