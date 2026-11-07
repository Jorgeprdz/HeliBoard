# Bundled emoji dictionaries

All 160 locales with actual emoji annotations in Unicode CLDR 47 are built into
the app. Sources cover 166 non-root locales plus derived annotations, including
flags and qualified sequences. Six source locales contain only non-emoji symbols
(doi, ff, mai, sa, su, tt); no empty or invented dictionaries are shipped.
Keywords and localized text-to-speech names come exclusively from pinned Unicode
CLDR data, under Unicode License V3; no Signal search-index data is included.

`cldr-47-source.tar.gz` contains the exact annotation, derived-annotation and
supplemental parent-locale XML inputs from CLDR commit
`2ef784e3a4168bc2a43cd1b5b9839b6636f5899c`. Each input and resulting native
binary has its SHA-256 recorded in `provenance.json`. The qualification mapping
uses the repository's Unicode 16.0 emoji-test data. Regional locales inherit CLDR
parents, and explicit inheritance/removal markers are respected. Internal root
identifiers are excluded from user search keywords.

Obtain the pinned builder from the URL in `provenance.json`, then run:

```sh
python3 tools/emoji-dictionaries/build.py --dicttool /path/to/dicttool_aosp.jar
DICTTOOL_JAR=/path/to/dicttool_aosp.jar python3 tools/emoji-dictionaries/test_dictionary_assets.py
```

The builder validates the tool hash before use. The test reader decodes actual
binary trie records with upstream Java code, independently of the generator and
without loading Android JNI. It checks all dictionary tries, keyword flags and
shortcut frequencies, plus real Spanish and English keyword-to-emoji mappings.
Shortcut scores stay below native whitelist frequency 15 so emojis are offered
for explicit selection and cannot become whitelist auto-corrections.

The distributed license and provenance are also included in
`app/src/main/assets/emoji-dictionaries/`.
