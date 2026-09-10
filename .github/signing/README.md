# iOS Glass development signing

This directory contains the **development-only** signing key used by the `feature/ios-glass-keyboard` debug APK.

Its purpose is to keep the signing certificate stable across GitHub Actions runners so successive physical-test APKs can be installed as updates instead of requiring an uninstall/reinstall cycle.

The key is intentionally not a production/release credential. Do not reuse it for a Play Store or production release.
