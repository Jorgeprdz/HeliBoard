#!/usr/bin/env python3
"""R2.5.2 conservative static tripwire, NOT a runtime or physical gate.

Exits 2 if known hazards still exist; exits 3 when source anchors drift.
A zero exit only means anchors disappeared and NEVER proves safe integration.
"""
from pathlib import Path
import sys

root = Path(__file__).resolve().parents[2]
sources = {
    "ime": root / "app/src/main/java/helium314/keyboard/latin/LatinIME.java",
    "switcher": root / "app/src/main/java/helium314/keyboard/keyboard/KeyboardSwitcher.java",
    "monet": root / "app/src/main/java/helium314/keyboard/latin/common/dynamic/MonetKeyboardColors.kt",
    "theme": root / "app/src/main/java/helium314/keyboard/keyboard/KeyboardTheme.kt",
    "settings": root / "app/src/main/java/helium314/keyboard/latin/settings/SettingsValues.java",
}
try:
    data = {key: path.read_text(encoding="utf-8") for key, path in sources.items()}
except OSError as exc:
    print(f"R252_UNVERIFIED missing sources: {exc}")
    sys.exit(3)

anchors = {
    "inputViewReload": "mLatinIME.setInputView(onCreateInputView(displayContext, mIsHardwareAcceleratedDrawingEnabled));" in data["switcher"],
    "immediateReload": "mLatinIME.hideWindow();" in data["switcher"] and "mLatinIME.showWindow(true);" in data["switcher"],
    "restartSkipsSettings": "boolean isDifferentTextField = !restarting || inputTypeChanged;" in data["ime"]
      and "if (isDifferentTextField || !currentSettingsValues.hasSameOrientation(" in data["ime"],
    "mColorsMaterializedInSettings": "mColors = KeyboardTheme.getColorsForCurrentTheme(context);" in data["settings"],
    "overrideLosesFingerprint": "tracksSeedProvider = seedOverride == null" in data["monet"]
      and "tracksSeedProvider && DynamicColorSeedProvider.snapshot" in data["monet"],
}
print("R252_STATIC_ANCHORS", anchors)
if not all(anchors.values()):
    print("R252_UNVERIFIED: source anchors changed; manual lifecycle re-audit required")
    sys.exit(3)
print("R252_NO_GO: unsafe/stale color transition paths remain; STOP before LatinIME integration")
sys.exit(2)
