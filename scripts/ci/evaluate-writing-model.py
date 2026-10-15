"""Real focused GGUF decoding and the production guard; Linux dictionary fixture != Android acceptance."""
import base64,hashlib,json,os,re,statistics,subprocess,tempfile,urllib.request
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
CASES=json.loads((ROOT/"scripts/tests/fixtures/r242-writing-evaluation.json").read_text())
MODELS=[
 ("Qwen2.5-0.5B-Q4_K_M","Qwen/Qwen2.5-0.5B-Instruct-GGUF","9217f5db79a29953eb74d5343926648285ec7e67","qwen2.5-0.5b-instruct-q4_k_m.gguf","74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db"),
 ("Qwen2.5-1.5B-Q4_K_M","Qwen/Qwen2.5-1.5B-Instruct-GGUF","91cad51170dc346986eccefdc2dd33a9da36ead9","qwen2.5-1.5b-instruct-q4_k_m.gguf","6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e")]
def distance(a,b):
 row=list(range(len(b)+1))
 for i,x in enumerate(a,1):
  next_row=[i]
  for j,y in enumerate(b,1):next_row.append(min(next_row[-1]+1,row[j]+1,row[j-1]+(x!=y)))
  row=next_row
 return row[-1]
def main():
 directory=Path(os.environ["RUNNER_TEMP"])/"writing-models";directory.mkdir(exist_ok=True)
 evidence=ROOT/"writing-evidence";evidence.mkdir(exist_ok=True)
 executable=Path(os.environ["RUNNER_TEMP"])/"writing-host/writing-eval"
 classes=Path(os.environ["RUNNER_TEMP"])/"writing-guard-classes";classes.mkdir(exist_ok=True)
 java_dir=ROOT/"app/src/main/java/helium314/keyboard/latin/ai/local"
 subprocess.run(["javac","-d",str(classes)]+[str(java_dir/name) for name in ["LocalContextReviewer.java","TypingCandidateRanker.java","WordBoundarySegmenter.java","SemanticEditGuard.java","CorrectionFocus.java"]]+[str(ROOT/"scripts/ci/WritingGuardEvaluation.java")],check=True)
 vocabulary=directory/"fixture-vocabulary.txt"
 vocabulary.write_text("\n".join(sorted(set(w.lower() for case in CASES for w in re.findall(r"[^\W\d_]+",case["reference"],re.UNICODE)))))
 def guard(source,output,mode="apply"):
  source_file=directory/"guard-input.txt";result_file=directory/"guard-output.txt";prefix=directory/"guard-comparison"
  source_file.write_text(source);result_file.write_text(output)
  subprocess.run(["java","-cp",str(classes),"WritingGuardEvaluation",str(vocabulary),str(source_file),str(result_file),str(prefix),mode],check=True)
  return prefix
 results=[]
 for name,repo,revision,filename,sha in MODELS:
  model=directory/filename
  if not model.exists():urllib.request.urlretrieve("https://huggingface.co/"+repo+"/resolve/"+revision+"/"+filename,model)
  with model.open("rb") as file:digest=hashlib.file_digest(file,"sha256").hexdigest()
  if digest!=sha:raise ValueError("model integrity mismatch")
  rows=[]
  for case in CASES:
   prefix=guard(case["input"],"","prepare")
   baseline=Path(str(prefix)+".baseline").read_text()
   scopes=Path(str(prefix)+".focus").read_text().splitlines()
   edits=[];raw_edits=[];runs=[];proposals=0
   for scope in scopes:
    start,end,encoded,prompt=scope.split("\t")
    start,end=int(start),int(end);source=base64.b64decode(encoded).decode()
    with tempfile.NamedTemporaryFile(mode="w",encoding="utf-8") as request:
     request.write(base64.b64decode(prompt).decode());request.flush()
     result=subprocess.run([str(executable),str(model),request.name],capture_output=True,text=True,timeout=90)
     repeat=None
     if case["id"]=="user-real":
      second=subprocess.run([str(executable),str(model),request.name],capture_output=True,text=True,timeout=90)
      repeat=dict(completed=second.returncode==0,identical=result.stdout==second.stdout)
    output=result.stdout.strip();metrics={}
    for line in result.stderr.splitlines():
     try:metrics=json.loads(line)
     except json.JSONDecodeError:pass
    prefix=guard(source,output)
    accepted=Path(str(prefix)+".accepted").read_text();count=int(Path(str(prefix)+".count").read_text())
    proposals+=count
    if accepted!=source:edits.append((start,end,accepted))
    if output:raw_edits.append((start,end,output))
    runs.append(dict(original=source,output=output,afterGuard=accepted,completed=result.returncode==0,metrics=metrics,reproducibility=repeat))
   accepted=baseline;raw=baseline
   for start,end,text in reversed(edits):accepted=accepted[:start]+text+accepted[end:]
   for start,end,text in reversed(raw_edits):raw=raw[:start]+text+raw[end:]
   rows.append(dict(id=case["id"],group=case["group"],input=case["input"],reference=case["reference"],
     componentR241Baseline=baseline,componentBaselineDistance=distance(baseline,case["reference"]),inferences=runs,
     afterExplicitAcceptance=accepted,acceptedDistance=distance(accepted,case["reference"]),guardedProposals=proposals,
     guardedProtectedUnchanged=accepted==case["input"] if case.get("protected") else None,
     output=raw,modelDistance=distance(raw,case["reference"]),originalDistance=distance(case["input"],case["reference"])))
  results.append(dict(model=name,sha256=sha,bytes=model.stat().st_size,cases=rows,
   environment="GitHub-hosted Linux CPU, two inference threads; Android input latency, RAM and battery NOT VERIFIED"))
 report=dict(models=results,semanticCorrectionAndroid="NOT VALIDATED",r241ImeComparison="NOT VERIFIED",
  scope="Hybrid R2.4.1 component + actual focused model + production guard with public dictionary fixture; not Android IME acceptance",
  priorParagraphEvaluation="Run 38820291461: both models worsened user paragraph and were rejected; targeted scope evaluated instead")
 report["runtimeFunctional"]="PASS" if all(any(run["completed"] for row in model["cases"] for run in row["inferences"]) for model in results) else "FAIL"
 report["criticalGuard"]="PASS" if all(row["guardedProtectedUnchanged"] is not False for model in results for row in model["cases"]) else "FAIL"
 report["qualityApproval"]="NOT APPROVED: Android IME, latency, RAM, battery and physical acceptance pending"
 (evidence/"real-model-evaluation.json").write_text(json.dumps(report,ensure_ascii=False,indent=2))
 print(json.dumps({model["model"]:dict(actualDecodes=sum(len(row["inferences"]) for row in model["cases"]),
  completed=sum(run["completed"] for row in model["cases"] for run in row["inferences"]),
  improved=sum(row["acceptedDistance"]<row["componentBaselineDistance"] for row in model["cases"]),
  regressed=sum(row["acceptedDistance"]>row["componentBaselineDistance"] for row in model["cases"]),
  userOriginalDistance=model["cases"][0]["originalDistance"],userGuardedDistance=model["cases"][0]["acceptedDistance"])
  for model in results},indent=2))
 if report["criticalGuard"]=="FAIL":raise SystemExit("Real-output protected text integrity gate failed")
 if report["runtimeFunctional"]=="FAIL":raise SystemExit("Real model functionality gate failed")
if __name__=="__main__":main()
