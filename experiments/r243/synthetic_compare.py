#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Fresh synthetic component comparison with actual GGUF decoding. Never certifies production."""
import argparse,base64,collections,hashlib,importlib.util,json,os,re,subprocess,time,urllib.request
from pathlib import Path
import evaluate as evaluation
import synthetic_corpus
from synthetic_diagnostics import candidate_diagnosis
ROOT=Path(__file__).resolve().parents[2]
FREQUENCY_REV="525f9b560de45753a5ea01069454e72e9aa541c6"
MODELS=[
 ("qwen05","Qwen/Qwen2.5-0.5B-Instruct-GGUF","9217f5db79a29953eb74d5343926648285ec7e67","qwen2.5-0.5b-instruct-q4_k_m.gguf"),
 ("qwen15","Qwen/Qwen2.5-1.5B-Instruct-GGUF","91cad51170dc346986eccefdc2dd33a9da36ead9","qwen2.5-1.5b-instruct-q4_k_m.gguf")]
def download(url,path):
 with urllib.request.urlopen(url,timeout=90) as source,Path(path).open("wb") as out:
  while True:
   chunk=source.read(1024*1024)
   if not chunk:break
   out.write(chunk)
def vocabulary(directory):
 # Prepared from a fixed pre-existing frequency list, never from benchmark references.
 words={};sources=[]
 for lang in ["es","en"]:
  url=f"https://raw.githubusercontent.com/hermitdave/FrequencyWords/{FREQUENCY_REV}/content/2018/{lang}/{lang}_50k.txt"
  data=urllib.request.urlopen(url,timeout=60).read();(directory/(lang+"-frequency-source.txt")).write_bytes(data)
  sources.append(dict(url=url,sha256=hashlib.sha256(data).hexdigest(),credit="Hermit Dave / FrequencyWords; OpenSubtitles2018",license="CC-BY-SA-4.0"))
  count=0
  for line in data.decode().splitlines():
   word,freq=line.rsplit(" ",1)
   if not word.isalpha() or word!=word.lower() or not 1<=len(word)<=32:continue
   words[word]=max(words.get(word,0),int(freq));count+=1
   if count==10000:break
 path=directory/"independent-vocabulary.tsv"
 header="# CC-BY-SA-4.0; Hermit Dave / FrequencyWords (OpenSubtitles2018); "+FREQUENCY_REV+"\n"
 path.write_text(header+"".join(f"{w}\t{words[w]}\n" for w in sorted(words)),encoding="utf-8")
 metadata=dict(sources=sources,words=len(words),sha256=evaluation.digest(path),limit="first 10000 eligible words per language; no reference-derived additions or bigrams")
 (directory/"vocabulary-provenance.json").write_text(json.dumps(metadata,indent=2))
 return path,set(words)
def verify_versions():
 checks=[]
 for version,sha,names in [
 ("r241","ff27fe9ad30d28b4e5e9e17457db6d27e3487429",["LocalContextReviewer","TypingCandidateRanker","WordBoundarySegmenter"]),
 ("r242","a5d7bbf0d364fd9650a4e304975402662010ae11",["CorrectionFocus","SemanticEditGuard"])]:
  for name in names:
   path="app/src/main/java/helium314/keyboard/latin/ai/local/"+name+".java"
   data=urllib.request.urlopen("https://raw.githubusercontent.com/Jorgeprdz/HeliBoard/"+sha+"/"+path,timeout=30).read()
   if data!=(ROOT/path).read_bytes():raise ValueError("Historical component differs; cannot label current adapter as "+version+": "+name)
   checks.append(dict(version=version,commit=sha,path=path,sha256=hashlib.sha256(data).hexdigest()))
 return checks
def apply(text,changes):
 for start,end,replacement in sorted(changes,reverse=True):text=text[:start]+replacement+text[end:]
 return text
def signature(text):
 tokens=re.findall(r"[\w’'-]+",text,re.UNICODE)
 protected={"no","ni","sin","nunca","jamás","tampoco","not","never","neither","nor","don't","can't","won't","will","shall","must","prometo","pagaré","acepto"}
 return [w for w in tokens if any(c.isdigit() for c in w) or w.lower() in protected or any(c.isupper() for c in w)]
