# SPDX-License-Identifier: GPL-3.0-only
"""Independent-source synthetic keyboard noise. No engine, candidate vocabulary or reference ranking imports."""
import hashlib,html.parser,json,random,re,subprocess,urllib.request
from pathlib import Path
SEED=2026101601
KINDS=("neighbor","duplicate","omit","transpose","missing-space","concatenation","multiple")
SOURCES=[
 dict(id="inegi-molec2025",language="es-MX",url="https://www.inegi.org.mx/contenidos/saladeprensa/boletines/2025/molec/molec2025_RR.pdf",license="INEGI Terminos de Libre Uso",license_url="https://www.inegi.org.mx/inegi/terminos.html",credit="Fuente: INEGI, Modulo sobre Lectura (MOLEC) 2025",sha256="b16f8369b255d820d90911f335bb12b7016e2230d7256ac99ef790697922936e"),
 dict(id="usgs-hydrology",language="en-US",url="https://www.usgs.gov/water-science-school/science/what-hydrology",license="US Government public domain (USGS-authored text)",license_url="https://www.usgs.gov/information-policies-and-instructions/copyrights-and-credits",credit="U.S. Geological Survey, Water Science School, What is Hydrology?")
]
NEG={"no","ni","sin","nunca","jamás","not","never","neither","nor","cannot","don't","can't","won't"}
class Paragraphs(html.parser.HTMLParser):
 def __init__(self):super().__init__();self.depth=0;self.current=[];self.paragraphs=[]
 def handle_starttag(self,tag,attrs):
  if tag=="p":self.depth+=1
 def handle_endtag(self,tag):
  if tag=="p" and self.depth:
   self.depth-=1
   if not self.depth:self.paragraphs.append("".join(self.current));self.current=[]
 def handle_data(self,text):
  if self.depth:self.current.append(text)
def noise_once(text,kind,rng):
 words=[m for m in re.finditer(r"[^\W\d_]+",text,re.UNICODE) if len(m.group())>=4 and m.group().islower() and m.group() not in NEG]
 if kind in ("missing-space","concatenation"):
  gaps=[m for m in re.finditer(r"(?<=[^\W\d_]) (?=[^\W\d_])",text)]
  if not gaps:raise ValueError("No eligible space")
  first=rng.randrange(len(gaps));selected=gaps[first:first+(2 if kind=="concatenation" else 1)]
  result=text
  for gap in reversed(selected):result=result[:gap.start()]+result[gap.end():]
  return result
 if not words:raise ValueError("No eligible word")
 word=rng.choice(words);positions=list(range(word.start(),word.end()));rng.shuffle(positions)
 rows=["qwertyuiop","asdfghjklñ","zxcvbnm"]
 for pos in positions:
  char=text[pos]
  if kind=="omit":return text[:pos]+text[pos+1:]
  if kind=="duplicate":return text[:pos]+char+text[pos:]
  if kind=="transpose" and pos+1<word.end() and text[pos]!=text[pos+1]:return text[:pos]+text[pos+1]+text[pos]+text[pos+2:]
  if kind=="neighbor":
   location=next(((r,line.index(char)) for r,line in enumerate(rows) if char in line),None)
   if location is None:continue
   r,c=location;neighbors=[]
   for rr,line in enumerate(rows):
    for cc,key in enumerate(line):
     if key!=char and (rr-r)**2+(cc-c+(rr-r)*.25)**2<=1.7:neighbors.append(key)
   if neighbors:return text[:pos]+rng.choice(neighbors)+text[pos+1:]
 raise ValueError("No eligible mutation")
def inject(text,kind,seed):
 rng=random.Random(seed);events=[];result=text
 for operation in (("neighbor","omit","transpose") if kind=="multiple" else (kind,)):
  before=result;result=noise_once(before,operation,rng)
  if before==result:raise ValueError("Mutation did not change input")
  events.append(dict(kind=operation,before=before,after=result))
 return result,events
