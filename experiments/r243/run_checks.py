#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Cloud-only javac component checks. Never invokes Gradle, builds Android or downloads models."""
import hashlib,json,os,re,subprocess,sys,tempfile,urllib.request
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];HERE=Path(__file__).resolve().parent
def command(args,**kw):return subprocess.run([str(a) for a in args],cwd=ROOT,text=True,check=True,**kw)
def main():
 if not os.environ.get("GITHUB_ACTIONS"):raise SystemExit("Run Java checks on the ephemeral cloud runner, not the phone.")
 with tempfile.TemporaryDirectory(prefix="tactic-components-",dir=os.environ.get("RUNNER_TEMP")) as tmp:
  tmp=Path(tmp);classes=tmp/"classes";classes.mkdir()
  jars=[]
  for relative,sha in [("junit/junit/4.13.2/junit-4.13.2.jar","8ac9e16d933b6fb43bc7f576336b8f4d7eb5ba12"),("org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar","42a25dc3219429f0e5d060061f71acb49bf010a0")]:
   path=tmp/Path(relative).name;urllib.request.urlretrieve("https://repo.maven.apache.org/maven2/"+relative,path)
   if hashlib.sha1(path.read_bytes()).hexdigest()!=sha:raise ValueError("Pinned test dependency integrity failed")
   jars.append(path)
  local=ROOT/"app/src/main/java/helium314/keyboard/latin/ai/local"
  names=["LocalContextReviewer","TypingCandidateRanker","WordBoundarySegmenter","ObservedLexicon","BoundedDictionaryLexicon","ContinuousReviewAnchor","CorrectionFocus","SemanticEditGuard","PersonalStyleProfile"]
  sources=[local/(name+".java") for name in names]+[ROOT/"app/src/main/java/helium314/keyboard/latin/ai/SensitiveInputPolicy.java"]
  tests=["R24TypingTest","R241WritingTest","R241SafetyTest","R242AnchorGuardTest","R242FocusTest"]
  sources+=[ROOT/"app/src/test/java/helium314/keyboard/latin/ai/local"/(name+".java") for name in tests]
  classpath=os.pathsep.join(str(p) for p in jars)
  sources+=list((HERE/"src").glob("*.java"))+[HERE/"tests/HybridWritingTest.java"]
  command(["javac","-cp",classpath,"-d",classes]+sources)
  fullpath=str(classes)+os.pathsep+classpath
  red=subprocess.run(["java","-cp",fullpath,"tactic.experimental.HybridWritingTest","red"],cwd=ROOT,text=True,capture_output=True)
  if red.returncode==0 or "AssertionError" not in red.stderr:raise AssertionError("New contract did not expose unsupported legacy behavior: "+red.stdout+red.stderr)
  print("EXPECTED RED against existing reviewer: "+red.stderr.splitlines()[0],flush=True)
  command(["java","-cp",fullpath,"tactic.experimental.HybridWritingTest"])
  regression=command(["java","-cp",fullpath,"org.junit.runner.JUnitCore"]+["helium314.keyboard.latin.ai.local."+name for name in tests],capture_output=True)
  print(regression.stdout,flush=True)
  command([sys.executable,"-m","unittest","discover","-s",HERE/"tests","-p","test_*.py","-v"])
  command([sys.executable,HERE/"check_branding.py"])
  command([sys.executable,HERE/"evaluate.py","--classes",classes])
  report={"javaRegression":regression.stdout,"newContractLegacyRed":red.stderr.splitlines()[0],"componentChecks":"PASS","androidTestsLintBuild":"NOT RUN: independent linguistic gate is BLOCKED","modelInference":"NOT RUN: independent corpus absent","identity":"unchanged; packaged and physical checks NOT RUN"}
  output=ROOT/"writing-evidence";output.mkdir(exist_ok=True);(output/"r243-component-checks.json").write_text(json.dumps(report,indent=2))
if __name__=="__main__":main()
