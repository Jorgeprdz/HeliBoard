#!/usr/bin/env python3
"""Offline component evaluator. Missing independent human evidence means BLOCKED, never fabricated PASS."""
import argparse,base64,difflib,hashlib,json,re,subprocess,tempfile
from pathlib import Path
COUNTS={"es-MX":40,"en-US":20,"mixed":10,"protected":30}
SYSTEMS=("r241-component","r242-component","r243-prototype")
MODEL_SHAS={"qwen05":"74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db",
 "qwen15":"6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e"}
def digest(path):
 with Path(path).open("rb") as stream:return hashlib.file_digest(stream,"sha256").hexdigest()
def validate_corpus(data,lexicon):
 meta=data.get("provenance",{});rows=data.get("messages",[])
 if not all(meta.get(k) is True for k in ["independent_from_development","human_annotated","publishable"]):raise ValueError("Independent human annotated publishable corpus required")
 if len(set(meta.get("human_reviewers",[])))<2 or not meta.get("source") or not meta.get("license"):raise ValueError("Two reviewers, provenance and license required")
 if meta.get("lexicon_sha256")!=digest(lexicon) or not meta.get("lexicon_source") or meta.get("lexicon_from_references") is not False:raise ValueError("Independent fixed vocabulary required")
 if len({r["id"] for r in rows})!=len(rows):raise ValueError("Duplicate IDs")
 if {g:sum(r["group"]==g for r in rows) for g in COUNTS}!=COUNTS or len(rows)!=100:raise ValueError("Expected 40/20/10/30 messages")
 for row in rows:
  if not isinstance(row.get("text"),str) or not isinstance(row.get("reference"),str) or not row.get("annotators"):raise ValueError("Missing annotations")
  if len(row["text"].encode("utf-16-le"))//2>256:raise ValueError("Evaluation unit must be a bounded clause, not a paragraph")
  if row["group"]=="protected" and row["text"]!=row["reference"]:raise ValueError("Protected controls must be unchanged")
  if row.get("known_diagnostic") is not False:raise ValueError("A/B/C and development examples are not blind controls")
 return rows
def codepoint_offset(text,utf16_offset):
 return len(text.encode("utf-16-le")[:int(utf16_offset)*2].decode("utf-16-le"))
def edits(source,output):
 return {(a,b,output[c:d]) for tag,a,b,c,d in difflib.SequenceMatcher(a=source,b=output,autojunk=False).get_opcodes() if tag!="equal"}
def metrics(source,reference,output):
 gold,predicted=edits(source,reference),edits(source,output)
 tp=len(gold&predicted);fp=len(predicted-gold);fn=len(gold-predicted)
 return dict(tp=tp,fp=fp,fn=fn,residual=fn+fp,changed=output!=source,abstained=output==source,opportunities=len(gold))
def aggregate(rows):
 tp=sum(r["tp"] for r in rows);fp=sum(r["fp"] for r in rows);fn=sum(r["fn"] for r in rows)
 precision=tp/(tp+fp) if tp+fp else None;recall=tp/(tp+fn) if tp+fn else None
 f05=1.25*tp/(1.25*tp+.25*fn+fp) if tp+fp+fn else None
 return dict(precision=precision,recall=recall,f05=f05,tp=tp,incorrectChanges=fp,residual=fn+fp,
  coverage=sum(r["changed"] for r in rows)/len(rows) if rows else None,abstentions=sum(r["abstained"] for r in rows))
def gate(comparison,meaning_reviewed):
 if not meaning_reviewed:return "BLOCKED"
 for language in ["es-MX","en-US"]:
  baseline=comparison["r241-component"][language]["automatic"]
  candidate=comparison["r243-prototype"][language]["assisted"]
  candidate_auto=comparison["r243-prototype"][language]["automatic"]
  if baseline["residual"]==0 or candidate["residual"]>baseline["residual"]*.8:return "FAIL"
  if candidate["incorrectChanges"]>baseline["incorrectChanges"] or candidate_auto["incorrectChanges"]>baseline["incorrectChanges"]:return "FAIL"
 for system in comparison.values():
  if system["protected"]["automatic"]["incorrectChanges"] or system["protected"]["assisted"]["incorrectChanges"]:return "FAIL"
 return "PASS"
def model_output(executable,model,prompt):
 with tempfile.NamedTemporaryFile(mode="w",encoding="utf-8") as request:
  request.write(prompt);request.flush()
  result=subprocess.run([str(executable),str(model),request.name],capture_output=True,text=True,timeout=45,check=True)
  return result.stdout.strip()
def selection_prompt(clause,options):
 return "<|im_start|>system\nSelect the single minimal correction that preserves meaning, names, numbers, negation and commitments. Do not rewrite. Return ONLY its integer ID. Select 0 if uncertain.\n<|im_end|>\n<|im_start|>user\n"+json.dumps({"clause":clause,"options":options},ensure_ascii=False)+"<|im_end|>\n<|im_start|>assistant\n"