def synthetic_gate(comparison,all_models_completed,critical):
 if not all_models_completed:return "BLOCKED"
 if critical:return "FAIL"
 for group in ["es-MX","en-US"]:
  old=comparison["r241-component"][group]["assisted"]["residual"]
  new=comparison["r243-prototype"][group]["assisted"]["residual"]
  if old==0 or new>old*.8:return "FAIL"
 for group in evaluation.COUNTS:
  for mode in ["automatic","assisted"]:
   a=comparison["r243-prototype"][group][mode]
   for name in ["r241-component","r242-component"]:
    b=comparison[name][group][mode]
    if a["incorrectChanges"]>b["incorrectChanges"] or a["residual"]>b["residual"]:return "FAIL"
 return "PASS"
def main():
 parser=argparse.ArgumentParser();parser.add_argument("--classes",default="");parser.add_argument("--prepare-only",action="store_true");parser.add_argument("--frozen",action="store_true");args=parser.parse_args()
 directory=ROOT/"writing-evidence/r243-synthetic";directory.mkdir(parents=True,exist_ok=True)
 (directory/"status.json").write_text(json.dumps(dict(experimental="RUNNING",productionGate="BLOCKED",scope="Synthetic host components; Android integration NOT RUN")))
 if args.frozen:
  corpus_path=directory/"synthetic-corpus.json";lexicon=directory/"independent-vocabulary.tsv"
  frozen=json.loads((directory/"pre-inference-lock.json").read_text())
  if frozen["commit"]!=os.environ.get("GITHUB_SHA") or frozen["corpus_sha256"]!=evaluation.digest(corpus_path) or frozen["lexicon_sha256"]!=evaluation.digest(lexicon):raise ValueError("Frozen evaluation inputs changed")
  corpus=json.loads(corpus_path.read_text());lexical_words={line.split("\t")[0] for line in lexicon.read_text().splitlines() if not line.startswith("#")}
 else:
  versions=verify_versions()
  lexicon,lexical_words=vocabulary(directory)
  corpus_path=synthetic_corpus.build(directory);corpus=json.loads(corpus_path.read_text())
  frozen=dict(commit=os.environ.get("GITHUB_SHA"),corpus_sha256=evaluation.digest(corpus_path),lexicon_sha256=evaluation.digest(lexicon),seed=synthetic_corpus.SEED,
   selectionBudget="first 8 candidates, then remove tail until prompt <=1800 characters; fixed before inference, no reference access",
   components=versions,productionGate="BLOCKED")
  (directory/"pre-inference-lock.json").write_text(json.dumps(frozen,indent=2))
 if args.prepare_only:
  print("Independent source corpus and external vocabulary frozen; no models executed.",flush=True);return 0
 rows=corpus["messages"]
 model_dir=Path(os.environ["RUNNER_TEMP"])/"r243-comparator-models";model_dir.mkdir(exist_ok=True);models={}
 for key,repo,revision,name in MODELS:
  path=model_dir/name
  if not path.exists():print("Downloading authorized comparator",key,flush=True);download("https://huggingface.co/"+repo+"/resolve/"+revision+"/"+name,path)
  if evaluation.digest(path)!=evaluation.MODEL_SHAS[key]:raise ValueError("Model integrity failure: "+key)
  models[key]=path
 runtime=Path(os.environ["RUNNER_TEMP"])/"writing-host/writing-eval"
 model_counts={key:collections.Counter() for key in models}
 def infer(key,prompt):
  model_counts[key]["attempted"]+=1
  try:
   with __import__("tempfile").NamedTemporaryFile(mode="w",encoding="utf-8") as request:
    request.write(prompt);request.flush()
    process=subprocess.run([str(runtime),str(models[key]),request.name],capture_output=True,text=True,timeout=45)
   metrics={}
   for line in process.stderr.splitlines():
    try:metrics=json.loads(line)
    except json.JSONDecodeError:pass
   completed=process.returncode==0
   model_counts[key]["completed" if completed else "failed"]+=1
   return process.stdout.strip() if completed else "",dict(completed=completed,exitCode=process.returncode,metrics=metrics)
  except subprocess.TimeoutExpired:
   model_counts[key]["failed"]+=1;return "",dict(completed=False,error="decoder process timeout")
 def bridge(mode,text,output=None):
  command=["java","-cp",args.classes,"tactic.experimental.PrototypeBridge",str(lexicon),mode,base64.b64encode(text.encode()).decode()]
  if output is not None:command.append(base64.b64encode(output.encode()).decode())
  process=subprocess.run(command,capture_output=True,text=True,check=True,timeout=60)
  result=[line.split("\t") for line in process.stdout.splitlines()]
  for row in result:
   if row[0] in ("edit","focus"):row[1],row[2]=str(evaluation.codepoint_offset(text,row[1])),str(evaluation.codepoint_offset(text,row[2]))
  return result
 outputs=[];records=directory/"outputs.jsonl"
 with records.open("w") as log:
  for i,row in enumerate(rows):
   source=row["text"];legacy=dict(bridge("legacy-all",source));baseline=base64.b64decode(legacy["automatic"]).decode();legacy_manual=base64.b64decode(legacy["assisted"]).decode()
   focuses=bridge("focus",baseline);legacy_changes=[];focus_trace=[]
   # Real R242: inspect scopes sequentially, expose first validated bounded proposal for one action.
   for _,start,end,encoded_prompt in focuses:
    start,end=int(start),int(end);original=baseline[start:end]
    raw,run=infer("qwen05",base64.b64decode(encoded_prompt).decode())
    changes=[(int(a),int(b),base64.b64decode(value).decode()) for _,a,b,value in bridge("guard",original,raw)] if raw else []
    focus_trace.append(dict(start=start,end=end,original=original,raw=raw,guardAccepted=bool(changes),run=run))
    if changes:
     a,b,value=changes[0];legacy_changes=[(start+a,start+b,value)];break
   candidates=[(int(a),int(b),base64.b64decode(value).decode()) for _,a,b,value in bridge("hybrid",baseline)]
   options=[baseline]+[apply(baseline,[change]) for change in candidates[:8]]
   while len(options)>1 and len(evaluation.selection_prompt(baseline,dict(enumerate(options))))>1800:options.pop()
   selection="0";selection_run=None
   if len(options)>1:selection,selection_run=infer("qwen15",evaluation.selection_prompt(baseline,dict(enumerate(options))))
   index=int(selection) if re.fullmatch(r"\d{1,2}",selection) and int(selection)<len(options) else 0
   chosen=options[index] if index else legacy_manual
   systems={"r241-component":{"automatic":baseline,"assisted":legacy_manual},
    "r242-component":{"automatic":baseline,"assisted":apply(baseline,legacy_changes) if legacy_changes else legacy_manual},
    "r243-prototype":{"automatic":baseline,"assisted":chosen}}
   # Gold is used only here, after generation/ranking/decoding; never supplied to those processes.
   metrics={name:{mode:evaluation.metrics(source,row["reference"],value) for mode,value in modes.items()} for name,modes in systems.items()}
   baseline_gold=evaluation.edits(baseline,row["reference"])
   candidate_hits=[len(evaluation.edits(baseline,apply(baseline,[change]))&baseline_gold) for change in candidates]
   record=dict(id=row["id"],group=row["group"],noise_kind=row["noise_kind"],input=source,reference=row["reference"],
    systems=systems,metrics=metrics,trace=dict(focuses=len(focuses),focusTrace=focus_trace,candidates=candidates,
    candidateHits=candidate_hits,shownCandidates=len(options)-1,selection=selection,selectionRun=selection_run,
    chosenIndex=index,referenceVocabularyMissing=[w for w in re.findall(r"[^\W\d_]+",row["reference"].lower()) if w not in lexical_words]),
    criticalProxy={name:{mode:signature(value)!=signature(row["reference"]) for mode,value in modes.items()} for name,modes in systems.items()},
    meaningAssessment="NOT VERIFIED: no human output annotations; token signature is an incomplete proxy")
   outputs.append(record);log.write(json.dumps(record,ensure_ascii=False)+"\n");log.flush()
   print(f"case {i+1}/100 {row['group']} {row['noise_kind']} candidates={len(candidates)}",flush=True)
 comparison={}
 for system in evaluation.SYSTEMS:
  comparison[system]={}
  for group in list(evaluation.COUNTS)+["ALL"]:
   comparison[system][group]={mode:evaluation.aggregate([r["metrics"][system][mode] for r in outputs if group=="ALL" or r["group"]==group]) for mode in ["automatic","assisted"]}
 critical=sum(v for r in outputs if r["group"]=="protected" for name in r["criticalProxy"].values() for v in name.values())
 complete=all(model_counts[key]["completed"]>0 and model_counts[key]["failed"]==0 for key in models)
 decision=synthetic_gate(comparison,complete,critical)
 by_noise={}
 for kind in list(synthetic_corpus.KINDS)+["clean"]:
  subset=[r for r in outputs if r["noise_kind"]==kind]
  by_noise[kind]=dict(cases=len(subset),noCandidates=sum(not r["trace"]["candidates"] for r in subset),
   candidateOpportunity=sum(any(r["trace"]["candidateHits"]) for r in subset),
   shownCandidateOpportunity=sum(any(r["trace"]["candidateHits"][:r["trace"]["shownCandidates"]]) for r in subset),
   withheldByPromptBudget=sum(candidate_diagnosis(r["trace"]["candidateHits"],r["trace"]["shownCandidates"],r["metrics"]["r241-component"]["automatic"]["tp"],r["metrics"]["r243-prototype"]["assisted"]["tp"])["withheldByPromptBudget"] for r in subset),
   usefulCandidateNotSelected=sum(candidate_diagnosis(r["trace"]["candidateHits"],r["trace"]["shownCandidates"],r["metrics"]["r241-component"]["automatic"]["tp"],r["metrics"]["r243-prototype"]["assisted"]["tp"])["shownUsefulNotSelected"] for r in subset),
   rawModelChangesRejected=sum(bool(t["raw"]) and t["raw"]!=t["original"] and not t["guardAccepted"] for r in subset for t in r["trace"]["focusTrace"]),
   hybridResidual=sum(r["metrics"]["r243-prototype"]["assisted"]["residual"] for r in subset))
 report=dict(experimentalGate=decision,productionGate="BLOCKED",reason="Independent human annotations and real Android acceptance remain required",
  comparisonScope="Actual frozen production Java reviewer/focus/guard components with shared external vocabulary, actual GGUF decoder and isolated hybrid prototype. Complete native Android IMEs NOT RUN.",
  corpusSha256=frozen["corpus_sha256"],lexiconSha256=frozen["lexicon_sha256"],commit=frozen["commit"],
  models={key:dict(model_counts[key]) for key in models},comparativeResults=comparison,diagnosticsByNoise=by_noise,
  criticalProtectedProxyChanges=critical,meaningLoss="NOT VERIFIED: proxy checks do not establish meaning preservation",
  integration="NOT RUN: host engine experiment only",physicalPerformance="NOT VERIFIED")
 (directory/"report.json").write_text(json.dumps(report,ensure_ascii=False,indent=2))
 markdown=["# Tactic Keyboard R2.4.3 — evaluación sintética",f"Commit: {frozen['commit']}",f"Evaluación experimental: **{decision}**. Puerta de producción: **BLOCKED**.",
 "100 casos: 40 ES-MX, 20 EN-US, 10 mezclas sintéticas, 30 correctos/protegidos. Fuentes y semillas en synthetic-corpus.json.",
 "Componentes reales Java y GGUF en Linux; no IME Android ni evaluación humana ciega. Asistido = una sugerencia hipotéticamente aceptada, no aplicada automáticamente.",
 "| Motor | Modo | Corregidos | Residuales | Falsos | Precisión | Recall | F0.5 | Cobertura | Abstenciones |",
 "|---|---|---:|---:|---:|---:|---:|---:|---:|---:|"]
 def fmt(value):return "N/A" if value is None else f"{value:.3f}"
 for name in evaluation.SYSTEMS:
  for mode in ["automatic","assisted"]:
   m=comparison[name]["ALL"][mode]
   markdown.append(f"| {name} | {mode} | {m['tp']} | {m['residual']} | {m['incorrectChanges']} | {fmt(m['precision'])} | {fmt(m['recall'])} | {fmt(m['f05'])} | {fmt(m['coverage'])} | {m['abstentions']} |")
 markdown+=["","## Diferencia frente al baseline (residuales; negativo = mejora)","| Grupo | R241 asistido | R242 asistido | R243 asistido | Cambio R243/R241 |","|---|---:|---:|---:|---:|"]
 for group in list(evaluation.COUNTS)+["ALL"]:
  a=comparison["r241-component"][group]["assisted"]["residual"];b=comparison["r242-component"][group]["assisted"]["residual"];c=comparison["r243-prototype"][group]["assisted"]["residual"]
  markdown.append(f"| {group} | {a} | {b} | {c} | {100*(c-a)/a:.1f}% |" if a else f"| {group} | {a} | {b} | {c} | N/A |")
 markdown+=["","## Diagnóstico por ruido","| Ruido | Casos | Sin candidatos | Útil generado | Útil mostrado | Retenido por presupuesto | Mostrado útil no seleccionado | Cambio crudo rechazado | Residuales R243 |","|---|---:|---:|---:|---:|---:|---:|---:|---:|"]
 for kind,d in by_noise.items():markdown.append(f"| {kind} | {d['cases']} | {d['noCandidates']} | {d['candidateOpportunity']} | {d['shownCandidateOpportunity']} | {d['withheldByPromptBudget']} | {d['usefulCandidateNotSelected']} | {d['rawModelChangesRejected']} | {d['hybridResidual']} |")
 markdown+=["","## Ejemplos (no seleccionados para ajustar el motor)"]
 example_rows=[r for r in outputs if r["systems"]["r243-prototype"]["assisted"]!=r["input"]][:3]+[r for r in outputs if r["noise_kind"] in ["multiple","concatenation"]][:2]
 for row in example_rows:
  markdown.extend([f"- {row['id']} ({row['noise_kind']}):",f"  - Entrada: {row['input']}",f"  - R243 asistido: {row['systems']['r243-prototype']['assisted']}",f"  - Referencia sintética: {row['reference']}"])
 markdown+=["","## Límites y decisión","- Métricas de eventos exactos de edición de caracteres, no comprensión semántica ni porcentaje de intención preservada.",
 "- Pérdida de significado: NO VERIFICADA por humanos. El proxy de negaciones/números/nombres no demuestra equivalencia.",
 f"- Inferencias reales por modelo: {json.dumps(report['models'])}. Casos sin candidatos no llaman al modelo 1.5B; no se contabilizan como inferencias exitosas.",
 "- Registro formal INEGI/USGS, fragmentos y mezcla artificial; errores originales y extracción PDF pueden afectar referencias.",
 "- Vocabulario externo de frecuencias, sin bigramas; no equivale a diccionarios nativos completos ni a la geometría medida de un S25.",
 "- Integración, cursor, WhatsApp y rendimiento físico: NOT RUN. Branding, icono y producción sin cambios.",
 "- No se ajustaron prompts, umbrales ni modelos tras observar este corpus. A/B/C excluidos del benchmark.",
 "- Reproducción: conservar corpus, fuentes, semilla, pre-inference-lock.json, vocabulario/licencias y salidas JSONL del artefacto.",
 "- Si falla: priorizar cobertura de candidatos, selección/ranking y alcance de guard; distinguir abstención deliberada de un defecto. No relajar protecciones para mejorar la tabla.",
 "- Puerta de producción BLOCKED independientemente del resultado sintético; no APK."]
 (directory/"REPORT.md").write_text("\n".join(markdown)+"\n")
 print(json.dumps({k:v for k,v in report.items() if k!="comparativeResults"},indent=2),flush=True)
 return 0
if __name__=="__main__":raise SystemExit(main())
