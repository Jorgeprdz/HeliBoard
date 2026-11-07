# GIF panel rework plan

1. Add regression coverage for native-keyboard mode, Unicode query isolation and no-key browsing hierarchy; observe missing API failure or source guard RED before implementation.
2. Rebuild GifPanel browse/compact views using existing palette; delete imitation key rows. Preserve provider/history/share behavior and make stale-editor checks logical.
3. KeyboardSwitcher keeps GIF at measured native frame height, switches the panel between full browse and compact strip overlay, restores native visibility and preserves other internal panel behavior. LatinIME exposes the guarded mode bridge and forwards native layout-state events while consuming query text.
4. Run pure source/query guards and direct compile; primary agent owns full build and physical screenshots/search/native typing/draft-preservation smoke checks.
