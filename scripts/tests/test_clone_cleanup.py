"""Use real temporary Git repos and a controlled GitHub read boundary."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
SCRIPT=Path(__file__).resolve().parents[1]/"termux/heliboard-r22-clean-clones.sh"
class CleanupTest(unittest.TestCase):
    def fixture(self, protected):
        with tempfile.TemporaryDirectory(prefix="r22-clean-test-") as d:
            root=Path(d);repo=root/"HeliBoard";repo.mkdir()
            git=shutil.which("git")
            def call(*args):
                return subprocess.check_output([git,"-C",str(repo),*args],stderr=subprocess.DEVNULL,text=True).strip()
            call("init")
            (repo/"fixture").write_text("published")
            call("add","fixture")
            call("-c","user.email=test@example.invalid","-c","user.name=fixture","commit","-m","fixture")
            call("remote","add","origin","https://github.com/Jorgeprdz/HeliBoard.git")
            head=call("rev-parse","HEAD")
            call("update-ref","refs/remotes/origin/main",head)
            if protected:
                file=repo/protected;file.parent.mkdir(parents=True,exist_ok=True);file.write_text("UNIQUE CONTENT")
            bin=root/"bin";bin.mkdir()
            fake=bin/"git"
            fake.write_text("#!/bin/bash\nif [[ \"$1\" == ls-remote ]]; then printf '%s\\trefs/heads/main\\n' '"+head+"'; else exec '"+git+"' \"$@\"; fi\n")
            fake.chmod(0o755)
            env=dict(os.environ,PATH=str(bin)+":"+os.environ["PATH"])
            result=subprocess.run(["bash",str(SCRIPT),str(repo)],input="ELIMINAR R2.2\n",env=env,
                                  text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,timeout=15)
            return result,repo.exists(),(repo/protected).read_text() if protected and (repo/protected).exists() else None
    def test_clean_published_clone_is_removable(self):
        result,exists,_=self.fixture(None)
        self.assertEqual(0,result.returncode,result.stdout)
        self.assertFalse(exists,result.stdout)
    def test_unique_metadata_and_backups_are_preserved(self):
        for file in (".git/backup.p12",".git/private.zip",".git/hooks/pre-commit",".git/unique-note","personal.zip","unpublished.txt"):
            with self.subTest(file=file):
                result,exists,content=self.fixture(file)
                self.assertEqual(0,result.returncode,result.stdout)
                self.assertTrue(exists,result.stdout)
                self.assertEqual("UNIQUE CONTENT",content)
if __name__=="__main__": unittest.main()
