# Conservative Spanish acute evidence

`app/src/main/assets/spanish-accents.tsv` is generated from the existing public bundled `dicts/main_es.dict`. It is not a learned user dictionary or a replacement phrase inventory.

Run with JDK 21 and the same SHA-verified dicttool used by `tools/emoji-dictionaries`:

```sh
DICTTOOL_JAR=/path/to/dicttool_aosp.jar python3 tools/spanish-accents/generate.py
DICTTOOL_JAR=/path/to/dicttool_aosp.jar python3 tools/spanish-accents/generate.py --check
```

The pinned upstream Java trie reader enumerates actual valid entries; NOT_A_WORD records are excluded. Generation groups every lowercase target with exactly one acute before applying the frequency floor of 64. A bare form present anywhere in the Spanish dictionary, ignoring case, vetoes the pair. Multiple accented targets also veto it, even if only one meets the frequency floor. The resulting 10,407 entries occupy 227,881 UTF-8 bytes before APK compression. Provenance records the input, builder and output digests. Dictionary data retain the licensing of the bundled source dictionary.

Runtime reads the immutable index once on the existing review worker. Only the primary Spanish keyboard enables this override. A manual user entry, contact or app name, explicit keep-word, rejected correction, protected context or cancelled/stale review prevents an automatic change. Active dictionaries must also validate the proposed target. Initial titlecase at the start of text is preserved; other capitalizations are excluded. Existing transaction and undo handling applies.

This recovers orthography for forms like `tambien`, `ademas` and `despues`. It does not infer the intended tense or meaning of `termino`, `continuo`, `publico` or `practico`; these have valid bare Spanish forms. The frequency floor is a conservative dictionary filter, not an accuracy measurement.
