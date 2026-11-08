# R30 Spanish accent recovery implementation plan

**Goal:** Restore unambiguous Spanish acute accents even when permissive configured dictionaries accept the bare spelling, including tambien→también; preserve valid Spanish alternatives, personal entries, rejected pairs and native undo.

**Architecture:** A deterministic index derived from the existing bundled main_es.dict, retaining lowercase targets with one acute, frequency >=64, no bare source anywhere in that Spanish dictionary and exactly one accented target before frequency filtering. Read once on the existing worker, never on IME thread. Apply only for Spanish primary locale after personal/contact/app validity veto, configured target validation and existing protected-context/cancellation/editor-anchor checks. No generative model/network. Initial titlecase allowed only at text start, keep ALL CAPS/mixed case unchanged.

**Alternatives:** Whitelisting a few adverbs misses the requested general behavior. Ignoring all dictionary validity changes meaningful verbs. Chosen general lexical evidence preserves termino/continuo/si/el/tu/mas/solo and ambiguous accented alternatives. Does not infer tense for valid source words.

**Tech stack:** Existing Java/Kotlin IME, pure Java index/lexicon tests, pinned existing dicttool Java reader, deterministic Python generator, existing assets and Gradle CI.

- [ ] Red pure reviewer regression with permissive source validity; controls valid ambiguities, uppercase, protected text, rejection, cancellation and target validity.
- [ ] Generate reproducible public index and provenance; validate exact regeneration from bundled dictionary in CI; parser tests for duplicate/invalid/oversize input.
- [ ] Implement worker-only canonical accent callback, personal source veto and reviewer proposal; green old/new host and native InputLogic transaction/undo/rejection regressions.
- [ ] R30 version20261114, lint/release with bounded memory, signer/native/runtime/appearance/installed SHA checks; real native taps including también and context controls, always finally Termux.
- [ ] Document actual results and limits, copy signed update to Download, push and full CI.

User authorized implementation and installation with “dale”; existing preference is autonomous execution without additional question forms. No new approval gate. Existing feature branch retained; no agents.
