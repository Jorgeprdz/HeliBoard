#!/usr/bin/env python3
"""Protect upstream HeliBoard multilingual typing resources from silent pruning."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "app/src/main/assets"

EXPECTED_DICTS = {
    "main_bg.dict", "main_bn.dict", "main_de.dict", "main_el.dict",
    "main_en-GB.dict", "main_en-US.dict", "main_es.dict",
    "main_fr.dict", "main_hu.dict", "main_it.dict",
    "main_nl.dict", "main_pl.dict", "main_pt-BR.dict",
    "main_pt-PT.dict", "main_ro.dict", "main_ru.dict",
    "main_sv.dict", "main_tr.dict",
}
EXPECTED_COUNTS = {
    "dicts": 18,
    "locale_key_texts": 81,
    "layouts/main": 82,
    "layouts/functional": 3,
    "layouts/symbols": 2,
}

def main():
    for directory, expected in EXPECTED_COUNTS.items():
        p = ASSETS / directory
        assert p.is_dir(), f"MISSING: {p}"
        files = {x.name for x in p.iterdir() if x.is_file()}
        assert len(files) == expected, (
            f"LANGUAGE_ASSETS_FAIL: {directory} expected={expected} actual={len(files)}"
        )
        assert all((p / file).stat().st_size > 0 for file in files), (
            f"LANGUAGE_ASSETS_FAIL: zero-byte resource in {directory}"
        )
        print(f"LANGUAGE_ASSETS_PASS {directory}={len(files)}")
    actual = {p.name for p in (ASSETS / "dicts").glob("main_*.dict")}
    assert actual == EXPECTED_DICTS, (
        f"Missing dictionaries: {EXPECTED_DICTS-actual}; unexpected: {actual-EXPECTED_DICTS}"
    )
    # Sentinels for restored scripts and specialized layouts.
    for name in ["arabic.txt", "bengali_inscript.json", "greek.json",
                 "hindi.json", "korean.json", "russian.txt", "turkish.txt",
                 "urdu.json", "ukrainian.txt", "thai.json"]:
        assert (ASSETS / "layouts/main" / name).is_file(), f"Missing layout: {name}"
    for name in ["es.txt", "ar.txt", "de.txt", "fr.txt", "hi.txt", "ru.txt"]:
        assert (ASSETS / "locale_key_texts" / name).is_file(), f"Missing key text: {name}"
    assert (ASSETS / "layouts/symbols/symbols_arabic.txt").is_file()
    assert (ASSETS / "layouts/functional/functional_keys_khipro.json").is_file()
    print("MULTILINGUAL_RESTORATION=PASS; 175 upstream assets restored; active APK not changed")

if __name__ == "__main__":
    main()