def run(args):
 if not args.corpus or not Path(args.corpus).is_file():return dict(gate="BLOCKED",reason="No independent 40/20/10/30 human-annotated corpus supplied",comparativeResults=None)
 if not args.lexicon or not Path(args.lexicon).is_file():return dict(gate="BLOCKED",reason="Independent fixed vocabulary missing",comparativeResults=None)
 data=json.loads(Path(args.corpus).read_text());rows=validate_corpus(data,args.lexicon)
 if not all(getattr(args,k) and Path(getattr(args,k)).is_file() for k in ["runtime","qwen05","qwen15"]):return dict(gate="BLOCKED",reason="Existing host runtime and both pinned comparator models required; no automatic downloads",comparativeResults=None)
 for key in MODEL_SHAS:
  if digest(getattr(args,key))!=MODEL_SHAS[key]:raise ValueError("Model integrity mismatch: "+key)
 classes=args.classes
 def bridge(mode,text,output=None):
  command=["java","-cp",classes,"tactic.experimental.PrototypeBridge",args.lexicon,mode,base64.b64encode(text.encode()).decode()]
  if output is not None:command.append(base64.b64encode(output.encode()).decode())
  result=subprocess.run(command,capture_output=True,text=True,check=True,timeout=15)
  rows=[line.split("\t") for line in result.stdout.splitlines()]
  for row in rows:
   if row[0] in ("edit","focus"):
    row[1],row[2]=str(codepoint_offset(text,row[1])),str(codepoint_offset(text,row[2]))
  return rows
 def apply(text,changes):
  for a,b,value in sorted(changes,reverse=True):text=text[:a]+value+text[b:]
  return text
 outputs=[]
 # References remain in this evaluator; bridge/model only receive input and external lexicon.
 for row in rows:
  source=row["text"];baseline=base64.b64decode(bridge("legacy",source)[0][1]).decode()
  legacy_edits=[];focuses=bridge("focus",baseline)
  for _,start,end,prompt in focuses:
   target=baseline[int(start):int(end)]
   result=model_output(args.runtime,args.qwen05,base64.b64decode(prompt).decode())
   guarded=bridge("guard",target,result)
   for _,a,b,value in guarded:legacy_edits.append((int(start)+int(a),int(start)+int(b),base64.b64decode(value).decode()))
  alternatives=[baseline]
  for _,a,b,value in bridge("hybrid",baseline):
   alternatives.append(apply(baseline,[(int(a),int(b),base64.b64decode(value).decode())]))
  answer=model_output(args.runtime,args.qwen15,selection_prompt(baseline,dict(enumerate(alternatives)))) if len(alternatives)>1 else "0"
  index=int(answer) if re.fullmatch(r"\d{1,2}",answer) and int(answer)<len(alternatives) else 0
  chosen=alternatives[index]
  # Only a prevalidated bounded candidate can be selected. No free generated text applies.
  # Valid-word edits stay explicit suggestions and still require output-specific human meaning review.
  outputs.append(dict(id=row["id"],group=row["group"],input=source,reference=row["reference"],systems={
   "r241-component":{"automatic":baseline,"assisted":baseline},
   "r242-component":{"automatic":baseline,"assisted":apply(baseline,legacy_edits)},
   "r243-prototype":{"automatic":baseline,"assisted":chosen}}))
 comparison={}
 for name in SYSTEMS:
  comparison[name]={}
  for group in COUNTS:
   comparison[name][group]={mode:aggregate([metrics(r["input"],r["reference"],r["systems"][name][mode]) for r in outputs if r["group"]==group]) for mode in ["automatic","assisted"]}
 sealed=hashlib.sha256(json.dumps(outputs,sort_keys=True,ensure_ascii=False).encode()).hexdigest()
 reviewed=False;critical=False
 if args.meaning_review and Path(args.meaning_review).is_file():
  review=json.loads(Path(args.meaning_review).read_text())
  required={(r["id"],s,mode) for r in outputs for s in SYSTEMS for mode in ["automatic","assisted"] if r["systems"][s][mode]!=r["input"]}
  records=review.get("reviews",[])
  reviewed=review.get("outputs_sha256")==sealed and {(r["id"],r["system"],r["mode"]) for r in records}==required and all(len(set(r.get("human_reviewers",[])))>=2 and isinstance(r.get("preserves_intent"),bool) for r in records)
  critical=any(r.get("preserves_intent") is False for r in records)
 decision=gate(comparison,reviewed)
 if reviewed and critical:decision="FAIL"
 return dict(gate=decision,comparisonScope="Shared external-dictionary components, not Android IME acceptance or the complete native baseline",
  corpusSha256=digest(args.corpus),lexiconSha256=digest(args.lexicon),outputs_sha256=sealed,
  semantics="human-reviewed" if reviewed else "BLOCKED: output-specific human meaning review required",
  metric="Exact character-span edit events; auxiliary to bilingual human review, not ERRANT/WER or calibrated confidence",
  comparativeResults=comparison,outputs=outputs,physicalPerformance="NOT VERIFIED")
def main():
 parser=argparse.ArgumentParser()
 for key in ["corpus","lexicon","runtime","qwen05","qwen15","meaning-review"]:parser.add_argument("--"+key)
 parser.add_argument("--classes",default="");parser.add_argument("--out",default="writing-evidence/r243-prototype.json")
 args=parser.parse_args()
 try:report=run(args)
 except (ValueError,KeyError,TypeError,subprocess.SubprocessError) as error:report=dict(gate="BLOCKED",reason=str(error),comparativeResults=None)
 path=Path(args.out);path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(report,ensure_ascii=False,indent=2))
 print(json.dumps({k:v for k,v in report.items() if k not in ["outputs","comparativeResults"]},indent=2))
 return 1 if report["gate"]=="FAIL" else 0
if __name__=="__main__":raise SystemExit(main())