def normalized(text):return re.sub(r"\s+"," ",text).strip()
def source_windows(text):
 # Disjoint contiguous word windows. Formal fragments, not authored chat or grammar gold labels.
 parts=[];cursor=0;words=list(re.finditer(r"\S+",text))
 while cursor+8<=len(words):
  end=min(cursor+14,len(words));start_offset=words[cursor].start();end_offset=words[end-1].end()
  fragment=text[start_offset:end_offset]
  if 35<=len(fragment)<=180 and len(re.findall(r"\b[a-záéíóúüñ]{4,}\b",fragment))>=3:
   parts.append(dict(original=fragment,start=start_offset,end=end_offset))
  cursor=end
 return parts
def build(directory):
 directory=Path(directory);directory.mkdir(parents=True,exist_ok=True);archive=directory/"sources";archive.mkdir(exist_ok=True)
 pools={};metadata=[]
 for source in SOURCES:
  raw=urllib.request.urlopen(source["url"],timeout=60).read();sha=hashlib.sha256(raw).hexdigest()
  if source.get("sha256") and sha!=source["sha256"]:raise ValueError("Pinned source PDF changed")
  path=archive/(source["id"]+(".pdf" if source["url"].endswith(".pdf") else ".html"));path.write_bytes(raw)
  if path.suffix==".pdf":
   text=subprocess.run(["pdftotext","-enc","UTF-8",str(path),"-"],capture_output=True,text=True,check=True).stdout
  else:
   parser=Paragraphs();parser.feed(raw.decode("utf-8"))
   text="\n".join(parser.paragraphs)
   # Restrict to the USGS-authored article, excluding navigation, footer and image captions.
   start=text.index("Water is one of our most important natural resources.")
   finish=text.find("Sources and more information",start)
   text=text[start:finish if finish>start else len(text)]
  text=normalized(text);(archive/(source["id"]+".txt")).write_text(text)
  windows=source_windows(text);random.Random(SEED+len(metadata)).shuffle(windows)
  for row in windows:row["source_id"]=source["id"]
  pools[source["language"]]=windows
  metadata.append({**source,"download_sha256":sha,"normalized_text_sha256":hashlib.sha256(text.encode()).hexdigest(),"window_count":len(windows)})
  print(source["id"],"eligible windows",len(windows),flush=True)
 records=[];used=set();rng=random.Random(SEED);kinds=list(KINDS)*10;rng.shuffle(kinds)
 def take(language,short=False):
  pool=pools[language]
  for row in pool:
   key=(row["source_id"],row["start"])
   if key not in used and (not short or len(row["original"])<=110):
    used.add(key);return row
  raise ValueError("Insufficient disjoint independent-source excerpts: "+language)
 for group,count in [("es-MX",40),("en-US",20),("mixed",10),("protected",30)]:
  for index in range(count):
   language=group if group!="protected" else ("es-MX" if index<15 else "en-US")
   if group=="mixed":
    parts=[take("es-MX",True),take("en-US",True)];original="; ".join(p["original"] for p in parts)
   else:
    parts=[take(language)];original=parts[0]["original"]
   if len(original.encode("utf-16-le"))//2>256:raise ValueError("Clause limit exceeded")
   case_seed=SEED+len(records);kind="clean" if group=="protected" else kinds.pop()
   corrupted,events=(original,[]) if kind=="clean" else inject(original,kind,case_seed)
   records.append(dict(id=f"{group}-{index+1:02d}",group=group,text=corrupted,reference=original,
    noise_kind=kind,seed=case_seed,mutations=events,source_parts=parts,known_diagnostic=False,
    annotation="synthetic restoration target, NOT human grammar/meaning annotation"))
 provenance=dict(evaluation_type="independent-source synthetic keyboard noise",human_annotated=False,human_blind=False,
  seed=SEED,sources=metadata,source_selection="disjoint 14-word windows, shuffled before inference; no engine-dependent filtering",
  warning="INEGI and USGS formal prose; synthetic code-switching; may contain original author/PDF extraction errors. Not WhatsApp, not independence from LLM pretraining. Corruptions are ours, not endorsed by sources.")
 result=dict(provenance=provenance,messages=records)
 path=directory/"synthetic-corpus.json";path.write_text(json.dumps(result,ensure_ascii=False,indent=2))
 print("Frozen corpus SHA256",hashlib.sha256(path.read_bytes()).hexdigest(),flush=True)
 return path
if __name__=="__main__":
 import sys
 build(sys.argv[1])
