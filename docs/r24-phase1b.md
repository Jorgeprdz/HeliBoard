# Phase 1B — partial; semantic generation blocked
PR #5 only. No build, tests, lint, Actions, model download, APK or installation.
## Delivered
- Existing local opt-in profile additionally records fixed-category message openings, clause structure and final punctuation; never sentence text.
- Explicit current register overrides habits. Neutral context uses history only after >=5 classified observations and a margin >=3; sparse/ambiguous history abstains.
- Existing prediction ranking uses opening preferences and explicit protected vocabulary. This is **not** semantic generation or personalization of generated continuations.
- Completed-sentence extraction handles ellipses and real newlines; truncated editor context is excluded. Existing disable, inspect and erase controls apply.
## Runtime/model evaluation and exact blocker
- CleverKeys' immediate local predictions/learning were already reviewed in Phase 1; they do not supply the required generative runtime.
- AutoBoard's documented path is JNI llama.cpp + a separately downloaded 762 MB fine-tuned Gemma model, with screen Accessibility deliberately excluded here.
- [LeanType plugin](https://github.com/LeanBitLab/LeanType-Offline-AI-Plugin) uses `io.github.ljcamargo:llamacpp-kotlin:0.4.0`; app GPL-3.0. [Runtime upstream](https://github.com/ljcamargo/kotlinllamacpp) declares ARM64/x86_64 and minSdk 24 in `llamaCpp/build.gradle.kts`. HeliBoard remains minSdk 21. A direct dependency is therefore not treated as a compatible drop-in. No minSdk override or dynamic executable-plugin loader added.
- [Published POM](https://repo.maven.apache.org/maven2/io/github/ljcamargo/llamacpp-kotlin/0.4.0/llamacpp-kotlin-0.4.0.pom) is available; its Apache-2.0 metadata differs from upstream MIT LICENSE. Neither source verification nor the POM proves the published JNI artifact's compatibility; artifact licensing/manifest must be reconciled before distribution.
- Candidate model: [Qwen2.5-0.5B-Instruct-GGUF](https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF), Q4_K_M, official Apache-2.0 model card lists Spanish/English support. This does not establish ES-MX autocomplete quality, S25 latency/RAM/battery or no-hallucination behavior.
- Completing integration requires a compatible optional runtime packaging path, verified ARM64 native/model artifacts, cancellable inference and model-backed evaluation. These cannot be certified by the permitted static-only checks. No runtime/model has been added or selected for release.
## Verification status
- Eight executable tests added for real style functions, IME sentence observation and SQLite durability/deletion; **not executed**. Existing protections and Undo tests remain.
- Multiword relevance, generated-style adaptation, stale-generation cancellation, one-tap phrase acceptance, safe abstention on factual content and no-model fallback remain **BLOCKED: no integrated generator**, rather than mocked passing tests.
- Functional model acceptance must use actual model outputs: ES-MX casual/professional and EN-US matched contexts; multiword suffixes; context/register change; names/acronyms/numbers/negations; cancel/edit/editor switch; one-tap insertion and Backspace; absent-model/native correction coexistence; zero network and protected-field access; RAM/latency and unchanged geometry.
- Static diff/scope/literal checks only. Main keyboard resources, autocorrection behavior code, speech, Drive, build config and workflows unchanged by this extension.
