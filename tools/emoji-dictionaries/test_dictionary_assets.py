#!/usr/bin/env python3
"""Decode shipped dictionaries, checking real shortcuts rather than filename presence.
Run with DICTTOOL_JAR=/path/to/verified/dicttool_aosp.jar python3 this_file.py.
"""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'app/src/main/assets/dicts'
DATA = Path(__file__).resolve().parent

class EmojiDictionaryAssetsTest(unittest.TestCase):
    def decode(self, locale):
        dictionary = ASSETS / f'emoji_{locale}.dict'
        self.assertTrue(dictionary.is_file(), f'Missing built-in {locale} emoji dictionary')
        jar = Path(os.environ['DICTTOOL_JAR'])
        manifest = json.loads((DATA / 'provenance.json').read_text())
        self.assertEqual(manifest['builder']['sha256'], hashlib.sha256(jar.read_bytes()).hexdigest())
        self.assertEqual(manifest['outputs'][dictionary.name], hashlib.sha256(dictionary.read_bytes()).hexdigest())
        with tempfile.TemporaryDirectory() as tmp:
            combined = Path(tmp) / 'dictionary.combined'
            subprocess.run(['javac', '-cp', str(jar), '-d', tmp, str(DATA / 'DecodeEmojiDictionary.java')], check=True, capture_output=True)
            result = subprocess.run(['java', '-cp', tmp + os.pathsep + str(jar), 'com.android.inputmethod.latin.makedict.DecodeEmojiDictionary', str(dictionary)], check=True, capture_output=True, text=True)
            combined.write_text(result.stdout)
            words, current = {}, None
            for line in combined.read_text().splitlines():
                if line.startswith(' word='):
                    current = line[6:].split(',f=')[0]
                    words[current] = []
                elif line.startswith('  shortcut='):
                    target, frequency = line[11:].rsplit(',f=', 1)
                    self.assertLess(int(frequency), 15, 'Emoji shortcuts must not use the auto-correction whitelist frequency')
                    words[current].append(target)
            return words

    def test_all_cldr_locales_are_built_in(self):
        manifest = json.loads((DATA / 'provenance.json').read_text())
        self.assertEqual(160, len(manifest['outputs']))
        self.assertEqual(['doi', 'ff', 'mai', 'sa', 'su', 'tt'], manifest['locales_without_emoji_annotations'])
        for filename, digest in manifest['outputs'].items():
            self.assertEqual(digest, hashlib.sha256((ASSETS / filename).read_bytes()).hexdigest(), filename)
        jar = Path(os.environ['DICTTOOL_JAR'])
        with tempfile.TemporaryDirectory() as tmp:
            subprocess.run(['javac', '-cp', str(jar), '-d', tmp, str(DATA / 'DecodeEmojiDictionary.java')], check=True, capture_output=True)
            result = subprocess.run(['java', '-cp', tmp + os.pathsep + str(jar), 'com.android.inputmethod.latin.makedict.DecodeEmojiDictionary', '--check', *[str(ASSETS / name) for name in manifest['outputs']]], check=True, capture_output=True, text=True)
            self.assertEqual(160, len(result.stdout.splitlines()))

    def test_spanish_real_dictionary_contains_canonical_shortcuts(self):
        words = self.decode('es')
        for keyword, emoji in [('gato', '🐈'), ('corazón', '❤️'), ('amor', '🥰'), ('feliz', '😀'), ('fiesta', '🎉')]:
            self.assertIn(keyword, words)
            self.assertIn(emoji, words[keyword], keyword)
        self.assertGreater(len(words), 2000)

    def test_english_real_dictionary_contains_canonical_shortcuts(self):
        words = self.decode('en')
        for keyword, emoji in [('cat', '🐈'), ('heart', '❤️'), ('love', '❤️'), ('happy', '😀'), ('party', '🎉')]:
            self.assertIn(keyword, words)
            self.assertIn(emoji, words[keyword], keyword)
        self.assertGreater(len(words), 2000)

if __name__ == '__main__':
    unittest.main()
