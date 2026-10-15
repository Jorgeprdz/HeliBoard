# R2.4 Phase 1 — WIP, no build
Base: `5afae24149164b9c9791e4a103a92e09f5d5c106`.
Spec: `docs/NEXT_PERSONAL_STYLE_PHRASE_COMPLETION.md` on `docs/next-backup-sync-implementation`.
- Balanced local motor-error ranking adds active-layout geometry, weighted omission/repetition/transposition/accent costs, native dictionary priors, context, personal evidence and rejection vetoes.
- Native dictionary-valid words, explicit protected vocabulary, uppercase names/acronyms, code/URLs and private fields retain protection. One existing transaction owns immediate Backspace undo.
- SQLite v1→v2 adds vocabulary/style tables without replacing evidence or changing backup schema. Style is local-only and opt-in; settings inspect/delete aggregates and protect individual words.
- Style ranking changes existing next-word predictions only. Register/connector features are small explicit heuristics, not advanced semantic understanding. It never memorizes complete sentences.
- **Pending:** genuine multiword contextual generation, async model integration/cancellation and phrase acceptance. No fixed phrases, chained n-grams, empty provider adapters or simulated generator ship as substitutes.
- Scoped references reviewed: CleverKeys README/LICENSE (GPL-3.0, local learning and instant predictions); AutoBoard README/LICENSE (GPL-3.0 app, JNI llama.cpp + separately licensed 762 MB Gemma derivative). No third-party code/models copied. Model license, ES/EN quality, memory and latency must be verified before any optional model download.
- No network requests, model downloads, accessibility service, voice changes, Drive changes, keyboard resources or geometry changes.
- Tests added for ranking outcomes, rejection/undo, protected text, opt-in style ordering, SQLite migration, durability and erasure. **Not executed** by explicit instruction; Gradle, lint, compilation and APK are also NOT RUN.
- Commits carry `[skip ci]` to suppress existing push/PR workflows. No workflow disabled or changed. PR stays open against R2.3 and must not be merged as approved.
