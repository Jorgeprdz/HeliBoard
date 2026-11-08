#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Derive conservative accent evidence from the shipped Spanish dictionary; --check in CI."""
import collections,hashlib,json,os,re,subprocess,tempfile,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
HERE=Path(__file__).resolve().parent
SOURCE=ROOT/'app/src/main/assets/dicts/main_es.dict'
OUTPUT=ROOT/'app/src/main/assets/spanish-accents.tsv'
FOLD=str.maketrans('áéíóú','aeiou')
def derive(words):
    bare={w.lower() for w in words}
    groups=collections.defaultdict(list)
    for word in words:
        if re.fullmatch('[a-zñüáéíóú]{4,32}',word) and sum(word.count(c) for c in 'áéíóú')==1:
            source=word.translate(FOLD)
            if source not in bare:groups[source].append(word)
    return {source:targets[0] for source,targets in groups.items()
            if len(targets)==1 and words[targets[0]]>=64}
def main():
    jar=Path(os.environ['DICTTOOL_JAR'])
    builder=json.loads((ROOT/'tools/emoji-dictionaries/provenance.json').read_text())['builder']
    assert hashlib.sha256(jar.read_bytes()).hexdigest()==builder['sha256'],'Unverified dicttool'
    with tempfile.TemporaryDirectory() as tmp:
        subprocess.run(['javac','-cp',str(jar),'-d',tmp,str(HERE/'DecodeSpanishDictionary.java')],check=True)
        decoded=subprocess.check_output(['java','-Xmx192m','-cp',tmp+os.pathsep+str(jar),
            'com.android.inputmethod.latin.makedict.DecodeSpanishDictionary',str(SOURCE)],text=True)
    words={}
    for line in decoded.splitlines():
        if line.startswith(' word='):
            word,frequency=line[6:].rsplit(',f=',1);words[word]=int(frequency)
    assert len(words)>200000,'Incomplete dictionary'
    pairs=derive(words)
    assert pairs.get('tambien')=='también'
    assert not set(['termino','continuo','si','el','tu','mas','solo','jose'])&pairs.keys()
    data=''.join(k+'\t'+pairs[k]+'\n' for k in sorted(pairs))
    provenance=json.dumps(dict(source='app/src/main/assets/dicts/main_es.dict',
        source_sha256=hashlib.sha256(SOURCE.read_bytes()).hexdigest(),builder=builder,
        minimum_frequency=64,entries=len(pairs),sha256=hashlib.sha256(data.encode()).hexdigest(),
        policy='One acute, lowercase, unique before frequency filter, no bare Spanish form (case insensitive).',
        license='Derived from the existing bundled Spanish dictionary; same dictionary licensing applies.'),indent=2,ensure_ascii=False)+'\n'
    paths={OUTPUT:data,HERE/'provenance.json':provenance}
    for path,text in paths.items():
        if '--check' in sys.argv:assert path.read_text()==text,'Stale generated file: '+str(path)
        else:path.write_text(text)
    print('Spanish accent index:',len(pairs),'pairs;',len(data.encode()),'bytes; verified' if '--check' in sys.argv else 'bytes; generated')
if __name__=='__main__':main()
