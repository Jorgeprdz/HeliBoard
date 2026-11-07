#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Build offline native shortcut dictionaries from pinned Unicode CLDR annotations."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import tempfile
import tarfile
from functools import lru_cache
import unicodedata
import xml.etree.ElementTree as ET

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
EMOJI_TEST = ROOT / 'tools/make-emoji-keys/src/main/resources/emoji/ucd/16.0/emoji-test.txt'
ASSETS = ROOT / 'app/src/main/assets/dicts'
BUILDER_SHA256 = 'a8c5bd21f631ed0a92235d42d2fe83af5d70216172bf7e22781a9a946858237e'


def checksum(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


@lru_cache(None)
def source_files():
    with tarfile.open(HERE / 'cldr-47-source.tar.gz') as archive:
        return {member.name: archive.extractfile(member).read() for member in archive if member.isfile()}


@lru_cache(None)
def inherited_annotations(locale):
    if locale == 'root':
        return {}  # CLDR root contains internal identifiers, rather than localized search words.
    files = source_files()
    parents = {}
    supplemental = ET.fromstring(files['supplemental/supplementalData.xml'])
    for group in supplemental.findall('parentLocales'):
        if group.get('component'):
            continue
        for item in group.findall('parentLocale'):
            for child in item.get('locales', '').split():
                parents[child] = item.get('parent')
    parent = parents.get(locale, locale.rsplit('_', 1)[0] if '_' in locale else 'root')
    inherited = dict(inherited_annotations(parent))
    for directory in ('annotations', 'annotationsDerived'):
        data = files.get(f'{directory}/{locale}.xml')
        if not data:
            continue
        for annotation in ET.fromstring(data).iter('annotation'):
            if annotation.get('alt') or not annotation.text or annotation.text == '↑↑↑':
                continue
            key = (annotation.get('cp'), annotation.get('type', 'keywords'))
            if annotation.text == '∅∅∅':
                inherited.pop(key, None)
            else:
                inherited[key] = annotation.text
    return inherited


def annotation_shortcuts(locale):
    qualified = {}
    for line in EMOJI_TEST.read_text().splitlines():
        if '; fully-qualified' not in line:
            continue
        points = line.split(';')[0].strip().split()
        emoji = ''.join(chr(int(point, 16)) for point in points)
        qualified[emoji.replace('\ufe0f', '')] = emoji
    words = {}
    for (cp, kind), text in inherited_annotations(locale).items():
        emoji = qualified.get(cp.replace('\ufe0f', ''))
        if not emoji:
            continue
        tts = kind == 'tts'
        for raw in ([text] if tts else text.split('|')):
            word = unicodedata.normalize('NFC', raw.strip().lower())
            # Native combined format uses comma/newline as delimiters and limits words to 48 code points.
            if not word or len(word.encode('utf-16-le')) // 2 >= 48 or any(c in word for c in ',\n\r='):
                continue
            shortcuts = words.setdefault(word, {})
            # 15 is the native auto-correction whitelist flag: never produce that flag.
            shortcuts[emoji] = max(shortcuts.get(emoji, 0), 14 if tts else 12)
    return words


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--dicttool', type=Path, required=True)
    args = parser.parse_args()
    if checksum(args.dicttool) != BUILDER_SHA256:
        raise SystemExit('dicttool does not match the pinned upstream builder SHA-256')
    locales = sorted(Path(name).stem for name in source_files() if name.startswith('annotations/') and name != 'annotations/root.xml')
    manifest = {
        'cldr': {'release': '47', 'commit': '2ef784e3a4168bc2a43cd1b5b9839b6636f5899c',
                 'url': 'https://github.com/unicode-org/cldr/tree/2ef784e3a4168bc2a43cd1b5b9839b6636f5899c/common/annotations',
                 'license': 'Unicode-3.0'},
        'builder': {'url': 'https://codeberg.org/Helium314/aosp-dictionaries/raw/commit/ff0cd52be8d9530200fefe8b296c53d4f662091e/dicttool_aosp.jar',
                    'sha256': BUILDER_SHA256},
        'source_archive': {'file': 'cldr-47-source.tar.gz', 'sha256': checksum(HERE / 'cldr-47-source.tar.gz')},
        'sources': {name: hashlib.sha256(data).hexdigest() for name, data in sorted(source_files().items())},
        'emoji_qualification': {'file': str(EMOJI_TEST.relative_to(ROOT)), 'sha256': checksum(EMOJI_TEST)},
        'outputs': {},
        'locales_without_emoji_annotations': [],
    }
    with tempfile.TemporaryDirectory() as tmp:
        for locale in locales:
            words = annotation_shortcuts(locale)
            if not words:
                manifest['locales_without_emoji_annotations'].append(locale)
                (ASSETS / f"emoji_{locale.replace('_', '-')}.dict").unlink(missing_ok=True)
                continue
            combined = Path(tmp) / f'emoji_{locale}.combined'
            lines = [f'dictionary=emoji:{locale},description=Unicode CLDR 47 emoji annotations,locale={locale},date=1744675200,version=47']
            for word, shortcuts in sorted(words.items()):
                lines.append(f' word={word},f=16,not_a_word=true')
                for emoji, score in sorted(shortcuts.items(), key=lambda item: (-item[1], item[0])):
                    lines.append(f'  shortcut={emoji},f={score}')
            combined.write_text('\n'.join(lines) + '\n')
            output = ASSETS / f"emoji_{locale.replace('_', '-')}.dict"
            subprocess.run(['java', '-jar', str(args.dicttool), 'makedict', '-s', str(combined), '-d', str(output), '-2', '-t', 'off'], check=True, stdout=subprocess.DEVNULL)
            manifest['outputs'][output.name] = checksum(output)
    (HERE / 'provenance.json').write_text(json.dumps(manifest, indent=2) + '\n')
    (ROOT / 'app/src/main/assets/emoji-dictionaries/provenance.json').write_text(json.dumps(manifest, indent=2) + '\n')


if __name__ == '__main__':
    main()
