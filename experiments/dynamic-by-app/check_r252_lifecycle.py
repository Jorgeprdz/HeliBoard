#!/usr/bin/env python3
"""R2.5.2 fail-closed structural contract (not a behavioral or physical PASS).
Old hazards are preserved in generic IME APIs but must have explicit by-app guards.
"""
from pathlib import Path
import sys

root=Path(__file__).resolve().parents[2]
files={
    "ime":"app/src/main/java/helium314/keyboard/latin/LatinIME.java",
    "switcher":"app/src/main/java/helium314/keyboard/keyboard/KeyboardSwitcher.java",
    "monet":"app/src/main/java/helium314/keyboard/latin/common/dynamic/MonetKeyboardColors.kt",
    "theme":"app/src/main/java/helium314/keyboard/keyboard/KeyboardTheme.kt",
    "policy":"app/src/main/java/helium314/keyboard/latin/common/dynamic/PaletteTransitionPolicy.kt",
    "bridge":"app/src/main/java/helium314/keyboard/latin/common/dynamic/R252ColorLifecycle.kt",
    "provider":"app/src/main/java/helium314/keyboard/latin/common/dynamic/AppColorSeedProvider.kt",
    "coord":"app/src/main/java/helium314/keyboard/latin/common/dynamic/AppSessionColorCoordinator.kt",
    "manifest":"app/src/debugNoMinify/AndroidManifest.xml",
}
try:
    s={k:(root/v).read_text(encoding="utf-8") for k,v in files.items()}
except OSError as e:
    print("R252_STATIC_UNVERIFIED",e);sys.exit(3)
identity=s["policy"].split("internal data class PaletteIdentity(",1)[1].split("internal sealed interface",1)[0]
checks={
    "no_epoch_in_palette_identity":("epoch:" not in identity and "packageName:" not in identity),
    "stale_epoch_only_in_policy":("next.epoch != activeEpoch" in s["policy"] and "PaletteDecision.RejectStale" in s["policy"]),
    "composition_defers":("if (composing) return PaletteDecision.Defer(candidate)" in s["policy"]),
    "off_by_default":("private var enabled = false" in s["bridge"] and "if (!enabled) return" in s["bridge"]),
    "targeted_restart_load":("|| r252PaletteBoundary" in s["ime"] and "prepareAtInputBoundary" in s["ime"]),
    "guard_before_view_replace":("if (r252InputBoundary && (mLatinIME.hasAiComposition()" in s["switcher"] and "throw new IllegalStateException" in s["switcher"]),
    "effective_identity_not_epoch":("R252PaletteRegistry.current()?.palette != sessionBinding.palette" in s["monet"]),
    "preserve_provider_when_off":("tracksSeedProvider = sessionBinding == null && seedOverride == null" in s["monet"]),
    "ten_local_families_only":("LOCAL_DYNAMIC_SCHEMES[name] ?: return null" in s["theme"]),
    "worker_has_no_view_actions":all(token not in s["provider"]+s["coord"] for token in ("setInputView(", "hideWindow(", "showWindow(", "setThemeNeedsReload(")),
    "test_receiver_protected":('tools:node="remove"' in s["manifest"] and "SystemBroadcastReceiver" in s["manifest"]),
}
for key,ok in checks.items():print(("PASS" if ok else "FAIL"),key)
if not all(checks.values()):
    print("R252_STATIC_NO_GO",sum(checks.values()),"/",len(checks));sys.exit(2)
print("R252_STATIC_CONTRACT_PASS",len(checks),"/",len(checks),
      "— NOT first-frame, emulator, Samsung, or composing-text acceptance")
