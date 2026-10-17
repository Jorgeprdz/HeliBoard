#!/usr/bin/env bash
# Host-only check: does not build Android, install APKs, or require network.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
P="$ROOT/app/src/main/java/helium314/keyboard/latin/ai"
javac -encoding UTF-8 -d "$OUT" \
  "$P/SensitiveInputPolicy.java" \
  "$P/EditorSnapshot.java" \
  "$P/AiCursorPolicy.java" \
  "$P/AiEditTransaction.java" \
  "$P/local/ContinuousReviewAnchor.java" \
  "$ROOT/experiments/r243/tests/AndroidEditContractHostTest.java"
java -cp "$OUT" AndroidEditContractHostTest
