# Text stickers Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Generate, preview, save and send transparent text stickers within the keyboard.
**Architecture:** Isolated local renderer/store and automatic preview in an existing suggestion slot; reuse GIF content permissions and session guard.
**Tech Stack:** Android Java Canvas, PNG, FileProvider, InputConnectionCompat.
**Spec:** docs/superpowers/specs/2026-11-07-text-stickers-design.md

## Global Constraints
- minSdk21; preserve existing appearance; no new dependencies or credentials.
- 512x512 PNG, maximum120 Unicode code points,40 saved stickers.

## Review Focus
- Emoji/accents/RTL/very long words: valid PNG and nonclipped visible text.
- Empty or overlimit input: reject clearly.
- Saved state persists restart and never exceeds40 files.
- Editor switch/disposal: stale content never goes to new editor.
- Unsupported image receiver: explicit status with no app/popup fallback.

### Task1: Renderer/store and panel integration
- [ ] Write tests for validation, transparent PNG dimensions/content, saved persistence/bounds, and editor invalidation and private field suppression.
- [ ] Run targeted tests and observe initial failure.
- [ ] Create focused renderer/store in latin/gifs; integrate suggestion thumbnail with guarded tap send/long-press save in SuggestionStripView.kt + LatinIME.java and reuse gif_provider_paths.xml existing cache path (add private files path only if needed).
- [ ] Run targeted tests, whole JVM suite/lint, appearance guard; review changed code.
- [ ] Build signed minified release and test real-device startup plus create/preview/save/send in fixture receiver before declaring complete.
